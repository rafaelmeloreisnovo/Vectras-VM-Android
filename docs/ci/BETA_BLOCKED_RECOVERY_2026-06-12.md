# BETA_BLOCKED recovery — CI, proof nodes e compliance

Data: 2026-06-12  
Base auditada: `513097a` após merge do PR #1007  
Issue-matriz: #1008

## Estado

O projeto permanece em `BETA_BLOCKED` até existir evidência de GitHub Actions real e verde no `master`.

Este documento separa o que foi corrigido por código do que depende de configuração externa do GitHub. Essa fronteira evita falso claim de CI verde.

## Corrigido neste PR

- `host-ci.yml` passa a aceitar `master` também em gatilhos diretos.
- `host-ci.yml` executa `tools/check_sensitive_artifacts.sh` antes da build nativa.
- `host-ci.yml` executa `make verify-vectra-os-contract` como proof node explícito.
- `host-ci.yml` executa `make run-vectra-os-mvp-bench` e coleta `.txt`/`.json` em `bench-results`.
- `validate-formula.yml` troca `actions/setup-python@v4` por `@v5`.
- `LICENSES_REGISTER.md` deixa de ser template vazio e passa a registrar matriz de bloqueio/compliance.
- `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` passa a listar binários e zonas bloqueadas com `TOKEN_VAZIO` explícito.

## Ainda externo à árvore

A assinatura descrita no laudo indica que de 22/05 em diante há `startup_failure` ligado a workflow morto/deletado. Se a UI continuar gerando run fantasma antes de baixar o YAML, o código deste PR não consegue corrigir sozinho.

Verificação manual obrigatória:

1. `Settings → Billing → Actions usage`
   - confirmar se há cota/limite/billing bloqueando Actions;
   - procurar banner de cobrança/limite no run falho.
2. `Settings → Actions → General`
   - confirmar Actions habilitado para o repo;
   - confirmar permissão de workflow adequada.
3. `Settings → Rules / Rulesets`
   - procurar regra exigindo workflow/check antigo chamado `BuildFailed`;
   - remover ou atualizar regra que aponta para workflow deletado.
4. Aba `Actions`
   - abrir run `startup_failure` mais recente;
   - verificar se o erro acontece antes do checkout/YAML ou dentro do job.

## Critério mínimo para sair de `BETA_BLOCKED`

- [ ] Um run real inicia em `master` ou PR.
- [ ] `host-ci` executa checkout e chega ao step `Block sensitive artifacts and credential patterns`.
- [ ] `make verify-vectra-os-contract` executa no runner.
- [ ] `make run-vectra-os-mvp-bench` gera `bench/results/vectra_os_mvp_bench.txt`.
- [ ] O artifact `host-ci-results` contém `bench-results/vectra_os_mvp_bench.txt`.
- [ ] `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` não contém `TOKEN_VAZIO` para artefatos de release.
- [ ] `LICENSES_REGISTER.md` e `THIRD_PARTY_NOTICES.md` convergem.

## Fronteira epistemológica

Até esses itens passarem, frases públicas devem dizer:

> Vectras-VM-Android tem núcleo nativo com proof nodes locais e gates de CI declarados, mas permanece `BETA_BLOCKED` enquanto a execução real do GitHub Actions e a proveniência de binários não forem fechadas.

Não dizer:

> CI verde, release validável, compliance fechado, distribuição pronta.

## Próximo PR técnico recomendado

Depois que este PR rodar pelo menos uma vez:

1. transformar `resources/compliance/ASSET_PROVENANCE_REGISTER.csv` em gate estrito de release;
2. adicionar verificação de SHA256 por binário;
3. isolar `RAFAELMELOREIS/MIT/NAOCOMERCIAL/` de qualquer pacote GPLv2;
4. abrir PR separado para hardening de `ShellExecutor` e sincronização de `MainService`.

## Retroalimentação RAFAELIA

- `F_ok`: proof nodes e registros deixam de ser promessa solta e entram no caminho executável.
- `F_gap`: Billing/Ruleset/Actions UI ainda é externo ao código.
- `F_next`: primeiro run real decide se o bloqueio era só configuração externa ou se há falha interna residual.
