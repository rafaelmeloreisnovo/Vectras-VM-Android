# VECTRA_FREESTANDING_VOID_CONTRACT

## Estado

`FATO_DOCUMENTADO`: contrato para o uso de `void` no freestanding/baremetal Vectras/RAFAELIA.

Este documento registra que `void` não deve ser lido apenas como “sem retorno”. No low-level do projeto ele pode representar fronteira de mundo, endereço cru, corte de warning, tipo opaco, stub deliberado ou ausência materializada.

---

## Frase canônica

```text
No freestanding Vectras, void é fronteira: onde o C comum esperaria objeto, o engine pode estar preservando ABI, endereço, efeito ou ausência deliberada.
```

---

## Papéis do void

| Forma | Leitura comum | Leitura Vectras |
|---|---|---|
| `void*` | ponteiro genérico | endereço cru sem semântica de objeto |
| `void fn(...)` | função sem retorno útil | efeito de máquina / barreira / mutação de estado |
| `(void)x` | silenciar warning | corte intencional de parâmetro/sinal |
| `typedef void tipo_t` | tipo incompleto | objeto impossível/opaco no mundo freestanding |
| `(void*)0` | NULL | ausência de endereço, não necessariamente erro lógico |

---

## Regras de auditoria

Antes de alterar qualquer uso de `void`:

1. Verificar se o arquivo compila em `RMR_BAREMETAL`, `RMR_JNI_BUILD` ou host.
2. Verificar se o `void` preserva assinatura ABI/API.
3. Verificar se o `void` evita dependência de libc/stdint/stdio.
4. Verificar se `(void)x` está cortando warning intencional.
5. Verificar se `typedef void` representa tipo opaco deliberado.
6. Verificar se troca por tipo concreto puxaria símbolo, struct, header ou lib.
7. Classificar como `FATO`, `WARNING_INTENCIONAL`, `TOKEN_VAZIO` ou `BUG_REAL`.

---

## Casos típicos

### Ponteiro cru

```c
void *p;
```

Leitura correta:

```text
endereço sem contrato de objeto de alto nível
```

### No-op intencional

```c
(void)p;
```

Leitura correta:

```text
parâmetro recebido para compatibilidade, mas sem consumo neste mundo de build
```

### Tipo opaco/impossível

```c
typedef void rmr_file_t;
```

Leitura correta:

```text
arquivo não existe no baremetal; a assinatura sobrevive sem puxar filesystem/libc
```

### Retorno void

```c
void fence(void);
```

Leitura correta:

```text
a função é efeito/ordenação, não valor
```

---

## O que não fazer

```text
não trocar typedef void por struct sem medir dependência
não remover (void)x só para “limpar”
não transformar stub void em implementação hosted dentro do baremetal
não interpretar (void*)0 como falha sem contexto
não substituir void* por tipo forte se isso quebra ABI ou puxa header/lib
```

---

## Relação com outros contratos

- `LOWLEVEL_WARNING_INTENT_CONTRACT.md`
- `LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`
- `VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`
- `VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT.md`
- `engine/rmr/include/rmr_baremetal_compat.h`
- `engine/rmr/include/rmr_vectra_os.h`

---

## Ledger

| Estado | Objeto | Regra |
|---|---|---|
| `FATO` | `void*` | endereço cru |
| `FATO` | `(void)x` | corte ou consumo intencional |
| `FATO` | `typedef void` | tipo opaco/impossível |
| `FATO` | `void fn` | efeito sem valor de retorno |
| `TOKEN_VAZIO` | uso não classificado | não alterar sem contexto |

---

## Frase final

```text
Void não é vazio inútil; no freestanding ele pode ser a forma correta de proteger a fronteira entre mundos.
```
