# VECTRA_RAFAELIA_RUNTIME_ENTRYPOINTS_NOTE

## Estado

`FATO_DOCUMENTADO`: nota de entrada sobre onde a metodologia RAFAELIA pode dialogar com o runtime canônico.

---

## Pontos canônicos lidos

| Arquivo | Papel |
|---|---|
| `NativeFastPath.java` | adaptador fino do caminho nativo principal |
| `LowLevelBridge.java` | wrapper Java para operações low-level com fallback |
| `HardwareProfileBridge.java` | snapshot de ABI, SIMD e perfil de hardware |
| `RuntimeContract.java` | contrato estável de sessão/runtime |
| `VmFlowNativeBridge.java` | ponte opcional para estado nativo de fluxo VM |
| `VmFlowTracker.java` | tracker canônico de fluxo VM com auditoria |

---

## Decisão

RAFAELIA deve dialogar primeiro com diagnóstico, contrato e fluxo, não com o hot path de produção.

```text
entrada segura: RuntimeContract / VmFlowTracker / HardwareProfileBridge
entrada bloqueada: substituir vectra_core_accel ou LowLevelBridge
```

---

## Classificação

| Item | Estado |
|---|---|
| RAFAELIA como diagnóstico de hardware | `CANDIDATO_SEGURO` |
| RAFAELIA como hot path de produção | `BLOQUEADO_SEM_VALIDACAO` |
| RAFAELIA como substituto de engine/rmr | `NAO_AUTORIZADO` |
| RAFAELIA como contrato/metodologia observável | `F_NEXT_MAPEAR` |

---

## Próximo F_NEXT

1. Mapear quais campos de `RuntimeContract` poderiam receber estado experimental sem quebrar produção.
2. Mapear como `VmFlowTracker` pode registrar eventos RAFAELIA como auditoria, sem mudar execução.
3. Só depois avaliar wrapper experimental.

---

## Frase final

```text
O primeiro encaixe correto da RAFAELIA no app não é acelerar: é observar, diagnosticar, registrar e comparar com o caminho canônico.
```
