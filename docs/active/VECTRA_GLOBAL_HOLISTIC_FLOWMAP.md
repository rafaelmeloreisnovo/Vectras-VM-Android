# VECTRA_GLOBAL_HOLISTIC_FLOWMAP

## Estado

`FATO_DOCUMENTADO`: mapa holístico global da Vectra para orientar humanos e IA na escolha do melhor caminho de execução, documentação, triagem, refatoração e validação.

Este documento existe para abrir espaço de visão antes de agir. Ele não substitui os ledgers de camada; ele mostra o campo inteiro e a ordem saudável de travessia.

---

## Frase canônica

```text
Ver o todo antes de mexer na parte: a Vectra precisa de fluxo, camada, evidência e retorno, não de impulso local.
```

---

## Visão global

A Vectra deve ser lida como um sistema de sete campos:

| Campo | Função |
|---|---|
| Entrada institucional | README, PROJECT_STATE, DOC_INDEX, hub técnico |
| Governança de leitura | anti-óbvio, TOKEN_VAZIO, doc atrasada, fricção determinística |
| Incubadora técnica | `Rafaelia/`, `_incoming/`, `tools/baremetal/rafcode_phi/` |
| Core canônico | `engine/rmr/include`, `engine/rmr/src`, manifestos |
| Runtime/app | `app/`, Java/Kotlin, JNI, VM, QEMU, VNC, X11, Termux |
| Validação | CI, build, tests, benchmarks, release, assinatura |
| Memória/evidência | `reports/`, `archive/`, `Incluir/`, `addthis/`, docs ativos |

---

## Fluxograma holístico

```mermaid
flowchart TD
    A[Pedido / tarefa / nova rodada] --> B[Ler rota de entrada]
    B --> B1[README.md]
    B --> B2[PROJECT_STATE.md]
    B --> B3[docs/README.md]
    B --> B4[Guia de continuidade]

    B4 --> C{Camada alvo clara?}
    C -- não --> C1[Escolher camada por impacto]
    C1 --> C2[Governança / Incubadora / Core / App / CI / Evidência]
    C -- sim --> D[Aplicar contrato anti-óbvio]
    C2 --> D

    D --> E{Artefato é canônico?}
    E -- sim --> F[Verificar build/manifesto/API/ABI]
    E -- não --> G{É incubadora, ingestão ou histórico?}

    G -- incubadora --> H[Triagem técnica por ledger]
    G -- ingestão --> I[Inventariar sem promover]
    G -- histórico --> J[Preservar como evidência, não estado atual]
    G -- desconhecido --> K[TOKEN_VAZIO]

    H --> L{Fricção encontrada?}
    F --> L
    I --> L
    J --> L
    K --> L

    L -- atrito útil --> M[Documentar e manter]
    L -- fricção sem motivo --> N[Propor refatoração mínima]
    L -- dúvida --> K

    N --> O{Tem rollback e teste mínimo?}
    O -- não --> K
    O -- sim --> P[Aplicar mudança pequena]

    M --> Q[Atualizar doc/ledger]
    P --> Q
    K --> Q

    Q --> R{Precisa validação?}
    R -- sim --> S[Build/CI/benchmark/selftest]
    R -- não --> T[Fechar rodada com FATO/LACUNA/F_NEXT]
    S --> U{Validação passou?}
    U -- sim --> T
    U -- não --> V[ERRO medido / rollback / nova lacuna]
    V --> T
```

---

## Caminho de melhor decisão

A ordem preferida é:

```text
1. continuidade de leitura;
2. camada alvo;
3. classificação;
4. evidência;
5. fricção;
6. decisão mínima;
7. documentação;
8. validação;
9. F_NEXT.
```

---

## Heurística de escolha da próxima camada

| Pergunta | Caminho |
|---|---|
| Há falha simples que bloqueia leitura/build? | corrigir com shim/compat mínimo, se permitido |
| Há incubadora com alto valor e risco de perda? | ledger progressivo antes de promoção |
| Há documentação atrasada confundindo IA/humano? | doc ativa antes de código |
| Há core canônico dependendo de manifesto? | verificar manifesto antes de mover |
| Há claim de build/performance? | CI/benchmark antes de concluir |
| Há arquivo pesado/zip/doc/imagem? | inventário e proveniência antes de uso |

---

## Melhor caminho atual observado

A partir das rodadas recentes, o caminho mais saudável é:

```text
P0 — preservar visão global e continuidade;
P1 — fechar triagem RAFAELIA B1–B4;
P2 — entender script de build ARM32;
P3 — resolver shim baremetal.h quando a escrita permitir;
P4 — comparar RAFAELIA incubadora com engine/rmr;
P5 — só então propor promoção ou refatoração;
P6 — validar por build/CI/benchmark quando possível.
```

---

## Onde não avançar ainda

```text
não promover B1/B2/B3 como core;
não reescrever ASM por estética;
não apagar _incoming;
não normalizar histórico;
não declarar performance;
não tratar B3 como erro antes de medir runtime;
não trocar include legado se o shim preserva melhor a intenção;
```

---

## Relação entre campos

```text
Rafaelia/              → incubadora técnica
_incoming/             → ingestão e sementes pendentes
engine/rmr/            → core canônico
app/                   → runtime operacional Android/VM/JNI
reports/               → evidência e inventário
docs/active/           → contratos vivos
PROJECT_STATE.md       → limite de afirmação atual
```

---

## Padrão de fechamento por rodada

Toda rodada deve fechar com:

```text
camada_alvo:
arquivos_lidos:
o_que_foi_criado:
o_que_foi_alterado:
o_que_nao_foi_mexido:
fato_confirmado:
lacuna_protegida:
token_vazio:
falha/bloqueio:
proximo_f_next:
```

---

## Frase final

```text
A visão holística não atrasa o trabalho; ela impede que o trabalho certo seja feito no lugar errado.
```
