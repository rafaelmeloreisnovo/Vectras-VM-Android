#!/usr/bin/env python3
"""
populate_seed_frames.py -- fill PLACEHOLDER content in frames_seed.json
from an omega_msgs.jsonl export.

Usage:
    python3 tools/populate_seed_frames.py \
        --jsonl path/to/omega_msgs.jsonl \
        --frames docs/research/data/frames_seed.json \
        [--dry-run]

Input schema (omega_msgs.jsonl) -- one JSON object per line:
    {"conv_i": 2688, "role": "user",   "text": "...", ...}
    {"conv_i": 2688, "role": "assistant", "text": "...", ...}

Any additional fields on each line are preserved but ignored.
The tool auto-detects whether the field name is "text", "content",
or "body" so it works with multiple export formats.

Output:
    Updates frames_seed.json in-place; reports how many frames were
    filled vs. how many still need data.

Gap: G20 (docs/ALL_GAPS_REGISTRY.md).
"""

import argparse
import json
import pathlib
import sys
from collections import defaultdict


PLACEHOLDER_PREFIX = "[PLACEHOLDER"
TEXT_FIELDS = ("text", "content", "body", "message")
MAX_EXCERPT_CHARS = 1200


def _extract_text(msg: dict) -> str:
    for key in TEXT_FIELDS:
        val = msg.get(key)
        if isinstance(val, str) and val.strip():
            return val.strip()
    return ""


def _load_jsonl(path: pathlib.Path) -> dict[int, list[dict]]:
    """Return mapping conv_i -> list of messages (ordered)."""
    convs: dict[int, list[dict]] = defaultdict(list)
    with path.open(encoding="utf-8") as fh:
        for lineno, raw in enumerate(fh, 1):
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError as exc:
                print(f"  WARN: line {lineno} not valid JSON — {exc}", file=sys.stderr)
                continue
            conv_i = obj.get("conv_i")
            if not isinstance(conv_i, int):
                try:
                    conv_i = int(obj.get("conv_i", ""))
                except (ValueError, TypeError):
                    continue
            convs[int(conv_i)].append(obj)
    return dict(convs)


def _summarise(messages: list[dict]) -> str:
    """Build a content summary from a list of messages."""
    parts: list[str] = []
    total_chars = 0
    for msg in messages:
        text = _extract_text(msg)
        if not text:
            continue
        role = msg.get("role", "?")
        excerpt = text[:400].replace("\n", " ")
        entry = f"[{role}] {excerpt}"
        if total_chars + len(entry) > MAX_EXCERPT_CHARS:
            parts.append("…")
            break
        parts.append(entry)
        total_chars += len(entry)
    if not parts:
        return "(sem conteudo extraivel)"
    return " | ".join(parts)


def _parse_conv_i(content: str) -> int | None:
    """Extract conv_i from a PLACEHOLDER content string."""
    for token in content.split():
        if token.startswith("conv_i="):
            try:
                return int(token.split("=", 1)[1])
            except ValueError:
                pass
    return None


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Populate PLACEHOLDER seed frames from omega_msgs.jsonl"
    )
    parser.add_argument(
        "--jsonl",
        required=True,
        type=pathlib.Path,
        help="Path to omega_msgs.jsonl export",
    )
    parser.add_argument(
        "--frames",
        default=pathlib.Path("docs/research/data/frames_seed.json"),
        type=pathlib.Path,
        help="Path to frames_seed.json (default: docs/research/data/frames_seed.json)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print what would change without modifying the file",
    )
    args = parser.parse_args()

    if not args.jsonl.is_file():
        print(f"ERROR: {args.jsonl} not found", file=sys.stderr)
        return 1
    if not args.frames.is_file():
        print(f"ERROR: {args.frames} not found", file=sys.stderr)
        return 1

    print(f"Loading conversations from {args.jsonl} …")
    convs = _load_jsonl(args.jsonl)
    print(f"  Loaded {len(convs)} distinct conv_i values.")

    frames = json.loads(args.frames.read_text(encoding="utf-8"))

    filled = 0
    still_placeholder = 0
    missing_data = []

    for frame in frames:
        content = frame.get("content", "")
        if not isinstance(content, str) or not content.startswith(PLACEHOLDER_PREFIX):
            continue

        conv_i = _parse_conv_i(content)
        if conv_i is None:
            still_placeholder += 1
            continue

        messages = convs.get(conv_i)
        if not messages:
            still_placeholder += 1
            missing_data.append((frame["id"], conv_i))
            continue

        summary = _summarise(messages)
        meta = content.split("] ", 1)[1] if "] " in content else ""
        new_content = f"{meta} | {summary}" if meta else summary

        if args.dry_run:
            print(f"  DRY-RUN {frame['id']} (conv_i={conv_i}): {new_content[:120]}…")
        else:
            frame["content"] = new_content
        filled += 1

    if not args.dry_run and filled > 0:
        args.frames.write_text(
            json.dumps(frames, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"\nWrote {args.frames}")

    print(f"\nResult: {filled} frame(s) filled, {still_placeholder} still PLACEHOLDER.")
    if missing_data:
        print("  Frames with no data in jsonl:")
        for fid, ci in missing_data:
            print(f"    {fid} (conv_i={ci})")

    return 0 if still_placeholder == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
