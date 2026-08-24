#!/usr/bin/env python3
import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

SUPPORTED_RUNTIME_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
DEFAULT_REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a")

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
        }
        for abi in abis
    }


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

    missing_native = {
        abi: [p for p in REQUIRED_NATIVE_BY_ABI[abi] if p not in names]
        for abi in abis
    }
    missing_native = {abi: items for abi, items in missing_native.items() if items}

    has_loader = "assets/bootstrap/loader.apk" in names
    shell_only_risk = has_loader and bool(missing_runtime_tars)
    runtime_payload_complete = (
        has_loader and not missing_runtime_tars and not missing_native
    )

    return {
        "schema": "vectras.device_runtime.apk_payload.v2",
        "apk": str(apk),
        "sha256": sha256(apk),
        "size_bytes": apk.stat().st_size,
        "required_abis": list(abis),
        "loader_apk_present": has_loader,
        "required_runtime_tars": runtime_tars,
        # Compatibility field retained for v1 readers.
        "missing_bootstrap_tars": missing_bootstrap,
        "missing_rootfs_tars": missing_rootfs,
        "missing_runtime_tars": missing_runtime_tars,
        "missing_native_runtime": missing_native,
        "shell_only_risk": shell_only_risk,
        "runtime_payload_complete": runtime_payload_complete,
        "runtime_contract": "bootstrap-seed + alpine19-rootfs + JNI surface",
        "qemu_runtime_verified": False,
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
        "FAIL: APK lacks the complete runtime seed contract for the requested ABI(s): "
        "bootstrap/<abi>.tar (PRoot seed), alpine19/<abi>.tar (rootfs/userland), "
        "and the JNI bootstrap surface must all be present. Do not promote this APK "
        "as a functional PRoot/rootfs runtime.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
