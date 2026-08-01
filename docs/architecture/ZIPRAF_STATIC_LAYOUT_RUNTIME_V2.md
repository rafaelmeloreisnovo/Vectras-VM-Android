# ZIPRAF Static Layout Runtime V2

Status: `IMPLEMENTED_REFERENCE / CLAIM_LIMITED`  
Data: `2026-08-01`  
Claim global: `claim_allowed=false`

## 1. Decisão de aplicação

A implementação foi aplicada no `Vectras-VM-Android` porque este repositório já concentra os três mecanismos necessários:

1. parser e validação ZIP clássico `STORE`;
2. runtime de janelas mapeadas com `FileChannel.map`;
3. contrato C `rmr_static_layout` para `Base + StableOffset`.

O `Mapa` permanece como plano de controle federado. Termux, GAIA e PCR continuam como fontes/adaptadores até possuírem execução específica equivalente. Não foi copiado código idêntico para todos os repositórios.

## 2. Invariante executável

```text
logical_address(region, local_offset)
  = zip_payload_base
  + region.stable_offset
  + local_offset
```

A base virtual do mapping pode variar. O manifesto preserva a geometria relativa:

```text
A(o, mapping_epoch) = B(mapping_epoch) + Delta(o)
```

Logo:

```text
FIXED_OFFSET != FIXED_VIRTUAL != FIXED_PHYSICAL
```

O adaptador ZIPRAF aceita somente `BASE_RELATIVE`. `FIXED_PHYSICAL` e regiões `PHYSICAL_FIXED` são rejeitados.

## 3. Mudanças no runtime

### Antes

O construtor tentava mapear o payload integral e mantinha o limite:

```text
payloadOffset + payloadSize <= Int.MAX_VALUE
```

Depois, cada chamada de `window()` criava outro mapping. Isso combinava dois modelos concorrentes e desperdiçava espaço virtual.

### Agora

O runtime:

- não mapeia o payload inteiro ao abrir;
- cria mappings delimitados pelo tamanho L2;
- reutiliza o mapping ativo quando a próxima janela está contida nele;
- fatia `ByteBuffer` somente para a janela solicitada;
- mantém handles absolutos limitados à sessão/época;
- registra operações, reutilizações, bytes e latências.

A mudança remove o limite artificial de uma única `MappedByteBuffer` de até 2 GiB. Cada mapping individual continua limitado e auditável.

## 4. Manifesto C-compatible

`ZiprafStaticLayoutManifest` espelha os campos alimentados por `RmR_StaticLayout_ManifestSignature`:

```text
abi_version
layout_epoch
total_size
base_alignment
region_count
base_policy
regions[]
  region_id
  offset
  size
  alignment
  fixed_offset_mask
  fixed_offset_value
  mobility
  semantic_state
  flags
```

A assinatura usa FNV-1a 64 em ordem little-endian, igual à implementação C.

Vetor conhecido do gate:

```text
signature C      = dc16075f7047df36
signature Kotlin = dc16075f7047df36
```

Isso demonstra identidade do manifesto entre as duas camadas delimitadas. Não é hash criptográfico nem prova de autenticidade externa.

## 5. Estados e bloqueios

Somente regiões `PRESENT` podem ser resolvidas como payload.

```text
ABSENT       -> rejeitado
EMPTY        -> rejeitado para leitura de payload
PRESENT      -> permitido dentro dos bounds
FAULT        -> rejeitado
TOKEN_VAZIO  -> rejeitado
```

O validador também rejeita:

- ABI incompatível;
- alinhamento inválido;
- overflow/bounds;
- IDs duplicados;
- regiões sobrepostas;
- bits fixos incompatíveis;
- manifesto maior que o payload;
- base virtual/física fixa no adaptador ZIPRAF;
- região física fixa;
- span maior que a região ou que a janela selecionada.

## 6. Métricas observáveis

`ZiprafRuntimeMetricsSnapshot` registra:

```text
mapOperations
mapReuseHits
bytesMapped
bytesExposed
crcBytesRead
mappingSamples
mapLatencyP50Nanos
mapLatencyP95Nanos
mapLatencyP99Nanos
mappingReuseRatio
```

As latências são observacionais e não possuem threshold universal no gate. Resultados de runner compartilhado não devem ser promovidos como benchmark de hardware Android.

## 7. CRC e SHA-256

O CRC permite um `ByteArray` fornecido pelo chamador:

```text
verifyCrc32(expected, caller_owned_scratch)
```

Assim, verificações repetidas podem reutilizar o mesmo scratch. O caminho padrão ainda cria um único array por chamada e permanece compatível com `minSdk 23`.

O SHA-256 da política passou a consumir diretamente o `ByteBuffer` mapeado, eliminando a criação de um `ByteArray` para cada janela.

## 8. Gate focal

```sh
bash tools/zipraf/run_zipraf_host_kat.sh
```

O KAT V2 cobre 31 condições, entre elas:

- central directory e CRC;
- conteúdo e lane;
- mapping delimitado;
- reutilização do cache;
- métricas e percentis ordenados;
- scan multifilamento lógico;
- assinatura C/Kotlin idêntica;
- resolução de região;
- reuso de offsets;
- bloqueio de `FAULT`;
- recibo com `claim_allowed=false`;
- rejeição de overlap;
- rejeição de `FIXED_PHYSICAL`;
- política SHA-256 positiva e negativa.

Workflow:

```text
.github/workflows/zipraf-static-layout-runtime.yml
```

O relatório `result.json` é publicado como artefato por 30 dias.

## 9. Limites preservados

```yaml
host_kat: PASS_LOCAL
android_runtime: TOKEN_VAZIO
android_aslr_fixed_virtual: REJECTED_BY_POLICY
physical_address_fixed: REJECTED_BY_POLICY
zip64: TOKEN_VAZIO
payload_over_2gib_real_archive: TOKEN_VAZIO
zero_copy_global: false
zero_allocation_global: false
independent_reproduction: TOKEN_VAZIO
claim_allowed: false
```

## 10. Próxima aplicação por dependência

A ordem coerente após esta unidade é:

1. executar o gate focal no GitHub Actions;
2. executar instrumented test no Android 10/14/15;
3. produzir arquivo real acima de 2 GiB ou teste esparso equivalente;
4. comparar `mapOperations`, page faults e RSS com o runtime anterior;
5. emitir receipt por dispositivo/ABI;
6. somente então atualizar Mapa e Drive de `HOST_PASS` para `ANDROID_EVIDENCED`.

## R3

```text
F_ok:
  ZIPRAF e RMR agora compartilham manifesto e assinatura;
  mmap integral de abertura foi removido;
  cache e métricas foram materializados.

F_gap:
  execução Android, ZIP64, arquivo real >2 GiB e reprodução independente.

F_next:
  CI focal -> instrumented Android -> benchmark comparativo com receipts.
```
