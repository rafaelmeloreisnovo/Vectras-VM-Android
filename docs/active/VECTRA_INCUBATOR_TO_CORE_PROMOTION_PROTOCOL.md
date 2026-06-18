# VECTRA_INCUBATOR_TO_CORE_PROMOTION_PROTOCOL

## Estado

`FATO_DOCUMENTADO`: protocolo para promover material de `Rafaelia/`, `_incoming/`, `Incluir/`, `addthis/` ou `tools/baremetal/` para o core canônico `engine/rmr/` sem quebrar build, ABI, autoria, licença, histórico ou rollback.

---

## Frase canônica

```text
Incubadora não é lixo.
Canônico não é entusiasmo.
Promoção exige evidência, contrato, build e rollback.
```

---

## Mapa de camadas

| Camada | Papel | Pode orientar decisão? |
|---|---|---|
| `engine/rmr/include` | contrato público/API/ABI | sim |
| `engine/rmr/src` | implementação canônica low-level | sim |
| `engine/rmr/interop` | ASM por ABI/arquitetura | sim, com build guard |
| `Rafaelia/` | incubadora C/ASM/JNI/baremetal | sim, após triagem |
| `tools/baremetal/rafcode_phi/` | micro-base C→ASM→hex | sim, como ponte técnica |
| `_incoming/` | ingestão/pending | não direto; requer triagem |
| `Incluir/` | pacotes, zips, docs, scripts | não direto; requer triagem |
| `addthis/` | evidência/ingestão/histórico | não direto; requer triagem |
| `archive/` | histórico/anterioridade | não como estado atual |
| `bug/` | sandbox/diagnóstico | não promover em bloco |

---

## Estados de promoção

| Estado | Significado |
|---|---|
| `SEMENTE` | ideia/código parcial localizado |
| `INCUBADORA` | protótipo com forma técnica própria |
| `CANDIDATO_CORE` | pronto para desenho de contrato público |
| `CORE_HEADER` | header público criado em `engine/rmr/include` |
| `CORE_IMPL` | implementação criada em `engine/rmr/src` |
| `BUILD_MANIFESTED` | adicionado ao manifesto canônico, quando há `.c/.S` |
| `VALIDADO` | build/teste/CI ou selftest anexado |
| `ROLLBACK_READY` | caminho de reversão conhecido |

---

## Protocolo de promoção

1. Identificar origem: `Rafaelia`, `_incoming`, `Incluir`, `addthis`, `tools/baremetal`.
2. Classificar licença/autoria/proveniência.
3. Verificar se é C, ASM, shell, Java/Kotlin, zip, doc ou binário.
4. Ler o arquivo; não promover por nome.
5. Verificar se há duplicata canônica ou versão mais nova.
6. Separar conceito de implementação.
7. Se for low-level, criar primeiro contrato em `engine/rmr/include`.
8. Só criar `.c` se existir implementação que precisa compilar.
9. Se criar `.c`/`.S`, atualizar `engine/rmr/sources_rmr_core.cmake`.
10. Não editar `sources_rmr_core.mk` manualmente; ele é gerado.
11. Criar doc ativo explicando o papel e riscos.
12. Registrar rollback.
13. Marcar lacunas como `TOKEN_VAZIO`, não inventar completude.

---

## Regras para ASM

```text
ASM curto não é necessariamente fraco.
ASM repetido não é necessariamente duplicado.
ASM pending não é lixo.
ASM precisa de ABI, arquitetura, entrada, saída e clobber documentados.
```

Antes de promover ASM:

- indicar arquitetura (`armv7`, `aarch64`, `x86_64`, etc.);
- indicar registradores usados;
- indicar alinhamento;
- indicar se é hot path;
- indicar se depende de guard CMake;
- indicar fallback C quando necessário;
- indicar se entra em `interop` ou permanece em incubadora.

---

## Regras para C/header

```text
.h público = contrato.
.c = corpo.
static inline = hot path curto.
macro = contrato de compilação.
void = fronteira possível.
warning = sinal possível.
```

Não mover C para `engine/rmr/src` sem definir:

- header correspondente;
- dependências;
- guard baremetal/JNI/host;
- ABI;
- teste mínimo;
- manifesto;
- rollback.

---

## Regra para BITWALK/BITGHOST

BITWALK/BITGHOST já possuem semente/documentação em `Rafaelia/` e `docs/`.

Antes de promover para `engine/rmr`:

1. Comparar com `tools/baremetal/rafcode_phi/c/rafcode_phi_vecbit.c`.
2. Comparar com `engine/rmr/src/rmr_tcg_cache.c`.
3. Decidir se o primeiro passo é `engine/rmr/include/rmr_bitwalk.h` header-only.
4. Só criar `.c` se houver estado/memória que não caiba em `static inline`.
5. Documentar como não substitui CRC, page table, ISOraf nem TCG cache.

---

## Proibição

```text
não promover pasta inteira
não substituir canônico por incubadora em bloco
não apagar origem após promoção sem preservar histórico
não normalizar estilo antes de entender intenção
não usar zip como fonte direta de build
não chamar protótipo de produção sem build
```

---

## Checklist mínimo

| Item | Obrigatório? |
|---|---|
| origem identificada | sim |
| licença/autoria | sim |
| papel técnico | sim |
| header/API | se virar core |
| implementação | se compilar |
| manifesto | se houver `.c/.S` no build |
| doc ativa | sim |
| teste/selftest | desejável/necessário para core |
| rollback | sim |

---

## Frase final

```text
A incubadora guarda potência; o core exige contrato.
```
