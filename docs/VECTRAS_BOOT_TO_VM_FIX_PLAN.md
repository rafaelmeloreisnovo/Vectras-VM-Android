# VECTRAS BOOT TO VM FIX PLAN

1. Cadeia real: APK → loader.apk → bootstrap tar → proot → distro → /bin/sh → qemu-system-* → QMP/VNC → boot.

2. Bloqueadores encontrados:
- QEMU missing fallback
- proot/rootfs/shell sem diagnóstico
- /dev/shm usando /root
- /data bind agressivo
- loader.apk obrigatório

3. Arquivos alterados
- app/src/main/java/com/vectras/vm/qemu/QemuExecConfig.java
- app/src/main/java/com/vectras/vm/StartVM.java
- app/src/main/java/com/vectras/vm/runtime/VectrasRuntimePreflight.java
- app/src/main/java/com/vectras/vm/core/ProotCommandBuilder.java
- app/src/main/java/com/vectras/vm/main/core/MainStartVM.java
- tools/diagnose_vectras_runtime.sh

4. Como testar
Executar validações de bootstrap, montar APK debug, instalar no aparelho e rodar diagnose.

5. Comandos
- ./tools/ci/verify_bootstrap_contract.sh
- ./tools/gradle_with_jdk21.sh :app:assembleDebug
- adb install -r app/build/outputs/apk/debug/app-debug.apk
- ./tools/diagnose_vectras_runtime.sh
