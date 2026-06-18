package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class ObserverGeometryEngineTest {

    @Test
    public void testFibKnownValues() {
        assertEquals(0,  ObserverGeometryEngine.fib(0));
        assertEquals(1,  ObserverGeometryEngine.fib(1));
        assertEquals(1,  ObserverGeometryEngine.fib(2));
        assertEquals(8,  ObserverGeometryEngine.fib(6));
        assertEquals(55, ObserverGeometryEngine.fib(10));
        assertEquals(144, ObserverGeometryEngine.fib(12));
    }

    @Test
    public void testFibNegativeThrows() {
        try {
            ObserverGeometryEngine.fib(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testStabilityWindowLength() {
        long[] w = ObserverGeometryEngine.stabilityWindow(10);
        assertEquals(7, w.length);
        assertEquals(ObserverGeometryEngine.fib(7),  w[0]);
        assertEquals(ObserverGeometryEngine.fib(10), w[3]);
        assertEquals(ObserverGeometryEngine.fib(13), w[6]);
    }

    @Test
    public void testFibOfPrimeKnownValues() {
        // k=0 → F(2)=1, k=1 → F(3)=2, k=2 → F(5)=5
        assertEquals(1, ObserverGeometryEngine.fibOfPrime(0));
        assertEquals(2, ObserverGeometryEngine.fibOfPrime(1));
        assertEquals(5, ObserverGeometryEngine.fibOfPrime(2));
    }

    @Test
    public void testZeckendorfDecompositionRoundTrip() {
        long[] values = {0, 1, 5, 8, 13, 21, 100, 144, 1000};
        for (long v : values) {
            int[] c = ObserverGeometryEngine.zeckendorf(v);
            assertEquals("roundtrip failed for " + v, v, ObserverGeometryEngine.fromZeckendorf(c));
        }
    }

    @Test
    public void testFibError() {
        double err = ObserverGeometryEngine.fibError(145.0);
        assertTrue("fibError(145)=" + err + " should be ≈1", err >= 0.0 && err <= 2.0);
    }

    @Test
    public void testFibErrorForExactFibIsZero() {
        assertEquals(0.0, ObserverGeometryEngine.fibError(144.0), 1e-9);
    }

    @Test
    public void testWaveCollapseInMinusOneToOne() {
        double x = ObserverGeometryEngine.waveCollapse(0.6, 0.8);
        assertTrue("waveCollapse out of [-1,1]: " + x, x >= -1.0 && x <= 1.0);
    }

    @Test
    public void testWaveCollapseZeroAmplitude() {
        assertEquals(0.0, ObserverGeometryEngine.waveCollapse(0.0, 0.0), 1e-9);
    }

    @Test
    public void testStabilityIsPositive() {
        double s = ObserverGeometryEngine.stability(10, 55.0); // F(10)=55 → exact
        assertTrue("stability should be > 0", s > 0.0);
    }
}
