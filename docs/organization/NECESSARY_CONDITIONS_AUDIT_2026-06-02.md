# Auditoria de condições necessárias, placeholders e materiais pendentes — 2026-06-02

Varredura determinística local, source-read-only, limitada a 5 níveis de profundidade.
Arquivos de saída gerados pela própria auditoria são excluídos da contagem para evitar auto-ruído.

## Contadores

| Métrica | Valor |
|---|---:|
| `bug_signal_hits` | 243 |
| `code_scanned` | 592 |
| `docs_scanned` | 451 |
| `files_scanned` | 1376 |
| `ingress_files` | 181 |
| `navigation_gaps` | 118 |
| `placeholder_hits` | 368 |
| `zip_files` | 27 |

## Condições necessárias de aceite

| Condição | Estado | Evidência/ação |
|---|---|---|
| Varredura até 5 níveis | PASS | Profundidade fixa no script e relatório. |
| Não remover funcionalidades | PASS | Auditoria source-read-only; organização por manifesto antes de mover. |
| Placeholders e pendências visíveis | PASS | Achados listados por severidade e categoria. |
| Failsafe/failover/rollback | PARCIAL | Documentado como critério de promoção; build/teste Android ainda dependem de SDK. |
| Hot path sem heap/GC | PASS nesta etapa | Nenhum `.S` ou hot path nativo foi alterado. |

## Distribuição dos achados

| Severidade | Total |
|---|---:|
| `high` | 208 |
| `medium` | 611 |
| `low` | 118 |

| Categoria | Total |
|---|---:|
| `bug-failsafe-signal` | 243 |
| `entrada-pendente` | 181 |
| `overlay-zip` | 27 |
| `placeholder-ou-pendente` | 368 |
| `sem-files-map` | 33 |
| `sem-readme` | 85 |

## Achados priorizados

| Severidade | Categoria | Caminho | Detalhe |
|---|---|---|---|
| high | entrada-pendente | `Incluir/.moveFiles para um su diretório.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/01_Rafaelia_Execucao_Saneada.docx` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/01_taxonomia_academica_malha_simbiotica.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/02_Paper_Base_Completo.docx` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/02_formalizacao_matematica_modelo_de_estados.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/03_paper_base_programa_interdisciplinar.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/09_Linha_de_Artigos.docx` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/11_Artigo_2_Experimental.docx` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/A incluir.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/APPLY_FIXES.sh` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/AUDIT_REPORT.json` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/AUDIT_REPORT.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/CODEX_PROMPT_VECTRAS_TERMUX_ARM32_CI.txt` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/ChatGPT Image 22 de abr. de 2026, 08_01_26.png` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/EXECUTION_REPORT.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FILE_ORGANIZATION.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_01_local-1.properties` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_02_google-services-1.json` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_03_sources_rmr_core-1.cmake` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_04_CMakeLists-1.txt` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_05_app_src_main_cpp_CMakeLists-1.txt` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_06_CMakePresets-1.json` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/FIX_07_rmr_unified_jni_base-1.h` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/INTEGRATION_STATUS.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Mais.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Possível núcleo.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_ARM32_HotPath_N55_56Ciclos.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_ARM32_Pipeline_FrameCacheTelemetry_v5.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_ARM32_Pipeline_v6_TraceFaultTarget.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_ARM32_Pipeline_v7_ProtoCRCTrace.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_ARM32_Pipeline_v8_ProtoZeroCopyReport.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_Solido_ARM32_ARMv7.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/RAF_C_ASM_Solido_x86_64.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Rafael estados.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Rafaelia_Execucao_Kit (1).zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Rafaelia_Execucao_Kit.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Rafaelia_Execucao_Kit_Completo.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Rafaelia_Execucao_Kit_V2_ABNT_Resultados_Artigos.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Rafaeltesesmd.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Readme.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/Texto colado.txt` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/UNIFIED_INVARIANT_SPEC.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/_VECTRAS_REPOSITORY_OVERVIEW.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/arm32_nanogpt_c_asm_sem_dependencias.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/fit_coherence_C.png` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/fit_timeseries_S.png` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/glmm_em_zip.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/matrix_ops (1).py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/matrix_ops.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/modelo_dinamico_pacote.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/pacote_1_2_3_malha_simbiotica.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/pacote_submissao_abnt_modelo_dinamico.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/pacote_submissao_abnt_modelo_dinamico_novo (1).zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/pacote_submissao_abnt_modelo_dinamico_novo (2).zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/pacote_submissao_abnt_modelo_dinamico_novo.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/rafaelia_anterioridade_pack.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/rafaelia_rigor_pipeline.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/regime_map_alpha_lambda.png` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/repo_audit_and_plan.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/report.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/sensitivity_mean_S.png` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/sessao_completa_possibilidades_e_matematica.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/sistema_minimo_invariantes (1).md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/sistema_minimo_invariantes (1).xlsx` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/sistema_minimo_invariantes.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/sistema_minimo_invariantes.xlsx` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/submissao_modelo_dinamico_abnt (1).zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/submissao_modelo_dinamico_abnt.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/t7_invariant_engine.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/termux-app-rafacodephi-master.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/test_repo_audit_and_plan.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/test_t7_invariant_engine.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/tinygpt_c_asm_like_nanogpt_src.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/tres_md_malha_simbiotica.zip` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/tue_apr_21_2026_comprehensive_documentation_for_vectras_vm_android.json` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/tue_apr_21_2026_repository_analysis_and_bug_report_summary.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `Incluir/vectras_bbs.c` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `__DELTA__/CMAKE_ANDROID_SNIPPET.txt` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `__DELTA__/CMAKE_ROOT_SNIPPET.txt` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `__DELTA__/PATCH_NOTES.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `__DELTA__/rrrrr` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/INTEGRATION_EVIDENCE.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/README.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/Android_nomalloc.mk` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/Application.mk` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/RAFAELIA_MATH_FORMULAS.md` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/RafaeliaCore.java` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/baremetal_nomalloc.c` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/baremetal_nomalloc.h` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/bitraf64_prototype_Version4.py` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/bitstack.c` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/bitstack.h` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/build_all.sh` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/diagnose.sh` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/diagnose_termux.sh` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/hyperforms.json` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/r.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/raf_asm_b1.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_10x10.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_7d.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_7d_gyro.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_7d_shapes.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_8way.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_936_fast.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_999_logsin.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_abs.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_arena.h` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_avalanche.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_avalanche_v2.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b1.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b2.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b3.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b4.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b5.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b6.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b7.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_b8.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_bench_phi.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_bitraf.c` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| high | entrada-pendente | `_incoming/pending/rafaelia_central_link.S` | Arquivo em área de ingresso; requer hash, teste e decisão de promoção. |
| info | truncado | `...` | 817 achados adicionais disponíveis no JSON. |

## Mitigação recomendada

1. Criar manifesto SHA-256 dos overlays ZIP e entradas pendentes antes de qualquer extração.
2. Converter placeholders legítimos em issues/backlog com dono, teste esperado e rollback.
3. Promover `_incoming/` e `Incluir/` em lotes pequenos, cada um com teste de equivalência ou justificativa SKIPPED.
4. Reexecutar esta auditoria antes de cada reorganização documental maior.
