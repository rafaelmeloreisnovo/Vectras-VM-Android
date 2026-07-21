<!-- DOC_ORG_SCAN: 2026-07-21 | source-scan: active-rmr-visual -->

# engine/FILES_MAP.md

Mapa arquivo-a-arquivo em três linhas por item: papel, ligação e comando de inspeção.

## `engine/README.md`
- **Papel**: documentação local do diretório.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/README.md"` e, quando texto, `sed -n "1,120p" "engine/README.md"`.

## `engine/rmr/README.md`
- **Papel**: documentação local do diretório.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/README.md"` e, quando texto, `sed -n "1,220p" "engine/rmr/README.md"`.

## `engine/rmr/include/bitraf.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/bitraf.h"` e, quando texto, `sed -n "1,160p" "engine/rmr/include/bitraf.h"`.

## `engine/rmr/include/bitraf_version.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/bitraf_version.h"` e, quando texto, `sed -n "1,120p" "engine/rmr/include/bitraf_version.h"`.

## `engine/rmr/include/rmr_apk_module.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/rmr_apk_module.h"` e, quando texto, `sed -n "1,160p" "engine/rmr/include/rmr_apk_module.h"`.

## `engine/rmr/include/rmr_bench.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/rmr_bench.h"` e, quando texto, `sed -n "1,160p" "engine/rmr/include/rmr_bench.h"`.

## `engine/rmr/include/rmr_bench_suite.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/rmr_bench_suite.h"` e, quando texto, `sed -n "1,160p" "engine/rmr/include/rmr_bench_suite.h"`.

## `engine/rmr/include/rmr_cycles.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/rmr_cycles.h"` e, quando texto, `sed -n "1,160p" "engine/rmr/include/rmr_cycles.h"`.

## `engine/rmr/include/rmr_hw_detect.h`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`engine/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "engine/rmr/include/rmr_hw_detect.h"` e, quando texto, `sed -n "1,180p" "engine/rmr/include/rmr_hw_detect.h"`.

## `engine/rmr/include/rmr_isorf.h`
- **Papel**: armazenamento lógico denso com físico esparso, sem compressão.
- **Liga com**: manifesto/identidade e mapas de páginas do core RMR.
- **Inspeção**: `file "engine/rmr/include/rmr_isorf.h"` e `sed -n "1,180p" "engine/rmr/include/rmr_isorf.h"`.

## `engine/rmr/include/rmr_policy_kernel.h`
- **Papel**: contrato do policy kernel determinístico.
- **Liga com**: `engine/rmr/src/rmr_policy_kernel.c` e módulos de custódia que usam CRC32C.
- **Inspeção**: `file "engine/rmr/include/rmr_policy_kernel.h"` e `sed -n "1,220p" "engine/rmr/include/rmr_policy_kernel.h"`.

## `engine/rmr/include/rmr_stability.h`
- **Papel**: API canônica para estado de estabilidade, associação TRIAD/GBS3 e descritor visual determinístico.
- **Liga com**: `rmr_visual_prototype.h`, backend ARMv7 e [`rmr/STABILITY_VISION_INTEGRATION.md`](rmr/STABILITY_VISION_INTEGRATION.md).
- **Inspeção**: `sed -n "1,220p" "engine/rmr/include/rmr_stability.h"`.

## `engine/rmr/include/rmr_visual_prototype.h`
- **Papel**: contrato de protótipos rotulados, 16 vistas, RAFSTORE fixo, classificação e cápsula RVC1.
- **Liga com**: `rmr_stability.h`, `rmr_visual_zipraf.h` e [`rmr/VISUAL_PROTOTYPE_INTEGRATION.md`](rmr/VISUAL_PROTOTYPE_INTEGRATION.md).
- **Inspeção**: `sed -n "1,260p" "engine/rmr/include/rmr_visual_prototype.h"`.

## `engine/rmr/include/rmr_visual_zipraf.h`
- **Papel**: adaptador entre cápsula visual serializada e custódia `RmR_Zipraf_Execute`.
- **Liga com**: `rmr_visual_prototype.h` e `rmr_zipraf_core.h`.
- **Inspeção**: `sed -n "1,180p" "engine/rmr/include/rmr_visual_zipraf.h"`.

## `engine/rmr/src/bitraf.c`
- **Papel**: implementação da API Bitraf.
- **Liga com**: ZIPRAF, hash/fingerprint e biblioteca `libbitraf`.
- **Inspeção**: `file "engine/rmr/src/bitraf.c"` e `sed -n "1,220p" "engine/rmr/src/bitraf.c"`.

## `engine/rmr/src/rafa_cti_scan.c`
- **Papel**: scanner CTI de blocos, offsets, métricas e identidade.
- **Liga com**: artefatos JSONL/CSV e análise externa RMR-CTI.
- **Inspeção**: `file "engine/rmr/src/rafa_cti_scan.c"` e `sed -n "1,260p" "engine/rmr/src/rafa_cti_scan.c"`.

## `engine/rmr/src/rafaelia_bitraf_core.c`
- **Papel**: núcleo/demonstração de integração Rafaelia/Bitraf.
- **Liga com**: `bitraf.c`, librmr e executável de demonstração.
- **Inspeção**: `file "engine/rmr/src/rafaelia_bitraf_core.c"` e `sed -n "1,220p" "engine/rmr/src/rafaelia_bitraf_core.c"`.

## `engine/rmr/src/rmr_apk_module.c`
- **Papel**: fingerprint e plano determinístico para módulo APK.
- **Liga com**: hardware detect, assinatura e orquestração Termux/Gradle.
- **Inspeção**: `file "engine/rmr/src/rmr_apk_module.c"` e `sed -n "1,240p" "engine/rmr/src/rmr_apk_module.c"`.

## `engine/rmr/src/rmr_bench.c`
- **Papel**: suporte de benchmark RMR.
- **Liga com**: `rmr_bench_suite.c` e executáveis em `build/bench`.
- **Inspeção**: `file "engine/rmr/src/rmr_bench.c"` e `sed -n "1,220p" "engine/rmr/src/rmr_bench.c"`.

## `engine/rmr/src/rmr_bench_suite.c`
- **Papel**: suíte de cenários de benchmark e cobertura determinística.
- **Liga com**: torus flow, core RMR e relatórios de benchmark.
- **Inspeção**: `file "engine/rmr/src/rmr_bench_suite.c"` e `sed -n "1,280p" "engine/rmr/src/rmr_bench_suite.c"`.

## `engine/rmr/src/rmr_cycles.c`
- **Papel**: primitivas e medição de ciclos.
- **Liga com**: benchmarks e seleção por arquitetura.
- **Inspeção**: `file "engine/rmr/src/rmr_cycles.c"` e `sed -n "1,220p" "engine/rmr/src/rmr_cycles.c"`.

## `engine/rmr/src/rmr_hw_detect.c`
- **Papel**: detecção de hardware e capacidades.
- **Liga com**: autotune, math fabric, APK e QEMU.
- **Inspeção**: `file "engine/rmr/src/rmr_hw_detect.c"` e `sed -n "1,260p" "engine/rmr/src/rmr_hw_detect.c"`.

## `engine/rmr/src/rmr_isorf.c`
- **Papel**: páginas físicas esparsas e identidade de armazenamento lógico.
- **Liga com**: `rmr_isorf.h` e manifestos reconstruíveis.
- **Inspeção**: `file "engine/rmr/src/rmr_isorf.c"` e `sed -n "1,260p" "engine/rmr/src/rmr_isorf.c"`.

## `engine/rmr/src/rmr_policy_kernel.c`
- **Papel**: implementação do policy kernel e CRC32C canônico do core.
- **Liga com**: módulos de verificação, ZIPRAF e mutação determinística.
- **Inspeção**: `file "engine/rmr/src/rmr_policy_kernel.c"` e `sed -n "1,300p" "engine/rmr/src/rmr_policy_kernel.c"`.

## `engine/rmr/src/rmr_stability.c`
- **Papel**: Otsu, foreground, histograma angular, diferença visual, `DeltaP` e dispatch ARMv7.
- **Liga com**: `rmr_stability_armv7.S`, `rmr_visual_prototype.c` e autotestes.
- **Inspeção**: `sed -n "1,440p" "engine/rmr/src/rmr_stability.c"`.

## `engine/rmr/src/rmr_visual_prototype.c`
- **Papel**: protótipo por vistas, score/margem, RAFSTORE sem heap e codec canônico RVC1.
- **Liga com**: `rmr_stability.c`, `rmr_visual_zipraf.c` e `ZiprafDirectRuntime.kt` pela cápsula STORE.
- **Inspeção**: `sed -n "1,520p" "engine/rmr/src/rmr_visual_prototype.c"`.

## `engine/rmr/src/rmr_visual_zipraf.c`
- **Papel**: sela bytes RVC1 e os encaminha à custódia ZIPRAF/Bitraf.
- **Liga com**: `rmr_zipraf_core.c`, `rmr_visual_prototype.c` e manifestos canônicos de source.
- **Inspeção**: `sed -n "1,220p" "engine/rmr/src/rmr_visual_zipraf.c"`.

## `demo_cli/src/rmr_visual_prototype_selftest.c`
- **Papel**: vetor dourado de três classes, duas vistas, margem, round-trip e adulteração.
- **Liga com**: `tools/test_rmr_visual_prototype.sh`.
- **Inspeção**: `sed -n "1,260p" "demo_cli/src/rmr_visual_prototype_selftest.c"`.

## `engine/vectra_policy_kernel/.gitignore`
- **Papel**: artefato de suporte do diretório.
- **Liga com**: crate Rust do policy kernel.
- **Inspeção**: `file "engine/vectra_policy_kernel/.gitignore"`.

## `engine/vectra_policy_kernel/Cargo.lock`
- **Papel**: lockfile do crate Rust.
- **Liga com**: `Cargo.toml` e builds reproduzíveis.
- **Inspeção**: `file "engine/vectra_policy_kernel/Cargo.lock"`.

## `engine/vectra_policy_kernel/Cargo.toml`
- **Papel**: configuração declarativa do crate.
- **Liga com**: `src/lib.rs`, `src/main.rs` e testes.
- **Inspeção**: `sed -n "1,180p" "engine/vectra_policy_kernel/Cargo.toml"`.

## `engine/vectra_policy_kernel/src/lib.rs`
- **Papel**: biblioteca Rust do policy kernel.
- **Liga com**: testes e binário demonstrativo.
- **Inspeção**: `sed -n "1,260p" "engine/vectra_policy_kernel/src/lib.rs"`.

## `engine/vectra_policy_kernel/src/main.rs`
- **Papel**: entrada executável Rust.
- **Liga com**: `lib.rs` e configuração Cargo.
- **Inspeção**: `sed -n "1,220p" "engine/vectra_policy_kernel/src/main.rs"`.

## `engine/vectra_policy_kernel/tests/policy_kernel_tests.rs`
- **Papel**: testes do policy kernel Rust.
- **Liga com**: `src/lib.rs`.
- **Inspeção**: `sed -n "1,260p" "engine/vectra_policy_kernel/tests/policy_kernel_tests.rs"`.
