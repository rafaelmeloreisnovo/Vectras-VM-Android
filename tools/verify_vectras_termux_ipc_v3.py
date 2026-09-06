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
    "maintenance": ROOT / "app/src/main/java/com/vectras/vm/integration/VectrasTermuxMaintenanceCoordinator.kt",
    "application": ROOT / "app/src/main/java/com/vectras/vm/VectrasApp.java",
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


def require(text: str, snippets: tuple[str, ...], label: str, errors: list[str]) -> None:
    for snippet in snippets:
        if snippet not in text:
            errors.append(f"{label}: missing {snippet!r}")


def forbid(text: str, snippets: tuple[str, ...], label: str, errors: list[str]) -> None:
    for snippet in snippets:
        if snippet in text:
            errors.append(f"{label}: forbidden {snippet!r}")


def integer_constant(text: str, name: str) -> int | None:
    match = re.search(rf"const val\s+{re.escape(name)}\s*=\s*([0-9]+)", text)
    return int(match.group(1)) if match else None


def load_inputs(errors: list[str]) -> tuple[dict[str, str], dict[str, Any]]:
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
    return texts, inputs


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    texts, inputs = load_inputs(errors)

    contract: dict[str, Any] = {}
    raw_contract = texts.get("contract_json")
    if raw_contract:
        try:
            contract = json.loads(raw_contract)
        except json.JSONDecodeError as exc:
            errors.append(f"contract_json invalid: {exc}")

    if contract:
        if contract.get("schema") != "raf.vectras-termux-ipc-contract.v3":
            errors.append("contract schema mismatch")
        if contract.get("claim_allowed") is not False:
            errors.append("contract claim_allowed must be false")
        request = contract.get("request", {})
        fixed = request.get("fixed_arguments", [])
        if not isinstance(fixed, list):
            errors.append("fixed_arguments is not a list")
        elif request.get("max_total_arguments") != len(fixed) + request.get("max_extra_arguments", -1):
            errors.append("max_total_arguments != fixed + max_extra")
        if contract.get("security", {}).get("result_requires_local_pending_request") is not True:
            errors.append("local pending request binding is not required")

    contract_source = texts.get("contract_source", "")
    require(contract_source, (
        'PROTOCOL = "raf.vectras-termux-ipc.v3"',
        'TERMUX_PACKAGE = "com.termux.rafacodephi"',
        'SERVICE_CLASS = "com.termux.app.RunCommandService"',
        'RUNNER_APP_SHELL = "app-shell"',
        'MAX_TOTAL_ARGUMENTS = 32',
        'MAX_ARGUMENT_LENGTH = 256',
        'MAX_ARGUMENT_BYTES = 4096',
        'enum class MaintenanceStage(',
        'MAINTENANCE_RAFPROOT_PATH = "\\$PREFIX/libexec/rafproot-fs"',
        'MAINTENANCE_PKG_PATH = "\\$PREFIX/bin/pkg"',
        'listOf("--pkg-bootstrap")',
        'listOf("update", "-y")',
        'listOf("--pkg-vectras")',
        'listOf("--probe")',
        'listOf("--run", "proot", "--version")',
        'listOf("--run", "ninja", "--version")',
        'listOf("--run", "qemu-system-x86_64", "--version")',
        'boundedMaintenanceArguments(',
        'maintenanceStageFrom(',
        'canonicalMaintenanceRequest(',
    ), "contract_source", errors)
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
    require(manager, (
        'TERMUX_PACKAGE = "com.termux.rafacodephi"',
        'TERMUX_RUN_COMMAND_PERMISSION =',
        '"qemu_binary_names"',
        '"qemu_binary_sha256"',
        '"provider_apk_sha256"',
        '"private_paths_exposed"',
        'persistProviderIdentity(',
        'Context.RECEIVER_EXPORTED',
    ), "manager", errors)
    forbid(manager, ('"prefix_path"', '"qemu_binary_paths"', 'Context.RECEIVER_NOT_EXPORTED'), "manager", errors)

    bridge = texts.get("bridge", "")
    require(bridge, (
        'VectrasTermuxIpcContract.boundedArguments(arguments)',
        'VectrasTermuxIpcContract.boundedMaintenanceArguments(stage)',
        'VectrasTermuxReceiptStore.writePending(',
        'commandPath = commandPath',
        'requestKind = requestKind',
        'PendingIntent.FLAG_MUTABLE',
        'fun dispatchMaintenance(',
        'requestKind = "maintenance"',
        'transactionIdOverride = transactionId',
        'executionProven = false',
        'claimAllowed = false',
    ), "bridge", errors)
    forbid(bridge, ('Runtime.getRuntime().exec', 'ProcessBuilder(', '/data/data/com.termux'), "bridge", errors)

    store = texts.get("store", "")
    require(store, (
        'REQUEST_SCHEMA = "raf.vectras-termux-request.v4"',
        '"command_path"',
        '"request_kind"',
        '"arguments_sha256"',
        'hashArguments(arguments)',
        'if (hashArguments(arguments) != argumentsSha256) return null',
        'if (target.exists()) return false',
        '"claim_allowed", false',
    ), "store", errors)

    receiver = texts.get("receiver", "")
    require(receiver, (
        'VectrasTermuxReceiptStore.loadPending(context, transactionId)',
        'VectrasTermuxBridge.allowedReceiptTargets()',
        '"raf.android-runtime-receipt.v3"',
        '"termux_error_code"',
        '"execution_receipt_present"',
        '"command_path"',
        '"request_kind"',
        '"provenance_chain_complete", false',
        '"claim_allowed", false',
        'VectrasTermuxReceiptStore.writeReceipt(',
        'if (persisted && pending.requestKind == "maintenance")',
        'VectrasTermuxMaintenanceCoordinator.onExecutionResult(',
    ), "receiver", errors)
    forbid(receiver, (
        'put("stdout", stdout)',
        'put("stderr", stderr)',
        'put("errmsg",',
        '"provenance_chain_complete", true',
    ), "receiver", errors)

    maintenance = texts.get("maintenance", "")
    require(maintenance, (
        'BOOTSTRAP -> REFRESH -> VECTRAS_QEMU -> PROBE -> PROOT_VERIFY -> NINJA_VERIFY -> QEMU_VERIFY',
        'current.expectedStage != stage',
        'current.expectedTransaction != pending.transactionId',
        'resultBundlePresent && errorCode == 0 && exitCode == 0',
        'MaintenanceStage.BOOTSTRAP ->',
        'MaintenanceStage.REFRESH ->',
        'MaintenanceStage.VECTRAS_QEMU ->',
        'MaintenanceStage.PROBE ->',
        'MaintenanceStage.PROOT_VERIFY ->',
        'MaintenanceStage.NINJA_VERIFY ->',
        'MaintenanceStage.QEMU_VERIFY -> null',
        'State.PROOT_VERIFY_PENDING',
        'State.NINJA_VERIFY_PENDING',
        'State.QEMU_VERIFY_PENDING',
        'bounded_maintenance_and_runtime_smokes_complete',
        'maintenance_already_in_progress',
    ), "maintenance", errors)
    forbid(maintenance, ('Runtime.getRuntime().exec', 'ProcessBuilder(', 'Thread.sleep(', '/data/data/com.termux'), "maintenance", errors)

    application = texts.get("application", "")
    require(application, (
        'activity instanceof MainActivity',
        'SetupFeatureCore.isInstalledSystemFiles(activity)',
        'SetupFeatureCore.isInstalledQemu(activity)',
        'VectrasTermuxMaintenanceCoordinator.offerRepairIfNeeded(activity)',
    ), "application", errors)

    for manifest in MANIFESTS:
        label = f"manifest:{manifest.parent.parent.name}"
        require(texts.get(label, ""), (
            'com.termux.rafacodephi.permission.RUN_COMMAND',
            '<package android:name="com.termux.rafacodephi"',
            'com.vectras.vm.integration.VectrasTermuxResultReceiver',
            'android:exported="false"',
        ), label, errors)

    maintenance_order_bound = all(token in maintenance for token in (
        'MaintenanceStage.PROBE ->',
        'MaintenanceStage.PROOT_VERIFY ->',
        'MaintenanceStage.NINJA_VERIFY ->',
        'MaintenanceStage.QEMU_VERIFY -> null',
    ))
    runtime_smokes_bound = all(token in contract_source for token in (
        'listOf("--run", "proot", "--version")',
        'listOf("--run", "ninja", "--version")',
        'listOf("--run", "qemu-system-x86_64", "--version")',
    ))

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
            "material_identity_discovery_bound": '"provider_apk_sha256_discovery"' in receiver,
            "execution_identity_not_promoted": (
                '"executable_sha256_at_execution", "TOKEN_VAZIO"' in receiver
                and '"provenance_chain_complete", false' in receiver
            ),
            "termux_error_metadata_preserved": '"termux_error_code"' in receiver,
            "raw_output_not_persisted": not any(
                token in receiver for token in ('put("stdout", stdout)', 'put("stderr", stderr)', 'put("errmsg",')
            ),
            "claim_boundary_preserved": '"claim_allowed", false' in receiver,
            "maintenance_argv_bounded": 'boundedMaintenanceArguments(stage)' in bridge,
            "maintenance_receipt_driven": 'if (persisted && pending.requestKind == "maintenance")' in receiver,
            "maintenance_order_bound": maintenance_order_bound,
            "maintenance_transaction_bound": 'current.expectedTransaction != pending.transactionId' in maintenance,
            "runtime_smokes_bound": runtime_smokes_bound,
        },
        "runtime_boundary": {
            "android_build": "TOKEN_VAZIO",
            "permission_grant": "TOKEN_VAZIO",
            "dispatch_execution": "TOKEN_VAZIO",
            "termux_exit_receipt": "TOKEN_VAZIO",
            "maintenance_complete": "TOKEN_VAZIO",
            "proot_smoke": "TOKEN_VAZIO",
            "ninja_smoke": "TOKEN_VAZIO",
            "qemu_smoke": "TOKEN_VAZIO",
            "executable_sha256_at_execution": "TOKEN_VAZIO",
            "qemu_guest_boot": "TOKEN_VAZIO",
        },
        "errors": errors,
        "falsifiers": [
            "result_without_local_pending_request_accepted",
            "maintenance_free_form_command_accepted",
            "maintenance_stage_advanced_without_zero_exit_receipt",
            "maintenance_stale_transaction_advanced_state",
            "maintenance_completed_after_probe_without_runtime_smokes",
            "proot_ninja_or_qemu_smoke_missing",
            "raw_private_output_persisted",
            "exit_code_promoted_to_guest_boot",
        ],
    }

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"state": state, "error_count": len(errors)}, sort_keys=True))
    for error in errors:
        print(f"FAIL: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
