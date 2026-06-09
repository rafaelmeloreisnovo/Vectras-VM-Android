package com.vectras.vm.rafaelia.attractor;

import org.junit.Test;

import static org.junit.Assert.*;

public class VectraAttractorEngineTest {

    @Test
    public void testAttractorCount() {
        assertEquals(42, VectraAttractorEngine.ATTRACTOR_COUNT);
    }

    @Test
    public void testLimiarIsPhiDerived() {
        double expected = 1.0 - (1.0 / 1.6180339887498948);
        assertEquals(expected, VectraAttractorEngine.LIMIAR, 1e-9);
    }

    @Test
    public void testStepReturnsInUnitRange() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        for (double input : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            double result = engine.step(input);
            assertTrue("coherence out of [0,1]: " + result, result >= 0.0 && result <= 1.0);
        }
    }

    @Test
    public void testGlobalCoherenceInUnitRange() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        engine.step(0.5);
        double c = engine.globalCoherence();
        assertTrue(c >= 0.0 && c <= 1.0);
    }

    @Test
    public void testStepCountIncrementsOnEachStep() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        assertEquals(0, engine.stepCount());
        engine.step(0.3);
        assertEquals(1, engine.stepCount());
        engine.step(0.7);
        assertEquals(2, engine.stepCount());
    }

    @Test
    public void testDeltaIncoherenceIsNonNegative() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        engine.step(0.5);
        assertTrue(engine.deltaIncoherence() >= 0.0);
    }

    @Test
    public void testProjectForAllDomains() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        engine.step(0.5);
        for (VectraAttractorEngine.Domain d : VectraAttractorEngine.Domain.values()) {
            double p = engine.project(d);
            assertTrue("domain " + d + " projection out of range: " + p, p >= 0.0 && p <= 1.0);
        }
    }

    @Test
    public void testCollapseCountIsNonNegative() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        for (int i = 0; i < 10; i++) engine.step(Math.random());
        assertTrue(engine.collapseCount() >= 0);
    }

    @Test
    public void testPipelineStageChanges() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        assertEquals(VectraAttractorEngine.PipelineStage.INPUT, engine.stage());
        engine.step(0.5);
        assertEquals(VectraAttractorEngine.PipelineStage.SAIDA, engine.stage());
    }

    @Test
    public void testCurrentAttractorInRange() {
        VectraAttractorEngine engine = VectraAttractorEngine.create();
        engine.step(0.5);
        int ca = engine.currentAttractor();
        assertTrue(ca >= 0 && ca < VectraAttractorEngine.ATTRACTOR_COUNT);
    }
}
