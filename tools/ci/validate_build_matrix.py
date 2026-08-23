#!/usr/bin/env python3
"""Validate CI workflow matrix/profile consistency and canonical workflow ownership."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / '.github' / 'workflows'
ABI_PROFILE_CONTRACT = ROOT / 'tools' / 'ci' / 'abi_profiles_contract.json'

required = [
    WORKFLOWS / 'pipeline-orchestrator.yml',
    WORKFLOWS / 'android-ci.yml',
    WORKFLOWS / 'android.yml',
    WORKFLOWS / 'host-ci.yml',
    WORKFLOWS / 'quality-gates.yml',
    WORKFLOWS / 'compile-matrix.yml',
    ABI_PROFILE_CONTRACT,
]

missing = [str(p) for p in required if not p.exists()]
if missing:
    print('missing workflow/contract files:')
    for item in missing:
        print(f' - {item}')
    sys.exit(1)

orch = (WORKFLOWS / 'pipeline-orchestrator.yml').read_text(encoding='utf-8')
for token in [
    'host_only',
    'android_only',
    'full',
    'quality-gates',
    './.github/workflows/host-ci.yml',
    './.github/workflows/android-ci.yml',
]:
    if token not in orch:
        print(f'orchestrator missing token: {token}')
        sys.exit(1)
if './.github/workflows/ci.yml' in orch:
    print('orchestrator cannot call ci.yml; use host-ci.yml as canonical host lane')
    sys.exit(1)
if './.github/workflows/android.yml' in orch:
    print('orchestrator cannot call android.yml; use android-ci.yml as canonical android lane')
    sys.exit(1)

android_ci = (WORKFLOWS / 'android-ci.yml').read_text(encoding='utf-8')
for token in [
    'workflow_call',
    'official_arm64',
    'internal_arm32_arm64',
    'native_matrix_profile',
    ':app:verifyDeliveredCompiledArtifacts',
]:
    if token not in android_ci:
        print(f'android-ci missing token: {token}')
        sys.exit(1)

try:
    abi_contract = json.loads(ABI_PROFILE_CONTRACT.read_text(encoding='utf-8'))
except (OSError, json.JSONDecodeError) as exc:
    print(f'cannot load canonical ABI profile contract: {exc}')
    sys.exit(1)

internal_4abi = abi_contract.get('profiles', {}).get('internal_4abi')
if not isinstance(internal_4abi, dict):
    print('canonical ABI profile contract missing profiles.internal_4abi')
    sys.exit(1)

expected_4abi = {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}
actual_4abi = set(internal_4abi.get('supported_abis', []))
if actual_4abi != expected_4abi:
    print(f'internal_4abi supported_abis mismatch: expected={sorted(expected_4abi)} actual={sorted(actual_4abi)}')
    sys.exit(1)
if internal_4abi.get('app_abi_policy') != 'internal-4abi':
    print('internal_4abi app_abi_policy must be internal-4abi')
    sys.exit(1)
if internal_4abi.get('ci_internal_validation') is not True:
    print('internal_4abi must keep ci_internal_validation=true')
    sys.exit(1)
if internal_4abi.get('channel') != 'internal':
    print('internal_4abi channel must be internal')
    sys.exit(1)

android_wrapper = (WORKFLOWS / 'android.yml').read_text(encoding='utf-8')
if './.github/workflows/android-ci.yml' not in android_wrapper:
    print('android.yml must delegate to android-ci.yml')
    sys.exit(1)
android_forbidden_markers = [
    'sdkmanager "platform-tools"',
    './tools/gradle_with_jdk21.sh',
    'build_android_cmake_matrix.sh',
    ':app:verifyDeliveredCompiledArtifacts',
]
for marker in android_forbidden_markers:
    if marker in android_wrapper:
        print(f'android.yml must stay wrapper-only; found android responsibility marker: {marker}')
        sys.exit(1)

host_ci = (WORKFLOWS / 'host-ci.yml').read_text(encoding='utf-8')
host_markers = [
    'validate_pipeline_directories.sh --profile host',
    'make run-selftest',
    'check_incoming_ingestion.py',
    'actions/upload-artifact@v4',
]
for marker in host_markers:
    if marker not in host_ci:
        print(f'host-ci missing canonical host marker: {marker}')
        sys.exit(1)

ci_alias = WORKFLOWS / 'ci.yml'
if ci_alias.exists():
    ci_content = ci_alias.read_text(encoding='utf-8')
    if './.github/workflows/host-ci.yml' not in ci_content:
        print('ci.yml must be an alias that calls host-ci.yml')
        sys.exit(1)

# Guardrail: workflows with host semantics in filename must not duplicate canonical host lane.
host_semantic_workflows = [
    p for p in WORKFLOWS.glob('*.yml') if p.name not in {'host-ci.yml', 'ci.yml'} and 'host' in p.stem.lower()
]
host_responsibility_markers = [
    'validate_pipeline_directories.sh --profile host',
    'make run-selftest',
    'check_incoming_ingestion.py',
]
for workflow_path in host_semantic_workflows:
    content = workflow_path.read_text(encoding='utf-8')
    for marker in host_responsibility_markers:
        if marker in content:
            print(
                f'workflow {workflow_path.name} duplicates host responsibility marker '
                f'({marker}); keep host checks canonical in host-ci.yml'
            )
            sys.exit(1)

# Guardrail: only canonical Android workflows can own Android build responsibilities.
android_canonical_workflows = {'android-ci.yml'}
android_wrapper_workflows = {'android.yml'}
android_aux_android_workflows = {'compile-matrix.yml'}
android_responsibility_markers = [
    'android-actions/setup-android@v3',
    './tools/gradle_with_jdk21.sh',
    'build_android_cmake_matrix.sh',
    ':app:assembleDebug',
    ':app:assembleRelease',
    ':app:verifyDeliveredCompiledArtifacts',
]
android_delegate_marker = './.github/workflows/android-ci.yml'

for workflow_path in WORKFLOWS.glob('*.yml'):
    content = workflow_path.read_text(encoding='utf-8')

    if workflow_path.name in android_wrapper_workflows:
        if android_delegate_marker not in content:
            print(f'workflow {workflow_path.name} must delegate to android-ci.yml')
            sys.exit(1)

    if workflow_path.name in android_canonical_workflows or workflow_path.name in android_aux_android_workflows:
        continue

    for marker in android_responsibility_markers:
        if marker in content:
            print(
                f'workflow {workflow_path.name} duplicates android responsibility marker '
                f'({marker}); keep android checks canonical in android-ci.yml'
            )
            sys.exit(1)

for workflow_path in WORKFLOWS.glob('*.yml'):
    if workflow_path.name in {'ci.yml', 'host-ci.yml'}:
        continue
    content = workflow_path.read_text(encoding='utf-8')
    if './.github/workflows/ci.yml' in content:
        print(f'workflow {workflow_path.name} must call host-ci.yml directly, not ci.yml alias')
        sys.exit(1)

print('build matrix validation: ok')
