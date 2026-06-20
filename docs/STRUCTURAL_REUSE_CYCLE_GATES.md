# Structural Reuse Cycle Gates

## Objetivo

Este documento fixa as condições estruturais para reduzir retrabalho entre repositórios RAFAELIA/Vectras.

A regra é simples:

```text
uma origem de verdade + um contrato + um gate + um relatório
```

## Problema que este gate resolve

Quando a mesma lógica aparece em pontos diferentes, surgem riscos:

1. documentação divergente;
2. scripts fazendo a mesma coisa com nomes diferentes;
3. artifacts sem SHA ou sem origem;
4. correção aplicada em um repo, mas não consumida pelo outro;
5. build local funcionando sem virar evidência reproduzível;
6. retrabalho por falta de matriz de responsabilidade.

## Invariante operacional

```text
PLAN -> SOURCE -> CONTRACT -> ARTIFACT -> VERIFY -> IMPORT -> PREFLIGHT -> RUNTIME_LEDGER
```

| Fase | Pergunta de bloqueio | Evidência esperada |
|---|---|---|
| PLAN | O que está sendo resolvido? | PR/issue/doc de decisão |
| SOURCE | Qual repo é fonte da verdade? | URL + branch + SHA pinado |
| CONTRACT | Qual formato liga os sistemas? | manifesto, JSON schema, doc |
| ARTIFACT | O que é consumível? | tar/APK/binário + SHA256 |
| VERIFY | Como provar que não corrompeu? | script + logs + `sha256sum -c` |
| IMPORT | Onde entra no repo consumidor? | staging controlado |
| PREFLIGHT | O runtime pode iniciar? | checks bloqueantes |
| RUNTIME_LEDGER | O que realmente executou? | relatório de sessão |

## Aplicação atual: Vectras <-> qemu_rafaelia

| Item | Fonte | Consumidor | Gate |
|---|---|---|---|
| QEMU/RAFAELIA IPC | `rafaelmeloreisnovo/qemu_rafaelia` | `Vectras-VM-Android` | artifact + SHA256 |
| Fonte externa | `tools/ci/external_sources.manifest` | CI Vectras | `verify_external_sources.sh` |
| Artifact QEMU | `qemu_rafaelia` release/CI | Vectras staging | `verify_qemu_rafaelia_artifact.sh` |
| Import local | artifact verificado | `.third_party_forks/qemu_rafaelia_artifact` | `import_qemu_rafaelia_artifact.sh` |
| Runtime | arquivos instalados no app | `QemuBinaryResolver` | preflight bloqueante |

## Regras anti-retrabalho

1. **Não repetir implementação quando basta contrato.**
   - QEMU compila fora.
   - Vectras consome artifact.

2. **Não repetir documento quando basta atualizar fonte de verdade.**
   - Se a decisão muda, atualizar o manifesto e o documento de resolução.

3. **Não aceitar caminho flutuante.**
   - Todo repo externo precisa de SHA pinado.

4. **Não aceitar artifact sem prova.**
   - `SHA256SUMS.txt`, `qemu-exec.json` e `BUILD_INFO.json` são obrigatórios.

5. **Não declarar execução sem runtime ledger.**
   - Build verde não prova boot.
   - Boot sem relatório não prova cadeia de custódia.

## Correção de fluxo

Quando uma correção nasce em outro ponto da estrutura:

```text
repo produtor corrige -> artifact muda -> SHA muda -> manifesto muda -> verificador passa -> consumidor importa -> preflight passa -> ledger registra
```

Se qualquer etapa falha, a conclusão correta é `TOKEN_VAZIO` para validação operacional daquele trecho, não afirmação inventada.

## Fórmula compacta

```text
menos retrabalho = mais contrato; mais contrato = menos ambiguidade; menos ambiguidade = execução auditável
```
