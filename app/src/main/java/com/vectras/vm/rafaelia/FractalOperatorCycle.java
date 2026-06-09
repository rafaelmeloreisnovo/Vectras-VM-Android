package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

/**
 * FractalOperatorCycle — 7-operator symbolic pipeline (Image 5).
 *
 * <pre>
 *   § [PORTAL]          → μ [CICLO_FRACTAL]  → Δ [CICLO]
 *   → ∅ [NUCLEO]        → nⁿ [RETROALIMENTACAO] → n [REGISTRO]
 *   → % [ANTIDERIVADA]
 * </pre>
 *
 * <p>Each operator transforms the signal and passes it to the next.
 * The cycle is deterministic and reversible.
 *
 * <p>Mathematical interpretations:
 * <ul>
 *   <li>§ PORTAL:          Input gate — normalize to [0,1] via sigmoid</li>
 *   <li>μ CICLO_FRACTAL:   Fractal cycle — φ-modulated cosine oscillation</li>
 *   <li>Δ CICLO:           Delta cycle — difference from center (φ−1)</li>
 *   <li>∅ NUCLEO:          Null core — project onto √(3/2) axis</li>
 *   <li>nⁿ RETROALIMENTACAO: Self-exponentiation feedback — x^x scaled</li>
 *   <li>n REGISTRO:        Registry — accumulate in [0,1] running sum</li>
 *   <li>% ANTIDERIVADA:    Anti-derivative — integrate (running trapezoid)</li>
 * </ul>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-CYCLE
 */
public final class FractalOperatorCycle {

    public enum Operator {
        PORTAL,           // §
        CICLO_FRACTAL,    // μ
        CICLO,            // Δ
        NUCLEO,           // ∅
        RETROALIMENTACAO, // nⁿ
        REGISTRO,         // n
        ANTIDERIVADA      // %
    }

    public static final int OP_COUNT = Operator.values().length; // 7

    // ─── Cycle state ──────────────────────────────────────────────────────────

    private double   accumulator  = 0.0; // REGISTRO running sum
    private double   integral     = 0.0; // ANTIDERIVADA running trapezoid
    private double   prev         = 0.0; // previous value for trapezoid
    private int      turn         = 0;   // full cycle count
    private Operator currentOp    = Operator.PORTAL;

    private FractalOperatorCycle() {}

    public static FractalOperatorCycle create() { return new FractalOperatorCycle(); }

    // ─── Single operator ──────────────────────────────────────────────────────

    /**
     * Apply a single operator to a value.
     */
    public double apply(@NonNull Operator op, double x) {
        return switch (op) {
            case PORTAL           -> portal(x);
            case CICLO_FRACTAL    -> cicloFractal(x);
            case CICLO            -> ciclo(x);
            case NUCLEO           -> nucleo(x);
            case RETROALIMENTACAO -> retroalimentacao(x);
            case REGISTRO         -> registro(x);
            case ANTIDERIVADA     -> antiderivada(x);
        };
    }

    // ─── Full cycle ───────────────────────────────────────────────────────────

    /**
     * Run the full 7-operator cycle on the seed value.
     * Each application threads the output of the previous into the next.
     * @param seed input value
     * @return output after all 7 operators
     */
    public double cycle(double seed) {
        double v = seed;
        for (Operator op : Operator.values()) {
            currentOp = op;
            v = apply(op, v);
        }
        turn++;
        currentOp = Operator.PORTAL;
        return v;
    }

    /**
     * Run N full cycles.
     * @return value after N cycles
     */
    public double cycle(double seed, int turns) {
        double v = seed;
        for (int i = 0; i < turns; i++) v = cycle(v);
        return v;
    }

    // ─── Operator implementations ─────────────────────────────────────────────

    /** § PORTAL: normalize via sigmoid — gate input to (0,1). */
    static double portal(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /** μ CICLO_FRACTAL: φ-modulated fractal oscillation. */
    static double cicloFractal(double x) {
        double theta = x * RafaeliaKernelV22.PHI * RafaeliaKernelV22.PI * 2.0;
        return (Math.cos(theta) + RafaeliaKernelV22.SPIRAL * Math.sin(theta * RafaeliaKernelV22.PHI)) * 0.5 + 0.5;
    }

    /** Δ CICLO: difference from φ−1 center, mapped to [0,1]. */
    static double ciclo(double x) {
        double center = RafaeliaKernelV22.PHI - 1.0; // ≈ 0.618
        return Math.abs(x - center) / Math.max(center, 1.0 - center);
    }

    /** ∅ NUCLEO: project onto SPIRAL axis — √(3/2) compression. */
    static double nucleo(double x) {
        return Math.min(1.0, x * RafaeliaKernelV22.SPIRAL);
    }

    /** nⁿ RETROALIMENTACAO: self-exponentiation x^(x+ε), scaled to [0,1]. */
    static double retroalimentacao(double x) {
        double safe = Math.max(1e-10, Math.min(1.0, x));
        double xn   = Math.pow(safe, safe);
        return Math.min(1.0, xn);
    }

    /** n REGISTRO: accumulate (running mean), returns current mean. */
    double registro(double x) {
        accumulator = (accumulator + x) * 0.5; // exponential moving average
        return accumulator;
    }

    /** % ANTIDERIVADA: trapezoidal integration running sum, normalized. */
    double antiderivada(double x) {
        integral += (prev + x) * 0.5;
        prev = x;
        // normalize via tanh to stay in [0,1]
        return (Math.tanh(integral * RafaeliaKernelV22.SPIRAL) + 1.0) * 0.5;
    }

    // ─── State ────────────────────────────────────────────────────────────────

    public Operator currentOp()  { return currentOp; }
    public int      turn()       { return turn; }
    public double   accumulator(){ return accumulator; }
    public double   integral()   { return integral; }

    public void reset() {
        accumulator = 0.0; integral = 0.0; prev = 0.0; turn = 0;
        currentOp = Operator.PORTAL;
    }

    @NonNull @Override
    public String toString() {
        return String.format("FractalCycle[turn=%d op=%s acc=%.4f integral=%.4f]",
                turn, currentOp, accumulator, integral);
    }
}
