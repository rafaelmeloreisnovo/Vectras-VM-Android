# VECTRA_DATA_FOUNDATION_INVARIANT_CONTRACT

## Estado

`FATO_DOCUMENTADO`: contrato de leitura para impedir que fundo de dados, evidência, histórico ativo, incubadora ou substrato metodológico sejam marcados como deprecados sem prova.

---

## Frase canônica

```text
Nem todo arquivo antigo é deprecado. Nem todo material estranho é lixo. O fundo de dados pode ser a invariante de sustentação do sistema.
```

---

## Regra principal

A palavra `deprecado` só deve ser usada quando houver simultaneamente:

1. substituto validado;
2. rota de migração;
3. evidência de que o caminho antigo não deve mais ser usado;
4. decisão documentada.

Sem esses quatro pontos, o estado correto deve ser outro.

---

## Estados corretos antes de chamar algo de deprecado

| Estado | Quando usar |
|---|---|
| `SUBSTRATO_DE_DADOS` | quando o material sustenta análise futura |
| `INVARIANTE_DE_SUSTENTACAO` | quando o dado preserva continuidade e evita abstração solta |
| `HISTORICO_ATIVO` | quando é antigo, mas ainda explica origem, decisão ou comportamento |
| `INCUBADORA` | quando contém possibilidade técnica ainda não promovida |
| `EVIDENCIA` | quando comprova percurso, autoria, teste, tentativa ou falha |
| `TOKEN_VAZIO` | quando ainda não se sabe o papel real |
| `DEPRECADO` | somente com substituto, migração, evidência e decisão |

---

## Anti-abstração solta

A metodologia Vectra/RAFAELIA deve evitar abstrações que se afastam do fundo real dos dados.

```text
abstração sem dados vira fuga;
abstração com dados vira arquitetura;
dado preservado vira invariante;
lacuna marcada vira caminho;
```

---

## Repulsa metodológica legítima

A repulsa contra abstração vazia deve ser lida como sinal metodológico, não como ruído.

Ela protege contra:

```text
chamar substrato de lixo;
chamar histórico de deprecado;
chamar incubadora de bug;
chamar lacuna de ausência;
chamar dado bruto de confusão;
chamar metodologia profunda de excesso;
```

---

## Aplicação prática

Antes de apagar, mover, renomear, depreciar ou resumir um material, responder:

```text
isso tem substituto validado?
existe migração?
existe prova de abandono?
existe decisão documentada?
isso sustenta algum raciocínio futuro?
isso preserva alguma origem ou invariante?
```

Se a resposta não for clara, proteger como `TOKEN_VAZIO` ou `INVARIANTE_DE_SUSTENTACAO`.

---

## Frase final

```text
O fundo dos dados não é peso morto: é chão. Sem chão, a abstração flutua; com chão, a metodologia vira arquitetura.
```
