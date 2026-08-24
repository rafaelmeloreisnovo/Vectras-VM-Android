#!/usr/bin/env python3
"""Generate reproducible build-side evidence and an embedded build context.

The pre-build context is packaged under assets/evidence/build-context.json so the
on-device catalog can tie the installed APK back to the exact build inputs. When
--apk is supplied, the same tool emits a post-build evidence artifact containing
APK SHA-256, ZIP inventory and provenance-receipt digests.

This is evidence support, not a certification or physical execution claim.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_EMBEDDED = ROOT / "app" / "build" / "generated" / "bootstrapAssets" / "evidence" / "build-context.json"

CONTRACT_FILES = [
    "app/build.gradle",
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/cpp/CMakeLists.txt",
    "configs/embedded_runtime_seed_assets.v1.json",
    "tools/ci/bootstrap-assets.v1.json",
    "tools/ci/external_sources.manifest",
]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def git(*args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(ROOT), *args],
            text=True,
            stderr=subprocess.STDOUT,
        ).strip()
    except Exception:
        return "TOKEN_VAZIO"


def java_version() -> str:
    try:
        process = subprocess.run(
            ["java", "-version"],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        lines = (process.stdout or "").splitlines()
        return lines[0].strip() if lines else "TOKEN_VAZIO"
    except Exception:
        return "TOKEN_VAZIO"


def contract_hashes() -> list[dict]:
    rows: list[dict] = []
    for relative in CONTRACT_FILES:
        path = ROOT / relative
        rows.append(
            {
                "path": relative,
                "present": path.is_file(),
                "sha256": sha256_file(path) if path.is_file() else "TOKEN_VAZIO",
                "size_bytes": path.stat().st_size if path.is_file() else 0,
            }
        )
    return rows


def ci_context() -> dict:
    names = [
        "GITHUB_ACTIONS",
        "GITHUB_REPOSITORY",
        "GITHUB_WORKFLOW",
        "GITHUB_RUN_ID",
        "GITHUB_RUN_ATTEMPT",
        "GITHUB_SHA",
        "GITHUB_REF",
        "GITHUB_HEAD_REF",
        "GITHUB_BASE_REF",
        "GITHUB_EVENT_NAME",
        "RUNNER_OS",
        "RUNNER_ARCH",
    ]
    return {name.lower(): os.environ.get(name, "TOKEN_VAZIO") for name in names}


def base_context(args: argparse.Namespace) -> dict:
    status = subprocess.run(
        ["git", "-C", str(ROOT), "status", "--porcelain"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    dirty: bool | str = bool((status.stdout or "").strip()) if status.returncode == 0 else "TOKEN_VAZIO"
    return {
        "schema_version": "vectras.build-evidence.v1",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "record_kind": "BUILD_CONTEXT",
        "repository": os.environ.get("GITHUB_REPOSITORY", "rafaelmeloreisnovo/Vectras-VM-Android"),
        "source": {
            "git_head": git("rev-parse", "HEAD"),
            "git_describe": git("describe", "--always", "--dirty", "--tags"),
            "git_worktree_dirty": dirty,
            "git_ref": os.environ.get("GITHUB_REF", "TOKEN_VAZIO"),
        },
        "lane": {
            "name": args.lane,
            "abi_policy": args.policy,
            "supported_abis": [item.strip() for item in args.abis.split(",") if item.strip()],
        },
        "toolchain_observation": {
            "python": platform.python_version(),
            "java": java_version(),
            "host_platform": platform.platform(),
            "machine": platform.machine(),
            "android_sdk_root_present": bool(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")),
        },
        "ci": ci_context(),
        "source_contract_hashes": contract_hashes(),
        "evidence_principles": [
            "identity",
            "provenance",
            "integrity",
            "traceability",
            "reproducibility",
            "uncertainty_separation",
            "chain_of_custody",
        ],
        "normative_boundary": {
            "profile": "EVIDENCE_SUPPORT_NOT_CERTIFICATION",
            "claim_allowed": False,
            "device_runtime_verified": False,
            "note": (
                "This record supports audit/scientific traceability; it is not by itself "
                "a certification or physical-device execution claim."
            ),
        },
    }


def read_json(path: Path | None):
    if not path:
        return None
    if not path.is_file():
        return {"status": "TOKEN_VAZIO", "path": str(path)}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        return {"status": "UNREADABLE", "path": str(path), "error": type(exc).__name__}


def zip_inventory(apk: Path) -> dict:
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
    return {
        "entries": len(names),
        "native_libraries": sorted(name for name in names if name.startswith("lib/") and name.endswith(".so")),
        "embedded_evidence": sorted(name for name in names if name.startswith("assets/evidence/")),
        "runtime_seed_entries": sorted(
            name
            for name in names
            if name.startswith("assets/bootstrap/") or name.startswith("assets/alpine19/")
        ),
        "has_android_manifest": "AndroidManifest.xml" in names,
        "dex_entries": sorted(name for name in names if name.startswith("classes") and name.endswith(".dex")),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lane", required=True)
    parser.add_argument("--policy", required=True)
    parser.add_argument("--abis", required=True)
    parser.add_argument("--embedded-out", type=Path, default=DEFAULT_EMBEDDED)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--payload-receipt", type=Path)
    parser.add_argument("--bootstrap-receipt", type=Path)
    parser.add_argument("--runtime-seed-receipt", type=Path)
    args = parser.parse_args()

    record = base_context(args)
    args.embedded_out.parent.mkdir(parents=True, exist_ok=True)
    args.embedded_out.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"[build-evidence] embedded_context={args.embedded_out}")

    if args.apk:
        apk = args.apk.resolve()
        if not apk.is_file():
            raise SystemExit(f"APK not found: {apk}")

        output = dict(record)
        output["record_kind"] = "BUILD_OUTPUT_EVIDENCE"
        output["apk"] = {
            "path": str(apk.relative_to(ROOT)) if ROOT in apk.parents else apk.name,
            "size_bytes": apk.stat().st_size,
            "sha256": sha256_file(apk),
            "zip_inventory": zip_inventory(apk),
        }
        output["receipts"] = {
            "runtime_payload": read_json(args.payload_receipt),
            "bootstrap_materialization": read_json(args.bootstrap_receipt),
            "runtime_seed_materialization": read_json(args.runtime_seed_receipt),
        }
        output["receipt_digests"] = {}
        for key, path in {
            "runtime_payload": args.payload_receipt,
            "bootstrap_materialization": args.bootstrap_receipt,
            "runtime_seed_materialization": args.runtime_seed_receipt,
        }.items():
            output["receipt_digests"][key] = (
                sha256_file(path) if path and path.is_file() else "TOKEN_VAZIO"
            )
        output["claims"] = {
            "apk_built": True,
            "apk_payload_statically_verified": bool(args.payload_receipt and args.payload_receipt.is_file()),
            "device_installed": False,
            "device_runtime_verified": False,
            "physical_vm_launch_verified": False,
            "claim_allowed": False,
        }

        if not args.out:
            raise SystemExit("--out required when --apk is supplied")
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"[build-evidence] output={args.out} sha256={sha256_file(args.out)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
