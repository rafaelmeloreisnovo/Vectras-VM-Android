#!/usr/bin/env python3
# ============================================================
# raf_arena_tool.py — lista / extrai / verifica raf_arena.bin
#
# Uso:
#   raf_arena_tool.py list   <arena.bin>
#   raf_arena_tool.py verify <arena.bin>
#   raf_arena_tool.py extract <arena.bin> <seq> <out_dir>
#
# Le exatamente o layout escrito por raf_arena_append.py / descrito
# em raf_arena_format.h. Nao depende de nenhuma outra ferramenta do
# forge — pode auditar a arena anos depois, sozinho.
# ============================================================
import struct
import sys
import os

MAGIC0 = 0x46415252
MAGIC1 = 0x4C495045
HEADER_SIZE = 64
RECORD_SIZE = 64
HEADER_FMT = "<8I8I"

# Mesmo layout de raf_arena_append.py, gerado programaticamente para
# nunca divergir por contagem manual de 'I's entre os dois arquivos.
RECORD_FIELDS = [
    "s0", "s1", "s2", "s3", "s4", "s5", "s6",
    "crc_state", "cycle", "formula_ck", "ttl",
    "payload_off", "payload_len", "payload_crc", "seq",
]
BODY_FMT = "<" + "I" * len(RECORD_FIELDS)
RECORD_FMT = BODY_FMT + "I"
assert struct.calcsize(BODY_FMT) == 60
assert struct.calcsize(RECORD_FMT) == 64


def build_crc32c_table():
    poly = 0x82F63B78
    table = []
    for i in range(256):
        crc = i
        for _ in range(8):
            crc = (crc >> 1) ^ poly if crc & 1 else crc >> 1
        table.append(crc & 0xFFFFFFFF)
    return table


_T = build_crc32c_table()


def crc32c(data: bytes) -> int:
    crc = 0xFFFFFFFF
    for b in data:
        crc = _T[(crc ^ b) & 0xFF] ^ (crc >> 8)
    return (~crc) & 0xFFFFFFFF


def read_header(f):
    f.seek(0)
    raw = f.read(HEADER_SIZE)
    vals = struct.unpack(HEADER_FMT, raw)
    (magic0, magic1, version, record_count, record_size,
     created_ts, last_ts, header_crc) = vals[:8]
    ok_magic = (magic0 == MAGIC0 and magic1 == MAGIC1)
    body = struct.pack("<7I", magic0, magic1, version, record_count,
                        record_size, created_ts, last_ts)
    ok_crc = (crc32c(body) == header_crc)
    return {
        "magic_ok": ok_magic, "crc_ok": ok_crc,
        "version": version, "record_count": record_count,
        "record_size": record_size, "created_ts": created_ts,
        "last_ts": last_ts,
    }


def read_record_at(f, offset):
    f.seek(offset)
    raw = f.read(RECORD_SIZE)
    if len(raw) < RECORD_SIZE:
        return None
    vals = struct.unpack(RECORD_FMT, raw)
    state = vals[0:7]
    (crc_state, cycle, formula_ck, ttl,
     payload_off, payload_len, payload_crc, seq, record_crc) = vals[7:]
    body = raw[:-4]
    crc_ok = (crc32c(body) == record_crc)
    return {
        "state": state, "crc_state": crc_state, "cycle": cycle,
        "formula_ck": formula_ck, "ttl": ttl,
        "payload_off": payload_off, "payload_len": payload_len,
        "payload_crc": payload_crc, "seq": seq, "record_crc_ok": crc_ok,
    }


def iter_records(f, header):
    pos = HEADER_SIZE
    f.seek(0, os.SEEK_END)
    end = f.tell()
    while pos + RECORD_SIZE <= end:
        rec = read_record_at(f, pos)
        if rec is None:
            break
        yield pos, rec
        pad_len = (-rec["payload_len"]) % 16
        pos = pos + RECORD_SIZE + rec["payload_len"] + pad_len


def cmd_list(arena_path):
    with open(arena_path, "rb") as f:
        hdr = read_header(f)
        print(f"Arena: {arena_path}")
        print(f"  magic_ok={hdr['magic_ok']} header_crc_ok={hdr['crc_ok']} "
              f"version={hdr['version']} record_count={hdr['record_count']}")
        print(f"  created_ts={hdr['created_ts']} last_ts={hdr['last_ts']}")
        print("-" * 70)
        for pos, rec in iter_records(f, hdr):
            print(f"  seq={rec['seq']:<4} off=0x{pos:06x}  "
                  f"state0=0x{rec['state'][0]:08x}  state6=0x{rec['state'][6]:08x}  "
                  f"cycle={rec['cycle']:<3} ttl={rec['ttl']:<3}  "
                  f"payload_len={rec['payload_len']:<5}  "
                  f"record_crc_ok={rec['record_crc_ok']}")


def cmd_verify(arena_path):
    bad = 0
    total = 0
    with open(arena_path, "rb") as f:
        hdr = read_header(f)
        if not (hdr["magic_ok"] and hdr["crc_ok"]):
            print("[FAIL] header invalido ou corrompido.")
            sys.exit(1)
        for pos, rec in iter_records(f, hdr):
            total += 1
            f.seek(pos + RECORD_SIZE)
            payload = f.read(rec["payload_len"])
            payload_ok = (crc32c(payload) == rec["payload_crc"])
            ok = rec["record_crc_ok"] and payload_ok
            if not ok:
                bad += 1
                print(f"[FAIL] seq={rec['seq']} record_crc_ok={rec['record_crc_ok']} "
                      f"payload_crc_ok={payload_ok}")
    status = "OK" if bad == 0 else "FALHAS"
    print(f"Verificação: {total - bad}/{total} records íntegros — {status}")
    sys.exit(0 if bad == 0 else 2)


def cmd_extract(arena_path, seq_str, out_dir):
    target_seq = int(seq_str)
    os.makedirs(out_dir, exist_ok=True)
    with open(arena_path, "rb") as f:
        hdr = read_header(f)
        for pos, rec in iter_records(f, hdr):
            if rec["seq"] == target_seq:
                f.seek(pos + RECORD_SIZE)
                payload = f.read(rec["payload_len"])
                out_path = os.path.join(out_dir, f"skill_seq{target_seq}.md")
                with open(out_path, "wb") as of:
                    of.write(payload)
                print(f"[OK] seq={target_seq} extraído -> {out_path} "
                      f"({rec['payload_len']}B)")
                return
    print(f"[ERRO] seq={target_seq} não encontrado.")
    sys.exit(1)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    cmd, arena_path = sys.argv[1], sys.argv[2]
    if cmd == "list":
        cmd_list(arena_path)
    elif cmd == "verify":
        cmd_verify(arena_path)
    elif cmd == "extract":
        if len(sys.argv) < 5:
            print("Uso: raf_arena_tool.py extract <arena.bin> <seq> <out_dir>")
            sys.exit(1)
        cmd_extract(arena_path, sys.argv[3], sys.argv[4])
    else:
        print(f"Comando desconhecido: {cmd}")
        sys.exit(1)


if __name__ == "__main__":
    main()
