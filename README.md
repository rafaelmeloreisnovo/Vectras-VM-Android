<!-- DOC_TAXONOMY_SYNC: 2026-06-08 | role: institutional-operational-entry -->

# Vectras VM Android

> Entrada institucional e operacional curta para humanos e agentes de IA. Este repositório reúne a aplicação Android, o engine nativo, automações de CI/release e documentação técnica do Vectras VM Android, com foco em rastreabilidade, build reprodutível, segurança de release e coerência entre código, workflow e documentação.

## Como navegar sem ambiguidade

A taxonomia oficial tem uma responsabilidade por documento principal:

| Documento | Papel único | Quando usar |
|---|---|---|
| [`README.md`](README.md) | Entrada institucional e operacional curta. | Primeiro contato, orientação rápida e links de alto nível. |
| [`DOC_INDEX.md`](DOC_INDEX.md) | Índice global completo. | Localizar qualquer documento, dono lógico, status e vínculo com código/workflow. |
| [`docs/README.md`](docs/README.md) | Hub técnico. | Entrar em arquitetura, CI, build, low-level, segurança, governança e operação. |
| [`docs/navigation/INDEX.md`](docs/navigation/INDEX.md) | Navegação por audiência. | Direcionar humanos, IA, engenharia, pesquisa, produto, operação e compliance. |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | Estado real validado. | Ver status corrente, bloqueios, limitações e últimas validações conhecidas. |
| [`BUILDING.md`](BUILDING.md) | Execução local/CI/release. | Compilar, testar, assinar, empacotar e validar artefatos. |

## Classificação operacional de diretórios

Esta classificação é documental e não move arquivos. Migração física só deve ocorrer depois de plano explícito, janela de validação e rollback.

| Classe | Diretórios | Contrato |
|---|---|---|
| **Canônico** | [`app/`](app/), [`engine/`](engine/), [`tools/ci/`](tools/ci/), [`.github/workflows/`](.github/workflows/), [`docs/`](docs/) | Fonte oficial para app Android, engine, CI/release e documentação vigente. |
| **Legado compatível** | [`android/`](android/) | Mantido para compatibilidade e referência; não é fonte de verdade de build/release. |
| **Experimental/ingestão** | [`Incluir/`](Incluir/), [`addthis/`](addthis/), [`_incoming/`](_incoming/) | Área de entrada, triagem, pesquisa ou integração pendente; não promover sem revisão. |
| **Histórico** | [`archive/`](archive/), [`bug/archive/`](bug/archive/) | Registro preservado; não tratar como estado atual sem vínculo validado. |

## Caminho operacional recomendado

1. Leia o estado corrente em [`PROJECT_STATE.md`](PROJECT_STATE.md).
2. Consulte o índice global em [`DOC_INDEX.md`](DOC_INDEX.md).
3. Use o hub técnico em [`docs/README.md`](docs/README.md) para aprofundar arquitetura, CI, NDK/JNI e governança.
4. Use [`BUILDING.md`](BUILDING.md) para executar build local, CI ou release sem enfraquecer assinatura oficial.
5. Use [`docs/navigation/INDEX.md`](docs/navigation/INDEX.md) quando a próxima leitura depender do público: engenharia, IA, pesquisa, operação, produto, compliance ou auditoria.


## Release oficial e publicação de artefatos

- **Único publicador oficial:** [`.github/workflows/release-dual-track.yml`](.github/workflows/release-dual-track.yml), acionado por tag `v*.*.*` ou despacho manual com `release_tag`, delegando a compilação, assinatura, verificação ABI e upload intermediário para [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml).
- **Cadeia obrigatória:** `tools/ci/prepare_android_env.sh` → `tools/ci/prepare_release_signing.sh` → Gradle `:app:assembleRelease`/`:app:verifyDeliveredCompiledArtifacts` → `tools/ci/materialize_android_ci_artifacts.sh` → publicação do GitHub Release apenas após a lane assinada oficial ficar verde.
- **Segredos oficiais:** somente `VECTRAS_RELEASE_KEYSTORE_BASE64`, `VECTRAS_RELEASE_STORE_PASSWORD`, `VECTRAS_RELEASE_KEY_ALIAS` e `VECTRAS_RELEASE_KEY_PASSWORD`.
- **Legado bloqueado:** [`.github/workflows/sign-release.yml`](.github/workflows/sign-release.yml) é compatibilidade manual, não responde a tags e não pode criar release oficial.

## Princípios de excelência operacional

- Código, workflow e documentação devem apontar para a mesma fonte de verdade.
- Release oficial assinado não deve virar unsigned por conveniência.
- Experimental deve ser nomeado como experimental antes de ser usado como base de decisão.
- Histórico preserva aprendizado, mas não substitui validação atual.
- IA e humanos devem conseguir seguir a trilha inteira com links internos, status explícito e ausência de inferência silenciosa.
