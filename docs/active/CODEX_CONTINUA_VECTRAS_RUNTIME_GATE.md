# CODEX CONTINUA — Vectras Runtime Gate

Status: guia operacional para continuação por Codex/agentes.
Data de referência: 2026-06-22.
Escopo: reduzir `BETA_BLOCKED` por evidência atual, sem inflar status de release.

## 1. Fonte de verdade antes de agir

Ler nesta ordem:

1. `PROJECT_STATE.md`
2. `reports/CANONICAL_BUILD_STATUS.md`
3. `BUILDING.md`
4. `DOC_INDEX.md`
5. `.github/workflows/android-ci.yml`
6. `.github/workflows/release-dual-track.yml`
7. `docs/RELEASE_EVIDENCE_LEDGER.md`

Se houver divergência, prevalece o documento canônico de estado/build mais específico.

## 2. Estado de partida

O estado declarado é `BETA_BLOCKED`. Portanto:

- não afirmar release oficial;
- não tratar validação antiga como validação do commit atual;
- não converter validação interna em distribuição oficial;
- não atualizar status canônico sem evidência executada no commit alvo;
- preservar histórico e registrar lacunas como `TOKEN_VAZIO`.

## 3. Sequência `continua`

Quando o usuário ou operador escrever `continua`, executar a menor próxima ação verificável:

```text
1. Verificar status canônico.
2. Rodar ou preparar o build de validação indicado por BUILDING.md.
3. Se falhar, corrigir o primeiro erro real, não sintomas secundários.
4. Registrar comando, ambiente, resultado e lacuna.
5. Reexecutar a validação mínima afetada.
6. Commitar uma unidade pequena.
7. Responder com F_ok, F_gap, F_next.
```

## 4. Gates

| Gate | PASS somente quando... | Caso contrário |
|---|---|---|
| Build atual | comando canônico passa no commit alvo | `BLOCKED` ou `PARTIAL` |
| Runtime | execução produz evidência de abertura/funcionamento sem erro crítico | `TOKEN_VAZIO` |
| Release oficial | lane oficial assinada e ledger preenchido | validação interna apenas |
| ABI | política ABI documentada e verificada | `PARTIAL` |
| Documentação | links e status refletem código atual | `PARTIAL` |

## 5. Commits recomendados

Usar commits pequenos:

```text
fix(build): resolve first current Vectras validation failure
docs(status): record current runtime gate evidence
test(runtime): add minimal Vectras execution smoke check
ci(android): align validation command with canonical status
```

## 6. O que não fazer

- Não declarar `STABLE` sem CI atual.
- Não chamar APK interno de distribuição oficial.
- Não remover histórico para esconder falha.
- Não misturar migração estrutural com hotfix de build no mesmo commit.
- Não atualizar `CANONICAL_BUILD_STATUS.md` sem data, SHA e comando de validação.

## 7. Retroalimentação

```text
F_ok: evidência nova obtida.
F_gap: próximo bloqueio real.
F_next: menor commit seguinte.
```

Este arquivo existe para fazer o Codex continuar com sequência, não com improviso.
