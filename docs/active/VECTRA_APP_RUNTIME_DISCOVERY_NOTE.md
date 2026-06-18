# VECTRA_APP_RUNTIME_DISCOVERY_NOTE

## Estado

`FATO_DOCUMENTADO`: nota curta de descoberta sobre o runtime canônico do app.

---

## Descoberta

O app canônico possui uma rota nativa própria em `app/src/main/cpp` e uma rota Java própria em `app/src/main/java/com/vectras/vm/core`.

Arquivos observados:

| Arquivo | Papel observado |
|---|---|
| `app/src/main/cpp/CMakeLists.txt` | define biblioteca nativa principal e módulos auxiliares |
| `app/src/main/cpp/lowlevel_bridge.c` | ponte nativa de operações low-level |
| `app/src/main/cpp/hardware_profile_bridge.c` | ponte nativa de perfil de hardware |
| `app/src/main/java/com/vectras/vm/core/LowLevelBridge.java` | wrapper Java do caminho low-level |
| `app/src/main/java/com/vectras/vm/core/HardwareProfileBridge.java` | wrapper Java de perfil de hardware |
| `app/src/main/java/com/vectras/vm/core/NativeFastPath.java` | adaptador fino do caminho nativo principal |

---

## Decisão de leitura

A metodologia RAFAELIA deve ser comparada com essa rota canônica, não confundida com ela.

```text
RAFAELIA = incubadora metodológica e experimental
app/core = caminho canônico de produção
```

---

## Lacuna protegida

`TOKEN_VAZIO_APP_BRIDGE`: até esta rodada, não foi confirmado consumo direto da ponte Java RAFAELIA pelo app canônico.

---

## Próximo F_NEXT

Ler `NativeFastPath.java`, `LowLevelBridge.java`, `HardwareProfileBridge.java` e os pontos que consomem essas classes no app.

---

## Frase final

```text
O app já tem rota nativa canônica; RAFAELIA deve ser encaixada por comparação e validação, não por substituição automática.
```
