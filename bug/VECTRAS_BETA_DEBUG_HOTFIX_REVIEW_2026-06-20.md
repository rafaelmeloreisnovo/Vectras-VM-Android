# Vectras VM Android — Beta Debug Hotfix Review

Data: 2026-06-20
Branch: codex/runtime-proof-foundation
PR: #1023

## Veredito de betatester/debugger

A cadeia do APK e do run de VM existe e é coerente:

```text
Android launcher
→ VectrasApp
→ SplashActivity
→ MainActivity
→ VmsFragment
→ VmsHomeAdapter
→ StartVM.env()
→ MainStartVM.startNow()
→ MainService
→ Terminal.executeShellCommand2()
→ PRoot shell
→ QEMU
```

O problema não é ausência de corpo. O problema está em risco de travamento, corrida, processo zumbi, laudo pesado no caminho de start e setup minimal.

## Achados P0

### P0-1 — Runtime proof pesado no caminho do serviço

O PR #1023 adicionou `RuntimeSessionReport` e `ExpandedRuntimePreflight`. Isso é correto como fundação pericial, mas precisa de hardening:

- não fazer hash completo de binários/firmwares grandes no caminho de start;
- executar geração do laudo em executor de baixa prioridade;
- manter o start da VM livre de I/O pesado.

Hotfix correto:

```text
MainService.startCommand/onCreate
→ dispatch QEMU imediatamente
→ executor single low priority gera runtime_session_report.json
```

E no preflight:

```text
hash somente até orçamento fixo, ex.: primeiro 1 MiB
registrar size total
registrar se hash é sample ou full
```

### P0-2 — SetupWizard2Activity é fallback minimal

`SetupWizard2Activity` apenas abre `MainActivity` e finaliza. Isso preserva compilação, mas não protege o usuário quando QEMU/rootfs/proot estão ausentes.

Hotfix correto:

```text
SetupWizard2Activity
→ rodar SetupFeatureCore.runSetupPostCheck
→ rodar runProotSelfCheck
→ rodar VectrasRuntimePreflight
→ se falhar: tela de reparar/reinstalar
→ se OK: MainActivity
```

### P0-3 — QEMU ainda entra como string no shell

O PRoot usa `ProcessBuilder`, mas o QEMU ainda é escrito no stdin do shell. Isso funciona, mas é fonte de bugs de quoting, argumentos colados e comportamento inesperado.

Hotfix correto:

```text
QemuLaunchContract
→ manter command_string para compat
→ criar argv real
→ executar por wrapper seguro dentro do rootfs
→ registrar argv_sha256
```

### P0-4 — Prova de processo ainda aponta para PRoot, não necessariamente QEMU

`Terminal` registra o processo lançado pelo `ProcessBuilder`, que é PRoot/shell. O QEMU real é filho interno.

Hotfix correto:

```text
RuntimeSessionReport
→ pid_proot
→ pid_qemu detectado via /proc quando possível
→ qmp_socket_ready
→ vnc_socket_ready
→ first_runtime_signal_ms
```

## Achados P1

### P1-1 — killall global é risco para multi-VM

`MainService.cleanup()` chama kill global de QEMU. Para uma VM é aceitável; para multi-VM é risco.

Hotfix correto:

```text
killByVmId(vmId)
→ qmp system_powerdown
→ supervisor.stopGracefully
→ fallback destroyForcibly somente no processo da VM
```

### P1-2 — Assets são instalados em background

`SplashActivity` chama `FileInstaller.installFiles(..., true)` em executor. Isso pode concorrer com o usuário tocando em iniciar VM.

Hotfix correto:

```text
SplashActivity
→ registrar asset_install_state
→ MainStartVM bloqueia run se asset install em andamento e asset requerido ausente
```

### P1-3 — Log flood já é mitigado, mas precisa telemetria final por sessão

`Terminal.streamLog` já tem ring buffer, token bucket, degraded marker e audit ledger. Falta exportar esse estado no runtime_session_report.

Hotfix correto:

```text
runtime_session_report.json
→ log_lines_dropped
→ log_bytes_seen
→ degraded_reason
→ recovered=true/false
```

## Achados P2

### P2-1 — Primeiro frame e latência visual ainda não são medidos

VNC/SPICE/X11 existem, mas falta:

```text
first_frame_ms
fps_avg
jank_count
input_latency_ms
frontend_attach_ms
```

### P2-2 — Setup/Run precisa de modo diagnóstico em tela

Criar tela simples:

```text
Runtime Diagnostics
→ ABI
→ QEMU path
→ rootfs
→ firmware
→ disk
→ qmp/vnc/x11
→ native/fallback
→ último runtime_session_report.json
```

## Ordem correta de hotfix

1. Mover runtime proof para executor de baixa prioridade.
2. Limitar hashing de assets/binários.
3. Criar gate real no SetupWizard2Activity.
4. Registrar pid_proot e pid_qemu separado.
5. Trocar killall global por killByVmId.
6. Anexar flood/degraded counters ao relatório.
7. Adicionar first-frame/frontend telemetry.
8. Refatorar QEMU string para argv real.

## Nota de execução

Foram tentados patches diretos em `MainService.java` e `ExpandedRuntimePreflight.java`, mas a ferramenta bloqueou o envio do update. Este arquivo registra o plano técnico auditável para continuar a implementação sem perder a cadeia de custódia.
