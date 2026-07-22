#!/usr/bin/env bash
# ============================================================
# Instalador dos skills casa-conceitos + rafaelia-corpus
# Termux / Moto E7 / ARM32
# ============================================================
set -euo pipefail

TARGET="${1:-$HOME/.claude/skills/user}"

echo "[*] Instalando skills em: $TARGET"
mkdir -p "$TARGET"

if [ ! -f "rafaelia-skills-install.tar.gz" ]; then
    echo "[ERRO] rafaelia-skills-install.tar.gz não encontrado no diretório atual."
    echo "       Baixe o arquivo do chat e coloque na mesma pasta que este script."
    exit 1
fi

tar -xzf rafaelia-skills-install.tar.gz -C "$TARGET"

echo "[*] Conteúdo instalado:"
find "$TARGET/casa-conceitos" "$TARGET/rafaelia-corpus" -type f 2>/dev/null | sed 's/^/    /'

echo ""
echo "[OK] casa-conceitos e rafaelia-corpus instalados em $TARGET"
echo "     Ajuste TARGET (primeiro argumento) se seu ambiente usar outro caminho de skills."
