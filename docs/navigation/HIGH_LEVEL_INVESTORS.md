<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

> **Classificação:** `NARRATIVE`
> **Natureza:** brief executivo para diligência técnica, sem promessa financeira nem performance sem evidência.
> **Obrigação de evidência:** claims de desempenho, benchmark, throughput, latência, aceleração, regressão ou eficiência devem citar artefatos em [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/), [`../../tools/perf/`](../../tools/perf/) ou CI em [`.github/workflows/`](../../.github/workflows/).
> **Abstenção técnica:** quando a evidência não estiver anexada, usar linguagem de escopo, método, risco ou hipótese; não declarar resultado operacional como fato validado.

# Vectras VM — Investor Brief (Technical Due Diligence)

## Resumo
Documento executivo para diligência técnica. Resume capacidades implementadas e riscos técnicos observáveis, sem projeções financeiras não auditadas.

## Escopo
- Coberto:
  - Tese técnica verificável no código.
  - Riscos técnicos e mitigação operacional.
  - Checklist de diligência.
- Não coberto:
  - Projeções financeiras sem fonte auditável anexada.


## Classificação de claims e diligência de evidência
| Tipo de claim | Status permitido neste brief | Evidência mínima |
|---|---|---|
| Arquitetura, autoria e governança | Pode ser descrito quando houver vínculo com código/docs internos. | Código, documentação canônica e índice de rastreabilidade. |
| Performance, benchmark, latência, throughput ou eficiência | Só pode ser tratado como resultado quando houver artefato medido. | [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/), [`../../tools/perf/`](../../tools/perf/) ou CI em [`.github/workflows/`](../../.github/workflows/). |
| Mercado, receita, valuation ou retorno financeiro | Fora do escopo sem fonte auditável anexada. | Relatório externo auditável, contrato ou documento financeiro autorizado. |

Boas práticas para investidores e avaliadores: separar **capacidade implementada**, **protocolo de medição**, **resultado medido** e **hipótese de expansão**. Essa separação reduz ruído, protege a decisão humana e impede que sistemas de IA ampliem inferências sem lastro.

## Tese técnica verificável
- Stack de virtualização Android com QEMU.
- Benchmark low-level com 79 métricas.
- Core de otimização com fast-path nativo opcional.
- Governança de rastreabilidade docs⇄código.

## Checklist de diligência
1. Validar build/manifest e variantes.
2. Revisar stack de launch VM e benchmark.
3. Reproduzir benchmark com protocolo documentado.
4. Verificar cobertura de testes unitários.

## Metadados
- Versão do documento: 1.3
- Última atualização: 2026-03-06
- Commit de referência: `HEAD`
- Domínio de código coberto: Visão executiva de app/engine/runtime com rastreabilidade documental.

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
