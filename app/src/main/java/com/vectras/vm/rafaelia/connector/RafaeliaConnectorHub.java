package com.vectras.vm.rafaelia.connector;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

/**
 * RAFAELIA Connector Hub — central orchestrator for all 6 connector families.
 *
 * <p>Connector map (from RAFAELIA architecture Image 8):
 * <pre>
 *   01. JSON / JSON600 / JSON800    → JsonExportConnector
 *   02. CHUNKS / CONVERSATIONS      → ChunkConversationManager
 *   03. DB / BRAIN_VAULT / AUTOAPREND → BrainVaultStore
 *   04. ZIPRAF / SHARDS / CORE      → ZiprafCore (static)
 *   05. ZRF / PRIVACY / KERNEL      → PrivacyKernelZrf
 *   06. LOGS / HAJA / SYNC_HASH     → SyncHashLogger
 * </pre>
 *
 * <p>All connectors are lazy-initialized on first access.
 * The hub uses a base directory under {@code context.getFilesDir()/rafaelia/}.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-HUB
 */
public final class RafaeliaConnectorHub {

    private static volatile RafaeliaConnectorHub INSTANCE;

    private final File baseDir;

    // Lazy connector references
    private volatile JsonExportConnector      jsonExport;
    private volatile ChunkConversationManager chunkManager;
    private volatile BrainVaultStore          brainVault;
    private volatile PrivacyKernelZrf         privacyKernel;
    private volatile SyncHashLogger           syncLogger;

    private RafaeliaConnectorHub(@NonNull File baseDir) throws IOException {
        this.baseDir = baseDir;
        baseDir.mkdirs();
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    public static RafaeliaConnectorHub getInstance(@NonNull Context context) throws IOException {
        if (INSTANCE == null) {
            synchronized (RafaeliaConnectorHub.class) {
                if (INSTANCE == null) {
                    File base = new File(context.getFilesDir(), "rafaelia/connectors");
                    INSTANCE = new RafaeliaConnectorHub(base);
                }
            }
        }
        return INSTANCE;
    }

    /** For testing: create with explicit directory. */
    public static RafaeliaConnectorHub createForDir(@NonNull File dir) throws IOException {
        return new RafaeliaConnectorHub(dir);
    }

    // ─── Connector accessors (lazy init, thread-safe DCL) ────────────────────

    @NonNull
    public JsonExportConnector json() {
        if (jsonExport == null) {
            synchronized (this) {
                if (jsonExport == null) {
                    jsonExport = JsonExportConnector.create(new File(baseDir, "json"));
                }
            }
        }
        return jsonExport;
    }

    @NonNull
    public ChunkConversationManager chunks() {
        if (chunkManager == null) {
            synchronized (this) {
                if (chunkManager == null) {
                    chunkManager = ChunkConversationManager.create();
                }
            }
        }
        return chunkManager;
    }

    @NonNull
    public BrainVaultStore brainVault() throws IOException {
        if (brainVault == null) {
            synchronized (this) {
                if (brainVault == null) {
                    brainVault = BrainVaultStore.open(new File(baseDir, "brainvault"));
                }
            }
        }
        return brainVault;
    }

    /**
     * Returns the ZiprafCore class object for static-method access.
     * ZiprafCore is all-static; callers use ZiprafCore.pack()/unpack() directly.
     */
    @NonNull
    public Class<ZiprafCore> ziprafClass() { return ZiprafCore.class; }

    @NonNull
    public PrivacyKernelZrf privacy() {
        if (privacyKernel == null) {
            synchronized (this) {
                if (privacyKernel == null) {
                    privacyKernel = PrivacyKernelZrf.createEphemeral();
                }
            }
        }
        return privacyKernel;
    }

    @NonNull
    public SyncHashLogger logger() throws IOException {
        if (syncLogger == null) {
            synchronized (this) {
                if (syncLogger == null) {
                    syncLogger = SyncHashLogger.open(new File(baseDir, "logs"), "main");
                }
            }
        }
        return syncLogger;
    }

    // ─── Convenience: log + chain verify ─────────────────────────────────────

    public void logInfo(@NonNull String tag, @NonNull String msg) {
        try {
            logger().info(tag, msg);
        } catch (IOException e) {
            android.util.Log.e("RafaeliaHub", "log failed: " + e.getMessage());
        }
    }

    public boolean isChainIntact() {
        try {
            return logger().verifyChain();
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Hub health check ─────────────────────────────────────────────────────

    @NonNull
    public HubHealthReport checkHealth() {
        boolean jsonOk    = jsonExport  != null || new File(baseDir, "json").canWrite()
                || !new File(baseDir, "json").exists();
        boolean chunksOk  = true; // in-memory
        boolean vaultOk;
        boolean privacyOk = privacyKernel != null;
        boolean loggerOk;
        boolean chainOk   = false;

        try {
            brainVault();
            vaultOk = true;
        } catch (IOException e) {
            vaultOk = false;
        }

        try {
            logger();
            loggerOk = true;
            chainOk  = syncLogger.verifyChain();
        } catch (IOException e) {
            loggerOk = false;
        }

        return new HubHealthReport(jsonOk, chunksOk, vaultOk, privacyOk, loggerOk, chainOk);
    }

    public static final class HubHealthReport {
        public final boolean jsonOk, chunksOk, vaultOk, privacyOk, loggerOk, chainOk;

        HubHealthReport(boolean jsonOk, boolean chunksOk, boolean vaultOk,
                        boolean privacyOk, boolean loggerOk, boolean chainOk) {
            this.jsonOk    = jsonOk;
            this.chunksOk  = chunksOk;
            this.vaultOk   = vaultOk;
            this.privacyOk = privacyOk;
            this.loggerOk  = loggerOk;
            this.chainOk   = chainOk;
        }

        public boolean allOk() {
            return jsonOk && chunksOk && vaultOk && privacyOk && loggerOk;
        }

        @NonNull @Override public String toString() {
            return String.format(
                    "HubHealth[json=%b chunks=%b vault=%b privacy=%b log=%b chain=%b]",
                    jsonOk, chunksOk, vaultOk, privacyOk, loggerOk, chainOk);
        }
    }
}
