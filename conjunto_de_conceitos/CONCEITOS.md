# Conjunto de Conceitos — engenharia de baixo nível, autoral e auditável

Catálogo dos conceitos aplicados neste diretório, com **comparação honesta ao
que existe no mercado**, a aplicação concreta aqui, a licença de cada análogo e
o status de verificação. Nenhuma afirmação por inferência: cada item diz se foi
verificado por **execução** (host), por **codegen/disassembly** (ARM, sem qemu)
ou se é **LACUNA** declarada.

Princípio: os conceitos do mercado são *ideias* (livres). A **expressão**
(código) aqui é **autoral, clean-room**, escrita do zero — ver
`PROVENANCE_AND_LICENSES.md`.

---

## 1. Syscall cru (freestanding, sem libc)

- **O que é:** falar direto com o kernel Linux por `svc #0` (aarch64) /
  `swi #0` (armv7) / `syscall` (x86_64), sem C runtime, sem `libc`.
- **Mercado / análogos:** `musl` (MIT), `glibc` (LGPL), `bionic` do Android
  (BSD-2/Apache-2), `nolibc` do kernel Linux (GPL-2.0). Todos *implementam* o
  mesmo conceito; aqui a expressão é própria (`include/abi.h`).
- **Aplicação:** `cdc_sc()` e fachadas `cdc_write/read/close/exit`. Números de
  syscall em hexadecimal, por arquitetura.
- **Status:** FATO_VERIFICADO — `_start` cru linka e executa (host x86_64);
  aarch64 linka sem símbolos indefinidos (sem libc).

## 2. SIMD NEON + blocagem por linha de cache

- **O que é:** processar 16 bytes por instrução com registradores Q (128 bits),
  iterando em blocos de 64 B (uma linha de cache L1) para casar com a
  hierarquia de memória (L1/L2 → RAM).
- **Mercado / análogos:** rotinas SIMD de `glibc`/`bionic` (memcpy/memset),
  `liboil`/`Agner Fog` (referências de ideia). Licenças variam (LGPL, BSD).
- **Aplicação:** `prim.c` → `memcpy`/`memset` NEON e o kernel de redução
  `cdc_sum8_simd` (acumulação em 16 lanes via `uaddlp`/`uadalp`).
- **Status:** FATO_VERIFICADO (codegen) — disassembly do alvo aarch64 mostra
  11 instruções NEON no kernel; resultado numérico idêntico ao escalar no host.

## 3. Branchless / void / sem heap / sem GC

- **O que é:** minimizar ramificações no caminho quente, usar `void`/`u8*`
  onde crítico, armazenamento estático (`.bss`) em vez de `malloc`. Sem coletor
  de lixo, sem alocador, sem destrutores.
- **Mercado / análogos:** estilo de kernels de DSP, `liburing`, código de
  bootloader. Conceito de domínio público.
- **Aplicação:** buffer de 1 MiB em `.bss` (`g_buf`), fachadas de syscall sem
  ramo, parsing de flags por comparação direta. Zero `malloc` no binário.
- **Status:** FATO_VERIFICADO — `size` mostra `data=0`, todo dinamismo é `.bss`;
  nenhum símbolo de alocador no ELF.

## 4. TCP cru + HTTP/1.0 (aquisição de dados estilo BBS)

- **O que é:** `socket`/`connect` por syscall e um GET HTTP/1.0 montado à mão;
  menu numérico de seleção como nas BBS dos anos 90, com defaults.
- **Mercado / análogos:** `curl` (curl license, MIT-like), `wget` (GPL-3.0),
  `busybox wget` (GPL-2.0), `libcurl`. Aqui nada é copiado: protocolo montado
  a partir do RFC (ideia), não do código deles.
- **Aplicação:** `net.c` → `cdc_http_get_ip` (IP literal) e `cdc_dns_a`
  (resolver A mínimo sobre UDP). Menu em `disc.c`.
- **Status:** FATO_VERIFICADO (codegen aarch64 + compila/linka host);
  **runtime de rede NÃO VERIFICADO** neste ambiente (depende de egress do
  dispositivo). **LACUNA declarada: HTTPS/TLS** não é suportado (exige módulo
  de criptografia autoral — ver §7).

## 5. Medição honesta (benchmark)

- **O que é:** contar ciclos com `cntvct_el0`/`cntfrq_el0` (aarch64) ou `rdtsc`
  (host), comparar SIMD vs escalar, reportar igualdade do resultado como prova
  de correção.
- **Mercado / análogos:** `google/benchmark` (Apache-2.0), `perf` (GPL-2.0).
  Conceito livre; expressão própria.
- **Aplicação:** `disc.c` → `bench()` mede redução de 1 MiB e imprime ciclos +
  speedup×100 (sem ponto flutuante).
- **Status:** FATO_VERIFICADO (host) — `soma(simd)==soma(escalar)=173015040`
  (= 0xA5 × 1 MiB), ciclos medidos. No host, NEON está desligado → speedup ≈ 1×
  (declarado sem maquiar); o ganho real aparece em hardware ARM com NEON.

## 6. CLI: logotipo ASCII colorido + flags + defaults

- **O que é:** identidade visual em ASCII art com cores ANSI escritas direto no
  fluxo, parser de flags com `--no-color`, `-h`, e default para menu.
- **Mercado / análogos:** `figlet` (conceito), `ncurses` (MIT-like). Aqui sem
  dependência: escapes ANSI crus.
- **Status:** FATO_VERIFICADO (host) — `--logo`, `--help`, `--bench`, `--caps`,
  `--conceitos`, `--menu` executam e renderizam.

## 7. LACUNAS declaradas (próximos módulos autorais)

| Lacuna | Por que | Próximo passo autoral |
|---|---|---|
| TLS/HTTPS | sem criptografia não há `https://` | módulo `tls.c` clean-room (curva/AEAD) |
| DNS robusto | só 1 registro A, sem cache/EDNS | endurecer `cdc_dns_a` |
| Runtime ARM | sem qemu/dispositivo aqui | rodar `make termux` no Android e anexar log |
| Paralelismo multi-core | hoje single-thread | `clone`/futex autoral para 8 cores |

---

## Tabela-resumo de status

| Conceito | Verificação | Classe |
|---|---|---|
| syscall cru | host roda + aarch64 linka | FATO_VERIFICADO |
| NEON + cache | disassembly aarch64 (11 instr) | FATO_VERIFICADO (codegen) |
| sem heap/branchless | `data=0`, sem alocador no ELF | FATO_VERIFICADO |
| TCP/HTTP | codegen + compila; rede | PARCIAL (runtime NÃO VERIFICADO) |
| benchmark | host: soma idêntica | FATO_VERIFICADO |
| CLI/logo/flags | host executa tudo | FATO_VERIFICADO |
| HTTPS/TLS | ausente | LACUNA |
| multi-core | ausente | LACUNA |
