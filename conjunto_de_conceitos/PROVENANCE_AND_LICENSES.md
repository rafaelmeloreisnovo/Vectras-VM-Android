# Procedência e Licenças — conjunto_de_conceitos

Alinhado ao `AUTHORSHIP_CLEANROOM_PLAN.md` do repositório: cada artefato com
autoria, licença e procedência explícitas; **conceitos** de terceiros são
permitidos (ideias livres), **expressão** (código/texto) de terceiros **não** é
copiada.

## Declaração de autoria (clean-room)

- **Autor:** Rafael Melo Reis.
- **Método:** todo o código deste diretório foi escrito do zero a partir de
  requisitos funcionais e de especificações de protocolo/ABI (RFCs, manuais de
  syscall, ISA ARM), **sem** copiar código, comentários, nomes ou estruturas de
  qualquer projeto externo.
- **Sem reuso textual:** nenhuma linha foi derivada de `curl`, `wget`, `musl`,
  `glibc`, `bionic`, `busybox` ou similares. Esses projetos aparecem apenas como
  **comparação de mercado**, não como fonte de cópia.

## Licença dos artefatos novos

`SPDX-License-Identifier: GPL-2.0-only` em cada arquivo, por **coerência com a
licença do repositório** (`LICENSE` = GNU GPL v2). Se o autor quiser
relicenciar este subdiretório (ex.: MIT/0BSD por ser 100% autoral), pode
fazê-lo por ser o titular — registrar a decisão aqui antes de mudar o cabeçalho.

## Matriz de procedência por arquivo

| path | owner | origem | licença | risco derivação | status |
|---|---|---|---|---|---|
| `include/abi.h` | Rafael Melo Reis | autoral (ABI/ISA pública) | GPL-2.0-only | nenhum | A (autoral) |
| `src/prim.c` | Rafael Melo Reis | autoral (NEON intrinsics públicas) | GPL-2.0-only | nenhum | A |
| `src/net.c` | Rafael Melo Reis | autoral (RFC 1035/2616 — ideia) | GPL-2.0-only | nenhum | A |
| `src/disc.c` | Rafael Melo Reis | autoral | GPL-2.0-only | nenhum | A |
| `Makefile` | Rafael Melo Reis | autoral | GPL-2.0-only | nenhum | A |
| `tools/verify_codegen.sh` | Rafael Melo Reis | autoral | GPL-2.0-only | nenhum | A |
| docs `*.md` | Rafael Melo Reis | autoral | GPL-2.0-only | nenhum | A |

## Registro de inspiração (apenas conceitos, sem cópia de expressão)

| Ferramenta de mercado | Licença | Conceito observado | O que NÃO foi feito |
|---|---|---|---|
| musl libc | MIT | wrappers de syscall freestanding | não copiamos seu `syscall.h` nem nomes |
| Linux `nolibc` | GPL-2.0 | `_start` sem crt | entrada reescrita do zero |
| bionic (Android) | BSD-2 / Apache-2 | memcpy/memset SIMD | kernels NEON próprios |
| curl / libcurl | curl (MIT-like) | cliente HTTP | request HTTP/1.0 montada do RFC |
| wget | GPL-3.0 | download por CLI | nenhuma linha reaproveitada |
| busybox | GPL-2.0 | utilitários mínimos | só a ideia de "mínimo que basta" |
| google/benchmark | Apache-2.0 | medição de microbench | contador próprio (`cntvct`/`rdtsc`) |
| figlet | conceito | logotipo ASCII | arte ASCII desenhada à mão |

## Compatibilidade legal

- Comparar com GPL-3.0 (`wget`) e Apache-2.0 **não** contamina: não há
  vínculo de código, apenas observação conceitual.
- Intrínsecos NEON (`arm_neon.h`) e cabeçalhos de ISA são interface, não
  obra protegida; seu uso não cria derivação.
- Recomendado adicionar, no nível do repo, um CI que bloqueie arquivos sem
  cabeçalho SPDX (já previsto no backlog do `AUTHORSHIP_CLEANROOM_PLAN.md`).
