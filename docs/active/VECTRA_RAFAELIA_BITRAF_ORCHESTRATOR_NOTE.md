# VECTRA_RAFAELIA_BITRAF_ORCHESTRATOR_NOTE

## Estado

`FATO_DOCUMENTADO`: nota curta de continuidade sobre `Rafaelia/rafaelia_bitraf.c` e `Rafaelia/rafaelia_orchestrator.c`.

---

## Leitura de continuidade

`rafaelia_bitraf.c` foi lido como incubadora BitRAF geométrica, com matriz de pontos, camadas de bits, travessia, integridade e rollback mínimo.

`rafaelia_orchestrator.c` foi lido como camada de orquestração RAFAELIA, conectando vCPU, memória, hardware, integridade e execução experimental.

---

## Discernimento aplicado

O BitRAF em `Rafaelia/` não deve ser confundido com o BitRAF canônico do `engine/rmr`.

```text
Rafaelia/rafaelia_bitraf.c = incubadora geométrica
engine/rmr/bitraf.*       = contrato canônico de engine
```

---

## Classificação

| Item | Estado |
|---|---|
| `Rafaelia/rafaelia_bitraf.c` | `INCUBADORA_COM_VALOR` |
| `Rafaelia/rafaelia_orchestrator.c` | `INCUBADORA_COM_VALOR` |
| `engine/rmr/bitraf.*` | `CORE_CANONICO` |

---

## Limites

```text
não promover em bloco;
não unificar sem comparação;
não declarar build sem execução;
não declarar performance sem benchmark;
```

---

## Próximo F_NEXT

1. Ler `Rafaelia/rafaelia_glue.c`.
2. Ler `Rafaelia/rafaelia_jni_direct.c`.
3. Mapear a ponte RAFAELIA → JNI/app.
4. Só depois propor integração, isolamento ou build gate.

---

## Frase final

```text
BitRAF RAFAELIA é incubadora; BitRAF RMR é contrato canônico. O orquestrador mostra direção de integração, mas ainda exige validação.
```
