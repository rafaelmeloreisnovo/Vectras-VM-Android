#!/usr/bin/env bash
set +e
REPORT="reports/VECTRAS_RUNTIME_DIAGNOSIS.md"; mkdir -p reports
PKG="com.rafacodephi.app"
status(){ printf '| %s | %s | %s |\n' "$1" "$2" "$3" >> "$REPORT"; }
run(){ out=$(eval "$2" 2>&1); rc=$?; lvl=$3; [[ $rc -eq 0 ]] && lvl=PASS; status "$1" "$lvl" "

txt
$out
"; }
echo "# VECTRAS Runtime Diagnosis" > "$REPORT"
echo "Generated: $(date -u)" >> "$REPORT"
echo '| Item | Status | Details |' >> "$REPORT"; echo '|---|---|---|' >> "$REPORT"
run "package installed" "adb shell pm path $PKG" BLOCKER
run "device abi" "adb shell getprop ro.product.cpu.abi" WARN
run "android sdk" "adb shell getprop ro.build.version.sdk" WARN
run "page size" "adb shell getconf PAGESIZE" WARN
APPDIR=$(adb shell run-as $PKG sh -lc 'pwd' 2>/dev/null)
run "nativeLibraryDir" "adb shell dumpsys package $PKG | sed -n '/nativeLibraryDir/p'" WARN
run "app files dir" "adb shell run-as $PKG sh -lc 'echo \$PWD/files'" WARN
for p in "files/usr" "files/usr/bin/proot" "files/distro" "files/distro/bin/sh" "files/distro/usr/bin/qemu-system-x86_64" "files/distro/usr/bin/qemu-system-i386" "files/distro/usr/bin/qemu-img" "files/usr/tmp" "files/distro/root" "files/qemu-exec.json"; do
  run "$p exists" "adb shell run-as $PKG sh -lc 'test -e $p'" FAIL
done
run "proot executable" "adb shell run-as $PKG sh -lc 'test -x files/usr/bin/proot'" BLOCKER
run "proot --version" "adb shell run-as $PKG sh -lc 'files/usr/bin/proot --version'" FAIL
run "shell executable" "adb shell run-as $PKG sh -lc 'test -x files/distro/bin/sh'" BLOCKER
run "shell echo" "adb shell run-as $PKG sh -lc 'files/distro/bin/sh -c \"echo VECTRAS_SHELL_OK\"'" FAIL
run "qemu-system-aarch64 exists" "adb shell run-as $PKG sh -lc 'test -e files/distro/usr/bin/qemu-system-aarch64'" WARN
run "qemu aliases" "adb shell run-as $PKG sh -lc 'ls files/distro/usr/bin/qemu-system-*-rafaelia files/distro/usr/bin/qemu-system-*-rafacodephi 2>/dev/null'" WARN
run "ROM/BIOS" "adb shell run-as $PKG sh -lc 'ls files/data/Vectras 2>/dev/null'" WARN
run "qemu-exec.json content" "adb shell run-as $PKG sh -lc 'cat files/qemu-exec.json 2>/dev/null'" WARN
run "logcat filtered" "adb logcat -d | grep -E 'StartVM|QemuExecConfig|QemuBinaryResolver|Vterm|MainStartVM|MainService|proot|qemu|linker|Exception|RuntimeException' | tail -n 200" WARN
echo "Done: $REPORT"
