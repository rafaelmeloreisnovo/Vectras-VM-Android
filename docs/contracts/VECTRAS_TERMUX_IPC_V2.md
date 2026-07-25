# Vectras VM Android ↔ Termux RAFCODE-Φ IPC v2

## Problema corrigido

O protocolo anterior retornava caminhos absolutos dentro do sandbox privado do
Termux. Um caminho existente no processo produtor não constitui um caminho
executável pelo aplicativo consumidor.

```text
path discovery != cross-app execution
```

## Protocolo v2

1. Vectras descobre somente capacidades e nomes de binários.
2. A consulta usa nonce; respostas fora da transação são ignoradas.
3. Nenhum `$PREFIX`, `$HOME` ou caminho privado atravessa o broadcast.
4. Vectras declara e verifica a permissão do `RunCommandService`.
5. `VectrasTermuxBridge` exige `vmRequired=true` e despacha somente QEMU permitido.
6. Argumentos com NUL ou quebra de linha são rejeitados.
7. Um `PendingIntent` recebe exit code; stdout/stderr brutos não são persistidos.
8. O receipt guarda apenas tamanhos e SHA-256 de stdout/stderr.
9. A VM permanece em safe state até o gate explícito; exit code não equivale a guest boot.

## Estado

```yaml
discovery_v2: IMPLEMENTED
nonce_binding: IMPLEMENTED
bounded_dispatcher: IMPLEMENTED
manifest_permission_and_query: IMPLEMENTED_FOR_DEBUG_RELEASE_PERFRELEASE
async_exit_receipt: IMPLEMENTED
android_build: TOKEN_VAZIO
device_permission_grant: TOKEN_VAZIO
dispatch_execution_receipt: TOKEN_VAZIO
guest_boot: TOKEN_VAZIO
claim_allowed: false
```

## Gate estático

```bash
python3 tools/verify_vectras_termux_ipc_v2.py
```

Resultado local limitado:

```yaml
static_validator: PASS
kotlin_syntax_with_android_stubs: PASS
apk_build: TOKEN_VAZIO
physical_device: TOKEN_VAZIO
```

## R3

```text
F_ok   = capability discovery, nonce, permissão, dispatcher limitado e receipt minimizado
F_gap  = build APK, grant no device, receipt real e guest boot
F_next = compilar os dois APKs e executar o primeiro dispatch com vmRequired=true
```
