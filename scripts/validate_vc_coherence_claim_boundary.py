#!/usr/bin/env python3
from pathlib import Path
import sys

readme = Path('app/src/main/java/com/vectras/vm/rafaelia/token/README.md').read_text(encoding='utf-8')
java_root = Path('app/src/main/java/com/vectras/vm/rafaelia/token')
java_text = '\n'.join(p.read_text(encoding='utf-8', errors='replace') for p in java_root.glob('*.java'))

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

# Runtime boundary: this module may classify quality, but must not contain an
# implementation-level claim_allowed promotion token in Java source.
req('claim_allowed' not in java_text.lower(), 'token Java source contains claim_allowed; inspect for authority coupling')

if errors:
    print('FAIL vc coherence claim boundary')
    for e in errors:
        print('-', e)
    sys.exit(1)

print('PASS vc coherence is quality/provenance only; claim authority remains external')
