# RAFAELIA QEMU ⇄ Vectra Integration Contract

## Objetivo

Integrar `rafaelmeloreisnovo/qemu_rafaelia` na Vectra sem versionar binários pesados no repositório Android.

A Vectra deve guardar:

- código Android/Java/Kotlin/C/CMake;
- contratos de execução;
- manifestos de provenance e SHA-256;
- scripts de validação;
- ledger local de execução.

A Vectra não deve guardar:

- `qemu-system-*` pré-compilado;
- firmware `.img`, `.fd`, `.bin`;
- rootfs `.tar`;
- APK/AAB/DEX gerado;
- `.so` pré-compilado que não venha do build NDK atual.

## Separação de responsabilidades

```text
qemu_rafaelia
  = motor fonte QEMU + RAFAELIA core/runtime/process monitor/process health/RMR

Vectras-VM-Android
  = app Android + launcher + QMP/telemetria + VmLaunchLedger + assets verificados em runtime
```

## Pontos de integração já existentes na Vectra

- `app/src/main/java/com/vectras/vm/qemu/QemuBinaryResolver.java`
  - resolve `qemu-system-*` em `${filesDir}/distro/usr/local/bin`, `${filesDir}/distro/usr/bin`, `${filesDir}/usr/bin`, `${filesDir}/bin`, `/usr/local/bin`, `/usr/bin` e `PATH`.
  - já prioriza aliases `-rafacodephi` e `-rafaelia`.
- `app/src/main/java/com/vectras/vm/qemu/QemuArgsBuilder.java`
  - aplica perfil de execução, virtio, iothread e aceleração KVM/TCG.
- `app/src/main/java/com/vectras/vm/qemu/VmLaunchLedger.java`
  - grava contrato append-only com CRC32C por lançamento.
- `tools/qemu_launch.yml`
  - descreve perfis de máquina e telemetria QMP.

## Pontos de integração vindos de qemu_rafaelia

Fonte canônica: `rafaelmeloreisnovo/qemu_rafaelia`.

Módulos de interesse:

- `hw/core/rafaelia-runtime.c`
  - runtime com timer/tick real de QEMU;
  - chama `rafaelia_loop_step()`;
  - mede entropia/coerência;
  - acompanha `RunState`.
- `hw/core/rafaelia-qemu-shell.c`
  - adapter QEMU para `rafaelia-kernel-abi.h`.
- `hw/core/rafaelia-qemu-shell-standalone.c`
  - permite testar a integração sem build completo de QEMU.
- `hw/core/rafaelia-route-table.c`
  - roteamento determinístico por host/arch/KVM/page size.
- `include/hw/core/rafaelia-kernel-abi.h`
  - fronteira estável de memória, pool, instrumentos, rota e RNG.
- `system/process-monitor.c`
  - contadores de loop/kick/runstate/BQL.
- `system/process-health.c`
  - health states e recovery suave.

## Fluxo de execução pretendido

```text
1. Vectra instala ou localiza qemu_rafaelia em runtime.
2. QemuBinaryResolver encontra qemu-system-*-rafaelia ou fallback QEMU padrão.
3. QemuArgsBuilder monta argumentos com perfil/virtio/KVM/TCG.
4. QEMU executa com runtime RAFAELIA habilitado quando o binário suportar.
5. QMP/telemetria alimenta status Android.
6. VmLaunchLedger registra vmId/profile/headless/KVM/envMix/CRC32C.
7. Ciclo posterior usa logs e métricas para ajustar perfil.
```

## Estado atual

Implementado nesta branch:

- `.gitignore` reforçado contra blobs QEMU/firmware/rootfs/APK/SO.
- `tools/ci/ban_repository_binaries.sh` para CI/local.
- `tools/qemu_rafaelia_assets.lock.yml` como manifesto de origem, runtime path e SHA-256 pendente.
- `tools/qemu_launch.yml` atualizado para referenciar runtime paths e não assets versionados.

Ainda pendente:

- Remover do índice Git os blobs já existentes com `git rm --cached`.
- Preencher SHA-256 e URL real dos artefatos externos promovidos.
- Rodar build de `qemu_rafaelia` e publicar artifacts/release.
- Opcional: adicionar job CI que clone `qemu_rafaelia` e rode `Makefile.integration test`.

## Comandos locais para limpar blobs já rastreados

```bash
# ★cat EOF — limpeza local de blobs rastreados
cat > /tmp/vectra_binary_exodus_paths.txt <<'EOF'
app/src/main/assets/roms/QEMU_EFI.img
app/src/main/assets/roms/QEMU_VARS.img
app/src/main/assets/roms/RELEASEX64_OVMF.fd
app/src/main/assets/roms/RELEASEX64_OVMF_VARS.fd
app/src/main/assets/roms/bios-vectras.bin
app/src/main/assets/alpine19/arm64-v8a.tar
app/src/main/assets/alpine19/armeabi-v7a.tar
app/src/main/assets/alpine19/x86.tar
app/src/main/assets/alpine19/x86_64.tar
app/src/main/assets/bootstrap/arm64-v8a.tar
app/src/main/assets/bootstrap/armeabi-v7a.tar
app/src/main/assets/bootstrap/x86.tar
app/src/main/assets/bootstrap/x86_64.tar
3dfx/3dfx-wrappers-2.9.5.iso
3dfx/3dfx-wrappers-3.0.0.iso
3dfx/3dfx-wrappers-3.4.7.iso
3dfx/3dfx-wrappers-3.5.0.iso
EOF

while IFS= read -r p; do
  [ -z "$p" ] && continue
  git rm --cached --ignore-unmatch "$p"
done < /tmp/vectra_binary_exodus_paths.txt

git add .gitignore tools/ci/ban_repository_binaries.sh tools/qemu_rafaelia_assets.lock.yml tools/qemu_launch.yml docs/qemu/RAFAELIA_QEMU_VECTRA_INTEGRATION.md
git commit -m "chore: externalize qemu rafaelia binary artifacts"
```

## Critério de aceite

- `bash tools/ci/ban_repository_binaries.sh` passa.
- `QemuBinaryResolver` continua encontrando binários em runtime.
- APK não depende de blobs versionados em `app/src/main/assets/roms`.
- `VmLaunchLedger` continua registrando cada lançamento.
- `tools/qemu_rafaelia_assets.lock.yml` tem hash real antes de release.

## Retroalimentação

F_ok: QEMU fica como motor fonte/reprodutível, e a Vectra como launcher/ledger/UI.
F_gap: blobs antigos ainda precisam ser removidos do índice e, se desejado, do histórico.
F_next: publicar artifacts de `qemu_rafaelia` e preencher SHA-256 no lockfile.
