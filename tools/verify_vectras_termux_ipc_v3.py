#!/usr/bin/env python3
"""Fail-closed static verifier for Vectras -> Termux RAFCODE-Phi IPC v3."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SCHEMA = "raf.vectras-termux-ipc-verification.v3"

FILES = {
    "manager": ROOT / "app/src/main/java/com/vectras/vm/integration/CrossRepoIntegrationManager.kt",
    "contract_source": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxIpcContract.kt",
    "bridge": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxBridge.kt",
    "store": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxReceiptStore.kt",
    "receiver": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxResultReceiver.kt",
    "contract_json": ROOT / "docs/contracts/VECTRAS_TERMUX_IPC_V3.json",
}
MANIFESTS = [ROOT / f"app/src/{name}/AndroidManifest.xml" for name in ("debug", "release", "perfRelease")]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require(text: str, snippets: list[str], label: str, errors: list[str]) -> None:
    for snippet in snippets:
        if snippet not in text:
            errors.append(f"{label}: missing {snippet!r}")


def forbid(text: str, snippets: list[str], label: str, errors: list[str]) -> None:
    for snippet in snippets:
        if snippet in text:
            errors.append(f"{label}: forbidden {snippet!r}")


def integer_constant(text: str, name: str) -> int | None:
    match = re.search(rf"const val\s+{re.escape(name)}\s*=\s*([0-9]+)", text)
    return int(match.group(1)) if match else None


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    texts: dict[str, str] = {}
    inputs: dict[str, Any] = {}

    for label, path in FILES.items():
        if not path.is_file():
            errors.append(f"missing {label}: {path}")
            continue
        texts[label] = path.read_text(encoding="utf-8")
        inputs[label] = {"path": str(path.relative_to(ROOT)), "sha256": sha256_file(path)}

    for manifest in MANIFESTS:
        label = f"manifest:{manifest.parent.parent.name}"
        if not manifest.is_file():
            errors.append(f"missing {label}: {manifest}")
            continue
        texts[label] = manifest.read_text(encoding="utf-8")
        inputs[label] = {"path": str(manifest.relative_to(ROOT)), "sha256": sha256_file(manifest)}

    contract: dict[str, Any] = {}
    if "contract_json" in texts:
        try:
            contract = json.loads(texts["contract_json"])
        except json.JSONDecodeError as error:
            errors.append(f"contract_json invalid: {error}")

    if contract:
        if contract.get("schema") != "raf.vectras-termux-ipc-contract.v3":
            errors.append("contract schema mismatch")
        request = contract.get("request", {})
        fixed = request.get("fixed_arguments", [])
        max_total = request.get("max_total_arguments")
        max_extra = request.get("max_extra_arguments")
        if not isinstance(fixed, list):
            errors.append("fixed_arguments is not a list")
        elif max_total != len(fixed) + max_extra:
            errors.append("max_total_arguments != fixed + max_extra")
        if contract.get("claim_allowed") is not False:
            errors.append("contract claim_allowed must be false")
        if contract.get("security", {}).get("result_requires_local_pending_request") is not True:
            errors.append("local pending request binding is not required")

    contract_source = texts.get("contract_source", "")
    require(
        contract_source,
        [
            'PROTOCOL = "raf.vectras-termux-ipc.v3"',
            'TERMUX_PACKAGE = "com.termux.rafacodephi"',
            'SERVICE_CLASS = "com.termux.app.RunCommandService"',
            'RESULT_BUNDLE = "result"',
            'RESULT_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"',
            'RESULT_STDERR_ORIGINAL_LENGTH = "stderr_original_length"',
            'RESULT_EXIT_CODE = "exitCode"',
            'RESULT_ERR = "err"',
            'RESULT_ERRMSG = "errmsg"',
            'RUNNER_APP_SHELL = "app-shell"',
            'WORKDIR = "~/"',
            'MAX_TOTAL_ARGUMENTS = 32',
            'MAX_ARGUMENT_LENGTH = 256',
            'MAX_ARGUMENT_BYTES = 4096',
            '"-accel", "tcg"',
            '"-display", "none"',
            '"-monitor", "none"',
            '"-serial", "stdio"',
            '"-no-reboot"',
            'canonicalRequest(',
            'appendField("transaction_id", transactionId)',
        ],
        "contract_source",
        errors,
    )
    if contract:
        for name, json_key in (
            ("MAX_TOTAL_ARGUMENTS", "max_total_arguments"),
            ("MAX_ARGUMENT_LENGTH", "max_argument_length"),
            ("MAX_ARGUMENT_BYTES", "max_argument_bytes"),
        ):
            observed = integer_constant(contract_source, name)
            expected = contract.get("request", {}).get(json_key)
            if observed != expected:
                errors.append(f"{name} mismatch source={observed} contract={expected}")

    manager = texts.get("manager", "")
    require(
        manager,
        [
            'TERMUX_PACKAGE = "com.termux.rafacodephi"',
            'TERMUX_RUN_COMMAND_PERMISSION =',
            '"qemu_binary_names"',
            '"qemu_binary_sha256"',
            '"provider_apk_sha256"',
            '"protocol_version"',
            '"private_paths_exposed"',
            'provenanceReady',
            'persistProviderIdentity(',
            'Context.RECEIVER_EXPORTED',
            'UUID.randomUUID()',
        ],
        "manager",
        errors,
    )
    forbid(
        manager,
        ['"prefix_path"', '"qemu_binary_paths"', 'Context.RECEIVER_NOT_EXPORTED'],
        "manager",
        errors,
    )

    bridge = texts.get("bridge", "")
    require(
        bridge,
        [
            'VectrasTermuxIpcContract.boundedArguments(arguments)',
            'CrossRepoIntegrationManager.loadProviderIdentity(context, binaryName)',
            'producerApkSha256',
            'VectrasTermuxReceiptStore.writePending(',
            'State.REQUEST_PERSISTENCE_FAILED',
            'VectrasTermuxIpcContract.EXTRA_RUNNER',
            'VectrasTermuxIpcContract.RUNNER_APP_SHELL',
            'PendingIntent.FLAG_MUTABLE',
            'executionProven = false',
            'claimAllowed = false',
            'provenanceBound = provenanceBound',
            '"dispatch_accepted_discovery_identity_bound_execution_receipt_pending"',
            '"dispatch_accepted_provenance_partial_execution_receipt_pending"',
        ],
        "bridge",
        errors,
    )
    forbid(
        bridge,
        [
            'RUN_COMMAND_BACKGROUND',
            'EXTRA_BACKGROUND',
            'Runtime.getRuntime().exec',
            '/data/data/com.termux',
        ],
        "bridge",
        errors,
    )

    store = texts.get("store", "")
    require(
        store,
        [
            'REQUEST_SCHEMA = "raf.vectras-termux-request.v4"',
            'state", "PENDING_DISPATCH_RESULT"',
            '"request_sha256"',
            '"arguments_sha256"',
            '"producer_apk_sha256"',
            '"provider_apk_sha256_discovery"',
            '"provider_binary_sha256_discovery"',
            '"provider_identity_scope", "DISCOVERY_NOT_EXECUTION"',
            '"executable_sha256_at_execution", "TOKEN_VAZIO"',
            'writeAtomic(',
            'if (target.exists()) return false',
            '"claim_allowed", false',
        ],
        "store",
        errors,
    )

    receiver = texts.get("receiver", "")
    require(
        receiver,
        [
            'VectrasTermuxReceiptStore.loadPending(context, transactionId)',
            'RESULT_STDOUT_ORIGINAL_LENGTH',
            'RESULT_STDERR_ORIGINAL_LENGTH',
            'RESULT_ERRMSG',
            'RESULT_ERR',
            '"raf.android-runtime-receipt.v3"',
            '"termux_error_code"',
            '"termux_error_message_sha256"',
            '"stdout_truncated"',
            '"stderr_truncated"',
            '"execution_receipt_present"',
            '"producer_apk_sha256"',
            '"provider_apk_sha256_discovery"',
            '"provider_binary_sha256_discovery"',
            '"provider_identity_scope", "DISCOVERY_NOT_EXECUTION"',
            '"executable_sha256_at_execution", "TOKEN_VAZIO"',
            '"provenance_chain_sha256"',
            '"provenance_chain_complete", false',
            '"guest_boot_artifact_sha256"',
            '"claim_allowed", false',
            'VectrasTermuxReceiptStore.writeReceipt(',
        ],
        "receiver",
        errors,
    )
    forbid(
        receiver,
        [
            'put("stdout", stdout)',
            'put("stderr", stderr)',
            'put("errmsg",',
            '"provenance_chain_complete", true',
        ],
        "receiver",
        errors,
    )

    for manifest in MANIFESTS:
        label = f"manifest:{manifest.parent.parent.name}"
        text = texts.get(label, "")
        require(
            text,
            [
                'com.termux.rafacodephi.permission.RUN_COMMAND',
                '<package android:name="com.termux.rafacodephi"',
                'com.vectras.vm.integration.VectrasTermuxResultReceiver',
                'android:exported="false"',
            ],
            label,
            errors,
        )

    state = "FAIL" if errors else "PASS_STATIC_CONTRACT"
    report = {
        "schema": SCHEMA,
        "cycle_id": "C07",
        "state": state,
        "claim_allowed": False,
        "protocol": "raf.vectras-termux-ipc.v3",
        "inputs": inputs,
        "checks": {
            "bounded_arguments": not any("MAX_" in item for item in errors),
            "request_persisted_before_dispatch": "writePending(" in bridge,
            "result_bound_to_pending_request": "loadPending(context, transactionId)" in receiver,
            "material_identity_discovery_bound": (
                '"provider_apk_sha256_discovery"' in receiver
                and '"provider_binary_sha256_discovery"' in receiver
            ),
            "execution_identity_not_promoted": (
                '"executable_sha256_at_execution", "TOKEN_VAZIO"' in receiver
                and '"provenance_chain_complete", false' in receiver
            ),
            "termux_error_metadata_preserved": '"termux_error_code"' in receiver,
            "raw_output_not_persisted": not any(
                forbidden in receiver
                for forbidden in ('put("stdout", stdout)', 'put("stderr", stderr)', 'put("errmsg",')
            ),
            "claim_boundary_preserved": '"claim_allowed", false' in receiver,
        },
        "runtime_boundary": {
            "android_build": "TOKEN_VAZIO",
            "permission_grant": "TOKEN_VAZIO",
            "dispatch_execution": "TOKEN_VAZIO",
            "termux_exit_receipt": "TOKEN_VAZIO",
            "executable_sha256_at_execution": "TOKEN_VAZIO",
            "qemu_guest_boot": "TOKEN_VAZIO",
        },
        "errors": errors,
        "falsifiers": [
            "request_not_persisted_before_mutable_pending_intent",
            "result_without_local_pending_request_accepted",
            "total_argument_bound_exceeded",
            "protected_qemu_option_overridden",
            "termux_internal_error_ignored",
            "truncated_output_reported_as_complete",
            "raw_private_output_persisted",
            "discovery_digest_promoted_to_execution_identity",
            "provenance_chain_marked_complete_without_execution_identity",
            "exit_code_promoted_to_guest_boot",
        ],
    }

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"state": state, "error_count": len(errors)}, sort_keys=True))
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
