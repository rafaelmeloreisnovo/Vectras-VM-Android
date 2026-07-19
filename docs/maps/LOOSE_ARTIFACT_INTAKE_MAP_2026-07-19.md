# Mapa de Entrada dos Arquivos Soltos

Data: 2026-07-19  
Escopo inicial: `Incluir/`, `_incoming/`, `addthis/`, `bug/`

## Invariante

```text
arquivo encontrado
!= código integrado
!= documentação canônica
!= artefato executado
```

Nenhum arquivo é movido automaticamente. A primeira passagem produz:

- caminho relativo;
- tamanho;
- SHA-256;
- extensão;
- rota sugerida;
- grupos de duplicatas por conteúdo.

## Rotas iniciais

| Extensão | Rota sugerida | Gate antes da promoção |
|---|---|---|
| `.c` | `source/c` | autoria/licença + compilação + owner de módulo |
| `.h` | `source/include` | ABI + consumidores + include guard |
| `.S`/`.s` | `source/asm` | arquitetura + ABI + assembler + selftest |
| `.java` | `source/android-java` | package + Gradle source set + teste |
| `.kt` | `source/android-kotlin` | package + Gradle source set + teste |
| `.py` | `tools/python` | CLI + determinismo + teste |
| `.sh` | `tools/shell` | shellcheck lógico + fail-closed + execução |
| `.md`/`.txt` | `docs/intake` | classificação: fato, hipótese, plano ou histórico |
| `.json`/`.yaml`/`.yml` | `data-or-config/review` | schema + autoridade + consumidor |
| `.xml` | `android-or-data/review` | distinguir manifest, layout, resource ou dado |
| `.zip` | `quarantine/archive` | inventário interno + hash + licença |
| `.apk` | `quarantine/artifact` | assinatura + DEX + ELF + ABI + proveniência |
| outras | `quarantine/unclassified` | inspeção manual |

## Processo de promoção

```text
INVENTORIED
→ IDENTIFIED
→ LICENSED
→ ROUTED
→ INTEGRATED
→ BUILT
→ TESTED
→ PROVEN_RUNTIME
```

Uma etapa não implica a seguinte.

## Regras para completar documentos pelos arquivos soltos

O documento pode ser enriquecido por um arquivo solto somente quando existir uma ponte explícita:

```json
{
  "source_path": "Incluir/exemplo.c",
  "source_sha256": "...",
  "target_document": "docs/...md",
  "section": "...",
  "relation": "implements|contradicts|historical|example|planned",
  "claim_state": "...",
  "reviewed_by": "..."
}
```

Sem essa ponte, a relação permanece `TOKEN_VAZIO`.

## Duplicatas

Arquivos com o mesmo SHA-256 são agrupados. Isso permite distinguir:

- cópia exata;
- fork interno;
- backup;
- versão concorrente;
- arquivo promovido sem remoção da origem.

O auditor não apaga duplicatas. Ele apenas as torna visíveis para decisão posterior.

## Comando

```bash
python3 tools/audit_vectra_capabilities.py \
  --output reports/vectra_capability_surface.json
```

O resultado machine-readable fica em:

```text
reports/vectra_capability_surface.json
```

## Próxima etapa operacional

Após executar o inventário no clone completo, a segunda parte deve selecionar um lote pequeno e reversível, por exemplo:

```text
BATCH_001:
  - compiladorlowFala.txt
  - vectras_bbs.c
  - fontes ELF/DEX relacionadas
  - documentos que os citam
```

Cada item receberá owner, destino, contrato de build, teste e critério de rollback antes de qualquer movimentação.
