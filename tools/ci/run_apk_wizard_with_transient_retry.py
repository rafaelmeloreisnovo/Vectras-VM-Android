#!/usr/bin/env python3
"""Run the APK wizard with bounded retries for transient transport failures only.

This wrapper does not alter runtime-source selection or integrity policy. Each retry
re-runs the same build command, which in turn reconstructs the same pinned URLs and
revalidates exact size, Git blob SHA-1, SHA-256, TAR safety and family markers.
Non-transport failures are returned immediately without retry.
"""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from collections.abc import Callable, Sequence

MAX_OUTPUT_TAIL_CHARS = 131072
TRANSIENT_TRANSPORT_PATTERNS = (
    re.compile(r"Connection reset by peer", re.IGNORECASE),
    re.compile(r"ConnectionResetError", re.IGNORECASE),
    re.compile(r"RemoteDisconnected", re.IGNORECASE),
    re.compile(r"(?:urlopen error|URLError).*timed out", re.IGNORECASE),
    re.compile(r"TimeoutError", re.IGNORECASE),
    re.compile(r"Temporary failure in name resolution", re.IGNORECASE),
    re.compile(r"HTTP Error (?:408|425|429|500|502|503|504)\b", re.IGNORECASE),
    re.compile(r"HTTP (?:408|425|429|500|502|503|504)\b", re.IGNORECASE),
)


def is_transient_transport_failure(text: str) -> bool:
    return any(pattern.search(text) for pattern in TRANSIENT_TRANSPORT_PATTERNS)


def run_once(command: Sequence[str]) -> tuple[int, str]:
    process = subprocess.Popen(
        list(command),
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    if process.stdout is None:
        raise RuntimeError("subprocess stdout pipe was not created")
    tail = ""
    for line in process.stdout:
        sys.stdout.write(line)
        sys.stdout.flush()
        tail = (tail + line)[-MAX_OUTPUT_TAIL_CHARS:]
    return process.wait(), tail


def run_with_retry(
    command: Sequence[str],
    *,
    attempts: int = 4,
    base_backoff_seconds: float = 1.0,
    runner: Callable[[Sequence[str]], tuple[int, str]] = run_once,
    sleeper: Callable[[float], None] = time.sleep,
) -> int:
    if attempts < 1:
        raise ValueError("attempts must be >= 1")
    if base_backoff_seconds < 0:
        raise ValueError("base_backoff_seconds must be >= 0")
    if not command:
        raise ValueError("command must not be empty")

    for attempt in range(1, attempts + 1):
        rc, output_tail = runner(command)
        if rc == 0:
            return 0
        transient = is_transient_transport_failure(output_tail)
        if not transient:
            print(
                f"APK_WIZARD_RETRY_DECISION attempt={attempt}/{attempts} "
                f"retry=false reason=NON_TRANSIENT_FAILURE rc={rc}",
                file=sys.stderr,
            )
            return rc
        if attempt == attempts:
            print(
                f"APK_WIZARD_RETRY_DECISION attempt={attempt}/{attempts} "
                f"retry=false reason=TRANSIENT_RETRY_EXHAUSTED rc={rc}",
                file=sys.stderr,
            )
            return rc
        delay = min(base_backoff_seconds * (2 ** (attempt - 1)), 8.0)
        print(
            f"APK_WIZARD_RETRY_DECISION attempt={attempt}/{attempts} "
            f"retry=true reason=TRANSIENT_TRANSPORT delay_seconds={delay:g}",
            file=sys.stderr,
        )
        sleeper(delay)
    raise AssertionError("unreachable retry state")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--attempts", type=int, default=4)
    parser.add_argument("--base-backoff-seconds", type=float, default=1.0)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = list(args.command)
    if command and command[0] == "--":
        command = command[1:]
    if not command:
        parser.error("command is required after --")
    return run_with_retry(
        command,
        attempts=args.attempts,
        base_backoff_seconds=args.base_backoff_seconds,
    )


if __name__ == "__main__":
    raise SystemExit(main())
