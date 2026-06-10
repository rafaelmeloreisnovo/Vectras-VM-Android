package com.vectras.vm.rafaelia;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SementeEvolutivaNucleus — Evolutionary seed AI nucleus (Image 17).
 *
 * <pre>
 * Modules:
 *   1. NUCLEO_EVOLUTIVO   — models, configs, rules, context, tasks
 *   2. CAMARA_ADAPTACAO   — state, mutex, rollback, policies, continuous evolution
 *   3. GOVERNANCA         — NIST, ISO/IEC, GDPR, LGPD, privacy, self-sufficiency
 *   4. DETECCAO_AMBIENTE  — OS, CPU, GPU, temperature, energy
 *   5. AVALIACAO_DECISAO  — context evaluation, routing, learn from outcome
 *   6. LOGS_AUDITORIA     — sections, cache, alert ledger
 *
 * Flow: INPUT → EXTRACAO → PROCESSO → DECISAO → SAIDA
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-SEMENTE
 */
public final class SementeEvolutivaNucleus {

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum GovernanceLaw { NIST, ISO_IEC, GDPR, LGPD, PRIVACY, SELF_SUFFICIENCY }

    public enum ArchMode { X86_64, ARM64, RISC_V, UNKNOWN }

    public enum FlowStage { INPUT, EXTRACAO, PROCESSO, DECISAO, SAIDA }

    public enum AdaptationPolicy { CONSERVADOR, MODERADO, AGRESSIVO, ROLLBACK }

    // ─── Governance record ────────────────────────────────────────────────────

    public static final class GovernanceProfile {
        public final boolean nist, isoIec, gdpr, lgpd, privacy, selfSufficiency;

        GovernanceProfile(boolean nist, boolean isoIec, boolean gdpr,
                          boolean lgpd, boolean privacy, boolean selfSufficiency) {
            this.nist = nist; this.isoIec = isoIec;
            this.gdpr = gdpr; this.lgpd = lgpd;
            this.privacy = privacy; this.selfSufficiency = selfSufficiency;
        }

        public boolean isCompliant() { return nist && isoIec && (gdpr || lgpd) && privacy; }

        public static GovernanceProfile strict() {
            return new GovernanceProfile(true, true, true, true, true, true);
        }

        @NonNull @Override
        public String toString() {
            return String.format("Gov[NIST=%b ISO=%b GDPR=%b LGPD=%b PRIV=%b SELF=%b]",
                    nist, isoIec, gdpr, lgpd, privacy, selfSufficiency);
        }
    }

    // ─── Environment detection ────────────────────────────────────────────────

    public static final class EnvironmentProfile {
        public final ArchMode arch;
        public final String   osName;
        public final int      cpuCount;
        public final boolean  hasGpu;
        public final double   estimatedEnergyJ; // joules per tick estimate

        EnvironmentProfile(ArchMode arch, String osName, int cpuCount,
                           boolean hasGpu, double energyJ) {
            this.arch = arch; this.osName = osName;
            this.cpuCount = cpuCount; this.hasGpu = hasGpu;
            this.estimatedEnergyJ = energyJ;
        }

        @NonNull @Override
        public String toString() {
            return String.format("Env[%s/%s cpus=%d gpu=%b energy=%.4fJ]",
                    arch, osName, cpuCount, hasGpu, estimatedEnergyJ);
        }
    }

    // ─── Audit entry ──────────────────────────────────────────────────────────

    public static final class AuditLog {
        public final long   tsNs;
        public final String section;
        public final String message;
        AuditLog(String section, String message) {
            this.tsNs = System.nanoTime();
            this.section = section;
            this.message = message;
        }
    }

    // ─── Nucleus state ────────────────────────────────────────────────────────

    private final GovernanceProfile  governance;
    private final EnvironmentProfile environment;
    private volatile AdaptationPolicy policy        = AdaptationPolicy.MODERADO;
    private volatile FlowStage        flowStage     = FlowStage.INPUT;
    private volatile double           coherenceState = 0.0;
    private volatile int              generation     = 0;
    private volatile boolean          rollbackActive = false;

    private final List<AuditLog>  logs       = new ArrayList<>();
    private final AtomicInteger   decisions  = new AtomicInteger(0);

    // Snapshot for rollback
    private volatile double       snapshotCoherence = 0.0;

    private SementeEvolutivaNucleus(GovernanceProfile governance, EnvironmentProfile environment) {
        this.governance  = governance;
        this.environment = environment;
        log("INIT", "SementeEvolutivaNucleus created arch=" + environment.arch);
    }

    public static SementeEvolutivaNucleus create() {
        return new SementeEvolutivaNucleus(GovernanceProfile.strict(), detectEnvironment());
    }

    public static SementeEvolutivaNucleus create(GovernanceProfile gov, EnvironmentProfile env) {
        return new SementeEvolutivaNucleus(gov, env);
    }

    // ─── Main flow ────────────────────────────────────────────────────────────

    /**
     * Process one input through the full pipeline: INPUT → EXTRACAO → PROCESSO → DECISAO → SAIDA.
     * @param rawInput raw observation value [0..1]
     * @return processed coherence output [0..1]
     */
    public synchronized double process(double rawInput) {
        double v = Math.max(0.0, Math.min(1.0, rawInput));

        flowStage = FlowStage.INPUT;
        snapshotCoherence = coherenceState; // capture rollback point

        flowStage = FlowStage.EXTRACAO;
        double extracted = extrair(v);

        flowStage = FlowStage.PROCESSO;
        double processed = processar(extracted);

        flowStage = FlowStage.DECISAO;
        double decided = decidir(processed);

        flowStage = FlowStage.SAIDA;
        coherenceState = decided;
        generation++;
        decisions.incrementAndGet();
        log("PROCESS", String.format("gen=%d in=%.3f out=%.3f", generation, rawInput, decided));
        return decided;
    }

    private double extrair(double v) {
        // Extract invariants: φ-weighted signal
        return v * RafaeliaKernelV22.PHI / (RafaeliaKernelV22.PHI + 1.0);
    }

    private double processar(double v) {
        // Apply adaptation policy
        return switch (policy) {
            case CONSERVADOR -> v * RafaeliaKernelV22.SPIRAL;
            case MODERADO    -> v * (RafaeliaKernelV22.PHI - 1.0) + coherenceState * (2.0 - RafaeliaKernelV22.PHI);
            case AGRESSIVO   -> Math.min(1.0, v * RafaeliaKernelV22.PHI);
            case ROLLBACK    -> snapshotCoherence; // revert
        };
    }

    private double decidir(double v) {
        if (!governance.isCompliant()) {
            log("DECISION", "Governance violation — rollback");
            rollbackActive = true;
            policy = AdaptationPolicy.ROLLBACK;
            return snapshotCoherence;
        }
        rollbackActive = false;
        return Math.max(0.0, Math.min(1.0, v));
    }

    // ─── Adaptation ───────────────────────────────────────────────────────────

    public void setPolicy(@NonNull AdaptationPolicy p) {
        policy = p;
        log("POLICY", "Policy set to " + p);
    }

    // ─── Audit ────────────────────────────────────────────────────────────────

    private synchronized void log(String section, String message) {
        logs.add(new AuditLog(section, message));
    }

    @NonNull
    public synchronized List<AuditLog> logs() {
        return Collections.unmodifiableList(new ArrayList<>(logs));
    }

    // ─── Environment detection ────────────────────────────────────────────────

    static EnvironmentProfile detectEnvironment() {
        String osArch = System.getProperty("os.arch", "").toLowerCase();
        ArchMode arch = osArch.contains("aarch64") || osArch.contains("arm64") ? ArchMode.ARM64 :
                        osArch.contains("riscv")                                ? ArchMode.RISC_V :
                        osArch.contains("x86_64") || osArch.contains("amd64")  ? ArchMode.X86_64 :
                        ArchMode.UNKNOWN;
        int cpus = Runtime.getRuntime().availableProcessors();
        return new EnvironmentProfile(arch, System.getProperty("os.name", "unknown"),
                cpus, false, 0.001 * cpus);
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public GovernanceProfile  governance()     { return governance; }
    public EnvironmentProfile environment()    { return environment; }
    public FlowStage          flowStage()      { return flowStage; }
    public AdaptationPolicy   policy()         { return policy; }
    public double             coherenceState() { return coherenceState; }
    public int                generation()     { return generation; }
    public boolean            rollbackActive() { return rollbackActive; }
    public int                decisions()      { return decisions.get(); }

    @NonNull @Override
    public String toString() {
        return String.format("Semente[gen=%d coh=%.3f policy=%s flow=%s gov=%b]",
                generation, coherenceState, policy, flowStage, governance.isCompliant());
    }
}
