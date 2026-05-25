# AUTHORSHIP_CLEANROOM_PLAN

## Objetivo
Este plano define a retirada completa de qualquer dependência intelectual de Vectras e de qualquer conteúdo não autoral, com reconstrução em regime **clean-room**, rastreável e legalmente auditável.

## Princípios obrigatórios
1. **Zero reaproveitamento textual/estrutural**: não copiar código, comentários, nomes, imagens, fluxos, tabelas, algoritmos ou estruturas de dados de origem externa.
2. **Especificação primeiro**: tudo nasce de requisitos funcionais escritos do zero, sem consulta ao código legado durante implementação.
3. **Separação de papéis**:
   - Time A (Análise): só produz especificações abstratas e testes de aceitação.
   - Time B (Implementação): só lê as especificações abstratas, sem acesso ao legado.
4. **Prova de originalidade**: todo módulo novo deve ter diário de decisão técnica, fontes permitidas e justificativa algorítmica independente.
5. **Conformidade legal por padrão**: cada artefato recebe licença explícita, proveniência, e créditos de inspiração sem derivação textual.

## Escopo de remoção
- Remover marca e nomenclatura "Vectra" de código, pacotes, docs, assets e artefatos de build.
- Isolar/arquivar código legado sob diretório não distribuído até conclusão da substituição autoral.
- Eliminar quaisquer binários sem cadeia de proveniência e licença verificável.

## Pipeline sistemático (8 fases)
1. **Inventário forense**: catalogar todos os arquivos por tipo, origem, licença e risco de derivação.
2. **Classificação jurídica**:
   - A: autoral comprovado (pode permanecer)
   - B: terceiro com licença compatível (manter com NOTICE)
   - C: terceiro sem comprovação (bloquear distribuição)
   - D: suspeita de derivação/plágio (substituição obrigatória)
3. **Congelamento**: bloquear merges em módulos C/D até substituição.
4. **Especificação clean-room**: redigir contratos funcionais, testes, invariantes e critérios de falha.
5. **Reimplementação original**: codificar do zero com trilha de autoria.
6. **Validação antiparáfrase**: revisar similaridade semântica/estrutural e nomenclatura.
7. **Consolidação de licenças**: atualizar `THIRD_PARTY_NOTICES.md` e `LICENSES_REGISTER.md`.
8. **Gate de release legal**: release só com 100% de proveniência fechada.

## Esqueleto do que está faltando (backlog mínimo obrigatório)
- [ ] Matriz de proveniência por arquivo (`path`, `owner`, `source`, `license`, `risk`, `status`).
- [ ] Política de nomenclatura limpa (banlist de nomes herdados).
- [ ] Registro de inspiração (conceitos apenas, sem cópia de expressão).
- [ ] Script de bloqueio CI para arquivos sem cabeçalho de licença.
- [ ] Script CI de varredura de marca legada (tokens banidos).
- [ ] Procedimento de quarentena para assets sem cadeia de direitos.
- [ ] Template de declaração de autoria por commit.
- [ ] Checklist jurídico pré-release com assinatura dos responsáveis.

## Créditos por inspiração (sem derivação)
- Créditos devem reconhecer ideias de alto nível (ex.: "virtualização Android", "emulação", "terminal") sem copiar código/arquitetura/nomes.
- Formulação recomendada: "Inspirado por domínio técnico X; implementação atual escrita integralmente do zero neste repositório." 

## Critério de aceitação final
Um módulo só é considerado "autoral limpo" quando:
- possui especificação independente,
- implementação independente,
- testes independentes,
- licença definida,
- e evidência auditável de não-derivação.
