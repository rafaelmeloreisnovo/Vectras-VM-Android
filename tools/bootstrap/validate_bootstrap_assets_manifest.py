#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import tarfile
from pathlib import Path, PurePosixPath
from typing import Any

EXPECTED_FILES = {
    "arm64-v8a": "arm64-v8a.tar",
    "armeabi-v7a": "armeabi-v7a.tar",
    "x86": "x86.tar",
    "x86_64": "x86_64.tar",
}
TOKEN_PREFIX = "TOKEN_VAZIO"
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
BLOCKED_STATE = "BETA_BLOCKED_MISSING_BOOTSTRAP_ASSETS"
VERIFIED_STATE = "BOOTSTRAP_ASSETS_VERIFIED_NOT_DEVICE_TESTED"
DEVICE_STATE = "DEVICE_BOOTSTRAP_VERIFIED_LIMITED"


class ContractError(ValueError):
    pass


def fail(message: str) -> None:
    raise ContractError(message)


def is_token(value: Any) -> bool:
    return isinstance(value, str) and value.startswith(TOKEN_PREFIX)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_tar(path: Path) -> dict[str, int]:
    members = 0
    regular_files = 0
    unpacked_bytes = 0
    try:
        with tarfile.open(path, "r:*") as archive:
            for member in archive.getmembers():
                members += 1
                name = PurePosixPath(member.name)
                if name.is_absolute() or ".." in name.parts or not name.parts:
                    fail(f"{path.name}: unsafe TAR member {member.name!r}")
                if member.issym() or member.islnk():
                    target = PurePosixPath(member.linkname)
                    if target.is_absolute() or ".." in target.parts:
                        fail(f"{path.name}: unsafe TAR link {member.name!r} -> {member.linkname!r}")
                if member.isdev():
                    fail(f"{path.name}: device node is not allowed: {member.name!r}")
                if member.isfile():
                    regular_files += 1
                    unpacked_bytes += member.size
    except (tarfile.TarError, OSError) as exc:
        fail(f"{path.name}: unreadable TAR: {exc}")
    if members == 0 or regular_files == 0:
        fail(f"{path.name}: TAR must contain at least one regular file")
    return {
        "tar_members": members,
        "tar_regular_files": regular_files,
        "tar_unpacked_bytes": unpacked_bytes,
    }


def validate_structure(data: dict[str, Any]) -> list[dict[str, Any]]:
    if data.get("schema_version") != "bootstrap-assets.production.v1":
        fail("schema_version")
    if data.get("claim_allowed") is not False:
        fail("claim_allowed must remain false in V1")
    if data.get("release_allowed") is not False:
        fail("release_allowed must remain false in V1")
    if data.get("android_runtime_verified") is not False:
        fail("android_runtime_verified must remain false in V1")
    if data.get("source_directory") != "app/src/main/assets/bootstrap":
        fail("source_directory")
    if data.get("generated_directory") != "app/build/generated/bootstrapAssets/bootstrap":
        fail("generated_directory")

    loader = data.get("loader")
    if not isinstance(loader, dict):
        fail("loader")
    if loader.get("filename") != "loader.apk":
        fail("loader filename")
    if loader.get("producer") != ":shell-loader:assembleDebug":
        fail("loader producer")
    if loader.get("substitutes_for_architecture_tar") is not False:
        fail("loader cannot substitute architecture TARs")

    assets = data.get("required_assets")
    if not isinstance(assets, list) or len(assets) != 4:
        fail("required_assets must contain exactly four ABIs")
    by_abi: dict[str, dict[str, Any]] = {}
    for asset in assets:
        if not isinstance(asset, dict):
            fail("asset must be an object")
        abi = asset.get("abi")
        if abi not in EXPECTED_FILES or abi in by_abi:
            fail(f"invalid or duplicate ABI: {abi!r}")
        if asset.get("filename") != EXPECTED_FILES[abi]:
            fail(f"{abi}: filename must be {EXPECTED_FILES[abi]}")
        by_abi[abi] = asset
    if set(by_abi) != set(EXPECTED_FILES):
        fail("ABI set mismatch")

    claims = data.get("claims")
    if not isinstance(claims, dict):
        fail("claims")
    for key in (
        "official_bootstrap_assets_present",
        "beta_installable",
        "apk_runtime_verified",
        "device_bootstrap_verified",
        "claim_allowed",
    ):
        if claims.get(key) is not False:
            fail(f"claim overpromoted: {key}")
    return [by_abi[abi] for abi in EXPECTED_FILES]


def validate_blocked(data: dict[str, Any], assets: list[dict[str, Any]], assets_dir: Path | None) -> dict[str, Any]:
    if data.get("state") != BLOCKED_STATE:
        fail(f"blocked manifest state must be {BLOCKED_STATE}")
    missing: list[str] = []
    for asset in assets:
        abi = asset["abi"]
        if asset.get("state") != "MISSING":
            fail(f"{abi}: blocked asset state must be MISSING")
        for field in ("source_uri", "source_ref", "license_or_provenance", "sha256"):
            if not is_token(asset.get(field)):
                fail(f"{abi}: {field} must remain TOKEN_VAZIO while missing")
        if asset.get("size_bytes") is not None:
            fail(f"{abi}: size_bytes must be null while missing")
        if assets_dir is not None and (assets_dir / asset["filename"]).exists():
            fail(f"{abi}: file exists but manifest still says MISSING")
        missing.append(asset["filename"])
    return {
        "state": BLOCKED_STATE,
        "ready": False,
        "missing": missing,
        "verified": [],
    }


def validate_verified(data: dict[str, Any], assets: list[dict[str, Any]], assets_dir: Path | None) -> dict[str, Any]:
    if data.get("state") not in {VERIFIED_STATE, DEVICE_STATE}:
        fail(f"verified manifest state must be {VERIFIED_STATE} or {DEVICE_STATE}")
    if assets_dir is None:
        fail("--assets-dir is required for verified state")
    verified: list[dict[str, Any]] = []
    for asset in assets:
        abi = asset["abi"]
        if asset.get("state") != "VERIFIED":
            fail(f"{abi}: state must be VERIFIED")
        for field in ("source_uri", "source_ref", "license_or_provenance"):
            value = asset.get(field)
            if not isinstance(value, str) or not value or is_token(value):
                fail(f"{abi}: concrete {field} is required")
        expected_sha = asset.get("sha256")
        if not isinstance(expected_sha, str) or not SHA256_RE.fullmatch(expected_sha):
            fail(f"{abi}: concrete lowercase SHA-256 is required")
        expected_size = asset.get("size_bytes")
        if not isinstance(expected_size, int) or expected_size <= 0:
            fail(f"{abi}: positive size_bytes is required")
        path = assets_dir / asset["filename"]
        if not path.is_file():
            fail(f"{abi}: missing file {path}")
        actual_size = path.stat().st_size
        if actual_size != expected_size:
            fail(f"{abi}: size mismatch expected={expected_size} actual={actual_size}")
        actual_sha = sha256_file(path)
        if actual_sha != expected_sha:
            fail(f"{abi}: SHA-256 mismatch expected={expected_sha} actual={actual_sha}")
        tar_report = validate_tar(path)
        verified.append({
            "abi": abi,
            "filename": asset["filename"],
            "size_bytes": actual_size,
            "sha256": actual_sha,
            **tar_report,
        })
    return {
        "state": data["state"],
        "ready": True,
        "missing": [],
        "verified": verified,
    }


def validate(data: dict[str, Any], expected_state: str, assets_dir: Path | None) -> dict[str, Any]:
    assets = validate_structure(data)
    if expected_state == "blocked":
        return validate_blocked(data, assets, assets_dir)
    if expected_state == "verified":
        return validate_verified(data, assets, assets_dir)
    fail(f"unknown expected state: {expected_state}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("configs/bootstrap_assets.production.v1.json"),
    )
    parser.add_argument("--assets-dir", type=Path)
    parser.add_argument("--expect", choices=("blocked", "verified"), required=True)
    parser.add_argument("--write-report", type=Path)
    args = parser.parse_args()

    raw = json.loads(args.manifest.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        fail("manifest root must be an object")
    report = validate(raw, args.expect, args.assets_dir)
    report.update({
        "schema_version": "bootstrap-assets-validation.v1",
        "manifest": str(args.manifest),
        "claim_allowed": False,
        "release_allowed": False,
    })
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.write_report:
        args.write_report.parent.mkdir(parents=True, exist_ok=True)
        args.write_report.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
