# Mapa canônico de ingresso — APK, DEX, ELF, ASM e arquivos soltos

**Estado:** primeira parte executável  
**Repositório integrador:** `Vectras-VM-Android`  
**Plano de controle federado:** `RafGitTools`  
**Regra:** arquivo existente ≠ componente integrado ≠ execução comprovada.

## 1. Problema fechado por esta etapa

O repositório contém materiais em `Incluir/`, `_incoming/`, `__DELTA__/` e na
raiz que ainda não podem ser promovidos apenas pelo nome ou extensão. Antes de
mover qualquer arquivo, é necessário construir uma cadeia reproduzível:

```text
arquivo solto
  → SHA-256 e caminho de origem
  → classificação por magic bytes
  → inspeção estrutural sem extração
  → divergências tipadas
  → decisão humana de destino
  → teste do domínio
  → promoção ou quarentena
```

`tools/repo_ingress/generate_ingress_manifest.py` implementa a parte objetiva
dessa cadeia usando somente a biblioteca padrão do Python.

## 2. O que o scanner reconhece

| Corpo | Evidência usada | Verificações |
|---|---|---|
| APK | ZIP central directory + `AndroidManifest.xml` + `classes*.dex` | DEX internos, bibliotecas `lib/<abi>/*.so`, nomes duplicados, paths inseguros |
| DEX | magic `dex\nNNN\0` | versão, `file_size`, `header_size=0x70`, endian tag |
| ELF | magic `0x7fELF` | ELF32/ELF64, endian, `e_machine` |
| ZIP/JAR/AAB | magic e central directory | tipo de contêiner, criptografia, traversal, duplicatas |
| Arquivo genérico | bytes iniciais | texto UTF-8 ou binário |
| Duplicata | SHA-256 completo | grupos de conteúdo idêntico em caminhos diferentes |

O scanner **não extrai ZIP/APK**, não executa binários e não reescreve arquivos.

## 3. Contrato APK ↔ DEX ↔ ELF

```text
Gradle/AGP
  ├─ compila Java/Kotlin → DEX
  ├─ compila C/C++/ASM via NDK/CMake → ELF .so
  └─ empacota Manifest + DEX + resources + ELF → APK
```

### Gates mínimos

1. Cada `classes*.dex` precisa ter cabeçalho coerente com seu tamanho
   descompactado.
2. Cada `lib/<abi>/*.so` precisa declarar uma máquina ELF compatível:

| Caminho APK | Máquina ELF esperada |
|---|---|
| `lib/armeabi-v7a/` | ARM |
| `lib/arm64-v8a/` | AArch64 |
| `lib/x86/` | x86 |
| `lib/x86_64/` | x86-64 |
| `lib/riscv64/` | RISC-V |

3. Divergência de ABI, DEX malformado, path traversal, nome duplicado ou entrada
   criptografada bloqueiam promoção automática.
4. Uma biblioteca ELF válida ainda precisa passar pelo contrato JNI/símbolos do
   módulo de destino; este scanner não substitui `readelf`, linker ou teste em
   dispositivo.

## 4. Fronteira dos repositórios

| Repositório | Autoridade nesta cadeia |
|---|---|
| `Vectras-VM-Android` | integração Android, Gradle, APK, DEX, JNI e consumo das `.so` |
| `qemu_rafaelia` | runtime QEMU e produção/consumo dos binários nativos próprios |
| `termux-app-rafacodephi` | terminal, serviço Android e bootstrap Termux |
| `androidx_RmR` | bibliotecas AndroidX autorais/fork e contratos de lifecycle |
| `llamaRafaelia` | inferência/modelo e kernels próprios |
| `RafGitTools` | mapa federado, proveniência, governança e ligação entre PRs |

Código não deve ser copiado entre repositórios para “parecer integrado”. A
integração deve usar contrato, dependência versionada, submódulo, artefato
assinado ou documentação de fronteira.

## 5. Navegador ASM e TLS 1.2/1.3

O pedido descreve um navegador de baixo nível/ASM com suporte TLS. Nesta etapa,
a localização do source canônico e da suíte de prova permanece:

```text
browser_source_path      = TOKEN_VAZIO
asm_transport_path       = TOKEN_VAZIO
tls_1_2_test_evidence    = TOKEN_VAZIO
tls_1_3_test_evidence    = TOKEN_VAZIO
certificate_claim        = PROHIBITED_WITHOUT_EVIDENCE
```

Suporte de protocolo, validação de certificados e “certificação” são três
coisas diferentes:

- **suporte TLS**: handshake/protocolo implementado pela stack usada;
- **validação**: hostname, cadeia de confiança, tempo, revogação e política;
- **certificação**: evidência externa ou suíte formal identificada.

Assim que o source path for encontrado, ele entra no mapa como produtor ou
consumidor; até lá, nenhum claim é promovido.

## 6. Execução local

```bash
python3 -m unittest discover -s tools/repo_ingress -p 'test_*.py'

python3 tools/repo_ingress/generate_ingress_manifest.py \
  --repo-root . \
  --roots Incluir _incoming __DELTA__ \
  --include-root-files \
  --json-out reports/ingress/ingress_manifest.json \
  --markdown-out reports/ingress/ingress_manifest.md \
  --fail-on critical
```

Códigos de saída:

- `0`: varredura concluída sem bloqueadores do nível escolhido;
- `2`: erro de leitura/varredura;
- `3`: achado crítico;
- `4`: qualquer achado com `--fail-on any`.

## 7. Critério de conclusão da primeira parte

- [x] scanner read-only e stdlib-only;
- [x] classificação APK/DEX/ELF por conteúdo;
- [x] cruzamento de ABI Android com `e_machine`;
- [x] detecção de duplicatas e paths inseguros;
- [x] relatório JSON + mapa Markdown;
- [x] testes unitários sintéticos;
- [ ] executar sobre todo o repositório em checkout local;
- [ ] revisar achados e promover apenas o primeiro lote sem divergências;
- [ ] localizar o source canônico do navegador ASM/TLS;
- [ ] executar build Gradle/NDK e testes em dispositivo.

## 8. Invariante

\[
\boxed{\text{Promoção} =
\text{proveniência}
\land \text{estrutura válida}
\land \text{contrato}
\land \text{teste}
\land \text{rollback}}
\]

Um arquivo solto deixa de ser ruído somente quando sua origem, função, destino e
prova são explícitos.
