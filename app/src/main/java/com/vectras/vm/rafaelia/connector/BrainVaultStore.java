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

    private final Map<String, Entry> hot;
    private final File warmFile;
    private final File coldFile;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());
    private final ConcurrentHashMap<String, Entry> index = new ConcurrentHashMap<>();

    private BrainVaultStore(File dir) throws IOException {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create Brain Vault directory: " + dir);
        }
        if (!dir.isDirectory()) {
            throw new IOException("Brain Vault path is not a directory: " + dir);
        }
        warmFile = new File(dir, "brainvault.jsonl");
        coldFile = new File(dir, "brainvault.cold.jsonl");
        hot = Collections.synchronizedMap(new LinkedHashMap<>(HOT_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BrainVaultStore.Entry> eldest) {
                return size() > HOT_CAPACITY;
            }
        });
        replayWarm();
    }

    public static BrainVaultStore open(@NonNull File dir) throws IOException {
        return new BrainVaultStore(dir);
    }

    // ─── Store ────────────────────────────────────────────────────────────────

    /**
     * Store a key-value pair in the vault. Overwrites the current in-memory
     * projection only after the append-only record has been persisted.
     */
    public synchronized void store(@NonNull String key, @NonNull String value,
                                   @NonNull String category) throws IOException {
        Entry entry = new Entry(idSeq.incrementAndGet(), key, value, category,
                0, false, System.currentTimeMillis());
        rotateBeforeAppendIfNeeded();
        appendToWarm(entry);
        hot.put(key, entry);
        index.put(key, entry);
    }

    // ─── Recall ───────────────────────────────────────────────────────────────

    /** Recall a value by key, incrementing hit count and applying auto-learn. */
    @Nullable
    public synchronized Entry recall(@NonNull String key) throws IOException {
        Entry current = index.get(key);
        if (current == null) {
            return null;
        }

        Entry updated = current.withHit();
        if (updated.hits >= LEARN_THRESHOLD && !updated.learned) {
            updated = updated.withLearned();
        }

        rotateBeforeAppendIfNeeded();
        appendToWarm(updated);
        hot.put(key, updated);
        index.put(key, updated);
        return updated;
    }

    // ─── Query ────────────────────────────────────────────────────────────────

    /** Return all entries matching a category. */
    @NonNull
    public List<Entry> queryByCategory(@NonNull String category) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : index.values()) {
            if (category.equals(entry.category)) {
                result.add(entry);
            }
        }
        result.sort((left, right) -> Long.compare(right.hits, left.hits));
        return Collections.unmodifiableList(result);
    }

    /** Return all "learned" entries. */
    @NonNull
    public List<Entry> queryLearned() {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : index.values()) {
            if (entry.learned) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int totalEntries() {
        return index.size();
    }

    public int hotEntries() {
        return hot.size();
    }

    // ─── Replay ───────────────────────────────────────────────────────────────

    private void replayWarm() throws IOException {
        if (!warmFile.exists()) {
            return;
        }
        if (!warmFile.isFile()) {
            throw new IOException("Brain Vault WARM path is not a regular file: " + warmFile);
        }

        List<String> lines = Files.readAllLines(warmFile.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Entry entry = Entry.fromJson(new JSONObject(line));
                index.put(entry.key, entry);
                hot.put(entry.key, entry);
                idSeq.accumulateAndGet(entry.id, Math::max);
            } catch (JSONException ignored) {
                // Fail closed for this record: malformed or CRC-invalid lines do not
                // enter the current projection. The original append-only bytes remain.
            }
        }
    }

    // ─── WARM file I/O ────────────────────────────────────────────────────────

    private void appendToWarm(@NonNull Entry entry) throws IOException {
        final String jsonLine;
        try {
            // Serialize before opening the append stream. A serialization failure
            // therefore cannot create an empty or partial JSONL record.
            jsonLine = entry.toJsonLine();
        } catch (JSONException exception) {
            throw new IOException(
                    "Unable to serialize Brain Vault entry id=" + entry.id,
                    exception
            );
        }

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(warmFile, StandardCharsets.UTF_8, true))) {
            writer.write(jsonLine);
            writer.newLine();
        }
    }

    private void rotateBeforeAppendIfNeeded() throws IOException {
        if (!warmFile.exists() || warmFile.length() <= WARM_MAX_BYTES) {
            return;
        }
        if (!warmFile.isFile()) {
            throw new IOException("Brain Vault WARM path is not a regular file: " + warmFile);
        }
        if (coldFile.exists() && !coldFile.delete()) {
            throw new IOException("Unable to replace Brain Vault COLD archive: " + coldFile);
        }
        if (!warmFile.renameTo(coldFile)) {
            throw new IOException("Unable to rotate Brain Vault WARM log to: " + coldFile);
        }
    }

    // ─── Data type ────────────────────────────────────────────────────────────

    public static final class Entry {
        public final long id;
        public final String key;
        public final String value;
        public final String category;
        public final long hits;
        public final boolean learned;
        public final long tsMs;
        public final long crc32c;

        Entry(long id, String key, String value, String category,
              long hits, boolean learned, long tsMs) {
            this.id = id;
            this.key = key;
            this.value = value;
            this.category = category;
            this.hits = hits;
            this.learned = learned;
            this.tsMs = tsMs;
            this.crc32c = computeCrc(key + value + category);
        }

        Entry withHit() {
            return new Entry(id, key, value, category, hits + 1, learned, tsMs);
        }

        Entry withLearned() {
            return new Entry(id, key, value, category, hits, true, tsMs);
        }

        @NonNull
        String toJsonLine() throws JSONException {
            return toJson().toString();
        }

        @NonNull
        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("id", id);
            object.put("key", key);
            object.put("value", value);
            object.put("category", category);
            object.put("hits", hits);
            object.put("learned", learned);
            object.put("tsMs", tsMs);
            object.put("crc32c", crc32c);
            return object;
        }

        @NonNull
        static Entry fromJson(JSONObject object) throws JSONException {
            long persistedCrc = object.getLong("crc32c");
            Entry entry = new Entry(
                    object.getLong("id"),
                    object.getString("key"),
                    object.getString("value"),
                    object.getString("category"),
                    object.getLong("hits"),
                    object.getBoolean("learned"),
                    object.getLong("tsMs")
            );
            if (entry.crc32c != persistedCrc) {
                throw new JSONException(
                        "CRC32C mismatch for Brain Vault entry id=" + entry.id
                );
            }
            return entry;
        }

        private static long computeCrc(String text) {
            CRC32C crc = CRC_POOL.get();
            crc.reset();
            crc.update(text.getBytes(StandardCharsets.UTF_8));
            return crc.getValue();
        }

        @NonNull
        @Override
        public String toString() {
            return "Entry[" + id + "|" + key + "|cat=" + category
                    + "|hits=" + hits + "|learned=" + learned + "]";
        }
    }
}
