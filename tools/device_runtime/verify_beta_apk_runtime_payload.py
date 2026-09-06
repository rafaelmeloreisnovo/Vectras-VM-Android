#!/usr/bin/env python3
import argparse
import hashlib
import io
import json
import posixpath
import sys
import tarfile
import zipfile
from pathlib import Path, PurePosixPath

SUPPORTED_RUNTIME_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
DEFAULT_REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a")
BOOTSTRAP_RUNTIME_MARKERS = (
    "usr/bin/proot",
    "usr/tmp",
)
ROOTFS_RUNTIME_MARKERS = (
    "bin/busybox",
    "bin/sh",
    "usr/bin/env",
)
QEMU_RUNTIME_MARKERS = (
    "usr/bin/qemu-system-x86_64",
    "usr/bin/qemu-system-i386",
    "usr/bin/qemu-system-arm",
    "usr/bin/qemu-system-aarch64",
    "usr/bin/qemu-system-ppc",
    "usr/bin/qemu-img",
    *ROOTFS_RUNTIME_MARKERS,
)

REQUIRED_NATIVE_BY_ABI = {
    abi: [
        f"lib/{abi}/libtermux-bootstrap.so",
        f"lib/{abi}/libtermux_terminal_jni.so",
    ]
    for abi in SUPPORTED_RUNTIME_ABIS
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def required_runtime_tars(abis: tuple[str, ...]) -> dict[str, dict[str, str]]:
    return {
        abi: {
            "bootstrap": f"assets/bootstrap/{abi}.tar",
            "rootfs": f"assets/alpine19/{abi}.tar",
            "qemu": f"assets/qemu19/{abi}.tar",
        }
        for abi in abis
    }


def normalize_tar_name(name: str) -> str:
    raw = name.replace("\\", "/")
    normalized = posixpath.normpath(raw)
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def safe_tar_member(name: str) -> tuple[bool, str]:
    raw = name.replace("\\", "/")
    pure = PurePosixPath(raw)
    normalized = normalize_tar_name(raw)
    if pure.is_absolute() or normalized in {"", ".."} or normalized.startswith("../"):
        return False, normalized
    # '.' / './' is the conventional TAR root member and is not a traversal.
    if normalized == ".":
        return True, "."
    return True, normalized.rstrip("/")


def inspect_runtime_tar(
    zf: zipfile.ZipFile,
    entry: str,
    required_markers: tuple[str, ...],
    *,
    require_tmp_directory: bool = False,
) -> dict:
    result = {
        "entry": entry,
        "size_bytes": 0,
        "sha256": "TOKEN_VAZIO",
        "required_markers": list(required_markers),
        "missing_markers": [],
        "unsafe_member_paths": [],
        "absolute_symlink_targets": [],
        "absolute_hardlink_targets": [],
        "usr_tmp_is_directory": None,
        "readable": False,
        "ok": False,
    }
    try:
        payload = zf.read(entry)
        result["size_bytes"] = len(payload)
        result["sha256"] = hashlib.sha256(payload).hexdigest()
        names: set[str] = set()
        member_types: dict[str, str] = {}
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:*") as tf:
            for member in tf.getmembers():
                safe, normalized = safe_tar_member(member.name)
                if not safe:
                    result["unsafe_member_paths"].append(member.name)
                    continue
                names.add(normalized)
                if member.isdir():
                    member_types[normalized] = "dir"
                elif member.isfile():
                    member_types[normalized] = "file"
                elif member.issym():
                    member_types[normalized] = "symlink"
                    target = member.linkname.replace("\\", "/")
                    if PurePosixPath(target).is_absolute():
                        result["absolute_symlink_targets"].append(
                            {"name": normalized, "target": target}
                        )
                elif member.islnk():
                    member_types[normalized] = "hardlink"
                    target = member.linkname.replace("\\", "/")
                    if PurePosixPath(target).is_absolute():
                        result["absolute_hardlink_targets"].append(
                            {"name": normalized, "target": target}
                        )
                else:
                    member_types[normalized] = "other"

        result["missing_markers"] = [marker for marker in required_markers if marker not in names]
        if require_tmp_directory:
            result["usr_tmp_is_directory"] = member_types.get("usr/tmp") == "dir"
        result["readable"] = True
        result["ok"] = (
            not result["missing_markers"]
            and not result["unsafe_member_paths"]
            and not result["absolute_symlink_targets"]
            and not result["absolute_hardlink_targets"]
            and (not require_tmp_directory or result["usr_tmp_is_directory"] is True)
        )
        return result
    except (KeyError, tarfile.TarError, OSError) as exc:
        result["error"] = f"unreadable:{type(exc).__name__}:{exc}"
        result["missing_markers"] = list(required_markers)
        return result


def inspect(apk: Path, abis: tuple[str, ...]) -> dict:
    with zipfile.ZipFile(apk) as zf:
        names = set(zf.namelist())
        runtime_tars = required_runtime_tars(abis)

        missing_bootstrap = {
            abi: paths["bootstrap"]
            for abi, paths in runtime_tars.items()
            if paths["bootstrap"] not in names
        }
        missing_rootfs = {
            abi: paths["rootfs"]
            for abi, paths in runtime_tars.items()
            if paths["rootfs"] not in names
        }
        missing_qemu = {
            abi: paths["qemu"]
            for abi, paths in runtime_tars.items()
            if paths["qemu"] not in names
        }
        missing_runtime_tars = {
            abi: {
                family: path
                for family, path in paths.items()
                if path not in names
            }
            for abi, paths in runtime_tars.items()
        }
        missing_runtime_tars = {
            abi: paths for abi, paths in missing_runtime_tars.items() if paths
        }

        bootstrap_tar_checks = {}
        rootfs_tar_checks = {}
        qemu_tar_checks = {}
        bootstrap_contract_failures = {}
        rootfs_contract_failures = {}
        qemu_contract_failures = {}

        for abi, paths in runtime_tars.items():
            bootstrap_entry = paths["bootstrap"]
            if bootstrap_entry in names:
                check = inspect_runtime_tar(
                    zf,
                    bootstrap_entry,
                    BOOTSTRAP_RUNTIME_MARKERS,
                    require_tmp_directory=True,
                )
                bootstrap_tar_checks[abi] = check
                if not check["ok"]:
                    bootstrap_contract_failures[abi] = check

            rootfs_entry = paths["rootfs"]
            if rootfs_entry in names:
                check = inspect_runtime_tar(zf, rootfs_entry, ROOTFS_RUNTIME_MARKERS)
                rootfs_tar_checks[abi] = check
                if not check["ok"]:
                    rootfs_contract_failures[abi] = check

            qemu_entry = paths["qemu"]
            if qemu_entry in names:
                check = inspect_runtime_tar(zf, qemu_entry, QEMU_RUNTIME_MARKERS)
                qemu_tar_checks[abi] = check
                if not check["ok"]:
                    qemu_contract_failures[abi] = check

        missing_native = {
            abi: [p for p in REQUIRED_NATIVE_BY_ABI[abi] if p not in names]
            for abi in abis
        }
        missing_native = {abi: items for abi, items in missing_native.items() if items}

        has_loader = "assets/bootstrap/loader.apk" in names

    bootstrap_runtime_verified = not missing_bootstrap and not bootstrap_contract_failures
    rootfs_runtime_verified = not missing_rootfs and not rootfs_contract_failures
    qemu_runtime_verified = not missing_qemu and not qemu_contract_failures
    shell_only_risk = has_loader and bool(missing_runtime_tars)
    runtime_payload_complete = (
        has_loader
        and not missing_runtime_tars
        and not missing_native
        and bootstrap_runtime_verified
        and rootfs_runtime_verified
        and qemu_runtime_verified
    )

    return {
        "schema": "vectras.device_runtime.apk_payload.v4",
        "apk": str(apk),
        "sha256": sha256(apk),
        "size_bytes": apk.stat().st_size,
        "required_abis": list(abis),
        "loader_apk_present": has_loader,
        "required_runtime_tars": runtime_tars,
        "missing_bootstrap_tars": missing_bootstrap,
        "missing_rootfs_tars": missing_rootfs,
        "missing_qemu_tars": missing_qemu,
        "missing_runtime_tars": missing_runtime_tars,
        "missing_native_runtime": missing_native,
        "bootstrap_tar_checks": bootstrap_tar_checks,
        "rootfs_tar_checks": rootfs_tar_checks,
        "qemu_tar_checks": qemu_tar_checks,
        "bootstrap_contract_failures": bootstrap_contract_failures,
        "rootfs_contract_failures": rootfs_contract_failures,
        "qemu_contract_failures": qemu_contract_failures,
        "shell_only_risk": shell_only_risk,
        "runtime_payload_complete": runtime_payload_complete,
        "runtime_contract": "bootstrap(proot+usr/tmp) + alpine19(busybox+sh+env) + embedded-qemu19 + JNI surface",
        "bootstrap_runtime_verified": bootstrap_runtime_verified,
        "rootfs_runtime_verified": rootfs_runtime_verified,
        "qemu_runtime_verified": qemu_runtime_verified,
        "claim_allowed": False,
        "device_runtime_verified": False,
    }


def parse_abis(raw: str) -> tuple[str, ...]:
    values = tuple(dict.fromkeys(v.strip() for v in raw.split(",") if v.strip()))
    if not values:
        raise ValueError("at least one ABI is required")
    unsupported = [abi for abi in values if abi not in SUPPORTED_RUNTIME_ABIS]
    if unsupported:
        raise ValueError(f"unsupported ABI(s): {', '.join(unsupported)}")
    return values


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument(
        "--abis",
        default=",".join(DEFAULT_REQUIRED_ABIS),
        help="comma-separated runtime ABIs that must be complete",
    )
    ap.add_argument("--json-out", type=Path)
    ap.add_argument("--allow-incomplete", action="store_true")
    args = ap.parse_args()

    if not args.apk.is_file():
        print(f"APK not found: {args.apk}", file=sys.stderr)
        return 2
    try:
        abis = parse_abis(args.abis)
        result = inspect(args.apk, abis)
    except ValueError as exc:
        print(f"Invalid ABI contract: {exc}", file=sys.stderr)
        return 2
    except zipfile.BadZipFile:
        print(f"Not a valid APK/ZIP: {args.apk}", file=sys.stderr)
        return 2

    encoded = json.dumps(result, indent=2, sort_keys=True)
    print(encoded)
    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(encoded + "\n", encoding="utf-8")

    if result["runtime_payload_complete"] or args.allow_incomplete:
        return 0
    print(
        "FAIL: APK lacks the complete standalone runtime contract for the requested ABI(s): "
        "bootstrap/<abi>.tar must contain usr/bin/proot + usr/tmp; alpine19/<abi>.tar must contain "
        "busybox + sh + env with APK-safe link semantics; qemu19/<abi>.tar must contain the QEMU "
        "system binaries plus the same rootfs contract; JNI bootstrap surfaces must be present.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
