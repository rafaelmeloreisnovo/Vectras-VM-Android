# ZIPRAF no Vectras — acesso direto

O runtime aceita apenas extents ZIP `STORE` e mapeia o arquivo em modo somente leitura. A janela devolvida aponta diretamente para a região do payload, sem materialização intermediária e sem rotina de descompressão.

## Fluxo

```text
arquivo STORE
→ mapeamento somente leitura
→ BUFFER
→ L1_HOT
→ L2_SHARED
→ lane determinística 0..7
→ operação Vectra
```

`BUFFER`, `L1_HOT` e `L2_SHARED` são níveis lógicos de trabalho. O processo Android não reivindica controle físico da cache do processador.

## Invariantes

- método diferente de `STORE` é recusado;
- o extent precisa caber integralmente no arquivo;
- `coreCount` fica entre 1 e 8;
- bits marcados por `fixedMask` mantêm `fixedValue`;
- o mapeamento é somente leitura;
- nenhuma imagem de VM é modificada por esse leitor.

## Estado

```text
Kotlin standalone KAT = PASS_LOCAL
Android device execution = TOKEN_VAZIO
physical cache residency = TOKEN_VAZIO
performance benefit = TOKEN_VAZIO
claim_allowed = false
```
