# Mapa de ligação — GitHub ↔ Google Drive ↔ Dropbox legado ↔ ZIPRAFAELIA

Data de geração: 2026-06-12
Repositório alvo: `rafaelmeloreisnovo/Vectras-VM-Android`
Branch: `master`

> Objetivo: registrar, dentro do repositório, como a sessão isolada/ZIPRAFAELIA se conecta às bases externas conhecidas: Google Drive, Dropbox legado e GitHub.

---

## 1. Veredito executivo

A sessão isolada analisada não é apenas um chat. Ela funciona como um pacote de consolidação RAFAELIA contendo:

- memória textual massiva;
- exportações ChatGPT;
- banco SQLite;
- backups Termux/Android;
- zips/ZIPRAF;
- APKs;
- scripts de automação;
- imagens e artefatos multimodais.

O Google Drive contém sinais fortes de ser a camada persistente/remota dessa mesma base. O Dropbox legado foi consultado, mas não retornou resultados para `RAFAELIA` nesta busca inicial; portanto fica marcado como lacuna verificada, não como ausência absoluta.

---

## 2. GitHub — repositório alvo

| Campo | Valor |
|---|---|
| Repositório | `rafaelmeloreisnovo/Vectras-VM-Android` |
| Visibilidade | privado |
| Branch padrão | `master` |
| Permissões observadas | `admin`, `maintain`, `pull`, `push`, `triage` |
| Função neste mapa | destino versionado para consolidar documentação, laudos, mapas e vínculos externos |

---

## 3. Google Drive — vínculos encontrados

| Item no Drive | Tipo | Relação com ZIPRAFAELIA / sessão isolada | Status |
|---|---|---|---|
| `BLOCO_UNIFICADO_TOTAL_RafaelIA.txt` | texto mestre | provável bloco unificado da memória/corpus RAFAELIA | confirmado por busca |
| `RAFAELIA_BACKUP` | pasta | espelho/backup remoto dos backups locais RAFAELIA | confirmado por busca |
| `RAFAELIA_CORE` | pasta | núcleo remoto do projeto RAFAELIA | confirmado por busca |
| `RAFAELIA_STREAM_BACKUP` | pasta | histórico/stream/logs/backups de execução | confirmado por busca |
| `RAFAELIA_ZIPRAF_REAL_5GB (1)` | pasta | provável ZIPRAF maior/persistente ligado ao conceito ZIPRAFAELIA | confirmado por busca |
| `rafaelia_matrizes_v1` | pasta | matrizes/fractais; conecta com módulos de matriz, CRC, imagem e áudio | confirmado por busca |
| `VOYNICH_FIBONACCI_GASUR_RAFAELIA.md` | markdown | eixo simbólico-técnico Voynich/Fibonacci/GASUR | confirmado por busca |
| `LIVRO_VIVO_100P.pdf` | PDF | camada editorial/publicável do corpus | confirmado por busca |
| `VERBO_VIVO_INDEX.txt` | índice | provável índice textual/ontológico para navegar o corpus | confirmado por busca |
| `mapa.txt` | texto | possível índice/mapa de exportação ou organização | confirmado por busca |

---

## 4. Dropbox legado — busca inicial

| Consulta | Resultado | Interpretação |
|---|---|---|
| `RAFAELIA` | sem resultados | lacuna verificada; não prova inexistência |

Observação: o conector usado é `Dropbox (Legacy)`, que está depreciado. Recomendado migrar/atualizar o conector para o novo Dropbox quando possível.

---

## 5. Modelo de linkagem recomendado

Cada item externo deve ser registrado assim:

```yaml
id_local: ""
origem: "github | google_drive | dropbox_legacy | sandbox | chatgpt_export"
nome: ""
tipo: "core | backup | zipraf | matriz | log | indice | livro | banco | apk | imagem | conversa | lacuna"
status: "confirmado | provável | lacuna | duplicado | obsoleto"
prova: "nome do arquivo, pasta, hash, data, caminho, citação ou commit"
acao_proxima: "fetch | indexar | comparar_hash | deduplicar | migrar | documentar"
```

---

## 6. Arquitetura de consolidação

```text
Google Drive
  ├── RAFAELIA_CORE
  ├── RAFAELIA_BACKUP
  ├── RAFAELIA_STREAM_BACKUP
  ├── RAFAELIA_ZIPRAF_REAL_5GB
  ├── rafaelia_matrizes_v1
  ├── BLOCO_UNIFICADO_TOTAL_RafaelIA.txt
  └── VERBO_VIVO_INDEX.txt
        ↓
ZIPRAFAELIA / sessão isolada
  ├── Σ_MEMORIA_TOTAL_RAFAELI2A.txt
  ├── rafaelia_campo_avancado.db
  ├── ChatGPT exports
  ├── backups .tar.gz
  ├── zips RAFAELIA
  ├── APKs
  └── mapas gerados
        ↓
GitHub / Vectras-VM-Android
  └── mapa/
      └── GITHUB_GOOGLE_DRIVE_DROPBOX_LEGADO_ZIPRAFAELIA_MAP.md
```

---

## 7. Próximas ações técnicas

1. Fazer `fetch`/leitura dos itens-chave do Google Drive.
2. Comparar nomes e, quando possível, hashes/tamanhos com os arquivos locais da sessão.
3. Criar índice `mapa/EXTERNAL_ASSET_INDEX.json`.
4. Separar estados:
   - `FATO`: item encontrado com nome/caminho/data.
   - `HIPÓTESE`: item provável por nome/contexto.
   - `LACUNA`: item buscado e não localizado.
   - `RISCO`: duplicado, obsoleto, parcial ou conector legado.
5. Migrar Dropbox legado para novo conector quando disponível.

---

## 8. Retroalimentação RAFAELIA

- `F_ok`: Google Drive linka fortemente com a sessão isolada e com ZIPRAFAELIA.
- `F_gap`: Dropbox legado não retornou RAFAELIA; conteúdo interno das pastas Drive ainda precisa ser aberto e indexado.
- `F_next`: criar índice JSON de ativos externos e comparar com o inventário local gerado no ZIPRAFAELIA.
