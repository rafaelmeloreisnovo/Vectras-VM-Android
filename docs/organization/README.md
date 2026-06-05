# Organização documental

Este diretório reúne o saneamento documental iniciado em 2026-06-02 para organizar dados, Markdown soltos, fragmentos e documentos desatualizados sem apagar funcionalidades nem mover artefatos sem evidência.

## Documentos

- `../active/AI_SESSION_FACT_MAP_2026-06-05.md` — organização semântica da sessão atual, distinguindo fato, hipótese, metáfora/parábola, token vazio, validação e rollback.
- `../rafaelia_reference/RAFAELIA_SEED_BLOCK_CATALOG_2026-06-05.md` — catálogo navegável das sementes E20/E13/S11 para promoção futura sem misturar conceito e claim de produção.
- `../active/RAFAELIA_ENTERPRISE_COMPLETION_PLAYBOOK_2026-06-05.md` — camada de uso real enterprise com práticas, validação, failover e rollback por camada.
- `DOC_ORGANIZATION_PLAN_2026-06-02.md` — plano de dois ciclos com critérios PASS, rollback, failsafe e failover.
- `LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md` — inventário local até 7 níveis para arquivos soltos, nomes fragmentados e entradas pendentes.
- `SOURCE_ARCHITECTURE_SYNC_2026-06-02.md` — mapa da arquitetura real até 5 níveis para alinhar documentos aos fontes atuais.
- `NECESSARY_CONDITIONS_AUDIT_2026-06-02.md` — auditoria executável de placeholders, gaps, materiais faltantes e sinais de bug.
- `NECESSARY_DATA_DELIVERY_MATRIX_2026-06-02.md` — matriz fullstack/enterprise de dados necessários, correção, promoção, testes e rollback.
- `INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md` — manifesto SHA-256 para promoção, rollback e failover de entradas pendentes.

## Regras de uso

1. Não mover protótipos, ZIPs ou notas conceituais sem manifesto de origem.
2. Não marcar documento como canônico se ele não aponta para código, teste ou workflow real.
3. Ao promover arquivo de `_incoming/` ou `Incluir/`, registrar teste, hash e rollback.
4. Separar claramente teoria, implementação, relatório histórico e artefato transitório.

## Ferramenta de auditoria

Execute a varredura novamente com:

```bash
./tools/docs/audit_documentation_state.py --max-depth 5
```

A ferramenta é somente leitura para a árvore de fontes: ela atualiza apenas os relatórios documentais configurados.
