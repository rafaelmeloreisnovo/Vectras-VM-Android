#!/usr/bin/env bash
set +e
PKG="com.rafacodephi.app"
OUT="reports/VECTRAS_RUNTIME_DIAGNOSIS.md"
mkdir -p reports
: > "$OUT"
status(){ printf "- [%s] **%s** — %s\n" "$1" "$2" "$3" >> "$OUT"; }
run(){ adb "$@" 2>/tmp/vectras_diag_err; return $?; }
app_files=$(adb shell run-as "$PKG" sh -c 'pwd' 2>/dev/null)
files_dir="${app_files:-/data/data/$PKG}/files"
echo "# VECTRAS Runtime Diagnosis" >> "$OUT"
echo "Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$OUT"
echo >> "$OUT"
run shell pm list packages | grep -q "$PKG"; [ $? -eq 0 ] && status PASS package_installed "$PKG" || status BLOCKER package_installed "$PKG not installed"
abi=$(adb shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r'); [ -n "$abi" ] && status PASS device_abi "$abi" || status WARN device_abi "unavailable"
sdk=$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r'); [ -n "$sdk" ] && status PASS android_sdk "$sdk" || status WARN android_sdk "unavailable"
pg=$(adb shell getconf PAGESIZE 2>/dev/null | tr -d '\r'); [ -n "$pg" ] && status PASS page_size "$pg" || status WARN page_size "unavailable"
natlib=$(adb shell dumpsys package "$PKG" | sed -n 's/.*nativeLibraryDir=//p' | head -n1 | tr -d '\r'); [ -n "$natlib" ] && status PASS nativeLibraryDir "$natlib" || status WARN nativeLibraryDir "not found"
status PASS app_files_dir "$files_dir"
check_exists(){ p="$1"; id="$2"; lvl="${3:-FAIL}"; adb shell run-as "$PKG" sh -c "[ -e '$p' ]" >/dev/null 2>&1; [ $? -eq 0 ] && status PASS "$id" "$p exists" || status "$lvl" "$id" "$p missing"; }
check_exec(){ p="$1"; id="$2"; lvl="${3:-BLOCKER}"; adb shell run-as "$PKG" sh -c "[ -x '$p' ]" >/dev/null 2>&1; [ $? -eq 0 ] && status PASS "$id" "$p executable" || status "$lvl" "$id" "$p not executable"; }
check_run(){ cmd="$1"; id="$2"; lvl="${3:-BLOCKER}"; adb shell run-as "$PKG" sh -c "$cmd" >/tmp/vectras_diag_cmd 2>&1; [ $? -eq 0 ] && status PASS "$id" "$(head -n1 /tmp/vectras_diag_cmd)" || status "$lvl" "$id" "failed: $(head -n1 /tmp/vectras_diag_cmd)"; }
check_exists "$files_dir/usr" files_usr BLOCKER
check_exists "$files_dir/usr/bin/proot" proot_exists BLOCKER
check_exec "$files_dir/usr/bin/proot" proot_exec BLOCKER
check_run "'$files_dir/usr/bin/proot' --version" proot_version BLOCKER
check_exists "$files_dir/distro" distro_exists BLOCKER
check_exists "$files_dir/distro/bin/sh" shell_exists BLOCKER
check_exec "$files_dir/distro/bin/sh" shell_exec BLOCKER
check_run "'$files_dir/distro/bin/sh' -c 'echo VECTRAS_SHELL_OK'" shell_run BLOCKER
for b in qemu-system-x86_64 qemu-system-i386 qemu-system-aarch64 qemu-img qemu-system-x86_64-rafaelia qemu-system-i386-rafaelia qemu-system-aarch64-rafaelia; do
  lvl=FAIL; [[ "$b" == qemu-system-* ]] && lvl=BLOCKER
  check_exists "$files_dir/distro/usr/bin/$b" "$b" "$lvl"
done
check_exists "$files_dir/usr/tmp" usr_tmp FAIL
check_exists "$files_dir/distro/root" root_home FAIL
check_exists "$files_dir/data/Vectras" roms_bios_expected WARN
check_exists "$files_dir/data/Vectras/roms" roms_dir WARN
check_exists "$files_dir/qemu-exec.json" qemu_exec_json WARN
if adb shell run-as "$PKG" sh -c "[ -f '$files_dir/qemu-exec.json' ]" >/dev/null 2>&1; then
  target=$(adb shell run-as "$PKG" sh -c "sed -n 's/.*\"default\"[ ]*:[ ]*\"\([^\"]*\)\".*/\1/p' '$files_dir/qemu-exec.json' | head -n1" | tr -d '\r')
  [ -n "$target" ] && adb shell run-as "$PKG" sh -c "[ -x '$target' ]" >/dev/null 2>&1 && status PASS qemu_exec_target "$target executable" || status WARN qemu_exec_target "missing or non executable target"
fi
printf "\n## Filtered logcat\n\n```\n" >> "$OUT"
adb logcat -d | egrep "StartVM|QemuExecConfig|QemuBinaryResolver|Vterm|MainStartVM|MainService|proot|qemu|linker|Exception|RuntimeException" | tail -n 200 >> "$OUT"
printf "\n```\n" >> "$OUT"
echo "Wrote $OUT"
