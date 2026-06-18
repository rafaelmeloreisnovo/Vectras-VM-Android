<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

# demo_cli/FILES_MAP.md

Mapa arquivo-a-arquivo em três linhas por item: papel, ligação e comando de inspeção.

## `demo_cli/README.md`
- **Papel**: documentação local do diretório.
- **Liga com**: ver [`demo_cli/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "demo_cli/README.md"` e, quando texto, `sed -n "1,80p" "demo_cli/README.md"`.

## `demo_cli/src/apk_module_demo.c`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`demo_cli/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "demo_cli/src/apk_module_demo.c"` e, quando texto, `sed -n "1,80p" "demo_cli/src/apk_module_demo.c"`.

## `demo_cli/src/bitraf_selftest.c`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`demo_cli/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "demo_cli/src/bitraf_selftest.c"` e, quando texto, `sed -n "1,80p" "demo_cli/src/bitraf_selftest.c"`.

## `demo_cli/src/main.c`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`demo_cli/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "demo_cli/src/main.c"` e, quando texto, `sed -n "1,80p" "demo_cli/src/main.c"`.

## `demo_cli/src/policy_kernel_demo.c`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`demo_cli/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "demo_cli/src/policy_kernel_demo.c"` e, quando texto, `sed -n "1,80p" "demo_cli/src/policy_kernel_demo.c"`.

## `demo_cli/src/policy_kernel_selftest.c`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`demo_cli/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "demo_cli/src/policy_kernel_selftest.c"` e, quando texto, `sed -n "1,80p" "demo_cli/src/policy_kernel_selftest.c"`.


## `demo_cli/src/rmr_tcg_cache_selftest.c`
- **Papel**: auto-teste do cache TCG — miss como estado, mutação seletiva de bits (delta XOR), colapso pegajoso e replay determinístico.
- **Liga com**: [`engine/rmr/src/rmr_tcg_cache.c`](../engine/rmr/src/rmr_tcg_cache.c), [`docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`](../docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md), [`Makefile`](../Makefile) e [`CMakeLists.txt`](../CMakeLists.txt).
- **Inspeção**: `file "demo_cli/src/rmr_tcg_cache_selftest.c"` e, quando texto, `sed -n "1,180p" "demo_cli/src/rmr_tcg_cache_selftest.c"`.

## `demo_cli/src/rmr_vectra_os_contract_selftest.c`
- **Papel**: prova executável do contrato VECTRA_OS — tabela-verdade VOS_CSEL, arena mark/restore, dispatch hotswap com rollback e caps report (ledger §8).
- **Liga com**: [`engine/rmr/include/rmr_vectra_os.h`](../engine/rmr/include/rmr_vectra_os.h), [`docs/VECTRA_OS_LIVING_SYSTEM_GAP_LEDGER.md`](../docs/VECTRA_OS_LIVING_SYSTEM_GAP_LEDGER.md), [`tools/verify_vectra_os_contract.sh`](../tools/verify_vectra_os_contract.sh), [`Makefile`](../Makefile) e [`CMakeLists.txt`](../CMakeLists.txt).
- **Inspeção**: `file "demo_cli/src/rmr_vectra_os_contract_selftest.c"` e, quando texto, `sed -n "1,120p" "demo_cli/src/rmr_vectra_os_contract_selftest.c"`.

## `demo_cli/src/rmr_unified_arena_selftest.c`
- **Papel**: código-fonte de auto-teste da arena unificada com cenários de fragmentação/reuso.
- **Liga com**: [`engine/rmr/src/rmr_unified_kernel.c`](../engine/rmr/src/rmr_unified_kernel.c), [`Makefile`](../Makefile) e [`CMakeLists.txt`](../CMakeLists.txt).
- **Inspeção**: `file "demo_cli/src/rmr_unified_arena_selftest.c"` e, quando texto, `sed -n "1,220p" "demo_cli/src/rmr_unified_arena_selftest.c"`.
