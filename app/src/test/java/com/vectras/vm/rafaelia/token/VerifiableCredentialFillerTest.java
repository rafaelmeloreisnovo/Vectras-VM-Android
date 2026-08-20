package com.vectras.vm.rafaelia.token;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for VerifiableCredentialFiller and VC integration.
 */
public class VerifiableCredentialFillerTest {

    private TokenVectorizationEngine engine;
    private VerifiableCredentialFiller filler;

    @Before
    public void setUp() {
        engine = TokenVectorizationEngine.create();
        filler = VerifiableCredentialFiller.create(engine);
    }

    @Test
    public void testFillSingleClaim() {
        VerifiableCredentialFiller.FilledClaim claim = filler.fillClaim("name", "John Doe");
        assertNotNull(claim);
        assertEquals("name", claim.claimKey);
        assertEquals("John Doe", claim.claimValue);
        assertNotNull(claim.vectorizedToken);
        assertTrue(claim.vectorizedToken.coherence >= 0.0);
        assertTrue(claim.vectorizedToken.coherence <= 1.0);
    }

    @Test
    public void testFillMultipleClaims() {
        Map<String, String> claims = new HashMap<>();
        claims.put("name", "John Doe");
        claims.put("email", "john@example.com");
        claims.put("organization", "Test Org");

        List<VerifiableCredentialFiller.FilledClaim> filled = filler.fillClaims(claims);
        assertEquals(3, filled.size());
        assertEquals(3, filler.getFilledClaimsCount());
    }

    @Test
    public void testVerifiableCredentialConstruction() {
        filler.fillClaim("name", "Alice");
        filler.fillClaim("role", "Admin");

        VerifiableCredential vc = filler.buildCredential(
                "https://example.com/credentials/v1",
                "did:example:issuer123",
                "did:example:subject456"
        );

        assertNotNull(vc);
        assertEquals("https://example.com/credentials/v1", vc.getContext());
        assertEquals("did:example:issuer123", vc.getIssuer());
        assertEquals("did:example:subject456", vc.getSubject());
        assertEquals(2, vc.getClaims().size());
        assertEquals(2, vc.getVectorizedClaims().size());
    }

    @Test
    public void testVectorizedClaimsInVC() {
        filler.fillClaim("expertise", "quantum_computing");
        filler.fillClaim("level", "expert");

        VerifiableCredential vc = filler.buildCredential(
                "https://credentials.example.com/v1",
                "did:example:issuer",
                "did:example:subject"
        );

        Map<String, TokenVectorizationEngine.VectorizedToken> vectorized = vc.getVectorizedClaims();
        assertTrue(vectorized.containsKey("expertise"));
        assertTrue(vectorized.containsKey("level"));

        TokenVectorizationEngine.VectorizedToken expertiseToken = vectorized.get("expertise");
        assertEquals("quantum_computing", expertiseToken.text);
        assertNotNull(expertiseToken.vector);
        assertEquals(7, expertiseToken.vector.length); // 7 directions
        assertNotNull(expertiseToken.chain);
        assertNotNull(expertiseToken.classification);
    }

    @Test
    public void testCoherenceCalculation() {
        filler.fillClaim("claim1", "value1");
        filler.fillClaim("claim2", "value2");
        filler.fillClaim("claim3", "value3");

        double avgCoherence = filler.computeAverageCoherence();
        assertTrue(avgCoherence >= 0.0);
        assertTrue(avgCoherence <= 1.0);
    }

    @Test
    public void testVectorizationReport() {
        filler.fillClaim("id", "user123");
        filler.fillClaim("status", "active");

        VerifiableCredentialFiller.VectorizationReport report = filler.getReport();
        assertNotNull(report);
        assertEquals(2, report.claims.size());
        assertTrue(report.averageCoherence >= 0.0);
        assertTrue(report.getForteCount() >= 0);
        assertTrue(report.getModeradoCount() >= 0);
        assertTrue(report.getFracoCount() >= 0);
        // Claims might be FORTE, MODERADO, FRACO, or ABORTADO
        int total = report.getForteCount() + report.getModeradoCount() +
                   report.getFracoCount() + report.getAbortadoCount();
        assertEquals(2, total);
    }

    @Test
    public void testProofMetadata() {
        filler.fillClaim("property", "value");
        VerifiableCredential vc = filler.buildCredential(
                "https://example.com/v1",
                "did:example:issuer",
                "did:example:subject"
        );

        Map<String, String> proof = vc.getProof();
        assertTrue(proof.containsKey("vectorization_engine"));
        assertTrue(proof.containsKey("vectorization_generation"));
        assertTrue(proof.containsKey("filled_claims_count"));
        assertTrue(proof.containsKey("average_coherence"));

        assertEquals("TokenVectorizationEngine", proof.get("vectorization_engine"));
        assertEquals("1", proof.get("filled_claims_count"));
    }

    @Test
    public void testCredentialExpiration() {
        VerifiableCredential vc = filler.buildCredential(
                "https://example.com/v1",
                "did:example:issuer",
                "did:example:subject"
        );

        assertFalse(vc.isExpired()); // Default 1 year expiration

        // Create VC with short expiration
        VerifiableCredential.Builder builder = new VerifiableCredential.Builder()
                .context("https://example.com/v1")
                .issuer("did:example:issuer")
                .subject("did:example:subject")
                .expiresIn(1); // 1 millisecond

        try {
            Thread.sleep(2); // Wait for expiration
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        VerifiableCredential expiredVc = builder.build();
        assertTrue(expiredVc.isExpired());
    }

    @Test
    public void testStrictMode() {
        VerifiableCredentialFiller strictFiller = VerifiableCredentialFiller.create(engine)
                .withStrictMode(true)
                .withMinCoherenceThreshold(0.95); // Very high threshold

        // This might throw if coherence is below threshold
        try {
            strictFiller.fillClaim("test", "value");
            // If no exception, the coherence was high enough
        } catch (VerifiableCredentialFiller.VectorizationException e) {
            assertTrue(e.getMessage().contains("below threshold"));
        }
    }

    @Test
    public void testTokenVectorizationDirections() {
        filler.fillClaim("test", "complex_token_value");

        VerifiableCredential vc = filler.buildCredential(
                "https://example.com/v1",
                "did:example:issuer",
                "did:example:subject"
        );

        TokenVectorizationEngine.VectorizedToken token =
                vc.getVectorizedClaims().get("test");

        assertNotNull(token.vector);
        assertEquals(7, token.vector.length);

        // Each direction should be a value between 0 and 1
        for (int i = 0; i < 7; i++) {
            assertTrue(token.vector[i] >= 0.0);
            assertTrue(token.vector[i] <= 1.0);
        }

        // Chain should be assigned
        assertTrue(token.chain == TokenVectorizationEngine.Chain.NUCLEO_FORMAL ||
                  token.chain == TokenVectorizationEngine.Chain.PONTE_ENTRE_AREAS ||
                  token.chain == TokenVectorizationEngine.Chain.CONVERGENCIA_DISTANTE);

        // Classification should be valid
        assertTrue(token.classification == TokenVectorizationEngine.Classification.FORTE ||
                  token.classification == TokenVectorizationEngine.Classification.MODERADO ||
                  token.classification == TokenVectorizationEngine.Classification.FRACO ||
                  token.classification == TokenVectorizationEngine.Classification.ABORTADO);
    }

    @Test
    public void testMultipleVectorizationCycles() {
        VerifiableCredentialFiller filler1 = VerifiableCredentialFiller.create(engine);
        filler1.fillClaim("cycle1", "data1");

        int gen1 = engine.generation();

        engine.evolve(); // Run evolution cycle

        VerifiableCredentialFiller filler2 = VerifiableCredentialFiller.create(engine);
        filler2.fillClaim("cycle2", "data2");

        int gen2 = engine.generation();

        assertTrue(gen2 > gen1);

        VerifiableCredential vc1 = filler1.buildCredential(
                "https://example.com/v1",
                "did:example:issuer",
                "did:example:subject"
        );

        VerifiableCredential vc2 = filler2.buildCredential(
                "https://example.com/v1",
                "did:example:issuer",
                "did:example:subject"
        );

        String gen1Str = vc1.getProofValue("vectorization_generation");
        String gen2Str = vc2.getProofValue("vectorization_generation");

        assertNotNull(gen1Str);
        assertNotNull(gen2Str);
        assertNotEquals(gen1Str, gen2Str);
    }
}
