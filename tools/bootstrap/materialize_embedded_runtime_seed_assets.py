#!/usr/bin/env python3
"""Materialize the small embedded PRoot + Alpine seed assets from a pinned upstream commit.

This tool intentionally does NOT download the large QEMU distribution bootstrap.
It closes only the APK embedded setup-seed layer used by SetupFeatureCore:
  bootstrap/<abi>.tar -> <filesDir>/usr/...
  alpine19/<abi>.tar  -> <filesDir>/distro/...

The source URL is constructed from repository + pinned commit + source_path in the
manifest. Arbitrary download URLs are not accepted. Every object is checked by
exact size, Git blob SHA-1, TAR safety, and family markers before atomic publish.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import posixpath
import tarfile
import tempfile
import urllib.error
import urllib.request
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "configs" / "embedded_runtime_seed_assets.v1.json"
DEFAULT_TARGET_ROOT = ROOT / "app" / "src" / "main" / "assets"
ALLOWED_REPOSITORY = "xoureldeen/Vectras-VM-Android"
ALLOWED_FAMILIES = {"bootstrap", "alpine19"}
ALLOWED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
FAMILY_MARKERS = {
    "bootstrap": {"usr/bin/proot"},
    "alpine19": {"bin/busybox", "bin/sh"},
}


def git_blob_sha1(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize_member_name(name: str) -> str:
    normalized = posixpath.normpath(name.replace("\\", "/"))
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def validate_tar(data: bytes, family: str) -> dict:
    names: set[str] = set()
    try:
        with tarfile.open(fileobj=io.BytesIO(data), mode="r:*") as tf:
            members = tf.getmembers()
            if not members:
                raise ValueError("empty TAR")
            for member in members:
                raw_name = member.name.replace("\\", "/")
                pure = PurePosixPath(raw_name)
                normalized = normalize_member_name(raw_name)
                if pure.is_absolute() or normalized.startswith("../") or normalized == "..":
                    raise ValueError(f"unsafe member path: {member.name}")
                if member.isdev():
                    raise ValueError(f"device node rejected: {member.name}")
                if member.issym() or member.islnk():
                    link = member.linkname.replace("\\", "/")
                    if PurePosixPath(link).is_absolute():
                        raise ValueError(f"absolute link target rejected: {member.name} -> {member.linkname}")
                    base = posixpath.dirname(normalized) if member.issym() else ""
                    resolved = posixpath.normpath(posixpath.join(base, link))
                    if resolved.startswith("../") or resolved == "..":
                        raise ValueError(f"escaping link target rejected: {member.name} -> {member.linkname}")
                names.add(normalized)
    except tarfile.TarError as exc:
        raise ValueError(f"unreadable TAR: {exc}") from exc

    missing_markers = sorted(FAMILY_MARKERS[family] - names)
    if missing_markers:
        raise ValueError(f"family={family} missing marker(s): {missing_markers}")
    return {"members": len(names), "markers": sorted(FAMILY_MARKERS[family])}


def load_manifest(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != "vectras.embedded-runtime-seed-assets.v1":
        raise ValueError("unexpected manifest schema")
    if data.get("source_repository") != ALLOWED_REPOSITORY:
        raise ValueError("source_repository is not the approved original upstream")
    commit = str(data.get("source_commit", ""))
    if len(commit) != 40 or any(c not in "0123456789abcdef" for c in commit):
        raise ValueError("source_commit must be a full lowercase 40-hex commit")
    return data


def parse_abis(raw: str) -> set[str]:
    abis = {item.strip() for item in raw.split(",") if item.strip()}
    if not abis:
        raise ValueError("at least one ABI is required")
    unknown = sorted(abis - ALLOWED_ABIS)
    if unknown:
        raise ValueError(f"unsupported ABI(s): {unknown}")
    return abis


def source_url(repository: str, commit: str, path: str) -> str:
    if path.startswith("/") or ".." in PurePosixPath(path).parts:
        raise ValueError(f"unsafe source_path: {path}")
    return f"https://raw.githubusercontent.com/{repository}/{commit}/{path}"


def download(url: str, timeout: int) -> bytes:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "Vectras-runtime-seed-materializer/1"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            if response.status != 200:
                raise RuntimeError(f"HTTP {response.status} for {url}")
            return response.read()
    except urllib.error.URLError as exc:
        raise RuntimeError(f"download failed for {url}: {exc}") from exc


def atomic_write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    tmp = Path(tmp_name)
    try:
        with os.fdopen(fd, "wb") as out:
            out.write(data)
            out.flush()
            os.fsync(out.fileno())
        os.chmod(tmp, 0o644)
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)


def validate_asset_record(asset: dict) -> None:
    family = asset.get("family")
    abi = asset.get("abi")
    if family not in ALLOWED_FAMILIES:
        raise ValueError(f"unsupported family: {family}")
    if abi not in ALLOWED_ABIS:
        raise ValueError(f"unsupported ABI: {abi}")
    target = PurePosixPath(str(asset.get("target_path", "")))
    expected_target = PurePosixPath(f"{family}/{abi}.tar")
    if target != expected_target:
        raise ValueError(f"target_path mismatch: {target} != {expected_target}")
    blob = str(asset.get("git_blob_sha1", ""))
    if len(blob) != 40 or any(c not in "0123456789abcdef" for c in blob):
        raise ValueError(f"invalid git_blob_sha1 for {family}/{abi}")
    if not isinstance(asset.get("size_bytes"), int) or asset["size_bytes"] <= 0:
        raise ValueError(f"invalid size_bytes for {family}/{abi}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--target-root", type=Path, default=DEFAULT_TARGET_ROOT)
    parser.add_argument(
        "--abis",
        default="arm64-v8a,armeabi-v7a,x86,x86_64",
        help="comma-separated subset to materialize",
    )
    parser.add_argument("--receipt", type=Path)
    parser.add_argument("--timeout", type=int, default=120)
    args = parser.parse_args()

    manifest = load_manifest(args.manifest)
    selected_abis = parse_abis(args.abis)
    repository = manifest["source_repository"]
    commit = manifest["source_commit"]
    selected = [a for a in manifest.get("assets", []) if a.get("abi") in selected_abis]

    expected_pairs = {(family, abi) for family in ALLOWED_FAMILIES for abi in selected_abis}
    actual_pairs = {(a.get("family"), a.get("abi")) for a in selected}
    if actual_pairs != expected_pairs:
        missing = sorted(expected_pairs - actual_pairs)
        extra = sorted(actual_pairs - expected_pairs)
        raise ValueError(f"manifest coverage mismatch missing={missing} extra={extra}")

    receipt_assets = []
    for asset in sorted(selected, key=lambda a: (a["family"], a["abi"])):
        validate_asset_record(asset)
        url = source_url(repository, commit, asset["source_path"])
        data = download(url, args.timeout)
        if len(data) != asset["size_bytes"]:
            raise ValueError(
                f"size mismatch {asset['family']}/{asset['abi']}: "
                f"expected={asset['size_bytes']} actual={len(data)}"
            )
        blob = git_blob_sha1(data)
        if blob != asset["git_blob_sha1"]:
            raise ValueError(
                f"Git blob SHA-1 mismatch {asset['family']}/{asset['abi']}: "
                f"expected={asset['git_blob_sha1']} actual={blob}"
            )
        tar_report = validate_tar(data, asset["family"])
        destination = (args.target_root / asset["target_path"]).resolve()
        target_root = args.target_root.resolve()
        if target_root not in destination.parents:
            raise ValueError(f"target escapes target-root: {destination}")
        atomic_write(destination, data)
        receipt_assets.append(
            {
                "family": asset["family"],
                "abi": asset["abi"],
                "source_url": url,
                "source_commit": commit,
                "source_path": asset["source_path"],
                "git_blob_sha1": blob,
                "sha256": sha256(data),
                "size_bytes": len(data),
                "target_path": str(destination.relative_to(target_root)),
                "tar_members": tar_report["members"],
                "markers_verified": tar_report["markers"],
            }
        )
        print(
            f"MATERIALIZED family={asset['family']} abi={asset['abi']} "
            f"bytes={len(data)} git_blob={blob}"
        )

    receipt = {
        "schema_version": "vectras.embedded-runtime-seed-materialization.v1",
        "status": "MATERIALIZED_VERIFIED_NOT_DEVICE_TESTED",
        "source_repository": repository,
        "source_commit": commit,
        "source_license": manifest.get("source_license", "TOKEN_VAZIO"),
        "selected_abis": sorted(selected_abis),
        "assets": receipt_assets,
        "qemu_distribution_materialized": False,
        "device_runtime_verified": False,
        "claim_allowed": False,
        "release_allowed": False,
    }
    rendered = json.dumps(receipt, indent=2, sort_keys=True) + "\n"
    if args.receipt:
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
