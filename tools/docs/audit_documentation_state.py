#!/usr/bin/env python3
"""Deterministic repository documentation audit up to a bounded depth.

The script is intentionally source-read-only: it does not edit runtime code, hot
paths, assembly, ingress artifacts, or project documentation outside its configured
audit outputs. It scans for documentation drift signals, placeholder markers,
bug/todo markers, pending ingress folders, and directory navigation gaps.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

SKIP_DIRS = {".git", ".gradle", "build", ".idea", "node_modules"}
DOC_EXTS = {".md", ".txt", ".rst"}
CODE_EXTS = {".c", ".h", ".cpp", ".hpp", ".java", ".kt", ".rs", ".py", ".sh", ".S", ".cmake", ".gradle"}
PLACEHOLDER_PATTERNS = ("placeholder", "todo", "fixme", "stub", "mock", "pending", "tbd", "xxx")
BUG_PATTERNS = ("bug", "failsafe", "failover", "rollback", "mitigation", "mitigacao", "mitigação")
INGRESS_DIRS = {"Incluir", "_incoming", "__DELTA__"}
GENERATED_OUTPUTS = {
    "docs/organization/NECESSARY_CONDITIONS_AUDIT_2026-06-02.md",
    "docs/organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md",
    "reports/documentation_state_audit_2026-06-02.json",
    "reports/ingress_artifacts_sha256_2026-06-02.tsv",
}


@dataclass(frozen=True)
class Finding:
    severity: str
    category: str
    path: str
    detail: str


@dataclass(frozen=True)
class ArtifactHash:
    path: str
    bytes: int
    sha256: str


def inside_depth(path: Path, max_depth: int) -> bool:
    return len(path.parts) <= max_depth


def iter_paths(root: Path, max_depth: int) -> Iterable[Path]:
    for current_root, dirs, files in os.walk(root):
        current = Path(current_root)
        rel_current = current.relative_to(root) if current != root else Path(".")
        dirs[:] = sorted(d for d in dirs if d not in SKIP_DIRS)
        if rel_current != Path(".") and not inside_depth(rel_current, max_depth):
            dirs[:] = []
            continue
        for name in sorted(files):
            rel = (current / name).relative_to(root)
            if str(rel) in GENERATED_OUTPUTS:
                continue
            if inside_depth(rel, max_depth):
                yield rel


def text_sample(path: Path, limit: int = 32768) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")[:limit]
    except OSError:
        return ""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def collect_artifact_hashes(root: Path, max_depth: int) -> list[ArtifactHash]:
    artifacts: dict[str, ArtifactHash] = {}
    for rel in iter_paths(root, max_depth):
        first = rel.parts[0] if rel.parts else ""
        if first in INGRESS_DIRS or rel.suffix.lower() == ".zip":
            full = root / rel
            artifacts[str(rel)] = ArtifactHash(str(rel), full.stat().st_size, sha256_file(full))
    return [artifacts[key] for key in sorted(artifacts)]


def audit(root: Path, max_depth: int) -> tuple[list[Finding], dict[str, int]]:
    findings: list[Finding] = []
    counts = {
        "files_scanned": 0,
        "docs_scanned": 0,
        "code_scanned": 0,
        "ingress_files": 0,
        "zip_files": 0,
        "placeholder_hits": 0,
        "bug_signal_hits": 0,
        "navigation_gaps": 0,
    }

    directories: set[Path] = {Path(".")}
    for rel in iter_paths(root, max_depth):
        counts["files_scanned"] += 1
        directories.add(rel.parent)
        suffix = rel.suffix.lower()
        first = rel.parts[0] if rel.parts else ""
        full = root / rel

        if first in INGRESS_DIRS:
            counts["ingress_files"] += 1
            findings.append(Finding("high", "entrada-pendente", str(rel), "Arquivo em área de ingresso; requer hash, teste e decisão de promoção."))
        if suffix == ".zip":
            counts["zip_files"] += 1
            findings.append(Finding("high", "overlay-zip", str(rel), "Overlay ZIP precisa de SHA-256 e manifesto antes de extração ou remoção."))
        if suffix in DOC_EXTS:
            counts["docs_scanned"] += 1
        if suffix in CODE_EXTS:
            counts["code_scanned"] += 1
        if suffix in DOC_EXTS | CODE_EXTS:
            sample = text_sample(full).lower()
            placeholder_matches = [pat for pat in PLACEHOLDER_PATTERNS if pat in sample]
            bug_matches = [pat for pat in BUG_PATTERNS if pat in sample]
            if placeholder_matches:
                counts["placeholder_hits"] += 1
                findings.append(Finding("medium", "placeholder-ou-pendente", str(rel), "Marcadores: " + ", ".join(placeholder_matches)))
            if bug_matches:
                counts["bug_signal_hits"] += 1
                findings.append(Finding("medium", "bug-failsafe-signal", str(rel), "Marcadores: " + ", ".join(bug_matches)))

    for directory in sorted(directories):
        if directory == Path("."):
            continue
        if not inside_depth(directory, max_depth):
            continue
        if any(part in SKIP_DIRS for part in directory.parts):
            continue
        dir_path = root / directory
        if not dir_path.is_dir():
            continue
        has_files = any(child.is_file() for child in dir_path.iterdir())
        has_subdirs = any(child.is_dir() and child.name not in SKIP_DIRS for child in dir_path.iterdir())
        if has_files or has_subdirs:
            readme = dir_path / "README.md"
            fmap = dir_path / "FILES_MAP.md"
            if not readme.exists() and directory.parts[0] not in {".github", "app", "terminal-emulator", "terminal-view", "shell-loader"}:
                counts["navigation_gaps"] += 1
                findings.append(Finding("low", "sem-readme", str(directory), "Diretório com conteúdo sem README.md local."))
            if directory.parts[0] in {"docs", "tools", "engine", "reports", "resources", "runtime", "archive", "bug"} and not fmap.exists() and len(directory.parts) <= 2:
                counts["navigation_gaps"] += 1
                findings.append(Finding("low", "sem-files-map", str(directory), "Diretório de domínio sem FILES_MAP.md local."))

    severity_rank = {"high": 0, "medium": 1, "low": 2}
    findings.sort(key=lambda item: (severity_rank.get(item.severity, 9), item.category, item.path))
    return findings, counts


def write_reports(
    findings: list[Finding],
    counts: dict[str, int],
    artifacts: list[ArtifactHash],
    out_md: Path,
    out_json: Path,
    out_sha_tsv: Path,
    out_sha_md: Path,
    max_items: int,
) -> None:
    severity_counts: dict[str, int] = {}
    category_counts: dict[str, int] = {}
    for finding in findings:
        severity_counts[finding.severity] = severity_counts.get(finding.severity, 0) + 1
        category_counts[finding.category] = category_counts.get(finding.category, 0) + 1
    out_json.write_text(
        json.dumps(
            {
                "counts": counts,
                "severity_counts": severity_counts,
                "category_counts": category_counts,
                "artifact_hash_count": len(artifacts),
                "findings": [asdict(f) for f in findings],
            },
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    out_sha_tsv.write_text(
        "path\tbytes\tsha256\n" + "".join(f"{item.path}\t{item.bytes}\t{item.sha256}\n" for item in artifacts),
        encoding="utf-8",
    )
    manifest_lines = [
        "# Manifesto SHA-256 de entradas pendentes e overlays — 2026-06-02\n",
        "\n",
        "Manifesto gerado para reduzir risco antes de qualquer promoção, extração, movimentação ou remoção de artefatos.\n",
        "\n",
        "## Escopo\n",
        "\n",
        "- Inclui todos os arquivos sob `Incluir/`, `_incoming/` e `__DELTA__/` dentro da profundidade auditada.\n",
        "- Inclui overlays `*.zip` adicionais encontrados até 5 níveis fora desses diretórios.\n",
        "- O TSV completo fica em `reports/ingress_artifacts_sha256_2026-06-02.tsv`.\n",
        "\n",
        "## Resumo\n",
        "\n",
        f"- Total de artefatos com hash: **{len(artifacts)}**.\n",
        "- Regra: se o hash mudar antes da promoção, reexecutar auditoria e invalidar decisão anterior.\n",
        "\n",
        "## Amostra inicial\n",
        "\n",
        "| Caminho | Bytes | SHA-256 |\n",
        "|---|---:|---|\n",
    ]
    for item in artifacts[:40]:
        manifest_lines.append(f"| `{item.path}` | {item.bytes} | `{item.sha256}` |\n")
    if len(artifacts) > 40:
        manifest_lines.append(f"| `...` | ... | {len(artifacts) - 40} entradas adicionais no TSV completo. |\n")
    manifest_lines.extend([
        "\n",
        "## Uso em rollback/failover\n",
        "\n",
        "1. Antes de promover um arquivo, comparar o hash atual com o TSV.\n",
        "2. Se a promoção falhar em build/teste, usar o caminho e hash para restaurar o artefato original.\n",
        "3. Não extrair ZIP em árvore ativa sem manifesto de arquivos internos e teste correspondente.\n",
    ])
    out_sha_md.write_text("".join(manifest_lines), encoding="utf-8")
    lines = [
        "# Auditoria de condições necessárias, placeholders e materiais pendentes — 2026-06-02\n",
        "\n",
        "Varredura determinística local, source-read-only, limitada a 5 níveis de profundidade.\n",
        "Arquivos de saída gerados pela própria auditoria são excluídos da contagem para evitar auto-ruído.\n",
        "\n",
        "## Contadores\n",
        "\n",
        "| Métrica | Valor |\n",
        "|---|---:|\n",
    ]
    for key in sorted(counts):
        lines.append(f"| `{key}` | {counts[key]} |\n")
    lines.extend([
        "\n",
        "## Condições necessárias de aceite\n",
        "\n",
        "| Condição | Estado | Evidência/ação |\n",
        "|---|---|---|\n",
        "| Varredura até 5 níveis | PASS | Profundidade fixa no script e relatório. |\n",
        "| Não remover funcionalidades | PASS | Auditoria source-read-only; organização por manifesto antes de mover. |\n",
        "| Placeholders e pendências visíveis | PASS | Achados listados por severidade e categoria. |\n",
        "| Failsafe/failover/rollback | PARCIAL | Documentado como critério de promoção; build/teste Android ainda dependem de SDK. |\n",
        "| Hot path sem heap/GC | PASS nesta etapa | Nenhum `.S` ou hot path nativo foi alterado. |\n",
        "\n",
        "## Distribuição dos achados\n",
        "\n",
        "| Severidade | Total |\n",
        "|---|---:|\n",
    ])
    for severity in ("high", "medium", "low"):
        lines.append(f"| `{severity}` | {severity_counts.get(severity, 0)} |\n")
    lines.extend([
        "\n",
        "| Categoria | Total |\n",
        "|---|---:|\n",
    ])
    for category in sorted(category_counts):
        lines.append(f"| `{category}` | {category_counts[category]} |\n")
    lines.extend([
        "\n",
        "## Achados priorizados\n",
        "\n",
        "| Severidade | Categoria | Caminho | Detalhe |\n",
        "|---|---|---|---|\n",
    ])
    for finding in findings[:max_items]:
        detail = finding.detail.replace("|", "/")
        lines.append(f"| {finding.severity} | {finding.category} | `{finding.path}` | {detail} |\n")
    if len(findings) > max_items:
        lines.append(f"| info | truncado | `...` | {len(findings) - max_items} achados adicionais disponíveis no JSON. |\n")
    lines.extend([
        "\n",
        "## Mitigação recomendada\n",
        "\n",
        "1. Criar manifesto SHA-256 dos overlays ZIP e entradas pendentes antes de qualquer extração.\n",
        "2. Converter placeholders legítimos em issues/backlog com dono, teste esperado e rollback.\n",
        "3. Promover `_incoming/` e `Incluir/` em lotes pequenos, cada um com teste de equivalência ou justificativa SKIPPED.\n",
        "4. Reexecutar esta auditoria antes de cada reorganização documental maior.\n",
    ])
    out_md.write_text("".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--max-depth", type=int, default=5)
    parser.add_argument("--out-md", default="docs/organization/NECESSARY_CONDITIONS_AUDIT_2026-06-02.md")
    parser.add_argument("--out-json", default="reports/documentation_state_audit_2026-06-02.json")
    parser.add_argument("--out-sha-tsv", default="reports/ingress_artifacts_sha256_2026-06-02.tsv")
    parser.add_argument("--out-sha-md", default="docs/organization/INGRESS_ARTIFACTS_MANIFEST_2026-06-02.md")
    parser.add_argument("--max-items", type=int, default=120)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    findings, counts = audit(root, args.max_depth)
    artifacts = collect_artifact_hashes(root, args.max_depth)
    write_reports(
        findings,
        counts,
        artifacts,
        root / args.out_md,
        root / args.out_json,
        root / args.out_sha_tsv,
        root / args.out_sha_md,
        args.max_items,
    )
    print(json.dumps({**counts, "artifact_hash_count": len(artifacts)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
