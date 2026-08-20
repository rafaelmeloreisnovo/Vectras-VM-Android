package com.vectras.vm.rafaelia.token;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * VerifiableCredential — W3C-compatible verifiable credential with Vectra vectorization.
 *
 * Structure:
 * - context: credential context/namespace
 * - issuer: entity issuing the credential
 * - subject: entity the credential is about
 * - claims: key-value pairs representing assertions
 * - vectorization: vectorized representation of claims via TokenVectorizationEngine
 * - proof: cryptographic proof (placeholder for future implementation)
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-VC
 */
public final class VerifiableCredential {

    private final String context;
    private final String issuer;
    private final String subject;
    private final Map<String, Object> claims;
    private final Map<String, TokenVectorizationEngine.VectorizedToken> vectorizedClaims;
    private final long issuedAt;
    private final long expiresAt;
    private final Map<String, String> proof;

    private VerifiableCredential(Builder builder) {
        this.context = builder.context;
        this.issuer = builder.issuer;
        this.subject = builder.subject;
        this.claims = Collections.unmodifiableMap(new HashMap<>(builder.claims));
        this.vectorizedClaims = Collections.unmodifiableMap(new HashMap<>(builder.vectorizedClaims));
        this.issuedAt = builder.issuedAt;
        this.expiresAt = builder.expiresAt;
        this.proof = Collections.unmodifiableMap(new HashMap<>(builder.proof));
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    @NonNull
    public String getContext() {
        return context;
    }

    @NonNull
    public String getIssuer() {
        return issuer;
    }

    @NonNull
    public String getSubject() {
        return subject;
    }

    @NonNull
    public Map<String, Object> getClaims() {
        return claims;
    }

    @NonNull
    public Map<String, TokenVectorizationEngine.VectorizedToken> getVectorizedClaims() {
        return vectorizedClaims;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    @NonNull
    public Map<String, String> getProof() {
        return proof;
    }

    @Nullable
    public String getProofValue(String key) {
        return proof.get(key);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    public static class Builder {
        private String context;
        private String issuer;
        private String subject;
        private final Map<String, Object> claims = new HashMap<>();
        private final Map<String, TokenVectorizationEngine.VectorizedToken> vectorizedClaims = new HashMap<>();
        private long issuedAt = System.currentTimeMillis();
        private long expiresAt = issuedAt + 365 * 24 * 60 * 60 * 1000L; // 1 year default
        private final Map<String, String> proof = new HashMap<>();

        public Builder context(@NonNull String context) {
            this.context = context;
            return this;
        }

        public Builder issuer(@NonNull String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder subject(@NonNull String subject) {
            this.subject = subject;
            return this;
        }

        public Builder addClaim(@NonNull String key, @NonNull Object value) {
            this.claims.put(key, value);
            return this;
        }

        public Builder addVectorizedClaim(@NonNull String key,
                                         @NonNull TokenVectorizationEngine.VectorizedToken token) {
            this.vectorizedClaims.put(key, token);
            return this;
        }

        public Builder issuedAt(long timestamp) {
            this.issuedAt = timestamp;
            return this;
        }

        public Builder expiresAt(long timestamp) {
            this.expiresAt = timestamp;
            return this;
        }

        public Builder expiresIn(long milliseconds) {
            this.expiresAt = issuedAt + milliseconds;
            return this;
        }

        public Builder addProof(@NonNull String key, @NonNull String value) {
            this.proof.put(key, value);
            return this;
        }

        public VerifiableCredential build() {
            if (context == null) throw new IllegalStateException("context is required");
            if (issuer == null) throw new IllegalStateException("issuer is required");
            if (subject == null) throw new IllegalStateException("subject is required");
            return new VerifiableCredential(this);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("VC[issuer=%s subject=%s claims=%d vectorized=%d expires=%s]",
                issuer, subject, claims.size(), vectorizedClaims.size(),
                isExpired() ? "EXPIRED" : "VALID");
    }
}
