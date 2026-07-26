# Vectras VM Android ↔ Termux RAFCODE-Φ IPC v3

**Base Vectras:** `a29392e65948463ab9cb6dbfefe64eb060e23a07`  
**Base Termux:** `508efb3d01594cc20d51b7fb01d5f2a790169bf2`  
**Estado:** `IMPLEMENTED / EXECUTION_PENDING`  
**Claim:** `claim_allowed=false`

## Finalidade

O protocolo v3 fecha duas ambiguidades do v2:

1. o limite passa a ser **32 argumentos totais**, incluindo 11 argumentos fixos de segurança;
2. o retorno do `PendingIntent` mutável só é aceito quando existe um pedido imutável correspondente no armazenamento interno da Vectras.

## Compatibilidade confirmada com o Termux RAFCODE-Φ

O fork usa por padrão:

```text
package = com.termux.rafacodephi
service = com.termux.app.RunCommandService
action = com.termux.rafacodephi.RUN_COMMAND
permission = com.termux.rafacodephi.permission.RUN_COMMAND
runner = app-shell
```

O bundle de resultado usa:

```text
result
├── stdout
├── stdout_original_length
├── stderr
├── stderr_original_length
├── exitCode
├── err
└── errmsg
```

O v2 já lia `stdout`, `stderr` e `exitCode`. O v3 também preserva o erro interno do Termux e detecta truncamento por meio dos comprimentos originais.

## Argumentos

Argumentos fixos:

```text
-accel tcg
-display none
-monitor none
-serial stdio
-no-reboot
-name vectras-termux-ipc-v3
```

Limites:

```yaml
max_total_arguments: 32
fixed_arguments: 11
max_extra_arguments: 21
max_argument_length: 256
max_argument_bytes: 4096
```

Argumentos extras não podem substituir opções fixas nem ativar `-daemonize`, `-pidfile`, `-qmp`, `-readconfig` ou `-writeconfig`.

## Sequência de custódia

```text
validar binário e argumentos
→ construir argv final
→ criar transaction_id
→ gerar SHA-256 do envelope canônico
→ persistir pedido imutável
→ criar PendingIntent explícito e mutável
→ despachar RunCommandService
→ receber bundle
→ localizar pedido local
→ comparar transaction/binary/request hash
→ gerar receipt minimizado
```

Se o pedido não puder ser persistido, o dispatch não ocorre. Se o retorno não possuir pedido local correspondente, ele é ignorado.

## Pedido imutável

Schema:

```text
raf.vectras-termux-request.v3
```

O pedido contém alvo, serviço, action, permission, command path, runner, workdir, argumentos, hashes, horário e `claim_allowed=false`.

O hash do pedido usa um envelope com nomes e valores prefixados por comprimento, evitando colisões de concatenação ambígua.

## Receipt

Schema:

```text
raf.android-runtime-receipt.v2
```

O receipt não persiste stdout, stderr ou errmsg brutos. Registra:

- bytes retornados;
- comprimentos originais;
- flags de truncamento;
- SHA-256 de stdout/stderr/errmsg;
- exit code;
- erro interno Termux;
- hash de entrada e saída;
- efeitos observados;
- F_ok, F_gap e F_next;
- `claim_allowed=false`.

## Semântica de erro

No Termux, `exitCode != 0` significa que o processo executado terminou com código não zero. Isso não é a mesma coisa que erro interno do serviço. O erro interno é indicado por `err != 0`.

Estados principais:

```yaml
TERMUX_INTERNAL_ERROR: err != 0
EXECUTION_EXIT_TOKEN_VAZIO: bundle presente sem exitCode
EXECUTED_EXIT_ZERO: err == 0 e exitCode == 0
EXECUTED_NONZERO: err == 0 e exitCode != 0
RESULT_BUNDLE_TOKEN_VAZIO: bundle ausente
```

## Fronteira epistemológica

```text
dispatch aceito != processo executado
exit code recebido != guest boot
QEMU iniciado != VM correta
stdout contém texto de boot != boot provado
```

Mesmo com receipt de exit code:

```yaml
android_build: TOKEN_VAZIO
permission_grant: TOKEN_VAZIO
dispatch_execution: TOKEN_VAZIO
qemu_guest_boot: TOKEN_VAZIO
vm_correctness: TOKEN_VAZIO
claim_allowed: false
```

## Gate estático

```sh
python3 tools/verify_vectras_termux_ipc_v3.py \
  --output artifacts/c07/vectras-termux-ipc-v3.json
```

O gate cruza o contrato JSON, fontes Kotlin e manifests. Ele falha se limites divergirem, pedido não for persistido antes do dispatch, erro/truncamento forem ignorados, raw output for armazenado ou o receiver for exportado.

## Gate físico

O C07 só fecha após:

1. builds Vectras e Termux identificados por hash;
2. permissão concedida no aparelho;
3. pedido persistido;
4. dispatch aceito;
5. bundle real recebido;
6. receipt v2 armazenado;
7. request/receipt hashes reconciliados.

Guest boot pertence ao C08 e permanece separado.