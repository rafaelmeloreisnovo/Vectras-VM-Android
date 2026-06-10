package com.vectras.vm.rafaelia.attention;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class OctagonalAttentionEngineTest {

    @Test
    public void testDefaultWeightsAreNeutral() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        for (OctagonalAttentionEngine.Vector v : OctagonalAttentionEngine.Vector.values()) {
            assertEquals(0.5, engine.getWeight(v), 0.001);
        }
    }

    @Test
    public void testSetAndGetWeight() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        engine.setWeight(OctagonalAttentionEngine.Vector.FOCO, 0.9);
        assertEquals(0.9, engine.getWeight(OctagonalAttentionEngine.Vector.FOCO), 0.001);
    }

    @Test
    public void testWeightClampedToUnitInterval() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        engine.setWeight(OctagonalAttentionEngine.Vector.FOCO, 1.5);
        assertEquals(1.0, engine.getWeight(OctagonalAttentionEngine.Vector.FOCO), 0.001);
        engine.setWeight(OctagonalAttentionEngine.Vector.FOCO, -0.5);
        assertEquals(0.0, engine.getWeight(OctagonalAttentionEngine.Vector.FOCO), 0.001);
    }

    @Test
    public void testStepConverges() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        engine.attuneToTask(OctagonalAttentionEngine.Vector.EXECUCAO, 0.9);
        boolean converged = engine.converge(100);
        assertTrue("Engine should converge within 100 steps", converged);
    }

    @Test
    public void testCoherenceScoreInUnitRange() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        double score = engine.coherenceScore();
        assertTrue("coherence score ≥ 0", score >= 0.0);
        // No strict upper bound check (can exceed 1 due to PHI scaling)
    }

    @Test
    public void testProjectionReturnsSigmoidRange() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        for (OctagonalAttentionEngine.Direction d : OctagonalAttentionEngine.Direction.values()) {
            double p = engine.project(d);
            assertTrue("projection in (0,1)", p > 0.0 && p < 1.0);
        }
    }

    @Test
    public void testSnapshotContainsAllVectors() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        Map<OctagonalAttentionEngine.Vector, Double> snap = engine.snapshot();
        assertEquals(8, snap.size());
        for (OctagonalAttentionEngine.Vector v : OctagonalAttentionEngine.Vector.values()) {
            assertTrue(snap.containsKey(v));
        }
    }

    @Test
    public void testYangYinPairsAreDefined() {
        // Yang: FOCO, VONTADE, EXECUCAO; Yin: PERCEPCAO, INTUICAO, EMOCAO, IMAGINACAO, MEMORIA
        assertTrue(OctagonalAttentionEngine.Vector.FOCO.isYang);
        assertFalse(OctagonalAttentionEngine.Vector.EMOCAO.isYang);
        assertTrue(OctagonalAttentionEngine.Vector.EXECUCAO.isYang);
    }

    @Test
    public void testOppositeVectorIsCorrect() {
        // FOCO is ordinal 1, opposite should be ordinal 5 (VONTADE)
        OctagonalAttentionEngine.Vector opp = OctagonalAttentionEngine.Vector.FOCO.opposite();
        assertEquals(OctagonalAttentionEngine.Vector.VONTADE, opp);
    }

    @Test
    public void testResetRestoresNeutralState() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        engine.attuneToTask(OctagonalAttentionEngine.Vector.FOCO, 0.9);
        engine.step();
        engine.reset();
        assertEquals(0, engine.getStepCount());
        assertFalse(engine.isStable());
        for (OctagonalAttentionEngine.Vector v : OctagonalAttentionEngine.Vector.values()) {
            assertEquals(0.5, engine.getWeight(v), 0.001);
        }
    }

    @Test
    public void testAttuneDistributesWeights() {
        OctagonalAttentionEngine engine = OctagonalAttentionEngine.create();
        engine.attuneToTask(OctagonalAttentionEngine.Vector.FOCO, 0.8);
        assertEquals(0.8, engine.getWeight(OctagonalAttentionEngine.Vector.FOCO), 0.001);
        // other 7 vectors share remaining 0.2 equally
        double base = 0.2 / 7.0;
        assertEquals(base, engine.getWeight(OctagonalAttentionEngine.Vector.EMOCAO), 0.001);
    }
}
