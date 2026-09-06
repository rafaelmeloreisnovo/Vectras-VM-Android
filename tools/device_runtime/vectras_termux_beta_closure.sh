#!/data/data/com.termux.rafacodephi/files/usr/bin/bash
# Vectras/RAFCODEPHI beta closure doctor.
# Audits the whole chain and, with --repair, performs idempotent staged repair.
# It intentionally surveys every gate before returning a fail-closed status.
set -u

MODE="audit"
case "${1:-}" in
  "") ;;
  --audit) MODE="audit" ;;
  --repair|--enterprise) MODE="repair" ;;
  -h|--help)
    cat <<'EOF'
Usage: vectras_termux_beta_closure.sh [--audit|--repair|--enterprise]
  --audit       inspect every gate; do not install packages (default)
  --repair      staged pkg repair + full inspection; RAFCODEPHI gate is required
  --enterprise  alias for --repair
EOF
    exit 0
    ;;
  *) printf 'unknown option: %s\n' "$1" >&2; exit 2 ;;
esac

PREFIX="${PREFIX:-/data/data/com.termux.rafacodephi/files/usr}"
PKG_APP="${VECTRAS_PACKAGE:-com.rafacodephi.app}"
BASE_OUT="${VECTRAS_BETA_RECEIPT_DIR:-$HOME/.local/state/vectras-beta}"
RUN_AS="$(command -v run-as 2>/dev/null || true)"
[ -n "$RUN_AS" ] || RUN_AS="/system/bin/run-as"
mkdir -p "$BASE_OUT" 2>/dev/null || true
STAMP="$(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || date +%Y%m%dT%H%M%S)"
RECEIPT="$BASE_OUT/vectras-beta-closure-$STAMP.tsv"
LATEST="$BASE_OUT/latest.tsv"
: > "$RECEIPT"

failures=0
blockers=0
passes=0
token_vazios=0

clean_field() {
  printf '%s' "$1" | tr '\t\r\n' '   '
}

record() {
  local phase="$1" status="$2" subject="$3" detail="${4:-}"
  printf '%s\t%s\t%s\t%s\t%s\n' "$STAMP" "$phase" "$status" "$subject" "$(clean_field "$detail")" >> "$RECEIPT"
  printf '[%-13s] %-11s %-30s %s\n' "$phase" "$status" "$subject" "$detail"
  case "$status" in
    PASS) passes=$((passes + 1)) ;;
    BLOCKER) blockers=$((blockers + 1)); failures=$((failures + 1)) ;;
    FAIL) failures=$((failures + 1)) ;;
    TOKEN_VAZIO) token_vazios=$((token_vazios + 1)) ;;
  esac
}

run_capture() {
  local phase="$1" subject="$2"; shift 2
  local output rc
  output="$("$@" 2>&1)"; rc=$?
  if [ "$rc" -eq 0 ]; then
    record "$phase" PASS "$subject" "$(printf '%s' "$output" | head -n 1)"
  else
    record "$phase" FAIL "$subject" "rc=$rc $(printf '%s' "$output" | head -n 1)"
  fi
  return 0
}

check_command() {
  local cmd="$1" phase="TERMUX_COMMAND"
  if command -v "$cmd" >/dev/null 2>&1; then
    record "$phase" PASS "$cmd" "$(command -v "$cmd")"
  else
    record "$phase" BLOCKER "$cmd" "command not found"
  fi
}

check_pkg() {
  local pkg="$1"
  if command -v dpkg >/dev/null 2>&1 && dpkg -s "$pkg" >/dev/null 2>&1; then
    local version
    version="$(dpkg-query -W -f='${Version}' "$pkg" 2>/dev/null || true)"
    record TERMUX_PACKAGE PASS "$pkg" "${version:-installed}"
  else
    record TERMUX_PACKAGE BLOCKER "$pkg" "not installed/resolved"
  fi
}

check_app_path() {
  local rel="$1" id="$2" kind="${3:-exists}"
  if [ ! -x "$RUN_AS" ]; then
    record APP_FILES TOKEN_VAZIO "$id" "run-as unavailable; in-app receipt must prove private runtime"
    return 0
  fi
  local testop="-e"
  [ "$kind" = "exec" ] && testop="-x"
  if "$RUN_AS" "$PKG_APP" sh -c "[ $testop \"\$PWD/$rel\" ]" >/dev/null 2>&1; then
    record APP_FILES PASS "$id" "$rel $kind"
  else
    local rc=$?
    if ! "$RUN_AS" "$PKG_APP" sh -c 'pwd' >/dev/null 2>&1; then
      record APP_FILES TOKEN_VAZIO "$id" "run-as denied/non-debuggable; use in-app vectras.runtime-evidence.v1 receipt (probe rc=$rc)"
    else
      record APP_FILES BLOCKER "$id" "$rel missing or not $kind"
    fi
  fi
}

record META PASS mode "$MODE"
record META PASS prefix "$PREFIX"
record META PASS package "$PKG_APP"
record META PASS machine "$(uname -m 2>/dev/null || echo TOKEN_VAZIO)"
record META PASS android_sdk "$(getprop ro.build.version.sdk 2>/dev/null || echo TOKEN_VAZIO)"
record META PASS page_size "$(getconf PAGESIZE 2>/dev/null || echo TOKEN_VAZIO)"

BOOTSTRAP_PKGS=(
  bash aria2 tar xterm pulseaudio x11-repo proot proot-distro
  ninja clang lld cmake make binutils file patchelf
)
QEMU_PKGS=(qemu-common qemu-system-x86-64-headless qemu-utils)

if [ "$MODE" = "repair" ]; then
  if ! command -v pkg >/dev/null 2>&1; then
    record REPAIR BLOCKER pkg "Termux pkg command unavailable"
  else
    out="$(pkg install -y "${BOOTSTRAP_PKGS[@]}" 2>&1)"; rc=$?
    [ "$rc" -eq 0 ] && record REPAIR PASS bootstrap_toolchain "stage1 installed" || record REPAIR FAIL bootstrap_toolchain "rc=$rc $(printf '%s' "$out" | tail -n 1)"

    out="$(pkg update -y 2>&1)"; rc=$?
    [ "$rc" -eq 0 ] && record REPAIR PASS metadata_refresh "x11 repository metadata refreshed" || record REPAIR FAIL metadata_refresh "rc=$rc $(printf '%s' "$out" | tail -n 1)"

    out="$(pkg install -y "${QEMU_PKGS[@]}" 2>&1)"; rc=$?
    [ "$rc" -eq 0 ] && record REPAIR PASS vectras_qemu "stage2 installed" || record REPAIR FAIL vectras_qemu "rc=$rc $(printf '%s' "$out" | tail -n 1)"
  fi
fi

for p in "${BOOTSTRAP_PKGS[@]}"; do check_pkg "$p"; done
for p in "${QEMU_PKGS[@]}"; do check_pkg "$p"; done

for c in proot proot-distro ninja clang ld.lld cmake make readelf file patchelf qemu-system-x86_64 qemu-img; do
  check_command "$c"
done

command -v proot >/dev/null 2>&1 && run_capture TERMUX_EXEC proot_version proot --version
command -v proot-distro >/dev/null 2>&1 && run_capture TERMUX_EXEC proot_distro_list proot-distro list
command -v ninja >/dev/null 2>&1 && run_capture TERMUX_EXEC ninja_version ninja --version
command -v clang >/dev/null 2>&1 && run_capture TERMUX_EXEC clang_version clang --version
command -v qemu-system-x86_64 >/dev/null 2>&1 && run_capture TERMUX_EXEC qemu_x86_64_version qemu-system-x86_64 --version
command -v qemu-img >/dev/null 2>&1 && run_capture TERMUX_EXEC qemu_img_version qemu-img --version

if /system/bin/cmd package path "$PKG_APP" >/tmp/vectras_pkg_path.$$ 2>/tmp/vectras_pkg_err.$$; then
  APP_PATH_LINE="$(head -n 1 /tmp/vectras_pkg_path.$$)"
  record ANDROID_PACKAGE PASS installed "$APP_PATH_LINE"
  APP_APK="${APP_PATH_LINE#package:}"
  if [ -r "$APP_APK" ] && command -v sha256sum >/dev/null 2>&1; then
    record ANDROID_PACKAGE PASS apk_sha256 "$(sha256sum "$APP_APK" | awk '{print $1}')"
  else
    record ANDROID_PACKAGE TOKEN_VAZIO apk_sha256 "APK path not readable externally; in-app receipt hashes its own sourceDir"
  fi
else
  record ANDROID_PACKAGE BLOCKER installed "$(head -n 1 /tmp/vectras_pkg_err.$$ 2>/dev/null || echo package_not_visible)"
fi
rm -f /tmp/vectras_pkg_path.$$ /tmp/vectras_pkg_err.$$ 2>/dev/null || true

check_app_path files/usr APP_USR exists
check_app_path files/usr/bin/proot APP_PROOT exists
check_app_path files/usr/bin/proot APP_PROOT_EXEC exec
check_app_path files/distro APP_DISTRO exists
check_app_path files/distro/bin/sh APP_DISTRO_SH exec
check_app_path files/distro/usr/bin/qemu-system-x86_64 APP_QEMU_X86_64 exec
check_app_path files/distro/usr/bin/qemu-system-i386 APP_QEMU_I386 exec
check_app_path files/distro/usr/bin/qemu-system-aarch64 APP_QEMU_AARCH64 exec
check_app_path files/distro/usr/bin/qemu-img APP_QEMU_IMG exec

RAF_GATE="$PREFIX/libexec/rafproot-fs"
if [ -x "$RAF_GATE" ]; then
  record RAFCODE_GATE PASS rafproot_fs "$RAF_GATE"
  if command -v sha256sum >/dev/null 2>&1; then
    record RAFCODE_GATE PASS gate_sha256 "$(sha256sum "$RAF_GATE" | awk '{print $1}')"
  else
    record RAFCODE_GATE TOKEN_VAZIO gate_sha256 "sha256sum unavailable"
  fi
  run_capture RAFCODE_GATE probe "$RAF_GATE" --probe
  run_capture RAFCODE_GATE ninja "$RAF_GATE" --run ninja --version
  run_capture RAFCODE_GATE proot "$RAF_GATE" --run proot --version
  run_capture RAFCODE_GATE qemu "$RAF_GATE" --run qemu-system-x86_64 --version
else
  if [ "$MODE" = "repair" ]; then
    record RAFCODE_GATE BLOCKER rafproot_fs "$RAF_GATE missing in enterprise repair mode"
  else
    record RAFCODE_GATE TOKEN_VAZIO rafproot_fs "$RAF_GATE not installed; audit claim remains open"
  fi
fi

cp "$RECEIPT" "$LATEST" 2>/dev/null || true
printf '\nReceipt: %s\n' "$RECEIPT"
printf 'Summary: PASS=%d FAIL/BLOCKER=%d BLOCKER=%d TOKEN_VAZIO=%d\n' "$passes" "$failures" "$blockers" "$token_vazios"

# Full survey first, fail-closed status second. Any real FAIL is a failed closure.
if [ "$failures" -gt 0 ]; then
  exit 1
fi
exit 0
