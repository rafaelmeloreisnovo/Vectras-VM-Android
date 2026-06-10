package com.vectras.vm.rafaelia.toroidal;

import androidx.annotation.NonNull;

import com.vectras.vm.rafaelia.RafaeliaKernelV22;

/**
 * Toroidal Matrix Engine — MATRIZES TORO/37 (module 6 of the 12-module architecture).
 *
 * <p>Implements the toroidal field topology where the data wraps around in both
 * dimensions (like a torus surface). This makes the matrix "boundary-free":
 * row N wraps to row 0, column N wraps to column 0.
 *
 * <p>Mathematical basis (from toroidal field equations image):
 * <pre>
 *   Surface parametric: (R + r·cos θ)·cos φ, (R + r·cos θ)·sin φ, r·sin θ
 *   Field equation: ∂²φ/∂t² = ∇²φ (wave propagation on torus)
 *   Wavefunction collapse: Ψ → Ψ̃  ⇒  E ≤ C
 *   H-Torus: genus-1 surface, CRW/CSL candidate
 * </pre>
 *
 * <p>The "37" in MATRIZES TORO/37 refers to the 37-point stencil used for
 * the toroidal Laplacian on a 6×6 compact neighborhood.
 *
 * <p>Operational uses:
 * <ul>
 *   <li>M5_T7_TOROIDAL_UPDATE benchmark metric (RAFAELIA_HEXGRID)</li>
 *   <li>State propagation for cognitive loop smoothing</li>
 *   <li>Energy-conserving field evolution</li>
 * </ul>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-TORO
 */
public final class ToroidalMatrixEngine {

    // ─── Geometry constants ───────────────────────────────────────────────────
    /** Major radius R of the torus */
    public static final double TORUS_R = RafaeliaKernelV22.PHI * 3.0;
    /** Minor radius r */
    public static final double TORUS_r = 1.0;
    /** θ_999 — toroidal energy angle */
    public static final double THETA_999 = RafaeliaKernelV22.THETA_999;

    // ─── Matrix state ─────────────────────────────────────────────────────────
    private final int     rows, cols;
    private final double[] field;    // current field values (flat: row*cols + col)
    private final double[] velocity; // time derivative ∂φ/∂t
    private double         time     = 0.0;
    private double         energy   = 0.0;

    // Wave propagation parameters
    private double waveSpeed  = 1.0;   // c in ∂²φ/∂t² = c²·∇²φ
    private double dt         = 0.05;  // time step

    private ToroidalMatrixEngine(int rows, int cols) {
        this.rows     = rows;
        this.cols     = cols;
        this.field    = new double[rows * cols];
        this.velocity = new double[rows * cols];
    }

    public static ToroidalMatrixEngine create(int rows, int cols) {
        if (rows < 2 || cols < 2) throw new IllegalArgumentException("min 2×2");
        return new ToroidalMatrixEngine(rows, cols);
    }

    /** Create a square toroidal matrix sized to accommodate the "37-point" neighborhood. */
    public static ToroidalMatrixEngine createToro37() {
        return create(37, 37);
    }

    // ─── Initialization patterns ──────────────────────────────────────────────

    /** Initialize with a Gaussian pulse at center. */
    public void initGaussianPulse(double amplitude, double sigma) {
        int cr = rows / 2, cc = cols / 2;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double dr = toroidalDist(r, cr, rows);
                double dc = toroidalDist(c, cc, cols);
                double d2 = dr * dr + dc * dc;
                field[flat(r, c)] = amplitude * Math.exp(-d2 / (2.0 * sigma * sigma));
            }
        }
        computeEnergy();
    }

    /** Initialize with a spiral pattern aligned to Fibonacci Rafael. */
    public void initSpiralPattern() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double theta = 2.0 * Math.PI * r / rows;
                double phi   = 2.0 * Math.PI * c / cols;
                // Torus surface value
                double x = (TORUS_R + TORUS_r * Math.cos(theta)) * Math.cos(phi);
                double y = (TORUS_R + TORUS_r * Math.cos(theta)) * Math.sin(phi);
                double z = TORUS_r * Math.sin(theta);
                field[flat(r, c)] = Math.sin(
                        x * RafaeliaKernelV22.PHI + y * RafaeliaKernelV22.SPIRAL + z
                ) * 0.5 + 0.5;
            }
        }
        computeEnergy();
    }

    // ─── Wave propagation step ────────────────────────────────────────────────

    /**
     * Advance one time step using the leapfrog wave equation:
     * <pre>φ(t+dt) = 2φ(t) - φ(t-dt) + c²·dt²·∇²φ(t)</pre>
     *
     * Implemented via velocity–position leapfrog (energy-conserving):
     * <pre>
     *   v(t+dt/2) = v(t-dt/2) + dt · c² · ∇²φ(t)
     *   φ(t+dt)   = φ(t) + dt · v(t+dt/2)
     * </pre>
     */
    public void step() {
        double c2dt2 = waveSpeed * waveSpeed * dt * dt;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = flat(r, c);
                double lap = toroidalLaplacian(r, c);
                velocity[idx] += c2dt2 * lap;
                field[idx]    += dt * velocity[idx];
            }
        }
        time += dt;
        computeEnergy();
    }

    /** Run N steps. */
    public void evolve(int steps) {
        for (int i = 0; i < steps; i++) step();
    }

    // ─── Wavefunction collapse (Ψ → Ψ̃ ⇒ E ≤ C) ──────────────────────────────

    /**
     * Apply wavefunction collapse: normalize energy to ≤ energyCap.
     * If current energy > cap, scale all field values to meet the constraint.
     */
    public boolean collapse(double energyCap) {
        if (energy <= energyCap) return false; // already within bounds
        double scale = Math.sqrt(energyCap / energy);
        for (int i = 0; i < field.length; i++) {
            field[i]    *= scale;
            velocity[i] *= scale;
        }
        computeEnergy();
        return true;
    }

    // ─── Toroidal Laplacian (5-point stencil, toroidal wrapping) ─────────────

    double toroidalLaplacian(int r, int c) {
        double center = field[flat(r, c)];
        double up     = field[flat(wrap(r-1, rows), c)];
        double down   = field[flat(wrap(r+1, rows), c)];
        double left   = field[flat(r, wrap(c-1, cols))];
        double right  = field[flat(r, wrap(c+1, cols))];
        return up + down + left + right - 4.0 * center;
    }

    // ─── Energy computation ────────────────────────────────────────────────────

    private void computeEnergy() {
        double e = 0.0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = flat(r, c);
                double ke = 0.5 * velocity[idx] * velocity[idx];
                // Potential energy via gradient squared (central differences, toroidal)
                double dFdr = (field[flat(wrap(r+1, rows), c)] - field[flat(wrap(r-1, rows), c)]) / 2.0;
                double dFdc = (field[flat(r, wrap(c+1, cols))] - field[flat(r, wrap(c-1, cols))]) / 2.0;
                double pe = 0.5 * (dFdr * dFdr + dFdc * dFdc);
                e += ke + pe;
            }
        }
        energy = e;
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public double get(int r, int c)     { return field[flat(r, c)]; }
    public double getVelocity(int r, int c) { return velocity[flat(r, c)]; }
    public double getEnergy()           { return energy; }
    public double getTime()             { return time; }
    public int    getRows()             { return rows; }
    public int    getCols()             { return cols; }

    public void   setWaveSpeed(double c){ waveSpeed = c; }
    public void   setDt(double dt)      { this.dt = dt; }

    /** Compute mean field value (DC component). */
    public double meanField() {
        double sum = 0.0;
        for (double v : field) sum += v;
        return sum / field.length;
    }

    /** Compute RMS field amplitude. */
    public double rmsField() {
        double sumSq = 0.0;
        for (double v : field) sumSq += v * v;
        return Math.sqrt(sumSq / field.length);
    }

    // ─── Geometry helpers ─────────────────────────────────────────────────────

    /** Map a point on the torus to Cartesian coordinates (R + r·cosθ)·cosφ etc. */
    public static double[] toCartesian(double theta, double phi,
                                        double R, double r) {
        return new double[]{
                (R + r * Math.cos(theta)) * Math.cos(phi),
                (R + r * Math.cos(theta)) * Math.sin(phi),
                r * Math.sin(theta)
        };
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private int flat(int r, int c) { return r * cols + c; }

    private static int wrap(int v, int size) {
        return ((v % size) + size) % size;
    }

    private static double toroidalDist(int a, int b, int size) {
        int d = Math.abs(a - b);
        return Math.min(d, size - d);
    }

    @NonNull @Override public String toString() {
        return String.format("ToroidalMatrix[%d×%d t=%.2f E=%.4f rms=%.4f]",
                rows, cols, time, energy, rmsField());
    }
}
