# Matriz enterprise de dados necessários, correção e promoção — 2026-06-02

Esta matriz transforma a varredura de 5 níveis em uma rota profissional de entrega: observar, classificar, corrigir, promover, testar e documentar sem remover funcionalidades existentes. Ela complementa a auditoria executável e o manifesto SHA-256.

## Critérios de aceite por lote

| Critério | Obrigatório antes de promover | Failsafe/failover/rollback |
|---|---|---|
| Evidência de origem | Caminho original + SHA-256 no manifesto | Se hash divergir, bloquear promoção e regenerar manifesto |
| Classificação de domínio | App, nativo, assembly, docs, assets, build, CI ou pesquisa | Se domínio incerto, manter em ingresso e criar nota de decisão |
| Teste mínimo | Unitário, lint, build parcial ou justificativa SKIPPED por ambiente | Se falhar, reverter lote e registrar causa em `reports/` |
| Contrato low-level | Sem heap em hot path, sem abstração em `.S`, Q16.16 quando aplicável | Se tocar `.S`, exigir leitura de `VECTRA_OS.md` quando existir |
| Documentação canônica | Link em índice do domínio e substituto para docs obsoletos | Se link quebrar, reverter `git mv` ou criar stub de redirecionamento |

## Mapa fullstack de materiais necessários

| Camada | Dados observados | O que falta/corrigir | Caminho de promoção |
|---|---|---|---|
| Navegação raiz | `README.md`, `START_HERE.md`, `DOC_INDEX.md`, docs canônicos e notas soltas | Separar notas conceituais soltas de documentação operacional | Lote `git mv` para `docs/rafaelia_reference/` ou `archive/root-history/` com aliases |
| Android app | `app/`, `android/`, recursos, testes e fluxo VM/QEMU | Atualizar arquitetura quando módulos Java/Kotlin forem extraídos | Sincronizar `docs/ARCHITECTURE.md` e testes `app/src/test/` |
| Nativo C/JNI | `app/src/main/cpp`, `engine/rmr/include`, `engine/rmr/src` | Mapear cada fórmula executável para header/source/teste real | Promover só com teste C/JNI ou equivalência host |
| Assembly/low-level | `engine/rmr/interop`, `_incoming/pending/*.S`, `Rafaelia/*.S` | Separar protótipos de `.S` dos módulos designados; não tocar x0..x4 fora contrato | Primeiro hash + leitura de contrato + teste ABI; sem BL desconhecido |
| Rust policy | `engine/vectra_policy_kernel/` | Documentar fronteira Rust ↔ C ↔ Java | Rodar testes Rust quando toolchain disponível e atualizar `docs/ARCHITECTURE.md` |
| Ingressos | `Incluir/`, `_incoming/`, `__DELTA__/` | 181 artefatos requerem classificação e decisão de promoção | Usar TSV SHA-256 e lotes pequenos por domínio |
| ZIP/overlays | 27 overlays ZIP detectados | Manifesto interno de arquivos antes de extração | Arquivar ou promover apenas após comparação de árvore e teste |
| Assets/imagens | PNGs soltos e imagens em ingressos | Legenda, proveniência e destino em `docs/assets/` | Mover com manifesto e atualizar `docs/IMAGES_INDEX.md` |
| Relatórios | `reports/`, `bug/`, docs ativos e históricos | Separar evidência histórica de fonte canônica | Rebaixar superados para `docs/archive/` com substituto |
| CI/build | `.github/`, `tools/ci`, scripts Gradle | SDK ausente no ambiente local atual impede build completo | Configurar SDK/NDK e repetir `./build.sh` + `./run_tests.sh` |

## Ordem recomendada de correção

1. **Lote 0 — estabilidade documental**: manter auditoria e manifesto regeneráveis; não mover código.
2. **Lote 1 — docs soltos da raiz**: mover apenas Markdown conceitual para `docs/rafaelia_reference/` ou histórico para `archive/root-history/`.
3. **Lote 2 — assets**: mover capturas/imagens com legenda e atualizar índice de imagens.
4. **Lote 3 — ingressos de documentação**: promover documentos de `Incluir/` que tenham substituto claro; arquivar duplicatas.
5. **Lote 4 — protótipos C/Rust/Java**: promover somente com teste, sem sobrescrever módulos existentes.
6. **Lote 5 — assembly**: revisar separadamente, obedecendo contratos de registradores, macros e terminação.
7. **Lote 6 — overlays ZIP**: extrair em área temporária, comparar, gerar manifesto interno e só então decidir.

## Matriz de teste por tipo de mudança

| Tipo | Teste/check mínimo | Status se ambiente faltar |
|---|---|---|
| Markdown/índice | `npx --yes markdownlint-cli2 ...` | FAIL se lint falhar |
| Auditoria/manifesto | `./tools/docs/audit_documentation_state.py --max-depth 5` | FAIL se não gerar outputs |
| Python tooling | `python3 -m py_compile tools/docs/audit_documentation_state.py` | FAIL se sintaxe quebrar |
| Dependências repo | `python3 tools/verify_repo_file_dependencies.py` | FAIL se arquivo/módulo sumir |
| Pipeline dirs | `./tools/ci/validate_pipeline_directories.sh` | FAIL se contrato quebrar |
| Android build/test | `./build.sh`, `./run_tests.sh` | SKIPPED somente quando SDK/NDK ausente e explicitamente registrado |
| Native/ASM | teste ABI/equivalência aplicável | SKIPPED só com toolchain ausente e risco documentado |

## Dados que ainda faltam para uma entrega enterprise completa

- Manifesto interno de cada ZIP com lista de arquivos, tamanhos e hashes.
- Mapa de duplicatas por hash entre raiz, `Incluir/`, `_incoming/`, `Rafaelia/` e `engine/`.
- Tabela de docs obsoletos com documento substituto e dono de decisão.
- Resultado de build/teste Android em ambiente com SDK/NDK configurado.
- Relatório específico para `.S` pendentes, somente após ler o contrato aplicável e sem alterar assembly nesta etapa.
- Validação de fórmulas RAFAELIA/T7 contra fontes reais antes de qualquer promoção para runtime.

## Decisão desta etapa

A correção aplicada aqui melhora a observabilidade e a organização dos dados necessários, mas **não** promove código nem remove arquivos. Isso preserva funcionalidades existentes enquanto cria evidência suficiente para executar os próximos lotes com rollback e mitigação.
