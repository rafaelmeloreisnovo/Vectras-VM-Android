<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

> **Classificação:** `EXPERIMENTAL`
> **Natureza:** guia de pesquisa e protocolo reproduzível; perguntas e hipóteses dependem de dataset versionado.
> **Obrigação de evidência:** resultados de performance só podem ser publicados com vínculo a [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/), [`../../tools/perf/`](../../tools/perf/) ou artefato de CI em [`.github/workflows/`](../../.github/workflows/).
> **Abstenção técnica:** sem dados brutos, commit, dispositivo, variante e método, o documento deve formular hipótese/protocolo, não conclusão.

# Vectras VM — Scientists & Research (Reproducible)

## Resumo
Guia de pesquisa alinhado ao código do projeto, com foco em reprodutibilidade experimental e rastreabilidade de resultados.

## Escopo
- Coberto:
  - Perguntas de pesquisa suportadas pela implementação atual.
  - Protocolo de coleta e publicação de dados.
- Não coberto:
  - Resultados estatísticos fixos sem dataset versionado no repositório.


## Classificação experimental e publicação responsável
| Elemento | Pode afirmar? | Condição de publicação |
|---|---|---|
| Pergunta de pesquisa | Sim, como hipótese investigável. | Explicitar variável, cenário e condição de falsificação. |
| Resultado de benchmark | Somente com evidência. | Anexar dados em [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/) ou relatório de CI; citar ferramenta em [`../../tools/perf/`](../../tools/perf/) quando usada. |
| Generalização científica | Não por padrão. | Exigir dataset versionado, múltiplas rodadas, método estatístico e limitações. |

Inclusão informacional: o documento deve ser legível por pesquisadores humanos e por agentes de IA. Por isso, cada conclusão precisa distinguir observação, inferência mínima, hipótese e limitação ambiental.

## Fontes no repositório
- `app/src/main/java/com/vectras/vm/benchmark/VectraBenchmark.java`
- `app/src/main/java/com/vectras/vm/benchmark/BenchmarkManager.java`
- `app/src/main/java/com/vectras/vm/core/*`
- `docs/BENCHMARK_MANAGER.md`
- `docs/PERFORMANCE_INTEGRITY.md`

## Perguntas sugeridas
1. Como cada categoria de métrica varia entre cenários equivalentes?
2. Quais sinais de ambiente afetam consistência dos resultados?
3. Quais ajustes de execução alteram latência/throughput?

## Protocolo mínimo para paper
1. Fixar commit SHA, variante e dispositivo.
2. Executar múltiplas rodadas por cenário com mesmas condições.
3. Versionar dados brutos e scripts de análise.
4. Publicar método e limitações do ambiente de teste.

## Metadados
- Versão do documento: 1.3
- Última atualização: 2026-03-06
- Commit de referência: `HEAD`
- Domínio de código coberto: Pesquisa reproduzível sobre benchmark, core e integração JNI/app.

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
