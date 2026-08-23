#!/usr/bin/env python3
"""List, verify and extract RAF Arena v1/v2 files without forge dependencies."""
from __future__ import annotations

import os
import struct
import sys

MAGIC0 = 0x46415252
MAGIC1 = 0x4C495045
HEADER_SIZE = 64
HEADER_FMT = "<8I8I"
VERSION_V1 = 1
VERSION_V2 = 2
RECORD_SIZE_V1 = 64
RECORD_SIZE_V2 = 80
PAYLOAD_ALIGN = 16
TOKEN_VAZIO = "TOKEN_VAZIO"

V1_BODY_FIELDS = [
    "s0", "s1", "s2", "s3", "s4", "s5", "s6",
    "crc_state", "cycle", "formula_ck", "ttl",
    "payload_off", "payload_len", "payload_crc", "seq",
]
V2_BODY_FIELDS = [
    "s0", "s1", "s2", "s3", "s4", "s5", "s6",
    "crc_state", "cycle", "domain_tag", "formula_ck", "ttl",
    "payload_off", "payload_len", "payload_crc", "seq",
    "reserved0", "reserved1", "reserved2",
]
V1_BODY_FMT = "<" + "I" * len(V1_BODY_FIELDS)
V1_RECORD_FMT = V1_BODY_FMT + "I"
V2_BODY_FMT = "<" + "I" * len(V2_BODY_FIELDS)
V2_RECORD_FMT = V2_BODY_FMT + "I"
assert struct.calcsize(V1_RECORD_FMT) == RECORD_SIZE_V1
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


def read_header(handle) -> dict:
    handle.seek(0)
    raw = handle.read(HEADER_SIZE)
    if len(raw) != HEADER_SIZE:
        return {"magic_ok": False, "crc_ok": False, "layout_ok": False}
    vals = struct.unpack(HEADER_FMT, raw)
    magic0, magic1, version, record_count, record_size, created_ts, last_ts, header_crc = vals[:8]
    body = struct.pack("<7I", magic0, magic1, version, record_count, record_size, created_ts, last_ts)
    layout_ok = (
        (version == VERSION_V1 and record_size == RECORD_SIZE_V1)
        or (version == VERSION_V2 and record_size == RECORD_SIZE_V2)
    )
    return {
        "magic_ok": magic0 == MAGIC0 and magic1 == MAGIC1,
        "crc_ok": crc32c(body) == header_crc,
        "layout_ok": layout_ok,
        "version": version,
        "record_count": record_count,
        "record_size": record_size,
        "created_ts": created_ts,
        "last_ts": last_ts,
    }


def read_record_at(handle, offset: int, header: dict):
    record_size = header["record_size"]
    handle.seek(offset)
    raw = handle.read(record_size)
    if len(raw) != record_size:
        return None

    if header["version"] == VERSION_V1:
        vals = struct.unpack(V1_RECORD_FMT, raw)
        state = vals[0:7]
        crc_state, cycle, formula_ck, ttl, payload_off, payload_len, payload_crc, seq, record_crc = vals[7:]
        domain_tag = None
        reserved = []
    else:
        vals = struct.unpack(V2_RECORD_FMT, raw)
        state = vals[0:7]
        (
            crc_state, cycle, domain_tag, formula_ck, ttl,
            payload_off, payload_len, payload_crc, seq,
            reserved0, reserved1, reserved2, record_crc,
        ) = vals[7:]
        reserved = [reserved0, reserved1, reserved2]

    return {
        "state": state,
        "crc_state": crc_state,
        "cycle": cycle,
        "domain_tag": domain_tag,
        "formula_ck": formula_ck,
        "ttl": ttl,
        "payload_off": payload_off,
        "payload_len": payload_len,
        "payload_crc": payload_crc,
        "seq": seq,
        "reserved": reserved,
        "record_crc_ok": crc32c(raw[:-4]) == record_crc,
    }


def iter_records(handle, header: dict):
    pos = HEADER_SIZE
    handle.seek(0, os.SEEK_END)
    end = handle.tell()
    yielded = 0
    while yielded < header["record_count"] and pos + header["record_size"] <= end:
        record = read_record_at(handle, pos, header)
        if record is None:
            break
        yield pos, record
        pad_len = (-record["payload_len"]) % PAYLOAD_ALIGN
        pos += header["record_size"] + record["payload_len"] + pad_len
        yielded += 1


def require_valid_header(header: dict) -> None:
    if not header.get("magic_ok") or not header.get("crc_ok") or not header.get("layout_ok"):
        raise ValueError(
            "header inválido: magic/crc/layout não sustentam leitura segura "
            f"(version={header.get('version')}, record_size={header.get('record_size')})"
        )


def cmd_list(arena_path: str) -> int:
    with open(arena_path, "rb") as handle:
        header = read_header(handle)
        require_valid_header(header)
        print(f"Arena: {arena_path}")
        print(
            f"  version={header['version']} record_size={header['record_size']} "
            f"record_count={header['record_count']} header_crc_ok={header['crc_ok']}"
        )
        print(f"  created_ts={header['created_ts']} last_ts={header['last_ts']}")
        print("-" * 88)
        for pos, record in iter_records(handle, header):
            domain = TOKEN_VAZIO if record["domain_tag"] is None else f"0x{record['domain_tag']:08x}"
            print(
                f"  seq={record['seq']:<4} off=0x{pos:06x} "
                f"state0=0x{record['state'][0]:08x} state6=0x{record['state'][6]:08x} "
                f"domain={domain} cycle={record['cycle']} ttl={record['ttl']} "
                f"payload_len={record['payload_len']} record_crc_ok={record['record_crc_ok']}"
            )
    return 0


def cmd_verify(arena_path: str) -> int:
    bad = 0
    total = 0
    with open(arena_path, "rb") as handle:
        header = read_header(handle)
        try:
            require_valid_header(header)
        except ValueError as exc:
            print(f"[FAIL] {exc}")
            return 1

        for pos, record in iter_records(handle, header):
            total += 1
            handle.seek(pos + header["record_size"])
            payload = handle.read(record["payload_len"])
            payload_ok = crc32c(payload) == record["payload_crc"]
            reserved_ok = header["version"] == VERSION_V1 or all(value == 0 for value in record["reserved"])
            ok = record["record_crc_ok"] and payload_ok and reserved_ok
            if not ok:
                bad += 1
                print(
                    f"[FAIL] seq={record['seq']} record_crc_ok={record['record_crc_ok']} "
                    f"payload_crc_ok={payload_ok} reserved_ok={reserved_ok}"
                )

        if total != header["record_count"]:
            print(f"[FAIL] header record_count={header['record_count']} mas apenas {total} records foram percorridos")
            return 3

    status = "OK" if bad == 0 else "FALHAS"
    print(f"Verificação v{header['version']}: {total - bad}/{total} records íntegros — {status}")
    return 0 if bad == 0 else 2


def cmd_extract(arena_path: str, seq_str: str, out_dir: str) -> int:
    target_seq = int(seq_str)
    os.makedirs(out_dir, exist_ok=True)
    with open(arena_path, "rb") as handle:
        header = read_header(handle)
        require_valid_header(header)
        for pos, record in iter_records(handle, header):
            if record["seq"] == target_seq:
                handle.seek(pos + header["record_size"])
                payload = handle.read(record["payload_len"])
                if crc32c(payload) != record["payload_crc"]:
                    print(f"[ERRO] seq={target_seq} possui payload CRC inválido; extração bloqueada.")
                    return 2
                out_path = os.path.join(out_dir, f"skill_seq{target_seq}.md")
                with open(out_path, "wb") as output:
                    output.write(payload)
                print(f"[OK] seq={target_seq} extraído -> {out_path} ({record['payload_len']}B)")
                return 0
    print(f"[ERRO] seq={target_seq} não encontrado.")
    return 1


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    command, arena_path = sys.argv[1], sys.argv[2]
    if command == "list":
        return cmd_list(arena_path)
    if command == "verify":
        return cmd_verify(arena_path)
    if command == "extract":
        if len(sys.argv) < 5:
            print("Uso: raf_arena_tool.py extract <arena.bin> <seq> <out_dir>")
            return 1
        return cmd_extract(arena_path, sys.argv[3], sys.argv[4])
    print(f"Comando desconhecido: {command}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
