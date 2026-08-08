#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import shutil
import tempfile
from pathlib import Path

from tools.bootstrap.validate_bootstrap_assets_manifest import validate


def copy_atomic(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(
        prefix=f".{destination.name}.", suffix=".tmp", dir=destination.parent
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(fd, "wb") as out, source.open("rb") as inp:
            shutil.copyfileobj(inp, out, length=1024 * 1024)
            out.flush()
            os.fsync(out.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Materialize only locally supplied bootstrap TARs that match the immutable manifest."
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--staging-dir", type=Path, required=True)
    parser.add_argument(
        "--target-dir",
        type=Path,
        default=Path("app/src/main/assets/bootstrap"),
    )
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--write-receipt", type=Path)
    args = parser.parse_args()

    data = json.loads(args.manifest.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("manifest root must be an object")
    report = validate(data, "verified", args.staging_dir)

    planned = []
    for asset in report["verified"]:
        source = args.staging_dir / asset["filename"]
        destination = args.target_dir / asset["filename"]
        planned.append({
            "abi": asset["abi"],
            "source": str(source),
            "destination": str(destination),
            "sha256": asset["sha256"],
            "size_bytes": asset["size_bytes"],
        })
        if args.apply:
            copy_atomic(source, destination)

    receipt = {
        "schema_version": "bootstrap-assets-materialization.v1",
        "status": "APPLIED" if args.apply else "DRY_RUN_VERIFIED",
        "network_access": False,
        "manifest": str(args.manifest),
        "staging_dir": str(args.staging_dir),
        "target_dir": str(args.target_dir),
        "assets": planned,
        "claim_allowed": False,
        "release_allowed": False,
        "device_runtime_verified": False,
    }
    rendered = json.dumps(receipt, indent=2, sort_keys=True) + "\n"
    if args.write_receipt:
        args.write_receipt.parent.mkdir(parents=True, exist_ok=True)
        args.write_receipt.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
