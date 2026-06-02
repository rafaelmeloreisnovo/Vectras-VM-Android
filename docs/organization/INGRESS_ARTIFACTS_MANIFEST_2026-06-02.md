# Manifesto SHA-256 de entradas pendentes e overlays — 2026-06-02

Manifesto gerado para reduzir risco antes de qualquer promoção, extração, movimentação ou remoção de artefatos.

## Escopo

- Inclui todos os arquivos sob `Incluir/`, `_incoming/` e `__DELTA__/`.
- Inclui overlays `*.zip` adicionais encontrados até 5 níveis fora desses diretórios.
- O TSV completo fica em `reports/ingress_artifacts_sha256_2026-06-02.tsv`.

## Resumo

- Total de artefatos com hash: **183**.
- Regra: se o hash mudar antes da promoção, reexecutar auditoria e invalidar decisão anterior.

## Amostra inicial

| Caminho | Bytes | SHA-256 |
|---|---:|---|
| `Incluir/.moveFiles para um su diretório.md` | 1 | `01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b` |
| `Incluir/01_Rafaelia_Execucao_Saneada.docx` | 41885 | `d4b7a82972e88d7b17964a234c88c10d79890d4337cdd83eed1454198b25a8b0` |
| `Incluir/01_taxonomia_academica_malha_simbiotica.md` | 15381 | `dec8c3b8dd038181fcba05f578050a13b60695042b8442fae2a3310aba9cad21` |
| `Incluir/02_Paper_Base_Completo.docx` | 41496 | `b4504fa21d029687d929589b605e00e552ed71b7a6ab272e7f6b10e9282003b1` |
| `Incluir/02_formalizacao_matematica_modelo_de_estados.md` | 9144 | `e1bb538399a1c0bb98db4c8aca390056266f1a6f77bbddcbdf7a58e5400c0a1b` |
| `Incluir/03_paper_base_programa_interdisciplinar.md` | 12231 | `dbdb8470484017c18d23fff35da3e8dd45158463d09d5cf14550b38ebad75f72` |
| `Incluir/09_Linha_de_Artigos.docx` | 37903 | `82211d9ddf8e1c1939b2514d8f707121e69aa653317329cd37556c8261404546` |
| `Incluir/11_Artigo_2_Experimental.docx` | 190288 | `85fbd9e06be4f0ad58a1f813245362cb2069825bfe935cfd956d95ae5998700e` |
| `Incluir/A incluir.md` | 11564 | `f05e4f47e7cf40396672eb6ec06ab65c254f43921efc3b4f1277f9821a2b93c6` |
| `Incluir/APPLY_FIXES.sh` | 9331 | `3c055f82bbfaeae24fef882770b18d4f8c2c6628b91b0effce09ee078a493dc2` |
| `Incluir/AUDIT_REPORT.json` | 162735 | `66a7b11068ffb1e1c378dd30992cdc5adf85f980dd5f80154ea67093fb0fda8c` |
| `Incluir/AUDIT_REPORT.md` | 10407 | `c44640e943c1ebcc2825c8aafc3aba2bfce8fbdf8fc8d8239ebf529dd45eb971` |
| `Incluir/CODEX_PROMPT_VECTRAS_TERMUX_ARM32_CI.txt` | 7997 | `9e76f629b3491a5b8c7ed59c25518ebbd8292ee3c915bbb6cd737aea1517203a` |
| `Incluir/ChatGPT Image 22 de abr. de 2026, 08_01_26.png` | 1317886 | `b19a2f96246142246e3905765702fe7492dd7510cc494502304fb26194b2d7bc` |
| `Incluir/EXECUTION_REPORT.md` | 1361 | `12d599ec81ddb94e41f15b46370f36e1c3d91420cb5656cd75ca09ac29608a73` |
| `Incluir/FILE_ORGANIZATION.md` | 14200 | `ca794dce0170eb0f5ae8e8ea7de91274b43b277756b6aeab84e49710aae89e5a` |
| `Incluir/FIX_01_local-1.properties` | 1409 | `1284d92cc50671170683b81658d70562f0f4b8321d1dddbf3f4a2b736615f82e` |
| `Incluir/FIX_02_google-services-1.json` | 690 | `00807863b96e66a5ea8dd77e394d797d2433612e5382b86f8877b4f8496c4fc2` |
| `Incluir/FIX_03_sources_rmr_core-1.cmake` | 3787 | `659e7d8248028586c146479c149d643c3c647121e72c9580ce8e08aa8d2c465d` |
| `Incluir/FIX_04_CMakeLists-1.txt` | 13542 | `f3f62a4c316f74f308b97e0f4b23db43180fd44527eb94387c9f5cdfa5d55dfc` |
| `Incluir/FIX_05_app_src_main_cpp_CMakeLists-1.txt` | 9688 | `058d5ffdb6464d88b15627d4044ac639b2f69860f7fe6b17f3249c55655b7d5c` |
| `Incluir/FIX_06_CMakePresets-1.json` | 3352 | `fa876d711b102ab5324579ef7078faccb465c581f638a9d0eda7d0cc35d2420d` |
| `Incluir/FIX_07_rmr_unified_jni_base-1.h` | 10064 | `d8b6b925ba3977138f997566c8d847deda4243ea8dba130f8dde0e1b02ed5116` |
| `Incluir/INTEGRATION_STATUS.md` | 1621 | `93f54e2c733455c529fbda7826921c2561e2d457da11474c84229c47d4744632` |
| `Incluir/Mais.md` | 5839 | `522b1f7022d523566bc29254ba80d8d8132ae25122301fbcde7a2da941c8fff4` |
| `Incluir/Possível núcleo.md` | 5839 | `522b1f7022d523566bc29254ba80d8d8132ae25122301fbcde7a2da941c8fff4` |
| `Incluir/RAF_C_ASM_ARM32_HotPath_N55_56Ciclos.zip` | 21845 | `8c058e7ee3aa2424d983e111f54afe9b53d53e709481a3e6265c93956c149255` |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_FrameCacheTelemetry_v5.zip` | 971265 | `c97feaf130c9a8456c85f29b20b873e7077071379a7f83dfbf09db5bf05e6719` |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_v6_TraceFaultTarget.zip` | 942191 | `36c8c3aa25c5ba2b0877731b287d6ba73d44727bdb4a389b43714b1764334bea` |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_v7_ProtoCRCTrace.zip` | 1041203 | `e2a4419b17617f043274981cb69f7e44f3fb9a42d2bef7d8019ebaa27a492051` |
| `Incluir/RAF_C_ASM_ARM32_Pipeline_v8_ProtoZeroCopyReport.zip` | 593126 | `458dc9b79c41f51d7ba1a2f6d62eec69d27e29a946c3275ad026ac53b74c894a` |
| `Incluir/RAF_C_ASM_Solido_ARM32_ARMv7.zip` | 14876 | `25960b077a4e3be87e4127889098427c1e1b4fe5ede4af1b659d141280f4f0ae` |
| `Incluir/RAF_C_ASM_Solido_x86_64.zip` | 21161 | `4820bcc0d9f52216ece2749b0ea3d48fcf63fe4b7e9b74feea248941bb443616` |
| `Incluir/Rafael estados.md` | 15940 | `ef94154f4133d1df1cbf09775482e13666ef4d6bdf09597206816cce44d8806b` |
| `Incluir/Rafaelia_Execucao_Kit (1).zip` | 42667 | `5a91138a822dc5d608f4ee635758d18a6d809fb95724a109fab68b3f864e59dd` |
| `Incluir/Rafaelia_Execucao_Kit.zip` | 42667 | `5a91138a822dc5d608f4ee635758d18a6d809fb95724a109fab68b3f864e59dd` |
| `Incluir/Rafaelia_Execucao_Kit_Completo.zip` | 142367 | `eb5fb60c3e93b404c9ab09d2f0ebe4c4bdbe7ba140eb05029aa5a3dc92012413` |
| `Incluir/Rafaelia_Execucao_Kit_V2_ABNT_Resultados_Artigos.zip` | 1622102 | `7e602fb93bb4b09c83ddacd8ba134d5e41a9640f160e6e2c31f6bb34e9785bd1` |
| `Incluir/Rafaeltesesmd.md` | 8929 | `ca5f7002251c81ed5f5ea8eff73aceea8b7f4c0af423307921fb90f9d4b7e93b` |
| `Incluir/Readme.md` | 1791 | `455bdb337591a52cafdc143eecec1e29c8f977862c46ecb67b0fd392b14f7586` |
| `...` | ... | 143 entradas adicionais no TSV completo. |

## Uso em rollback/failover

1. Antes de promover um arquivo, comparar o hash atual com o TSV.
2. Se a promoção falhar em build/teste, usar o caminho e hash para restaurar o artefato original.
3. Não extrair ZIP em árvore ativa sem manifesto de arquivos internos e teste correspondente.
