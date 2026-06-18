# VECTRA_ANTI_OBVIOUS_REVIEW_CONTRACT

## Estado

`FATO_DOCUMENTADO`: contrato de revisão para impedir que humano ou IA trate como trivial aquilo que no Vectras pode ser código, rota, marker, fallback, sombra, semente, histórico ou evidência.

Este documento existe porque o código executando está à frente da documentação. Antes de varrer, corrigir, limpar ou promover qualquer item, a revisão deve passar por este contrato.

---

## Frase canônica

```text
O óbvio é a crença limitante.
Na Vectra, primeiro classifique a função do artefato; só depois chame de bug, lixo, duplicata ou documentação atrasada.
```

---

## Regra principal

Não chamar de erro antes de classificar.

```text
arquivo estranho      → classificar
warning               → classificar
comentário            → classificar
void                  → classificar
trailing whitespace   → classificar
CRLF                  → classificar
stub                  → classificar
fallback              → classificar
duplicata             → classificar
_incoming             → classificar
archive               → classificar
bug/core              → classificar
```

---

## Classes de leitura

| Classe | Significado | Ação |
|---|---|---|
| `CANONICO` | fonte vigente de build, execução, release ou docs | pode orientar decisão |
| `INCUBADORA` | protótipo vivo ainda não promovido | não apagar; mapear promoção |
| `INGESTAO` | material recebido, pacote, zip, doc, script ou experimento | triagem antes de uso |
| `HISTORICO` | registro de caminho anterior | preservar; não tratar como estado atual |
| `WARNING_INTENCIONAL` | sinal usado pelo compilador/linker/tooling | manter ou documentar |
| `MARKER_ESTRUTURAL` | comentário/linha usado por CI, grep, scan ou auditoria | não remover sem prova |
| `SOMBRA` | path/fallback latente por flag, ABI, layer ou build matrix | testar antes de cortar |
| `SEMENTE` | ideia parcial sem integração | registrar lacuna, não inventar conclusão |
| `BUG_REAL` | quebra semântica, ABI, build ou runtime | corrigir com evidência |
| `TOKEN_VAZIO` | não classificado ainda | não afirmar |

---

## Diretórios e leitura anti-óbvia

| Diretório | Leitura rasa | Leitura correta |
|---|---|---|
| `app/` | app Android | casca operacional: VM, QEMU, VNC, X11, Termux, setup, telemetria |
| `engine/rmr/` | código C nativo | núcleo canônico low-level |
| `Rafaelia/` | pasta experimental | incubadora C/ASM/JNI/baremetal |
| `tools/baremetal/rafcode_phi/` | ferramenta pequena | micro-base C→ASM→hex e VecBit |
| `_incoming/` | lixeira | área de ingestão e pendência de promoção |
| `Incluir/` | anexos soltos | banco de sementes, pacotes, papers, zips e scripts |
| `addthis/` | acúmulo | evidência/ingestão/histórico a classificar |
| `archive/` | velho | memória técnica preservada |
| `bug/` | problema | sandbox/registro/diagnóstico; não promover em bloco |
| `reports/` | relatório | inventário, evidência e status operacional |
| `docs/active/` | documentação | contrato vivo vigente |

---

## Itens que não devem ser normalizados por estética

Antes de mudar, perguntar o que o item faz.

```text
trailing whitespace
CRLF
missing-final-newline
comentários DOC_ORG_SCAN / HOTFIX / source-scan
warnings unused
defines aparentemente redundantes
stubs void
fallbacks não chamados
shadow paths
arquivos repetidos em incubadora
zips de anterioridade
ASM curto demais
scripts de build locais
```

Esses itens podem ser problema real, mas não devem ser tratados como problema por aparência.

---

## Aplicação ao low-level

No low-level Vectras/RAFAELIA:

```text
void pode ser fronteira freestanding;
warning pode ser instrução de corte;
comentário pode ser marker de tool;
miss pode ser próxima instrução;
zero pode preservar esparsidade;
duplicata pode ser anterioridade;
_incoming pode ser semente;
archive pode ser prova histórica;
```

---

## Antes de mexer em código

1. Localizar arquivo no mapa de diretórios.
2. Verificar se é canônico, incubadora, ingestão ou histórico.
3. Procurar documento ativo relacionado.
4. Procurar manifesto ou build script que consome o arquivo.
5. Verificar se warning/comentário/void/stub tem função de pipeline.
6. Se for hot path, comparar assembly/binário quando possível.
7. Se for app Android, verificar fluxo Java/Kotlin → JNI → engine.
8. Se for pacote/zip/doc, tratar como ingestão até triagem.
9. Registrar `FATO`, `LACUNA`, `TOKEN_VAZIO`, `F_NEXT`.
10. Só então corrigir/promover/apagar.

---

## Proibição operacional

```text
Não promover experimental por entusiasmo.
Não apagar histórico por limpeza.
Não chamar pending de lixo.
Não chamar warning de bug sem teste de intenção.
Não chamar documentação atrasada de inexistência do conceito.
Não tratar código que parece redundante como redundância sem medir função.
```

---

## Relação com contratos existentes

- `docs/active/LOWLEVEL_WARNING_INTENT_CONTRACT.md`
- `docs/active/LOWLEVEL_BRANCHLESS_SANS_HEAP_GUIDE.md`
- `docs/active/VECTRA_COMPILER_PRECOMPILER_NONACADEMIC_2026-06-05.md`
- `docs/active/VECTRA_TCG_DELTA_XOR_AUDIT_2026-06-11.md`
- `README.md`
- `PROJECT_STATE.md`
- `reports/full_repo_audit.tsv`

---

## Ledger

| Estado | Objeto | Próxima ação |
|---|---|---|
| `FATO` | Código executando à frente da documentação | canonizar contratos antes de nova varredura |
| `FATO` | Vectra contém canônico, incubadora, ingestão e histórico | preservar taxonomia |
| `LACUNA` | Nem todo C/ASM/Java/Kotlin foi lido linha a linha | mapear por camada |
| `TOKEN_VAZIO` | Uso exato de cada trailing whitespace/CRLF | não normalizar em bloco |
| `F_NEXT` | Criar matriz de documentação atrasada | vincular docs ao código vivo |
