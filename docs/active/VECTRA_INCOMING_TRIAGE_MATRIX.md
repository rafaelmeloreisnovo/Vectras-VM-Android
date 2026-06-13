# VECTRA_INCOMING_TRIAGE_MATRIX

## Estado

`FATO_DOCUMENTADO`: matriz inicial para triagem de material em `Rafaelia/`, `_incoming/`, `Incluir/`, `addthis/`, `archive/` e `bug/` antes de qualquer promoção, limpeza, normalização ou exclusão.

---

## Frase canônica

```text
Material pendente não é lixo; é objeto sem classificação final.
```

---

## Objetivo

Criar uma forma repetível de classificar arquivos e diretórios que ainda não pertencem claramente ao core canônico.

A matriz evita quatro erros:

```text
apagar semente;
promover protótipo como produção;
confundir histórico com estado atual;
confundir documentação atrasada com ausência técnica.
```

---

## Estados permitidos

| Estado | Significado | Ação |
|---|---|---|
| `CANONICO` | fonte vigente já validada | manter como referência |
| `INCUBADORA` | protótipo vivo com potencial técnico | documentar e avaliar promoção |
| `INGESTAO` | material recebido/pendente | triagem antes de uso |
| `HISTORICO` | evidência preservada | não usar como estado atual |
| `DUPLICATA_INTENCIONAL` | cópia para anterioridade, fallback ou comparação | não apagar sem prova |
| `DUPLICATA_RISCO` | duplicata possivelmente acidental | medir e propor consolidação |
| `BUG_REAL` | quebra comprovada | corrigir com rollback |
| `TOKEN_VAZIO` | ainda não classificado | não afirmar |

---

## Diretórios iniciais

| Diretório | Estado inicial | Regra |
|---|---|---|
| `Rafaelia/` | `INCUBADORA` | C/ASM/JNI/baremetal; pode conter semente de core |
| `_incoming/` | `INGESTAO` | material pendente; não promover em bloco |
| `_incoming/pending/` | `INGESTAO`/`INCUBADORA` | vários `.S`/`.c`; classificar um por um |
| `Incluir/` | `INGESTAO` | zips, docs, scripts, papers, pacotes |
| `addthis/` | `INGESTAO`/`HISTORICO` | evidência/entrada; precisa triagem |
| `archive/` | `HISTORICO` | preservar como memória; não estado atual |
| `bug/` | `HISTORICO`/`SANDBOX` | diagnóstico/sandbox; não promover em bloco |

---

## Critérios por tipo de arquivo

| Tipo | Risco de leitura rasa | Critério |
|---|---|---|
| `.S`/`.s` | “ASM pequeno demais” | verificar ABI, registradores, entrada/saída, arquitetura |
| `.c` | “duplicado ou bruto” | verificar header, build, hot path, compatibilidade |
| `.h` | “só definição” | verificar contrato público, macro, guard, ABI |
| `.sh` | “script local” | verificar Termux, CI, bootstrap, ambiente real |
| `.zip` | “lixo/artefato pesado” | verificar se é anterioridade, pacote de submissão, build ou seed |
| `.docx` | “não-código” | verificar autoria, paper, submissão, documentação científica |
| `.json` | “dump” | verificar manifesto, auditoria, schema, inventário |
| `.png/.jpg` | “imagem solta” | verificar evidência visual, publicação, asset, anterioridade |
| `.md/.txt` | “texto longo” | verificar se é contrato, ledger, sessão, paper, prompt ou histórico |

---

## Template por item

```text
objeto:
caminho:
tipo:
tamanho/linhas:
estado_inicial:
evidencia_lida:
o_que_existe:
o_que_nao_existe:
risco_se_apagar:
risco_se_promover:
valor_tecnico:
valor_historico:
proxima_acao:
status_final:
```

---

## Lotes iniciais de trabalho

### Lote A — RAFAELIA raiz

| Item | Estado inicial | Próxima ação |
|---|---|---|
| `Rafaelia/baremetal_nomalloc.c/.h` | `INCUBADORA` | comparar com `engine/rmr/*baremetal*` |
| `Rafaelia/rafaelia_b1.S`–`b8.S` | `INCUBADORA` | mapear arquitetura, registro, função |
| `Rafaelia/rafaelia_bitraf.c` | `INCUBADORA` | comparar com BITRAF/RMR canônico |
| `Rafaelia/rafaelia_orchestrator.c` | `INCUBADORA` | mapear fluxo e dependências |
| `Rafaelia/termux_arm32_build.sh` | `INGESTAO`/`INCUBADORA` | comparar com build atual/Termux alvo |

### Lote B — `_incoming/pending`

| Grupo | Estado inicial | Próxima ação |
|---|---|---|
| `rafaelia_*.S` | `INGESTAO`/`INCUBADORA` | agrupar por tema: torus, delta, vortex, chrono, final |
| `rmr_*.S` | `INGESTAO`/`INCUBADORA` | identificar relação com engine/rmr |
| `*.c` | `INGESTAO` | comparar com versão em `Rafaelia/` e `engine/rmr/` |
| scripts build | `INGESTAO` | verificar Termux/ARM32/host |

### Lote C — `Incluir/`

| Grupo | Estado inicial | Próxima ação |
|---|---|---|
| zips ARM32/C/ASM | `INGESTAO` | inventariar sem extrair em massa no repo |
| papers/docx | `INGESTAO`/`HISTORICO` | separar científico/documentação/submissão |
| scripts Python | `INGESTAO` | identificar auditoria, matriz, matemática |
| imagens/gráficos | `INGESTAO` | verificar se são evidência de publicação/resultado |

### Lote D — `addthis/`

| Grupo | Estado inicial | Próxima ação |
|---|---|---|
| imagens | `INGESTAO`/`HISTORICO` | classificar como evidência visual/asset/anterioridade |
| docs VECTRAS_* | `HISTORICO`/`INGESTAO` | comparar com docs ativos |
| scripts | `INGESTAO` | verificar se ainda são aplicáveis |

---

## Regras de promoção

Um item só pode virar core quando tiver:

```text
origem lida;
função técnica descrita;
licença/autoria/proveniência classificada;
contrato API/ABI, se aplicável;
manifesto de build, se aplicável;
selftest ou teste mínimo;
rollback;
referência no hub técnico ou doc ativo.
```

---

## O que não fazer

```text
não extrair zip dentro do repo por impulso;
não apagar duplicata por nome;
não normalizar CRLF/trailing whitespace em massa;
não promover pending em bloco;
não mover arquivo histórico para core;
não confundir paper/doc com lixo;
não tratar imagem como inútil sem verificar evidência;
```

---

## Ligação com excelência operacional

Esta matriz é o espaço onde cada coisa ganha nome antes de ganhar destino.

```text
nomear
→ classificar
→ medir
→ documentar
→ promover ou preservar
```

---

## Frase final

```text
Triagem é respeito: antes de mover uma peça, entender qual corpo ela sustenta.
```
