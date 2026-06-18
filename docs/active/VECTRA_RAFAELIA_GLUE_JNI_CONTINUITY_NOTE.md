# VECTRA_RAFAELIA_GLUE_JNI_CONTINUITY_NOTE

## Estado

`FATO_DOCUMENTADO`: nota curta de continuidade sobre `Rafaelia/rafaelia_glue.c` e `Rafaelia/rafaelia_jni_direct.c`.

---

## Leitura de continuidade

`rafaelia_glue.c` foi lido como agregador C puro da incubadora RAFAELIA. Ele reúne módulos em uma sequência operacional: perfil de hardware, vCPU, camadas de memória, commit gate, BitStacks, senoides, CRC chain, GPU probe e relatório final.

`rafaelia_jni_direct.c` foi lido como ponte JNI direta usando `DirectByteBuffer`, com arena estática, estado global, CRC32C local, `processNative`, `stepNative`, `profileNative`, `arenaSizeNative` e `crc32Native`.

---

## Discernimento aplicado

A sequência observada agora é:

```text
B1–B4
→ BitRAF geométrico
→ Orquestrador CPU/GPU/memória
→ Glue C puro
→ JNI DirectByteBuffer
→ possível ponte Java/app
```

Isso não autoriza promoção para `engine/rmr`. Autoriza a próxima leitura: mapear o lado Java/app que chama essas funções.

---

## Classificação

| Item | Estado |
|---|---|
| `Rafaelia/rafaelia_glue.c` | `INCUBADORA_AGREGADORA` |
| `Rafaelia/rafaelia_jni_direct.c` | `INCUBADORA_JNI_BRIDGE` |

---

## Limites

```text
não declarar integração app sem encontrar chamada Java/Kotlin correspondente;
não declarar build JNI sem Android.mk/CMake confirmado;
não trocar DirectByteBuffer por cópia;
não promover para core sem contrato;
não declarar performance sem benchmark;
```

---

## Próximo F_NEXT

1. Buscar `RafaeliaCore` no app/Java/Kotlin.
2. Buscar `processNative`, `stepNative`, `profileNative`, `arenaSizeNative`, `crc32Native`.
3. Mapear a ponte Java/Kotlin → JNI → RAFAELIA.
4. Só depois propor integração, isolamento, build gate ou documentação de lacuna.

---

## Frase final

```text
Glue consolida a incubadora em C; JNI DirectByteBuffer abre a porta para o app. A ponte só vira fato completo quando o lado Java/Kotlin for mapeado.
```
