package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class GeometricCoherenceTheoremTest {

    @Test
    public void testDefaultMoments() {
        GeometricCoherenceTheorem theorem = GeometricCoherenceTheorem.create();
        assertEquals(6, theorem.moments());
    }

    @Test
    public void testTheoremHolds_allSubsetsReconstructible() {
        GeometricCoherenceTheorem theorem = GeometricCoherenceTheorem.create();
        assertTrue("∀k∈n, G/S_k must be reconstructible", theorem.allSubsetsReconstructible());
    }

    @Test
    public void testReconstructibleCountEqualsN() {
        GeometricCoherenceTheorem theorem = GeometricCoherenceTheorem.create();
        assertEquals(6, theorem.reconstructibleCount());
    }

    @Test
    public void testEachNodeHasMinConnections() {
        GeometricCoherenceTheorem theorem = GeometricCoherenceTheorem.create();
        for (int i = 0; i < theorem.moments(); i++) {
            assertTrue("node " + i + " has < " + GeometricCoherenceTheorem.MIN_CONNECTIONS + " connections",
                    theorem.connectionCount(i) >= GeometricCoherenceTheorem.MIN_CONNECTIONS);
        }
    }

    @Test
    public void testWeightsAllInUnitRange() {
        GeometricCoherenceTheorem theorem = GeometricCoherenceTheorem.create();
        for (double w : theorem.weights()) {
            assertTrue("weight out of [0,1]: " + w, w >= 0.0 && w <= 1.0);
        }
    }

    @Test
    public void testGlobalWeightAboveGeometricFloor() {
        GeometricCoherenceTheorem theorem = GeometricCoherenceTheorem.create();
        assertTrue("globalWeight must be ≥ GEOMETRIC_FLOOR",
                theorem.globalWeight() >= GeometricCoherenceTheorem.GEOMETRIC_FLOOR);
    }

    @Test
    public void testCustomMomentsTheoremHolds() {
        for (int n : new int[]{4, 5, 7, 8, 12}) {
            GeometricCoherenceTheorem t = GeometricCoherenceTheorem.create(n);
            assertTrue("theorem fails for n=" + n, t.allSubsetsReconstructible());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTooFewMomentsThrows() {
        GeometricCoherenceTheorem.create(2);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testOutOfRangeRemovalThrows() {
        GeometricCoherenceTheorem.create().isReconstructible(99);
    }

    @Test
    public void testGeometricFloorIsPhiDerived() {
        assertEquals(RafaeliaKernelV22.PHI - 1.0, GeometricCoherenceTheorem.GEOMETRIC_FLOOR, 1e-10);
    }
}
