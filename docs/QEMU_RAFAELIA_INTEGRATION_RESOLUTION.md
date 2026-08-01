# QEMU RAFAELIA Integration Resolution

## Estado decidido

O `Vectras-VM-Android` deve consumir `qemu_rafaelia` como **fonte externa pinada**, não como vendor interno nem como submodule obrigatório.

```text
qemu_rafaelia = motor/emulador RAFAELIA-QEMU versionado
Vectras-VM-Android = runtime Android que valida, instala, resolve, executa e audita o motor
```

## Resolução aplicada

O manifesto `tools/ci/external_sources.manifest` aponta o contrato `qemu_rafaelia` para:

```text
https://github.com/rafaelmeloreisnovo/qemu_rafaelia
branch: master
pinned_commit_sha: ae94fc60aabb0bbe82abb01038b33ecba790e4ce
```

O SHA foi verificado como o topo atual de `master` em 2026-08-01. Nesse commit:

- `hw/core/connectors/rafaelia-connector-ipc.c` existe;
- `hw/core/Makefile.integration` inclui `connectors/rafaelia-connector-ipc.c` em `CONNECTOR_SRCS`;
- o contrato remoto pode validar branch, commit e checkout pinado antes da compilação Android.

### Histórico append-only da pinagem

| Estado | SHA | Resultado observado |
|---|---|---|
| `SUPERSEDED_INVALID_REMOTE` | `2346c30c2ba77881c2930add83523ea903b173fe` | Não encontrado no remoto durante Android CI; bloqueou antes da compilação. |
| `ACTIVE_PIN` | `ae94fc60aabb0bbe82abb01038b33ecba790e4ce` | Topo verificado de `master`; contém o conector IPC e sua inclusão no build de integração. |

A substituição do pin corrige apenas a proveniência da dependência. Ela **não** promove compilação Android, execução em dispositivo, artifact QEMU nem endereço físico para `PASS`.

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

## Ferramentas adicionadas no consumidor

```bash
tools/qemu/verify_qemu_rafaelia_artifact.sh --artifact qemu-rafaelia-artifact-<sha>.tar.gz
tools/qemu/import_qemu_rafaelia_artifact.sh --artifact qemu-rafaelia-artifact-<sha>.tar.gz
```

- `verify_qemu_rafaelia_artifact.sh` valida `qemu-exec.json`, `BUILD_INFO.json`, `SHA256SUMS.txt`, binários executáveis e consistência de SHA.
- `import_qemu_rafaelia_artifact.sh` só importa depois da verificação e registra `.qemu-rafaelia-import.json`.

## Fronteiras de responsabilidade

| Camada | Repositório responsável | Observação |
|---|---|---|
| Código QEMU e RAFAELIA IPC | `qemu_rafaelia` | Mantém core, hub, IPC e binários QEMU customizados. |
| Build QEMU | `qemu_rafaelia` | Deve gerar artifacts por arquitetura e checksums. |
| Manifesto de fonte externa | `Vectras-VM-Android` | Pinagem do repo/branch/SHA. |
| Verificação/importação de artifact | `Vectras-VM-Android` | Scripts em `tools/qemu/*`. |
| Instalação/descoberta do binário | `Vectras-VM-Android` | `qemu-exec.json`, resolver e preflight. |
| Execução Android | `Vectras-VM-Android` | `StartVM`, `MainStartVM`, `MainService`, `Terminal`. |
| Auditoria de sessão | `Vectras-VM-Android` | Ledger, trace e relatório runtime. |

## Não decisões

- Não copiar o QEMU inteiro para dentro do Vectras.
- Não depender de `master` flutuante sem SHA pinado.
- Não tratar docs antigas como prova de build atual.
- Não declarar release estável sem CI/artifact/logcat/ledger do commit atual.

## Estado de evidência após a correção do pin

```yaml
qemu_source_remote: VERIFIED
qemu_branch_head: VERIFIED
qemu_ipc_source_present: VERIFIED
qemu_ipc_build_inclusion: VERIFIED
external_source_contract_ci: PENDING
android_compile: TOKEN_VAZIO
android_device_runtime: TOKEN_VAZIO
qemu_artifact_for_android: TOKEN_VAZIO
claim_allowed: false
```

## Próximo gate recomendado

Adicionar ao runtime:

```text
runtime_session_report.json
```

Campos mínimos:

```json
{
  "vm_id": "...",
  "qemu_source_repo": "rafaelmeloreisnovo/qemu_rafaelia",
  "qemu_source_commit": "ae94fc60aabb0bbe82abb01038b33ecba790e4ce",
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
