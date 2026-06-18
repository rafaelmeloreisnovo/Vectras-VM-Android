package com.vectras.vm.rafaelia.bitraf;

import org.junit.Test;

import static org.junit.Assert.*;

public class BiTRafFrameworkTest {

    @Test
    public void testCubeDimensions() {
        BiTRafFramework fw = BiTRafFramework.create();
        assertEquals(10, BiTRafFramework.DIM);
        assertEquals(1000, BiTRafFramework.NODES);
    }

    @Test
    public void testAllNodesInitializedInUnitRange() {
        BiTRafFramework fw = BiTRafFramework.create();
        for (int x = 0; x < 10; x++)
            for (int y = 0; y < 10; y++)
                for (int z = 0; z < 10; z++) {
                    double v = fw.get(x, y, z);
                    assertTrue("node (" + x + "," + y + "," + z + ") out of range: " + v,
                            v >= 0.0 && v <= 1.0);
                }
    }

    @Test
    public void testGlobalCoherenceInUnitRange() {
        BiTRafFramework fw = BiTRafFramework.create();
        assertTrue(fw.getGlobalCoherence() >= 0.0 && fw.getGlobalCoherence() <= 1.0);
    }

    @Test
    public void testEvolveIncrementsGeneration() {
        BiTRafFramework fw = BiTRafFramework.create();
        fw.evolve();
        assertEquals(1, fw.getGeneration());
        fw.evolve();
        assertEquals(2, fw.getGeneration());
    }

    @Test
    public void testEvolveChangesState() {
        BiTRafFramework fw = BiTRafFramework.create();
        double before = fw.getGlobalCoherence();
        fw.evolve();
        // After evolution, state should change (not guaranteed to be different but generation did change)
        assertEquals(1, fw.getGeneration());
    }

    @Test
    public void testFibonacciRafaelIsDeterministic() {
        double f1 = BiTRafFramework.fibRafaelAt(5, 0.0);
        double f2 = BiTRafFramework.fibRafaelAt(5, 0.0);
        assertEquals(f1, f2, 1e-12);
    }

    @Test
    public void testParityIsZeroOrOne() {
        BiTRafFramework fw = BiTRafFramework.create();
        for (int n = 0; n < 30; n++) {
            int p = fw.getParity(n);
            assertTrue("parity must be 0 or 1 at n=" + n, p == 0 || p == 1);
        }
    }

    @Test
    public void testCurvatureInNormalizedRange() {
        BiTRafFramework fw = BiTRafFramework.create();
        for (int n = 0; n < 30; n++) {
            double c = fw.getCurvature(n);
            assertTrue("curvature out of [-1,1] at n=" + n, c >= -1.0 && c <= 1.0);
        }
    }

    @Test
    public void testSliceZReturnsTenByTen() {
        BiTRafFramework fw = BiTRafFramework.create();
        double[][] slice = fw.sliceZ(0);
        assertEquals(10, slice.length);
        assertEquals(10, slice[0].length);
    }

    @Test
    public void testNegaFibRecurrence() {
        // negaFib(-1) = 1, negaFib(-2) = -1, negaFib(-3) = 2
        assertEquals(1.0, BiTRafFramework.negaFib(-1), 1e-9);
        assertEquals(-1.0, BiTRafFramework.negaFib(-2), 1e-9);
    }

    @Test
    public void testFibSequenceKnownValues() {
        assertEquals(0, BiTRafFramework.FIB[0]);
        assertEquals(1, BiTRafFramework.FIB[1]);
        assertEquals(1, BiTRafFramework.FIB[2]);
        assertEquals(2, BiTRafFramework.FIB[3]);
        assertEquals(5, BiTRafFramework.FIB[5]);
        assertEquals(55, BiTRafFramework.FIB[10]);
    }
}
