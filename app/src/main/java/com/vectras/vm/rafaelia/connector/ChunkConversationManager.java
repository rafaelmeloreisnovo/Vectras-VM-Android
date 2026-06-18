package com.vectras.vm.rafaelia.connector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chunk / Conversation Manager — CHUNKS/CONVERSATIONS connector.
 *
 * <p>Manages fragments, series, and the "possible 10 units" hypothesis from the
 * RAFAELIA connector map. Each conversation is composed of up to MAX_UNITS chunks.
 *
 * <p>Architecture:
 * <pre>
 *   Conversation → [Chunk0, Chunk1, ... ChunkN]  (max N = MAX_UNITS)
 *   Each Chunk  → {id, seq, role, content, tokenCount, tsNs, coherence}
 *   Series      → ordered list of Conversation IDs with shared context
 * </pre>
 *
 * <p>The "10 units" hypothesis maps chunks to the 10-fold RAFAELIA structure:
 * units 0-9 represent ψ, χ, ρ, Δ, Σ, Ω, Φ, √3/2, Λ, and synthesis.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-CHUNKS
 */
public final class ChunkConversationManager {

    public static final int MAX_UNITS        = 10;
    public static final int MAX_CONVERSATIONS = 1_000;
    public static final int MAX_CHUNK_TOKENS  = 8_192;

    // Symbolic unit labels for the 10 positions
    static final String[] UNIT_SYMBOLS = {
            "ψ-INPUT", "χ-OBSERVE", "ρ-DENOISE", "Δ-TRANSMUTE", "Σ-MEMORY",
            "Ω-COMPLETE", "Φ-COHERENCE", "√3/2-SPIRAL", "Λ-LATENT", "⊕-SYNTHESIS"
    };

    public enum ChunkRole { SYSTEM, USER, ASSISTANT, TOOL, CONTEXT, MEMORY }

    private final Deque<Conversation>     conversations = new ArrayDeque<>();
    private final AtomicLong              globalSeq     = new AtomicLong(0);
    private final AtomicInteger           unitCursor    = new AtomicInteger(0);

    private volatile Conversation         activeConversation = null;

    private ChunkConversationManager() {}

    public static ChunkConversationManager create() { return new ChunkConversationManager(); }

    // ─── Conversation management ──────────────────────────────────────────────

    /** Start a new conversation, making it active. */
    @NonNull
    public Conversation beginConversation(@Nullable String contextTag) {
        Conversation conv = new Conversation(
                UUID.randomUUID().toString().replace("-", ""),
                contextTag,
                System.currentTimeMillis()
        );
        if (conversations.size() >= MAX_CONVERSATIONS) {
            conversations.pollFirst(); // drop oldest
        }
        conversations.addLast(conv);
        activeConversation = conv;
        unitCursor.set(0);
        return conv;
    }

    /** Close the active conversation, computing final coherence. */
    public void closeConversation() {
        Conversation conv = activeConversation;
        if (conv != null) {
            conv.close(System.currentTimeMillis());
            activeConversation = null;
        }
    }

    @Nullable
    public Conversation getActiveConversation() { return activeConversation; }

    public List<Conversation> allConversations() {
        return Collections.unmodifiableList(new ArrayList<>(conversations));
    }

    // ─── Chunk management ─────────────────────────────────────────────────────

    /**
     * Append a chunk to the active conversation.
     * Auto-advances the unit cursor (0-9 cycle).
     * Returns the created chunk, or null if no active conversation.
     */
    @Nullable
    public Chunk appendChunk(@NonNull ChunkRole role, @NonNull String content,
                              double coherence) {
        Conversation conv = activeConversation;
        if (conv == null) return null;

        int unit = unitCursor.getAndUpdate(u -> (u + 1) % MAX_UNITS);
        int tokens = estimateTokens(content);
        if (tokens > MAX_CHUNK_TOKENS) {
            throw new IllegalArgumentException("chunk exceeds MAX_CHUNK_TOKENS=" + MAX_CHUNK_TOKENS);
        }

        Chunk chunk = new Chunk(
                globalSeq.incrementAndGet(),
                unit,
                role,
                content,
                tokens,
                System.nanoTime(),
                coherence
        );
        conv.addChunk(chunk);
        return chunk;
    }

    // ─── Series ───────────────────────────────────────────────────────────────

    /** Build a Series from conversation IDs sharing a context tag. */
    @NonNull
    public Series buildSeries(@NonNull String contextTag) {
        List<String> ids = new ArrayList<>();
        for (Conversation c : conversations) {
            if (contextTag.equals(c.contextTag)) ids.add(c.id);
        }
        return new Series(contextTag, ids);
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    @NonNull
    public JSONObject exportConversationJson(@NonNull String convId) throws JSONException {
        for (Conversation c : conversations) {
            if (c.id.equals(convId)) return c.toJson();
        }
        throw new IllegalArgumentException("conversation not found: " + convId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    static int estimateTokens(String text) {
        // ~4 chars per token heuristic
        return Math.max(1, text.length() / 4);
    }

    // ─── Data types ───────────────────────────────────────────────────────────

    public static final class Chunk {
        public final long      seq;
        public final int       unit;
        public final String    unitSymbol;
        public final ChunkRole role;
        public final String    content;
        public final int       tokenCount;
        public final long      tsNs;
        public final double    coherence;

        Chunk(long seq, int unit, ChunkRole role, String content,
              int tokenCount, long tsNs, double coherence) {
            this.seq        = seq;
            this.unit       = unit;
            this.unitSymbol = unit < UNIT_SYMBOLS.length ? UNIT_SYMBOLS[unit] : "UNK";
            this.role       = role;
            this.content    = content;
            this.tokenCount = tokenCount;
            this.tsNs       = tsNs;
            this.coherence  = coherence;
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("seq",        seq);
            o.put("unit",       unit);
            o.put("unitSymbol", unitSymbol);
            o.put("role",       role.name());
            o.put("content",    content);
            o.put("tokens",     tokenCount);
            o.put("tsNs",       tsNs);
            o.put("coherence",  coherence);
            return o;
        }

        @NonNull @Override public String toString() {
            return "[" + seq + "|" + unitSymbol + "|" + role + "] " +
                    content.substring(0, Math.min(40, content.length())) + "…";
        }
    }

    public static final class Conversation {
        public final String id;
        public final @Nullable String contextTag;
        public final long startMs;
        private volatile long endMs = -1;

        private final List<Chunk>   chunks        = new ArrayList<>();
        private volatile double     avgCoherence  = 0.0;
        private volatile int        totalTokens   = 0;
        private volatile boolean    closed        = false;

        Conversation(String id, @Nullable String contextTag, long startMs) {
            this.id         = id;
            this.contextTag = contextTag;
            this.startMs    = startMs;
        }

        synchronized void addChunk(Chunk c) {
            if (closed) throw new IllegalStateException("conversation already closed");
            if (chunks.size() >= MAX_UNITS * 100) throw new IllegalStateException("chunk limit exceeded");
            chunks.add(c);
            totalTokens += c.tokenCount;
            avgCoherence = chunks.stream().mapToDouble(ch -> ch.coherence).average().orElse(0.0);
        }

        synchronized void close(long endMs) {
            this.endMs  = endMs;
            this.closed = true;
        }

        public synchronized List<Chunk> getChunks() { return Collections.unmodifiableList(chunks); }
        public boolean  isClosed()      { return closed; }
        public int      getTotalTokens(){ return totalTokens; }
        public double   getAvgCoherence(){ return avgCoherence; }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id",          id);
            o.put("contextTag",  contextTag);
            o.put("startMs",     startMs);
            o.put("endMs",       endMs);
            o.put("totalTokens", totalTokens);
            o.put("avgCoherence", avgCoherence);
            JSONArray arr = new JSONArray();
            for (Chunk c : chunks) arr.put(c.toJson());
            o.put("chunks", arr);
            return o;
        }
    }

    public static final class Series {
        public final String      contextTag;
        public final List<String> conversationIds;

        Series(String contextTag, List<String> ids) {
            this.contextTag      = contextTag;
            this.conversationIds = Collections.unmodifiableList(ids);
        }

        @NonNull @Override public String toString() {
            return "Series[" + contextTag + "](" + conversationIds.size() + " convs)";
        }
    }
}
