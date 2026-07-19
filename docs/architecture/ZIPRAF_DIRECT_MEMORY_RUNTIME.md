# ZIPRAF no Vectras — acesso direto com validação do arquivo

O runtime aceita somente entradas ZIP clássicas `STORE`, valida o arquivo ZIP e cria mapeamentos somente leitura por janela. Não há descompressão nem materialização integral do payload.

## Fluxo endurecido

```text
arquivo ZIP clássico
→ localização e validação do EOCD
→ validação de arquivo single-disk
→ leitura do central directory
→ seleção nominal e rejeição de duplicata
→ cross-check central directory ↔ local-file header
→ validação de assinatura, flags, método, CRC, tamanhos, nome e bounds
→ extent STORE validado
→ CRC-32 integral
→ mmap somente da janela solicitada
→ BUFFER / L1_HOT / L2_SHARED
→ lane determinística 0..7
→ operação Vectra
```

`BUFFER`, `L1_HOT` e `L2_SHARED` são níveis lógicos de trabalho. Não representam controle físico da cache L1/L2 do processador.

## APIs

### Fronteira de baixo nível

```kotlin
ZiprafStoredEntryParser.parse(file, localHeaderOffset, expectedName)
```

Valida somente o `local-file header`. É útil para arquivos controlados e diagnóstico, mas não promove a entrada para confiança de arquivo completo.

### Fronteira validada

```kotlin
ZiprafArchiveValidator.parseStoredEntry(file, expectedName)
```

Valida EOCD, central directory e concordância com o local header.

### Abertura recomendada

```kotlin
ZiprafDirectRuntime.openValidated(
    file = archive,
    entryName = "runtime/core.bin",
    plan = ZiprafRuntimePlan(coreCount = 8),
    verifyCrc32 = true
)
```

Essa chamada bloqueia a abertura quando a estrutura ou o CRC não correspondem.

## Invariantes implementadas

- assinatura local `0x04034b50`;
- assinatura central `0x02014b50`;
- assinatura EOCD `0x06054b50`;
- arquivo single-disk;
- quantidade de entradas consistente;
- central directory integralmente limitado pelo EOCD;
- ausência de registros inesperados entre central directory e EOCD;
- nome solicitado deve existir uma única vez;
- entrada criptografada recusada;
- data descriptor recusado;
- método diferente de `STORE` recusado;
- ZIP64 recusado até parser dedicado;
- tamanhos comprimido e descomprimido iguais e maiores que zero;
- CRC, flags, método e tamanho iguais entre registro local e central;
- payload não pode sobrepor o central directory;
- nome vazio, absoluto, com NUL, drive ou `..` recusado;
- nome sem flag UTF-8 só é aceito quando ASCII;
- arquivo e extent precisam conter integralmente o payload;
- cada chamada mapeia somente sua janela lógica;
- offsets usam `Long`; o payload completo não precisa caber em um único `ByteBuffer`;
- `coreCount` fica entre 1 e 8;
- bits marcados por `fixedMask` preservam `fixedValue`;
- janelas são somente leitura;
- nenhuma imagem de VM é modificada pelo leitor;
- CRC-32 integral pode ser obrigatório antes da abertura.

## Prova standalone sem Gradle

Comando local:

```bash
bash tools/zipraf/run_zipraf_host_kat.sh
```

O script compila diretamente:

```text
ZiprafDirectRuntime.kt
+
ZiprafDirectRuntimeKat.kt
→ kotlinc
→ JAR standalone
→ execução Java
→ result.json
```

Execução observada em 19 de julho de 2026:

```text
host_arch       = x86_64
kotlinc         = 1.9.0
java            = OpenJDK 21.0.10
checks          = 7/7
status          = PASS
```

Checks executados:

1. central directory validado;
2. tamanho do payload;
3. comprimento da janela;
4. lane determinística;
5. conteúdo da janela;
6. CRC-32;
7. entrada ausente recusada.

Essa prova confirma compilação JVM e comportamento standalone. Ela não substitui Gradle, APK ou dispositivo Android.

## Cobertura unitária adicionada

`ZiprafDirectRuntimeTest` contém cenários para:

1. três estágios e oito lanes;
2. arquivo validado e abertura segura;
3. fronteira local mantida explicitamente como baixo nível;
4. mutação detectada por CRC;
5. divergência de CRC central/local;
6. divergência de tamanho central/local;
7. nomes duplicados;
8. seleção obrigatória em arquivo com múltiplas entradas;
9. entrada ausente;
10. arquivo multi-disk;
11. data descriptor;
12. path traversal;
13. payload truncado;
14. ausência de central directory;
15. extent vazio;
16. método não-STORE;
17. preservação de bits fixos.

## Teste Android e benchmark-harness

O arquivo:

```text
app/src/androidTest/java/com/vectras/vm/vectra/ZiprafDirectRuntimeInstrumentedTest.kt
```

prepara:

- round-trip em armazenamento temporário Android;
- janelas no início, meio e fim;
- oito lanes;
- CRC no dispositivo;
- registro de ABI e SDK;
- harness de 256 janelas sobre payload de 2 MiB;
- relatório JSON local;
- `claim_allowed=false` para o tempo medido.

O teste foi adicionado, mas sua execução ARM32/ARM64 continua pendente.

## Limites preservados

```text
ZIP64                         = BLOCKED_BY_DESIGN
Android ARM32                 = TOKEN_VAZIO
Android ARM64                 = TOKEN_VAZIO
page faults / RSS             = TOKEN_VAZIO
cache física L1/L2            = TOKEN_VAZIO
ganho de desempenho           = TOKEN_VAZIO
comparação mmap/read/stream    = HARNESS_ADDED_NOT_EXECUTED
Gradle/JUnit real              = TOKEN_VAZIO
Actions/YAML                   = DEFERRED_BY_OWNER
claim_allowed                  = false
```

A programação pode continuar pelo gate local, sem depender do GitHub Actions. Promoção para runtime Android exige execução instrumentada em aparelho e registro da evidência correspondente.
