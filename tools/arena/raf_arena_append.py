#!/usr/bin/env python3
"""Append one verified record to a RAF Arena.

Write contract:
  * new arenas are VERSION=2, RECORD_SIZE=80;
  * domain_tag is CRC32C(domain UTF-8) and is serialized in the record;
  * legacy VERSION=1 / 64-byte arenas remain readable but are not mutated.

The fail-closed v1 rule preserves append-only custody: old bytes are never
silently reinterpreted under a new schema. Choose a new arena path to start v2.
"""
from __future__ import annotations

import argparse
import os
import struct
import sys
import time

MAGIC0 = 0x46415252
MAGIC1 = 0x4C495045
VERSION_V1 = 1
VERSION_V2 = 2
HEADER_FMT = "<8I8I"
HEADER_SIZE = 64
RECORD_SIZE_V1 = 64
RECORD_SIZE_V2 = 80
PAYLOAD_ALIGN = 16

# V2 body: 19 u32 = 76 bytes; record_crc is the final u32 = 80 bytes.
V2_BODY_FIELDS = [
    "s0", "s1", "s2", "s3", "s4", "s5", "s6",
    "crc_state", "cycle", "domain_tag", "formula_ck", "ttl",
    "payload_off", "payload_len", "payload_crc", "seq",
    "reserved0", "reserved1", "reserved2",
]
V2_BODY_FMT = "<" + "I" * len(V2_BODY_FIELDS)
V2_RECORD_FMT = V2_BODY_FMT + "I"
assert struct.calcsize(V2_BODY_FMT) == 76
assert struct.calcsize(V2_RECORD_FMT) == RECORD_SIZE_V2


def build_crc32c_table() -> list[int]:
    poly = 0x82F63B78
    table: list[int] = []
    for i in range(256):
        crc = i
        for _ in range(8):
            crc = (crc >> 1) ^ poly if crc & 1 else crc >> 1
        table.append(crc & 0xFFFFFFFF)
    return table


_CRC32C_TABLE = build_crc32c_table()


def crc32c(data: bytes) -> int:
    crc = 0xFFFFFFFF
    for byte in data:
        crc = _CRC32C_TABLE[(crc ^ byte) & 0xFF] ^ (crc >> 8)
    return (~crc) & 0xFFFFFFFF


def read_header(handle):
    handle.seek(0)
    raw = handle.read(HEADER_SIZE)
    if len(raw) != HEADER_SIZE:
        return None
    vals = struct.unpack(HEADER_FMT, raw)
    magic0, magic1, version, record_count, record_size, created_ts, last_ts, header_crc = vals[:8]
    if magic0 != MAGIC0 or magic1 != MAGIC1:
        return None
    body = struct.pack("<7I", magic0, magic1, version, record_count, record_size, created_ts, last_ts)
    if crc32c(body) != header_crc:
        return None
    return {
        "magic0": magic0,
        "magic1": magic1,
        "version": version,
        "record_count": record_count,
        "record_size": record_size,
        "created_ts": created_ts,
        "last_ts": last_ts,
        "header_crc": header_crc,
    }


def write_header(handle, header) -> None:
    handle.seek(0)
    body = struct.pack(
        "<7I",
        header["magic0"], header["magic1"], header["version"],
        header["record_count"], header["record_size"],
        header["created_ts"], header["last_ts"],
    )
    header_crc = crc32c(body)
    raw = struct.pack(HEADER_FMT, *struct.unpack("<7I", body), header_crc, *([0] * 8))
    handle.write(raw)


def hex_to_u32(value: str) -> int:
    try:
        return int(value.strip(), 16) & 0xFFFFFFFF
    except ValueError:
        return 0


def parse_formula_ck(value: str) -> int:
    value = value.strip()
    if value.isdigit():
        return int(value) & 0xFFFFFFFF
    return hex_to_u32(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--arena", required=True)
    parser.add_argument("--s0", required=True)
    parser.add_argument("--s6", required=True)
    parser.add_argument("--crc", required=True)
    parser.add_argument("--cycle", required=True)
    parser.add_argument("--ttl", required=True)
    parser.add_argument("--domain", required=True)
    parser.add_argument("--formula", required=True)
    parser.add_argument("--ck", required=True)
    parser.add_argument("--payload", required=True, help="path to payload file")
    args = parser.parse_args()

    now = int(time.time())
    state = [hex_to_u32(args.s0), 0, 0, 0, 0, 0, hex_to_u32(args.s6)]
    crc_state = hex_to_u32(args.crc)
    cycle = hex_to_u32(args.cycle)
    ttl = hex_to_u32(args.ttl)
    domain_tag = crc32c(args.domain.encode("utf-8"))
    formula_ck = parse_formula_ck(args.ck)

    with open(args.payload, "rb") as payload_file:
        payload = payload_file.read()
    payload_crc = crc32c(payload)
    payload_padded = payload + b"\x00" * ((-len(payload)) % PAYLOAD_ALIGN)

    arena_path = args.arena
    is_new = not os.path.exists(arena_path) or os.path.getsize(arena_path) < HEADER_SIZE
    if is_new:
        with open(arena_path, "wb") as handle:
            header = {
                "magic0": MAGIC0,
                "magic1": MAGIC1,
                "version": VERSION_V2,
                "record_count": 0,
                "record_size": RECORD_SIZE_V2,
                "created_ts": now,
                "last_ts": now,
            }
            write_header(handle, header)
            handle.flush()
            os.fsync(handle.fileno())

    with open(arena_path, "r+b") as handle:
        header = read_header(handle)
        if header is None:
            print(f"[ERRO] arena '{arena_path}' corrompida ou header CRC inválido.", file=sys.stderr)
            return 1

        if header["version"] == VERSION_V1 and header["record_size"] == RECORD_SIZE_V1:
            print(
                f"[ERRO] arena v1 legada é somente leitura: '{arena_path}'. "
                "Preserve-a como evidência e use outro --arena para iniciar v2.",
                file=sys.stderr,
            )
            return 3
        if header["version"] != VERSION_V2 or header["record_size"] != RECORD_SIZE_V2:
            print(
                f"[ERRO] schema de arena não suportado: version={header['version']} "
                f"record_size={header['record_size']}",
                file=sys.stderr,
            )
            return 4

        seq = header["record_count"]
        handle.seek(0, os.SEEK_END)
        payload_off = handle.tell() - HEADER_SIZE + RECORD_SIZE_V2

        body = struct.pack(
            V2_BODY_FMT,
            state[0], state[1], state[2], state[3], state[4], state[5], state[6],
            crc_state, cycle, domain_tag, formula_ck, ttl,
            payload_off, len(payload), payload_crc, seq,
            0, 0, 0,
        )
        record_crc = crc32c(body)
        record = body + struct.pack("<I", record_crc)

        # Commit record/payload first; advance the mutable header only after the
        # append bytes are durable. An interrupted append therefore cannot make
        # record_count claim a record that was never persisted.
        handle.seek(0, os.SEEK_END)
        handle.write(record)
        handle.write(payload_padded)
        handle.flush()
        os.fsync(handle.fileno())

        header["record_count"] = seq + 1
        header["last_ts"] = now
        write_header(handle, header)
        handle.flush()
        os.fsync(handle.fileno())

    print(
        f"[OK] arena v2 '{arena_path}': record #{seq} commitado "
        f"(domain_tag=0x{domain_tag:08x}, payload={len(payload)}B, crc32c=0x{payload_crc:08x})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
