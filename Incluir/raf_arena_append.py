#!/usr/bin/env python3
# ============================================================
# raf_arena_append.py — escreve um RafRecord + payload na arena
# Layout (deve espelhar raf_arena_format.h byte a byte):
#   HEADER (64B): magic0,magic1,version,record_count,record_size,
#                 created_ts,last_ts,header_crc, pad[8]
#   RECORD (64B): state[7],crc_state,cycle,domain_tag,formula_ck,ttl,
#                 payload_off,payload_len,payload_crc,seq,record_crc
#   PAYLOAD (var, padded to 16B): bytes do .md do skill
#
# CRC32c (Castagnoli, poly 0x1EDC6F41 / reversed 0x82F63B78) é
# implementado aqui sem zlib (zlib só tem CRC32 padrão, polinomio
# diferente) — tabela gerada em runtime, mesma matemática usada
# no .c freestanding, para que o binário seja auto-verificável.
# ============================================================
import argparse
import os
import struct
import sys
import time

MAGIC0 = 0x46415252
MAGIC1 = 0x4C495045
VERSION = 1
HEADER_FMT = "<8I8I"   # 8 campos nominais + 8 de padding (todos u32) = 64B
HEADER_SIZE = 64
RECORD_SIZE = 64
PAYLOAD_ALIGN = 16

# Layout do record gerado PROGRAMATICAMENTE a partir da lista de campos,
# para nunca mais depender de contar letras 'I' a mao (foi exatamente
# isso que quebrou nas duas primeiras tentativas desta sessao).
RECORD_FIELDS = [
    "s0", "s1", "s2", "s3", "s4", "s5", "s6",   # state[7]
    "crc_state", "cycle", "formula_ck", "ttl",
    "payload_off", "payload_len", "payload_crc", "seq",
]  # 15 campos = 60 bytes
BODY_FMT = "<" + "I" * len(RECORD_FIELDS)
RECORD_FMT = BODY_FMT + "I"  # + record_crc final = 16 campos = 64 bytes

assert struct.calcsize(BODY_FMT) == 60, struct.calcsize(BODY_FMT)
assert struct.calcsize(RECORD_FMT) == 64, struct.calcsize(RECORD_FMT)


def build_crc32c_table():
    poly = 0x82F63B78
    table = []
    for i in range(256):
        crc = i
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ poly
            else:
                crc >>= 1
        table.append(crc & 0xFFFFFFFF)
    return table


_CRC32C_TABLE = build_crc32c_table()


def crc32c(data: bytes) -> int:
    crc = 0xFFFFFFFF
    for b in data:
        crc = _CRC32C_TABLE[(crc ^ b) & 0xFF] ^ (crc >> 8)
    return (~crc) & 0xFFFFFFFF


def read_header(f):
    f.seek(0)
    raw = f.read(HEADER_SIZE)
    if len(raw) < HEADER_SIZE:
        return None
    vals = struct.unpack(HEADER_FMT, raw)
    (magic0, magic1, version, record_count, record_size,
     created_ts, last_ts, header_crc) = vals[:8]
    if magic0 != MAGIC0 or magic1 != MAGIC1:
        return None
    return {
        "magic0": magic0, "magic1": magic1, "version": version,
        "record_count": record_count, "record_size": record_size,
        "created_ts": created_ts, "last_ts": last_ts,
        "header_crc": header_crc,
    }


def write_header(f, hdr):
    f.seek(0)
    body = struct.pack(
        "<7I",
        hdr["magic0"], hdr["magic1"], hdr["version"],
        hdr["record_count"], hdr["record_size"],
        hdr["created_ts"], hdr["last_ts"],
    )
    hcrc = crc32c(body)
    pad = [0] * 8
    raw = struct.pack(HEADER_FMT, *struct.unpack("<7I", body), hcrc, *pad)
    f.write(raw)


def hex_to_u32(s: str) -> int:
    s = s.strip()
    try:
        return int(s, 16) & 0xFFFFFFFF
    except ValueError:
        return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--arena", required=True)
    ap.add_argument("--s0", required=True)
    ap.add_argument("--s6", required=True)
    ap.add_argument("--crc", required=True)
    ap.add_argument("--cycle", required=True)
    ap.add_argument("--ttl", required=True)
    ap.add_argument("--domain", required=True)
    ap.add_argument("--formula", required=True)
    ap.add_argument("--ck", required=True)
    ap.add_argument("--payload", required=True, help="path to .md payload file")
    args = ap.parse_args()

    now = int(time.time())

    # state[7]: temos s0 e s6 reais da execucao; os demais (1..5) nao
    # foram impressos pelo binario (so 5 valores sao printados), ficam
    # zerados aqui — TOKEN_VAZIO honesto em vez de inventar valor.
    s0 = hex_to_u32(args.s0)
    s6 = hex_to_u32(args.s6)
    state = [s0, 0, 0, 0, 0, 0, s6]
    crc_state = hex_to_u32(args.crc)
    cycle = hex_to_u32(args.cycle)
    ttl = hex_to_u32(args.ttl)

    formula_ck = int(args.ck) & 0xFFFFFFFF if args.ck.isdigit() else hex_to_u32(args.ck)

    with open(args.payload, "rb") as pf:
        payload = pf.read()
    pad_len = (-len(payload)) % PAYLOAD_ALIGN
    payload_padded = payload + b"\x00" * pad_len
    payload_crc = crc32c(payload)

    arena_path = args.arena
    is_new = not os.path.exists(arena_path) or os.path.getsize(arena_path) < HEADER_SIZE

    if is_new:
        with open(arena_path, "wb") as f:
            hdr = {
                "magic0": MAGIC0, "magic1": MAGIC1, "version": VERSION,
                "record_count": 0, "record_size": RECORD_SIZE,
                "created_ts": now, "last_ts": now,
            }
            write_header(f, hdr)

    with open(arena_path, "r+b") as f:
        hdr = read_header(f)
        if hdr is None:
            print(f"[ERRO] arena '{arena_path}' corrompida ou nao reconhecida.", file=sys.stderr)
            sys.exit(1)

        seq = hdr["record_count"]
        # offset do payload = a partir do FIM do header, contando todos
        # os records+payloads anteriores; aqui calculamos via tamanho do
        # arquivo, que e a forma mais robusta (nao acumula erro de calculo).
        f.seek(0, os.SEEK_END)
        payload_off = f.tell() - HEADER_SIZE + RECORD_SIZE  # offset apos este record

        body = struct.pack(
            BODY_FMT,
            state[0], state[1], state[2], state[3], state[4], state[5], state[6],
            crc_state, cycle, formula_ck, ttl,
            payload_off, len(payload), payload_crc, seq,
        )
        rcrc = crc32c(body)
        record = body + struct.pack("<I", rcrc)
        assert len(record) == RECORD_SIZE, f"record size mismatch: {len(record)}"

        f.seek(0, os.SEEK_END)
        f.write(record)
        f.write(payload_padded)

        hdr["record_count"] = seq + 1
        hdr["last_ts"] = now
        write_header(f, hdr)

    print(f"[OK] arena '{arena_path}': record #{seq} commitado "
          f"(payload {len(payload)}B, crc32c=0x{payload_crc:08x})")


if __name__ == "__main__":
    main()
