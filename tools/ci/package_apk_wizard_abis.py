#!/usr/bin/env python3
"""Fail-closed APK Wizard ZIP distribution, separated by installed Android ABI.

This packaging gate does NOT rebuild, resign or mutate an APK. It checks the
original APK against the existing runtime/build receipts before distribution.
Native ABI exclusivity is enforced; embedded TAR ABIs are inventoried honestly.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import zipfile
from pathlib import Path

LANES = (
    ("arm64-v8a", "app-debug-arm64-v8a", "arm64-only", ("arm64-v8a",)),
    ("armeabi-v7a", "app-debug-armeabi-v7a", "arm32-debug", ("armeabi-v7a",)),
    ("arm32-arm64", "app-debug-arm32-arm64", "arm32-arm64", ("arm64-v8a", "armeabi-v7a")),
)
KNOWN_ABIS = frozenset(("arm64-v8a", "armeabi-v7a", "x86", "x86_64", "riscv64"))
FAMILIES = ("bootstrap", "alpine19", "qemu19")


class PackageError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PackageError(message)


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def member_digest(archive: zipfile.ZipFile, name: str) -> str:
    h = hashlib.sha256()
    with archive.open(name) as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def json_file(path: Path) -> dict:
    require(path.is_file(), f"missing receipt: {path.name}")
    try:
        obj = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PackageError(f"invalid receipt {path.name}: {type(exc).__name__}") from exc
    require(isinstance(obj, dict), f"not a JSON object: {path.name}")
    return obj


def apk_inventory(apk: Path, expected: tuple[str, ...]) -> tuple[list[str], dict]:
    require(apk.is_file() and apk.stat().st_size > 0, f"missing/empty APK: {apk.name}")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), f"duplicate ZIP paths: {apk.name}")
        entries = set(names)
        require("AndroidManifest.xml" in entries, f"missing AndroidManifest.xml: {apk.name}")
        require(any(re.fullmatch(r"classes\d*\.dex", n) for n in entries),
                f"missing classes DEX: {apk.name}")
        native = sorted(n for n in names if n.startswith("lib/") and n.endswith(".so"))
        native_abis = sorted({n.split("/")[1] for n in native if len(n.split("/")) >= 3})
        require(native_abis == sorted(expected),
                f"ABI contamination in {apk.name}: expected={expected} actual={native_abis}")
        for abi in expected:
            for leaf in ("libtermux-bootstrap.so", "libtermux_terminal_jni.so"):
                require(f"lib/{abi}/{leaf}" in entries, f"missing native {abi}/{leaf}")
        for family in FAMILIES:
            for abi in expected:
                require(f"assets/{family}/{abi}.tar" in entries,
                        f"missing APK-local runtime {family}/{abi}")
        require("assets/bootstrap/loader.apk" in entries, f"missing loader.apk: {apk.name}")
        embedded = {
            family: sorted(
                abi for abi in KNOWN_ABIS
                if f"assets/{family}/{abi}.tar" in entries
            ) for family in FAMILIES
        }
        return native, embedded


def validate_lane(root: Path, lane: str, stem: str, policy: str,
                  abis: tuple[str, ...], commit: str) -> tuple[Path, dict, list[Path]]:
    apk = root / f"{stem}.apk"
    runtime_path = root / f"{stem}.runtime-payload.json"
    evidence_path = root / f"{stem}.build-evidence.json"
    native, embedded = apk_inventory(apk, abis)
    runtime = json_file(runtime_path)
    evidence = json_file(evidence_path)
    sha = digest(apk)

    require(runtime.get("sha256") == sha, f"runtime receipt APK hash mismatch: {lane}")
    require(runtime.get("required_abis") == list(abis), f"runtime receipt ABIs mismatch: {lane}")
    require(runtime.get("runtime_payload_complete") is True, f"incomplete runtime: {lane}")
    require(runtime.get("claim_allowed") is False, f"invalid runtime claim: {lane}")
    require(evidence.get("record_kind") == "BUILD_OUTPUT_EVIDENCE", f"invalid build evidence kind: {lane}")
    require(evidence.get("source", {}).get("git_head") == commit, f"source commit mismatch: {lane}")
    require(evidence.get("lane", {}).get("name") == stem, f"build evidence lane mismatch: {lane}")
    require(evidence.get("lane", {}).get("abi_policy") == policy, f"ABI policy mismatch: {lane}")
    require(evidence.get("lane", {}).get("supported_abis") == list(abis),
            f"build evidence ABIs mismatch: {lane}")
    require(evidence.get("apk", {}).get("sha256") == sha, f"build evidence APK hash mismatch: {lane}")
    require(sorted(evidence.get("apk", {}).get("zip_inventory", {}).get("native_libraries", [])) == native,
            f"native inventory mismatch: {lane}")
    require(evidence.get("claims", {}).get("claim_allowed") is False,
            f"invalid build claim: {lane}")
    require(evidence.get("claims", {}).get("device_runtime_verified") is False,
            f"device runtime claim without physical receipt: {lane}")

    files = [apk, runtime_path, evidence_path]
    if lane != "arm64-v8a":
        omega = root / f"{stem}.omega-materialization.json"
        omega_receipt = json_file(omega)
        require(omega_receipt.get("result") == "PASS",
                f"Omega ARMv7 receipt not PASS: {lane}")
        require(omega_receipt.get("source", {}).get("commit") == commit,
                f"Omega ARMv7 source commit mismatch: {lane}")
        require(omega_receipt.get("source", {}).get("apk_sha256") == sha,
                f"Omega ARMv7 APK hash mismatch: {lane}")
        require(omega_receipt.get("materialization", {}).get("runtime_load_image_identity") is True,
                f"Omega ARMv7 PT_LOAD identity mismatch: {lane}")
        require(omega_receipt.get("boundary", {}).get("claim_allowed") is False,
                f"invalid Omega ARMv7 claim: {lane}")
        files.append(omega)
    # Sidecar produced by the existing build-evidence generator.
    checksum = Path(str(evidence_path) + ".sha256")
    require(checksum.is_file(), f"missing build-evidence checksum: {lane}")
    line = checksum.read_text(encoding="utf-8").strip()
    require(line == f"{digest(evidence_path)}  {evidence_path.name}",
            f"invalid build-evidence checksum: {lane}")
    files.append(checksum)
    # Common build receipts are source evidence. Keep them as files in each ZIP,
    # rather than copying unverified claims into a package manifest.
    shared = (
        "bootstrap-materialization.json",
        "bootstrap-layout-normalization.json",
        "alpine19-materialization.json",
        "qemu19-materialization.json",
        "rootfs-symlink-normalization.json",
        "REPORT.md",
        "sizes.tsv",
    )
    for name in shared:
        path = root / name
        require(path.is_file(), f"missing common build receipt/report: {name}")
        files.append(path)

    manifest = {
        "schema": "vectras.apk-wizard.abi-package.v1",
        "distribution": "internal-debug-not-release",
        "source_commit": commit,
        "lane": lane,
        "policy": policy,
        "expected_native_abis": list(abis),
        "observed_native_abis": sorted({n.split("/")[1] for n in native}),
        "embedded_runtime_abis": embedded,
        "runtime_assets_may_include_other_abis": any(
            set(present) != set(abis) for present in embedded.values()
        ),
        "apk": {"name": apk.name, "size_bytes": apk.stat().st_size, "sha256": sha},
        "static_runtime_payload_verified": True,
        "device_installation": "NOT_RUN",
        "guest_boot": "NOT_RUN",
        "claim_allowed": False,
    }
    return apk, manifest, files


def stable_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = 0o100644 << 16
    return info


def package_lane(root: Path, out: Path, spec: tuple, commit: str) -> Path:
    lane, stem, policy, abis = spec
    apk, manifest, source_files = validate_lane(root, lane, stem, policy, abis, commit)
    out.mkdir(parents=True, exist_ok=True)
    archive_path = out / f"vectras-debug-{lane}.zip"
    require(not archive_path.exists(), f"package already exists (no overwrite): {archive_path}")
    tmp_path = out / f".{archive_path.name}.tmp"
    require(not tmp_path.exists(), f"stale temporary package (review before retry): {tmp_path}")
    names = {source.name for source in source_files}
    require(len(names) == len(source_files), f"basename collision: {lane}")
    manifest_data = (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode("utf-8")
    readme = (
        f"Vectras VM Android / {lane} / internal DEBUG only\n"
        f"Source commit: {commit}\n"
        f"Native Android ABIs: {', '.join(abis)}\n"
        "Extract this ZIP; install ONLY the .apk, not the distribution ZIP.\n"
        f"Example: adb install -r {apk.name}\n"
        "Check SHA256SUMS; ABI is independently checked in MANIFEST.json.\n"
        "The APK may also carry non-selected runtime TAR assets: see manifest.\n"
        "Build/static verification does NOT prove installation, QEMU or guest boot.\n"
    ).encode("utf-8")
    checksums = []
    try:
        with zipfile.ZipFile(tmp_path, "w", allowZip64=True) as archive:
            for source in source_files:
                entry = source.name if source == apk else f"evidence/{source.name}"
                archive.write(source, arcname=entry, compress_type=zipfile.ZIP_STORED)
                checksums.append(f"{digest(source)}  {entry}")
            archive.writestr(stable_info("MANIFEST.json"), manifest_data)
            archive.writestr(stable_info("README.txt"), readme)
            checksums.extend((
                f"{hashlib.sha256(manifest_data).hexdigest()}  MANIFEST.json",
                f"{hashlib.sha256(readme).hexdigest()}  README.txt",
            ))
            archive.writestr(stable_info("SHA256SUMS"),
                             ("\n".join(checksums) + "\n").encode("utf-8"))
        with zipfile.ZipFile(tmp_path) as archive:
            require(archive.testzip() is None, f"ZIP CRC failure: {lane}")
            require(json.loads(archive.read("MANIFEST.json")) == manifest,
                    f"archive manifest mismatch: {lane}")
            for checksum_line in archive.read("SHA256SUMS").decode("utf-8").splitlines():
                expected_hash, separator, entry = checksum_line.partition("  ")
                require(bool(separator) and entry in archive.namelist(),
                        f"invalid checksum member: {lane}")
                require(member_digest(archive, entry) == expected_hash,
                        f"SHA-256 mismatch in package member {entry}: {lane}")
            require(member_digest(archive, apk.name) == manifest["apk"]["sha256"],
                    f"APK changed inside distribution ZIP: {lane}")
        tmp_path.replace(archive_path)
    except Exception:
        if tmp_path.is_file():
            tmp_path.unlink()
        raise
    print(f"ABI_PACKAGE_OK lane={lane} apk_sha256={manifest['apk']['sha256']} "
          f"zip_sha256={digest(archive_path)} zip={archive_path}")
    return archive_path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        parser.error("--source-commit must be a full 40-character Git SHA-1")
    try:
        for spec in LANES:
            package_lane(args.input_dir, args.output_dir, spec, args.source_commit)
    except (PackageError, OSError, zipfile.BadZipFile, KeyError) as exc:
        print(f"ABI_PACKAGE_BLOCKED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
