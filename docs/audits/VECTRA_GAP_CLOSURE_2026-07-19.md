# Vectra gap closure — 2026-07-19

## Escopo

Este registro acompanha o trabalho executado na branch `agent/vectra-gap-closure-zipraf-20260719` sem promover código adicionado a runtime comprovado.

## Bloco fechado por implementação

### ZIPRAF direct runtime

- parser de local-file header clássico;
- exigência do método ZIP `STORE`;
- rejeição de criptografia;
- rejeição de data descriptor;
- rejeição explícita de ZIP64 até parser dedicado;
- validação de tamanhos e limites do arquivo;
- validação de nome contra NUL, caminho absoluto e `..`;
- derivação do extent do payload;
- CRC-32 esperado associado ao extent;
- verificação CRC-32 do payload;
- mmap somente do extent, em vez do arquivo inteiro;
- fechamento do descritor quando a construção falha;
- preservação dos estágios lógicos `BUFFER`, `L1_HOT`, `L2_SHARED`;
- preservação das lanes determinísticas `0..7`;
- preservação da invariante de bits fixos.

## Testes adicionados

1. janelas dos três estágios e lane de oito cores;
2. parser e extent correto;
3. leitura do payload sem cópia intermediária do arquivo completo;
4. CRC-32 válido;
5. CRC-32 inválido após mutação;
6. rejeição de data descriptor;
7. rejeição de path traversal;
8. rejeição de payload truncado;
9. rejeição de extent vazio;
10. rejeição de método não-STORE;
11. preservação dos bits fixos.

## Estado epistemológico

```text
code_added                 = true
tests_added                = true
static_review              = performed
gradle_test_executed       = TOKEN_VAZIO
android_instrumented_test  = TOKEN_VAZIO
arm32_device               = TOKEN_VAZIO
arm64_device               = TOKEN_VAZIO
central_directory_check    = TOKEN_VAZIO
zip64                      = TOKEN_VAZIO
performance_benchmark      = TOKEN_VAZIO
claim_allowed              = false
```

## TAIL do fragmento

```yaml
tail:
  traceability:
    repository: rafaelmeloreisnovo/Vectras-VM-Android
    branch: agent/vectra-gap-closure-zipraf-20260719
    base: master
    date: 2026-07-19
  authorship:
    upstream_lineage: Vectras VM Android
    modification_author: Rafael Melo Reis project workflow
  intent:
    purpose: close direct-memory ZIPRAF safety and integrity gaps
    distribution: draft review only
  license:
    inherited_scope: repository license map applies
    new_code_spdx: TOKEN_VAZIO pending directory-wide legal decision
  evidence:
    source_review: PRESENT
    tests_present: PRESENT
    tests_executed: TOKEN_VAZIO
    device_runtime: TOKEN_VAZIO
```

## Gaps prioritários remanescentes

### P0

- CI real no commit da branch;
- APK/AAB com SHA-256 e ABI report;
- ARM32 e ARM64 instrumentados;
- boot mínimo de VM;
- proveniência de `libXlorie.so`, rootfs, BIOS e OVMF;
- decisão `NAOCOMERCIAL × GPLv2`;
- SPDX final de `engine/rmr/**`.

### P1

- cross-check entre local header e central directory;
- suporte ZIP64 ou recusa documentada permanente;
- benchmark mmap versus leitura convencional;
- telemetria de page faults/RSS;
- migração de comando QEMU string para `argv` estruturado;
- prova de caminho JNI versus fallback Java.

## Regra de promoção

```text
ADDED
→ TEST_EXECUTED
→ ANDROID_VERIFIED
→ ARM32_ARM64_VERIFIED
→ RELEASE_EVIDENCE_RECORDED
```

Nenhuma etapa pode ser pulada por documentação.

## Retroalimentação

- `F_ok`: o leitor direto deixa de confiar em offsets totalmente externos e reduz o mapeamento ao payload validado.
- `F_gap`: central directory, CI, dispositivo e desempenho continuam sem prova.
- `F_next`: executar a suíte no commit do PR e acrescentar cross-check do central directory antes de aceitar ZIP não confiável.
