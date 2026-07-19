# ZIPRAF direct — sessão, política e fronteiras

## 1. Dois formatos que não devem ser confundidos

O repositório contém dois mecanismos com nomes relacionados, mas contratos diferentes.

### A. Arquivo ZIP padrão `STORE`

Implementação:

```text
ZiprafDirectRuntime.kt
ZiprafDirectStoreSession.kt
ZiprafDirectEntryPolicy.kt
```

Finalidade:

- ler entrada de um arquivo ZIP clássico;
- recusar compressão;
- validar EOCD, central directory, local header e CRC-32;
- mapear janelas somente leitura;
- entregar `ByteBuffer` direto a consumidores Java/Kotlin/JNI;
- não reconstruir o payload inteiro.

### B. Bloco customizado `ZiprafCore`

Implementação:

```text
app/src/main/java/com/vectras/vm/rafaelia/connector/ZiprafCore.java
```

Finalidade atual:

- formato próprio com magic `ZIPR`;
- DEFLATE opcional;
- SHA-256;
- CRC32C;
- sharding;
- materialização e remontagem de arrays.

Portanto:

```text
ZIP padrão STORE direto
!=
bloco customizado ZIPRAF/SHARDS
```

Nenhum deles substitui silenciosamente o outro.

## 2. Sessão de consumo direto

A API:

```kotlin
ZiprafDirectStoreSession.open(
    file = archive,
    entryName = "runtime/core.bin",
    plan = ZiprafRuntimePlan(coreCount = 8)
).use { session ->
    val result = session.scan(
        stage = ZiprafMemoryStage.L1_HOT,
        routeSeed = 0,
        startOffset = 0,
        maxBytes = Long.MAX_VALUE
    ) { window ->
        // window.bytes é direto e somente leitura.
        // Pode ser encaminhado para parser, codec ou JNI.
    }
}
```

O resultado registra:

```text
entryName
startOffset
bytesVisited
windowCount
laneMask
```

### Invariantes

- a entrada foi validada pelo central directory;
- CRC-32 é verificado por padrão;
- cada janela exposta respeita `maxBytes`;
- a última janela é limitada ao intervalo restante;
- o buffer exposto permanece direto e somente leitura;
- lanes são derivadas deterministicamente;
- não existe concatenação obrigatória do payload.

## 3. Gate de política/manifesto

A classe:

```kotlin
ZiprafDirectEntryPolicy(
    entryName = "runtime/core.bin",
    maxPayloadBytes = 64L * 1024 * 1024,
    expectedPayloadBytes = 4096,
    expectedCrc32 = 0x12345678,
    expectedSha256Hex = "...64 hex..."
)
```

é aplicada por:

```kotlin
ZiprafDirectPolicyVerifier.open(file, policy, plan)
```

### Ordem de validação

```text
nome
→ central directory
→ local header
→ método STORE
→ bounds
→ tamanho máximo
→ tamanho exato opcional
→ CRC-32 do arquivo
→ CRC-32 esperado opcional
→ SHA-256 esperado opcional
→ sessão aberta
```

### Evidência retornada

```text
entryName
payloadBytes
crc32
sha256Hex
centralDirectoryValidated
```

O SHA-256 é calculado por varredura de janelas, sem exigir um único array com o payload integral.

## 4. Uso Java/JNI

As fábricas são `@JvmStatic`/`@JvmOverloads`. Código Java pode abrir a sessão e consumir cada `ByteBuffer` direto por callback.

Fronteira prevista:

```text
arquivo ZIP STORE
→ ZiprafDirectPolicyVerifier
→ ZiprafDirectStoreSession.scan
→ ByteBuffer direto somente leitura
→ JNI GetDirectBufferAddress
→ engine/rmr
```

A chamada JNI real ainda precisa de um adapter explícito com:

- limite e offset em `long`;
- proibição de retenção do ponteiro após retorno;
- código de erro tipado;
- checksum/contador de bytes;
- cancelamento;
- teste ARM32 e ARM64.

## 5. Gates locais

### KAT mínimo

```bash
bash tools/zipraf/run_zipraf_host_kat.sh
```

### Gate agregado

Somente host:

```bash
bash tools/zipraf/run_zipraf_local_gate.sh
```

Host + JUnit Gradle:

```bash
RUN_GRADLE=1 bash tools/zipraf/run_zipraf_local_gate.sh
```

Host + JUnit + aparelho conectado:

```bash
RUN_GRADLE=1 RUN_DEVICE=1 bash tools/zipraf/run_zipraf_local_gate.sh
```

Nenhum desses comandos depende de GitHub Actions.

## 6. Estado

```text
central directory              = IMPLEMENTED
cross-check local/central      = IMPLEMENTED
mmap por janela                = IMPLEMENTED
sessão de scan                 = IMPLEMENTED
política de tamanho/CRC/SHA    = IMPLEMENTED
host KAT                       = PASS_15_OF_15
unit test sources              = ADDED
Android harness                = ADDED_NOT_EXECUTED
JNI direct-buffer adapter      = TOKEN_VAZIO
ARM32                          = TOKEN_VAZIO
ARM64                          = TOKEN_VAZIO
performance claim              = PROHIBITED
Actions/YAML                   = DEFERRED_BY_OWNER
claim_allowed                  = false
```
