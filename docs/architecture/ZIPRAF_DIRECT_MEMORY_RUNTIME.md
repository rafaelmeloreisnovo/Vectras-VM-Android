# ZIPRAF no Vectras — acesso direto validado por extent

O runtime aceita somente entradas ZIP clássicas com método `STORE` e mapeia em modo somente leitura **apenas o extent do payload**, sem materialização intermediária e sem rotina de descompressão.

## Fluxo endurecido

```text
arquivo ZIP
→ parser do local-file header
→ validação de assinatura, flags, método, tamanhos, nome e bounds
→ extent STORE com CRC-32 esperado
→ mmap somente do payload
→ BUFFER / L1_HOT / L2_SHARED
→ lane determinística 0..7
→ verificação CRC-32 opcional/obrigatória pelo chamador
→ operação Vectra
```

`BUFFER`, `L1_HOT` e `L2_SHARED` são níveis lógicos de trabalho. O processo Android não reivindica controle físico de cache L1/L2 do processador.

## Invariantes implementadas

- assinatura do local-file header deve ser `0x04034b50`;
- entrada criptografada é recusada;
- entrada com data descriptor é recusada, pois tamanhos e CRC precisam existir antes do mapeamento;
- método diferente de ZIP `STORE` é recusado;
- sentinela ZIP64 é recusada até existir parser próprio;
- tamanhos comprimido e descomprimido precisam ser iguais;
- extent vazio é recusado;
- metadados e payload precisam caber integralmente no arquivo;
- nomes absolutos, com NUL ou `..` são recusados;
- nomes não UTF-8 só são aceitos quando integralmente ASCII;
- o mapeamento cobre somente `payloadOffset..payloadOffset+payloadSize`;
- o extent mapeado precisa caber na capacidade de `ByteBuffer`;
- `coreCount` fica entre 1 e 8;
- bits marcados por `fixedMask` mantêm `fixedValue`;
- o mapeamento e todas as janelas são somente leitura;
- nenhuma imagem de VM é modificada por esse leitor;
- CRC-32 do payload pode ser conferido contra o valor do header.

## Cobertura de regressão adicionada

`ZiprafDirectRuntimeTest` cobre agora:

1. três estágios lógicos e oito lanes;
2. derivação de extent pelo local header;
3. leitura direta do payload;
4. CRC-32 válido;
5. mutação de payload detectada por CRC;
6. rejeição de data descriptor;
7. rejeição de path traversal;
8. rejeição de payload truncado;
9. rejeição de extent vazio;
10. rejeição de método não-STORE;
11. preservação de bits fixos.

## Limites preservados como `TOKEN_VAZIO`

- cruzamento com central directory;
- ZIP64;
- múltiplos local headers encadeados;
- benchmark mmap versus `FileChannel.read`/stream;
- execução instrumentada em Android ARM32;
- execução instrumentada em Android ARM64;
- medição de page faults/RSS;
- residência física em cache;
- ganho de desempenho;
- política de unmap explícito, que não faz parte da API pública Java padrão.

## Estado desta mudança

```text
implementação parser STORE        = ADDED
mapeamento somente do extent      = ADDED
verificação CRC-32                = ADDED
testes de regressão               = ADDED_NOT_EXECUTED_IN_THIS_BRANCH
CI do commit                      = TOKEN_VAZIO
Android device execution          = TOKEN_VAZIO
physical cache residency          = TOKEN_VAZIO
performance benefit               = TOKEN_VAZIO
claim_allowed                     = false
```

A promoção para `VERIFIED` exige Gradle/JUnit real no commit da branch e teste instrumentado em dispositivo. O local header, isoladamente, não prova concordância com o central directory; portanto, distribuição hostil ou não confiável continua bloqueada até o cross-check correspondente.
