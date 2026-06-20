# QEMU RAFAELIA Integration Resolution

## Estado decidido

O `Vectras-VM-Android` deve consumir `qemu_rafaelia` como **fonte externa pinada**, não como vendor interno nem como submodule obrigatório.

```text
qemu_rafaelia = motor/emulador RAFAELIA-QEMU versionado
Vectras-VM-Android = runtime Android que valida, instala, resolve, executa e audita o motor
```

## Resolução aplicada

O manifesto `tools/ci/external_sources.manifest` passa a apontar o contrato `qemu_rafaelia` para:

```text
https://github.com/rafaelmeloreisnovo/qemu_rafaelia
branch: master
pinned_commit_sha: 2346c30c2ba77881c2930add83523ea903b173fe
```

Motivo técnico: o commit pinado contém a correção `fix: link IPC connector in RAFAELIA integration build`, incluindo `connectors/rafaelia-connector-ipc.c` no build de integração RAFAELIA/QEMU.

## Invariante de integração

```text
fonte pinada + artifact verificável + qemu-exec.json + resolver determinístico + preflight bloqueante + ledger runtime
```

Em termos operacionais:

1. `qemu_rafaelia` compila fora do app Android.
2. O build do QEMU gera binários/artifacts com SHA256 e `BUILD_INFO.json`.
3. O Vectras importa/instala esses artifacts em caminho controlado.
4. `QemuExecConfig` e `QemuBinaryResolver` localizam o binário real.
5. `VectrasRuntimePreflight` bloqueia o start se QEMU/PRoot/rootfs/shell estiverem ausentes.
6. O runtime grava evidência de sessão: VM id, QEMU path, SHA, arch, UI mode, QMP/VNC, status e erro.

## Fronteiras de responsabilidade

| Camada | Repositório responsável | Observação |
|---|---|---|
| Código QEMU e RAFAELIA IPC | `qemu_rafaelia` | Mantém core, hub, IPC e binários QEMU customizados. |
| Build QEMU | `qemu_rafaelia` | Deve gerar artifacts por arquitetura e checksums. |
| Manifesto de fonte externa | `Vectras-VM-Android` | Pinagem do repo/branch/SHA. |
| Instalação/descoberta do binário | `Vectras-VM-Android` | `qemu-exec.json`, resolver e preflight. |
| Execução Android | `Vectras-VM-Android` | `StartVM`, `MainStartVM`, `MainService`, `Terminal`. |
| Auditoria de sessão | `Vectras-VM-Android` | Ledger, trace e relatório runtime. |

## Não decisões

- Não copiar o QEMU inteiro para dentro do Vectras.
- Não depender de `master` flutuante sem SHA pinado.
- Não tratar docs antigas como prova de build atual.
- Não declarar release estável sem CI/artifact/logcat/ledger do commit atual.

## Próximo gate recomendado

Adicionar ao Vectras:

```text
tools/qemu/import_qemu_rafaelia_artifact.sh
tools/qemu/verify_qemu_artifact.sh
docs/QEMU_RAFAELIA_ARTIFACT_CONSUMPTION.md
```

Adicionar ao runtime:

```text
runtime_session_report.json
```

Campos mínimos:

```json
{
  "vm_id": "...",
  "qemu_source_repo": "rafaelmeloreisnovo/qemu_rafaelia",
  "qemu_source_commit": "2346c30c2ba77881c2930add83523ea903b173fe",
  "qemu_binary": "...",
  "qemu_sha256": "...",
  "arch": "...",
  "ui": "VNC/X11/SPICE/headless",
  "preflight": "PASS/FAIL",
  "qmp_socket": "...",
  "started_at": "...",
  "last_error": ""
}
```

## Fórmula compacta

```text
QEMU compila fora; Vectras executa dentro; o contrato prova a ponte.
```
