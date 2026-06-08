<!-- DOC_METADATA_SYNC: 2026-06-08 | role: documentation-standards -->

# Vectras VM – Documentation Standards

## Metadados canônicos

- Versão do documento: 2.1.
- Última revisão: 2026-06-08.
- Escopo: padrões de documentação, metadados canônicos, rastreabilidade, links internos, versionamento e regras de revisão para build/workflow/release.
- Status: canônico vigente de governança documental.
- Commit de referência: `HEAD`.
- Fonte de verdade relacionada: [`../DOC_INDEX.md`](../DOC_INDEX.md), [`README.md`](README.md), [`navigation/INDEX.md`](navigation/INDEX.md) e [`ci/workflow-matrix.md`](ci/workflow-matrix.md).

> **Objetivo / Purpose**: Padronizar a documentação técnica, acadêmica e legal, garantindo clareza, navegabilidade e conformidade autoral.

## 1) Estrutura Obrigatória / Required Structure

Cada documento técnico deve conter, quando aplicável:

- **Título**
- **Resumo** (2–4 frases)
- **Escopo** (o que cobre e o que não cobre)
- **Público-alvo**
- **Conteúdo principal** (com seções e subtítulos claros)
- **Referências** (links internos/externos relevantes)
- **Metadados canônicos** (versão, última revisão, escopo, status, commit de referência e fonte de verdade relacionada)

## 2) Bloco padrão de metadados canônicos

Todo documento canônico deve declarar, logo após o H1, um bloco `## Metadados canônicos` com estes campos, nesta ordem:

```markdown
## Metadados canônicos

- Versão do documento: <major.minor>.
- Última revisão: <YYYY-MM-DD da revisão real>.
- Escopo: <o que o documento cobre e o que não pretende cobrir>.
- Status: <canônico vigente | canônico técnico | legado | experimental | arquivado | bloqueado>.
- Commit de referência: `HEAD`.
- Fonte de verdade relacionada: <links internos para documentos, workflows, scripts ou módulos que sustentam este documento>.
```

Regras de uso:

- `Commit de referência` pode usar `HEAD`, uma tag de release ou um SHA curto, conforme a regra operacional da seção 7.1.
- **Não atualizar data por conveniência**: `Última revisão` só muda quando o conteúdo for inspecionado e revisado de fato.
- **Rastreabilidade antes de fluidez**: `Fonte de verdade relacionada` deve apontar para links internos verificáveis, não para memória oral ou inferência implícita.
- **Status explícito**: documentos legados, experimentais, históricos ou bloqueados devem dizer isso no bloco; não deixar o leitor humano ou agente de IA inferir.
- **Coerência entre documentos**: quando um documento canônico aponta para outro, revisar ambos se a mudança alterar escopo, status, workflow, build, release, ABI, signing, artefatos ou navegação.
- **Mudança em workflow/build/release exige revisão dos metadados dos documentos afetados**, incluindo pelo menos os guias de build/release, matriz de CI, índices de navegação e estado do projeto quando a mudança alterar comportamento operacional.

## 3) Normas de Escrita / Writing Standards

- **Linguagem clara** e consistente (evitar ambiguidade).
- Usar **termos técnicos padronizados** conforme [docs/GLOSSARY.md](GLOSSARY.md).
- Preferir **voz ativa** e frases objetivas.
- Evitar jargão sem explicação.

## 4) Navegação e Acessibilidade

- Incluir **sumário** quando o documento for longo.
- Garantir links funcionais e caminhos relativos corretos.
- Usar **títulos hierárquicos** (H1 → H2 → H3) sem saltos.
- Imagens devem ter **texto alternativo** quando possível.

## 5) Referências e Citações

- Qualquer referência acadêmica deve estar registrada em [docs/BIBLIOGRAPHY.md](BIBLIOGRAPHY.md).
- Informações derivadas de terceiros devem ser citadas com **URL e data** quando apropriado.
- Evite copiar conteúdo protegido sem autorização.

## 6) Licenças e Direitos Autorais

- Preservar avisos de copyright e licenças.
- Incluir a licença aplicável ao documento (geralmente GPL-2.0).
- Seguir as diretrizes de [docs/LEGAL_AND_LICENSES.md](LEGAL_AND_LICENSES.md).

## 7) Versionamento e Controle de Mudanças

- Atualizar a seção **Metadados canônicos** ao modificar o documento.
- Manter um **Change Log** se o documento for crítico (ex.: arquitetura, compliance).
- Registrar saneamentos de links internos em `CHANGELOG.md` e, quando aplicável, no documento de governança correspondente.
- Alinhar versões com a versão do projeto quando possível.
- Toda mudança em caminhos críticos (app, engine, tools, web, runtime e docs de governança) deve atualizar os metadados e os links de rastreabilidade do documento correspondente.

### 7.1) Regra operacional para metadados de rastreabilidade

- Atualização **obrigatória em toda PR que altere qualquer arquivo em `docs/`**.
- `Commit de referência` / commit-referência:
  - usar o **commit atual de `HEAD`** quando a revisão documental for publicada sem tag de release.
  - usar a **tag de release** (e o commit apontado por ela) quando a documentação fizer parte de corte formal de versão.
- `Última revisão`: usar data ISO (`YYYY-MM-DD`) da revisão efetiva da PR; não atualizar data sem inspeção real do conteúdo afetado.
- `Versão do documento`: incrementar em `+0.1` para ajustes editoriais/estruturais e em `+1.0` para reestruturação completa de escopo.
- Em documentos relacionados (ex.: `docs/README.md`, `docs/navigation/INDEX.md` e guias de navegação vinculados), atualizar metadados em bloco na mesma PR para manter coerência de rastreabilidade.
- Em CI, executar `tools/check_docs_reference_commit.sh`; a pipeline deve falhar quando qualquer `Commit de referência` divergir do commit alvo de publicação (`DOCS_TARGET_COMMIT`, `GITHUB_SHA` ou `HEAD`).

## 8) Padrões para Arquivos de Navegação

- Atualizar [docs/README.md](README.md) e [docs/navigation/INDEX.md](navigation/INDEX.md) ao criar ou mover documentos.
- Evitar duplicidade de índices; centralizar a navegação no `docs/README.md`.

---

## 9) Naming Canônico de Arquivos de Navegação

- O nome canônico para arquivos de entrada em diretórios é **`README.md`** (maiúsculo).
- Não usar variantes como `readme.md`, `Readme.md` ou equivalentes.
- Ao renomear ou mover qualquer `README.md`, atualizar imediatamente os links internos em mapas de navegação (ex.: `FILES_MAP.md`, `docs/navigation/INDEX.md` e índices locais).
- Em sistemas case-sensitive, considerar divergência de caixa como quebra de navegação e tratar como regressão documental.

---

## 10) Metadados Mínimos para Novos Diagramas ASCII

Todo novo diagrama ASCII adicionado em `docs/assets/` deve ter metadados mínimos no manifesto único (`docs/assets/MANIFEST.md`) antes de ser referenciado em índices ou documentação:

- `file_name` (nome exato do arquivo versionado)
- `source_url` (URL de origem rastreável)
- `capture_date` (data de captura/importação no formato `YYYY-MM-DD`)
- `sha256` (checksum SHA-256 do arquivo)

Regras adicionais:
- Entradas marcadas como “Provided via chat prompt” **não** são consideradas concluídas sem vínculo rastreável (issue, PR ou artefato versionado com link estável).
- `docs/IMAGES_INDEX.md` deve referenciar apenas itens com metadados completos no manifesto.

---


## 11) Padrão de Sessão de IA para Refatoração Estrutural

Quando a revisão for conduzida por agente de IA com objetivo de estabilização de build/release/CI:

- Executar abordagem de **causa-raiz** (não apenas correção cosmética).
- Registrar no fechamento, de forma objetiva:
  - causas-raiz encontradas;
  - arquivos alterados;
  - comandos executados + resultado;
  - artefatos gerados;
  - bloqueios remanescentes.
- Toda alegação de consistência deve apontar para validação executável (script/comando real do repositório).
- É proibido degradar o caminho oficial de release (assinatura, trilha de publicação, controles de segurança) para “fazer passar”.

---

## Template Rápido / Quick Template

```markdown
# Título

## Resumo

## Escopo

## Público-alvo

## Conteúdo

## Referências

## Metadados canônicos

- Versão do documento:
- Última revisão:
- Escopo:
- Status:
- Commit de referência: `HEAD`
- Fonte de verdade relacionada:
```

---

**Última revisão / Last reviewed**: 2026-06-08

© 2024-2026 Vectras VM Development Team — Licensed under GPL-2.0.

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
