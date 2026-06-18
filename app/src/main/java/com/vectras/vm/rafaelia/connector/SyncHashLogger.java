package com.vectras.vm.rafaelia.connector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32C;

/**
 * Sync Hash Logger — LOGS/HAJA/SYNC_HASH connector.
 *
 * <p>Implements the temporal-track logging layer with hash-chaining for integrity.
 * Each log entry is hash-chained to its predecessor (SYNC_HASH), providing a
 * tamper-evident append-only ledger.
 *
 * <p>Entry format (JSONL):
 * <pre>
 *   {"seq":N, "tsNs":T, "level":"INFO", "tag":"TAG", "msg":"...",
 *    "payload":{...}, "prevHash":"HEX", "selfHash":"HEX", "crc32c":N}
 * </pre>
 *
 * <p>"HAJA" philosophy: "Act as you know — the system that needs no permission."
 * Logging is unconditional and always-on; the chain cannot be silenced.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-SYNCLOG
 */
public final class SyncHashLogger {

    public enum Level { DEBUG, INFO, WARN, ERROR, FATAL }

    static final int  RING_CAPACITY  = 4_096;
    static final long MAX_FILE_BYTES = 16L * 1024 * 1024;  // 16 MB

    private static final ThreadLocal<CRC32C> CRC_POOL = ThreadLocal.withInitial(CRC32C::new);
    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    private final AtomicLong         seq       = new AtomicLong(0);
    private final Deque<LogEntry>    ring      = new ArrayDeque<>(RING_CAPACITY);
    private final File               logFile;
    private volatile String          prevHash  = GENESIS_HASH;

    private SyncHashLogger(File file) {
        this.logFile = file;
    }

    public static SyncHashLogger open(@NonNull File dir, @NonNull String tag) throws IOException {
        dir.mkdirs();
        return new SyncHashLogger(new File(dir, "haja_sync_" + tag + ".jsonl"));
    }

    // ─── Log API ─────────────────────────────────────────────────────────────

    public void debug(@NonNull String tag, @NonNull String msg) throws IOException {
        write(Level.DEBUG, tag, msg, null);
    }

    public void info(@NonNull String tag, @NonNull String msg) throws IOException {
        write(Level.INFO, tag, msg, null);
    }

    public void info(@NonNull String tag, @NonNull String msg,
                     @Nullable JSONObject payload) throws IOException {
        write(Level.INFO, tag, msg, payload);
    }

    public void warn(@NonNull String tag, @NonNull String msg) throws IOException {
        write(Level.WARN, tag, msg, null);
    }

    public void error(@NonNull String tag, @NonNull String msg) throws IOException {
        write(Level.ERROR, tag, msg, null);
    }

    public void error(@NonNull String tag, @NonNull String msg,
                      @Nullable Throwable t) throws IOException {
        JSONObject p = null;
        if (t != null) {
            try {
                p = new JSONObject();
                p.put("exception", t.getClass().getName());
                p.put("message", t.getMessage());
            } catch (JSONException ignored) {}
        }
        write(Level.ERROR, tag, msg, p);
    }

    public void fatal(@NonNull String tag, @NonNull String msg) throws IOException {
        write(Level.FATAL, tag, msg, null);
    }

    // ─── Chain integrity check ────────────────────────────────────────────────

    /**
     * Verify the hash chain of the in-memory ring buffer.
     * Returns true if every entry's selfHash matches the computed hash of its content.
     */
    public synchronized boolean verifyChain() {
        String prev = GENESIS_HASH;
        for (LogEntry e : ring) {
            String expected = computeHash(e.seq, e.tsNs, e.level, e.tag, e.msg, prev);
            if (!expected.equals(e.selfHash)) return false;
            prev = e.selfHash;
        }
        return true;
    }

    /** Return a snapshot of the most-recent entries (up to n). */
    @NonNull
    public synchronized java.util.List<LogEntry> tail(int n) {
        LogEntry[] arr = ring.toArray(new LogEntry[0]);
        int start = Math.max(0, arr.length - n);
        java.util.List<LogEntry> result = new java.util.ArrayList<>(n);
        for (int i = start; i < arr.length; i++) result.add(arr[i]);
        return Collections.unmodifiableList(result);
    }

    public long getEntryCount() { return seq.get(); }

    // ─── Internal write ───────────────────────────────────────────────────────

    private synchronized void write(Level level, String tag, String msg,
                                     @Nullable JSONObject payload) throws IOException {
        long s  = seq.incrementAndGet();
        long ts = System.nanoTime();
        String self = computeHash(s, ts, level, tag, msg, prevHash);
        long crc = crc32c((s + ts + level.name() + tag + msg + self).getBytes(StandardCharsets.UTF_8));

        LogEntry entry = new LogEntry(s, ts, level, tag, msg, payload, prevHash, self, crc);

        if (ring.size() >= RING_CAPACITY) ring.pollFirst();
        ring.addLast(entry);
        prevHash = self;

        appendFile(entry);
    }

    private void appendFile(LogEntry e) throws IOException {
        if (logFile.length() > MAX_FILE_BYTES) rotate();
        try (BufferedWriter bw = new BufferedWriter(
                new FileWriter(logFile, StandardCharsets.UTF_8, true))) {
            bw.write(e.toJsonLine());
            bw.newLine();
        }
    }

    private void rotate() {
        File old = new File(logFile.getParent(), logFile.getName() + ".prev");
        if (old.exists()) old.delete();
        logFile.renameTo(old);
    }

    // ─── Hash computation (FNV-1a inspired + hex) ────────────────────────────

    public static String computeHash(long seq, long tsNs, Level level, String tag,
                                     String msg, String prevHash) {
        String input = seq + "|" + tsNs + "|" + level + "|" + tag + "|" + msg + "|" + prevHash;
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        long h1 = 0xCBF29CE484222325L;
        long h2 = 0x517CC1B727220A95L;
        for (byte b : bytes) {
            h1 = (h1 ^ b) * 0x00000100000001B3L;
            h2 = (h2 ^ b) * 0x00000100000001B3L + 0x9E3779B97F4A7C15L;
        }
        return String.format("%016x%016x%016x%016x", h1, h2, h1 ^ h2, ~(h1 + h2));
    }

    private static long crc32c(byte[] data) {
        CRC32C crc = CRC_POOL.get();
        crc.reset();
        crc.update(data);
        return crc.getValue();
    }

    // ─── Data type ────────────────────────────────────────────────────────────

    public static final class LogEntry {
        public final long   seq;
        public final long   tsNs;
        public final Level  level;
        public final String tag;
        public final String msg;
        public final @Nullable JSONObject payload;
        public final String prevHash;
        public final String selfHash;
        public final long   crc32c;

        LogEntry(long seq, long tsNs, Level level, String tag, String msg,
                 @Nullable JSONObject payload, String prevHash, String selfHash, long crc32c) {
            this.seq      = seq;
            this.tsNs     = tsNs;
            this.level    = level;
            this.tag      = tag;
            this.msg      = msg;
            this.payload  = payload;
            this.prevHash = prevHash;
            this.selfHash = selfHash;
            this.crc32c   = crc32c;
        }

        @NonNull
        public String toJsonLine() {
            try {
                JSONObject o = new JSONObject();
                o.put("seq",      seq);
                o.put("tsNs",     tsNs);
                o.put("level",    level.name());
                o.put("tag",      tag);
                o.put("msg",      msg);
                if (payload != null) o.put("payload", payload);
                o.put("prevHash", prevHash);
                o.put("selfHash", selfHash);
                o.put("crc32c",   crc32c);
                return o.toString();
            } catch (JSONException e) {
                return "{\"error\":\"serialization failed\"}";
            }
        }

        @NonNull @Override public String toString() {
            return String.format("[%d|%s|%s] %s", seq, level, tag, msg);
        }
    }
}
