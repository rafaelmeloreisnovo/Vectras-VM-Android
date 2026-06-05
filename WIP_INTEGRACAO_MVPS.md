# WIP — Integração dos MVPs

<!-- Atualizado 2026-06-05: status atualizado com base em auditoria completa do repositório -->

Este arquivo marca a trilha de integração dos MVPs do projeto Vectras-VM-Android.

## Objetivo
- Consolidar os componentes já validados isoladamente
- Preparar integração incremental sem tocar a branch principal
- Servir como ponto-base para próximos commits de montagem

## Status (2026-06-05)

### Componentes Validados ✅
- **Engine RMR**: `rmr_unified_kernel.c/.h`, `rmr_unified_jni_bridge.c` — API JNI completa e testada
- **NEON SIMD**: `rmr_neon_simd.c` — xor_fold, crc32c, phi_step, memcpy, popcount
- **Lowlevel**: `rmr_lowlevel_portable.c`, `rmr_lowlevel_mix.c`, `rmr_lowlevel_reduce.c` — fold32, rotl32, reduce_xor, checksum32
- **JNI Bridge**: `rmr_unified_jni_bridge.c` — todas as 9 funções `rmr_jni_kernel_*` implementadas
- **Magic constant**: `RMR_UK_NATIVE_OK_MAGIC = 0x56414343` alinhado em todos os 3 pontos C + Java
- **BITRAF core**: `rafaelia_bitraf_core.c`, `bitraf.c` — hash e paridade 2D
- **Policy kernel**: `rmr_policy_kernel.c` — ciclos Hit/Miss determinísticos
- **Torus flow**: `rmr_torus_flow.c`, endereçamento toroidal 7D
- **Selftest suite**: 25+ arquivos em `demo_cli/src/`
- **Android app layer**: `VectraCore.kt`, `VectraDeterministicContainer.kt`
- **Build guards**: `-DRMR_JNI_BUILD=1` corretamente separado de `-DRMR_BAREMETAL`

### HOTFIX Aplicado nesta sessão ✅
- `engine/rmr/src/rmr_zipraf_core.c`: inicialização explícita de `tri_flow`, `tri_closed`, `tri_coherence` — corrige aviso de variável não inicializada e UB potencial

### Pendente — Decisão do Mantenedor ⚠️
- **181 arquivos em `Incluir/` e `_incoming/`**: aguardam classificação e promoção em lotes (ver `docs/organization/NECESSARY_DATA_DELIVERY_MATRIX_2026-06-02.md`)
- **51 arquivos `.S` de assembly**: status TBD, aguardam revisão de contrato de registradores (ver `docs/TODO_INCOMING_PENDING.md`)
- **Firebase `google-services.json`**: placeholder — requer credenciais reais do projeto
- **Certificate pinning**: removido por placeholder de hash — requer hash real do certificado
- **Deprecated API v3.5**: `getForceRefreshVNCDisplay()` / `setForceRefreshVNCDisplay()` em `MainSettingsManager.java`

### Próximos Passos de Integração
1. Promover arquivos de `Incluir/` em Lote 1 (Markdown root) conforme `NECESSARY_DATA_DELIVERY_MATRIX`
2. Revisar contratos de assembly dos 51 arquivos `.S` antes de incluir no build
3. Executar CI canônico no commit atual para validar status BETA_BLOCKED → STABLE

## Referência
- Auditoria completa: [`docs/active/AUDIT_HOTFIX_REPORT_2026-06-05.md`](docs/active/AUDIT_HOTFIX_REPORT_2026-06-05.md)
- Plano de entrega: [`docs/organization/NECESSARY_DATA_DELIVERY_MATRIX_2026-06-02.md`](docs/organization/NECESSARY_DATA_DELIVERY_MATRIX_2026-06-02.md)
