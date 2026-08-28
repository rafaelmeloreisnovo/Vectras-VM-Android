#!/usr/bin/env python3
import json
from pathlib import Path

P = Path('docs/assurance/vectras-consumer-assurance.v1.json')

def fail(msg):
    raise SystemExit(f'FAIL: {msg}')

m = json.loads(P.read_text(encoding='utf-8'))
if m.get('schema') != 'rafaelia.vectras-consumer-assurance.v1': fail('schema')
if m.get('claim_allowed') is not False: fail('claim_allowed')
if m['license'].get('relicense_upstream_allowed_by_this_manifest') is not False: fail('upstream relicense')
chain = m['execution_chain']
if chain.get('discovery_is_execution') is not False: fail('discovery promoted')
if chain.get('dispatch_is_qemu_process') is not False: fail('dispatch promoted')
if chain.get('qemu_process_is_guest_boot') is not False: fail('process promoted to boot')
if chain.get('guest_boot_is_performance_claim') is not False: fail('boot promoted to performance')
if m['security_privacy'].get('state') != 'FAIL_CLOSED': fail('security/privacy')
if m['image_guest'].get('raw_guest_payload_in_public_receipt') is not False: fail('guest data exposure')
if not any(g['id']=='QEMU_PROCESS_EXIT' and g['state']=='TOKEN_VAZIO' for g in m['gates']): fail('qemu exit prematurely closed')
if not any(g['urgency']=='P0' and g['state']=='TOKEN_VAZIO' for g in m['gaps']): fail('P0 gaps absent')
if not m['rollback'].get('available'): fail('rollback')
print('PASS: Vectras consumer chain preserves request/dispatch/process/exit/guest boundaries')
