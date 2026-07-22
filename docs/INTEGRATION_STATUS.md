# Integração do diretório `Incluir` no repositório principal

## Escopo aplicado
- O diretório `Incluir/` já está presente e sincronizado com a origem `https://github.com/wojcikiewicz17/Vectras-VM-Android`.
- Foi executada validação de paridade por diff entre `Incluir` local e remoto (sem divergências).
- O conteúdo de correção `FIX_03_sources_rmr_core-1.cmake` foi conferido com o manifesto ativo `engine/rmr/sources_rmr_core.cmake`.

## Estado estrutural atual
- `rmr_neon_simd.c` permanece no grupo ARM-only (`RMR_SOURCE_GROUP_ASM_ARM64_NEON`), evitando compilação indevida em ABIs não ARM.
- Pipeline Android mantém trilhas separadas para release assinado oficial e release unsigned interno (sem rebaixar segurança da trilha oficial).
- Matriz de ABIs arm32+arm64 permanece suportada via parâmetros `APP_ABI_POLICY` e `SUPPORTED_ABIS`.

## Validação executada nesta integração
1. Clone da origem remota para comparação de diretório `Incluir`.
2. Diff recursivo do diretório `Incluir` local vs remoto.
3. Tentativa de compilação `:app:assembleDebug` com política arm32+arm64.

## Bloqueio encontrado
- O ambiente local atual não possui Android SDK configurado (`local.properties`/`ANDROID_HOME`), impedindo finalizar a compilação APK nesta máquina.

## Próximo passo objetivo
- Definir `sdk.dir` em `local.properties` (ou `ANDROID_HOME`) e repetir:
  - `./gradlew :app:assembleDebug -PAPP_ABI_POLICY=arm32-arm64 -PSUPPORTED_ABIS=arm64-v8a,armeabi-v7a`
  - `./gradlew :app:assembleRelease -Psigning_mode=unsigned -PAPP_ABI_POLICY=arm32-arm64 -PSUPPORTED_ABIS=arm64-v8a,armeabi-v7a`
