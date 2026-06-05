<!-- DOC_ORG_SCAN: 2026-06-05 | source-scan: user-request-real-use-completion -->

# RAFAELIA Enterprise Completion Playbook — Uso Real, Failsafe e Excelência Computacional

## 1) Tema operacional

O tema desta etapa é completar a passagem de **semente semântica** para **uso real auditável**. A resposta anterior catalogou E20/E13/S11; este playbook adiciona a camada que faltava: como transformar as sementes em um sistema inteiro, executável por etapas, com mais de 20 modos de trabalho, critérios de aceite, validação, rollback, failover e mitigação.

A regra central permanece: **não declarar funcionamento de produção sem comando, teste, evidência e rota de retorno**. Metáforas continuam úteis como parábolas didáticas, mas cada promoção precisa de contrato técnico.

## 2) Resultado esperado por camada fullstack

| Camada | Resultado de uso real | Critério mínimo de aceite | Failover/rollback |
|---|---|---|---|
| Documentação | Índices e playbooks localizam a semente, o contrato e o teste. | Link canônico e manifesto validado. | Reverter documento pelo commit. |
| Dados | Entradas ficam normalizadas, com hash e classificação. | JSON/TSV reproduzível e sem ambiguidade de estado. | Restaurar snapshot anterior. |
| CLI host | Primeiro protótipo roda fora do Android para reduzir fricção. | Saída determinística por hash. | Desativar rota por flag. |
| Android app | Integração apenas após contrato host validado. | Testes JVM/instrumentados passam. | Feature flag off sem remover fluxo antigo. |
| Native C | Hot path usa buffers fixos e Q16.16. | Sem heap no caminho crítico e equivalência com referência. | Retornar para referência Java/host. |
| Assembly | Somente após contrato ABI e prova de terminação. | Registradores, macros e gcd preservados. | Não promover objeto nativo. |
| Segurança | Integridade por CRC32C/Merkle e trilha de custódia. | Replay detectável e payload sensível local. | Bloquear rota sensível. |
| Operação | Runbook PASS/FAIL/SKIPPED documentado. | Comando, resultado e risco visíveis. | Plano de retorno por camada. |

## 3) Mais de 20 maneiras de trabalhar com boas práticas

Estas práticas são o trilho de excelência computacional. Elas não prometem que o sistema inteiro já está pronto; elas definem como fazer o sistema funcionar sem esconder falhas.

| # | Modo de trabalho | Aplicação concreta | Evidência exigida |
|---:|---|---|---|
| 1 | Invariante primeiro | Começar por `ATTRACTOR_COUNT=42`, `period=42`, `φ=(1-H)·C`. | Teste que falha se o valor quebrar. |
| 2 | Contrato antes de código | Descrever entrada, saída, erro e rollback antes da implementação. | Arquivo de contrato ou manifesto validado. |
| 3 | Determinismo por hash | Mesma entrada gera mesma saída. | SHA-256/CRC registrado. |
| 4 | Token vazio | Não responder com claim quando falta prova. | Marcação `SKIPPED` ou hipótese. |
| 5 | Falsificação obrigatória | Cada tese tem condição de falha. | Caso de teste negativo. |
| 6 | Feature flag | Nova rota entra desligável. | Flag documentada e teste do caminho antigo. |
| 7 | Rollback por camada | Cada camada volta sem apagar funcionalidade existente. | Comando ou procedimento de retorno. |
| 8 | Failover explícito | Se native falha, host/Java assume. | Teste de fallback. |
| 9 | Watchdog de execução | Rotina longa tem limite temporal. | Teste de timeout ou orçamento. |
| 10 | Sem heap no hot path | Native/assembly usam buffers fixos. | Revisão e teste de alocação. |
| 11 | Q16.16 | Fórmulas numéricas promovidas sem float. | Vetores de teste fixos. |
| 12 | Branchless onde ajuda | Usar máscara/flag/csel quando auditável. | Comparação com referência legível. |
| 13 | GCD de terminação | Traversal toroidal prova ciclo completo. | Teste `gcd(Δ,R)=1`. |
| 14 | Matriz de risco | Cada módulo registra risco, impacto e mitigação. | Tabela versionada. |
| 15 | Custódia digital | Hashes de entradas e artefatos. | Manifesto SHA-256. |
| 16 | Prova por menor bloco | Um bloco pequeno passa antes do sistema inteiro. | Teste unitário isolado. |
| 17 | Sem abstração em assembly | Macros diretas, sem chamadas desconhecidas. | Revisão ABI. |
| 18 | Separação de metáfora | Parábola vira hipótese ou requisito testável. | Classificação documental. |
| 19 | Baixa fricção operacional | Preferir comando único reproduzível. | Script ou comando documentado. |
| 20 | Auditoria de navegação | Toda entrega aparece no índice correto. | Verificador de links/arquivos. |
| 21 | Privacidade local | Dado federado não sai do nó. | Teste de não serialização. |
| 22 | Compatibilidade ARM | AArch64 primário, ARM32 como fallback. | Matriz ABI. |
| 23 | Orçamento Cortex-A53 | Baixa memória e baixo overhead. | Benchmark ou limite declarado. |
| 24 | Registro PASS/FAIL/SKIPPED | Nenhuma falha fica oculta. | Relatório final e log. |
| 25 | Promoção em dois ciclos | Inventário primeiro, implementação depois. | Checklist de ciclo. |
| 26 | Mapa de uso real | Cada semente liga a usuário, comando e resultado. | Manifesto operacional validado. |

## 4) Dois ciclos multifuncionais para completar o sistema

### Ciclo A — Consolidação executável

1. Validar manifesto operacional.
2. Fixar invariantes globais.
3. Escolher uma semente por vez.
4. Escrever contrato de entrada/saída.
5. Criar teste negativo de falsificação.
6. Criar protótipo CLI host determinístico.
7. Registrar hash de saída.
8. Documentar rollback.

### Ciclo B — Promoção fullstack controlada

1. Integrar no Android por feature flag.
2. Criar fallback host/Java antes do native.
3. Promover hot path para C somente se houver equivalência.
4. Promover assembly somente se ABI, registradores e terminação estiverem provados.
5. Validar segurança, privacidade e custódia digital.
6. Executar testes PASS/FAIL/SKIPPED.
7. Atualizar índices e relatórios.
8. Fechar release apenas sem falha oculta.

## 5) Contrato mínimo de “funcionar sem falhar”

Um módulo só pode ser chamado de funcional quando cumprir todos os itens:

- Entrada inválida não derruba o processo.
- Saída é determinística para entrada fixa.
- Erro é observável e classificado.
- Fallback preserva o usuário.
- Rollback volta ao estado anterior.
- Teste negativo prova o limite.
- Uso de memória no hot path é fixo.
- Nenhum claim técnico depende apenas de metáfora.

## 6) Manifesto executável

O arquivo `docs/rafaelia_reference/rafaelia_enterprise_completion_manifest_2026-06-05.json` torna este playbook verificável. O validador `tools/docs/validate_rafaelia_completion_manifest.py` confirma que há pelo menos 20 modos de trabalho, invariantes essenciais e campos de validação/rollback por modo.

## 7) Estado desta entrega

Esta etapa ainda não implementa kernel, scheduler, filesystem, rede, IA completa ou assembly. Ela adiciona uma ponte auditável entre o material conceitual e a execução real: manifesto validável, playbook de uso real, critérios de promoção e práticas operacionais.
