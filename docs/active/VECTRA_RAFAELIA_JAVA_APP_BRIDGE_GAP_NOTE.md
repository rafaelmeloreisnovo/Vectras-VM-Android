# VECTRA_RAFAELIA_JAVA_APP_BRIDGE_GAP_NOTE

## Estado

`FATO_DOCUMENTADO`: nota de continuidade sobre a ponte Java/JNI RAFAELIA e a lacuna de integração com o app canônico.

---

## Leitura de continuidade

`Rafaelia/RafaeliaCore.java` foi lido como bridge Java da incubadora RAFAELIA. Ele usa `DirectByteBuffer`, carrega a biblioteca `rafaelia_core` e declara os métodos nativos correspondentes ao JNI direto.

A busca por consumo equivalente dentro de `app/src/main` não confirmou chamada canônica no app nesta rodada.

---

## Sequência confirmada

```text
Rafaelia/rafaelia_jni_direct.c
→ pacote JNI: com.termux.rafaelia.RafaeliaCore
→ Rafaelia/RafaeliaCore.java
→ DirectByteBuffer IN/OUT/STATE
```

---

## Classificação

| Item | Estado |
|---|---|
| `Rafaelia/RafaeliaCore.java` | `INCUBADORA_JAVA_BRIDGE` |
| `Rafaelia/rafaelia_jni_direct.c` | `INCUBADORA_JNI_BRIDGE` |
| `app/src/main` consumo direto | `TOKEN_VAZIO_APP_BRIDGE` |

---

## Limites

```text
não declarar app integrado sem chamada canônica;
não mover Java da incubadora para app sem build contract;
não mudar pacote JNI sem revisar assinatura nativa;
não trocar DirectByteBuffer por byte array no hot path;
não declarar performance sem benchmark;
```

---

## Próximo F_NEXT

1. Buscar módulos `app/src/main/java/com/vectras/vm/rafaelia`.
2. Ler classes de runtime/telemetry/benchmark relacionadas.
3. Mapear se a ponte RAFAELIA está duplicada, ausente ou planejada no app canônico.
4. Criar mapa `VECTRA_RAFAELIA_APP_RUNTIME_BRIDGE_MAP.md`.

---

## Frase final

```text
A ponte Java/JNI RAFAELIA existe na incubadora; a integração com o app canônico ainda é lacuna protegida até leitura do runtime Android.
```
