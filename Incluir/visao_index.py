#!/usr/bin/env python3
"""
visao_index.py — Indexador real do dataset de imagens em ~/storage/downloads/visao/

Estrutura esperada:
  visao/
    to_do/       <- imagens novas, ainda não processadas
    doing/       <- em processamento/análise
    done/        <- processadas, catalogadas
    imgdataset/  <- (opcional) cópia/link das imagens finais
    indice.csv   <- gerado por este script (a "tabela relacional")
    indice.json  <- mesma info em JSON

Cada linha do índice contém, para cada arquivo real:
  nome, caminho, tamanho_bytes, sha256, crc32, extensao, pasta_atual, timestamp_indexado

SHA-256 e CRC32 aqui são hashes de verdade, calculados sobre os bytes reais
do arquivo — servem para: (1) detectar duplicatas, (2) verificar integridade
se o arquivo for movido/copiado, (3) referência estável mesmo se o nome mudar.

Isto NÃO é "blockchain" — é um índice local. Se quiser histórico auditável
de mudanças, use `git init` na pasta visao/ e commite o indice.csv a cada
rodada: o git já dá hash + histórico + diff de verdade, sem reinventar nada.
"""
import csv
import hashlib
import json
import os
import sys
import zlib
from datetime import datetime, timezone
from pathlib import Path

PASTAS_FLUXO = ["to_do", "doing", "done", "imgdataset"]
EXTENSOES_IMG = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".svg", ".heic"}


def calcular_hashes(caminho: Path):
    """SHA-256 (integridade forte) + CRC32 (checagem rápida) do arquivo real."""
    h_sha256 = hashlib.sha256()
    crc = 0
    with open(caminho, "rb") as f:
        while chunk := f.read(65536):
            h_sha256.update(chunk)
            crc = zlib.crc32(chunk, crc)
    return h_sha256.hexdigest(), format(crc & 0xFFFFFFFF, "08x")


def indexar(raiz: Path):
    registros = []
    for pasta in PASTAS_FLUXO:
        alvo = raiz / pasta
        if not alvo.is_dir():
            continue
        for item in sorted(alvo.iterdir()):
            if not item.is_file():
                continue
            if item.suffix.lower() not in EXTENSOES_IMG:
                continue
            sha256, crc32 = calcular_hashes(item)
            registros.append({
                "nome": item.name,
                "caminho": str(item.relative_to(raiz)),
                "tamanho_bytes": item.stat().st_size,
                "sha256": sha256,
                "crc32": crc32,
                "extensao": item.suffix.lower(),
                "pasta_atual": pasta,
                "timestamp_indexado": datetime.now(timezone.utc).isoformat(),
            })
    return registros


def salvar_csv(registros, destino: Path):
    if not registros:
        campos = ["nome", "caminho", "tamanho_bytes", "sha256", "crc32",
                  "extensao", "pasta_atual", "timestamp_indexado"]
    else:
        campos = list(registros[0].keys())
    with open(destino, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=campos)
        w.writeheader()
        w.writerows(registros)


def salvar_json(registros, destino: Path):
    with open(destino, "w", encoding="utf-8") as f:
        json.dump(registros, f, ensure_ascii=False, indent=2)


def detectar_duplicatas(registros):
    """Duplicata real = mesmo sha256, não mesmo nome."""
    por_hash = {}
    for r in registros:
        por_hash.setdefault(r["sha256"], []).append(r["caminho"])
    return {h: caminhos for h, caminhos in por_hash.items() if len(caminhos) > 1}


def main():
    raiz = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    raiz = raiz.expanduser().resolve()

    if not raiz.is_dir():
        print(f"[erro] pasta não existe: {raiz}")
        sys.exit(1)

    print(f"[*] Indexando: {raiz}")
    registros = indexar(raiz)

    salvar_csv(registros, raiz / "indice.csv")
    salvar_json(registros, raiz / "indice.json")

    print(f"[ok] {len(registros)} arquivos indexados -> indice.csv / indice.json")

    dups = detectar_duplicatas(registros)
    if dups:
        print(f"[aviso] {len(dups)} grupo(s) de arquivos duplicados (mesmo conteúdo, sha256 igual):")
        for h, caminhos in dups.items():
            print(f"  sha256={h[:12]}...")
            for c in caminhos:
                print(f"    - {c}")
    else:
        print("[ok] nenhuma duplicata real encontrada (por conteúdo)")


if __name__ == "__main__":
    main()
