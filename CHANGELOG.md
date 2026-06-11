<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: complete -->

# Changelog
All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog.

## [Unreleased]

### Added
- Mutação seletiva de bits no cache TCG (`engine/rmr/src/rmr_tcg_cache.c`): a reinserção de bloco grava apenas o delta XOR entre o byte residente e o candidato, preservando bits iguais e o físico esparso do ISOraf; novas métricas `delta_bits_flipped`/`delta_bits_preserved` com acessores `RmR_TCGCache_DeltaBitsFlipped` e `RmR_TCGCache_DeltaPreservedPct`.
- Selftest host `demo_cli/src/rmr_tcg_cache_selftest.c` (registrado em `Makefile` e `CMakeLists.txt`): miss como estado explícito, reinserção idêntica custa zero bits, mutação de 1 bit custa exatamente 1 bit, colapso pegajoso responde MISS por política e replay reproduz a mesma identidade ISOraf.
- Seção 10 em `docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`: miss como próxima instrução, delta XOR bit-a-bit e orientação da leitura como ponto de vista.
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
