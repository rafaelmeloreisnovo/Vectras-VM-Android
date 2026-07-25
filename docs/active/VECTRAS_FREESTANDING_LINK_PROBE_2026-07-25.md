<!-- DOC_TAXONOMY_SYNC: 2026-07-25 | role: active-build-evidence -->

# Vectras — probe de link final freestanding

## Escopo

Pendência canônica: `#1065`.

Objetivo:

```text
objetos C freestanding
→ archive abi_core_freestanding
→ consumidor ELF dedicado
→ link -nostdlib
→ inspeção de símbolos
→ manifesto por ABI
```

O probe não é o JNI hosted `vectra_core_accel` e não executa em aparelho durante o gate de link.

## Implementação

### Target e entry point

`app/src/main/cpp/CMakeLists.txt` cria, sob
`VECTRA_BUILD_FREESTANDING_LINK_PROBE=ON`:

```text
vectra_freestanding_link_probe_<abi>
```

O arquivo `freestanding_link_probe_entry.c`:

- inclui somente a ABI low-level canônica;
- mantém uma referência real a `abi_entry_validate_interop`;
- expõe `vectra_freestanding_probe_entry`;
- não chama syscall, heap, JNI, logcat ou libc;
- entra em laço sem retorno porque execução em device é gate separado.

As opções exclusivas do consumidor final são:

```text
-nostdlib
--gc-sections
--build-id=none
--no-undefined
-e vectra_freestanding_probe_entry
-Map vectra_freestanding_link_probe.map
```

Android também recebe `-z max-page-size=16384`.

### Auditor

`tools/ci/audit_freestanding_link_probe.py` produz o manifesto
`vectra.freestanding-link-probe.v1` e bloqueia:

- entry ELF diferente do símbolo controlado;
- símbolo indefinido fora da allowlist, vazia por padrão;
- biblioteca `NEEDED`;
- JNI, Android log, heap, libc e helpers proibidos;
- ausência da testemunha do archive no map e no ELF;
- divergência binária entre duas compilações limpas;
- ausência de BLAKE3 quando o gate remoto usa `--require-blake3`.

O manifesto preserva compiler/linker, comandos efetivos de compile/archive/link,
commit, ABI, SHA-256, BLAKE3, map e resultados de `readelf`, `nm -u` e
`objdump`.

### Matriz remota

O workflow `cmake-language-link-contract.yml` separa:

```yaml
host: x86_64 do runner
android:
  - armeabi-v7a
  - arm64-v8a
ndk: 27.2.12479018
api: 29
page_size_max: 16384
```

Cada variante compila duas vezes, exige binários idênticos e publica ELF, map,
logs e manifesto.

## Evidência local

Ambiente: Ubuntu x86_64, GCC 13.3.0 e GNU binutils 2.42.

```yaml
STATIC_PYTHON_CONTRACT: PASS
STATIC_SHELL_CONTRACT: PASS
HOST_DIRECT_FINAL_LINK: PASS
CONTROLLED_ENTRY: PASS
ARCHIVE_WITNESS: PASS
UNDEFINED_SYMBOLS: []
NEEDED_LIBRARIES: []
FORBIDDEN_SYMBOLS: []
REPRODUCIBLE_TWO_BUILDS: true
ELF_SHA256: 077bf3aca9a980388a6839bac2ec89c078350489e6f2d8731e6c77d402016841
MAP_SHA256: 9fcf2ec12f702af418acc6e63ab9ebf356aa22753a799dfeff66846ab4724e19
BLAKE3_LOCAL: TOKEN_VAZIO_TOOL_UNAVAILABLE
CMAKE_TARGET_LOCAL: TOKEN_VAZIO_TOOL_UNAVAILABLE
SOURCE_COMMIT_LOCAL: TOKEN_VAZIO_LOCAL_UNCOMMITTED
```

O `PASS` local demonstra a fronteira ELF com comandos equivalentes. Ele não
substitui a execução do target CMake no GitHub nem a matriz NDK.

## Gates após implementação local

```yaml
F0_CONTRACT_STATIC: PASS_LOCAL
F1_HOST_PROBE_LINK: PASS_LOCAL_DIRECT_LINK
F2_ARM32_NDK_LINK: TOKEN_VAZIO_UNTIL_REMOTE_RUN
F3_ARM64_NDK_LINK: TOKEN_VAZIO_UNTIL_REMOTE_RUN
F4_UNDEFINED_SYMBOL_AUDIT: PASS_LOCAL_HOST
F5_REPRODUCIBLE_SHA256: PASS_LOCAL_HOST
F5_BLAKE3: TOKEN_VAZIO_UNTIL_REMOTE_RUN
F6_DEVICE_EXECUTION: OPTIONAL_TOKEN_VAZIO
```

## R3

```text
R_ok   = archive consumido por ELF real; zero undefined/NEEDED/proibidos no host
R_gap  = NDK ARM32/ARM64, BLAKE3 e runner remoto ainda sem recibo
R_next = abrir PR, observar a matriz e promover manifests aprovados como evidência
```
