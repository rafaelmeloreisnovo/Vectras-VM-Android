package com.vectras.vm.rafaelia.session;

import org.junit.Test;

import static org.junit.Assert.*;

public class RafaeliaSessionCycleTest {

    @Test
    public void testBeginTransitionsToLer() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        assertTrue(cycle.begin());
        assertEquals(RafaeliaSessionCycle.CyclePhase.LER, cycle.getPhase());
    }

    @Test
    public void testBeginFailsIfAlreadyRunning() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        assertTrue(cycle.begin());
        assertFalse(cycle.begin());
    }

    @Test
    public void testFullCycleCompletesSuccessfully() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        cycle.begin();
        // Advance through all steps with good entropy/coherence
        RafaeliaSessionCycle.CyclePhase p;
        p = cycle.advance(0.1, 0.9); // LER → RETROALIMENTAR
        assertEquals(RafaeliaSessionCycle.CyclePhase.RETROALIMENTAR, p);
        p = cycle.advance(0.1, 0.9); // → EXPANDIR
        assertEquals(RafaeliaSessionCycle.CyclePhase.EXPANDIR, p);
        p = cycle.advance(0.1, 0.9); // → VALIDAR
        assertEquals(RafaeliaSessionCycle.CyclePhase.VALIDAR, p);
        p = cycle.advance(0.1, 0.9); // → EXECUTAR (ethical gate passes)
        assertEquals(RafaeliaSessionCycle.CyclePhase.EXECUTAR, p);
        p = cycle.advance(0.1, 0.9); // → ÉTICA
        assertEquals(RafaeliaSessionCycle.CyclePhase.ETICA, p);
        p = cycle.advance(0.1, 0.9); // → COMPLETE
        assertEquals(RafaeliaSessionCycle.CyclePhase.COMPLETE, p);
        assertEquals(1, cycle.getCycleCount());
    }

    @Test
    public void testEthicalGateBlocksHighEntropy() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        cycle.begin();
        cycle.advance(0.1, 0.9); // LER → RETROALIMENTAR
        cycle.advance(0.1, 0.9); // → EXPANDIR
        cycle.advance(0.1, 0.9); // → VALIDAR
        // High entropy → ENTROPY_TOO_HIGH abort
        RafaeliaSessionCycle.CyclePhase p = cycle.advance(0.9, 0.9);
        assertEquals(RafaeliaSessionCycle.CyclePhase.ABORTED, p);
        assertEquals(RafaeliaSessionCycle.AbortReason.ENTROPY_TOO_HIGH, cycle.getAbortReason());
    }

    @Test
    public void testEthicalGateBlocksLowPhiEthica() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        cycle.begin();
        cycle.advance(0.1, 0.9);
        cycle.advance(0.1, 0.9);
        cycle.advance(0.1, 0.9); // VALIDAR
        // Low coherence → phi_ethica below gate
        RafaeliaSessionCycle.CyclePhase p = cycle.advance(0.3, 0.05);
        assertEquals(RafaeliaSessionCycle.CyclePhase.ABORTED, p);
    }

    @Test
    public void testPhiEthicaFormula() {
        // phi = (1 - entropy) × coherence × SPIRAL
        double phi = RafaeliaSessionCycle.computePhiEthica(0.0, 1.0);
        // At zero entropy + full coherence = SPIRAL ≈ 0.866
        assertEquals(0.866, phi, 0.001);

        double phi2 = RafaeliaSessionCycle.computePhiEthica(1.0, 1.0);
        // At max entropy: phi = 0
        assertEquals(0.0, phi2, 0.001);
    }

    @Test
    public void testHistoryRecordsSteps() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        cycle.begin();
        cycle.advance(0.1, 0.9);
        cycle.advance(0.2, 0.8);
        assertEquals(2, cycle.getHistory().size());
        assertEquals(RafaeliaSessionCycle.CyclePhase.LER, cycle.getHistory().get(0).phase);
    }

    @Test
    public void testResetClearsState() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        cycle.begin();
        cycle.advance(0.1, 0.9);
        cycle.reset();
        assertEquals(RafaeliaSessionCycle.CyclePhase.IDLE, cycle.getPhase());
        assertTrue(cycle.getHistory().isEmpty());
        assertEquals(0, cycle.getCycleCount());
    }

    @Test
    public void testRestartAfterComplete() {
        RafaeliaSessionCycle cycle = RafaeliaSessionCycle.create();
        cycle.begin();
        for (int i = 0; i < 6; i++) cycle.advance(0.1, 0.9);
        assertEquals(RafaeliaSessionCycle.CyclePhase.COMPLETE, cycle.getPhase());
        assertTrue(cycle.begin());
        assertEquals(RafaeliaSessionCycle.CyclePhase.LER, cycle.getPhase());
    }
}
