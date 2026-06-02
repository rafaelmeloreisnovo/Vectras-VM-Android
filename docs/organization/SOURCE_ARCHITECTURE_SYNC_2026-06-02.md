# Sincronização código-fonte ↔ documentação (2026-06-02)

Este documento registra a arquitetura observada no código em 2026-06-02 e marca onde os documentos precisam acompanhar a árvore real. A varredura foi limitada a 5 níveis de diretórios para seguir a ordem de serviço e evitar conclusões baseadas apenas em documentos antigos.

## Fonte de verdade observada no build

| Camada | Fonte observada | Estado documental |
|---|---|---|
| Gradle multi-módulo | `settings.gradle` inclui `:app`, `:terminal-emulator`, `:terminal-view`, `:shell-loader:stub` e `:shell-loader`. | Documentos antigos que falam só em app/Termux precisam citar os módulos auxiliares. |
| Android/NDK | `build.gradle` centraliza política de ABI, JDK, SDK, NDK e CMake. | O alvo operacional atual usa política configurável e não deve ser descrito como ABI única sem contexto. |
| JNI/C/CMake | `app/src/main/cpp/CMakeLists.txt` compõe `vectra_core_accel`, ponte RMR, payload bootstrap Termux e módulos opcionais. | Documentos devem separar runtime Java/Kotlin, C/JNI e payload bootstrap. |
| Core nativo | `engine/rmr/include/` e `engine/rmr/src/` contêm BitOmega, BitRaf, torus flow, attractor, vector field, hw-detect, virtio, zipraf e bridges. | A documentação de matemática/arquitetura precisa apontar para headers/sources reais. |
| Policy kernel | `engine/vectra_policy_kernel/` contém crate Rust com FFI e testes. | Documentos que tratam apenas C/ASM estão incompletos para a camada de política. |
| App runtime | `app/src/main/java/com/vectras/vm/` contém VMManager, StartVM, setupwizard, supervisor, QEMU, benchmark, core e rafaelia. | A arquitetura atual já possui módulos de failover, preflight e governança que devem ser documentados como primeira classe. |
| Terminal fork | `terminal-emulator/`, `terminal-view/` e `shell-loader/` permanecem módulos versionados. | O drift Termux/com.termux continua risco conhecido e deve ser tratado como compatibilidade, não como fechamento silencioso. |

## Mapa de diretórios até 5 níveis

| Diretório | Responsabilidade atual | Ação documental |
|---|---|---|
| `app/src/main/java/com/vectras/vm` | UI principal, VM lifecycle, setup wizard, benchmark, runtime policies. | Manter `docs/ARCHITECTURE.md` sincronizado com módulos extraídos. |
| `app/src/main/cpp` | Biblioteca nativa Android/JNI e CMake de composição. | Atualizar docs quando mudar payload, ABI ou fonte C/ASM. |
| `engine/rmr/include` | Contratos C do core determinístico/RMR. | Tratar como fonte primária para fórmulas executáveis. |
| `engine/rmr/src` | Implementação C do core, sem depender de documentos conceituais. | Documentar divergência fórmula ↔ implementação antes de promover teoria. |
| `engine/rmr/interop` | ASM por arquitetura. | Antes de tocar `.S`, ler `VECTRA_OS.md` se existir e preservar contrato x0..x4. |
| `engine/vectra_policy_kernel/src` | Kernel Rust de política/ops/FFI. | Registrar fronteira Rust↔C↔Java. |
| `tools/ci` | Gatilhos e gates auxiliares de CI/ABI/ASM. | Usar como trilha de validação documental. |
| `docs/active` | Relatórios ativos e auditorias. | Rebaixar documentos superados para `docs/archive/` em lote futuro. |
| `reports` | Evidências e relatórios pontuais. | Não confundir relatório histórico com fonte canônica. |
| `Incluir` e `_incoming` | Pacotes, notas, protótipos e artefatos pendentes. | Promover somente com teste, hash e manifesto. |

## Estratégia branchless/sans-heap para documentação técnica

A organização documental não altera hot paths. Quando uma ordem futura tocar código de baixo nível, a decisão deve seguir esta sequência:

1. **Plan**: localizar fonte real e contrato ABI antes de escrever.
2. **Apply**: preferir patch pequeno, sem nova camada abstrata em assembly, sem heap em hot path e com branchless quando aplicável.
3. **Verify**: executar teste local relevante; se falhar por ambiente, registrar `SKIPPED`/limitação.
4. **Failover/Rollback**: manter manifesto de arquivos movidos e reversão por `git mv`/`git checkout`.
5. **Audit**: atualizar o documento primário do domínio, não apenas relatório histórico.

## Riscos de documentação desatualizada

- Documentos conceituais podem declarar invariantes ainda não implementados ou não validados em todos os ABIs.
- Notas em `Incluir/` e `_incoming/` contêm código/protótipos que podem estar à frente ou fora da árvore canônica.
- Overlays ZIP podem duplicar fontes e esconder divergência; precisam de hash/manifests antes de promoção.
- Arquivos com nomes soltos dificultam CI, busca e links Markdown.
- O bug conhecido do `attractor_table` e o paradoxo VOID #22 não devem ser marcados como resolvidos sem patch e teste.

## Próximo lote recomendado

1. Criar manifesto SHA-256 de `Incluir/`, `_incoming/` e ZIPs.
2. Separar notas conceituais de raiz em `docs/rafaelia_reference/` usando `git mv` e índice de origem.
3. Atualizar `docs/ARCHITECTURE.md` com mapa dos módulos Java/Kotlin, C/JNI, Rust e terminal.
4. Rodar build/testes canônicos e anexar resultados em `reports/`.
5. Rebaixar documentos superados para `docs/archive/` com links de substituição.
