#!/usr/bin/env bash
set -u

ROOT="${1:-.}"
OUT_DIR="${2:-reports}"
TSV="$OUT_DIR/rafaelia_zrf_netraf_inventory.tsv"
JSONL="$OUT_DIR/rafaelia_zrf_netraf_inventory.jsonl"
mkdir -p "$OUT_DIR"

printf 'path\tsize_bytes\tsha256\tkind\tzip_entries\tandroid_manifest\tclasses_dex\tnative_libs\tmeta_inf\tstate\tnotes\n' > "$TSV"
: > "$JSONL"

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    printf 'TOKEN_VAZIO'
  fi
}

file_kind() {
  if command -v file >/dev/null 2>&1; then
    file -b "$1" | tr '\t\n' '  '
  else
    printf 'TOKEN_VAZIO'
  fi
}

zip_list() {
  local f="$1"
  if command -v unzip >/dev/null 2>&1; then
    unzip -Z1 "$f" 2>/dev/null || true
  elif command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$f" 2>/dev/null || true
  else
    true
  fi
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read())[1:-1])' 2>/dev/null || sed 's/\\/\\\\/g; s/"/\\"/g'
}

scan_one() {
  local f="$1"
  local size sha kind entries entry_count has_manifest has_dex native_count has_meta state notes

  size=$(wc -c < "$f" 2>/dev/null | tr -d ' ' || printf '0')
  sha=$(sha256_of "$f")
  kind=$(file_kind "$f")

  entries=$(zip_list "$f")
  if [ -n "$entries" ]; then
    entry_count=$(printf '%s\n' "$entries" | sed '/^$/d' | wc -l | tr -d ' ')
    printf '%s\n' "$entries" | grep -qx 'AndroidManifest.xml' && has_manifest=1 || has_manifest=0
    printf '%s\n' "$entries" | grep -Eq '(^|/)classes[0-9]*\.dex$' && has_dex=1 || has_dex=0
    native_count=$(printf '%s\n' "$entries" | grep -E '^lib/[^/]+/[^/]+\.so$' | wc -l | tr -d ' ')
    printf '%s\n' "$entries" | grep -Eq '^META-INF/' && has_meta=1 || has_meta=0
    state="PASS"
    notes="zip-readable; no execution; no decompilation"
  else
    entry_count=0
    has_manifest=0
    has_dex=0
    native_count=0
    has_meta=0
    state="AUDIT"
    notes="not zip-readable or unsupported; static metadata only"
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$f" "$size" "$sha" "$kind" "$entry_count" "$has_manifest" "$has_dex" "$native_count" "$has_meta" "$state" "$notes" >> "$TSV"

  local jf jk jn
  jf=$(printf '%s' "$f" | json_escape)
  jk=$(printf '%s' "$kind" | json_escape)
  jn=$(printf '%s' "$notes" | json_escape)
  printf '{"path":"%s","size_bytes":%s,"sha256":"%s","kind":"%s","zip_entries":%s,"android_manifest":%s,"classes_dex":%s,"native_libs":%s,"meta_inf":%s,"state":"%s","notes":"%s"}\n' \
    "$jf" "$size" "$sha" "$jk" "$entry_count" "$has_manifest" "$has_dex" "$native_count" "$has_meta" "$state" "$jn" >> "$JSONL"
}

# Keep the scan conservative: catalog artifacts; do not run, install, decode private data, or decompile.
find "$ROOT" -type f \( \
  -iname '*.apk' -o -iname '*.xapk' -o -iname '*.zip' -o -iname '*.zipraf' -o -iname '*.zrf' -o \
  -iname '*.img' -o -iname '*.bin' -o -iname '*.dex' -o -iname '*.so' -o -iname '*.json' -o -iname '*.jsonl' \
\) | sort | while IFS= read -r f; do
  scan_one "$f"
done

cat > "$OUT_DIR/RAFAELIA_ZRF_NETRAF_AUDIT_README.md" <<EOF
# RAFAELIA ZRF/NETRAF Audit Output

Generated files:

- \`rafaelia_zrf_netraf_inventory.tsv\`: spreadsheet-friendly inventory.
- \`rafaelia_zrf_netraf_inventory.jsonl\`: machine-readable inventory.

Rules:

- This scan does not execute APKs or binaries.
- This scan does not bypass protection or decrypt private content.
- \`TOKEN_VAZIO\` means a required tool/evidence was absent.
- A file being an APK/XAPK/split/native library is not evidence of wrongdoing.
EOF

echo "[RAFAELIA] inventory: $TSV"
echo "[RAFAELIA] jsonl:     $JSONL"
