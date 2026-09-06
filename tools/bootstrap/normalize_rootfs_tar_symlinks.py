#!/usr/bin/env python3
"""Normalize rootfs TAR symlinks for APK-local extraction on Android.

Alpine rootfs archives commonly encode links such as::

    bin/sh -> /bin/busybox
    usr/bin/env -> /bin/busybox

Those links are correct *inside* a chroot/PRoot namespace, but after the TAR is
first extracted into Android app-private storage Java's ``File.isFile()`` and
``canExecute()`` follow the absolute target in the Android host namespace.  The
setup post-check therefore sees a missing shell even though ``bin/busybox`` is
present and rolls the extraction back.

This tool derives an APK-safe TAR from the pinned/materialized rootfs without
changing file contents. Absolute symlink targets are rewritten to equivalent
relative targets inside the same rootfs. Absolute member names, escaping links,
device nodes, and absolute hard-link targets fail closed. A conventional TAR
root member named ``.``/``./`` is allowed; it cannot escape the archive root.
The operation is idempotent and emits an append-friendly JSON receipt containing
before/after SHA-256 values and every rewritten link sample.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import posixpath
import tarfile
import tempfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

ALLOWED_FAMILIES = {"alpine19", "qemu19"}
ALLOWED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
FAMILY_MARKERS = {
    "alpine19": {"bin/busybox", "bin/sh", "usr/bin/env"},
    "qemu19": {
        "bin/busybox",
        "bin/sh",
        "usr/bin/env",
        "usr/bin/qemu-system-x86_64",
        "usr/bin/qemu-system-i386",
        "usr/bin/qemu-system-arm",
        "usr/bin/qemu-system-aarch64",
        "usr/bin/qemu-system-ppc",
        "usr/bin/qemu-img",
    },
}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_member_name(name: str) -> str:
    normalized = posixpath.normpath(name.replace("\\", "/"))
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def assert_member_path_safe(name: str) -> str:
    raw = name.replace("\\", "/")
    pure = PurePosixPath(raw)
    normalized = normalize_member_name(raw)
    if pure.is_absolute() or normalized in {"", ".."} or normalized.startswith("../"):
        raise ValueError(f"unsafe TAR member path: {name}")
    # GNU/BusyBox tar commonly emits a root directory member as '.' or './'.
    # It is a location marker, not a path escape, and is safe to preserve.
    if normalized == ".":
        return "."
    return normalized


def resolve_relative_link(member_name: str, link: str, *, hardlink: bool) -> str:
    base = "" if hardlink else posixpath.dirname(member_name)
    resolved = posixpath.normpath(posixpath.join(base, link))
    if resolved == ".." or resolved.startswith("../") or resolved.startswith("/"):
        kind = "hardlink" if hardlink else "symlink"
        raise ValueError(f"escaping {kind} target rejected: {member_name} -> {link}")
    return resolved


def relative_root_target(member_name: str, absolute_target: str) -> str:
    if not absolute_target.startswith("/"):
        raise ValueError("absolute target required")
    target = posixpath.normpath(absolute_target.lstrip("/"))
    if target in {"", ".", ".."} or target.startswith("../"):
        raise ValueError(f"unsafe absolute symlink target: {member_name} -> {absolute_target}")
    base = posixpath.dirname(member_name) or "."
    relative = posixpath.relpath(target, start=base)
    resolved = resolve_relative_link(member_name, relative, hardlink=False)
    if resolved != target:
        raise ValueError(
            f"symlink rewrite changed rootfs semantics: {member_name} -> {absolute_target} => {relative} ({resolved} != {target})"
        )
    return relative


def inspect_tar(path: Path, family: str) -> dict:
    names: set[str] = set()
    absolute_symlinks: list[dict[str, str]] = []
    try:
        with tarfile.open(path, "r:*") as archive:
            for member in archive.getmembers():
                normalized = assert_member_path_safe(member.name)
                if member.isdev():
                    raise ValueError(f"device node rejected: {member.name}")
                if member.issym():
                    target = member.linkname.replace("\\", "/")
                    if PurePosixPath(target).is_absolute():
                        relative_root_target(normalized, target)
                        absolute_symlinks.append({"name": normalized, "target": target})
                    else:
                        resolve_relative_link(normalized, target, hardlink=False)
                elif member.islnk():
                    target = member.linkname.replace("\\", "/")
                    if PurePosixPath(target).is_absolute():
                        raise ValueError(f"absolute hardlink target rejected: {member.name} -> {member.linkname}")
                    resolve_relative_link(normalized, target, hardlink=True)
                names.add(normalized)
    except (tarfile.TarError, OSError) as exc:
        raise ValueError(f"unreadable TAR {path}: {exc}") from exc

    missing = sorted(FAMILY_MARKERS[family] - names)
    if missing:
        raise ValueError(f"family={family} missing runtime marker(s): {missing}")
    return {
        "members": len(names),
        "absolute_symlink_targets": len(absolute_symlinks),
        "absolute_symlink_samples": absolute_symlinks[:24],
        "markers_verified": sorted(FAMILY_MARKERS[family]),
    }


def rewrite_tar(path: Path, family: str) -> dict:
    before_sha = sha256_file(path)
    before_size = path.stat().st_size
    before = inspect_tar(path, family)

    if before["absolute_symlink_targets"] == 0:
        return {
            "changed": False,
            "before_sha256": before_sha,
            "after_sha256": before_sha,
            "before_size_bytes": before_size,
            "after_size_bytes": before_size,
            "rewritten_symlinks": 0,
            "rewrites": [],
            "members": before["members"],
            "markers_verified": before["markers_verified"],
        }

    rewrites: list[dict[str, str]] = []
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".normalized.tmp", dir=path.parent)
    os.close(fd)
    tmp_path = Path(tmp_name)
    try:
        with tarfile.open(path, "r:*") as src, tarfile.open(tmp_path, "w", format=tarfile.PAX_FORMAT) as dst:
            for member in src.getmembers():
                normalized = assert_member_path_safe(member.name)
                if member.isdev():
                    raise ValueError(f"device node rejected: {member.name}")

                if member.issym():
                    original = member.linkname.replace("\\", "/")
                    if PurePosixPath(original).is_absolute():
                        rewritten = relative_root_target(normalized, original)
                        member.linkname = rewritten
                        rewrites.append({
                            "name": normalized,
                            "before": original,
                            "after": rewritten,
                        })
                    else:
                        resolve_relative_link(normalized, original, hardlink=False)
                elif member.islnk():
                    target = member.linkname.replace("\\", "/")
                    if PurePosixPath(target).is_absolute():
                        raise ValueError(f"absolute hardlink target rejected: {member.name} -> {member.linkname}")
                    resolve_relative_link(normalized, target, hardlink=True)

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

        os.chmod(tmp_path, 0o644)
        after = inspect_tar(tmp_path, family)
        if after["absolute_symlink_targets"] != 0:
            raise ValueError(
                f"normalization incomplete for {path}: remaining={after['absolute_symlink_targets']}"
            )
        os.replace(tmp_path, path)
    finally:
        tmp_path.unlink(missing_ok=True)

    return {
        "changed": True,
        "before_sha256": before_sha,
        "after_sha256": sha256_file(path),
        "before_size_bytes": before_size,
        "after_size_bytes": path.stat().st_size,
        "rewritten_symlinks": len(rewrites),
        "rewrites": rewrites[:64],
        "members": after["members"],
        "markers_verified": after["markers_verified"],
    }


def parse_csv(raw: str, allowed: set[str], label: str) -> list[str]:
    values = [item.strip() for item in raw.split(",") if item.strip()]
    if not values:
        raise ValueError(f"at least one {label} is required")
    unknown = sorted(set(values) - allowed)
    if unknown:
        raise ValueError(f"unsupported {label}(s): {unknown}")
    return values


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True, help="generated asset root containing alpine19/ and qemu19/")
    parser.add_argument("--families", default="alpine19,qemu19")
    parser.add_argument("--abis", default="arm64-v8a,armeabi-v7a")
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()

    families = parse_csv(args.families, ALLOWED_FAMILIES, "family")
    abis = parse_csv(args.abis, ALLOWED_ABIS, "ABI")
    root = args.root.resolve()

    assets = []
    for family in families:
        for abi in abis:
            path = root / family / f"{abi}.tar"
            if not path.is_file():
                raise FileNotFoundError(f"required runtime TAR missing: {path}")
            result = rewrite_tar(path, family)
            row = {
                "family": family,
                "abi": abi,
                "path": str(path.relative_to(root)),
                **result,
            }
            assets.append(row)
            print(
                "ROOTFS_SYMLINK_NORMALIZED "
                f"family={family} abi={abi} changed={result['changed']} "
                f"rewritten={result['rewritten_symlinks']} sha256={result['after_sha256']}"
            )

    receipt = {
        "schema_version": "vectras.rootfs-symlink-normalization.v1",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "status": "NORMALIZED_VERIFIED_NOT_DEVICE_EXECUTED",
        "root": str(root),
        "families": families,
        "abis": abis,
        "assets": assets,
        "invariants": [
            "PINNED_SOURCE_BYTES_ARE_INPUT_PROVENANCE",
            "ABSOLUTE_ROOTFS_SYMLINKS_BECOME_EQUIVALENT_RELATIVE_SYMLINKS",
            "REGULAR_FILE_BYTES_UNCHANGED",
            "TAR_ROOT_MEMBER_DOT_ALLOWED",
            "NO_ABSOLUTE_MEMBER_PATHS",
            "NO_DOTDOT_ESCAPE",
            "NO_DEVICE_NODES",
            "NO_ABSOLUTE_HARDLINKS",
            "RUNTIME_MARKERS_PRESENT_AFTER_REWRITE",
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
