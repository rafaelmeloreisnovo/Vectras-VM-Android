# VECTRA_EXECUTION_CONTINUITY_READING_GUIDE

## Estado

`FATO_DOCUMENTADO`: guia de continuidade da atividade para humanos e IA, orientando como ler, continuar, documentar e executar trabalho na Vectra sem perder contexto, sem reduzir conceitos e sem refatorar por aparência.

Este documento existe porque a Vectra não deve depender de memória oral a cada nova sessão. A leitura precisa ter continuidade operacional.

---

## Frase canônica

```text
Continuar a Vectra é manter a linha viva: ler antes de agir, classificar antes de mover, documentar antes de promover, medir antes de concluir.
```

---

## Ordem de entrada obrigatória

Antes de mexer em código, humano ou IA deve ler nesta ordem:

1. `README.md` — entrada institucional e taxonomia de diretórios.
2. `PROJECT_STATE.md` — estado real e bloqueios.
3. `docs/README.md` — hub técnico.
4. `docs/active/VECTRA_OPERATIONAL_EXCELLENCE_WORKSPACE_PLAN.md` — sequência de trabalho.
5. `docs/active/VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT.md` — evitar leitura rasa.
6. `docs/active/VECTRA_CODE_AHEAD_OF_DOCS_LEDGER.md` — código pode estar à frente da documentação.
7. `docs/active/VECTRA_FRICTION_DETERMINISTIC_REFACTOR_PROTOCOL.md` — separar atrito útil de desperdício.
8. `docs/active/VECTRA_INCOMING_TRIAGE_MATRIX.md` — classificar incubadora/ingestão/histórico.
9. Ledgers específicos da camada em trabalho.

---

## Conduta de continuidade

Cada continuação de trabalho deve começar declarando:

```text
camada_alvo:
arquivos_lidos:
docs_consultados:
fato_confirmado:
lacuna_protegida:
token_vazio:
acao_executada:
proximo_f_next:
```

Isso impede que uma sessão comece tentando resolver tudo sem saber onde está.

---

## Critérios de leitura para IA

A IA deve ler como auditor estrutural, não como formatador automático.

| Situação | Leitura proibida | Leitura correta |
|---|---|---|
| warning | bug automático | sinal a classificar |
| comentário | ruído | possível marker/tooling/ABI |
| `void` | vazio inútil | fronteira freestanding possível |
| `_incoming` | lixo | ingestão/semente/pending |
| `Rafaelia/` | pasta paralela descartável | incubadora C/ASM/JNI/baremetal |
| zip/doc/imagem | acúmulo | possível anterioridade/evidência |
| duplicata | apagar | classificar função/histórico/fallback |
| documentação atrasada | conceito ausente | doc deve alcançar código vivo |
| código estranho | erro | medir função antes de concluir |

---

## Critérios de leitura para humanos

Humanos devem conseguir responder:

```text
O que este arquivo sustenta?
Ele é canônico, incubadora, ingestão ou histórico?
Ele entra no build?
Ele prova anterioridade?
Ele preserva ABI?
Ele evita dependência pesada?
Ele é fricção útil ou desperdício real?
Qual doc ativa explica isso?
Qual doc ainda falta?
```

---

## Regra de documentação antes de refatoração

Quando a documentação estiver atrás do código:

```text
1. registrar DOC_ATRASADA;
2. criar/atualizar doc ativa;
3. apontar no hub técnico;
4. só depois avaliar refatoração;
5. nunca apagar o código vivo para caber na documentação antiga.
```

---

## Continuidade por camadas

| Camada | Próximo modo de leitura |
|---|---|
| `Rafaelia/` | ledger progressivo por arquivo, começando B1–B8 e BitRAF |
| `_incoming/pending` | agrupar por família: `rafaelia_*.S`, `rmr_*.S`, scripts, C |
| `engine/rmr/` | comparar incubadora com contrato core existente |
| `tools/baremetal/rafcode_phi/` | ponte C→ASM→hex, VecBit, relação com BITWALK |
| `app/` | mapear Android/Kotlin/Java → JNI → RMR |
| `Incluir/` | inventariar pacotes sem extrair em massa no repo |
| `addthis/` | separar evidência, asset, histórico e ruído real |

---

## O que cada rodada deve produzir

Uma rodada boa não precisa resolver tudo. Ela deve produzir continuidade.

```text
mínimo:
- 1 camada delimitada;
- arquivos realmente lidos;
- classificação explícita;
- TOKEN_VAZIO protegido;
- doc/ledger atualizado;
- F_NEXT concreto;
```

---

## Como tratar pedidos amplos

Quando o pedido for “varrer tudo”, “avaliar tudo” ou “fazer tudo”:

```text
não tentar fingir totalidade;
criar trilha;
começar por camada;
documentar progresso;
marcar lacunas;
continuar por lotes;
```

Totalidade, na Vectra, nasce de continuidade determinística, não de pressa.

---

## Frases operacionais

```text
Não é lixo antes de triagem.
Não é bug antes de intenção.
Não é ausência antes de TOKEN_VAZIO.
Não é core antes de contrato.
Não é performance antes de benchmark.
Não é release antes de assinatura oficial.
Não é refatoração antes de fricção demonstrada.
```

---

## Relação com a obra

A Vectra deve ser lida como obra técnica em camadas:

```text
ruído → sinal;
erro medido → engenharia;
lacuna marcada → ciência;
TOKEN_VAZIO protegido → verdade futura;
```

A continuidade da leitura deve respeitar essa sequência.

---

## Frase final

```text
A atividade não é correr; é manter a linha de execução até cada peça ganhar nome, lugar, prova e destino.
```
