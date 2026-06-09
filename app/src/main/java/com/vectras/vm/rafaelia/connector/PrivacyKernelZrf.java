package com.vectras.vm.rafaelia.connector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Privacy Kernel ZRF — ZRF/PRIVACY/KERNEL connector.
 *
 * <p>The privacy layer sits between application data and external exposure.
 * It provides:
 * <ul>
 *   <li>Pseudonymization: replace real IDs with deterministic ZRF handles</li>
 *   <li>Data minimization: field-level redaction policy per category</li>
 *   <li>Consent gate: operations blocked if consent not recorded</li>
 *   <li>Ephemeral mode: in-memory keys with no persistence</li>
 * </ul>
 *
 * <p>ZRF handle format: "ZRF-" + hex(HMAC-like(salt, plaintext))[0..15]
 *
 * <p>Compliance tags: LGPD, GDPR-lite (no full DPA required for local-only processing).
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ZRF
 */
public final class PrivacyKernelZrf {

    public enum ConsentCategory {
        ANALYTICS, CRASH_REPORTING, PERSONALIZATION, TELEMETRY, STORAGE, NETWORKING
    }

    public enum RedactionPolicy { KEEP, PSEUDONYMIZE, REDACT, HASH_ONLY }

    // ─── ZRF handle constants ─────────────────────────────────────────────────
    static final String ZRF_PREFIX   = "ZRF-";
    static final int    HANDLE_CHARS = 16;

    private final byte[]                                   salt;
    private final ConcurrentHashMap<ConsentCategory, Boolean> consent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>        pseudoCache = new ConcurrentHashMap<>();

    // Field-level redaction policies by data category name
    private final ConcurrentHashMap<String, RedactionPolicy> fieldPolicies = new ConcurrentHashMap<>();

    private PrivacyKernelZrf(byte[] salt) {
        this.salt = salt.clone();
        applyDefaultPolicies();
    }

    /** Create with a random ephemeral salt (in-memory only). */
    public static PrivacyKernelZrf createEphemeral() {
        byte[] s = new byte[32];
        new SecureRandom().nextBytes(s);
        return new PrivacyKernelZrf(s);
    }

    /** Create with a fixed deterministic salt (for reproducible pseudonyms). */
    public static PrivacyKernelZrf createDeterministic(@NonNull byte[] salt) {
        if (salt.length < 16) throw new IllegalArgumentException("salt must be ≥ 16 bytes");
        return new PrivacyKernelZrf(salt);
    }

    // ─── Consent management ───────────────────────────────────────────────────

    public void grantConsent(@NonNull ConsentCategory category) {
        consent.put(category, Boolean.TRUE);
    }

    public void revokeConsent(@NonNull ConsentCategory category) {
        consent.put(category, Boolean.FALSE);
    }

    public boolean hasConsent(@NonNull ConsentCategory category) {
        return Boolean.TRUE.equals(consent.get(category));
    }

    /** Require consent or throw. Call before processing data of a category. */
    public void requireConsent(@NonNull ConsentCategory category) {
        if (!hasConsent(category)) {
            throw new PrivacyViolationException("consent not granted for " + category);
        }
    }

    // ─── Pseudonymization ─────────────────────────────────────────────────────

    /**
     * Replace plaintext identifier with a deterministic ZRF handle.
     * Cached so the same input always returns the same handle within this instance.
     */
    @NonNull
    public String pseudonymize(@NonNull String plaintext) {
        return pseudoCache.computeIfAbsent(plaintext, p -> {
            byte[] saltHex = sha256Hex(salt).getBytes(StandardCharsets.UTF_8);
            byte[] pBytes  = p.getBytes(StandardCharsets.UTF_8);
            byte[] input   = new byte[pBytes.length + 2 + saltHex.length];
            System.arraycopy(pBytes, 0, input, 0, pBytes.length);
            input[pBytes.length] = ':'; input[pBytes.length + 1] = ':';
            System.arraycopy(saltHex, 0, input, pBytes.length + 2, saltHex.length);
            byte[] digest = sha256(input);
            StringBuilder sb = new StringBuilder(ZRF_PREFIX);
            for (int i = 0; i < HANDLE_CHARS / 2; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        });
    }

    /** Reverse-lookup: find the original value for a handle (only within this session). */
    @Nullable
    public String reverseLookup(@NonNull String handle) {
        for (java.util.Map.Entry<String, String> e : pseudoCache.entrySet()) {
            if (e.getValue().equals(handle)) return e.getKey();
        }
        return null;
    }

    // ─── Field redaction ──────────────────────────────────────────────────────

    public void setFieldPolicy(@NonNull String fieldName, @NonNull RedactionPolicy policy) {
        fieldPolicies.put(fieldName, policy);
    }

    /**
     * Apply redaction policy to a field value.
     * @param fieldName the logical field name (used to look up policy)
     * @param value     the original value
     * @return processed value per policy
     */
    @NonNull
    public String applyPolicy(@NonNull String fieldName, @NonNull String value) {
        RedactionPolicy policy = fieldPolicies.getOrDefault(fieldName, RedactionPolicy.KEEP);
        return switch (policy) {
            case KEEP         -> value;
            case PSEUDONYMIZE -> pseudonymize(value);
            case REDACT       -> "[REDACTED]";
            case HASH_ONLY    -> sha256Hex(value.getBytes(StandardCharsets.UTF_8));
        };
    }

    // ─── Data minimization ────────────────────────────────────────────────────

    /** Truncate a string to max N characters for data minimization. */
    @NonNull
    public static String minimize(@NonNull String value, int maxLen) {
        if (value.length() <= maxLen) return value;
        return value.substring(0, maxLen) + "…";
    }

    // ─── Default policies ─────────────────────────────────────────────────────

    private void applyDefaultPolicies() {
        fieldPolicies.put("userId",    RedactionPolicy.PSEUDONYMIZE);
        fieldPolicies.put("deviceId",  RedactionPolicy.PSEUDONYMIZE);
        fieldPolicies.put("email",     RedactionPolicy.REDACT);
        fieldPolicies.put("ipAddress", RedactionPolicy.HASH_ONLY);
        fieldPolicies.put("phone",     RedactionPolicy.REDACT);
        fieldPolicies.put("location",  RedactionPolicy.REDACT);
    }

    // ─── Crypto helpers ───────────────────────────────────────────────────────

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static String sha256Hex(byte[] data) {
        byte[] raw = sha256(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── Exceptions ───────────────────────────────────────────────────────────

    public static final class PrivacyViolationException extends RuntimeException {
        public PrivacyViolationException(String msg) { super(msg); }
    }
}
