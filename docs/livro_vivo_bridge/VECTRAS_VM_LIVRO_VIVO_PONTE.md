# Ponte Livro Vivo — Vectras VM Android e Execução Isolada

> Modo: ponte operacional entre `Vectras-VM-Android` e o Livro Vivo RAFAELIA  
> Status inicial: `FORMALIZACAO_READY`  
> Regra: VM precisa separar anfitrião, convidado, imagem, permissão, comando e saída

## Parábola da casa dentro da casa

O discípulo colocou uma casa dentro de outra casa.

O mestre perguntou:

— Quem abre a primeira porta?

— O anfitrião.

— Quem mora na segunda?

— O convidado.

— E quem guarda a chave entre as duas?

O discípulo ficou em silêncio.

O mestre disse:

— Então ainda não tens VM. Tens apenas duas casas encostadas.

## Invariante

```text
Android host → VM guest → imagem/sistema → comando → saída isolada
```

Forma compacta:

```math
Inv(Vectras)=Host\rightarrow Guest\rightarrow Interface\rightarrow Execução\rightarrow Relatório
```

## Risco principal

| Risco | Correção |
|---|---|
| misturar app, VM, QEMU e ROM | criar mapa operacional separado |
| não declarar imagem convidada | registrar origem e checksum |
| permissões Android indefinidas | declarar permissões e storage |
| resultado sem comando | criar smoke test copiável |
| confundir host real com guest emulado | separar logs por camada |

## Próximos passos

1. Criar `VECTRAS_OPERATIONAL_MAP.md`.
2. Separar host Android, app, assets, guest, storage e rede.
3. Declarar imagens suportadas e checksums.
4. Criar smoke test mínimo.
5. Documentar falhas conhecidas e limitações sem root.

## Ficha Livro Vivo

```yaml
repo: rafaelmeloreisnovo/Vectras-VM-Android
familia: Android/VM
invariante: "Android host → VM guest → imagem/sistema → comando → saída isolada"
selo: FORMALIZACAO_READY
risco: "misturar host, guest, QEMU, ROM, assets e permissões sem mapa operacional"
proximo_passo: "criar VECTRAS_OPERATIONAL_MAP.md e smoke test mínimo"
```

## Retroalimentar[3]

- **F_ok:** Vectras recebe ponte clara para separar host, guest, interface e execução.
- **F_gap:** falta inventário real de imagens, permissões, comandos e falhas conhecidas.
- **F_next:** criar `VECTRAS_OPERATIONAL_MAP.md` com host, guest, assets, storage, rede e smoke test.
