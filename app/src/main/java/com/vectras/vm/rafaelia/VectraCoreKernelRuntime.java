package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VectraCoreKernelRuntime — VECTRAS-VM-ANDROID deterministic runtime (Image 15).
 *
 * <p>Core update formula:
 * <pre>
 *   R(t) = R(t−1) + Φ_ethics × E × √(3/2) × t_b
 * </pre>
 * where:
 * <ul>
 *   <li>R(t)      = current runtime state coherence</li>
 *   <li>Φ_ethics  = ethical gate value (0..1, calibrated to 0.875)</li>
 *   <li>E         = energy input (event significance)</li>
 *   <li>√(3/2)    = SPIRAL = RafaeliaKernelV22.SPIRAL</li>
 *   <li>t_b       = time-base tick (normalized [0,1])</li>
 * </ul>
 *
 * <p>Pipeline: ψ × P → Δ → Σ → Ω
 *
 * <p>Multi-arch detection: X86_64, ARM64, RISC_V.
 * SPLITBUFF: circular ring buffer for parallel event processing.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-VECTRACORE
 */
public final class VectraCoreKernelRuntime {

    // ─── Constants ────────────────────────────────────────────────────────────
    public static final double PHI_ETHICS_DEFAULT = 0.875;
    public static final int    AUDIT_CAPACITY     = 1024;
    public static final int    SPLITBUFF_SIZE     = 64;

    // ─── Architecture ─────────────────────────────────────────────────────────

    public enum Arch { X86_64, ARM64, RISC_V, UNKNOWN }

    public enum PipelinePhase { PSI, DELTA, SIGMA, OMEGA }

    // ─── Audit entry ──────────────────────────────────────────────────────────

    public static final class AuditEntry {
        public final long   seqNo;
        public final long   tsNs;
        public final String tag;
        public final String verdict;
        public final double rValue;

        AuditEntry(long seq, long tsNs, String tag, String verdict, double rValue) {
            this.seqNo   = seq;
            this.tag     = tag;
            this.tsNs    = tsNs;
            this.verdict = verdict;
            this.rValue  = rValue;
        }

        @NonNull @Override
        public String toString() {
            return String.format("Audit[%d tag=%s verdict=%s R=%.4f]", seqNo, tag, verdict, rValue);
        }
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private volatile double      r          = 0.0;  // current runtime coherence
    private volatile double      phiEthics;
    private volatile PipelinePhase phase    = PipelinePhase.PSI;
    private final AtomicLong     seqNo      = new AtomicLong(0);
    private final AtomicLong     tickCount  = new AtomicLong(0);
    private final Arch           arch;

    private final List<AuditEntry> auditLedger  = new ArrayList<>(AUDIT_CAPACITY);
    private final Deque<Double>    splitBuff     = new ArrayDeque<>(SPLITBUFF_SIZE);

    private VectraCoreKernelRuntime(double phiEthics, Arch arch) {
        this.phiEthics = Math.max(0.0, Math.min(1.0, phiEthics));
        this.arch      = arch;
    }

    public static VectraCoreKernelRuntime create() {
        return new VectraCoreKernelRuntime(PHI_ETHICS_DEFAULT, detectArch());
    }

    public static VectraCoreKernelRuntime create(double phiEthics, Arch arch) {
        return new VectraCoreKernelRuntime(phiEthics, arch);
    }

    // ─── Core update ──────────────────────────────────────────────────────────

    /**
     * R(t) = R(t−1) + Φ_ethics × E × SPIRAL × t_b
     *
     * @param energy  event significance [0..1]
     * @param timeTick normalized time-base tick [0..1]
     * @return new R(t), clamped to [0,1]
     */
    public synchronized double update(double energy, double timeTick) {
        double e  = Math.max(0.0, Math.min(1.0, energy));
        double tb = Math.max(0.0, Math.min(1.0, timeTick));
        r = Math.max(0.0, Math.min(1.0, r + phiEthics * e * RafaeliaKernelV22.SPIRAL * tb));
        advancePipeline();
        tickCount.incrementAndGet();
        pushSplitBuff(r);
        return r;
    }

    // ─── Pipeline ─────────────────────────────────────────────────────────────
    // ψ × P → Δ → Σ → Ω (4 phases, cycling)

    private void advancePipeline() {
        phase = switch (phase) {
            case PSI   -> PipelinePhase.DELTA;
            case DELTA -> PipelinePhase.SIGMA;
            case SIGMA -> PipelinePhase.OMEGA;
            case OMEGA -> PipelinePhase.PSI;
        };
    }

    // ─── Audit ledger ─────────────────────────────────────────────────────────

    public synchronized void audit(@NonNull String tag, @NonNull String verdict) {
        if (auditLedger.size() >= AUDIT_CAPACITY) {
            auditLedger.remove(0);
        }
        auditLedger.add(new AuditEntry(seqNo.incrementAndGet(),
                System.nanoTime(), tag, verdict, r));
    }

    @NonNull
    public synchronized List<AuditEntry> auditLedger() {
        return Collections.unmodifiableList(new ArrayList<>(auditLedger));
    }

    // ─── SPLITBUFF ────────────────────────────────────────────────────────────

    private synchronized void pushSplitBuff(double value) {
        if (splitBuff.size() >= SPLITBUFF_SIZE) splitBuff.pollFirst();
        splitBuff.addLast(value);
    }

    /** Mean of the SPLITBUFF sliding window (parallel event coherence average). */
    public synchronized double splitBuffMean() {
        if (splitBuff.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : splitBuff) sum += v;
        return sum / splitBuff.size();
    }

    // ─── Architecture detection ───────────────────────────────────────────────

    static Arch detectArch() {
        String os = System.getProperty("os.arch", "");
        if (os.contains("aarch64") || os.contains("arm64")) return Arch.ARM64;
        if (os.contains("x86_64") || os.contains("amd64"))  return Arch.X86_64;
        if (os.contains("riscv"))                            return Arch.RISC_V;
        return Arch.UNKNOWN;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public double          r()          { return r; }
    public double          phiEthics()  { return phiEthics; }
    public void            setPhiEthics(double v) { phiEthics = Math.max(0.0, Math.min(1.0, v)); }
    public PipelinePhase   phase()      { return phase; }
    public Arch            arch()       { return arch; }
    public long            tickCount()  { return tickCount.get(); }

    @NonNull @Override
    public String toString() {
        return String.format("VectraRuntime[R=%.4f φ=%.3f phase=%s arch=%s ticks=%d]",
                r, phiEthics, phase, arch, tickCount.get());
    }
}
