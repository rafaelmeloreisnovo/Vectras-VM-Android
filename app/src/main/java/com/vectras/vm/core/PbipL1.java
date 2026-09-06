package com.vectras.vm.core;

/**
 * PBIP-L1 deterministic kernel.
 *
 * <p>Formal relation:</p>
 * <pre>
 * q^2 = r^2 - d_perp^2
 * Delta_B = 4(r^2 - d_perp^2) = 4q^2
 * </pre>
 *
 * <p>This class implements Euclidean line-circle intersection classification only.
 * It does not assert a physical vortex model, Poincare-conjecture equivalence, or
 * any scientific claim beyond the implemented numerical contract.</p>
 */
public final class PbipL1 {

    public static final String FEDERATION_ID = "PBIP-L1-FED-V1";
    public static final String FORMULA_ID = "PBIP-L1";
    public static final double DEFAULT_TOLERANCE = 1.0e-12;

    public enum IntersectionClass {
        SECANT,
        TANGENT,
        NO_REAL_INTERSECTION
    }

    private PbipL1() {
        throw new AssertionError("PbipL1 is a utility class");
    }

    /** q^2 = r^2 - d_perp^2. */
    public static double halfChordSquared(double radius, double perpendicularDistance) {
        requireFiniteNonNegative("radius", radius);
        requireFiniteNonNegative("perpendicularDistance", perpendicularDistance);
        return radius * radius - perpendicularDistance * perpendicularDistance;
    }

    /** Delta_B = 4 q^2. */
    public static double discriminant(double radius, double perpendicularDistance) {
        return 4.0 * halfChordSquared(radius, perpendicularDistance);
    }

    /** Classify a discriminant with an explicit non-negative tolerance. */
    public static IntersectionClass classifyDiscriminant(double delta, double tolerance) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("delta must be finite");
        }
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and non-negative");
        }
        if (Math.abs(delta) <= tolerance) {
            return IntersectionClass.TANGENT;
        }
        return delta > 0.0
                ? IntersectionClass.SECANT
                : IntersectionClass.NO_REAL_INTERSECTION;
    }

    /** Classify the line-circle relation from radius and perpendicular distance. */
    public static IntersectionClass classify(double radius,
                                             double perpendicularDistance,
                                             double tolerance) {
        return classifyDiscriminant(discriminant(radius, perpendicularDistance), tolerance);
    }

    public static IntersectionClass classify(double radius, double perpendicularDistance) {
        return classify(radius, perpendicularDistance, DEFAULT_TOLERANCE);
    }

    private static void requireFiniteNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
