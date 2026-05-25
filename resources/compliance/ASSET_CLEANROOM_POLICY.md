# ASSET_CLEANROOM_POLICY

## Objetivo
Definir regras rígidas para criação, ingestão, revisão e distribuição de assets (imagens, ícones, áudio, fontes, vídeos e binários visuais/sonoros) com rastreabilidade autoral e conformidade legal.

## Regras obrigatórias
1. Nenhum asset entra em build/release sem registro no `ASSET_PROVENANCE_REGISTER.csv`.
2. Asset sem autor + licença SPDX + origem verificável = `status=quarantine`.
3. É proibido copiar parcial/totalmente imagens, nomes visuais, wireframes, ícones ou estilos protegidos sem licença.
4. Créditos de inspiração são permitidos apenas no nível conceitual, nunca com reaproveitamento de expressão protegida.
5. Toda modificação de asset deve atualizar `last_review_date` e `notes`.

## Classes de risco
- **A**: autoral interno comprovado.
- **B**: terceiro com licença compatível e prova arquivada.
- **C**: lacuna documental/licença ausente (bloqueio de release).
- **D**: suspeita de derivação/plágio (remoção imediata e reautoria).

## Fluxo operacional (2 ciclos)
### Ciclo 1 — Saneamento
- Inventariar todos os assets.
- Classificar risco A/B/C/D.
- Quarentenar C/D.

### Ciclo 2 — Reconstrução autoral
- Criar asset original do zero.
- Revisão por pares (legal + design técnico).
- Registrar licença e evidência.
- Promover para `approved`.

## Gate de CI recomendado
- Falhar se houver linha com `status=quarantine` em paths empacotados.
- Falhar se `license_spdx` for `UNKNOWN`.
- Falhar se `permission_proof` for `missing` em item não-autoral.
