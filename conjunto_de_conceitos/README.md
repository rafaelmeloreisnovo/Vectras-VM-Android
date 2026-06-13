# conjunto_de_conceitos — `discador`

CLI **freestanding** (sem libc, sem heap, sem GC) que reúne, num só lugar, um
**conjunto de conceitos** de engenharia de baixo nível — syscall cru, SIMD
NEON com blocagem de cache, branchless, TCP/HTTP estilo BBS dos anos 90,
benchmark e logotipo ASCII colorido — para **arm64/arm32 (Termux/Android)**,
com uma **porta de referência x86_64** que roda no host para validar a lógica.

Tudo é **autoral / clean-room**, alinhado ao `AUTHORSHIP_CLEANROOM_PLAN.md`.
Comparações de mercado e licenças em `PROVENANCE_AND_LICENSES.md`; o catálogo
conceitual em `CONCEITOS.md`.

## Estrutura

```
conjunto_de_conceitos/
├── include/abi.h          # ABI freestanding: syscalls svc/swi/syscall, contadores
├── src/prim.c             # memcpy/memset NEON + kernel SIMD de redução
├── src/net.c              # TCP cru + HTTP/1.0 GET + DNS A mínimo
├── src/disc.c             # _start cru, logo ASCII, flags, menu BBS, bench
├── Makefile               # termux | arm64 | arm32 | host | verify
├── tools/verify_codegen.sh# prova por cross-compile + disassembly (sem qemu)
├── CONCEITOS.md           # conjunto de conceitos + mercado + status
└── PROVENANCE_AND_LICENSES.md
```

## Build

No **Termux (Android, aarch64)** — compilador nativo:

```sh
pkg install clang lld binutils
make termux        # gera ./discador
./discador         # logo + menu interativo
```

Cross / host:

```sh
make arm64         # discador.arm64  (aarch64-linux-android)
make arm32         # discador.arm32  (armv7 + NEON)
make host          # discador.host   (x86_64, roda no PC para testar a lógica)
make verify        # checa link sem libc + NEON no codegen
```

Requisitos: `clang` + `lld` (linker freestanding). Sem nenhuma outra dependência.

## Flags

```
discador [flags]
  --logo       só o logotipo
  --bench      executa o benchmark e sai
  --caps       imprime contadores (cntvct/cntfrq) e sai
  --conceitos  imprime o conjunto de conceitos
  --fetch      demo de GET HTTP por IP literal
  --no-color   desliga ANSI
  --menu       menu interativo (default sem args)
  -h|--help    ajuda
```

## Status de verificação (honesto, sem inferência)

| Verificação | Resultado |
|---|---|
| aarch64 linka freestanding | OK, **0 símbolos indefinidos** (sem libc) |
| NEON no kernel SIMD (aarch64) | **11 instruções** confirmadas por disassembly |
| armv7 + NEON compila | OK |
| host x86_64 executa a lógica | OK (logo, flags, menu, bench) |
| benchmark correto | `soma(simd) == soma(escalar) = 173015040` (= 0xA5 × 1 MiB) |
| `data` segment | **0 bytes** (todo dinamismo em `.bss`, sem heap) |
| runtime ARM em dispositivo | **NÃO VERIFICADO** aqui (sem qemu) → rodar `make termux` |
| HTTPS/TLS | **LACUNA** declarada (só http puro) — ver `CONCEITOS.md` §7 |

Reproduza com `make verify` (não exige qemu nem dispositivo ARM).

## Limites declarados

- Sem TLS: `https://` não é suportado (precisaria de um módulo de criptografia
  autoral). O `--fetch` usa IP literal e http puro; em rede bloqueada ele
  degrada com mensagem clara, não trava.
- Single-thread por ora; paralelismo multi-core (8 cores via `clone`/futex) é
  próximo passo declarado.
- A porta x86_64 é **só referência de execução** da lógica: ela não ativa NEON
  (usa o caminho escalar), então o speedup real só aparece em ARM.
