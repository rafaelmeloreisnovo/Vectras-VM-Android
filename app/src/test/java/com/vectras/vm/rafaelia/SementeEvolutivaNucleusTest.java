package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class SementeEvolutivaNucleusTest {

    @Test
    public void testInitialStateIsZero() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        assertEquals(0.0, nucleus.coherenceState(), 1e-9);
        assertEquals(0, nucleus.generation());
    }

    @Test
    public void testProcessReturnsInUnitRange() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        double result = nucleus.process(0.7);
        assertTrue("result out of [0,1]: " + result, result >= 0.0 && result <= 1.0);
    }

    @Test
    public void testGenerationIncrementsOnProcess() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        nucleus.process(0.5);
        assertEquals(1, nucleus.generation());
        nucleus.process(0.5);
        assertEquals(2, nucleus.generation());
    }

    @Test
    public void testFlowStageAfterProcess() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        nucleus.process(0.5);
        assertEquals(SementeEvolutivaNucleus.FlowStage.SAIDA, nucleus.flowStage());
    }

    @Test
    public void testGovernanceIsCompliant() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        assertTrue(nucleus.governance().isCompliant());
    }

    @Test
    public void testDefaultPolicyIsModerate() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        assertEquals(SementeEvolutivaNucleus.AdaptationPolicy.MODERADO, nucleus.policy());
    }

    @Test
    public void testSetPolicyChangesPolicy() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        nucleus.setPolicy(SementeEvolutivaNucleus.AdaptationPolicy.AGRESSIVO);
        assertEquals(SementeEvolutivaNucleus.AdaptationPolicy.AGRESSIVO, nucleus.policy());
    }

    @Test
    public void testAuditLogsAreRecorded() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        nucleus.process(0.5);
        assertTrue("logs should be recorded", nucleus.logs().size() > 0);
    }

    @Test
    public void testDecisionsCount() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        assertEquals(0, nucleus.decisions());
        nucleus.process(0.4);
        nucleus.process(0.6);
        assertEquals(2, nucleus.decisions());
    }

    @Test
    public void testEnvironmentArchIsKnown() {
        SementeEvolutivaNucleus nucleus = SementeEvolutivaNucleus.create();
        assertNotNull(nucleus.environment().arch);
    }
}
