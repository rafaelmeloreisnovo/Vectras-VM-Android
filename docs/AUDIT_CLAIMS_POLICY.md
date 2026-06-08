# Audit Claims Policy

## Regra central
Este repositório não declara certificação ISO, conformidade formal ISO nem auditoria externa acreditada.

Toda afirmação técnica deve separar claramente:
- **fato verificável**: apoiado por código, CI, benchmark, relatório ou artefato versionado;
- **hipótese/protótipo**: ideia ainda em validação, marcada como `EXPERIMENTAL`;
- **narrativa institucional**: comunicação de posicionamento, marcada como `NARRATIVE`;
- **registro histórico**: material preservado por contexto, marcado como `ARCHIVAL`.

## Classificação obrigatória de novos documentos
Novos documentos de marketing técnico, navegação executiva, pesquisa, benchmark, anúncio institucional ou autoria técnica devem declarar no topo uma das classificações abaixo:

| Classificação | Uso correto | Obrigação de evidência |
|---|---|---|
| `VALIDATED` | Afirmações apoiadas por CI, benchmark, relatório, código ou artefato reproduzível. | Citar fonte interna verificável e comando/protocolo quando aplicável. |
| `EXPERIMENTAL` | Hipótese, protótipo, pesquisa em andamento ou resultado ainda não generalizado. | Declarar condição de falsificação, limitações e dados necessários. |
| `NARRATIVE` | Comunicação institucional, posicionamento, rapport, visão executiva ou orientação de leitura. | Evitar números/performance como fato; apontar trilhas de auditoria quando houver. |
| `ARCHIVAL` | Histórico preservado, material legado ou contexto anterior ao estado atual. | Declarar que pode estar desatualizado e apontar fonte canônica atual quando existir. |

## Claims de performance
Qualquer claim de performance — incluindo latência, throughput, aceleração, eficiência, regressão, comparação, fast-path, NEON, CPU, memória, storage, emulação ou benchmark — exige vínculo explícito com pelo menos uma destas fontes:

- `bench/`
- `reports/metrics/`
- `tools/perf/`
- artefato ou log de CI em `.github/workflows/`

Sem esse vínculo, o documento deve usar abstenção técnica: declarar método, hipótese, objetivo ou escopo, mas não resultado medido. Performance com benchmark, não com promessa.

## Marketing técnico e abstenção
Novos documentos de marketing técnico devem conter uma seção curta de **Evidência ou abstenção técnica** com:

1. classificação (`VALIDATED`, `EXPERIMENTAL`, `NARRATIVE` ou `ARCHIVAL`);
2. fontes internas citadas para claims técnicos;
3. fontes específicas para claims de performance, quando existirem;
4. frase explícita de abstenção quando a evidência ainda não existir.

Exemplo de abstenção aceitável:

> Este documento descreve intenção arquitetural e navegação institucional. Ele não declara ganho de performance sem artefato em `bench/`, `reports/metrics/`, `tools/perf/` ou CI.

## Inclusão informacional e coerência homem + IA
Documentos devem ser navegáveis por pessoas e por agentes de IA com inferências mínimas e auditáveis. Para isso:

- preferir links internos canônicos a afirmações soltas;
- diferenciar fato, evidência, hipótese, limitação e decisão;
- preservar autoria original e fonte de verdade;
- evitar adjetivos de superioridade sem medida;
- manter comandos de verificação quando o claim depender de inspeção local;
- registrar limitações em vez de mascarar ausência de evidência.

## Termos permitidos
- alinhado a boas práticas
- inspirado em ISO/IEC
- checklist interno
- referência metodológica
- mapeamento preliminar
- evidência interna
- método reproduzível
- hipótese experimental
- narrativa institucional
- abstenção técnica

## Termos proibidos sem certificação formal
- certificado ISO
- ISO certified
- ISO compliant
- compliance ISO garantido
- conforme ISO
- auditoria certificada

## Aplicação no projeto
Benchmarks, artifacts, relatórios e verificações internas são evidências técnicas internas e não representam certificação formal. Essas evidências não substituem auditoria externa acreditada.

A política também se aplica a documentos em `docs/navigation/`: anúncios, briefs, comparações, pesquisa e autoria técnica devem declarar classificação no topo e manter vínculo explícito entre claims de performance e evidências versionadas.
