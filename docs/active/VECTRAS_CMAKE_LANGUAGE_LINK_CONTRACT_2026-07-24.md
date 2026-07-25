<!-- DOC_TAXONOMY_SYNC: 2026-07-24 | role: active-build-contract -->

# Vectras — contrato CMake de linguagem, flags e link

## Metadados canônicos

- Data: `2026-07-24`.
- Escopo: `CMakeLists.txt`, `engine/platform/android/CMakeLists.txt` e `app/src/main/cpp/CMakeLists.txt`.
- Natureza: correção de contrato estático; não é benchmark nem execução em device.
- Estado do contrato estático: `IMPLEMENTED`.
- Execução do workflow no head: `TOKEN_VAZIO` até run.
- Build Android NDK ARM32/ARM64 no head: `TOKEN_VAZIO` até run.
- Implementação do link final freestanding dedicado: `IMPLEMENTED`.
- Prova remota do link final: `TOKEN_VAZIO` até run.

## Problema observado

O projeto é declarado como:

```cmake
project(... C ASM)
```

Mas o caminho Android mantinha:

- flags exclusivas de C++ (`-fno-rtti`, `-fno-exceptions`);
- uma variável de flags exportada sem consumidor localizado;
- nível `-O2` na plataforma enquanto outro perfil podia aplicar `-O3`;
- `target_link_options(... -nostdlib ...)` em uma biblioteca `STATIC`.

Esses pontos não eram equivalentes entre si:

```text
flag declarada != flag consumida
archive STATIC != link final
compile freestanding != artefato final freestanding
warning silenciada != contrato resolvido
```

## Correções

### 1. Política Android consumida por target

`engine/platform/android/CMakeLists.txt` agora define:

```cmake
vectra_apply_android_platform_c_flags(target)
```

A chamada é adiada com `cmake_language(DEFER)` até o target `rmr` existir.

As opções são limitadas a `COMPILE_LANGUAGE:C` e por ABI:

- `arm64-v8a`: `armv8-a+crc+simd`;
- `armeabi-v7a`: ARMv7, Thumb, NEON e softfp;
- `x86_64`: SSE4.2 + POPCNT;
- `x86`: SSSE3, SSE4.2 + POPCNT.

A plataforma não escolhe mais `-O2/-O3`; essa decisão pertence ao perfil chamador.

### 2. Flags C++ removidas do target C/ASM

O bloco `VECTRA_FREESTANDING_COMPILE_OPTIONS` removeu:

```text
-fno-rtti
-fno-exceptions
```

E reforçou o contrato C:

```text
-ffreestanding
-fno-builtin
-Werror=implicit-function-declaration
```

### 3. Fronteira de link corrigida e probe separado

`abi_core_freestanding` é um archive `STATIC`. Ele não executa o linker final; portanto, opções como `-nostdlib`, `--gc-sections` e `--build-id` naquele target não provavam um link freestanding.

A propriedade explícita do archive ficou:

```text
VECTRA_FREESTANDING_LINK_STATE=CONSUMED_BY_DEDICATED_LINK_PROBE
```

O target `vectra_freestanding_link_probe` agora:

- possui `vectra_freestanding_probe_entry` como entry point;
- consome `abi_core_freestanding`;
- recebe `-nostdlib`, `--gc-sections`, `--no-undefined` e map file somente na fronteira de link final;
- não usa JNI, Android log nem headers hosted;
- é auditado por `readelf`, `nm -u` e `objdump`.

O artefato JNI `vectra_core_accel` continua hosted por desenho e não é usado como prova do contrato freestanding.

## Gate automático

Arquivo:

```text
tools/ci/validate_cmake_language_link_contract.py
```

Verifica:

- linguagens `C ASM`;
- ausência de variável morta anterior;
- aplicação ao target `rmr`;
- escopo `COMPILE_LANGUAGE:C`;
- ausência de flags C++;
- ausência de otimização na plataforma;
- presença de `-ffreestanding` e `-fno-builtin`;
- ausência do falso `target_link_options` no archive;
- presença do target de link final, entry point e auditor de ELF;
- matriz de CI host, `armeabi-v7a` e `arm64-v8a`.

Workflow:

```text
.github/workflows/cmake-language-link-contract.yml
```

## Próximos gates

```yaml
C0_STATIC_CONTRACT: IMPLEMENTED
C1_WORKFLOW_EXECUTION: TOKEN_VAZIO
C2_ANDROID_NDK_ARM32_BUILD: TOKEN_VAZIO
C3_ANDROID_NDK_ARM64_BUILD: TOKEN_VAZIO
C4_UNDEFINED_SYMBOL_AUDIT: PASS_LOCAL_HOST
C5_DEDICATED_FREESTANDING_LINK_PROBE: PASS_LOCAL_HOST
C6_DEVICE_SMOKE: TOKEN_VAZIO
```

## Retroalimentação

```text
F_ok   = flags C/ASM corrigidas; falso gate por comentário removido; probe host reproduzível
F_gap  = execução CI/NDK ARM32+ARM64; BLAKE3 local; device opcional
F_next = publicar branch, executar matriz remota e anexar manifests por ABI
```
