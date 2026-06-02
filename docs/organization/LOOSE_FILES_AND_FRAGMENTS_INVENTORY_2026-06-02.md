# Inventário de arquivos soltos e fragmentos (varredura até 7 níveis)
Data da varredura: 2026-06-02.

## Escopo
- Varredura local até profundidade 7, sem entrar em `.git`, `.gradle`, `build` ou `.idea`.
- Este documento **não move arquivos**: ele classifica para organização segura, rollback e revisão humana.
- Critérios: arquivos de raiz que são documentos/dados, nomes com espaços/parênteses/ponto-e-vírgula, diretórios de entrada pendente (`Incluir/`, `_incoming/`, `__DELTA__/`) e overlays `*.zip`.

## Resultado resumido
- Total classificado: **243** arquivos.
- Diretórios de entrada pendente com maior risco de drift: `Incluir/`, `_incoming/`, `__DELTA__/`.
- Raiz contém documentos canônicos, notas conceituais soltas, imagens de captura e dados pontuais que precisam de decisão explícita antes de mover.

## Política de organização proposta
| Classe | Destino recomendado | Regra de rollback |
|---|---|---|
| Documentação canônica de raiz (`README`, `BUILDING`, `PROJECT_STATE`, `DOC_INDEX`) | permanecer na raiz | não mover; manter links em `DOC_INDEX.md` |
| Notas conceituais soltas (`raiz de cinco por doze.md`, `qtm.md`, `rrraf impoo.md`, etc.) | `docs/rafaelia_reference/` ou `archive/root-history/` após revisão | mover via `git mv` em lote separado, com manifesto de origem |
| Entradas pendentes (`Incluir/`, `_incoming/`) | promover para `docs/`, `engine/`, `tools/` ou `archive/experimental/` por evidência | preservar arquivo original até teste equivalente PASS |
| Overlays ZIP | manter como artefato histórico ou remover só com extração rastreada | validar hash + manifesto antes de excluir |
| Capturas/imagens soltas | `docs/assets/` com legenda/proveniência | conservar nome original no manifesto |

## Inventário detalhado
| Caminho | Profundidade | Bytes | Classificação |
|---|---:|---:|---|
| `.ci/files (26).zip` | 2 | 18908 | nome-fragmento+overlay-zip |
| `.ci/files (27).zip` | 2 | 25061 | nome-fragmento+overlay-zip |
| `.firebaserc` | 1 | 55 | raiz/sem-extensao |
| `.gitignore` | 1 | 2298 | raiz/sem-extensao |
| `.java-version` | 1 | 3 | raiz/sem-extensao |
| `1;.jkjjh` | 1 | 1134 | nome-fragmento |
| `33.md` | 1 | 4518 | raiz/doc-ou-dado-solto |
| `AGENTS.md` | 1 | 1670 | raiz/doc-ou-dado-solto |
| `AGENTS2.md` | 1 | 1723 | raiz/doc-ou-dado-solto |
| `AUTHORSHIP_CLEANROOM_PLAN.md` | 1 | 3579 | raiz/doc-ou-dado-solto |
| `Agents.md` | 1 | 3196 | raiz/doc-ou-dado-solto |
| `BOOTSTRAP_LOWLEVEL_RAFAELIA.txt` | 1 | 71133 | raiz/doc-ou-dado-solto |
| `BUILDING.md` | 1 | 20248 | raiz/doc-ou-dado-solto |
| `CHANGELOG.md` | 1 | 3298 | raiz/doc-ou-dado-solto |
| `CMakeLists.txt` | 1 | 12482 | raiz/doc-ou-dado-solto |
| `CODEX_EXECUTION_BRIEF.md` | 1 | 8983 | raiz/doc-ou-dado-solto |
| `COMPILATION_FIXES.md` | 1 | 2035 | raiz/doc-ou-dado-solto |
| `CONTRIBUTING.md` | 1 | 4982 | raiz/doc-ou-dado-solto |
| `CREDITS_INSPIRATION.md` | 1 | 830 | raiz/doc-ou-dado-solto |
| `Captura de tela 2026-05-05 114839.png` | 1 | 106153 | raiz/imagem-solta+nome-fragmento |
| `Captura de tela 2026-05-05 115346.png` | 1 | 115732 | raiz/imagem-solta+nome-fragmento |
| `Captura de tela 2026-05-05 115526.png` | 1 | 198972 | raiz/imagem-solta+nome-fragmento |
| `Captura de tela 2026-05-05 115833.png` | 1 | 108691 | raiz/imagem-solta+nome-fragmento |
| `Captura de tela 2026-05-10 211927.png` | 1 | 256777 | raiz/imagem-solta+nome-fragmento |
| `DOC_INDEX.md` | 1 | 10581 | raiz/doc-ou-dado-solto |
| `Erro.md` | 1 | 2344 | raiz/doc-ou-dado-solto |
| `FIXES_SUMMARY.md` | 1 | 5482 | raiz/doc-ou-dado-solto |
| `Incluir/.moveFiles para um su diretório.md` | 2 | 1 | nome-fragmento+entrada-pendente |
| `Incluir/01_Rafaelia_Execucao_Saneada.docx` | 2 | 41885 | entrada-pendente |
| `Incluir/01_taxonomia_academica_malha_simbiotica.md` | 2 | 15381 | entrada-pendente |
| `Incluir/02_Paper_Base_Completo.docx` | 2 | 41496 | entrada-pendente |
| `Incluir/02_formalizacao_matematica_modelo_de_estados.md` | 2 | 9144 | entrada-pendente |
| `Incluir/03_paper_base_programa_interdisciplinar.md` | 2 | 12231 | entrada-pendente |
| `Incluir/09_Linha_de_Artigos.docx` | 2 | 37903 | entrada-pendente |
| `Incluir/11_Artigo_2_Experimental.docx` | 2 | 190288 | entrada-pendente |
| `Incluir/A incluir.md` | 2 | 11564 | nome-fragmento+entrada-pendente |
| `Incluir/APPLY_FIXES.sh` | 2 | 9331 | entrada-pendente |
| `Incluir/AUDIT_REPORT.json` | 2 | 162735 | entrada-pendente |
| `Incluir/AUDIT_REPORT.md` | 2 | 10407 | entrada-pendente |
| `Incluir/CODEX_PROMPT_VECTRAS_TERMUX_ARM32_CI.txt` | 2 | 7997 | entrada-pendente |
| `Incluir/ChatGPT Image 22 de abr. de 2026, 08_01_26.png` | 2 | 1317886 | nome-fragmento+entrada-pendente |
| `Incluir/EXECUTION_REPORT.md` | 2 | 1361 | entrada-pendente |
| `Incluir/FILE_ORGANIZATION.md` | 2 | 14200 | entrada-pendente |
| `Incluir/FIX_01_local-1.properties` | 2 | 1409 | entrada-pendente |
| `Incluir/FIX_02_google-services-1.json` | 2 | 690 | entrada-pendente |
| `Incluir/FIX_03_sources_rmr_core-1.cmake` | 2 | 3787 | entrada-pendente |
| `Incluir/FIX_04_CMakeLists-1.txt` | 2 | 13542 | entrada-pendente |
| `Incluir/FIX_05_app_src_main_cpp_CMakeLists-1.txt` | 2 | 9688 | entrada-pendente |
| `Incluir/FIX_06_CMakePresets-1.json` | 2 | 3352 | entrada-pendente |
| `Incluir/FIX_07_rmr_unified_jni_base-1.h` | 2 | 10064 | entrada-pendente |
| `Incluir/INTEGRATION_STATUS.md` | 2 | 1621 | entrada-pendente |
| `Incluir/Mais.md` | 2 | 5839 | entrada-pendente |
| `Incluir/Possível núcleo.md` | 2 | 5839 | nome-fragmento+entrada-pendente |
| `Incluir/RAF_C_ASM_ARM32_HotPath_N55_56Ciclos.zip` | 2 | 21845 | entrada-pendente+overlay-zip |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_FrameCacheTelemetry_v5.zip` | 2 | 971265 | entrada-pendente+overlay-zip |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_v6_TraceFaultTarget.zip` | 2 | 942191 | entrada-pendente+overlay-zip |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_v7_ProtoCRCTrace.zip` | 2 | 1041203 | entrada-pendente+overlay-zip |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_v8_ProtoZeroCopyReport.zip` | 2 | 593126 | entrada-pendente+overlay-zip |
| `Incluir/RAF_C_ASM_Solido_ARM32_ARMv7.zip` | 2 | 14876 | entrada-pendente+overlay-zip |
| `Incluir/RAF_C_ASM_Solido_x86_64.zip` | 2 | 21161 | entrada-pendente+overlay-zip |
| `Incluir/Rafael estados.md` | 2 | 15940 | nome-fragmento+entrada-pendente |
| `Incluir/Rafaelia_Execucao_Kit (1).zip` | 2 | 42667 | nome-fragmento+entrada-pendente+overlay-zip |
| `Incluir/Rafaelia_Execucao_Kit.zip` | 2 | 42667 | entrada-pendente+overlay-zip |
| `Incluir/Rafaelia_Execucao_Kit_Completo.zip` | 2 | 142367 | entrada-pendente+overlay-zip |
| `Incluir/Rafaelia_Execucao_Kit_V2_ABNT_Resultados_Artigos.zip` | 2 | 1622102 | entrada-pendente+overlay-zip |
| `Incluir/Rafaeltesesmd.md` | 2 | 8929 | entrada-pendente |
| `Incluir/Readme.md` | 2 | 1791 | entrada-pendente |
| `Incluir/Texto colado.txt` | 2 | 17915 | nome-fragmento+entrada-pendente |
| `Incluir/UNIFIED_INVARIANT_SPEC.md` | 2 | 2733 | entrada-pendente |
| `Incluir/_VECTRAS_REPOSITORY_OVERVIEW.md` | 2 | 4285 | entrada-pendente |
| `Incluir/arm32_nanogpt_c_asm_sem_dependencias.zip` | 2 | 129793 | entrada-pendente+overlay-zip |
| `Incluir/fit_coherence_C.png` | 2 | 98021 | entrada-pendente |
| `Incluir/fit_timeseries_S.png` | 2 | 106931 | entrada-pendente |
| `Incluir/glmm_em_zip.zip` | 2 | 99166 | entrada-pendente+overlay-zip |
| `Incluir/matrix_ops (1).py` | 2 | 40349 | nome-fragmento+entrada-pendente |
| `Incluir/matrix_ops.py` | 2 | 40349 | entrada-pendente |
| `Incluir/modelo_dinamico_pacote.zip` | 2 | 479132 | entrada-pendente+overlay-zip |
| `Incluir/pacote_1_2_3_malha_simbiotica.zip` | 2 | 34898 | entrada-pendente+overlay-zip |
| `Incluir/pacote_submissao_abnt_modelo_dinamico.zip` | 2 | 1111368 | entrada-pendente+overlay-zip |
| `Incluir/pacote_submissao_abnt_modelo_dinamico_novo (1).zip` | 2 | 1111368 | nome-fragmento+entrada-pendente+overlay-zip |
| `Incluir/pacote_submissao_abnt_modelo_dinamico_novo (2).zip` | 2 | 1111368 | nome-fragmento+entrada-pendente+overlay-zip |
| `Incluir/pacote_submissao_abnt_modelo_dinamico_novo.zip` | 2 | 1111368 | entrada-pendente+overlay-zip |
| `Incluir/rafaelia_anterioridade_pack.zip` | 2 | 9042 | entrada-pendente+overlay-zip |
| `Incluir/rafaelia_rigor_pipeline.py` | 2 | 22480 | entrada-pendente |
| `Incluir/regime_map_alpha_lambda.png` | 2 | 64409 | entrada-pendente |
| `Incluir/repo_audit_and_plan.py` | 2 | 3591 | entrada-pendente |
| `Incluir/report.md` | 2 | 7494 | entrada-pendente |
| `Incluir/sensitivity_mean_S.png` | 2 | 54004 | entrada-pendente |
| `Incluir/sessao_completa_possibilidades_e_matematica.md` | 2 | 24082 | entrada-pendente |
| `Incluir/sistema_minimo_invariantes (1).md` | 2 | 6954 | nome-fragmento+entrada-pendente |
| `Incluir/sistema_minimo_invariantes (1).xlsx` | 2 | 18409 | nome-fragmento+entrada-pendente |
| `Incluir/sistema_minimo_invariantes.md` | 2 | 4396 | entrada-pendente |
| `Incluir/sistema_minimo_invariantes.xlsx` | 2 | 15789 | entrada-pendente |
| `Incluir/submissao_modelo_dinamico_abnt (1).zip` | 2 | 3937436 | nome-fragmento+entrada-pendente+overlay-zip |
| `Incluir/submissao_modelo_dinamico_abnt.zip` | 2 | 3937436 | entrada-pendente+overlay-zip |
| `Incluir/t7_invariant_engine.py` | 2 | 4858 | entrada-pendente |
| `Incluir/termux-app-rafacodephi-master.zip` | 2 | 2259663 | entrada-pendente+overlay-zip |
| `Incluir/test_repo_audit_and_plan.py` | 2 | 376 | entrada-pendente |
| `Incluir/test_t7_invariant_engine.py` | 2 | 1258 | entrada-pendente |
| `Incluir/tinygpt_c_asm_like_nanogpt_src.zip` | 2 | 453508 | entrada-pendente+overlay-zip |
| `Incluir/tres_md_malha_simbiotica.zip` | 2 | 15091 | entrada-pendente+overlay-zip |
| `Incluir/tue_apr_21_2026_comprehensive_documentation_for_vectras_vm_android.json` | 2 | 392786 | entrada-pendente |
| `Incluir/tue_apr_21_2026_repository_analysis_and_bug_report_summary.md` | 2 | 23765 | entrada-pendente |
| `Incluir/vectras_bbs.c` | 2 | 23090 | entrada-pendente |
| `LICENSES_REGISTER.md` | 1 | 1179 | raiz/doc-ou-dado-solto |
| `PRIVACY.md` | 1 | 3542 | raiz/doc-ou-dado-solto |
| `PROJECT_STATE.md` | 1 | 3192 | raiz/doc-ou-dado-solto |
| `PROVISIONING_REPORT_2026-04-03.md` | 1 | 1660 | raiz/doc-ou-dado-solto |
| `RAFAELMELOREIS/MIT/NAOCOMERCIAL/ChatGPT Image 10 de mai. de 2026, 20_59_55.png` | 4 | 1855817 | nome-fragmento |
| `README.md` | 1 | 19908 | raiz/doc-ou-dado-solto |
| `RELEASE_NOTES.md` | 1 | 2335 | raiz/doc-ou-dado-solto |
| `REPORT.md` | 1 | 849 | raiz/doc-ou-dado-solto |
| `SECURITY.md` | 1 | 4667 | raiz/doc-ou-dado-solto |
| `START_HERE.md` | 1 | 3285 | raiz/doc-ou-dado-solto |
| `THIRD_PARTY_NOTICES.md` | 1 | 1345 | raiz/doc-ou-dado-solto |
| `TROUBLESHOOTING.md` | 1 | 2380 | raiz/doc-ou-dado-solto |
| `VECTRAS_MEGAPROMPT_DOCS.md` | 1 | 28273 | raiz/doc-ou-dado-solto |
| `VECTRA_CORE.md` | 1 | 13014 | raiz/doc-ou-dado-solto |
| `VERSION_STABILITY.md` | 1 | 5421 | raiz/doc-ou-dado-solto |
| `WIP_INTEGRACAO_MVPS.md` | 1 | 404 | raiz/doc-ou-dado-solto |
| `__DELTA__/CMAKE_ANDROID_SNIPPET.txt` | 2 | 42 | entrada-pendente |
| `__DELTA__/CMAKE_ROOT_SNIPPET.txt` | 2 | 28 | entrada-pendente |
| `__DELTA__/PATCH_NOTES.md` | 2 | 802 | entrada-pendente |
| `__DELTA__/rrrrr` | 2 | 11435 | entrada-pendente |
| `_incoming/INTEGRATION_EVIDENCE.md` | 2 | 3299 | entrada-pendente |
| `_incoming/README.md` | 2 | 3705 | entrada-pendente |
| `_incoming/pending/Android_nomalloc.mk` | 3 | 1865 | entrada-pendente |
| `_incoming/pending/Application.mk` | 3 | 498 | entrada-pendente |
| `_incoming/pending/RAFAELIA_MATH_FORMULAS.md` | 3 | 6789 | entrada-pendente |
| `_incoming/pending/RafaeliaCore.java` | 3 | 4836 | entrada-pendente |
| `_incoming/pending/baremetal_nomalloc.c` | 3 | 25208 | entrada-pendente |
| `_incoming/pending/baremetal_nomalloc.h` | 3 | 5536 | entrada-pendente |
| `_incoming/pending/bitraf64_prototype_Version4.py` | 3 | 3363 | entrada-pendente |
| `_incoming/pending/bitstack.c` | 3 | 1813 | entrada-pendente |
| `_incoming/pending/bitstack.h` | 3 | 809 | entrada-pendente |
| `_incoming/pending/build_all.sh` | 3 | 2270 | entrada-pendente |
| `_incoming/pending/diagnose.sh` | 3 | 10867 | entrada-pendente |
| `_incoming/pending/diagnose_termux.sh` | 3 | 3830 | entrada-pendente |
| `_incoming/pending/hyperforms.json` | 3 | 3499 | entrada-pendente |
| `_incoming/pending/r.S` | 3 | 1826 | entrada-pendente |
| `_incoming/pending/raf_asm_b1.S` | 3 | 4113 | entrada-pendente |
| `_incoming/pending/rafaelia_10x10.S` | 3 | 1538 | entrada-pendente |
| `_incoming/pending/rafaelia_7d.S` | 3 | 1210 | entrada-pendente |
| `_incoming/pending/rafaelia_7d_gyro.S` | 3 | 1233 | entrada-pendente |
| `_incoming/pending/rafaelia_7d_shapes.S` | 3 | 1266 | entrada-pendente |
| `_incoming/pending/rafaelia_8way.S` | 3 | 1011 | entrada-pendente |
| `_incoming/pending/rafaelia_936_fast.S` | 3 | 1271 | entrada-pendente |
| `_incoming/pending/rafaelia_999_logsin.S` | 3 | 1247 | entrada-pendente |
| `_incoming/pending/rafaelia_abs.S` | 3 | 1499 | entrada-pendente |
| `_incoming/pending/rafaelia_arena.h` | 3 | 2133 | entrada-pendente |
| `_incoming/pending/rafaelia_avalanche.S` | 3 | 1444 | entrada-pendente |
| `_incoming/pending/rafaelia_avalanche_v2.S` | 3 | 1310 | entrada-pendente |
| `_incoming/pending/rafaelia_b1.S` | 3 | 15252 | entrada-pendente |
| `_incoming/pending/rafaelia_b2.S` | 3 | 9331 | entrada-pendente |
| `_incoming/pending/rafaelia_b3.S` | 3 | 8848 | entrada-pendente |
| `_incoming/pending/rafaelia_b4.S` | 3 | 11403 | entrada-pendente |
| `_incoming/pending/rafaelia_b5.S` | 3 | 28713 | entrada-pendente |
| `_incoming/pending/rafaelia_b6.S` | 3 | 17030 | entrada-pendente |
| `_incoming/pending/rafaelia_b7.S` | 3 | 18557 | entrada-pendente |
| `_incoming/pending/rafaelia_b8.S` | 3 | 13499 | entrada-pendente |
| `_incoming/pending/rafaelia_bench_phi.S` | 3 | 1461 | entrada-pendente |
| `_incoming/pending/rafaelia_bitraf.c` | 3 | 9357 | entrada-pendente |
| `_incoming/pending/rafaelia_central_link.S` | 3 | 1308 | entrada-pendente |
| `_incoming/pending/rafaelia_chrono.S` | 3 | 1221 | entrada-pendente |
| `_incoming/pending/rafaelia_concepts_refactor.c` | 3 | 5862 | entrada-pendente |
| `_incoming/pending/rafaelia_core.c` | 3 | 14758 | entrada-pendente |
| `_incoming/pending/rafaelia_decision.S` | 3 | 1142 | entrada-pendente |
| `_incoming/pending/rafaelia_delta.S` | 3 | 1589 | entrada-pendente |
| `_incoming/pending/rafaelia_equitas.S` | 3 | 1482 | entrada-pendente |
| `_incoming/pending/rafaelia_final.S` | 3 | 1263 | entrada-pendente |
| `_incoming/pending/rafaelia_final_bench.S` | 3 | 1340 | entrada-pendente |
| `_incoming/pending/rafaelia_final_seal.S` | 3 | 1194 | entrada-pendente |
| `_incoming/pending/rafaelia_fix.S` | 3 | 1249 | entrada-pendente |
| `_incoming/pending/rafaelia_flash.S` | 3 | 1113 | entrada-pendente |
| `_incoming/pending/rafaelia_flops.S` | 3 | 1221 | entrada-pendente |
| `_incoming/pending/rafaelia_fractal.S` | 3 | 1157 | entrada-pendente |
| `_incoming/pending/rafaelia_glue.c` | 3 | 18706 | entrada-pendente |
| `_incoming/pending/rafaelia_gpu_mid.c` | 3 | 7446 | entrada-pendente |
| `_incoming/pending/rafaelia_gpu_mid.h` | 3 | 1869 | entrada-pendente |
| `_incoming/pending/rafaelia_hold.S` | 3 | 1182 | entrada-pendente |
| `_incoming/pending/rafaelia_hw_sync.S` | 3 | 1132 | entrada-pendente |
| `_incoming/pending/rafaelia_integrity.S` | 3 | 1283 | entrada-pendente |
| `_incoming/pending/rafaelia_invasion.S` | 3 | 1216 | entrada-pendente |
| `_incoming/pending/rafaelia_jni_direct.c` | 3 | 11138 | entrada-pendente |
| `_incoming/pending/rafaelia_l2.S` | 3 | 932 | entrada-pendente |
| `_incoming/pending/rafaelia_life.S` | 3 | 1675 | entrada-pendente |
| `_incoming/pending/rafaelia_master.sh` | 3 | 45802 | entrada-pendente |
| `_incoming/pending/rafaelia_next.S` | 3 | 1249 | entrada-pendente |
| `_incoming/pending/rafaelia_orchestrator.c` | 3 | 32435 | entrada-pendente |
| `_incoming/pending/rafaelia_pilar.S` | 3 | 1634 | entrada-pendente |
| `_incoming/pending/rafaelia_prime.S` | 3 | 1350 | entrada-pendente |
| `_incoming/pending/rafaelia_prob.S` | 3 | 1225 | entrada-pendente |
| `_incoming/pending/rafaelia_pure_asm.c` | 3 | 2311 | entrada-pendente |
| `_incoming/pending/rafaelia_sigma_omega.c` | 3 | 13714 | entrada-pendente |
| `_incoming/pending/rafaelia_sin_log.S` | 3 | 1210 | entrada-pendente |
| `_incoming/pending/rafaelia_stabile.S` | 3 | 1365 | entrada-pendente |
| `_incoming/pending/rafaelia_toro.S` | 3 | 990 | entrada-pendente |
| `_incoming/pending/rafaelia_torus.S` | 3 | 1525 | entrada-pendente |
| `_incoming/pending/rafaelia_ttl` | 3 | 410520 | entrada-pendente |
| `_incoming/pending/rafaelia_ttl.S` | 3 | 1052 | entrada-pendente |
| `_incoming/pending/rafaelia_types.h` | 3 | 3381 | entrada-pendente |
| `_incoming/pending/rafaelia_ultra.S` | 3 | 1949 | entrada-pendente |
| `_incoming/pending/rafaelia_vacuo.S` | 3 | 1390 | entrada-pendente |
| `_incoming/pending/rafaelia_vacuo_v2.S` | 3 | 1200 | entrada-pendente |
| `_incoming/pending/rafaelia_vortex.S` | 3 | 1306 | entrada-pendente |
| `_incoming/pending/rafaelia_vortex_live.S` | 3 | 1155 | entrada-pendente |
| `_incoming/pending/rafaelia_vortex_v2.S` | 3 | 1282 | entrada-pendente |
| `_incoming/pending/rmr_final.S` | 3 | 1085 | entrada-pendente |
| `_incoming/pending/rmr_hidden.S` | 3 | 1134 | entrada-pendente |
| `_incoming/pending/rmr_matrix.S` | 3 | 1170 | entrada-pendente |
| `_incoming/pending/rmr_nihil.S` | 3 | 1871 | entrada-pendente |
| `_incoming/pending/rmr_persist.S` | 3 | 1328 | entrada-pendente |
| `_incoming/pending/rmr_spiral.S` | 3 | 1155 | entrada-pendente |
| `_incoming/pending/rmr_test.c` | 3 | 6433 | entrada-pendente |
| `_incoming/pending/termux_arm32_build.sh` | 3 | 37341 | entrada-pendente |
| `_incoming/rafaelia_arm.c` | 2 | 1726 | entrada-pendente |
| `_incoming/rafaelia_arm32.c` | 2 | 1229 | entrada-pendente |
| `_incoming/rafaelia_bare.c` | 2 | 2319 | entrada-pendente |
| `_incoming/rafaelia_clock.c` | 2 | 1837 | entrada-pendente |
| `_incoming/rafaelia_clock_fix.c` | 2 | 1705 | entrada-pendente |
| ... | ... | ... | 23 itens adicionais omitidos para manter o índice legível |
