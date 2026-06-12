<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: complete -->

# Changelog
All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added
- Mutação seletiva de bits no cache TCG (`engine/rmr/src/rmr_tcg_cache.c`): a reinserção de bloco grava apenas o delta XOR entre o byte residente e o candidato, preservando bits iguais e o físico esparso do ISOraf; novas métricas `delta_bits_flipped`/`delta_bits_preserved` com acessores `RmR_TCGCache_DeltaBitsFlipped` e `RmR_TCGCache_DeltaPreservedPct`.
- Selftest host `demo_cli/src/rmr_tcg_cache_selftest.c` (registrado em `Makefile` e `CMakeLists.txt`): miss como estado explícito, reinserção idêntica custa zero bits, mutação de 1 bit custa exatamente 1 bit, colapso pegajoso responde MISS por política e replay reproduz a mesma identidade ISOraf.
- Seção 10 em `docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`: miss como próxima instrução, delta XOR bit-a-bit e orientação da leitura como ponto de vista.
- VECTRA_OS — execução do "next patch target" do gap ledger: correção do `VOS_CSEL` (máscara `~mask` restaurada), correção do atrator FRAF para o ponto fixo do sistema Q16 quantizado (`F* = 0x17277A`, `ITERS` 48→96 para honrar `ε = 0.001`), selftest de contrato `demo_cli/src/rmr_vectra_os_contract_selftest.c` (G2 parcial) e auditoria executável `tools/verify_vectra_os_contract.sh` (G1: warnings `-Wunused` como sinal de eliminação, export público de exatamente 3 símbolos, efeito `--gc-sections`, varredura de símbolos proibidos), com evidência em `reports/vectra_os_contract_report.md` e alvo `make verify-vectra-os-contract`.

- VECTRA_OS G3 — `engine/rmr/include/rmr_vectra_flags.def` como fonte única X-macro dos bits de capability: enum `VOS_CAP_BIT_*`, `VOS_CAP_COUNT`, máscaras derivadas e `vos_flag_name` gerados do `.def`; prova de unicidade/consistência/lookup no contract selftest, sem novos símbolos exportados.
- VECTRA_OS G4 (núcleo) — rollback transacional do registrador de capabilities: `vos_g_caps_prev`, `VOS_FLAGS_MARK()`/`VOS_FLAGS_RESTORE()` e `RAF_TRY_FLAG(mask, body)` (body falso ⇒ rollback sem resíduo), provado no contract selftest; mapeamento TTL8 permanece pendente até o codex de referência entrar na árvore.
- VECTRA_OS G5 — camada CAS atômica por contrato sobre builtins GCC/Clang: `VOS_CAS32`, `VOS_ATOMIC_LOAD32`/`VOS_ATOMIC_STORE32` e `VOS_CAS_PTR` (hotswap de dispatch por CAS com rollback, provado no contract selftest); toolchain sem builtins atômicos gera erro de compilação explícito em vez de fallback silencioso. G1–G5 do ledger agora passam; G6 (trampoline) segue opt-in aguardando decisão.
- VECTRA_OS G6 (opt-in) — trampoline hotswap: encoder puro `vos_trampoline_encode` (B imm26 ARM64 / JMP rel32 x86_64) provado por selftest sem tocar `.text` vivo, rejeitando desalinhamento e fora-de-alcance; patch físico `vos_trampoline_patch` apenas sob `VOS_ENABLE_TRAMPOLINE=1` (default 0) com manutenção de I-cache no ARM64 e aviso explícito de não-atomicidade do patch de 5 bytes em x86_64; o selftest assegura o guard desligado por padrão (anti-falsificador W^X). G1–G8 do ledger agora passam.
- VECTRA_OS G8 — camada de prova de benchmark dos 5 kernels MVP (`bench/src/vectra_os_mvp_bench_main.c`, alvo `make run-vectra-os-mvp-bench`): FRAF Q16, CRC32C 4KB no dispatch ativo, arena alloc 64B, T7 100 steps e FSM-8 + contração de Lyapunov, medidos em ticks de `VOS_TICK` com fonte nomeada; saída com todos os campos exigidos pelo falsificador do ledger — 31 amostras brutas, median/p5/p95, tag de plataforma, nomes das caps (via `vos_flag_name`), compilador, flags, commit e FNV64 do próprio binário medido.
- VECTRA_OS G7 — Machine Codex de documentação para obrigação checável: `VOS_MC_ASSERT` (compile-time), `VOS_MC_REQUIRE_POW2`/`VOS_MC_REQUIRE_ALIGNED` (protocolo FAILSAFE, caminho negativo provado com o rollback do G4 contendo o resíduo), `VOS_MC_RECIP_U32` com domínio declarado (`VOS_MC_RECIP_BOUND`, exato para x·e < 2³²; divergência além da fronteira provada como informação do contrato) e `VOS_MC_LOOP_BOUND` (orçamento MC-01/MC-10 em compile-time); obrigações do próprio header (arena pow2/alinhada, registrador de 32 bits, orçamento do loop FRAF) agora checadas onde nascem.

### Fixed
- `engine/rmr/sources_rmr_core.mk` regenerado via `tools/sync_rmr_manifest_to_mk.py` — o gate `verify-rmr-source-alignment` falhava e `rmr_vectra_os.c` não entrava na lib host do Makefile.
- Módulo BITOMEGA adicionado ao engine RMR com API pública em `engine/rmr/include/bitomega.h` e implementação determinística em `engine/rmr/src/bitomega.c`.
- Política ABI `arm32-arm64` ativada no branch de desenvolvimento: artefatos compilados para `arm64-v8a` (NEON/SIMD + CRC32HW, `-march=armv8-a+crc+simd`) e `armeabi-v7a` (NEON, `-march=armv7-a -mfpu=neon`).

### Changed
- `APP_ABI_POLICY` alterado para `arm32-arm64` e `SUPPORTED_ABIS=arm64-v8a,armeabi-v7a` em `gradle.properties`, garantindo compilação dual-ARM32+ARM64 neste branch.
- Build CMake atualizado para compilar o BITOMEGA nos alvos `rmr` (root `CMakeLists.txt`) e `vectra_core_accel` (`app/src/main/cpp/CMakeLists.txt`), incluindo novo fonte C no pipeline nativo.
- Documentação pós-doc em `docs/bitomega_postdoc/00..06` revisada para cobrir objetivo/escopo de integração, detalhes de implementação, limitações e próximos passos.
- Impacto esperado: overhead baixo por chamada de transição, melhoria de auditabilidade de estado em runtime e compatibilidade preservada por integração aditiva (sem substituir módulos existentes).
- Saneados links locais em `VECTRAS_MEGAPROMPT_DOCS.md` para caminhos reais sob `./docs/` (`ESFERAS_METODOLOGICAS_RAFAELIA`, `DETERMINISTIC_VM_MUTATION_LAYER`, `PERFORMANCE_INTEGRITY`) e executada verificação estática de links markdown locais sem novos quebrados.
- Removida a diretiva global `-dontobfuscate` do `app/proguard-rules.pro`, com redução das regras `-keep` para apenas símbolos exigidos por reflexão/XML e inclusão de registro dos símbolos estáveis no guia de build/release.
- Alinhamento de baseline Android SDK 35 / minSdk 29 padronizado em Gradle, docs e CI; path legado `android/` bloqueado contra compilação acidental (PRs #907–#909).
- Verificador `verify_repo_file_dependencies.py` atualizado para ignorar paths interpolados Groovy/Gradle (`${...}` e `${}`) (PRs #910–#911).
- Metadados `DOC_ORG_SCAN` atualizados de `pending-manual-by-domain` para `complete` nos arquivos canônicos — varredura executada em 2026-04-07.
- Placeholder vazio `// Retrofit` removido de `app/build.gradle` (dependência não utilizada).

## [3.6.6] - 2026-02-10
### Added
- `TokenBucketRateLimiter` para controle de taxa de logs.
- `BoundedStringRingBuffer` para armazenamento bounded de saída.
- `ProcessOutputDrainer` para drenagem concorrente de stdout/stderr.
- `ProcessSupervisor` com máquina de estados e stop escalonado.
- `AuditEvent` e `AuditLedger` (JSONL rotativo).
- Testes unitários para rate limiter, ring buffer e drainer.

### Changed
- `Terminal.streamLog` agora usa backpressure, degradação e stop token.
- `ShellExecutor` retorna resultado estruturado com timeout/cancel.
- `VMManager` prioriza supervisão por processo em vez de `killall` global.
- `PermissionUtils` modernizado para Scoped Storage/SAF em Android 10+.

### Fixed
- Risco de deadlock por leitura sequencial de stdout/stderr.
- Risco de loop bloqueante de `readLine()` em processo longo.
- Risco de explosão de memória por concatenação ilimitada de logs.
