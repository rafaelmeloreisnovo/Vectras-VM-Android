#!/usr/bin/env python3
"""Verify the append-only Vectras provenance transform hash chain.

The first legacy v1 row is accepted as a genesis predecessor. Every v2 row must
point to the immediately preceding row and bind its canonical SHA-256. This is a
tamper-evident custody chain, not a consensus blockchain and not legal proof.
"""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path
from typing import Any

CHAIN_KIND = "TAMPER_EVIDENT_APPEND_ONLY_NOT_CONSENSUS_BLOCKCHAIN"
V2_SCHEMA = "rafaelia.provenance.transform.v2"
DEFAULT_REGISTER = Path("resources/compliance/PROVENANCE_TRANSFORM_REGISTER.jsonl")
HEX40 = set("0123456789abcdef")


def canonical_bytes(record: dict[str, Any]) -> bytes:
    payload = dict(record)
    payload.pop("record_sha256", None)
    return json.dumps(
        payload,
        ensure_ascii=True,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def record_digest(record: dict[str, Any]) -> str:
    if record.get("schema") == V2_SCHEMA:
        return hashlib.sha256(canonical_bytes(record)).hexdigest()
    # Legacy rows predate record_sha256. Bind their full canonical payload as the
    # genesis digest without rewriting the historical line.
    return hashlib.sha256(
        json.dumps(
            record,
            ensure_ascii=True,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    ).hexdigest()


def fail(message: str) -> int:
    print(f"PROVENANCE_CHAIN_FAIL: {message}", file=sys.stderr)
    return 1


def load_records(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as stream:
        for line_number, raw in enumerate(stream, 1):
            if not raw.strip():
                continue
            try:
                value = json.loads(raw)
            except json.JSONDecodeError as error:
                raise ValueError(f"line {line_number}: invalid JSON: {error}") from error
            if not isinstance(value, dict):
                raise ValueError(f"line {line_number}: record must be a JSON object")
            value["__line__"] = line_number
            records.append(value)
    return records


def clean_internal(record: dict[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in record.items() if not key.startswith("__")}


def is_hex_sha256(value: object) -> bool:
    return isinstance(value, str) and len(value) == 64 and set(value) <= HEX40


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_REGISTER
    if not path.is_file():
        return fail(f"missing register: {path}")

    try:
        records = load_records(path)
    except ValueError as error:
        return fail(str(error))

    if not records:
        return fail("register is empty")

    seen_ids: set[str] = set()
    v2_count = 0
    unresolved_count = 0

    previous_clean: dict[str, Any] | None = None
    previous_id: str | None = None
    previous_digest: str | None = None

    for raw_record in records:
        line_number = int(raw_record["__line__"])
        record = clean_internal(raw_record)
        record_id = record.get("record_id") or record.get("lineage_id")
        if not isinstance(record_id, str) or not record_id:
            return fail(f"line {line_number}: missing record_id/lineage_id")
        if record_id in seen_ids:
            return fail(f"line {line_number}: duplicate record id {record_id}")
        seen_ids.add(record_id)

        schema = record.get("schema")
        if schema == V2_SCHEMA:
            v2_count += 1
            if previous_clean is None or previous_id is None or previous_digest is None:
                return fail(f"line {line_number}: v2 record has no predecessor")
            if record.get("hash_chain_kind") != CHAIN_KIND:
                return fail(f"line {line_number}: invalid hash_chain_kind")
            if record.get("previous_record_id") != previous_id:
                return fail(
                    f"line {line_number}: previous_record_id={record.get('previous_record_id')!r} "
                    f"expected {previous_id!r}"
                )
            if record.get("previous_record_sha256") != previous_digest:
                return fail(
                    f"line {line_number}: predecessor digest mismatch for {record_id}"
                )

            stored_digest = record.get("record_sha256")
            computed_digest = record_digest(record)
            if not is_hex_sha256(stored_digest):
                return fail(f"line {line_number}: record_sha256 is not SHA-256 hex")
            if stored_digest != computed_digest:
                return fail(
                    f"line {line_number}: record digest mismatch: "
                    f"stored={stored_digest} computed={computed_digest}"
                )

            required = (
                "current_repository",
                "current_ref",
                "current_path",
                "current_git_blob",
                "origin_class",
                "originality_state",
                "roles",
                "origin_chain",
                "rights_state",
                "evidence_refs",
                "claim_allowed",
                "blocking_token_vazio",
            )
            missing = [key for key in required if key not in record]
            if missing:
                return fail(f"line {line_number}: missing v2 fields {missing}")

            if not isinstance(record["roles"], list) or not record["roles"]:
                return fail(f"line {line_number}: roles must be non-empty")
            if not isinstance(record["origin_chain"], list) or not record["origin_chain"]:
                return fail(f"line {line_number}: origin_chain must be non-empty")
            if not isinstance(record["evidence_refs"], list) or not record["evidence_refs"]:
                return fail(f"line {line_number}: evidence_refs must be non-empty")
            if not isinstance(record["blocking_token_vazio"], list):
                return fail(f"line {line_number}: blocking_token_vazio must be a list")

            serialized = json.dumps(record, ensure_ascii=True, sort_keys=True)
            has_unresolved = "TOKEN_VAZIO" in serialized or bool(record["blocking_token_vazio"])
            if has_unresolved:
                unresolved_count += 1
                if record.get("claim_allowed") is not False:
                    return fail(
                        f"line {line_number}: unresolved provenance may not set claim_allowed=true"
                    )

        elif previous_clean is not None:
            # New legacy records cannot be appended behind an established chain;
            # old history remains valid only as the prefix/genesis region.
            if v2_count:
                return fail(f"line {line_number}: legacy record appears after v2 chain start")

        previous_clean = record
        previous_id = record_id
        previous_digest = (
            str(record["record_sha256"])
            if schema == V2_SCHEMA
            else record_digest(record)
        )

    if v2_count == 0:
        return fail("no v2 hash-chain records found")

    print(f"PROVENANCE_CHAIN_OK records={len(records)} v2={v2_count} unresolved={unresolved_count}")
    print(f"PROVENANCE_CHAIN_HEAD_ID={previous_id}")
    print(f"PROVENANCE_CHAIN_HEAD_SHA256={previous_digest}")
    print("PROVENANCE_CHAIN_KIND=TAMPER_EVIDENT_APPEND_ONLY_NOT_CONSENSUS_BLOCKCHAIN")
    print("CLAIM_ALLOWED=false")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
