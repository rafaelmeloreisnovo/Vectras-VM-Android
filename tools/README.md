<!-- DOC_ORG_SCAN: 2026-04-07 | source-scan: pending-manual-by-domain -->

> **Atualização do mapa — 25/10/2026 (DRAFT/HOLD):** [Reconciliação de ferramentas e diretórios](../docs/RAFAELIA_DOCS_RECONCILIATION_20261025.md). A árvore `tools/` no corte `master@6e4d99c8d90a3a1ddcb68933c693815d7c6e847b` contém 468 arquivos e 47 diretórios internos, não apenas as três famílias listadas na classificação histórica abaixo. Famílias adicionais confirmadas: `ci/`, `bootstrap/`, `qemu/`, `device_runtime/`, `arena/`, `zipraf/`, `state_geometry_lab/`, `skills/`, `math/` e `security/`. Cada item ainda exige evidência funcional própria.

# tools/

## Camada 1 — Propósito do diretório
Automação de verificação e utilitários operacionais.

## Camada 2 — Estrutura (até 3 níveis)
- Nível 1: `tools/`
- Nível 2: `apk/`, `baremetal/`, `termux-arm64-orchestrator/`
- Destaque baremetal/RAFCODE❤️PHI: `tools/baremetal/rafcode_phi/README.md` (casca C + emissão ASM/hex).
- Nível 3: detalhamento por arquivo em [`FILES_MAP.md`](FILES_MAP.md).

## Camada 3 — Arquivos e vínculos
- Catálogo completo: [`FILES_MAP.md`](FILES_MAP.md)
- Fluxo de APK assinado local: [`tools/apk/README.md`](apk/README.md)
- Contexto global de camadas: [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md)


## Execução Gradle canônica
Use sempre o wrapper de ferramentas para comandos Gradle neste repositório:

```bash
./tools/gradle_with_jdk21.sh <tarefas-ou-opções-gradle>
```

## Fluxo oficial do shell-loader (bootstrap/loader.apk)
Quando o recurso Termux está habilitado, o bootstrap exige `loader.apk`.

Comandos canônicos:

```bash
# 1) Gerar loader estável no módulo shell-loader (padrão: release)
./tools/gradle_with_jdk21.sh :shell-loader:buildStableLoader

# 2) Copiar para assets intermediários do app (sem versionar binário)
./tools/gradle_with_jdk21.sh :app:syncShellLoaderBootstrap

# 3) Validar bootstraps (inclui checagem do loader quando Termux está habilitado)
python3 tools/verify_bootstrap_assets.py
```

## Cadeia de comando (lógica de inspeção)
```bash
find tools -maxdepth 3 -type d | sort
sed -n '1,120p' tools/FILES_MAP.md
```
