package com.vectras.vm.rafaelia.token;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VerifiableCredentialFiller — Fills VC claims using token vectorization.
 *
 * Process:
 * 1. Initialize with a TokenVectorizationEngine
 * 2. Add claim keys to vectorize
 * 3. Fill credentials with vectorized token representations
 * 4. Validate vectorization quality and coherence
 * 5. Generate VC payload ready for issuance
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-VC-FILLER
 */
public final class VerifiableCredentialFiller {

    private final TokenVectorizationEngine engine;
    private final List<FilledClaim> filledClaims;
    private final Map<String, TokenVectorizationEngine.VectorizedToken> vectorCache;

    // Configuration thresholds
    private double minCoherenceThreshold = 0.25;
    private boolean strictMode = false;

    private VerifiableCredentialFiller(TokenVectorizationEngine engine) {
        this.engine = engine;
        this.filledClaims = new ArrayList<>();
        this.vectorCache = new HashMap<>();
    }

    public static VerifiableCredentialFiller create(@NonNull TokenVectorizationEngine engine) {
        return new VerifiableCredentialFiller(engine);
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    public VerifiableCredentialFiller withMinCoherenceThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        this.minCoherenceThreshold = threshold;
        return this;
    }

    public VerifiableCredentialFiller withStrictMode(boolean strict) {
        this.strictMode = strict;
        return this;
    }

    // ─── Claim filling ────────────────────────────────────────────────────────

    /**
     * Fill a claim by vectorizing the value and storing it.
     */
    @NonNull
    public FilledClaim fillClaim(@NonNull String claimKey, @NonNull String claimValue) {
        TokenVectorizationEngine.VectorizedToken token = engine.vectorize(claimValue);
        validateVectorization(token);
        vectorCache.put(claimKey, token);

        FilledClaim filled = new FilledClaim(claimKey, claimValue, token);
        filledClaims.add(filled);
        return filled;
    }

    /**
     * Fill multiple claims at once.
     */
    @NonNull
    public List<FilledClaim> fillClaims(@NonNull Map<String, String> claimsMap) {
        List<FilledClaim> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : claimsMap.entrySet()) {
            results.add(fillClaim(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableList(results);
    }

    private void validateVectorization(@NonNull TokenVectorizationEngine.VectorizedToken token) {
        if (strictMode && token.coherence < minCoherenceThreshold) {
            throw new VectorizationException(
                    "Token coherence " + token.coherence + " below threshold " + minCoherenceThreshold);
        }
    }

    // ─── VC Construction ──────────────────────────────────────────────────────

    /**
     * Build a complete VerifiableCredential from filled claims.
     */
    @NonNull
    public VerifiableCredential buildCredential(
            @NonNull String context,
            @NonNull String issuer,
            @NonNull String subject) {

        VerifiableCredential.Builder vcBuilder = new VerifiableCredential.Builder()
                .context(context)
                .issuer(issuer)
                .subject(subject);

        // Add all claims and their vectorized representations
        for (FilledClaim filled : filledClaims) {
            vcBuilder.addClaim(filled.claimKey, filled.claimValue);
            vcBuilder.addVectorizedClaim(filled.claimKey, filled.vectorizedToken);
        }

        // Add vectorization metadata as proof
        vcBuilder.addProof("vectorization_engine", "TokenVectorizationEngine");
        vcBuilder.addProof("vectorization_generation", String.valueOf(engine.generation()));
        vcBuilder.addProof("filled_claims_count", String.valueOf(filledClaims.size()));
        vcBuilder.addProof("average_coherence", String.format("%.4f", computeAverageCoherence()));

        return vcBuilder.build();
    }

    // ─── Statistics and Analysis ──────────────────────────────────────────────

    /**
     * Get vectorization report for filled claims.
     */
    @NonNull
    public VectorizationReport getReport() {
        return new VectorizationReport(filledClaims, engine);
    }

    /**
     * Compute average coherence of all filled claims.
     */
    public double computeAverageCoherence() {
        if (filledClaims.isEmpty()) return 0.0;
        double sum = 0.0;
        for (FilledClaim claim : filledClaims) {
            sum += claim.vectorizedToken.coherence;
        }
        return sum / filledClaims.size();
    }

    public int getFilledClaimsCount() {
        return filledClaims.size();
    }

    @NonNull
    public List<FilledClaim> getFilledClaims() {
        return Collections.unmodifiableList(filledClaims);
    }

    // ─── Inner classes ────────────────────────────────────────────────────────

    public static final class FilledClaim {
        public final String claimKey;
        public final String claimValue;
        public final TokenVectorizationEngine.VectorizedToken vectorizedToken;

        FilledClaim(String claimKey, String claimValue,
                   TokenVectorizationEngine.VectorizedToken vectorizedToken) {
            this.claimKey = claimKey;
            this.claimValue = claimValue;
            this.vectorizedToken = vectorizedToken;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("FilledClaim[%s=%s → %s]",
                    claimKey, claimValue, vectorizedToken);
        }
    }

    public static final class VectorizationReport {
        public final List<FilledClaim> claims;
        public final TokenVectorizationEngine engine;
        public final double averageCoherence;
        public final long generatedAt;

        VectorizationReport(List<FilledClaim> claims, TokenVectorizationEngine engine) {
            this.claims = Collections.unmodifiableList(new ArrayList<>(claims));
            this.engine = engine;
            this.averageCoherence = claims.isEmpty() ? 0.0 :
                    claims.stream().mapToDouble(c -> c.vectorizedToken.coherence).average().orElse(0.0);
            this.generatedAt = System.currentTimeMillis();
        }

        public int getForteCount() {
            return (int) claims.stream()
                    .filter(c -> c.vectorizedToken.classification == TokenVectorizationEngine.Classification.FORTE)
                    .count();
        }

        public int getModeradoCount() {
            return (int) claims.stream()
                    .filter(c -> c.vectorizedToken.classification == TokenVectorizationEngine.Classification.MODERADO)
                    .count();
        }

        public int getFracoCount() {
            return (int) claims.stream()
                    .filter(c -> c.vectorizedToken.classification == TokenVectorizationEngine.Classification.FRACO)
                    .count();
        }

        public int getAbortadoCount() {
            return (int) claims.stream()
                    .filter(c -> c.vectorizedToken.classification == TokenVectorizationEngine.Classification.ABORTADO)
                    .count();
        }

        @NonNull
        @Override
        public String toString() {
            return String.format(
                    "VectorizationReport[claims=%d avg_coh=%.4f FORTE=%d MODERADO=%d FRACO=%d ABORTADO=%d]",
                    claims.size(), averageCoherence, getForteCount(), getModeradoCount(),
                    getFracoCount(), getAbortadoCount());
        }
    }

    public static final class VectorizationException extends RuntimeException {
        public VectorizationException(String message) {
            super(message);
        }

        public VectorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("VerifiableCredentialFiller[filled=%d avg_coh=%.4f]",
                filledClaims.size(), computeAverageCoherence());
    }
}
