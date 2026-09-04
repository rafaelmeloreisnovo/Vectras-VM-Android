# AUTHORSHIP_CLEANROOM_PLAN — autoria, upstream licenciado e clean-room

## Objetivo

Garantir que o projeto seja **autoral onde há criação independente comprovável** e **transparentemente derivado/terceiro onde existe upstream legítimo**, sem apagar história, licença ou autoria alheia.

Este plano substitui a interpretação anterior de “clean-room para tudo”. Clean-room é uma ferramenta específica, não um requisito universal.

## Princípio central

> A menor unidade prática de origem pertence à origem que a evidência demonstra.

Se um arquivo, função, patch, asset, algoritmo implementado, texto ou binário vem de upstream/terceiro, ele permanece classificado como tal mesmo quando o produto maior é mantido pela RAFAELIA. Modificações locais são nossas modificações; não transformam retrospectivamente o upstream em autoria local.

Na dúvida: **pró-upstream / pró-oficial para fins de atribuição, nunca pró-autoria local**. Registrar `PRO_OFFICIAL_PENDING` ou `TOKEN_VAZIO`, `claim_allowed=false`, e executar o próximo gate de proveniência.

## Duas vias legítimas

### Via A — upstream/terceiro licenciado

Usar quando origem e licença permitem o uso pretendido.

Obrigatório:

- preservar copyright, licença, NOTICE e atribuição exigidos;
- registrar fonte canônica e ref/versão quando possível;
- distinguir `UPSTREAM_VERIFIED` de `DERIVATIVE_MODIFIED`;
- descrever modificações locais sem insinuar autoria do original;
- verificar compatibilidade entre licenças e o modo de distribuição;
- refletir a proveniência no About/Notices do produto quando aplicável.

Licença permissiva não significa “sem autoria” nem “sem atribuição”: cumprir exatamente suas condições.

### Via B — clean-room independente

Usar quando houver motivo técnico/jurídico/estratégico para não reutilizar a expressão do upstream, especialmente quando:

- licença/origem é incompatível ou não verificável;
- o componente não pode ser redistribuído de forma segura;
- deseja-se substituição independente com fronteira auditável;
- há risco real de derivação indevida.

Nesse caso:

1. produzir requisitos/contratos funcionais independentes;
2. separar, quando necessário, análise de legado e implementação;
3. implementar sem copiar expressão protegida;
4. criar testes independentes e diário de decisão;
5. manter evidência de fontes permitidas e de não-derivação.

Não chamar algo de clean-room se implementadores consultaram/copiaram o material que a alegação de independência pretende excluir.

## Classes de origem

| Estado | Significado | Distribuição/claim |
|---|---|---|
| `ORIGINAL_VERIFIED` | autoria local com cadeia auditável | autoria local pode ser afirmada no escopo evidenciado |
| `UPSTREAM_VERIFIED` | upstream preservado e origem/licença verificadas | manter atribuição/licença |
| `DERIVATIVE_MODIFIED` | upstream + modificações locais | atribuir upstream + declarar modificações locais |
| `THIRD_PARTY_VERIFIED` | terceiro com direitos/licença verificados | cumprir licença/NOTICE |
| `PRO_OFFICIAL_PENDING` | origem ambígua; presume-se não-local para claims | não afirmar autoria; abrir gate |
| `TOKEN_VAZIO` | evidência insuficiente | não promover claim |
| `QUARANTINED` | risco jurídico/proveniência/segurança bloqueante | excluir de distribuição até resolução |

## Pipeline sistemático

1. **Inventário forense** — catalogar arquivos/componentes distribuídos por tipo, origem provável, licença, hash e risco.
2. **Granularização** — atribuir na menor unidade prática que evita misturar upstream e modificação local.
3. **Verificação de origem** — fonte canônica, histórico, ref/tag/commit e autores quando aplicável.
4. **Verificação de licença** — SPDX somente quando confirmado; registrar texto/NOTICE necessário.
5. **Escolha da via** — A (licensed-upstream) ou B (clean-room); justificar a decisão.
6. **Implementação/modificação** — respeitar fronteira e registrar decisão técnica.
7. **Validação** — testes funcionais, regressão, proveniência, segurança e empacotamento.
8. **About/Notices** — gerar disclosure consistente com o conjunto realmente distribuído.
9. **Gate de release** — nenhuma lacuna bloqueante de origem/licença no artefato distribuído.
10. **Receipt** — ligar source commit → build → artifact digest → registros de licença/proveniência → claim permitido.

## Registro mínimo por unidade

```text
path/component
origin_class
canonical_source
upstream_ref/version
license/SPDX
copyright/notice obligations
local_author/maintainer
local_modification_summary
source_digest/artifact_digest
evidence_refs
about_required
distribution_allowed
claim_allowed
next_gate
```

## About / transparência pública

O produto deve possuir um ponto central de disclosure em About, com:

- identidade do projeto atual;
- upstream/base e fontes canônicas;
- modificações locais;
- terceiros e licenças;
- versão/build/source identity;
- estado de proveniência/compliance sem marketing de certificação não demonstrada.

Não é necessário poluir cada tela com créditos repetidos quando a licença não exige isso. Porém qualquer tela/asset/componente distribuído deve ser rastreável até o registro canônico, e atribuições específicas devem aparecer onde a licença exigir.

## Backlog bloqueante já conhecido

- [ ] completar matriz de proveniência por arquivo/componente distribuído;
- [ ] verificar headers/licença do fork histórico Vectras por unidade relevante;
- [ ] fechar proveniência dos `.so`, firmware, BIOS/OVMF, rootfs/tarballs e assets distribuídos;
- [ ] reconciliar `THIRD_PARTY_NOTICES.md`, `LICENSES_REGISTER.md` e registro de assets;
- [ ] substituir listas/links hard-coded da tela About por manifesto canônico verificado;
- [ ] gerar/validar SBOM do artefato de release quando tecnicamente suportado;
- [ ] gate CI para impedir componente sem proveniência/licença no pacote final;
- [ ] receipt de release ligando fonte, build, hashes, notices e escopo de claim.

## Critério de aceitação

Um componente só pode ser chamado **autoral** quando existe evidência de autoria independente. Um componente upstream/terceiro só pode ser distribuído quando origem, direitos/licença e obrigações aplicáveis estão suficientemente fechados. Quando nenhum dos dois é demonstrado, o estado verdadeiro é `TOKEN_VAZIO`/`PRO_OFFICIAL_PENDING`/`QUARANTINED`, nunca uma conclusão inventada.
