# VECTRAS Boot to VM Fix Plan

## 1) Cadeia real
APK → loader.apk → bootstrap tar → proot → distro → /bin/sh → qemu-system-* → QMP/VNC → boot

## 2) Bloqueadores encontrados
- QEMU missing fallback (fail tardio)
- proot/rootfs/shell sem diagnóstico hard-fail
- /dev/shm usando /root
- /data bind agressivo por padrão
- loader.apk obrigatório para bootstrap

## 3) Arquivos alterados
- app/src/main/java/com/vectras/vm/qemu/QemuExecConfig.java
- app/src/main/java/com/vectras/vm/StartVM.java
- app/src/main/java/com/vectras/vm/runtime/VectrasRuntimePreflight.java
- app/src/main/java/com/vectras/vm/core/ProotCommandBuilder.java
- tools/diagnose_vectras_runtime.sh

## 4) Como testar
1. Rodar verificação de bootstrap/loader.
2. Buildar APK debug e instalar.
3. Rodar diagnóstico adb para validar runtime in-device.
4. Tentar start VM: se faltar QEMU/proot/rootfs/shell deve abortar com erro claro.

## 5) Comandos
- `./tools/ci/verify_bootstrap_contract.sh`
- `./tools/gradle_with_jdk21.sh :app:assembleDebug`
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- `./tools/diagnose_vectras_runtime.sh`
