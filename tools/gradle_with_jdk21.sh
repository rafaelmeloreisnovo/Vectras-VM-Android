#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

find_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi

  local candidates=(
    "/root/.local/share/mise/installs/java/21.0.2"
    "/usr/lib/jvm/java-21-openjdk-amd64"
    "/usr/lib/jvm/java-21-openjdk"
    "/usr/lib/jvm/temurin-21-jdk-amd64"
    "/usr/lib/jvm/java-17-openjdk-amd64"
    "/usr/lib/jvm/java-17-openjdk"
  )

  local c
  for c in "${candidates[@]}"; do
    if [[ -x "$c/bin/java" ]]; then
      printf '%s\n' "$c"
      return 0
    fi
  done

  if command -v java >/dev/null 2>&1; then
    local java_bin
    java_bin="$(command -v java)"
    java_bin="$(readlink -f "$java_bin")"
    if [[ -n "$java_bin" ]]; then
      printf '%s\n' "$(cd "$(dirname "$java_bin")/.." && pwd)"
      return 0
    fi
  fi

  return 1
}

JAVA_HOME_DETECTED="$(find_java_home || true)"
if [[ -z "$JAVA_HOME_DETECTED" || ! -x "$JAVA_HOME_DETECTED/bin/java" ]]; then
  echo "ERRO: JDK não encontrado. Defina JAVA_HOME com JDK 17+ e tente novamente." >&2
  exit 2
fi

export JAVA_HOME="$JAVA_HOME_DETECTED"
export PATH="$JAVA_HOME/bin:$PATH"

JAVA_MAJOR="$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 | awk -F= '/java\.specification\.version/ {gsub(/ /,"",$2); print $2; exit}')"
if [[ -z "$JAVA_MAJOR" ]]; then
  JAVA_MAJOR="$($JAVA_HOME/bin/java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1)"
fi

if [[ -z "$JAVA_MAJOR" || "$JAVA_MAJOR" -lt 17 ]]; then
  echo "ERRO: JDK incompatível ($JAVA_MAJOR). É necessário Java 17 ou superior." >&2
  exit 3
fi

echo "[gradle_with_jdk21] JAVA_HOME=$JAVA_HOME (major=$JAVA_MAJOR)"

if [[ -x "$REPO_ROOT/tools/check_android_toolchain.sh" ]]; then
  "$REPO_ROOT/tools/check_android_toolchain.sh" --quick >/dev/null || true
fi

cd "$REPO_ROOT"
exec ./gradlew "$@"
