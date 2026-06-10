package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class FractalOperatorCycleTest {

    @Test
    public void testOperatorCount() {
        assertEquals(7, FractalOperatorCycle.OP_COUNT);
    }

    @Test
    public void testPortalClampsToUnitRange() {
        double r = FractalOperatorCycle.portal(100.0);
        assertTrue(r > 0.99 && r <= 1.0);
        double r2 = FractalOperatorCycle.portal(-100.0);
        assertTrue(r2 >= 0.0 && r2 < 0.01);
    }

    @Test
    public void testCicloFractalInUnitRange() {
        for (double x : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            double r = FractalOperatorCycle.cicloFractal(x);
            assertTrue("cicloFractal(" + x + ")=" + r, r >= 0.0 && r <= 1.0);
        }
    }

    @Test
    public void testCicloInUnitRange() {
        for (double x : new double[]{0.0, 0.3, 0.618, 0.8, 1.0}) {
            double r = FractalOperatorCycle.ciclo(x);
            assertTrue("ciclo(" + x + ")=" + r, r >= 0.0);
        }
    }

    @Test
    public void testNucleoCompressesInput() {
        double r = FractalOperatorCycle.nucleo(1.0);
        assertTrue("nucleo(1.0) should be ≤ 1.0", r <= 1.0);
    }

    @Test
    public void testRetroalimentacaoInUnitRange() {
        for (double x : new double[]{0.01, 0.1, 0.5, 1.0}) {
            double r = FractalOperatorCycle.retroalimentacao(x);
            assertTrue("retroalimentacao(" + x + ")=" + r, r >= 0.0 && r <= 1.0);
        }
    }

    @Test
    public void testCycleOutputInUnitRange() {
        FractalOperatorCycle cycle = FractalOperatorCycle.create();
        for (double seed : new double[]{0.0, 0.1, 0.5, 0.9, 1.0}) {
            double r = cycle.cycle(seed);
            assertTrue("cycle(" + seed + ")=" + r, r >= 0.0 && r <= 1.0);
        }
    }

    @Test
    public void testTurnIncrementsOnCycle() {
        FractalOperatorCycle cycle = FractalOperatorCycle.create();
        assertEquals(0, cycle.turn());
        cycle.cycle(0.5);
        assertEquals(1, cycle.turn());
    }

    @Test
    public void testMultiTurnCycle() {
        FractalOperatorCycle cycle = FractalOperatorCycle.create();
        double r = cycle.cycle(0.5, 5);
        assertEquals(5, cycle.turn());
        assertTrue(r >= 0.0 && r <= 1.0);
    }

    @Test
    public void testResetClearsState() {
        FractalOperatorCycle cycle = FractalOperatorCycle.create();
        cycle.cycle(0.5, 3);
        cycle.reset();
        assertEquals(0, cycle.turn());
        assertEquals(0.0, cycle.accumulator(), 1e-9);
        assertEquals(0.0, cycle.integral(), 1e-9);
    }
}
