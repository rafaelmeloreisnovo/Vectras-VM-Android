#!/usr/bin/env python3
import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

REQUIRED_BOOTSTRAP_TARS = {
    "arm64-v8a": "assets/bootstrap/arm64-v8a.tar",
    "armeabi-v7a": "assets/bootstrap/armeabi-v7a.tar",
}
REQUIRED_NATIVE = {
    "arm64-v8a": [
        "lib/arm64-v8a/libtermux-bootstrap.so",
        "lib/arm64-v8a/libtermux_terminal_jni.so",
    ],
    "armeabi-v7a": [
        "lib/armeabi-v7a/libtermux-bootstrap.so",
        "lib/armeabi-v7a/libtermux_terminal_jni.so",
    ],
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def inspect(apk: Path) -> dict:
    with zipfile.ZipFile(apk) as zf:
        names = set(zf.namelist())
    missing_tars = {
        abi: path for abi, path in REQUIRED_BOOTSTRAP_TARS.items() if path not in names
    }
    missing_native = {
        abi: [p for p in paths if p not in names]
        for abi, paths in REQUIRED_NATIVE.items()
    }
    missing_native = {abi: items for abi, items in missing_native.items() if items}
    has_loader = "assets/bootstrap/loader.apk" in names
    suspicious_shell_only = has_loader and bool(missing_tars)
    return {
        "schema": "vectras.device_runtime.apk_payload.v1",
        "apk": str(apk),
        "sha256": sha256(apk),
        "size_bytes": apk.stat().st_size,
        "loader_apk_present": has_loader,
        "required_bootstrap_tars": REQUIRED_BOOTSTRAP_TARS,
        "missing_bootstrap_tars": missing_tars,
        "missing_native_runtime": missing_native,
        "shell_only_risk": suspicious_shell_only,
        "runtime_payload_complete": has_loader and not missing_tars and not missing_native,
        "claim_allowed": False,
        "device_runtime_verified": False,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument("--json-out", type=Path)
    ap.add_argument("--allow-incomplete", action="store_true")
    args = ap.parse_args()

    if not args.apk.is_file():
        print(f"APK not found: {args.apk}", file=sys.stderr)
        return 2
    try:
        result = inspect(args.apk)
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
        "FAIL: APK has terminal/bootstrap JNI surface but lacks required ABI rootfs payloads; "
        "do not promote this artifact as a functional PRoot/userland runtime.",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
