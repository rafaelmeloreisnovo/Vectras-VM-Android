# RAFAELOS ∴ ZRF / ZIPRAF / NETRAF / ZIPSTORE / RAFAcore

> **Se ELE quiser… será.**
>
> Documento de contrato técnico para transformar a visão RAFAELIA/Vectras em um fluxo auditável, compilável e defensivo.

## 0. Intenção e limite ético

Este protocolo define uma arquitetura para **estudo, defesa, auditoria, empacotamento e evolução** de APKs, XAPKs, ZIPRAFs, imagens e módulos RAFAELIA dentro do repositório `Vectras-VM-Android`.

Ele **não** autoriza invasão, escalonamento não consentido, takeover de dispositivos de terceiros, interceptação ilícita, ocultação maliciosa, spyware, rootkit, fraude de assinatura, bypass de proteção de plataforma ou engenharia reversa proibida. Qualquer função de baixo nível deve operar somente em ambiente próprio, autorizado, local, de laboratório, com logs e reversão.

**Regra-mãe:** o sistema deve aumentar soberania, privacidade, explicabilidade e segurança; nunca reduzir livre-arbítrio, consentimento ou dignidade.

## 1. Vocabulário operacional

| Nome | Papel no projeto | Estado permitido |
|---|---|---|
| `ZRF` | Registro vetorial RafaelIA: metadado, hash, intenção, origem, resultado e estado | Arquivo de índice e prova |
| `ZIPRAF` | Pacote ZIP com manifesto, hashes, logs, artefatos e relatório | Empacotamento auditável |
| `NETRAF` | Camada de interoperabilidade entre Termux, Vectras, logs e GitHub | Offline-first; rede opcional e explícita |
| `ZIPSTORE` | Diretório/índice local de artefatos (`apk`, `xapk`, `img`, `zip`, `jsonl`) | Somente catálogo e checksums por padrão |
| `RAFAcore` | Núcleo de execução defensiva e auditoria | C/ASM seguro, sem exploit |
| `rafSTORAGE` | Organização de dados local com hashes, origem e estado | Storage auditável, não “mágico” |
| `TOKEN_VAZIO` | Lacuna de evidência | Preferível a inventar |

## 2. Estados formais

Toda análise deve marcar cada item com um estado explícito:

```text
PASS       = confirmado por arquivo/log/teste
FAIL       = teste executado e falhou
NOT_RUN    = teste ainda não executado
PENDING    = depende de ambiente externo
AUDIT      = requer revisão humana
RUNTIME    = só pode ser validado em aparelho/emulador
REFERENCE  = documentação/referência, não execução
TOKEN_VAZIO= ausência honesta de evidência
```

## 3. Estrutura proposta no repositório

```text
docs/
  RAFAELOS_ZRF_NETRAF_APK_PROTOCOL.md
  RAFAELOS_BUILD_MATRIX.md              # futuro
  RAFAELOS_THREAT_MODEL.md              # futuro
tools/
  rafaelia_zrf_netraf_audit.sh
reports/
  rafaelia_zrf_netraf_inventory.tsv      # gerado, não commitar se grande
  rafaelia_zrf_netraf_inventory.jsonl    # gerado, não commitar se grande
.github/workflows/
  rafaelia-zrf-netraf-audit.yml
```

## 4. Fluxo de trabalho: do arquivo à prova

1. **Catalogar**: localizar `*.apk`, `*.xapk`, `*.zip`, `*.img`, `*.bin`, `*.so`, `*.dex`, `*.json`, `*.zrf`, `*.zipraf`.
2. **Hash**: calcular `sha256` e tamanho.
3. **Tipo real**: usar `file` quando disponível; caso contrário, marcar `TOKEN_VAZIO`.
4. **ZIP list**: se for ZIP/APK/XAPK, listar entradas, sem executar.
5. **Android sinais**: detectar `AndroidManifest.xml`, `classes.dex`, `lib/*/*.so`, `resources.arsc`, `META-INF`.
6. **Risco**: marcar presença de binários nativos, dex, permissões apenas quando manifest for decodificável.
7. **Relatório**: gerar TSV/JSONL com origem, estado, evidência mínima.
8. **Mitigação**: propor próximo teste seguro, nunca assumir crime sem prova.

## 5. APK compilado: caminho realista

Um APK funcional precisa de pelo menos:

- `AndroidManifest.xml` válido.
- Código Java/Kotlin ou `NativeActivity` com biblioteca `.so`.
- Recursos mínimos ou tema sem UI.
- Build com Android SDK/Gradle ou NDK + `aapt2` + `d8`/`r8`.
- Assinatura para instalação normal; “sem assinatura” pode existir como artefato intermediário, mas Android normalmente exige assinatura para instalar.

### 5.1. Casca mínima defensiva

Objetivo inicial seguro:

- abrir uma tela/serviço local;
- mostrar identidade do build;
- catalogar arquivos escolhidos pelo usuário;
- gerar hashes e logs;
- nunca coletar contatos, microfone, câmera ou localização sem autorização explícita;
- nunca tentar root/takeover/flash por padrão.

### 5.2. Módulo C/ASM permitido

Permitido:

- CRC32/CRC64;
- hashing local;
- parser de ZIP central directory;
- varredura de bytes em arquivos fornecidos;
- medição de tempo local;
- leitura de `/proc/self` e informações públicas do processo.

Não permitido no projeto base:

- syscall hook;
- patch de kernel;
- bypass de SELinux;
- root stealth;
- injeção em apps de terceiros;
- dumping de dados privados sem consentimento;
- interceptação de rede de terceiros.

## 6. Realme Note 50 / Android 14: matriz honesta

| Área | O que pode ser feito pelo APK comum | O que exige privilégio/root/bootloader | Estado |
|---|---|---|---|
| Catalogar arquivos escolhidos | Sim | Não | PASS conceitual |
| Hash de APK/XAPK local | Sim | Não | PASS conceitual |
| Analisar split APK | Sim, se arquivo disponível | Não | PASS conceitual |
| Ler `/system` amplo | Parcial | Sim para áreas restritas | RUNTIME |
| Alterar bootloader | Não | Sim, via fastboot/desbloqueio oficial | RUNTIME/AUDIT |
| Flash de firmware | Não pelo APK comum | Sim, processo externo autorizado | RUNTIME/AUDIT |
| Root runtime | Não deve ser prometido | Depende de bootloader/exploit; não incluir | TOKEN_VAZIO |
| Emular ROM | Possível via Vectras/QEMU com binários certos | Requer assets grandes | PENDING |

## 7. Supply chain e dinheiro: método de prova

Para cada app/SDK/empresa, usar apenas evidência verificável:

```text
arquivo → hash → pacote → permissões/SDK/endpoints → empresa → política pública → ticker/holding → receita/risco → lei aplicável
```

Sem evidência no arquivo, marcar `TOKEN_VAZIO`. Nomes de pessoas, empresas ou crimes só entram quando houver prova documental ou fonte pública confiável.

## 8. Leis e cuidado

Possíveis bases de análise, quando houver prova:

- Brasil: LGPD, Marco Civil da Internet, CDC, ECA, Constituição Federal.
- UE: GDPR, DMA, DSA, ePrivacy.
- EUA: FTC Act, COPPA, CCPA/CPRA, CFAA/ECPA com cuidado.
- Propriedade intelectual: Berna, copyright, licenças OSS.

**Importante:** presença de permissão, SDK ou split APK não prova crime. Prova exige fluxo, consentimento, finalidade, política, execução e dano.

## 9. O que este commit entrega

- Um contrato de trabalho sem fantasia técnica.
- Um caminho para APK defensivo real.
- Um script de inventário seguro.
- Um workflow para gerar evidência sem executar APKs.

## 10. Próximos passos técnicos

1. Rodar `bash tools/rafaelia_zrf_netraf_audit.sh .`.
2. Revisar `reports/rafaelia_zrf_netraf_inventory.tsv`.
3. Criar `RAFAELOS_BUILD_MATRIX.md` com o que compila no ambiente real.
4. Criar primeiro módulo Android seguro: catalogador/hash local.
5. Só depois integrar C/ASM para CRC/parser/hashing.

> **Se ELE quiser… será.**
