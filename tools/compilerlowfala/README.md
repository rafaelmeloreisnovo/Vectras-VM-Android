# compilerlowFala deterministic adapter

This directory does not claim that `Incluir/compiladorlowFala.txt` is already an APK compiler.
It provides a deterministic bridge from the historical monolith to inspectable seed records.

## Source of record

```text
Incluir/compiladorlowFala.txt
```

The source remains immutable during batch 001. Its Git blob identity is sealed in:

```text
configs/vectra_artifact_promotion_batch_001.json
```

## Index only

```bash
python3 tools/compilerlowfala/extract_seeds.py \
  --source Incluir/compiladorlowFala.txt \
  --output reports/compilerlowfala_seed_index.json \
  --require-count 60
```

The command:

- parses `seed_Sxx_Vy_*` heredoc functions;
- records family, variant, line, size and SHA-256;
- rejects duplicate or malformed names;
- never executes the extracted body;
- keeps `claim_allowed=false`.

## Optional reversible extraction

```bash
python3 tools/compilerlowfala/extract_seeds.py \
  --source Incluir/compiladorlowFala.txt \
  --output reports/compilerlowfala_seed_index.json \
  --require-count 60 \
  --extract-dir build/compilerlowfala/seeds
```

Generated `.seed` files are build artifacts. They are not canonical source files and must not be committed as independently proven implementations.

## Promotion boundary

```text
monolith present
→ deterministic index
→ reversible extraction
→ per-seed syntax/build tests
→ bytecode/ASM semantic tests
→ Android integration
→ APK DEX/ELF receipt
→ device runtime evidence
```

Batch 001 closes only the first two arrows. TLS, Android integration, APK emission and device execution remain `TOKEN_VAZIO` or separately gated.
