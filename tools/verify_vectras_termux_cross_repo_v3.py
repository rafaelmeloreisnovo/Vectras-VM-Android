#!/usr/bin/env python3
"""Verify the pinned Vectras <-> Termux RAFCODE-Phi IPC v3 contract pair.

This gate is provenance-oriented. It does not promote static compatibility to
Android execution, QEMU execution, guest boot, or VM correctness.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
CONSUMER_CONTRACT = ROOT / "docs/contracts/VECTRAS_TERMUX_IPC_V3.json"
PROVIDER_CONTRACT_REL = Path("docs/contracts/VECTRAS_TERMUX_PROVIDER_V3.json")
SCHEMA = "raf.vectras-termux-cross-repo-provenance.v1"
SHA40 = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_PROVIDER_REPO = "rafaelmeloreisnovo/termux-app-rafacodephi"
EXPECTED_CONSUMER_REPO = "rafaelmeloreisnovo/Vectras-VM-Android"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--provider-root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    return parser.parse_args()


def read_json(path: Path, label: str, errors: list[str]) -> dict[str, Any]:
    if not path.is_file():
        errors.append(f"missing {label}: {path}")
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        errors.append(f"invalid {label} JSON: {exc}")
        return {}
    if not isinstance(value, dict):
        errors.append(f"{label} must be a JSON object")
        return {}
    return value


def git(root: Path, *args: str) -> tuple[int, str]:
    proc = subprocess.run(
        ["git", "-C", str(root), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return proc.returncode, proc.stdout.strip()


def require_equal(errors: list[str], label: str, left: Any, right: Any) -> None:
    if left != right:
        errors.append(f"{label} mismatch: {left!r} != {right!r}")


def valid_sha(label: str, value: Any, errors: list[str]) -> str:
    text = value if isinstance(value, str) else ""
    if not SHA40.fullmatch(text):
        errors.append(f"{label} is not a lowercase 40-hex commit SHA: {value!r}")
    return text


def request_key_map(consumer: dict[str, Any]) -> dict[str, Any]:
    request = consumer.get("request", {})
    return {
        "command_path": request.get("command_path_key"),
        "arguments": request.get("arguments_key"),
        "workdir": request.get("workdir_key"),
        "runner": request.get("runner_key"),
        "pending_intent": request.get("pending_intent_key"),
    }


def result_key_map(consumer: dict[str, Any]) -> dict[str, Any]:
    result = consumer.get("result", {})
    return {
        "bundle": result.get("bundle_key"),
        "stdout": result.get("stdout_key"),
        "stdout_original_length": result.get("stdout_original_length_key"),
        "stderr": result.get("stderr_key"),
        "stderr_original_length": result.get("stderr_original_length_key"),
        "exit_code": result.get("exit_code_key"),
        "error_code": result.get("error_code_key"),
        "error_message": result.get("error_message_key"),
    }


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    provider_root = args.provider_root.resolve()

    consumer = read_json(CONSUMER_CONTRACT, "consumer contract", errors)
    provider = read_json(provider_root / PROVIDER_CONTRACT_REL, "provider contract", errors)

    if consumer.get("schema") != "raf.vectras-termux-ipc-contract.v3":
        errors.append("consumer contract schema mismatch")
    if provider.get("schema") != "raf.termux-run-command-provider.v3":
        errors.append("provider contract schema mismatch")
    if consumer.get("claim_allowed") is not False:
        errors.append("consumer claim_allowed must remain false")
    if provider.get("claim_allowed") is not False:
        errors.append("provider claim_allowed must remain false")

    c_consumer = consumer.get("consumer", {})
    c_provider = consumer.get("provider", {})
    p_consumer = provider.get("consumer", {})
    p_provider = provider.get("provider", {})

    require_equal(errors, "consumer repository", c_consumer.get("repository"), EXPECTED_CONSUMER_REPO)
    require_equal(errors, "provider repository", c_provider.get("repository"), EXPECTED_PROVIDER_REPO)
    require_equal(errors, "provider mirror repository", p_provider.get("repository"), EXPECTED_PROVIDER_REPO)
    require_equal(errors, "consumer mirror repository", p_consumer.get("repository"), EXPECTED_CONSUMER_REPO)

    consumer_pin = valid_sha("consumer.base_commit", c_consumer.get("base_commit"), errors)
    provider_pin = valid_sha("provider.base_commit", c_provider.get("base_commit"), errors)
    provider_self_pin = valid_sha("provider-contract provider.base_commit", p_provider.get("base_commit"), errors)
    provider_consumer_pin = valid_sha("provider-contract consumer.base_commit", p_consumer.get("base_commit"), errors)

    require_equal(errors, "provider base commit mirror", provider_pin, provider_self_pin)
    require_equal(errors, "consumer base commit mirror", consumer_pin, provider_consumer_pin)

    consumer_head_rc, consumer_head = git(ROOT, "rev-parse", "HEAD")
    if consumer_head_rc != 0 or not SHA40.fullmatch(consumer_head):
        errors.append(f"cannot resolve consumer checkout HEAD: {consumer_head}")
    provider_head_rc, provider_head = git(provider_root, "rev-parse", "HEAD")
    if provider_head_rc != 0 or not SHA40.fullmatch(provider_head):
        errors.append(f"cannot resolve provider checkout HEAD: {provider_head}")
    elif provider_pin and provider_head != provider_pin:
        errors.append(f"provider checkout does not equal pinned commit: {provider_head} != {provider_pin}")

    consumer_relation = "TOKEN_VAZIO"
    if consumer_pin and consumer_head_rc == 0:
        if consumer_head == consumer_pin:
            consumer_relation = "EXACT"
        else:
            ancestor_rc, _ = git(ROOT, "merge-base", "--is-ancestor", consumer_pin, consumer_head)
            if ancestor_rc == 0:
                consumer_relation = "DESCENDANT_OF_PINNED_BASELINE"
            else:
                consumer_relation = "DIVERGED_OR_UNAVAILABLE"
                errors.append("consumer pinned baseline is not an ancestor of the checked-out source")

    require_equal(errors, "protocol", p_consumer.get("protocol"), "raf.vectras-termux-ipc.v3")
    require_equal(errors, "package", c_provider.get("package"), p_provider.get("package_default"))
    for field in ("service_class", "permission", "action"):
        require_equal(errors, f"provider.{field}", c_provider.get(field), p_provider.get(field))

    require_equal(errors, "request keys", request_key_map(consumer), provider.get("request_keys", {}))
    require_equal(errors, "result keys", result_key_map(consumer), provider.get("result_keys", {}))
    require_equal(errors, "runner app-shell", consumer.get("request", {}).get("runner"), provider.get("runner", {}).get("app_shell"))

    if consumer.get("claim_boundary", {}).get("claim_allowed") is not False:
        errors.append("consumer claim boundary must remain false")
    if provider.get("claim_boundary", {}).get("claim_allowed") is not False:
        errors.append("provider claim boundary must remain false")

    pair_currentness = "PINNED_HISTORICAL_BASELINE"
    if consumer_relation == "EXACT" and provider_head == provider_pin:
        pair_currentness = "PINNED_EXACT_PAIR"

    report = {
        "schema": SCHEMA,
        "state": "FAIL" if errors else "PASS_PINNED_CONTRACT_PAIR",
        "claim_allowed": False,
        "consumer": {
            "repository": EXPECTED_CONSUMER_REPO,
            "contract_pin": consumer_pin or "TOKEN_VAZIO",
            "checkout_head": consumer_head if consumer_head_rc == 0 else "TOKEN_VAZIO",
            "pin_relation": consumer_relation,
        },
        "provider": {
            "repository": EXPECTED_PROVIDER_REPO,
            "contract_pin": provider_pin or "TOKEN_VAZIO",
            "checkout_head": provider_head if provider_head_rc == 0 else "TOKEN_VAZIO",
            "pin_relation": "EXACT" if provider_head == provider_pin and provider_pin else "MISMATCH",
        },
        "pair_currentness": pair_currentness,
        "semantic_pair": {
            "package": c_provider.get("package", "TOKEN_VAZIO"),
            "service_class": c_provider.get("service_class", "TOKEN_VAZIO"),
            "permission": c_provider.get("permission", "TOKEN_VAZIO"),
            "action": c_provider.get("action", "TOKEN_VAZIO"),
            "request_keys_match": request_key_map(consumer) == provider.get("request_keys", {}),
            "result_keys_match": result_key_map(consumer) == provider.get("result_keys", {}),
        },
        "runtime_boundary": {
            "android_build": "TOKEN_VAZIO",
            "provider_installed": "TOKEN_VAZIO",
            "permission_granted": "TOKEN_VAZIO",
            "dispatch_execution": "TOKEN_VAZIO",
            "qemu_execution": "TOKEN_VAZIO",
            "guest_boot": "TOKEN_VAZIO",
        },
        "errors": errors,
        "falsifiers": [
            "consumer_and_provider_contract_pins_disagree",
            "provider_checkout_not_exactly_pinned",
            "consumer_pin_not_in_checked_out_lineage",
            "package_service_permission_or_action_disagree",
            "request_or_result_keys_disagree",
            "static_pair_promoted_to_runtime_evidence",
        ],
    }

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"state": report["state"], "pair_currentness": pair_currentness, "error_count": len(errors)}, sort_keys=True))
    for error in errors:
        print(f"FAIL: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
