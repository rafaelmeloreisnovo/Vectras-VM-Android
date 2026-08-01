# ZIPRAF Corpus U2 V1

Status: `CORPUS_HARNESS / REAL_EXTERNAL_CORPUS_TOKEN_VAZIO`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`

## 1. Papel de U2

U0 localiza spans. U1 calcula identidade. U2 percorre conjuntos de arquivos e produz um manifesto por arquivo e por entrada.

```text
corpus
→ arquivo ZIP/APK candidato
→ identidade do arquivo
→ parser U0
→ ação por entrada
→ digests U1
→ marcadores estruturais
→ rejeições
→ manifesto append-only
```

O scanner não extrai, não carrega código e não autoriza execução.

## 2. Manifesto por arquivo

Cada arquivo registra:

```text
input_name
input_path
container_kind
mapping_epoch
archive_bytes
archive_sha256
archive_blake3
parse state/error
layout_fingerprint64
APK structural markers
entries[]
```

`APK_CANDIDATE_BY_EXTENSION` e `apk_structure_candidate=true` são classificações. Não demonstram assinatura Android, instalabilidade, DEX válido ou execução.

## 3. Manifesto por entrada

```text
entry_id
name
local_header_offset
payload_offset
stored_size
logical_size
method
crc32
action
state_flags
stored_sha256
stored_blake3
logical_digest_state
execution_authorized=false
dma_authorized=false
```

A ação continua pertencendo à álgebra:

```text
DIRECT_MAP_LAYOUT | COPY_STORE | DECOMPRESS | REJECT
```

## 4. Limites de segurança

- máximo de 256 MiB por arquivo no scanner de referência;
- máximo estrutural herdado de 64 entradas no parser V2;
- ZIP64 permanece fora do perfil;
- arquivos inválidos são registrados como `PARSE_REJECTED`, sem abortar a observação dos demais;
- caminhos inseguros e entradas não autorizadas permanecem `REJECT`;
- bytes comprimidos recebem digest `STORED_BYTES`, mas o digest lógico DEFLATE continua `MATERIALIZATION_REQUIRED`;
- digest não concede assinatura, autoria, execução ou DMA.

## 5. Harness determinístico

O gate cria três arquivos reais no filesystem do runner:

```text
sample.zip
  readme.txt          STORE
  packed.txt          DEFLATE

sample.apk
  AndroidManifest.xml STORE
  classes.dex         STORE
  resources.arsc      DEFLATE
  assets/data.bin     STORE

malformed.zip
  assinatura local truncada
```

O scanner roda duas vezes sobre os mesmos arquivos. Os manifestos devem ser byte a byte idênticos.

Resultado esperado:

```text
archives = 3
parsed = 2
parse_rejected = 1
entries = 6
extraction_performed = false
execution_authorized = false
```

## 6. Estado epistemológico

O harness prova que o scanner consegue observar arquivos reais gerados no runner e produzir um manifesto determinístico. Ele não é ainda um corpus externo independente.

```yaml
u2_harness: IMPLEMENTED
deterministic_manifest: GATE_REQUIRED
real_external_zip_apk_corpus: TOKEN_VAZIO
signed_production_apk: TOKEN_VAZIO
android_installation: TOKEN_VAZIO
execution: false
claim_allowed: false
```

## 7. Próximo corpus

Um corpus externo deve entrar como fonte imutável, com:

```text
source URI or repository
source commit/release
file SHA-256
license/provenance
size limit
expected parser state
manifest SHA-256
rejection report
```

Arquivos pessoais ou APKs privados não devem ser publicados. Para eles, o mesmo scanner pode ser executado em Termux e somente o receipt não sensível deve ser federado.

## R3

```text
F_ok:
  scanner, manifesto por arquivo/entrada, APK markers, rejeições,
  digests U1 e determinismo do harness foram materializados.

F_gap:
  gate remoto, corpus externo, APK assinado, DEFLATE lógico,
  Android/Termux e política de privacidade do corpus real.

F_next:
  executar o gate; depois selecionar um corpus público pequeno e pinado,
  mantendo corpus privado apenas local com receipts sanitizados.
```
