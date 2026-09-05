#!/usr/bin/env python3
"""Verify the pinned Vectras <-> Termux RAFCODE-Phi IPC v3 provenance chain.

The historical provider source commit and the commit that *declared* the provider
contract are intentionally distinct. This verifier binds both objects without
promoting static provenance to Android/QEMU/guest execution evidence.
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
SCHEMA = "raf.vectras-termux-cross-repo-provenance.v2"
SHA40 = re.compile(r"^[0-9a-f]{40}$")
EXPECTED_PROVIDER_REPO = "rafaelmeloreisnovo/termux-app-rafacodephi"
EXPECTED_CONSUMER_REPO = "rafaelmeloreisnovo/Vectras-VM-Android"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--provider-source-root", type=Path, required=True)
    parser.add_argument("--provider-contract-root", type=Path, required=True)
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


def checkout_head(root: Path, label: str, errors: list[str]) -> str:
    rc, value = git(root, "rev-parse", "HEAD")
    if rc != 0 or not SHA40.fullmatch(value):
        errors.append(f"cannot resolve {label} checkout HEAD: {value}")
        return ""
    return value


def main() -> int:
    args = parse_args()
    errors: list[str] = []
    provider_source_root = args.provider_source_root.resolve()
    provider_contract_root = args.provider_contract_root.resolve()

    consumer = read_json(CONSUMER_CONTRACT, "consumer contract", errors)
    provider = read_json(provider_contract_root / PROVIDER_CONTRACT_REL, "provider contract", errors)

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
    c_provenance = consumer.get("provenance", {})
    p_consumer = provider.get("consumer", {})
    p_provider = provider.get("provider", {})

    require_equal(errors, "consumer repository", c_consumer.get("repository"), EXPECTED_CONSUMER_REPO)
    require_equal(errors, "provider repository", c_provider.get("repository"), EXPECTED_PROVIDER_REPO)
    require_equal(errors, "provider mirror repository", p_provider.get("repository"), EXPECTED_PROVIDER_REPO)
    require_equal(errors, "consumer mirror repository", p_consumer.get("repository"), EXPECTED_CONSUMER_REPO)

    consumer_pin = valid_sha("consumer.base_commit", c_consumer.get("base_commit"), errors)
    provider_source_pin = valid_sha("provider.base_commit", c_provider.get("base_commit"), errors)
    authority_pin = valid_sha(
        "provenance.provider_contract_authority_commit",
        c_provenance.get("provider_contract_authority_commit"),
        errors,
    )
    provider_contract_source_pin = valid_sha(
        "provider-contract provider.base_commit", p_provider.get("base_commit"), errors
    )
    provider_contract_consumer_pin = valid_sha(
        "provider-contract consumer.base_commit", p_consumer.get("base_commit"), errors
    )

    require_equal(errors, "provider source pin mirror", provider_source_pin, provider_contract_source_pin)
    require_equal(errors, "consumer base pin mirror", consumer_pin, provider_contract_consumer_pin)

    consumer_head = checkout_head(ROOT, "consumer", errors)
    provider_source_head = checkout_head(provider_source_root, "provider source", errors)
    provider_contract_head = checkout_head(provider_contract_root, "provider contract authority", errors)

    if provider_source_pin and provider_source_head != provider_source_pin:
        errors.append(
            f"provider source checkout does not equal pinned source: {provider_source_head} != {provider_source_pin}"
        )
    if authority_pin and provider_contract_head != authority_pin:
        errors.append(
            f"provider contract checkout does not equal authority commit: {provider_contract_head} != {authority_pin}"
        )

    authority_parent = ""
    if provider_contract_head:
        parent_rc, authority_parent = git(provider_contract_root, "rev-parse", "HEAD^")
        if parent_rc != 0 or not SHA40.fullmatch(authority_parent):
            errors.append(f"cannot resolve provider contract authority parent: {authority_parent}")
            authority_parent = ""
        elif authority_parent != provider_source_pin:
            errors.append(
                "provider contract authority must directly declare the pinned source baseline: "
                f"parent={authority_parent} source_pin={provider_source_pin}"
            )

    expected_relation = c_provenance.get("provider_contract_source_relation")
    if expected_relation != "DIRECT_PARENT_DECLARATION":
        errors.append(
            "provenance.provider_contract_source_relation must be DIRECT_PARENT_DECLARATION"
        )

    consumer_relation = "TOKEN_VAZIO"
    if consumer_pin and consumer_head:
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
    require_equal(
        errors,
        "runner app-shell",
        consumer.get("request", {}).get("runner"),
        provider.get("runner", {}).get("app_shell"),
    )

    if consumer.get("claim_boundary", {}).get("claim_allowed") is not False:
        errors.append("consumer claim boundary must remain false")
    if provider.get("claim_boundary", {}).get("claim_allowed") is not False:
        errors.append("provider claim boundary must remain false")

    pair_currentness = "PINNED_HISTORICAL_BASELINE"
    report = {
        "schema": SCHEMA,
        "state": "FAIL" if errors else "PASS_PINNED_CONTRACT_CHAIN",
        "claim_allowed": False,
        "consumer": {
            "repository": EXPECTED_CONSUMER_REPO,
            "contract_source_pin": consumer_pin or "TOKEN_VAZIO",
            "checkout_head": consumer_head or "TOKEN_VAZIO",
            "pin_relation": consumer_relation,
        },
        "provider": {
            "repository": EXPECTED_PROVIDER_REPO,
            "source_pin": provider_source_pin or "TOKEN_VAZIO",
            "source_checkout_head": provider_source_head or "TOKEN_VAZIO",
            "contract_authority_commit": authority_pin or "TOKEN_VAZIO",
            "contract_checkout_head": provider_contract_head or "TOKEN_VAZIO",
            "contract_authority_parent": authority_parent or "TOKEN_VAZIO",
            "source_to_contract_relation": expected_relation or "TOKEN_VAZIO",
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
            "provider_source_checkout_not_exactly_pinned",
            "provider_contract_authority_checkout_not_exactly_pinned",
            "provider_contract_authority_parent_not_equal_source_pin",
            "consumer_and_provider_mirrored_pins_disagree",
            "consumer_pin_not_in_checked_out_lineage",
            "package_service_permission_or_action_disagree",
            "request_or_result_keys_disagree",
            "static_provenance_promoted_to_runtime_evidence",
        ],
    }

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {
                "state": report["state"],
                "pair_currentness": pair_currentness,
                "error_count": len(errors),
            },
            sort_keys=True,
        )
    )
    for error in errors:
        print(f"FAIL: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
