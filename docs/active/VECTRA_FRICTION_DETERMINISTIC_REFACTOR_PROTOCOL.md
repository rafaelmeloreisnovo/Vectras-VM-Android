# VECTRA_FRICTION_DETERMINISTIC_REFACTOR_PROTOCOL

## Estado

`FATO_DOCUMENTADO`: protocolo para separar atrito útil de fricção sem motivo e guiar refatoração determinística sem apagar semente, sombra, fallback, marker ou anterioridade.

Este documento responde ao problema: nem todo atrito é desperdício; às vezes o atrito é o acoplamento estrutural que mantém determinismo, ABI, build ou leitura histórica.

---

## Frase canônica

```text
Refatorar não é deixar bonito; é remover fricção sem função sem quebrar determinismo, evidência, ABI ou rota de execução.
```

---

## Definições

| Termo | Significado |
|---|---|
| `ATRITO_UTIL` | custo local que preserva ABI, determinismo, compatibilidade, fallback ou prova histórica |
| `FRICCAO_SEM_MOTIVO` | custo sem função técnica, histórica, autoral, operacional ou de build |
| `CATALISADOR` | estrutura aparentemente indireta que acelera triagem, build, execução ou auditoria |
| `SOMBRA` | caminho latente por flag, ABI, build, layer ou fallback |
| `ESQUECIDO` | item que pode ter valor, mas perdeu vínculo documental |
| `INDEVIDO` | item que causa dano comprovável ou bloqueia execução sem benefício |
| `TOKEN_VAZIO` | ainda não há evidência suficiente para decidir |

---

## Regra de ouro

```text
Só remover ou refatorar quando a fricção for sem função demonstrável.
Se houver função possível e não medida, marcar TOKEN_VAZIO.
```

---

## Perguntas antes de refatorar

1. O arquivo entra no build?
2. O arquivo é evidência histórica/anterioridade?
3. O arquivo é incubadora de core?
4. O arquivo preserva compatibilidade ABI/API?
5. O arquivo evita malloc/libc/heap/fragmentação?
6. O arquivo serve a Termux/ARM32/proot/JNI/host?
7. O comentário/warning/CRLF/trailing whitespace é marker?
8. A duplicata é intencional, fallback, comparação ou resíduo?
9. A remoção reduz binário ou só reduz aparência?
10. A refatoração tem rollback?

---

## Estados de decisão

| Estado | Decisão |
|---|---|
| `MANTER` | função clara ou risco de perda |
| `DOCUMENTAR` | função existe mas doc está atrasada |
| `MEDIR` | precisa build, diff, size, perf ou assembly |
| `PROMOVER` | incubadora pronta para core, com contrato |
| `ISOLAR` | valor existe, mas não deve contaminar core |
| `ARQUIVAR` | histórico preservado, não estado atual |
| `REMOVER` | fricção sem função, com rollback |
| `TOKEN_VAZIO` | não decidir ainda |

---

## Métricas de fricção

| Métrica | Sinal |
|---|---|
| Build graph | arquivo entra ou não entra no build |
| Binary size | seção sobrevive ou é cortada por linker |
| ABI/API | símbolo público, assinatura, header |
| Runtime | caminho executado, hot path, fallback |
| Tooling | consumido por script/grep/CI/auditoria |
| Histórico | anterioridade, comparação, trilha de decisão |
| Cognição operacional | reduz ou aumenta ambiguidade para humanos/IA |

---

## Fricção útil comum na Vectra

```text
void freestanding;
warning unused;
comentário marker;
shadow path;
stub;
fallback por ABI;
_incoming pending;
zip de anterioridade;
ASM pequeno;
duplicata incubadora/core;
CRLF legado;
trailing whitespace em texto histórico;
```

---

## Fricção sem motivo provável

Só classificar assim após leitura e evidência:

```text
arquivo órfão sem referência e sem valor histórico;
duplicata bit-a-bit sem função de anterioridade;
script antigo que contradiz build canônico e não tem uso histórico;
comentário enganoso que induz build errado;
include quebrado sem compatibilidade pretendida;
asset pesado sem licença/proveniência/uso/evidência;
```

---

## Refatoração determinística

Uma refatoração aceita precisa declarar:

```text
origem:
problema:
evidência:
por que é fricção sem motivo:
o que será preservado:
o que será removido:
rollback:
teste mínimo:
status:
```

---

## Relação com varredura total

A varredura não deve buscar apenas bugs. Ela deve buscar estrutura.

```text
varrer
→ classificar
→ identificar função
→ medir atrito
→ separar catalisador de desperdício
→ documentar
→ só então refatorar
```

---

## Exemplo de leitura correta

### Caso: arquivo parece duplicado

Leitura rasa:

```text
duplicado → apagar
```

Leitura Vectra:

```text
duplicado
→ é incubadora?
→ é histórico?
→ é anterioridade?
→ é fallback?
→ é comparação de versão?
→ entra no build?
→ se nada disso, fricção sem motivo
```

---

## Proibição

```text
não refatorar por estética;
não limpar para agradar linter sem entender o pipeline;
não remover sombra sem build matrix;
não remover duplicata sem classificar;
não promover criatividade esquecida como produção;
não negar valor a item não documentado;
```

---

## Frase final

```text
A boa refatoração não corta raiz; ela poda desperdício depois de entender a árvore.
```
