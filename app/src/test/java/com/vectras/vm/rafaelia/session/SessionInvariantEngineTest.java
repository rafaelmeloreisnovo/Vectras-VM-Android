package com.vectras.vm.rafaelia.session;

import org.junit.Test;

import static org.junit.Assert.*;

public class SessionInvariantEngineTest {

    private static SessionInvariantEngine.SystemSnapshot goodSnapshot() {
        return SessionInvariantEngine.SystemSnapshot.builder()
                .inputMagnitude(10.0).outputMagnitude(10.0)
                .resourceConsumed(0.8).resourceBudget(1.0)
                .microCoherence(0.9).macroCoherence(0.85)
                .entropy(0.1).coherence(0.9)
                .effectCount(10).orphanEffectCount(0)
                .entropyGrowthRate(0.01)
                .feedbackStable(true).cycleDepth(3).maxCycleDepth(10)
                .activeOperations(5).operationLimit(100)
                .contradictionCount(0).componentCount(10)
                .build();
    }

    @Test
    public void testAllInvariantsPassOnGoodSnapshot() {
        SessionInvariantEngine engine = SessionInvariantEngine.create();
        SessionInvariantEngine.InvariantReview review = engine.evaluate(goodSnapshot());
        assertEquals(9, review.passed);
        assertEquals(0, review.violations);
        assertTrue(review.stable);
        assertTrue(review.tesseractPassed);
        assertTrue(review.globalScore > 0.8);
    }

    @Test
    public void testConservationViolationDetected() {
        SessionInvariantEngine engine = SessionInvariantEngine.create();
        SessionInvariantEngine.SystemSnapshot snap = SessionInvariantEngine.SystemSnapshot.builder()
                .inputMagnitude(10).outputMagnitude(10)
                .resourceConsumed(1.5).resourceBudget(1.0) // 50% over budget
                .microCoherence(0.9).macroCoherence(0.9)
                .entropy(0.1).coherence(0.9)
                .effectCount(5).orphanEffectCount(0)
                .entropyGrowthRate(0.01)
                .feedbackStable(true).cycleDepth(1).maxCycleDepth(10)
                .activeOperations(5).operationLimit(100)
                .contradictionCount(0).componentCount(5)
                .build();
        SessionInvariantEngine.InvariantReview review = engine.evaluate(snap);
        assertEquals(SessionInvariantEngine.InvariantStatus.VIOLATION,
                review.results.get(SessionInvariantEngine.Invariant.CONSERVATION).status);
        assertFalse(review.tesseractPassed); // CONSERVATION is a tesseract member
        assertFalse(review.stable);
    }

    @Test
    public void testCausalityViolationDetected() {
        SessionInvariantEngine engine = SessionInvariantEngine.create();
        SessionInvariantEngine.SystemSnapshot snap = SessionInvariantEngine.SystemSnapshot.builder()
                .inputMagnitude(10).outputMagnitude(10)
                .resourceConsumed(0.5).resourceBudget(1.0)
                .microCoherence(0.9).macroCoherence(0.9)
                .entropy(0.1).coherence(0.9)
                .effectCount(10).orphanEffectCount(5) // 50% orphan effects
                .entropyGrowthRate(0.01)
                .feedbackStable(true).cycleDepth(1).maxCycleDepth(10)
                .activeOperations(5).operationLimit(100)
                .contradictionCount(0).componentCount(10)
                .build();
        SessionInvariantEngine.InvariantReview review = engine.evaluate(snap);
        assertEquals(SessionInvariantEngine.InvariantStatus.VIOLATION,
                review.results.get(SessionInvariantEngine.Invariant.CAUSALITY).status);
    }

    @Test
    public void testCompositionViolationMakesUnstable() {
        SessionInvariantEngine engine = SessionInvariantEngine.create();
        SessionInvariantEngine.SystemSnapshot snap = SessionInvariantEngine.SystemSnapshot.builder()
                .inputMagnitude(10).outputMagnitude(10)
                .resourceConsumed(0.5).resourceBudget(1.0)
                .microCoherence(0.9).macroCoherence(0.9)
                .entropy(0.1).coherence(0.9)
                .effectCount(5).orphanEffectCount(0)
                .entropyGrowthRate(0.01)
                .feedbackStable(true).cycleDepth(1).maxCycleDepth(10)
                .activeOperations(5).operationLimit(100)
                .contradictionCount(3).componentCount(10) // contradictions present
                .build();
        SessionInvariantEngine.InvariantReview review = engine.evaluate(snap);
        assertEquals(SessionInvariantEngine.InvariantStatus.VIOLATION,
                review.results.get(SessionInvariantEngine.Invariant.COMPOSITION).status);
        assertFalse(review.stable);
    }

    @Test
    public void testAllNineInvariantsAreEvaluated() {
        SessionInvariantEngine engine = SessionInvariantEngine.create();
        SessionInvariantEngine.InvariantReview review = engine.evaluate(goodSnapshot());
        assertEquals(9, review.results.size());
        for (SessionInvariantEngine.Invariant inv : SessionInvariantEngine.Invariant.values()) {
            assertTrue("Missing invariant: " + inv, review.results.containsKey(inv));
        }
    }
}
