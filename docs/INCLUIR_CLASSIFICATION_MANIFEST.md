# Incluir/ — Classification Manifest
<!-- Gerado: 2026-07-21 — fecha gap G24 (P1) -->
<!-- Escopo: 114 arquivos aguardando triagem/promocao desde 2026-06-05 -->

Este manifesto classifica todos os 114 arquivos de `Incluir/` em categorias
acionáveis. Cada categoria tem um dono e uma ação concreta.

**Status de fechamento de G24:** PARCIAL → FECHADO após commit deste arquivo.
O manifesto estabelece a cadeia de custódia; promoções individuais ocorrem
conforme cada owner confirma.

---

## Sumário por categoria

| Cat | Nome | Arquivos | Ação | Owner |
|-----|------|---------|------|-------|
| A | Artigos e papers ABNT | 27 | Mover para `docs/research/` | Rafael |
| B | Código-fonte C/ASM | 14 | Revisar e promover para `engine/rmr/` ou `_incoming/` | IA |
| C | Arquivos ZIP — C/ASM ARM32 | 9 | Extrair, auditar, promover ou arquivar | IA/Rafael |
| D | Scripts Python | 13 | Promover para `tools/` ou `docs/research/` | IA |
| E | Componentes UI (JSX) | 5 | Avaliar integração no app ou arquivar | Rafael |
| F | Scripts Shell e patches de fix | 12 | Aplicar pendentes ou arquivar como historico | IA/Rafael |
| G | Documentação e relatórios | 19 | Mover para `docs/` ou arquivar | IA |
| H | Imagens e ativos visuais | 5 | Mover para `docs/assets/` ou arquivar | Rafael |
| I | ZIPs mistos (runtime/pacotes) | 9 | Auditar conteúdo e registrar em ASSET_PROVENANCE_REGISTER | Rafael |
| J | Build / Makefile | 1 | Avaliar integração | IA |

---

## Categoria A — Artigos, papers e documentos acadêmicos ABNT

**Ação recomendada:** `mv Incluir/<arquivo> docs/research/`  
**Owner:** Rafael (conteúdo autoral acadêmico)

| Arquivo | Tipo | Notas |
|---------|------|-------|
| `01_Rafaelia_Execucao_Saneada.docx` | Word | Paper base revisado |
| `01_taxonomia_academica_malha_simbiotica.md` | Markdown | Taxonomia acadêmica |
| `02_Paper_Base_Completo.docx` | Word | Paper base completo |
| `02_formalizacao_matematica_modelo_de_estados.md` | Markdown | Formalização matemática |
| `03_paper_base_programa_interdisciplinar.md` | Markdown | Programa interdisciplinar |
| `09_Linha_de_Artigos.docx` | Word | Linha de artigos |
| `11_Artigo_2_Experimental.docx` | Word | Artigo experimental |
| `A incluir.md` | Markdown | Lista pendente de inclusões |
| `Mais.md` | Markdown | Notas diversas |
| `Possível núcleo.md` | Markdown | Rascunho de arquitetura |
| `Rafael estados.md` | Markdown | Modelo de estados |
| `Rafaeltesesmd.md` | Markdown | Teses |
| `sessao_completa_possibilidades_e_matematica.md` | Markdown | Sessão matemática |
| `sistema_minimo_invariantes (1).md` | Markdown | Duplicata — remover após validar |
| `sistema_minimo_invariantes.md` | Markdown | Invariantes do sistema |
| `sistema_minimo_invariantes (1).xlsx` | Excel | Duplicata — remover após validar |
| `sistema_minimo_invariantes.xlsx` | Excel | Planilha de invariantes |
| `UNIFIED_INVARIANT_SPEC.md` | Markdown | Spec de invariantes |
| `pacote_1_2_3_malha_simbiotica.zip` | ZIP papers | Malha simbiótica vols 1-3 |
| `pacote_submissao_abnt_modelo_dinamico.zip` | ZIP papers | Submissão ABNT |
| `pacote_submissao_abnt_modelo_dinamico_novo (1).zip` | ZIP papers | Duplicata — confirmar antes de remover |
| `pacote_submissao_abnt_modelo_dinamico_novo (2).zip` | ZIP papers | Duplicata — confirmar antes de remover |
| `pacote_submissao_abnt_modelo_dinamico_novo.zip` | ZIP papers | Nova versão do pacote ABNT |
| `submissao_modelo_dinamico_abnt (1).zip` | ZIP papers | Duplicata — confirmar antes de remover |
| `submissao_modelo_dinamico_abnt.zip` | ZIP papers | Submissão modelo dinâmico |
| `tres_md_malha_simbiotica.zip` | ZIP papers | 3 MDs da malha simbiótica |
| `Rafaelia_Execucao_Kit_V2_ABNT_Resultados_Artigos.zip` | ZIP papers | Kit execução + artigos ABNT |

---

## Categoria B — Código-fonte C e cabeçalhos para engine/rmr

**Ação recomendada:** Revisar cada arquivo; promover para `engine/rmr/` se
compilável e autoral, ou para `_incoming/pending/` se pendente de decisão.  
**Owner:** IA (revisão técnica) + Rafael (confirmação de autoria/licença)

| Arquivo | Destino sugerido | Dependências conhecidas |
|---------|-----------------|------------------------|
| `bench_logico_vs_fisico_armv7.c` | `engine/rmr/bench/` | ARM v7 instrínsecos |
| `lb_tables.h` | `engine/rmr/include/` | Lookup tables |
| `omega_forest.c` | `engine/rmr/omega/` | `omega_layersbit.h` |
| `omega_frames_export.c` | `engine/rmr/omega/` | frames_seed |
| `omega_kernel_v2.c` | `engine/rmr/omega/` | v2 — verificar se v3 supera |
| `omega_kernel_v3.c` | `engine/rmr/omega/` | v3 — versão mais recente |
| `omega_layersbit.c` | `engine/rmr/omega/` | `omega_layersbit.h` |
| `omega_layersbit.h` | `engine/rmr/omega/` | Cabeçalho público |
| `omega_neuro_full.c` | `engine/rmr/omega/` | Rede neural mínima |
| `raf_arena_format.h` | `engine/rmr/include/` | Arena memory header |
| `skill_baremetal.c` | `engine/rmr/skills/` | Baremetal runtime |
| `skill_rafaelia_core.c` | `engine/rmr/skills/` | Core skill runtime |
| `torus_path_sweep.c` | `engine/rmr/math/` | Geometria toroidal |
| `vectras_bbs.c` | `engine/rmr/core/` | BBS (bulletin board system?) |

---

## Categoria C — ZIPs de código C/ASM ARM32

**Ação recomendada:** Extrair cada ZIP; auditar conteúdo; registrar SHA-256 em
`resources/compliance/ASSET_PROVENANCE_REGISTER.csv`; promover código revisado
para `_incoming/pending/` (categoria benchmarks ou math).  
**Owner:** IA (extração e auditoria técnica) + Rafael (confirmação de autoria)

| Arquivo | Conteúdo presumido | Tamanho |
|---------|-------------------|---------|
| `RAF_C_ASM_ARM32_HotPath_N55_56Ciclos.zip` | Hot path ARM32 56 ciclos | — |
| `RAF_C_ASM_ARM32_Pipeline_FrameCacheTelemetry_v5.zip` | Pipeline telemetria v5 | — |
| `RAF_C_ASM_ARM32_Pipeline_v6_TraceFaultTarget.zip` | Pipeline trace fault v6 | — |
| `RAF_C_ASM_ARM32_Pipeline_v7_ProtoCRCTrace.zip` | Pipeline CRC trace v7 | — |
| `RAF_C_ASM_ARM32_Pipeline_v8_ProtoZeroCopyReport.zip` | Zero-copy report v8 | — |
| `RAF_C_ASM_Solido_ARM32_ARMv7.zip` | Sólido ARM32/ARMv7 | — |
| `RAF_C_ASM_Solido_x86_64.zip` | Sólido x86_64 | — |
| `arm32_nanogpt_c_asm_sem_dependencias.zip` | nanoGPT ARM32 sem deps | — |
| `tinygpt_c_asm_like_nanogpt_src.zip` | tinyGPT ARM32 | — |

---

## Categoria D — Scripts Python

**Ação recomendada:** Promover para `tools/` (ferramentas) ou
`docs/research/` (análise acadêmica); adicionar testes onde faltam.  
**Owner:** IA

| Arquivo | Função | Destino |
|---------|--------|---------|
| `matrix_ops.py` | Operações matriciais | `tools/math/` |
| `matrix_ops (1).py` | Duplicata — manter a mais recente | arquivar |
| `raf_arena_append.py` | Arena memory append | `tools/arena/` |
| `raf_arena_tool.py` | Arena memory tooling | `tools/arena/` |
| `raf_slice_registration.py` | Registro de slices | `tools/arena/` |
| `raf_toroide_field_derivation.py` | Derivação campo toroidal | `tools/math/` |
| `raf_toroide_grafo.py` | Grafo toroidal | `tools/math/` |
| `rafaelia_rigor_pipeline.py` | Pipeline rigor | `tools/ci/` |
| `repo_audit_and_plan.py` | Auditoria de repo | `tools/audit/` |
| `t7_invariant_engine.py` | Motor de invariantes T7 | `tools/math/` |
| `test_repo_audit_and_plan.py` | Testes de auditoria | `tools/audit/` |
| `test_t7_invariant_engine.py` | Testes invariantes T7 | `tools/math/` |
| `visao_index.py` | Indexador de visão | `tools/vision/` |

---

## Categoria E — Componentes UI (JSX/React)

**Ação recomendada:** Confirmar com Rafael se há frontend React no projeto;
se não, arquivar em `docs/prototypes/ui/`.  
**Owner:** Rafael (decisão de arquitetura UI)

| Arquivo | Componente |
|---------|-----------|
| `ZipRafStore-1.jsx` | Store Zip RAF — variante 1 |
| `ZipRafStore.jsx` | Store Zip RAF — principal |
| `omega-rafaelia-map.jsx` | Mapa Omega-Rafaelia |
| `rafaelia-bridge-ui.jsx` | Bridge UI |
| `rafaelia-ledger.jsx` | Ledger UI |

---

## Categoria F — Scripts shell e patches de fix

**Ação recomendada:** Scripts de fix (`FIX_*`) — verificar se os patches já
foram aplicados ao código-fonte principal; se sim, arquivar como histórico.
Scripts de skill (`*_skill*`, `gen_skill.sh`) — promover para `tools/skills/`.  
**Owner:** IA (verificação) + Rafael (confirmação de aplicação)

| Arquivo | Tipo | Ação |
|---------|------|------|
| `APPLY_FIXES.sh` | Orquestrador de patches | Verificar se ainda necessário |
| `FIX_01_local-1.properties` | Patch local.properties | Verificar se já aplicado |
| `FIX_02_google-services-1.json` | Patch google-services | ATENÇÃO: verificar segredos |
| `FIX_03_sources_rmr_core-1.cmake` | Patch CMake | Verificar se já aplicado |
| `FIX_04_CMakeLists-1.txt` | Patch CMakeLists | Verificar se já aplicado |
| `FIX_05_app_src_main_cpp_CMakeLists-1.txt` | Patch CMakeLists JNI | Verificar se já aplicado |
| `FIX_06_CMakePresets-1.json` | Patch CMakePresets | Verificar se já aplicado |
| `FIX_07_rmr_unified_jni_base-1.h` | Patch header JNI | Verificar se já aplicado |
| `gen_skill.sh` | Gerador de skills | `tools/skills/` |
| `instalar_skills.sh` | Instalador de skills | `tools/skills/` |
| `raf_skill_forge.sh` | Forge de skills | `tools/skills/` |
| `update_frames.sh` | Atualizador de frames | `tools/omega/` |

> ⚠️ `FIX_02_google-services-1.json`: verificar se contém credenciais Firebase
> reais antes de qualquer commit fora de `Incluir/`. Mesmo que seja placeholder,
> confirmar antes de promover.

---

## Categoria G — Documentação e relatórios

**Ação recomendada:** Mover para `docs/` ou `docs/reports/`.  
**Owner:** IA

| Arquivo | Tipo | Destino |
|---------|------|---------|
| `AUDIT_REPORT.json` | JSON | `docs/reports/` |
| `AUDIT_REPORT.md` | Markdown | `docs/reports/` |
| `CODEX_PROMPT_VECTRAS_TERMUX_ARM32_CI.txt` | Prompt CI | `docs/prompts/` |
| `EXECUTION_REPORT.md` | Markdown | `docs/reports/` |
| `FILE_ORGANIZATION.md` | Markdown | `docs/` |
| `INTEGRATION_STATUS.md` | Markdown | `docs/` |
| `README.md` | Markdown | Consolidar com root README |
| `Readme.md` | Markdown | Duplicata de README.md |
| `_VECTRAS_REPOSITORY_OVERVIEW.md` | Markdown | `docs/` |
| `forest.jsonl` | JSONL | `docs/research/data/` |
| `frames_seed.json` | JSON | `docs/research/data/` (ver gap G20) |
| `report.md` | Markdown | `docs/reports/` |
| `skill_rafaelia_core.md` | Markdown | `docs/skills/` |
| `table_20260707.csv` | CSV | `docs/research/data/` |
| `torus_path_sweep_sample_output.csv` | CSV | `docs/research/data/` |
| `Texto colado.txt` | Texto | Revisar e integrar ou remover |
| `tue_apr_21_2026_comprehensive_documentation_for_vectras_vm_android.json` | JSON | `docs/reports/` |
| `tue_apr_21_2026_repository_analysis_and_bug_report_summary.md` | Markdown | `docs/reports/` |
| `omega_frames_export.c` | C (também cat B) | ver Categoria B |

---

## Categoria H — Imagens e ativos visuais

**Ação recomendada:** Mover para `docs/assets/` com proveniência registrada.  
**Owner:** Rafael (confirmar autoria/fonte das imagens)

| Arquivo | Conteúdo | Origem provável |
|---------|----------|----------------|
| `ChatGPT Image 22 de abr. de 2026, 08_01_26.png` | Imagem AI | ChatGPT/DALL-E — licença a confirmar |
| `fit_coherence_C.png` | Gráfico de coerência | Gerado por script local |
| `fit_timeseries_S.png` | Gráfico de série temporal | Gerado por script local |
| `regime_map_alpha_lambda.png` | Mapa de regime | Gerado por script local |
| `sensitivity_mean_S.png` | Mapa de sensibilidade | Gerado por script local |

> ⚠️ `ChatGPT Image`: imagens geradas por DALL-E têm licença de uso pessoal/comercial
> condicionada aos ToS da OpenAI. Confirmar se pode ser distribuída com o projeto.

---

## Categoria I — ZIPs mistos (runtime, pacotes, ferramentas)

**Ação recomendada:** Extrair e auditar; registrar em
`resources/compliance/ASSET_PROVENANCE_REGISTER.csv` com SHA-256.  
**Owner:** Rafael (decisão sobre inclusão no APK/release)

| Arquivo | Conteúdo presumido | Risco |
|---------|-------------------|-------|
| `Rafaelia_Execucao_Kit (1).zip` | Kit de execução — duplicata | Confirmar antes de remover |
| `Rafaelia_Execucao_Kit.zip` | Kit de execução | Verificar conteúdo |
| `Rafaelia_Execucao_Kit_Completo.zip` | Kit completo | Verificar conteúdo |
| `glmm_em_zip.zip` | GLMM (modelo estatístico) | Verificar licença |
| `modelo_dinamico_pacote.zip` | Pacote modelo dinâmico | Verificar conteúdo |
| `rafaelia-skills-install.tar.gz` | Skills de instalação | Verificar scripts |
| `rafaelia_anterioridade_pack.zip` | Pack de anterioridade | Documento legal |
| `termux-app-rafacodephi-master.zip` | Snapshot do termux-app | Redundante (repo existe) |
| `visao-template.tar.gz` | Template de visão | Verificar conteúdo |

---

## Categoria J — Build / Makefile

**Ação recomendada:** Verificar se é complementar ao Gradle/CMake principal.  
**Owner:** IA

| Arquivo | Tipo | Ação |
|---------|------|------|
| `Makefile` | GNU Make | Verificar targets; integrar ou documentar separação de responsabilidades |

---

## Status de promoções realizadas antes deste manifesto

| Arquivo | Destino | PR |
|---------|---------|----|
| `_incoming/termux.c` | `app/src/main/cpp/termux_jni.c` | #1038 |
| `_incoming/pending/*.S` (51 arquivos) | `CLASSIFICATION_MANIFEST.md` criado; movimento aguarda owner | — |

---

## Próximos passos por prioridade

1. **Imediato (IA):** Promover scripts Python úteis para `tools/` (Categoria D)
2. **Imediato (IA):** Mover documentação para `docs/` (Categoria G)
3. **Rafael:** Confirmar autoria dos C/ASM em Categoria B e autorizar promoção para `engine/rmr/`
4. **Rafael:** Verificar se patches FIX_* já foram aplicados (Categoria F)
5. **Rafael:** Confirmar licença da imagem ChatGPT (Categoria H)
6. **Rafael:** Decidir sobre componentes JSX — há frontend React no projeto? (Categoria E)
7. **Rafael:** Extrair e auditar ZIPs da Categoria I; registrar proveniência
8. **Rafael:** Extrair e auditar ZIPs de papers (Categoria A)
