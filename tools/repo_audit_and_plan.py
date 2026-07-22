#!/usr/bin/env python3
"""Auditoria linear: encontra lacunas de documentação e gera plano de execução."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

DOC_EXT = {".md", ".txt", ".rst"}
CODE_EXT = {".py", ".c", ".h", ".cpp", ".java", ".kt", ".sh", ".yml", ".yaml", ".json"}
SKIP_EXT = {".zip", ".png", ".jpg", ".jpeg", ".xlsx", ".docx", ".pdf"}


def has_inline_doc(path: Path) -> bool:
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except Exception:
        return False
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    if not lines:
        return False
    head = "\n".join(lines[:12]).lower()
    tags = ("\"\"\"", "/*", "# ", "//", "usage", "descr", "overview", "readme")
    return any(t in head for t in tags)


def audit(root: Path) -> dict:
    files = [p for p in root.rglob("*") if p.is_file()]
    entries = []
    for p in files:
        ext = p.suffix.lower()
        rel = p.relative_to(root).as_posix()
        if ext in SKIP_EXT:
            status = "binary_or_archive"
            doc_needed = False
        elif ext in CODE_EXT:
            status = "code"
            doc_needed = not has_inline_doc(p)
        elif ext in DOC_EXT:
            status = "doc"
            doc_needed = False
        else:
            status = "other"
            doc_needed = True
        entries.append({"file": rel, "ext": ext, "status": status, "needs_doc_or_action": doc_needed})

    pending = [e for e in entries if e["needs_doc_or_action"]]
    by_status = {}
    for e in entries:
        by_status[e["status"]] = by_status.get(e["status"], 0) + 1

    return {
        "total_files": len(entries),
        "summary_by_status": by_status,
        "pending_count": len(pending),
        "pending": pending,
    }


def write_outputs(root: Path, report: dict) -> None:
    out_json = root / "Incluir" / "AUDIT_REPORT.json"
    out_md = root / "Incluir" / "AUDIT_REPORT.md"
    out_json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# Relatório de Auditoria (arquivos, lacunas e próximos passos)",
        "",
        f"- Total de arquivos analisados: **{report['total_files']}**",
        f"- Pendências (documentar/codificar): **{report['pending_count']}**",
        "",
        "## Resumo por status",
    ]
    for k, v in report["summary_by_status"].items():
        lines.append(f"- {k}: {v}")
    lines += ["", "## Pendências detectadas", ""]
    if not report["pending"]:
        lines.append("- Nenhuma pendência detectada.")
    else:
        for item in report["pending"][:200]:
            lines.append(f"- `{item['file']}` ({item['status']})")
    lines += [
        "",
        "## Execução recomendada (linear)",
        "1. Priorizar arquivos `code` sem cabeçalho/documentação inline.",
        "2. Criar ou atualizar README local por diretório com código executável.",
        "3. Cobrir módulos críticos com testes de invariantes (limites, integridade, estabilidade).",
        "4. Reexecutar esta auditoria e comparar `pending_count`.",
    ]
    out_md.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".")
    args = ap.parse_args()
    root = Path(args.root).resolve()
    report = audit(root)
    write_outputs(root, report)
    print(json.dumps({"total_files": report["total_files"], "pending_count": report["pending_count"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
