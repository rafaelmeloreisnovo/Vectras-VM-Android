#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

main_app_gradle="$ROOT_DIR/app/build.gradle"
legacy_app_gradle="$ROOT_DIR/android/app/build.gradle"
properties_file="$ROOT_DIR/gradle.properties"

fail() {
  echo "::error::$1" >&2
  exit 1
}

extract_prop() {
  local key="$1"
  local value
  value="$(sed -nE "s/^${key}=([0-9]+)$/\\1/p" "$properties_file" | head -n1)"
  [[ -n "$value" ]] || fail "Não foi possível resolver '${key}' em gradle.properties"
  printf '%s' "$value"
}

extract_gradle_number() {
  local pattern="$1"
  local file="$2"
  local value
  value="$(sed -nE "s/${pattern}/\\1/p" "$file" | head -n1)"
  [[ -n "$value" ]] || fail "Não foi possível extrair valor de '${pattern}' em ${file#$ROOT_DIR/}"
  printf '%s' "$value"
}

canonical_compile="$(extract_prop 'compile.api')"
canonical_target="$(extract_prop 'target.api')"
canonical_min="$(extract_prop 'min.api')"

[[ -f "$main_app_gradle" ]] || fail "Arquivo canônico ausente: app/build.gradle"
[[ -f "$legacy_app_gradle" ]] || fail "Arquivo legado ausente: android/app/build.gradle"

legacy_compile="$(extract_gradle_number '^[[:space:]]*compileSdk[[:space:]]+([0-9]+)[[:space:]]*$' "$legacy_app_gradle")"
legacy_target="$(extract_gradle_number '^[[:space:]]*targetSdk[[:space:]]+([0-9]+)[[:space:]]*$' "$legacy_app_gradle")"
legacy_min="$(extract_gradle_number '^[[:space:]]*minSdk[[:space:]]+([0-9]+)[[:space:]]*$' "$legacy_app_gradle")"

[[ "$legacy_compile" == "$canonical_compile" ]] || fail "Divergência compileSdk: canônico=${canonical_compile} legado=${legacy_compile}"
[[ "$legacy_target" == "$canonical_target" ]] || fail "Divergência targetSdk: canônico=${canonical_target} legado=${legacy_target}"
[[ "$legacy_min" == "$canonical_min" ]] || fail "Divergência minSdk: canônico=${canonical_min} legado=${legacy_min}"

# Built-in Bash globbing plus grep avoids a silent PASS if ripgrep is unavailable.
# Keep the same file extensions and path signatures as the original scanner.
shopt -s globstar nullglob dotglob
scan_files=(
  "$ROOT_DIR"/.github/workflows/*.yml
  "$ROOT_DIR"/.github/workflows/*.yaml
  "$ROOT_DIR"/tools/**/*.sh
  "$ROOT_DIR"/tools/**/*.py
)
(("${#scan_files[@]}" > 0)) || fail "Nenhum arquivo encontrado para auditar caminhos Android"
for scan_file in "${scan_files[@]}"; do
  [[ "$scan_file" == "$ROOT_DIR/tools/ci/validate_android_sdk_alignment.sh" ]] && continue
  if grep -nE \
    -e 'cd[[:space:]]+android(/|$)' \
    -e '(^|[^A-Za-z0-9_./-])android/gradlew([^A-Za-z0-9_./-]|$)' \
    -e '(^|[^A-Za-z0-9_./-])-p[[:space:]]+android([^A-Za-z0-9_./-]|$)' \
    -- "$scan_file"; then
    fail "Fluxo oficial referencia o caminho legado android/ em ${scan_file#"$ROOT_DIR"/}"
  else
    scan_status=$?
    ((scan_status == 1)) || fail "Falha ao auditar ${scan_file#"$ROOT_DIR"/} (grep=${scan_status})"
  fi
done

echo "Android SDK/build path alignment OK: compileSdk=${canonical_compile}, targetSdk=${canonical_target}, minSdk=${canonical_min}."
