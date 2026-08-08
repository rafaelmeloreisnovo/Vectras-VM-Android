#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

BLAKE3_COMMIT = "ff6991d8b13f5b4b16dc311b5acc9c63ae835152"


def fail(message: str) -> None:
    raise ValueError(message)


def require_digest(value: Any, label: str) -> None:
    if not isinstance(value, str) or len(value) != 64:
        fail(f"{label}: expected 64 hexadecimal characters")
    try:
        int(value, 16)
    except ValueError as exc:
        raise ValueError(f"{label}: invalid hexadecimal digest") from exc


def validate(manifest: dict[str, Any]) -> None:
    if manifest.get("schema_version") != "zipraf-corpus-manifest.u2.v1":
        fail("schema_version")
    if manifest.get("claim_allowed") is not False:
        fail("claim_allowed")
    if manifest.get("extraction_performed") is not False:
        fail("extraction boundary")
    if manifest.get("execution_authorized") is not False:
        fail("execution boundary")
    if manifest.get("blake3_provider_commit") != BLAKE3_COMMIT:
        fail("BLAKE3 provider pin")
    if manifest.get("u2_real_external_corpus") != "TOKEN_VAZIO":
        fail("external corpus overclaim")

    archives = manifest.get("archives")
    if not isinstance(archives, list) or len(archives) != 3:
        fail("fixture archive count")
    by_name = {item.get("input_name"): item for item in archives if isinstance(item, dict)}
    if set(by_name) != {"sample.zip", "sample.apk", "malformed.zip"}:
        fail("fixture archive identities")

    summary = manifest.get("summary", {})
    expected_summary = {
        "archive_total": 3,
        "parsed_archives": 2,
        "parse_failures": 1,
        "entry_total": 6,
        "entry_rejected": 0,
    }
    for key, value in expected_summary.items():
        if summary.get(key) != value:
            fail(f"summary {key}: expected {value}, got {summary.get(key)!r}")
    if not isinstance(summary.get("stored_bytes"), int) or summary["stored_bytes"] <= 0:
        fail("summary stored_bytes")
    if not isinstance(summary.get("logical_bytes"), int) or summary["logical_bytes"] <= 0:
        fail("summary logical_bytes")

    malformed = by_name["malformed.zip"]
    if malformed.get("state") != "PARSE_REJECTED":
        fail("malformed archive must be rejected")
    if malformed.get("entries") != []:
        fail("malformed archive entries")

    sample_zip = by_name["sample.zip"]
    sample_apk = by_name["sample.apk"]
    for archive in (sample_zip, sample_apk):
        if archive.get("state") != "PARSED":
            fail(f"{archive.get('input_name')}: expected PARSED")
        require_digest(archive.get("archive_sha256"), "archive_sha256")
        require_digest(archive.get("archive_blake3"), "archive_blake3")
        fingerprint = archive.get("layout_fingerprint64")
        if not isinstance(fingerprint, str) or len(fingerprint) != 16:
            fail("layout fingerprint")

    markers = sample_apk.get("apk_markers", {})
    if markers.get("android_manifest") is not True:
        fail("APK AndroidManifest marker")
    if markers.get("classes_dex") is not True:
        fail("APK classes.dex marker")
    if markers.get("apk_structure_candidate") is not True:
        fail("APK candidate marker")

    entries: list[dict[str, Any]] = []
    for archive in (sample_zip, sample_apk):
        value = archive.get("entries")
        if not isinstance(value, list):
            fail("entries list")
        entries.extend(value)

    names = {entry.get("name") for entry in entries}
    required_names = {
        "readme.txt",
        "packed.txt",
        "AndroidManifest.xml",
        "classes.dex",
        "resources.arsc",
        "assets/data.bin",
    }
    if names != required_names:
        fail("entry names")

    for entry in entries:
        require_digest(entry.get("stored_sha256"), f"{entry.get('name')} sha256")
        require_digest(entry.get("stored_blake3"), f"{entry.get('name')} blake3")
        if entry.get("execution_authorized") is not False:
            fail(f"{entry.get('name')}: execution promoted")
        if entry.get("dma_authorized") is not False:
            fail(f"{entry.get('name')}: DMA promoted")
        if entry.get("action") == "DECOMPRESS":
            if entry.get("logical_digest_state") != "MATERIALIZATION_REQUIRED":
                fail(f"{entry.get('name')}: DEFLATE logical digest overclaim")
        elif entry.get("action") in {"COPY_STORE", "DIRECT_MAP_LAYOUT"}:
            if entry.get("logical_digest_state") != "SAME_AS_STORED_FOR_STORE":
                fail(f"{entry.get('name')}: STORE logical state")
        else:
            fail(f"unexpected fixture action: {entry.get('action')}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    data = json.loads(args.manifest.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail("manifest root")
    validate(data)
    print("ZIPRAF_CORPUS_U2_MANIFEST PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
