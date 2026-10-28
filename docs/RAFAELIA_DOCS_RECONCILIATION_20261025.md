# RAFAELIA — Reconciliação documental do Vectras (2026-10-25)

> **Censo completo da raiz:** [arquivos soltos e todos os diretórios de primeiro nível](RAFAELIA_ROOT_CENSUS_20261025.md) — contagem por pasta, SHA Git e locais sem índice. Este mapa complementa o inventário recursivo `tools/` ou `scripts/`; não autoriza exclusões.


> **Estado: DRAFT / HOLD / SOURCE-INDEXED.** Este documento é um sucessor de navegação, não substitui nem apaga `DOC_INDEX.md`, `PROJECT_STATE.md`, `tools/FILES_MAP.md`, políticas de licença ou histórico. O README e os relatórios antigos continuam sendo fontes datadas, não prova automática do HEAD atual.

## Corte de origem e limites

- Base imutável: `master@6e4d99c8d90a3a1ddcb68933c693815d7c6e847b`.
- Enumeração `git/trees?recursive=1` do provedor: **2.791 arquivos**, **433 diretórios**, **606 arquivos de texto/documentos** (por extensão) e `truncated=false`.
- **51 diretórios de primeiro nível**, dos quais **25 não têm** README/INDEX/FILES_MAP diretamente dentro da pasta. *Isso não significa ausência de documentação em outros locais.*
- Em `tools/` foram registrados 468 arquivos e 47 subdiretórios; a varredura conjunta `tools/` + `scripts/` encontra 49 subdiretórios, dos quais 40 sem índice local direto.
- Os números são do corte acima, **não métricas dinâmicas do HEAD futuro**, e arquivos de UI, documentos, mocks e fornecedores não são produtos independentes.

## Navegação revisada por responsabilidade

| Propósito | Referência preservada | Observação operacional |
| --- | --- | --- |
| Visão e autoria do projeto | [README](../README.md) e [CREDITS](../CREDITS_INSPIRATION.md) | Confrontar forks, autoria e licenças por arquivo. |
| Índice anterior | [DOC_INDEX](../DOC_INDEX.md) | Revisão editorial em junho/2026; preservar como predecessor. |
| Estado e bloqueios | [PROJECT_STATE](../PROJECT_STATE.md) | Exigir receipt e SHA exato para alegações atuais. |
| Build/Android | [BUILDING](../BUILDING.md) | Compilação e assinatura não equivalem a instalação em hardware. |
| Ferramentas | [tools/README](../tools/README.md) e [tools/FILES_MAP](../tools/FILES_MAP.md) | O mapa anterior não cobre por si só toda a árvore atual. |
| ABI, ELF e CI | [tools/ci](../tools/ci/) | Scripts e resultados necessitam gate com logs reais. |
| APK e assinatura | [tools/apk](../tools/apk/) | Não publicar nem usar segredos de release automaticamente. |
| Termux ARM64 | [orquestrador](../tools/termux-arm64-orchestrator/) | Hospedado/Shell/Android SDK/NDK são fronteiras externas. |
| Núcleos de baixo nível | [baremetal](../tools/baremetal/) | Auditar imports, `svc`, heap e syscalls individualmente. |
| Máquina virtual | [qemu](../tools/qemu/) | Prova de download/build ≠ guest boot real. |
| Matemática experimental | [state_geometry_lab](../tools/state_geometry_lab/) e [math](../tools/math/) | Fórmulas e descrições não são validação científica. |
| Ferramentas ZIPRAF | [tools/zipraf](../tools/zipraf/) | Separar formato, testes KAT, integridade e segurança. |

## Fila de diretórios sem índice local (amostra objetiva)

`.ci/`, `configs/`, `contracts/`, `core/`, `data/`, `deployment/`, `examples/`, `federation/`, `formula_ci/`, `legal/`, `native/`, `rafaelia-arm32/`, `receipts/`, `schemas/`, `security/`, `tests/`.

Em `tools/`: `arena/`, `audit/`, `bootstrap/`, `ci/`, `compliance/`, `device_runtime/`, `math/`, `promotion/`, `qemu/`, `security/`, `skills/` e subpastas de `state_geometry_lab/`. Esses são **candidatos a índices locais**, não diretórios órfãos comprovados.

## Contrato de atualização documental

1. Inventariar com caminho, Git blob SHA, HEAD, classe, licença e autoridade do produtor.
2. Checar *cada link relativo* e capturar ausência com `TOKEN_VAZIO_MISSING_TARGET`; não corrigir link inventando destino.
3. Marcar documentos antigos como predecessores, usando sucessor + ponteiro, sem exclusão ou edição destrutiva.
4. Separar `SOURCE / ARTIFACT / EXECUTION / EVIDENCE / CLAIM`; nenhum `PASS` de um SHA vale para outro.
5. Para cada ferramenta, declarar: função, entradas/saídas, efeitos colaterais, execução segura, dependências, testes e rollback.
6. Respeitar licença/proveniência de terceiros, privacidade, dados de crianças e revisão humana.
7. Gate P0: teste de links + exatidão do HEAD + revisão humana independente + proteção efetiva no provedor antes de merge.

## Fechamento e falsificadores

**F_ok**: árvore Git enumerada; navegação de alto nível reindexada sem remover precedentes.

**F_gap**: leitura semântica de todos os 2.791 arquivos NÃO REALIZADA; links da documentação histórica e ferramentas executáveis ainda precisam de verificações; compilação, Android físico, segurança e certificação NÃO SÃO ALEGADAS.

**F_next**: gerar manifesto recursivo no repositório, comparar links e referências com o HEAD exato, classificar arquivos sem dono e executar teste negativo de link quebrado; anexar receipt e manter PR em HOLD.

**Rollback**: descartar somente esta branch de revisão (sem apagar branches históricas) ou reverter exclusivamente o novo documento e ponteiro; nunca excluir fontes, licenças ou registros autorais. `claim_allowed=false`.

## Sucessor S2 — capacidade operacional e índice local (2026-10-25)

- [Índice funcional confirmado](../tools/ci/README.md): funções, entradas/saídas documentadas e riscos, sem execução presumida.
- [Verificador determinístico](../tools/doc_ref_integrity_gate.py), [testes negativos](../tools/test_doc_ref_integrity_gate.py) e [workflow de CI](../.github/workflows/rafaelia-docs-integrity-v1.yml).
- O gate fornece estado JSON (PASS_SCOPED/FAIL_SCOPED) para links Markdown locais e censo de diretórios raiz sem índice direto. É **heurístico**, não prova de abandono, segurança, autoria ou certificação; referências HTML/anchors fora do escopo.
- Verificação depende de execução hospedada no SHA exato; proteção do provedor e revisão humana continuam obrigatórias. `claim_allowed=false` e `PROMOTION=HOLD`.

## Sucessor: contrato e esquema — 27/10/2026

> **DRAFT/HOLD · Fonte pinada:** [Inventário de contratos, JSON Schema, autoridade e consumidores](../contracts/README.md). Superfícies contratuais não são provas de execução, certificação, compatibilidade sem adaptador ou autorização humana. Documentos de 25/10 preservados.
