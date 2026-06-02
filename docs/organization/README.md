# Organização documental

Este diretório reúne o saneamento documental iniciado em 2026-06-02 para organizar dados, Markdown soltos, fragmentos e documentos desatualizados sem apagar funcionalidades nem mover artefatos sem evidência.

## Documentos

- `DOC_ORGANIZATION_PLAN_2026-06-02.md` — plano de dois ciclos com critérios PASS, rollback, failsafe e failover.
- `LOOSE_FILES_AND_FRAGMENTS_INVENTORY_2026-06-02.md` — inventário local até 7 níveis para arquivos soltos, nomes fragmentados e entradas pendentes.
- `SOURCE_ARCHITECTURE_SYNC_2026-06-02.md` — mapa da arquitetura real até 5 níveis para alinhar documentos aos fontes atuais.

## Regras de uso

1. Não mover protótipos, ZIPs ou notas conceituais sem manifesto de origem.
2. Não marcar documento como canônico se ele não aponta para código, teste ou workflow real.
3. Ao promover arquivo de `_incoming/` ou `Incluir/`, registrar teste, hash e rollback.
4. Separar claramente teoria, implementação, relatório histórico e artefato transitório.
