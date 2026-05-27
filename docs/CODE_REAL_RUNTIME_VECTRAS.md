# CODE_REAL Runtime Audit — Vectras VM Android

> Fonte de verdade: código ativo do repositório (não README/PROJECT_STATE).

## 1) Cadeia real de execução

Fluxo observado no código:

1. **UI** chama construção de comando QEMU via `StartVM.env(...)`.
2. `MainStartVM.startNow(...)` valida preflight, prepara modo UI/headless, injeta wrappers e grava ledger.
3. `MainService` recebe/envia comando final para execução em background.
4. `Terminal.executeShellCommand2(...)` abre `proot` via `ProcessBuilder`.
5. `ProotCommandBuilder` monta contrato de bind/env/workdir/shell.
6. Dentro do rootfs, o comando final invoca `qemu-system-*` resolvido por `QemuExecConfig` + `QemuBinaryResolver` + `QemuArgsBuilder`.

Resumo linear solicitado:

`UI → StartVM.env → MainStartVM.startNow → MainService → Terminal.executeShellCommand2 → ProotCommandBuilder → proot → qemu-system-*`

---

## 2) Fases com contrato real (entrada/saída/estado/erro/fallback/log)

## Fase A — UI/StartVM
- **Arquivo**: `app/src/main/java/com/vectras/vm/StartVM.java`
- **Função**: `env(Activity, String extras, String img, boolean isQuickRun)`
- **Entrada**: activity, extras CLI, imagem principal, flag quick run.
- **Saída**: `String command` (QEMU completo); atualiza `lastRuntimeContract`, `lastStartError`, `lastMountState`, `lastResolvedProfile`, `lastKvmEnabled`.
- **Estado global usado**: `StartVM.*` estáticos; `MainSettingsManager`; `Config` sockets (QMP/VNC).
- **Erro possível**:
  - comando vazio (gera `lastStartError=empty_command`);
  - binário QEMU ausente (retorna nome primário mesmo sem path executável);
  - UI desconhecida cai em `-display none`.
- **Fallback**:
  - em comando vazio: usa `QemuExecConfig.resolveBinary(...)` isolado;
  - em binário não resolvido: fallback para nome primário (`qemu-system-x86_64`, etc.).
- **Log existente**:
  - `Log.i` debug profile/KVM;
  - `Log.w` para UI desconhecida.
- **Log que falta**:
  - log estruturado da string final com campos seguros/sanitizados;
  - evento explícito quando fallback de binário é usado sem arquivo executável.

## Fase B — Orquestração de start
- **Arquivo**: `app/src/main/java/com/vectras/vm/main/core/MainStartVM.java`
- **Função**: `startNow(Context, vmName, env, vmID, thumbnailFile)`
- **Entrada**: id/nome/command/env vindos da UI.
- **Saída**: inicia `MainService`/`startCommand`, ativa `LaunchPoller`, abre UI (VNC/X11/headless).
- **Estado global usado**: `lastVMID`, `pending*`, `isStopNow`, poller singleton, reserva SPICE static.
- **Erro possível**:
  - preflight falha;
  - comando inseguro (`VMManager.isthiscommandsafe`);
  - diretório cache não criado;
  - porta VNC em uso;
  - reserva SPICE falha.
- **Fallback**:
  - aborta cedo com diálogo e ledger;
  - fallback mttcg `thread=single` em cenário ARM específico.
- **Log existente**:
  - `VectrasStatus.logError` preflight;
  - `VmFlowTracker.mark` estados;
  - `VmLaunchLedger.append` com comando final.
- **Log que falta**:
  - motivo detalhado de abort para todos ramos (alguns só UI/dialog);
  - métrica de latência por fase (preflight→service→socket up).

## Fase C — Serviço
- **Arquivo**: `app/src/main/java/com/vectras/vm/MainService.java`
- **Função**: `onCreate()`, `startCommand(...)`
- **Entrada**: `MainService.env` já montado.
- **Saída**: chama `Terminal.executeShellCommand2(...)`.
- **Estado global usado**: `MainService.env`, `service`, `activityContext`.
- **Erro possível**: `env == null`.
- **Fallback**: usa `ApplicationContext` quando `activityContext` é nulo.
- **Log existente**: `Log.e(TAG, "env is null")`.
- **Log que falta**: correlação `vmId + command hash` no service boundary.

## Fase D — Execução terminal/proot
- **Arquivo**: `app/src/main/java/com/vectras/vterm/Terminal.java`
- **Função**: `executeShellCommand2(String userCommand, ...)`
- **Entrada**: comando shell único (string), contexto.
- **Saída**: processo `proot` iniciado; streaming de logs; registro/cleanup de processo VM.
- **Estado global usado**: `qemuProcess`, `DISPLAY`, `MainStartVM.lastVMID`, registries de budget.
- **Erro possível**:
  - limite de processos;
  - slot não adquirido;
  - `processBuilder.start()` IO exception.
- **Fallback**:
  - bloqueio de start e mensagem ao usuário;
  - rotação de transient VM id em falha bootstrap.
- **Log existente**:
  - logs debug VTERM;
  - `AuditLedger`/`VectrasStatus` em partes do fluxo.
- **Log que falta**:
  - comando efetivo `argv` do proot (hoje só `userCommand` textual).

## Fase E — Contrato PRoot builder
- **Arquivo**: `app/src/main/java/com/vectras/vm/core/ProotCommandBuilder.java`
- **Função**: `buildCommand()`, `applyEnvironment(...)`
- **Entrada**: rootfsPath/workDir/opções de bind/env.
- **Saída**: `List<String>` argv de proot + env map.
- **Estado global usado**: `TermuxService.PREFIX_PATH`.
- **Erro possível**: binário `proot` não existir no prefix; binds perigosos habilitados por default.
- **Fallback**: toggles de bind por flags (mas default é agressivo).
- **Log existente**: inexistente no builder.
- **Log que falta**: dump estruturado de binds/env aplicados por sessão.

---

## 3) Contrato PRoot real

- **Caminho proot**: `TermuxService.PREFIX_PATH + "/bin/proot"`.
- **rootfs**: `${filesDir}/distro` (em `Terminal`).
- **workdir**: `"/root"`.
- **shell**: default `"/bin/sh --login"`.
- **binds default**:
  - `/dev`, `/proc`, `/sys`, `/sdcard`, `/storage`, `/data`;
  - `${rootfs}/root:/dev/shm`;
  - `${filesDir}/usr/tmp:/tmp`.
- **env vars**:
  - `PROOT_TMP_DIR`, `HOME`, `USER`, `TERM`, `TMPDIR`, `SHELL`, `DISPLAY`, `PULSE_SERVER`, `XDG_RUNTIME_DIR`, `PATH`, `SDL_VIDEODRIVER`.
- **riscos /data bind**:
  - `/data` completo exposto no guest namespace aumenta superfície para leitura/escrita acidental em diretórios sensíveis de app/terceiros.
- **riscos /dev/shm atual**:
  - mapeado para `${rootfs}/root`, não tmpfs dedicado; risco de colisão com HOME, permissões e lixo persistente simulando shm.

---

## 4) Contrato QEMU real

- **Resolução de binário**:
  - `QemuExecConfig.resolveBinary` tenta `qemu-exec.json` por arquitetura;
  - se ausente/inválido, usa `QemuBinaryResolver.resolveForArch`.
- **Paths pesquisados**:
  - `${filesDir}/distro/usr/local/bin`, `${filesDir}/distro/usr/bin`, `${filesDir}/usr/bin`, `${filesDir}/bin`, `/usr/local/bin`, `/usr/bin`, e entradas de `PATH`.
- **Aliases rafacodephi/rafaelia**:
  - busca `-rafacodephi` e `-rafaelia` por arch, além dos binários base.
- **Arquiteturas suportadas**: `X86_64`, `I386`, `ARM64`, `PPC` (normalize com default `X86_64`).
- **Erro quando binário não é encontrado**:
  - warning `event=qemu_binary_resolution_failed`;
  - retorno final ainda é nome primário (sem hard fail).
- **Necessidade de bloquear start cedo**:
  - sim: estado atual permite seguir para runtime e falhar tardiamente no `proot/shell`, piorando UX e diagnóstico.

---

## 5) Contrato UI real

- **VNC**:
  - `StartVM.env` aplica `-vnc`; externo usa host/porta e opcional `password=on`; interno usa socket unix.
  - `LaunchPoller` abre `MainVNCActivity` quando QMP/socket indica boot.
- **SPICE**:
  - usa placeholder `__VECTRAS_SPICE_PORT__` no comando;
  - `MainStartVM.reserveSpicePortIfNeeded` reserva porta randômica local e substitui string.
- **X11**:
  - `-display sdl` ou `gtk,gl=on`; monitor `stdio`/`vc` conforme setting;
  - pode exigir Termux-X11 externo; há cheque de pacote.
- **Headless**:
  - `VmLaunchMode.determine(..., AppConfig.engineHeadlessMode, env)`;
  - sem progress UI, poller fecha diálogo e não lança cliente gráfico.
- **QMP socket**:
  - sempre `-qmp unix:${Config.getLocalQMPSocketPath()},server,nowait` (inclusive quickrun adicional).
- **Launch poller**:
  - detecta stop/error/socket; então decide anexar VNC, X11 ou apenas concluir headless.

---

## 6) Contrato nativo real (melhorado com mitigação tática/profissional)

- **NativeFastPath**:
  - carrega `libvectra_core_accel`; se falha, ativa fallback Java e mantém API funcional.
  - **mitigação recomendada**: classificar fallback em níveis (`EXPECTED_DEVICE_LIMIT`, `JNI_MISSING`, `KERNEL_CONTRACT_MISMATCH`) para separar limitações de device vs bug real.
- **`vectra_core_accel.c`**:
  - implementa JNI para cópia/checksum/arena/route/verify/audit + capacidades da unified kernel.
  - **mitigação recomendada**: normalizar código de erro C/JNI (tabela única) e mapear para erro funcional no Java (`recoverable`, `degraded`, `fatal`).
- **JNI symbols reais**:
  - `Java_com_vectras_vm_core_NativeFastPath_nativeInit`, `nativeReadHardwareContract`, `nativeFeatureMask`, `nativePointerBits`, `nativePageBytes`, `nativeCore*`, etc.
  - **mitigação recomendada**: teste de contrato de símbolos no startup (smoke JNI) com fail-fast controlado e mensagem amigável antes do start VM.
- **Fallback Java**:
  - `NativeFastPath` retorna implementações determinísticas em Java quando native indisponível/retorna zero.
  - **mitigação recomendada**: manter equivalência determinística com suíte de comparação JNI vs Java (N=200, seed fixa) para evitar drift algorítmico.
- **Telemetry**:
  - contadores `TELEMETRY_*` (copy/xor/crc/route/audit/native hits/fallback hits) com leitura raw estruturada.
  - **mitigação recomendada**: incluir `vmId`, `arch`, `featureMask`, `pageBytes`, `pointerBits`, `fallbackReason`, `errorClass` em relatório runtime por sessão.
- **Feature mask**:
  - contrato público `FEATURE_NEON/AES/CRC32/POPCNT/SSE42/AVX2/SIMD`.
  - **mitigação recomendada**: strategy pattern por feature (ex.: CRC32 hw vs software), com guardas por bit e testes A/B de consistência.
- **Page size / pointer bits**:
  - vêm de capability nativa (`nativePageBytes`, `nativePointerBits`), fallback 4096/32 em C quando kernel state indisponível.
  - **mitigação recomendada**: validar compatibilidade mínima no boot (`pointerBits>=32`, `pageBytes` suportado) e registrar downgrade explícito no ledger.

### Plano tático (2 ciclos) para o contrato nativo

- **Ciclo 1 — Contenção de risco (rápido)**
  1. Implementar classificação de erro/fallback e log estruturado obrigatório.
  2. Adicionar smoke-test JNI no startup (sem bloquear app inteiro; bloqueia só fast-path quando necessário).
  3. Exportar snapshot de telemetria em arquivo por sessão VM.

- **Ciclo 2 — Robustez de produção**
  1. Criar suíte de paridade JNI↔Java com seeds fixas + casos limítrofes.
  2. Implantar SLO de confiabilidade do fast-path (ex.: taxa de fallback < X% por arch).
  3. Automatizar alarme CI quando contrato de capability mudar sem atualização de testes/documentação.

---

## 7) Bugs/gaps prioritários (com estratégia de correção e mitigação)

1. **QEMU missing fallback ruim**
   - **falha atual**: sem fail-fast quando binário inexistente executável.
   - **risco**: erro tardio no runtime, suporte operacional caro e UX ruim.
   - **mitigação tática**:
     - Gate em preflight: bloquear start se `QemuBinaryResolver` não encontrar executável válido.
     - Retornar diagnóstico com `checkedPaths`, arch e ação sugerida.
   - **boa prática**: validação de dependência crítica *antes* de side effects (foreground service/proot start).

2. **String shell command risk**
   - **falha atual**: `userCommand` único em shell dentro do proot.
   - **risco**: escaping inconsistente e injeção acidental em concatenações futuras.
   - **mitigação tática**:
     - Introduzir pipeline `argv` estruturado para QEMU (sem composição textual quando possível).
     - Canonicalizar/escapar apenas em camada única, com testes de comandos maliciosos.
   - **boa prática**: princípio “structured command over shell string”.

3. **`/data` bind agressivo**
   - **falha atual**: `/data` habilitado por padrão.
   - **risco**: exposição excessiva e possibilidade de escrita indevida no namespace host.
   - **mitigação tática**:
     - Trocar default para allowlist mínima (`/dev`,`/proc`,`/sys`,`/tmp`) e bind opcional de `/data` por feature flag explícita.
     - Quando necessário, preferir bind read-only e paths específicos.
   - **boa prática**: least privilege e defense-in-depth.

4. **`/dev/shm` usando root**
   - **falha atual**: `/dev/shm` apontando para `${rootfs}/root`.
   - **risco**: semântica IPC/shm incorreta, lixo persistente e conflito com HOME.
   - **mitigação tática**:
     - Criar diretório dedicado (`${rootfs}/dev/shm`) com permissão `1777` na preparação do rootfs.
     - Adicionar verificação de saúde do shm no preflight.
   - **boa prática**: separar dados efêmeros IPC de diretórios de usuário.

5. **Docs antigas desatualizadas**
   - **falha atual**: documentação não reflete runtime real.
   - **risco**: decisões erradas em operação/manutenção.
   - **mitigação tática**:
     - Estabelecer processo “code-real first”: gerar artefato de runtime em cada release.
     - Tornar divergência doc↔código um check de PR.
   - **boa prática**: documentação viva com owner definido.

6. **Falta relatório runtime gerado pelo app**
   - **falha atual**: ledger/tracker parciais sem visão consolidada por sessão.
   - **risco**: baixa rastreabilidade de incidentes e MTTR alto.
   - **mitigação tática**:
     - Criar `runtime_session_report.json` por VM contendo: command hash, UI mode, QEMU binary path, binds, feature mask, fallback reason, tempos por fase, códigos de erro.
     - Exportar relatório sob demanda no app para suporte técnico.
   - **boa prática**: observabilidade orientada a sessão com campos estáveis.

### Estratégia profissional de execução (priorização tática)

- **Prioridade P0 (segurança/estabilidade imediata)**
  1. Fail-fast de binário QEMU no preflight.
  2. Hardening de bind `/data` e correção de `/dev/shm`.

- **Prioridade P1 (confiabilidade operacional)**
  1. Migrar para execução estruturada de comandos (`argv`) onde viável.
  2. Implantar relatório consolidado de sessão runtime.

- **Prioridade P2 (governança e prevenção de regressão)**
  1. Testes automatizados de contrato (QEMU resolver + proot binds + JNI parity).
  2. Checklist de release exigindo atualização de CODE_REAL quando fluxo mudar.

---

## 8) Matriz de divergência CODE_REAL

| ID | Arquivo | Função | Realidade do código | Documentação atual | Divergência | Risco | Correção |
|---|---|---|---|---|---|---|---|
| CR-01 | StartVM.java | `env` | Continua com fallback para nome de binário sem validação final de existência | Assume start direto estável | Alta | Falha tardia difícil de diagnosticar | Bloquear start no preflight se binário ausente |
| CR-02 | QemuBinaryResolver.java | `resolveForArch` | Faz busca extensa com aliases rafacodephi/rafaelia | Geralmente docs citam só binário padrão | Média | Debug e empacotamento inconsistentes | Documentar ordem de busca e expor no UI debug |
| CR-03 | MainStartVM.java | `startNow` | Preflight + ledger + poller + reserve SPICE | Fluxo antigo simplificado | Alta | Operação incorreta de UI/start | Atualizar docs de fluxo completo |
| CR-04 | MainService.java | `onCreate/startCommand` | Execução real passa por service foreground | Alguns textos antigos sugerem execução direta | Média | Interpretação errada de lifecycle | Registrar boundary service no relatório runtime |
| CR-05 | Terminal.java | `executeShellCommand2` | Executa proot com command string | Docs podem omitir risco de shell string | Alta | Injection/quoting bug latent | Migrar para argv estruturado para QEMU entrypoint |
| CR-06 | ProotCommandBuilder.java | `buildDefaultBinds` | `/data`, `/sdcard`, `/storage` ativos por default | Contrato mínimo não explicitado | Alta | Exposição de dados e side effects | Default hardened + allowlist binds |
| CR-07 | ProotCommandBuilder.java | `devShmBind` | `/dev/shm` mapeado para `${rootfs}/root` | Contrato shm não descrito | Alta | IPC/semântica shm incorreta | Criar diretório shm dedicado e permissões 1777 |
| CR-08 | NativeFastPath.java + C | init/telemetry | Fallback Java robusto com telemetria | Docs antigas focam só JNI feliz | Média | Perda de observabilidade | Publicar contrato de fallback + counters |
| CR-09 | AndroidManifest.xml | service/query | Dependência explícita Termux + Termux-X11 | Docs antigas podem tratar opcionalidade diferente | Média | Setup quebrado em ambientes sem pacote | Pré-check de dependências com erro guiado |
| CR-10 | tools/verify_bootstrap_assets.py + verify_bootstrap_contract.sh | contrato bootstrap | Contrato oficial TAR + loader.apk estrito | Docs legadas podem falar em ZIP/JNI apenas | Alta | Build/runtime bootstrap inconsistente | Tornar contrato único visível no app e CI summary |

---

## 9) Escopo desta entrega

- **Não** atualiza README ainda (conforme solicitado).
- Entrega primeiro a auditoria **CODE_REAL** em `docs/CODE_REAL_RUNTIME_VECTRAS.md`.
- Próximo passo: patch técnico incremental (fail-fast QEMU, hardening PRoot binds, relatório runtime consolidado).
