#!/usr/bin/env python3
import argparse
import hashlib
import io
import json
import sys
import tarfile
import zipfile
from pathlib import Path

SUPPORTED_RUNTIME_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
DEFAULT_REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a")
QEMU_RUNTIME_MARKERS = (
    "usr/bin/qemu-system-x86_64",
    "usr/bin/qemu-system-i386",
    "usr/bin/qemu-system-arm",
    "usr/bin/qemu-system-aarch64",
    "usr/bin/qemu-system-ppc",
    "usr/bin/qemu-img",
    "bin/busybox",
    "bin/sh",
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
    normalized = name.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized.lstrip("/")


def inspect_qemu_tar(zf: zipfile.ZipFile, entry: str) -> tuple[list[str], int, str]:
    try:
        payload = zf.read(entry)
        digest = hashlib.sha256(payload).hexdigest()
        with tarfile.open(fileobj=io.BytesIO(payload), mode="r:*") as tf:
            names = {normalize_tar_name(member.name) for member in tf.getmembers()}
    except (KeyError, tarfile.TarError, OSError) as exc:
        return [f"unreadable:{type(exc).__name__}:{exc}"], 0, "TOKEN_VAZIO"
    missing = [marker for marker in QEMU_RUNTIME_MARKERS if marker not in names]
    return missing, len(payload), digest


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

        qemu_tar_checks = {}
        qemu_marker_failures = {}
        for abi, paths in runtime_tars.items():
            qemu_entry = paths["qemu"]
            if qemu_entry not in names:
                continue
            missing_markers, size_bytes, digest = inspect_qemu_tar(zf, qemu_entry)
            qemu_tar_checks[abi] = {
                "entry": qemu_entry,
                "size_bytes": size_bytes,
                "sha256": digest,
                "required_markers": list(QEMU_RUNTIME_MARKERS),
                "missing_markers": missing_markers,
            }
            if missing_markers:
                qemu_marker_failures[abi] = missing_markers

        missing_native = {
            abi: [p for p in REQUIRED_NATIVE_BY_ABI[abi] if p not in names]
            for abi in abis
        }
        missing_native = {abi: items for abi, items in missing_native.items() if items}

        has_loader = "assets/bootstrap/loader.apk" in names

    qemu_runtime_verified = not missing_qemu and not qemu_marker_failures
    shell_only_risk = has_loader and bool(missing_runtime_tars)
    runtime_payload_complete = (
        has_loader
        and not missing_runtime_tars
        and not missing_native
        and qemu_runtime_verified
    )

    return {
        "schema": "vectras.device_runtime.apk_payload.v3",
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
        "qemu_tar_checks": qemu_tar_checks,
        "qemu_marker_failures": qemu_marker_failures,
        "shell_only_risk": shell_only_risk,
        "runtime_payload_complete": runtime_payload_complete,
        "runtime_contract": "bootstrap-seed + alpine19-rootfs + embedded-qemu19 + JNI surface",
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
        "bootstrap/<abi>.tar, alpine19/<abi>.tar, qemu19/<abi>.tar with verified "
        "qemu-system-* markers, and the JNI bootstrap surface must all be present.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
