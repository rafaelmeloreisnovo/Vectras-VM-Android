# Token Vectorization & VC Filling Module

This module provides Verifiable Credential (VC) filling with semantic token vectorization, integrating W3C-compatible credential issuance with the Vectra 7-direction vectorization system.

## Components

### `TokenVectorizationEngine`

Core engine for vectorizing tokens in 7 semantic directions:

```
D1: FORMAL_ARITMETICA       — Formal / arithmetic representation
D2: COMPUTACIONAL           — Computational / algorithmic topology
D3: GEOMETRICA_TOPOLOGICA   — Geometric / topological structure
D4: SENSORIAL               — Sensory / perceptual quality
D5: LINGUISTICA_SEMANTICA   — Linguistic / semantic distribution
D6: SISTEMICA_ARQUITETURAL  — Systemic / architectural role
D7: ETICA_VALIDACAO         — Ethical / validation gate
```

**Features:**
- Vectorizes any string into a 7-dimensional semantic vector
- Assigns tokens to evolution chains (NUCLEO_FORMAL, PONTE_ENTRE_AREAS, CONVERGENCIA_DISTANTE)
- Classifies tokens by coherence: FORTE, MODERADO, FRACO, ABORTADO
- Tracks evolutionary generations via `evolve()` cycles

**Usage:**
```java
TokenVectorizationEngine engine = TokenVectorizationEngine.create();
TokenVectorizationEngine.VectorizedToken token = engine.vectorize("my_claim_value");

System.out.println(token.text);           // "my_claim_value"
System.out.println(token.coherence);      // 0.42 (0.0 to 1.0)
System.out.println(token.classification); // MODERADO
System.out.println(token.chain);          // PONTE_ENTRE_AREAS
```

### `VerifiableCredential`

W3C-compatible VC structure with vectorized claims:

**Fields:**
- `context` — VC context/namespace
- `issuer` — Entity issuing the credential
- `subject` — Entity the credential is about
- `claims` — Key-value pairs (original form)
- `vectorizedClaims` — Vectorized representations with coherence/classification
- `proof` — Metadata about vectorization (engine, generation, quality metrics)
- `issuedAt`, `expiresAt` — Temporal bounds

**Usage:**
```java
VerifiableCredential vc = new VerifiableCredential.Builder()
    .context("https://example.com/credentials/v1")
    .issuer("did:example:issuer123")
    .subject("did:example:subject456")
    .addClaim("name", "Alice")
    .addClaim("role", "Admin")
    .expiresIn(365 * 24 * 60 * 60 * 1000L) // 1 year
    .addProof("custom_key", "custom_value")
    .build();

System.out.println(vc.isExpired()); // false
```

### `VerifiableCredentialFiller`

Main API for filling VCs with token vectorization:

**Workflow:**
1. Create filler: `VerifiableCredentialFiller.create(engine)`
2. Fill claims: `filler.fillClaim("key", "value")`
3. Build VC: `filler.buildCredential(context, issuer, subject)`
4. Inspect quality: `filler.getReport()`

**Features:**
- Batch fill multiple claims
- Configurable coherence thresholds
- Strict mode for validation
- Quality reporting (FORTE/MODERADO/FRACO/ABORTADO breakdown)
- Automatic proof metadata attachment

**Usage:**
```java
TokenVectorizationEngine engine = TokenVectorizationEngine.create();
VerifiableCredentialFiller filler = VerifiableCredentialFiller.create(engine)
    .withMinCoherenceThreshold(0.50)
    .withStrictMode(false);

Map<String, String> claims = new HashMap<>();
claims.put("id", "user123");
claims.put("status", "active");
claims.put("role", "developer");

filler.fillClaims(claims);

VerifiableCredential vc = filler.buildCredential(
    "https://example.com/credentials/v1",
    "did:example:company",
    "did:example:employee456"
);

// Get quality report
VerifiableCredentialFiller.VectorizationReport report = filler.getReport();
System.out.println(report);
// Output: VectorizationReport[claims=3 avg_coh=0.6234 FORTE=1 MODERADO=2 FRACO=0 ABORTADO=0]
```

## Vectorization Quality

### Coherence Scoring

Each vectorized token receives a coherence score (0.0 to 1.0) that aggregates the 7-direction vector:

```
coherence = sum(|v[i]|) / 7  [clamped to 1.0]
```

### Classification

Coherence classification is a **quality/inspection signal only**. It does not grant federated claim authority, release authority, credential trust, or `claim_allowed` state.

| Classification | Threshold | Quality interpretation |
|---|---|---|
| **FORTE** | ≥ 0.75 | High coherence; still requires the claim's independent authority/evidence gates |
| **MODERADO** | ≥ 0.50 | Moderate coherence; review/evidence requirements remain unchanged |
| **FRACO** | ≥ 0.25 | Low coherence; review recommended before use |
| **ABORTADO** | < 0.25 | Structural/vectorization failure for this module |

Governance invariant:

```
COHERENCE_SCORE != CLAIM_AUTHORITY
CLASSIFICATION_FORTE != CLAIM_ALLOWED
VECTOR_VALIDATION != FEDERATED_EVIDENCE
```

### Evolution Chains

Tokens are assigned to chains governing how they evolve:

- **NUCLEO_FORMAL** — Formal/geometric rigor (D1 + D3 scores highest)
- **PONTE_ENTRE_AREAS** — Cross-domain bridges (D2 + D5 scores highest)
- **CONVERGENCIA_DISTANTE** — Creative convergence (D4 + D7 scores highest)

## Configuration

### Coherence Thresholds

```java
filler.withMinCoherenceThreshold(0.50); // Default: 0.25
```

### Strict Mode

```java
filler.withStrictMode(true);  // Throws exception if coherence below threshold
filler.withStrictMode(false); // Logs warning but continues (default)
```

Strict mode controls vectorization-quality validation only. It does not authorize a federated claim.

## Proof Metadata

When building a VC, the filler automatically attaches quality metadata:

```json
"proof": {
  "vectorization_engine": "TokenVectorizationEngine",
  "vectorization_generation": "5",
  "filled_claims_count": "3",
  "average_coherence": "0.6234"
}
```

This enables:
- Lineage tracking (which generation created this VC)
- Quality auditing (average coherence across all claims)
- Reproducibility (can re-vectorize using same engine generation)

The metadata above is provenance/quality metadata; it is not cryptographic proof and does not imply claim approval.

## Testing

Comprehensive test suite in `VerifiableCredentialFillerTest.java`:

- Single and batch claim filling
- VC construction and validation
- Vectorized claim verification
- Coherence calculations
- Report generation
- Expiration handling
- Strict mode enforcement
- Multi-generation vectorization tracking

**Run tests:**
```bash
./gradlew :app:testDebug -k VerifiableCredentialFillerTest
```

## Integration with Governance

The VC module may expose quality/provenance signals to Mapa governance, but authority remains external to the vectorization score:

```
VC.proof.vectorization_generation
  → RafaeliaKernelV22.generation counter
  → TokenVectorizationEngine.evolve() cycles
  → quality/provenance signal
  → independent Mapa authority + falsifier + evidence + receipt gates
```

The VC module MUST NOT infer `claim_allowed=true` solely from coherence, classification, strict-mode success, or proof metadata.

See `MAPA/protocolos/VC_VECTORIZATION_FILLING_PROTOCOL.md` for full governance specification when that referenced authority is available and exact-ref verified.

## Limitations

- **Vectorization is deterministic but not cryptographic:** Use additional JWS/JWZ signing for cryptographic proof
- **Coherence is a quality indicator, not integrity proof or authority:** It cannot independently promote a claim
- **Chain assignment can change across engine generations:** VCs are tied to their generation ID
- **No revocation registry yet:** Implement DID-based revocation for full lifecycle management
- **Cross-repository governance references require exact-ref verification:** documentation references alone are not execution/evidence

## Future Work

1. **Cryptographic Signing** — JWS/JWZ envelope for VCs
2. **Revocation Registry** — DID-based revocation status lists
3. **Portability** — JSON-LD and CBOR serialization
4. **Performance** — Vectorization caching and batch optimization
5. **Audit Trail** — Immutable ledger of VC creation, validation, revocation

## References

- W3C Verifiable Credentials Data Model 1.1
- `TokenVectorizationEngine` — 7-direction semantic vectorization
- `RafaeliaKernelV22` — Mathematical constants and helper functions
- Mapa governance references require exact-ref validation before being treated as authoritative
