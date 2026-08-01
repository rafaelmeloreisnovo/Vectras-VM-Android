# Vectras — Ingestão Runtime Multifilamento RAFAELIA V1

**Estado:** `CANONICAL_DRAFT`  
**Política:** `claim_allowed=false`  
**Escopo:** Vectras-VM-Android como consumidor de artefatos produzidos por RafPolimata  
**Autoridade:** `RAFAELIA — Implementação Latentes e Papers — Drive GitHub V1`

## 1. Função

Este contrato define como o Vectras recebe, valida, executa e registra artefatos do ecossistema RAFAELIA sem confundir presença de arquivo, build, boot, estabilidade e evidência.

```text
handoff manifest
  -> quarantine
  -> schema/hash/ABI validation
  -> dependency resolution
  -> bounded launch
  -> watchdog
  -> telemetry/logs
  -> decision
  -> runtime receipt
```

## 2. Topologia de ingestão

| estágio | entrada | gate | saída | falha segura |
|---|---|---|---|---|
| `Q0_QUARANTINE` | artefato + manifest | identidade mínima | candidato isolado | rejeitar |
| `Q1_INTEGRITY` | hashes | digest recalculado | integridade confirmada | `HASH_MISMATCH` |
| `Q2_COMPATIBILITY` | ABI/formato/dependências | compatibilidade declarada | plano de execução | `INCOMPATIBLE` |
| `Q3_LAUNCH` | comando/configuração | processo iniciado | PID/sessão | `LAUNCH_FAIL` |
| `Q4_WATCHDOG` | sessão ativa | heartbeat/limites | estado monitorado | terminate + rollback |
| `Q5_EVIDENCE` | logs/métricas | receipt completo | evidência runtime | `TOKEN_VAZIO_RUNTIME` |
| `Q6_PROMOTION` | evidência + falsificador | decisão limitada | estado promovível | `claim_allowed=false` |

## 3. Manifest de entrada

O Vectras não deve inferir campos ausentes. Campos mínimos:

- `artifact_id`;
- `producer`;
- `source_commit`;
- `target` e ABI;
- `format`;
- hashes;
- dependências;
- validadores executados;
- comando de lançamento limitado;
- limites de CPU, memória, tempo e armazenamento;
- rollback;
- estado epistemológico.

Ausência de campo obrigatório produz `TOKEN_VAZIO_HANDOFF`.

## 4. Watchdog

Monitorar:

- processo não iniciado;
- crash loop;
- ausência de heartbeat;
- timeout;
- consumo acima do limite;
- log interrompido;
- alteração do artefato após validação;
- divergência entre ABI declarada e observada;
- escrita fora do diretório autorizado;
- dependência carregada sem proveniência.

A ação padrão é fail-closed: interromper a sessão, preservar logs, restaurar o estado anterior e emitir receipt de falha.

## 5. Rollback e failover

```text
current candidate fails
  -> terminate bounded session
  -> preserve failure receipt
  -> restore previous verified runtime state
  -> verify restored hashes
  -> only then reopen service
```

Failover permitido:

```text
current verified artifact
  -> previous verified artifact
  -> rebuild request to RafPolimata
  -> TOKEN_VAZIO
```

Nenhum artefato “parecido” substitui o candidato por inferência.

## 6. Failsafe e circuit breaker

Estado seguro:

```text
runtime_promoted=false
publication_ready=false
claim_allowed=false
state=TOKEN_VAZIO_OR_FAIL
```

Abrir circuit breaker quando houver `HASH_MISMATCH`, `ABI_CONFLICT`, `UNKNOWN_DEPENDENCY`, `WATCHDOG_TIMEOUT`, `ROLLBACK_FAILURE`, `REPEATED_CRASH` ou `AUTHORITY_CONFLICT`.

## 7. Evidência mínima

Um receipt runtime deve conter:

- commit do Vectras;
- ID e hashes do artefato;
- origem RafPolimata;
- dispositivo/VM e versão Android;
- arquitetura e ABI;
- comando e configuração;
- horários de início/fim;
- exit status;
- stdout/stderr/logcat ou equivalente;
- métricas limitadas;
- eventos do watchdog;
- resultado do rollback, quando acionado;
- classificação epistemológica.

## 8. Métricas

Somente promover desempenho após séries reproduzíveis com:

- `p50`, `p95`, `p99` de inicialização;
- taxa de sucesso e falha;
- tempo médio de recuperação;
- frequência de watchdog;
- memória máxima observada;
- estabilidade por duração definida;
- tamanho da amostra e ambiente.

Sem amostra e receipt, desempenho permanece `TOKEN_VAZIO_PERFORMANCE`.

## 9. Classificação atual

- `PROVADO`: este contrato existe após commit observável.
- `EVIDENCIADO`: o repositório possui app, engine, rotas de build/release e documentação de runtime.
- `HIPÓTESE`: a quarentena e os gates reduzem falhas de integração.
- `MODELO_ANALÓGICO`: filamentos são trilhas de proveniência e controle.
- `REFUTADO`: documentação e artefato presente não provam boot estável.
- `TOKEN_VAZIO`: ingestão real, watchdog ativo, failover ensaiado, rollback comprovado e métricas atuais.

## 10. F_next verificável

1. Materializar schema do manifest de ingestão.
2. Criar validador read-only.
3. Testar casos negativos de hash, ABI e dependência.
4. Executar candidato em sessão limitada.
5. Preservar receipt e somente então avaliar promoção runtime.
