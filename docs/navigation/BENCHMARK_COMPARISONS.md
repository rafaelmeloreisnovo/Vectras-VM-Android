<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

> **Classificação:** `VALIDATED`
> **Natureza:** método de comparação apoiado por código de benchmark, ferramentas de performance e pastas de resultados; números específicos continuam dependentes de artefato bruto anexado.
> **Obrigação de evidência:** toda claim de performance deve apontar para [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/), [`../../tools/perf/`](../../tools/perf/) ou artefato de CI em [`.github/workflows/`](../../.github/workflows/).
> **Abstenção técnica:** este documento valida o protocolo e a rastreabilidade; não valida um resultado numérico quando o relatório bruto não estiver citado.

# Vectras VM — Benchmark Comparisons (Code-Grounded)

## Resumo
Documento de comparação de benchmark orientado por evidência reproduzível. Define método e formato de publicação; não fixa números sem artefato de execução.

## Escopo
- Coberto:
  - Estrutura da suíte de benchmark implementada.
  - Protocolo de coleta e comparação entre cenários.
  - Requisitos mínimos de publicação de resultados.
- Não coberto:
  - Tabelas fixas por dispositivo sem relatório bruto anexado.


## Estado validado do método e limite dos resultados
A classificação `VALIDATED` aplica-se ao **método documentado**, à existência de fontes de benchmark e às ferramentas de validação. Ela não transforma placeholders, templates ou exemplos em resultados medidos. Cada tabela publicada deve citar pelo menos um destes vínculos:

- Fonte de suíte ou baseline: [`../../bench/`](../../bench/).
- Métricas versionadas: [`../../reports/metrics/`](../../reports/metrics/).
- Ferramentas de comparação/validação: [`../../tools/perf/`](../../tools/perf/).
- Execução automatizada ou artefato: [`.github/workflows/`](../../.github/workflows/).

Critério de abstenção: se não houver arquivo bruto, comando e contexto de execução, preencher a coluna `Válido` como `false` e manter a linha como exemplo metodológico.

## Base no código
- `app/src/main/java/com/vectras/vm/benchmark/VectraBenchmark.java`
- `app/src/main/java/com/vectras/vm/benchmark/BenchmarkManager.java`
- `app/src/main/java/com/vectras/vm/benchmark/BenchmarkActivity.java`

## Estrutura da suíte
- `METRIC_COUNT = 79`.
- Categorias: CPU single-thread, CPU multi-thread, memória, storage, integridade, emulação.

## Protocolo de comparação
1. Definir dispositivo, build variant e commit SHA.
2. Executar preflight e capturar warnings/diagnósticos.
3. Rodar cenários equivalentes (ex.: baseline e VM).
4. Salvar resultado bruto por métrica e relatório consolidado.
5. Publicar comparação com artefatos anexos.

## Template mínimo
| Métrica | Cenário A | Cenário B | Diferença | Válido |
|---|---:|---:|---:|---|
| CPU_INTEGER_ADD | _valor_ | _valor_ | _calc_ | _true/false_ |
| MEM_COPY_BANDWIDTH | _valor_ | _valor_ | _calc_ | _true/false_ |
| STORAGE_SEQ_READ | _valor_ | _valor_ | _calc_ | _true/false_ |

## Regras de publicação
- Publicar somente com: dispositivo, variant, commit SHA, relatório bruto.
- Declarar limitações de ambiente que afetem estabilidade dos dados.

## Metadados
- Versão do documento: 1.3
- Última atualização: 2026-03-06
- Commit de referência: `HEAD`
- Domínio de código coberto: App benchmark (`app/src/main/java/com/vectras/vm/benchmark/*`) e documentação de método comparativo (`docs/navigation`).

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
