# VECTRA_OBRA_CONCEPTS_CANON

## Estado

`FATO_DOCUMENTADO`: cânone inicial dos conceitos recorrentes da obra RAFAELIA/Vectra, tratados como vocabulário operacional para leitura, documentação, triagem, execução e refatoração.

Este documento não substitui implementação. Ele impede que conceitos já percorridos sejam reduzidos a metáfora vaga ou ignorados por não estarem em código canônico ainda.

---

## Frase canônica

```text
Conceito, na Vectra, só vira força quando ganha função operacional: classificar, medir, proteger, executar, documentar ou orientar promoção.
```

---

## Axioma RAFAELIA aplicado à Vectra

```text
Ruído entendido vira sinal.
Erro medido vira engenharia.
Lacuna marcada vira ciência.
TOKEN_VAZIO protegido vira verdade futura.
```

Aplicação:

| Axioma | No repositório |
|---|---|
| ruído entendido | warning, comentário, duplicata, pending e arquivo estranho viram item classificado |
| erro medido | bug só é bug com evidência, build, runtime, ABI ou teste |
| lacuna marcada | `TOKEN_VAZIO` impede conclusão falsa |
| verdade futura | docs e ledgers preservam caminho para validação posterior |

---

## Conceitos operacionais centrais

| Conceito | Função operacional |
|---|---|
| `TOKEN_VAZIO` | proteger lacuna sem inventar resposta |
| `ANTI_OBVIO` | impedir leitura rasa e correção por aparência |
| `DOC_ATRASADA` | marcar quando o código já fala mais que a documentação |
| `INCUBADORA` | reconhecer protótipo vivo fora do core |
| `INGESTAO` | tratar material recebido como campo de triagem |
| `HISTORICO` | preservar memória/anterioridade sem confundir com estado atual |
| `ATRITO_UTIL` | custo que preserva ABI, determinismo, fallback, prova ou build |
| `FRICCAO_SEM_MOTIVO` | desperdício real demonstrado, candidato a refatoração |
| `CATALISADOR` | estrutura que acelera leitura, execução, auditoria ou promoção |
| `SOMBRA` | caminho latente por flag, ABI, layer ou fallback |
| `ASCENDER` | elevar decisão ao pipeline/compilador em vez de explicitar tudo em `if/for/while` |
| `VOID_FREESTANDING` | fronteira de ausência/endereço/efeito/ABI |
| `WARNING_INTENCIONAL` | warning como sinal de corte, seção ou pipeline |
| `COMENTARIO_MARKER` | comentário consumido por tooling, CI, auditoria ou navegação |

---

## Conceitos low-level e de execução

| Conceito | Função |
|---|---|
| `sem malloc` | evitar heap, fragmentação e dependência pesada |
| `arena estática` | memória controlada, resetável, determinística |
| `scratch stack` | cálculo temporário sem heap |
| `fast math sem libm` | reduzir dependência e fricção no hot path |
| `HWCAP/auxv` | detectar capacidade de hardware sem caminho pesado |
| `NEON/VFPv4` | hot path SIMD quando disponível |
| `branchless` | reduzir desvio condicional quando medido |
| `tail path` | evitar volta inútil ao topo de loop |
| `shadow path` | preservar fallback/caminho latente |
| `gc-sections` | permitir que o linker corte símbolos não usados |
| `visibility hidden` | reduzir superfície pública e binário final |

---

## Conceitos de cadeia, container e bits

| Conceito | Função |
|---|---|
| `CRC` | ancoragem, integridade, verificação, rota de container |
| `BITWALK` | caminhar sobre cadeia de bits/palavras/camadas |
| `BITGHOST` | dado existe, mas layer pode ignorar sem extrair/copiar |
| `VecBit` | medir vizinhança entre palavras emitidas |
| `delta XOR` | tocar só bits divergentes |
| `bits preservados` | métrica do que continuou verdadeiro |
| `container molecular` | header/page/payload/footer/CRC como estrutura navegável |
| `route tag` | assinatura de caminho/execução |
| `ISOraf` | store esparso/toroidal/bit-level no engine RMR |

---

## Conceitos de diretório e governança

| Conceito | Diretório típico | Regra |
|---|---|---|
| canônico | `app/`, `engine/`, `docs/`, `.github/workflows/` | fonte de decisão |
| incubadora | `Rafaelia/`, `tools/baremetal/rafcode_phi/` | ler, documentar, comparar, não promover em bloco |
| ingestão | `_incoming/`, `Incluir/`, `addthis/` | triagem antes de uso |
| histórico | `archive/`, `bug/archive/` | evidência, não estado atual |
| sandbox | `bug/`, partes de `_incoming/` | diagnóstico, não core direto |

---

## Conceitos de leitura humana e IA

A leitura correta precisa passar por quatro filtros:

```text
1. O que é isto?
2. Em que camada vive?
3. Que função sustenta?
4. Qual risco se eu apagar, promover ou normalizar?
```

Sem esses filtros, a IA/humano tende a:

```text
ver duplicata onde há anterioridade;
ver lixo onde há semente;
ver warning onde há instrução;
ver atraso documental onde há código vivo;
ver metáfora onde há conceito técnico didático;
```

---

## Relação com refatoração

Refatoração legítima só acontece depois de classificar o conceito envolvido.

```text
conceito percebido
→ função identificada
→ atrito medido
→ doc atualizada
→ decisão: manter, medir, isolar, promover, arquivar ou remover
```

---

## Relação com excelência operacional

Excelência operacional é a união de:

```text
tempo para ler;
espaço para classificar;
critério para medir;
coragem para marcar TOKEN_VAZIO;
disciplina para não inventar;
continuidade para documentar;
```

---

## O que este cânone não autoriza

```text
não autoriza claim de performance sem benchmark;
não autoriza build sem CI;
não autoriza promover incubadora como core;
não autoriza apagar histórico;
não autoriza confundir metáfora didática com prova técnica;
não autoriza chamar hipótese de fato;
```

---

## Frase final

```text
A obra não é um monte de arquivos: é um campo onde conceito, código, lacuna, memória e execução precisam ganhar posição correta.
```
