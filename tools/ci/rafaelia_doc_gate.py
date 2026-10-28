#!/usr/bin/env python3
"""Read-only, stdlib-only documentation gate. Run at repository root."""
import argparse
import pathlib
import re
import subprocess
import sys
from urllib.parse import unquote

DOC = re.compile(r"\.(?:md|mdx|rst|adoc)$", re.I)
LINK = re.compile(r"(?<!!)\[[^\]]*\]\((<[^>]+>|[^\s)]+)(?:\s+[^)]*)?\)")
INDEX = {"README.md", "README.rst", "INDEX.md", "index.md", "FILES_MAP.md"}

def tracked():
    p = subprocess.run(["git", "ls-files", "-z"], capture_output=True, check=True)
    return {pathlib.PurePosixPath(x.decode("utf-8", "surrogateescape")).as_posix()
            for x in p.stdout.split(b"\0") if x}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scope", choices=("added", "all"), default="added")
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    files = tracked()
    docs = sorted(p for p in files if DOC.search(p))
    if args.scope == "added":
        docs = [p for p in docs if pathlib.PurePosixPath(p).name.startswith(
            ("RAFAELIA_DOCS_RECONCILIATION_", "RAFAELIA_ROOT_CENSUS_"))]
    broken = []
    checked = 0
    for name in docs:
        file = pathlib.Path(name)
        try:
            content = file.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            broken.append((name, "UNREADABLE", str(exc)))
            continue
        for match in LINK.finditer(content):
            raw = match.group(1).strip("<>")
            if raw.startswith(("#", "https:", "http:", "mailto:", "data:")):
                continue
            path = unquote(raw.split("#", 1)[0].split("?", 1)[0])
            if not path:
                continue
            checked += 1
            candidate = (file.parent / path).resolve(strict=False)
            root = pathlib.Path.cwd().resolve()
            if not candidate.is_relative_to(root):
                broken.append((name, raw, "OUTSIDE_REPO"))
                continue
            if not candidate.exists():
                broken.append((name, raw, "MISSING_TARGET"))
    directories = sorted({str(pathlib.PurePosixPath(p).parent) for p in files
                          if "/" in p})
    unindexed = [d for d in directories if d != "." and not
                 any(f"{d}/{index}" in files for index in INDEX)]
    print(f"RAFAELIA_DOC_GATE scope={args.scope} docs={len(docs)} "
          f"links_checked={checked} broken={len(broken)} "
          f"dirs_without_local_index={len(unindexed)}")
    for a, b, c in broken[:80]:
        print(f"BROKEN {a} -> {b} [{c}]")
    for d in unindexed[:25]:
        print(f"INDEX_GAP {d} [HEURISTIC_NOT_ORPHAN]")
    if broken and args.strict:
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
