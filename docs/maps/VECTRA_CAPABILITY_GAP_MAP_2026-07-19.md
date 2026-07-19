# Mapa de Capacidades e Lacunas — Vectras VM

Data: 2026-07-19  
Branch: `claude/vectra-vm-gaps-audit-pvtiki`  
Regra: `arquivo presente != integrado != executado != certificado`

## Objetivo desta primeira parte

Transformar quatro grupos de afirmações em gates verificáveis:

1. navegador/cliente de rede low-level;
2. TLS 1.2/1.3;
3. compilador LowFala e geração de APK;
4. coerência entre APK, DEX, ELF e ABI.

O mapa não promove documentação, nomes ou código solto a produto concluído.

## Estado encontrado

| ID | Capacidade | Evidência localizada | Estado correto | Claim permitido |
|---|---|---|---|---|
| CAP_BROWSER_LOCAL_RMR | TBROWSER `rmr://` | `Incluir/vectras_bbs.c` | `SOURCE_PRESENT_LOCAL_RENDERER` | não |
| CAP_NET_RAW_HTTP | TCP/DNS/HTTP por syscall | `conjunto_de_conceitos/src/net.c` | `IMPLEMENTED_CODEGEN_UNPROVEN_RUNTIME` | não |
| CAP_TLS_12_13_CLEANROOM | TLS 1.2/1.3 autoral | `tls.c` ausente | `TOKEN_VAZIO` | não |
| CAP_LOWFALA_FRONTEND | fala→token→AST→bytecode→ASM→VM | `Incluir/compiladorlowFala.txt` | `DOCUMENT_GENERATOR_UNINTEGRATED` | não |
| CAP_APK_BUILD_PIPELINE | Gradle/assinatura APK | `tools/apk/build_release_signed_local.sh` | `BUILD_PIPELINE_PRESENT_UNPROVEN_ARTIFACT` | não |
| CAP_ELF_CONTRACT_AUDIT | símbolos/seções ELF | auditor e relatório histórico | `AUDIT_PRESENT_HISTORICAL` | não |
| CAP_APK_DEX_ELF_COHERENCE | DEX/ELF/ABI do APK real | novo auditor | `AUDIT_IMPLEMENTED_AWAITS_APK` | não |
| CAP_LOOSE_ARTIFACT_INTAKE | arquivos soltos | novo inventário determinístico | `INVENTORY_IMPLEMENTED` | não |

## Correções de semântica

### “Navegador totalmente ASM”

O código localizado não sustenta essa descrição:

- `Incluir/vectras_bbs.c` é C hosted, usa `stdio`, `stdlib`, `string`, `time` e páginas locais `rmr://` embutidas;
- a função chamada browser apenas renderiza páginas estáticas;
- `conjunto_de_conceitos/src/net.c` é outra peça: cliente HTTP/1.0 e DNS por syscall, sem TLS;
- não existe integração demonstrada entre o renderer local e o cliente HTTP cru.

Logo:

```text
renderer local rmr://                    = SOURCE_PRESENT
cliente HTTP cru por syscall             = IMPLEMENTED_CODEGEN_UNPROVEN_RUNTIME
navegador web ASM integrado              = TOKEN_VAZIO
navegação HTTPS certificada TLS 1.2/1.3 = TOKEN_VAZIO
```

### TLS 1.2/1.3

O próprio `net.c` declara que HTTPS/TLS não é suportado. Não foi localizado `conjunto_de_conceitos/src/tls.c`, suíte criptográfica, parser de certificados X.509, validação de cadeia, SNI, HKDF, AEAD, transcript hash, testes de interoperabilidade ou corpus de vetores oficiais.

“Certificado” só poderá ser usado depois de existir, no mínimo:

```text
implementação de handshake
+ suites declaradas
+ validação X.509
+ hostname verification
+ relógio confiável
+ vetores conhecidos
+ interoperabilidade com servidores independentes
+ logs e hashes do binário
```

### LowFala e compilador APK

`Incluir/compiladorlowFala.txt` contém um monólito shell/textual com sementes e a intenção:

```text
FALA→FONEMA→TOKEN→AST→BYTECODE→ASM→VM→OUTPUT
```

Mas o arquivo não é referenciado por `CMakeLists.txt`, `app/build.gradle` ou `settings.gradle`. Portanto ele não é, ainda, o compilador canônico do APK.

A promoção exige separar artefatos reais:

```text
frontend léxico/fonético
→ AST tipada
→ IR/bytecode versionado
→ backend ASM por ABI
→ empacotamento Android
→ DEX/R8/D8 ou caminho alternativo explicitado
→ APK assinado
→ instalação e execução
```

### ELF e DEX

O repositório já possui verificação histórica de símbolos/seções ELF e uma verificação simples de presença de `classes.dex` no APK. Isso não fecha divergências internas do artefato.

O novo auditor lê o APK e valida:

- magic e versão de cada `classes*.dex`;
- `file_size`, `header_size` e `endian_tag` do DEX;
- magic, classe, endianness e `e_machine` de cada `.so`;
- coerência entre `lib/<abi>/` e o header ELF;
- ARM32 como ELF32/`EM_ARM`;
- ARM64 como ELF64/`EM_AARCH64`;
- hash SHA-256 do APK analisado.

Sem `--apk`, o gate retorna `TOKEN_VAZIO` em vez de PASS.

## Execução

Auditoria estrutural e inventário:

```bash
python3 tools/audit_vectra_capabilities.py
```

Auditoria com artefato real:

```bash
python3 tools/audit_vectra_capabilities.py \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --output reports/vectra_capability_surface.json
```

Testes locais:

```bash
python3 -m unittest -v tests/test_audit_vectra_capabilities.py
```

## Critério para a segunda parte

A próxima parte poderá começar a integrar ou refatorar código somente depois que este mapa responder, para cada arquivo:

```text
origem
licença/autoria
papel
build owner
runtime owner
consumidores
estado de prova
rota de promoção
```

Estado desta entrega:

```text
map_created=true
auditor_created=true
synthetic_tests=PASS_5_OF_5_LOCAL
real_repository_scan=NOT_EXECUTED_BY_GITHUB_CONNECTOR
real_apk=TOKEN_VAZIO
claim_allowed=false
```
