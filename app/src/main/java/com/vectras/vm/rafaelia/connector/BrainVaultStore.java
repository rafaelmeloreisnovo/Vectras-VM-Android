package com.vectras.vm.rafaelia.connector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32C;

/**
 * Brain Vault Store — DB/BRAIN_VAULT/AUTOAPREND connector.
 *
 * <p>Persistent memory and auto-learning store with three tiers:
 * <pre>
 *   HOT   : In-memory LRU map (≤ HOT_CAPACITY entries, instant access)
 *   WARM  : On-disk JSONL log (append-only, rotated at WARM_MAX_BYTES)
 *   COLD  : Archived JSONL (kept for continuity and auto-learn replay)
 * </pre>
 *
 * <p>Auto-learn: every recall increments the hit-count; entries above
 * LEARN_THRESHOLD are promoted to HOT and flagged as "learned".
 *
 * <p>Entry format (JSONL line):
 * <pre>
 *   {"id":..., "key":..., "value":..., "category":...,
 *    "hits":..., "learned":..., "tsMs":..., "crc32c":...}
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-BRAINVAULT
 */
public final class BrainVaultStore {

    static final int  HOT_CAPACITY    = 512;
    static final long WARM_MAX_BYTES  = 8L * 1024 * 1024;   // 8 MB
    static final int  LEARN_THRESHOLD = 3;                   // hits before "learned"

    private static final ThreadLocal<CRC32C> CRC_POOL = ThreadLocal.withInitial(CRC32C::new);

    private final Map<String, Entry>      hot;
    private final File                    warmFile;
    private final File                    coldFile;
    private final AtomicLong              idSeq    = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentHashMap<String, Entry> index = new ConcurrentHashMap<>();

    private BrainVaultStore(File dir) throws IOException {
        dir.mkdirs();
        warmFile = new File(dir, "brainvault.jsonl");
        coldFile = new File(dir, "brainvault.cold.jsonl");
        hot = Collections.synchronizedMap(new LinkedHashMap<>(HOT_CAPACITY, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, BrainVaultStore.Entry> eldest) {
                return size() > HOT_CAPACITY;
            }
        });
        replayWarm();
    }

    public static BrainVaultStore open(@NonNull File dir) throws IOException {
        return new BrainVaultStore(dir);
    }

    // ─── Store ────────────────────────────────────────────────────────────────

    /** Store a key-value pair in the vault. Overwrites existing entry for key. */
    public synchronized void store(@NonNull String key, @NonNull String value,
                                   @NonNull String category) throws IOException {
        Entry e = new Entry(idSeq.incrementAndGet(), key, value, category,
                0, false, System.currentTimeMillis());
        hot.put(key, e);
        index.put(key, e);
        appendToWarm(e);
        maybeRotate();
    }

    // ─── Recall ───────────────────────────────────────────────────────────────

    /** Recall a value by key, incrementing hit count and applying auto-learn. */
    @Nullable
    public synchronized Entry recall(@NonNull String key) throws IOException {
        Entry e = index.get(key);
        if (e == null) return null;
        e = e.withHit();
        hot.put(key, e);
        index.put(key, e);
        if (e.hits >= LEARN_THRESHOLD && !e.learned) {
            e = e.withLearned();
            hot.put(key, e);
            index.put(key, e);
        }
        appendToWarm(e);
        return e;
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    /** Return all entries matching a category. */
    @NonNull
    public List<Entry> queryByCategory(@NonNull String category) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : index.values()) {
            if (category.equals(e.category)) result.add(e);
        }
        result.sort((a, b) -> Long.compare(b.hits, a.hits)); // most-hit first
        return Collections.unmodifiableList(result);
    }

    /** Return all "learned" entries. */
    @NonNull
    public List<Entry> queryLearned() {
        List<Entry> result = new ArrayList<>();
        for (Entry e : index.values()) {
            if (e.learned) result.add(e);
        }
        return Collections.unmodifiableList(result);
    }

    public int totalEntries() { return index.size(); }
    public int hotEntries()   { return hot.size(); }

    // ─── Replay ───────────────────────────────────────────────────────────────

    private void replayWarm() throws IOException {
        if (!warmFile.exists()) return;
        List<String> lines = Files.readAllLines(warmFile.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank()) continue;
            try {
                Entry e = Entry.fromJson(new JSONObject(line));
                index.put(e.key, e);
                hot.put(e.key, e);
            } catch (JSONException ignored) { /* skip corrupt lines */ }
        }
    }

    // ─── WARM file I/O ────────────────────────────────────────────────────────

    private void appendToWarm(Entry e) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(
                new FileWriter(warmFile, StandardCharsets.UTF_8, true))) {
            bw.write(e.toJsonLine());
            bw.newLine();
        }
    }

    private void maybeRotate() throws IOException {
        if (warmFile.length() > WARM_MAX_BYTES) {
            if (coldFile.exists()) coldFile.delete();
            warmFile.renameTo(coldFile);
        }
    }

    // ─── Data type ────────────────────────────────────────────────────────────

    public static final class Entry {
        public final long   id;
        public final String key;
        public final String value;
        public final String category;
        public final long   hits;
        public final boolean learned;
        public final long   tsMs;
        public final long   crc32c;

        Entry(long id, String key, String value, String category,
              long hits, boolean learned, long tsMs) {
            this.id       = id;
            this.key      = key;
            this.value    = value;
            this.category = category;
            this.hits     = hits;
            this.learned  = learned;
            this.tsMs     = tsMs;
            this.crc32c   = computeCrc(key + value + category);
        }

        Entry withHit()    { return new Entry(id, key, value, category, hits + 1, learned, tsMs); }
        Entry withLearned(){ return new Entry(id, key, value, category, hits, true, tsMs); }

        @NonNull
        String toJsonLine() throws JSONException {
            return toJson().toString();
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id",       id);
            o.put("key",      key);
            o.put("value",    value);
            o.put("category", category);
            o.put("hits",     hits);
            o.put("learned",  learned);
            o.put("tsMs",     tsMs);
            o.put("crc32c",   crc32c);
            return o;
        }

        @NonNull
        static Entry fromJson(JSONObject o) throws JSONException {
            return new Entry(
                    o.getLong("id"),
                    o.getString("key"),
                    o.getString("value"),
                    o.getString("category"),
                    o.getLong("hits"),
                    o.getBoolean("learned"),
                    o.getLong("tsMs")
            );
        }

        private static long computeCrc(String text) {
            CRC32C crc = new CRC32C();
            crc.update(text.getBytes(StandardCharsets.UTF_8));
            return crc.getValue();
        }

        @NonNull @Override public String toString() {
            return "Entry[" + id + "|" + key + "|cat=" + category
                    + "|hits=" + hits + "|learned=" + learned + "]";
        }
    }
}
