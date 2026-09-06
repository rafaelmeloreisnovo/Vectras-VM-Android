#!/usr/bin/env python3
"""Derive APK-safe PRoot bootstrap TARs from already verified pinned inputs.

SetupFeatureCore validates ``<filesDir>/usr/tmp`` immediately after extracting
``bootstrap/<abi>.tar``.  The historical setup flow only creates that directory
later, before Alpine extraction.  Therefore a bootstrap carrying a valid
``usr/bin/proot`` but no ``usr/tmp`` can be extracted and then rolled back by its
own post-check.

This normalizer closes that ordering gap at packaging time.  It preserves every
existing member and regular-file byte, and injects only ``usr/tmp/`` when absent.
The injected directory is mode 0771, uid/gid 0, mtime 0.  The pinned source TAR
hash remains provenance; the receipt records the derived TAR hash actually
embedded in the APK.
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
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

ALLOWED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
PROOT_MARKER = "usr/bin/proot"
TMP_MEMBER = "usr/tmp"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_name(name: str) -> str:
    raw = name.replace("\\", "/")
    pure = PurePosixPath(raw)
    normalized = posixpath.normpath(raw)
    while normalized.startswith("./"):
        normalized = normalized[2:]
    if pure.is_absolute() or normalized in {"", ".", ".."} or normalized.startswith("../"):
        raise ValueError(f"unsafe TAR member path: {name}")
    return normalized.rstrip("/")


def validate_link(member_name: str, member: tarfile.TarInfo) -> None:
    if not (member.issym() or member.islnk()):
        return
    target = member.linkname.replace("\\", "/")
    if PurePosixPath(target).is_absolute():
        raise ValueError(f"absolute link target rejected in bootstrap: {member_name} -> {target}")
    base = "" if member.islnk() else posixpath.dirname(member_name)
    resolved = posixpath.normpath(posixpath.join(base, target))
    if resolved == ".." or resolved.startswith("../") or resolved.startswith("/"):
        raise ValueError(f"escaping link target rejected in bootstrap: {member_name} -> {target}")


def inspect(path: Path) -> dict:
    names: set[str] = set()
    proot_mode = None
    try:
        with tarfile.open(path, "r:*") as archive:
            members = archive.getmembers()
            if not members:
                raise ValueError(f"empty TAR: {path}")
            for member in members:
                name = normalize_name(member.name)
                if member.isdev():
                    raise ValueError(f"device node rejected in bootstrap: {member.name}")
                validate_link(name, member)
                names.add(name)
                if name == PROOT_MARKER:
                    if not member.isfile():
                        raise ValueError(f"{PROOT_MARKER} is not a regular file")
                    proot_mode = member.mode
    except (tarfile.TarError, OSError) as exc:
        raise ValueError(f"unreadable TAR {path}: {exc}") from exc

    if PROOT_MARKER not in names:
        raise ValueError(f"bootstrap missing required marker: {PROOT_MARKER}")
    return {
        "members": len(names),
        "has_usr_tmp": TMP_MEMBER in names,
        "proot_mode": proot_mode,
    }


def derive(path: Path) -> dict:
    before_sha = sha256_file(path)
    before_size = path.stat().st_size
    before = inspect(path)
    if before["has_usr_tmp"]:
        return {
            "changed": False,
            "injected_usr_tmp": False,
            "before_sha256": before_sha,
            "after_sha256": before_sha,
            "before_size_bytes": before_size,
            "after_size_bytes": before_size,
            "members_before": before["members"],
            "members_after": before["members"],
            "proot_mode": before["proot_mode"],
        }

    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".layout.tmp", dir=path.parent)
    os.close(fd)
    tmp = Path(tmp_name)
    try:
        with tarfile.open(path, "r:*") as src, tarfile.open(tmp, "w", format=tarfile.PAX_FORMAT) as dst:
            for member in src.getmembers():
                name = normalize_name(member.name)
                if member.isdev():
                    raise ValueError(f"device node rejected in bootstrap: {member.name}")
                validate_link(name, member)
                fileobj = None
                if member.isfile():
                    fileobj = src.extractfile(member)
                    if fileobj is None:
                        raise ValueError(f"unable to read regular TAR member: {member.name}")
                try:
                    dst.addfile(member, fileobj)
                finally:
                    if fileobj is not None:
                        fileobj.close()

            directory = tarfile.TarInfo(TMP_MEMBER + "/")
            directory.type = tarfile.DIRTYPE
            directory.mode = 0o771
            directory.uid = 0
            directory.gid = 0
            directory.uname = "root"
            directory.gname = "root"
            directory.mtime = 0
            directory.size = 0
            dst.addfile(directory)

        os.chmod(tmp, 0o644)
        after = inspect(tmp)
        if not after["has_usr_tmp"]:
            raise ValueError("derived bootstrap still lacks usr/tmp")
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)

    return {
        "changed": True,
        "injected_usr_tmp": True,
        "before_sha256": before_sha,
        "after_sha256": sha256_file(path),
        "before_size_bytes": before_size,
        "after_size_bytes": path.stat().st_size,
        "members_before": before["members"],
        "members_after": after["members"],
        "proot_mode": after["proot_mode"],
    }


def parse_abis(raw: str) -> list[str]:
    values = [item.strip() for item in raw.split(",") if item.strip()]
    if not values:
        raise ValueError("at least one ABI is required")
    unknown = sorted(set(values) - ALLOWED_ABIS)
    if unknown:
        raise ValueError(f"unsupported ABI(s): {unknown}")
    return values


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True, help="generated bootstrap directory containing <abi>.tar")
    parser.add_argument("--abis", default="arm64-v8a,armeabi-v7a")
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()

    root = args.root.resolve()
    abis = parse_abis(args.abis)
    assets = []
    for abi in abis:
        path = root / f"{abi}.tar"
        if not path.is_file():
            raise FileNotFoundError(f"required bootstrap TAR missing: {path}")
        result = derive(path)
        assets.append({"abi": abi, "path": path.name, **result})
        print(
            "BOOTSTRAP_LAYOUT_NORMALIZED "
            f"abi={abi} changed={result['changed']} usr_tmp={result['injected_usr_tmp']} "
            f"sha256={result['after_sha256']}"
        )

    receipt = {
        "schema_version": "vectras.bootstrap-layout-normalization.v1",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "status": "DERIVED_LAYOUT_VERIFIED_NOT_DEVICE_EXECUTED",
        "root": str(root),
        "abis": abis,
        "assets": assets,
        "invariants": [
            "PINNED_BOOTSTRAP_IS_INPUT_PROVENANCE",
            "USR_BIN_PROOT_PRESENT",
            "USR_TMP_PRESENT_BEFORE_DEVICE_EXTRACTION",
            "USR_TMP_MODE_0771_WHEN_INJECTED",
            "REGULAR_FILE_BYTES_PRESERVED",
            "DERIVED_TAR_SHA256_RECORDED",
            "DEVICE_EXECUTION_NOT_CLAIMED",
        ],
        "device_runtime_verified": False,
        "claim_allowed": False,
    }
    rendered = json.dumps(receipt, indent=2, sort_keys=True) + "\n"
    if args.receipt:
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
