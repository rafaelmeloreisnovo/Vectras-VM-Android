<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

> **Classificação:** `NARRATIVE`
> **Natureza:** navegação institucional sobre tecnologia, inovação e autoria técnica.
> **Obrigação de evidência:** qualquer claim de benchmark, desempenho, aceleração, eficiência ou comparação deve citar [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/), [`../../tools/perf/`](../../tools/perf/) ou CI em [`.github/workflows/`](../../.github/workflows/).
> **Abstenção técnica:** autoria e arquitetura podem ser mapeadas por código/documentação; performance só pode ser afirmada com artefato medido.

# Tecnologia, Inovação e Autoria Técnica (Navegação Guiada)

## Propósito
Este documento consolida pilares de inovação e explicita a autoria técnica por subsistema, com rastreabilidade verificável entre código, documentação e estado do projeto.


## Escopo semântico e evidência
- **Autoria técnica:** pode ser mapeada por subsistema, código e trilha documental.
- **Inovação:** deve ser descrita como combinação verificável de arquitetura, governança, ferramentas e método; não como superioridade não medida.
- **Performance:** qualquer menção a benchmark determinístico, execução real, eficiência ou comparação exige vínculo com [`../../bench/`](../../bench/), [`../../reports/metrics/`](../../reports/metrics/), [`../../tools/perf/`](../../tools/perf/) ou CI em [`.github/workflows/`](../../.github/workflows/).
- **Homem + inteligência artificial:** humanos recebem contexto e caminhos de auditoria; agentes de IA devem preservar a diferença entre fato de autoria, hipótese experimental, narrativa institucional e resultado validado.

## Pilares de inovação
1. **Benchmark determinístico**
   - Execução reproduzível com critérios estáveis de medição e comparação.
   - Referências: [`../BENCHMARKS.md`](../BENCHMARKS.md), [`../BENCHMARK_MANAGER.md`](../BENCHMARK_MANAGER.md), [`../PERFORMANCE_INTEGRITY.md`](../PERFORMANCE_INTEGRITY.md).

2. **Core low-level orientado à execução real**
   - Foco em engine, integração nativa e previsibilidade de runtime.
   - Referências: [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../RAFAELIA_COHESION_ENTERPRISE_STACK.md`](../RAFAELIA_COHESION_ENTERPRISE_STACK.md), [`../FULLSTACK_SOURCE_MAP.md`](../FULLSTACK_SOURCE_MAP.md).

3. **Governança de rastreabilidade fim-a-fim**
   - Mapeamento de artefatos para reduzir lacunas de auditoria técnica.
   - Referências: [`../SOURCE_TRACEABILITY_MATRIX.md`](../SOURCE_TRACEABILITY_MATRIX.md), [`../IP_MAP.md`](../IP_MAP.md), [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md).

## Mapeamento de autoria técnica por subsistema
| Subsistema | Papel técnico | Evidência de código | Trilha documental |
|---|---|---|---|
| `engine/rmr/` | Núcleo de execução e rotinas low-level da engine. | [`../../engine/rmr/src/`](../../engine/rmr/src/), [`../../engine/rmr/include/`](../../engine/rmr/include/) | [`../ARCHITECTURE.md`](../ARCHITECTURE.md), [`../FULLSTACK_SOURCE_MAP.md`](../FULLSTACK_SOURCE_MAP.md), [`../SOURCE_TRACEABILITY_MATRIX.md`](../SOURCE_TRACEABILITY_MATRIX.md) |
| `app/src/main/java/com/vectras/vm/` | Orquestração Android do runtime VM e integração de fluxos da aplicação. | [`../../app/src/main/java/com/vectras/vm/`](../../app/src/main/java/com/vectras/vm/) | [`../API.md`](../API.md), [`../RAFAELIA_COHESION_ENTERPRISE_STACK.md`](../RAFAELIA_COHESION_ENTERPRISE_STACK.md), [`../FULLSTACK_SOURCE_MAP.md`](../FULLSTACK_SOURCE_MAP.md) |
| `tools/baremetal/` | Ferramental de suporte técnico para inspeção e operação de baixo nível. | [`../../tools/baremetal/`](../../tools/baremetal/) | [`../OPERATIONS.md`](../OPERATIONS.md), [`../BENCHMARK_MANAGER.md`](../BENCHMARK_MANAGER.md), [`../SOURCE_TRACEABILITY_MATRIX.md`](../SOURCE_TRACEABILITY_MATRIX.md) |

## 5 links de imersão obrigatórios (código + documentação)
1. **Engine + arquitetura:** [`../../engine/rmr/`](../../engine/rmr/) + [`../ARCHITECTURE.md`](../ARCHITECTURE.md)
2. **Runtime C/ASM + matriz de rastreabilidade:** [`../../engine/rmr/src/`](../../engine/rmr/src/) + [`../SOURCE_TRACEABILITY_MATRIX.md`](../SOURCE_TRACEABILITY_MATRIX.md)
3. **App Android + mapa fullstack:** [`../../app/src/main/java/com/vectras/vm/`](../../app/src/main/java/com/vectras/vm/) + [`../FULLSTACK_SOURCE_MAP.md`](../FULLSTACK_SOURCE_MAP.md)
4. **Ferramentas baremetal + benchmark manager:** [`../../tools/baremetal/`](../../tools/baremetal/) + [`../BENCHMARK_MANAGER.md`](../BENCHMARK_MANAGER.md)
5. **Estado do projeto + coesão enterprise:** [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md) + [`../RAFAELIA_COHESION_ENTERPRISE_STACK.md`](../RAFAELIA_COHESION_ENTERPRISE_STACK.md)

## Evidências verificáveis (comandos de inspeção)
```bash
# 1) Confirmar presença dos subsistemas mapeados
find engine/rmr app/src/main/java/com/vectras/vm tools/baremetal -maxdepth 2 -type f | head -n 40

# 2) Verificar links cruzados deste documento nos índices de navegação
rg -n "TECHNOLOGY_INNOVATION_AUTHORSHIP.md" docs/navigation/INDEX.md docs/README.md

# 3) Confirmar existência dos documentos canônicos relacionados
ls docs/RAFAELIA_COHESION_ENTERPRISE_STACK.md docs/SOURCE_TRACEABILITY_MATRIX.md docs/FULLSTACK_SOURCE_MAP.md PROJECT_STATE.md

# 4) Inspecionar cabeçalho e seções centrais deste documento
sed -n '1,240p' docs/navigation/TECHNOLOGY_INNOVATION_AUTHORSHIP.md

# 5) Verificar ligação cruzada de PROJECT_STATE para este documento
rg -n "TECHNOLOGY_INNOVATION_AUTHORSHIP.md" PROJECT_STATE.md
```

## Ligações cruzadas canônicas
- Coesão arquitetural: [`../RAFAELIA_COHESION_ENTERPRISE_STACK.md`](../RAFAELIA_COHESION_ENTERPRISE_STACK.md)
- Matriz de rastreabilidade: [`../SOURCE_TRACEABILITY_MATRIX.md`](../SOURCE_TRACEABILITY_MATRIX.md)
- Mapa fullstack: [`../FULLSTACK_SOURCE_MAP.md`](../FULLSTACK_SOURCE_MAP.md)
- Estado macro do projeto: [`../../PROJECT_STATE.md`](../../PROJECT_STATE.md)

## Referência canônica de CI Android/Host

- Pipeline oficial Android: `.github/workflows/android-ci.yml` (acionado por wrappers/orquestração).
- Entrada Android: `.github/workflows/android.yml` (wrapper de eventos + delegação).
- Compatibilidade ABI Android: `.github/workflows/compile-matrix.yml` (trilha auxiliar).
- Pipeline oficial Host: `.github/workflows/host-ci.yml`.
- Orquestração e gate final: `.github/workflows/pipeline-orchestrator.yml` + `.github/workflows/quality-gates.yml`.
- Matriz canônica documentada em `docs/ci/workflow-matrix.md`.
