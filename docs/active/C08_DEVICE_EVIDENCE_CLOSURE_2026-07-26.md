# C08 — Device Evidence Closure — 2026-07-26

**Stack base:** `c07/vectras-termux-qemu-receipt-2026-07-26`  
**Estado inicial:** `IMPLEMENTED / PHYSICAL_EXECUTION_PENDING`  
**Claim global:** `claim_allowed=false`

## 1. Objetivo

Fechar uma prova física limitada da cadeia:

```text
Vectras request
→ Termux RunCommandService
→ QEMU
→ guest kernel
→ guest userspace
→ guest shutdown
→ Termux result bundle
→ Vectras receipt
→ ADB device evidence packet
```

O ciclo não considera um texto genérico de boot como prova. A evidência exige nonce aleatório ligado ao pedido e três marcadores ordenados.

## 2. Challenge nonce

A Vectras gera 32 bytes por `SecureRandom` e serializa 64 caracteres hexadecimais.

O nonce entra em:

- argumento do guest: `rafaelia.boot_nonce=<nonce>`;
- envelope canônico do pedido;
- SHA-256 do pedido;
- registro interno append-only;
- receipt final;
- três marcadores do guest.

A chamada física deve usar:

```kotlin
val nonce = VectrasTermuxBridge.newGuestBootNonce()
val kernelToken = VectrasTermuxBridge.guestBootKernelArgument(nonce)

VectrasTermuxBridge.dispatchQemuWithGuestEvidence(
    context = context,
    binaryName = "qemu-system-aarch64",
    arguments = listOf(
        "-kernel", kernelPath,
        "-append", "console=ttyAMA0 $kernelToken",
        "-initrd", initrdPath,
    ),
    vmRequired = true,
    guestBootNonce = nonce,
)
```

O bridge não insere `-append` automaticamente, porque a topologia QEMU depende da arquitetura e da imagem. Ele exige que o token já esteja presente no vetor bounded.

## 3. Marcadores do guest

O arquivo `tools/device/c08_guest_marker.sh` deve ser incorporado ao guest ou initramfs e executado em três fases.

### Early boot

Depois de `/proc` estar montado, ainda no fluxo inicial:

```sh
/c08_guest_marker.sh boot
```

Saída:

```text
RAFAELIA_GUEST_BOOT_V1 nonce=<64hex> arch=<token> kernel=<token>
```

### Userspace pronto

Depois do critério de prontidão escolhido:

```sh
/c08_guest_marker.sh userspace
```

Saída:

```text
RAFAELIA_GUEST_USERSPACE_V1 nonce=<64hex> init=<token>
```

### Shutdown controlado

Imediatamente antes de poweroff/halt/reboot:

```sh
/c08_guest_marker.sh shutdown poweroff
```

Saída:

```text
RAFAELIA_GUEST_SHUTDOWN_V1 nonce=<64hex> reason=poweroff
```

O mesmo nonce deve aparecer nas três linhas e na mesma ordem.

## 4. Estados de evidência dentro da Vectras

```yaml
NOT_REQUESTED: dispatch sem challenge
CHALLENGE_NOT_OBSERVED: nonce solicitado, sem marcador inicial
BOOT_MARKER_ONLY: kernel observado, userspace ausente
USERSPACE_READY_NO_SHUTDOWN: boot e userspace, shutdown ausente
MARKERS_OUT_OF_ORDER: marcadores presentes em ordem inválida
INCOMPLETE_OUTPUT_TRUNCATED: stdout parcial
TERMUX_INTERNAL_ERROR: err interno diferente de zero
COMPLETE_MARKERS_EXIT_TOKEN_VAZIO: sequência completa sem exit code
COMPLETE_MARKERS_EXIT_NONZERO: sequência completa, processo não zero
COMPLETE_ORDERED_EXIT_ZERO: sequência completa, output íntegro e exit zero
```

Somente `COMPLETE_ORDERED_EXIT_ZERO` pode entrar no fechamento físico externo.

## 5. Privacidade

O receiver não persiste stdout, stderr ou errmsg brutos. Ele registra:

- bytes e comprimentos originais;
- flags de truncamento;
- SHA-256 das saídas;
- campos parseados dos marcadores;
- SHA-256 de cada marcador;
- estado da sequência;
- `claim_allowed=false`.

## 6. Coleta ADB

```sh
bash tools/device/c08_collect_device_evidence.sh \
  --serial DEVICE_SERIAL \
  --transaction-id tx-vectras-termux-... \
  --out artifacts/c08-device
```

O coletor:

1. identifica aparelho, Android e ABI;
2. armazena hash do serial, fingerprint e boot ID, não os valores brutos;
3. identifica versões dos pacotes;
4. calcula SHA-256 de todos os APK splits instalados;
5. verifica a permissão `RUN_COMMAND`;
6. lê request e receipt por `adb exec-out run-as`;
7. produz `device_manifest.json`;
8. executa o validador de fechamento.

## 7. Restrições do coletor

O coletor:

- não executa `logcat -c`;
- não usa logcat como evidência primária;
- não usa root;
- não usa `adb pull /data/data`;
- não dispara broadcast ou service de teste;
- não exige receiver exportado;
- não persiste serial ou fingerprint brutos.

Se `run-as` não funcionar, retorna:

```text
BLOCKED_RUN_AS_OR_INTERNAL_FILES
```

Isso normalmente significa build não debuggable ou request/receipt ausente. O bloqueio não é convertido em falha do QEMU nem em PASS por outra rota mais fraca.

## 8. Receipt externo

Schema:

```text
raf.c08-device-evidence-closure.v1
```

Resultado positivo:

```yaml
state: PASS_DEVICE_EVIDENCE_LIMITED
guest_boot_evidence_promotable: true
promotion_scope: ONE_DEVICE_ONE_TRANSACTION_NONCE_BOUND_ORDERED_BOOT_USERSPACE_SHUTDOWN
claim_allowed: false
```

O validador exige:

- transaction ID idêntico;
- request hash idêntico ao input do receipt;
- nonce idêntico e presente no argv;
- bundle e exit receipt;
- `err=0` e `exitCode=0`;
- stdout não truncado;
- três marcadores presentes e ordenados;
- hashes de marcadores;
- request/receipt obtidos por `run-as`;
- Vectras e Termux instalados e hashados;
- permission grant;
- timestamps ordenados.

## 9. Limite da promoção

Mesmo com PASS:

```yaml
guest_boot_on_observed_device: VERIFIED_LIMITED
other_devices: TOKEN_VAZIO
other_images: TOKEN_VAZIO
other_qemu_builds: TOKEN_VAZIO
arm32_device: TOKEN_VAZIO_UNLESS_EXPLICITLY_OBSERVED
arm64_device: TOKEN_VAZIO_UNLESS_EXPLICITLY_OBSERVED
vm_correctness_beyond_markers: TOKEN_VAZIO
performance_claim: FORBIDDEN_OUT_OF_SCOPE
claim_allowed: false
```

## 10. Gates estáticos

```sh
python3 tools/device/c08_verify_static_contract.py \
  --output artifacts/c08-static/static_contract.json

python3 -m unittest tests/test_c08_validate_device_evidence.py -v

bash -n tools/device/c08_collect_device_evidence.sh
sh -n tools/device/c08_guest_marker.sh
```

Gates estáticos provam implementação e coerência do contrato. Não provam aparelho, QEMU ou guest.

## 11. Gate de saída

O C08 permanece `PHYSICAL_EXECUTION_PENDING` até existir um pacote real contendo:

```text
collector_status.json
device_manifest.json
request.json
receipt.json
c08_device_evidence_receipt.json
```

Todos devem estar ligados à mesma transação e ao mesmo boot ID do aparelho observado.
