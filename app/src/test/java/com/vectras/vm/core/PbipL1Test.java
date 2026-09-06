package com.vectras.vm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class PbipL1Test {

    private static final double EPS = 0.0;

    @Test
    public void canonicalSecantVector_matchesContract() {
        assertEquals(16.0, PbipL1.halfChordSquared(5.0, 3.0), EPS);
        assertEquals(64.0, PbipL1.discriminant(5.0, 3.0), EPS);
        assertEquals(PbipL1.IntersectionClass.SECANT, PbipL1.classify(5.0, 3.0));
    }

    @Test
    public void canonicalTangentVector_matchesContract() {
        assertEquals(0.0, PbipL1.halfChordSquared(5.0, 5.0), EPS);
        assertEquals(0.0, PbipL1.discriminant(5.0, 5.0), EPS);
        assertEquals(PbipL1.IntersectionClass.TANGENT, PbipL1.classify(5.0, 5.0));
    }

    @Test
    public void canonicalNoRealIntersectionVector_matchesContract() {
        assertEquals(-11.0, PbipL1.halfChordSquared(5.0, 6.0), EPS);
        assertEquals(-44.0, PbipL1.discriminant(5.0, 6.0), EPS);
        assertEquals(PbipL1.IntersectionClass.NO_REAL_INTERSECTION, PbipL1.classify(5.0, 6.0));
    }

    @Test
    public void explicitTolerance_controlsNearTangentClassification() {
        assertEquals(PbipL1.IntersectionClass.TANGENT,
                PbipL1.classifyDiscriminant(5.0e-13, 1.0e-12));
        assertEquals(PbipL1.IntersectionClass.SECANT,
                PbipL1.classifyDiscriminant(5.0e-13, 1.0e-14));
    }

    @Test
    public void invalidGeometryInputs_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> PbipL1.halfChordSquared(-1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> PbipL1.halfChordSquared(1.0, -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> PbipL1.classifyDiscriminant(Double.NaN, PbipL1.DEFAULT_TOLERANCE));
        assertThrows(IllegalArgumentException.class,
                () -> PbipL1.classifyDiscriminant(0.0, -1.0));
    }
}
