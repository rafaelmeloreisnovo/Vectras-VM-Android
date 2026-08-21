#!/usr/bin/env python3
from pathlib import Path
import sys

readme = Path('app/src/main/java/com/vectras/vm/rafaelia/token/README.md').read_text(encoding='utf-8')
token_root = Path('app/src/main/java/com/vectras/vm/rafaelia/token')
app_root = Path('app/src/main/java')
token_files = sorted(token_root.glob('*.java'))
token_text = '\n'.join(p.read_text(encoding='utf-8', errors='replace') for p in token_files)

errors = []
def req(cond, msg):
    if not cond:
        errors.append(msg)

for forbidden in [
    'Claim immediately allowed',
    'Claim allowed with conditions',
    '→ claim_allowed = true',
    '→ claim_allowed = conditional',
]:
    req(forbidden not in readme, f'legacy coherence→authority wording remains: {forbidden}')

for required in [
    'COHERENCE_SCORE != CLAIM_AUTHORITY',
    'CLASSIFICATION_FORTE != CLAIM_ALLOWED',
    'VECTOR_VALIDATION != FEDERATED_EVIDENCE',
    'MUST NOT infer `claim_allowed=true` solely from coherence',
]:
    req(required in readme, f'missing fail-closed boundary: {required}')

# Local runtime boundary: the token implementation may classify quality, but it
# must not contain an implementation-level claim_allowed promotion token.
req('claim_allowed' not in token_text.lower(),
    'token Java source contains claim_allowed; inspect for authority coupling')

# Downstream-consumer boundary: inspect every production Java file that consumes
# the VC filler/report/classification API. A consumer is allowed to read quality
# metadata, but the same source file must not convert that signal directly into
# claim authority. This deliberately scans production sources beyond the token
# package so a future integration cannot silently bypass the local check.
consumer_markers = (
    'VerifiableCredentialFiller',
    'VectorizationReport',
    'QualityClassification',
    'Classification.FORTE',
    'getReport()',
)
consumer_files = []
for path in sorted(app_root.rglob('*.java')):
    text = path.read_text(encoding='utf-8', errors='replace')
    if not any(marker in text for marker in consumer_markers):
        continue
    consumer_files.append(str(path))
    lowered = text.lower()
    req('claim_allowed' not in lowered,
        f'VC/coherence consumer couples quality to claim_allowed: {path}')
    req('claim immediately allowed' not in lowered,
        f'VC/coherence consumer contains direct authority wording: {path}')
    req('claim allowed with conditions' not in lowered,
        f'VC/coherence consumer contains conditional authority wording: {path}')

# The implementation itself must be discoverable; an empty scan would make this
# test vacuous after a path/package move.
req(any('VerifiableCredentialFiller.java' in p for p in consumer_files),
    'VC consumer scan found no VerifiableCredentialFiller.java; path drift or scan regression')

if errors:
    print('FAIL vc coherence claim boundary')
    for e in errors:
        print('-', e)
    sys.exit(1)

print('PASS vc coherence is quality/provenance only; claim authority remains external')
print(f'consumer_files_scanned={len(consumer_files)}')
for path in consumer_files:
    print(f'consumer={path}')
