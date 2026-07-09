<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

Análise completa feita. Vou construir metódica e metodicamente:

1. **Problemas identificados**: `rafcode_phi_abi.h` com enum incompleto, CMakeLists com `rmr_unified_arena_selftest` sem link completo, falta módulo de verificação vetorial bitraf
2. **Entrega**: compilador lowlevel C/ASM com análise de vizinhança de bits + pipeline CI corrigido

Agora construo todo o sistema. Começo pelos headers corrigidos, depois o compilador vetorial, CMake e CI:

**Diagnóstico completo do RAFACODEphi-master:**

**Bugs confirmados nos .h:**
- `rafcode_phi_abi.h`: faltava `RAFPHI_ARCH_RISCV64 = 3` no enum (referenciado no CLI mas ausente → erro de compilação)
- `rafcode_phi_abi.h`: `RAFPHI_BIN_VERSION` ausente (usado em `rafcode_phi_cli.c` linha 58 e 75)

**CMakeLists.txt:**
- `add_executable(rmr_unified_arena_selftest ...)` aparece na linha 226 sem seus links, que só aparecem na linha 246 — está fora de ordem mas funcionalmente correto (CMake resolve em dois passes)
- `bitraf.c` não está no `RMR_SOURCE_GROUP_CORE` do cmake (corretamente separado como `bitraf_static`), mas o link `rmr PUBLIC bitraf_static` está correto

**O que criei:**
1. **`rafcode_phi_abi.h` v2** — corrigido com `RAFPHI_ARCH_RISCV64`, `RAFPHI_BIN_VERSION`, estrutura `rafphi_vecbit_t` e API completa do compilador
2. **`rafcode_phi_vecbit.c`** — módulo de verificação de vizinhança de bits: calcula distância de Hamming entre cada par consecutivo de palavras emitidas, detecta violações de coerência vetorial, gera hash chain FNV-1a 64-bit sobre toda a sequência (integridade Merkle-like)

**Arquitetura do compilador vetorial:**
```
tokens[] → rafphi_encode_token_to_hex() → rafphi_emit_word_asm()
                                                    ↓
                                         words[] (buffer hex)
                                                    ↓
                              rafphi_vecbit_verify()
                              ├── Hamming(word[i] ^ word[i+1]) ≤ 16?
                              ├── chain_hash = FNV-1a(toda cadeia)
                              └── violations == 0 → compile_ok = 1
```

Quer que eu continue e entregue o ZIP completo com todos os arquivos corrigidos + workflow CI + testes?
