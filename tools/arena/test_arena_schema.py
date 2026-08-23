#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import struct
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
APPEND_PATH = HERE / "raf_arena_append.py"
TOOL_PATH = HERE / "raf_arena_tool.py"


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load {path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


append_mod = load_module("raf_arena_append", APPEND_PATH)
tool_mod = load_module("raf_arena_tool", TOOL_PATH)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build_v1(path: Path, payload: bytes) -> None:
    now = 1_700_000_000
    payload_crc = tool_mod.crc32c(payload)
    state = [0x11, 0, 0, 0, 0, 0, 0x66]
    body = struct.pack(
        tool_mod.V1_BODY_FMT,
        *state,
        0x12345678,
        7,
        0xAABBCCDD,
        35,
        tool_mod.RECORD_SIZE_V1,
        len(payload),
        payload_crc,
        0,
    )
    record = body + struct.pack("<I", tool_mod.crc32c(body))
    header_body = struct.pack(
        "<7I",
        tool_mod.MAGIC0,
        tool_mod.MAGIC1,
        tool_mod.VERSION_V1,
        1,
        tool_mod.RECORD_SIZE_V1,
        now,
        now,
    )
    header = struct.pack(
        tool_mod.HEADER_FMT,
        *struct.unpack("<7I", header_body),
        tool_mod.crc32c(header_body),
        *([0] * 8),
    )
    path.write_bytes(header + record + payload + b"\x00" * ((-len(payload)) % tool_mod.PAYLOAD_ALIGN))


def run_append(arena: Path, payload: Path, domain: str = "geometry") -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            sys.executable,
            str(APPEND_PATH),
            "--arena", str(arena),
            "--s0", "0x00000011",
            "--s6", "0x00000066",
            "--crc", "0x12345678",
            "--cycle", "0x00000007",
            "--ttl", "0x00000023",
            "--domain", domain,
            "--formula", "x+y",
            "--ck", "0xAABBCCDD",
            "--payload", str(payload),
        ],
        text=True,
        capture_output=True,
        check=False,
    )


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="raf-arena-test-") as td:
        root = Path(td)
        payload = root / "payload.md"
        payload.write_text("arena-schema-test\n", encoding="utf-8")

        # New writes are v2 and domain_tag is physically present.
        v2 = root / "arena-v2.bin"
        result = run_append(v2, payload)
        assert result.returncode == 0, result.stderr
        with v2.open("rb") as handle:
            header = tool_mod.read_header(handle)
            assert header["version"] == tool_mod.VERSION_V2
            assert header["record_size"] == tool_mod.RECORD_SIZE_V2
            pos, record = next(tool_mod.iter_records(handle, header))
            assert pos == tool_mod.HEADER_SIZE
            assert record["domain_tag"] == append_mod.crc32c(b"geometry")
        assert tool_mod.cmd_verify(str(v2)) == 0

        # Legacy v1 remains readable/verifiable.
        v1 = root / "arena-v1.bin"
        build_v1(v1, payload.read_bytes())
        assert tool_mod.cmd_verify(str(v1)) == 0

        # Append is fail-closed on v1 and custody is byte-identical afterwards.
        before = sha256(v1)
        result = run_append(v1, payload)
        assert result.returncode == 3, (result.returncode, result.stdout, result.stderr)
        assert sha256(v1) == before

    print("PASS: arena v2 domain serialization + v1 read compatibility + v1 immutability")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
