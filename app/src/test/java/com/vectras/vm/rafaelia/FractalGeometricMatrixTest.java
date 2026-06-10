package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class FractalGeometricMatrixTest {

    @Test
    public void testNodeCount() {
        assertEquals(42, FractalGeometricMatrix.NODES);
    }

    @Test
    public void testBase60() {
        assertEquals(60, FractalGeometricMatrix.BASE_60);
    }

    @Test
    public void testAllCoherencesInUnitRange() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        for (int n = 0; n < 42; n++) {
            double c = m.getCoherence(n);
            assertTrue("node " + n + " coherence out of [0,1]: " + c, c >= 0.0 && c <= 1.0);
        }
    }

    @Test
    public void testGlobalCoherenceInUnitRange() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        double g = m.globalCoherence();
        assertTrue(g >= 0.0 && g <= 1.0);
    }

    @Test
    public void testEvolveIncrementsGeneration() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        m.evolve();
        assertEquals(1, m.getGeneration());
        m.evolve();
        assertEquals(2, m.getGeneration());
    }

    @Test
    public void testBase60AddressMapping() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        for (int n = 0; n < 42; n++) {
            int[] b60 = m.getBase60(n);
            assertEquals(n / 60, b60[0]); // major
            assertEquals(n % 60, b60[1]); // minor
        }
    }

    @Test
    public void testRingMembership() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        assertEquals(0, m.ringOf(0));  // center
        assertEquals(1, m.ringOf(1));  // ring 1 (nodes 1-6)
        assertEquals(1, m.ringOf(6));  // ring 1 last
        assertEquals(2, m.ringOf(7));  // ring 2 first
    }

    @Test
    public void testMaxCoherenceNodeIsValid() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        int maxN = m.maxCoherenceNode();
        assertTrue(maxN >= 0 && maxN < 42);
    }

    @Test
    public void testCartesianCenterNodeIsOrigin() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        double[] c = m.getCartesian(0); // center node
        assertEquals(0.0, c[0], 1e-9);
        assertEquals(0.0, c[1], 1e-9);
    }

    @Test
    public void testToroidalDistanceSelf() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        assertEquals(0.0, m.toroidalDistance(5, 5), 1e-9);
    }

    @Test
    public void testToroidalDistanceSymmetric() {
        FractalGeometricMatrix m = FractalGeometricMatrix.create();
        assertEquals(m.toroidalDistance(1, 7), m.toroidalDistance(7, 1), 1e-9);
    }
}
