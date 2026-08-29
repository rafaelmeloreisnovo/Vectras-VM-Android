#!/usr/bin/env python3
"""Materializa TARs de bootstrap a partir de um commit Git exato.

O script busca o SHA pinado diretamente, faz sparse checkout, valida que o objeto
materializado é exatamente o commit esperado, inspeciona os TARs e grava receipt
JSON. O HEAD da branch remota é apenas uma observação de drift: branches podem
avançar; o SHA imutável é a autoridade de reprodutibilidade.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "tools" / "ci" / "bootstrap-assets.v1.json"
EXTERNAL_MANIFEST = ROOT / "tools" / "ci" / "external_sources.manifest"
TOKEN_VAZIO = "TOKEN_VAZIO"


class ContractError(RuntimeError):
    pass


def run(command: list[str], *, cwd: Path | None = None, stdin: str | None = None) -> str:
    completed = subprocess.run(
        command,
        cwd=cwd,
        input=stdin,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if completed.returncode != 0:
        rendered = " ".join(command)
        raise ContractError(
            f"comando falhou ({completed.returncode}): {rendered}\n"
            f"stdout:\n{completed.stdout}\nstderr:\n{completed.stderr}"
        )
    return completed.stdout.strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_member_name(name: str) -> bool:
    pure = PurePosixPath(name)
    return not pure.is_absolute() and ".." not in pure.parts


def inspect_tar(path: Path) -> dict[str, Any]:
    try:
        with tarfile.open(path, "r:*") as archive:
            members = archive.getmembers()
    except (tarfile.TarError, OSError) as exc:
        raise ContractError(f"TAR inválido {path}: {exc}") from exc

    if not members:
        raise ContractError(f"TAR vazio: {path}")

    unsafe = [member.name for member in members if not safe_member_name(member.name)]
    if unsafe:
        raise ContractError(f"TAR contém caminhos inseguros em {path}: {unsafe[:5]}")

    return {
        "entries": len(members),
        "first_entry": members[0].name,
        "contains_absolute_or_parent_paths": False,
    }


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ContractError(f"manifesto inválido {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise ContractError("manifesto precisa ser um objeto JSON")
    return value


def external_source_row(name: str) -> dict[str, str]:
    if not EXTERNAL_MANIFEST.is_file():
        raise ContractError(f"manifesto externo ausente: {EXTERNAL_MANIFEST.relative_to(ROOT)}")

    for raw_line in EXTERNAL_MANIFEST.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("|")
        if len(fields) != 5:
            raise ContractError(f"linha inválida em {EXTERNAL_MANIFEST}: {raw_line}")
        row_name, url, branch, dest, pin = fields
        if row_name == name:
            return {"name": row_name, "url": url, "branch": branch, "dest": dest, "commit": pin}
    raise ContractError(f"fonte '{name}' não encontrada em {EXTERNAL_MANIFEST.relative_to(ROOT)}")


def validate_manifest(manifest: dict[str, Any]) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    source = manifest.get("source")
    assets = manifest.get("assets")
    if not isinstance(source, dict) or not isinstance(assets, list) or not assets:
        raise ContractError("manifesto exige source e assets não vazio")

    required_source = ("external_manifest_name", "url", "branch", "commit")
    for key in required_source:
        if not isinstance(source.get(key), str) or not source[key].strip():
            raise ContractError(f"source.{key} ausente")

    commit = source["commit"].lower()
    if len(commit) != 40 or any(char not in "0123456789abcdef" for char in commit):
        raise ContractError("source.commit precisa ser SHA Git completo de 40 hex")

    external = external_source_row(source["external_manifest_name"])
    for key in ("url", "branch", "commit"):
        if source[key] != external[key]:
            raise ContractError(
                f"divergência entre bootstrap-assets.v1.json e external_sources.manifest em {key}: "
                f"{source[key]!r} != {external[key]!r}"
            )

    seen_outputs: set[str] = set()
    validated_assets: list[dict[str, Any]] = []
    for index, asset in enumerate(assets):
        if not isinstance(asset, dict):
            raise ContractError(f"assets[{index}] precisa ser objeto")
        for key in ("abi", "source_path", "output_name"):
            if not isinstance(asset.get(key), str) or not asset[key].strip():
                raise ContractError(f"assets[{index}].{key} ausente")
        source_path = PurePosixPath(asset["source_path"])
        if source_path.is_absolute() or ".." in source_path.parts:
            raise ContractError(f"source_path inseguro: {asset['source_path']}")
        output_name = asset["output_name"]
        if Path(output_name).name != output_name or output_name in seen_outputs:
            raise ContractError(f"output_name inválido ou duplicado: {output_name}")
        seen_outputs.add(output_name)
        expected = asset.get("expected_sha256", TOKEN_VAZIO)
        if expected != TOKEN_VAZIO:
            if not isinstance(expected, str) or len(expected) != 64 or any(c not in "0123456789abcdef" for c in expected.lower()):
                raise ContractError(f"expected_sha256 inválido para {output_name}")
        validated_assets.append(asset)

    return source, validated_assets


def atomic_copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temp = destination.with_name(f".{destination.name}.tmp-{os.getpid()}")
    shutil.copyfile(source, temp)
    os.replace(temp, destination)


def observe_branch_head(source: dict[str, Any]) -> str:
    """Observa drift da branch sem transformar branch mutável em autoridade."""
    ref = f"refs/heads/{source['branch']}"
    listing = run(["git", "ls-remote", "--heads", source["url"], ref])
    if not listing:
        return TOKEN_VAZIO
    first = listing.splitlines()[0].split()
    if not first:
        return TOKEN_VAZIO
    value = first[0].lower()
    if len(value) != 40 or any(char not in "0123456789abcdef" for char in value):
        return TOKEN_VAZIO
    return value


def fetch_pinned_commit(checkout: Path, source: dict[str, Any]) -> tuple[str, str]:
    """Busca o objeto exato; falha apenas se o SHA pinado não puder ser provado."""
    run(["git", "init", "--quiet", str(checkout)])
    run(["git", "-C", str(checkout), "remote", "add", "origin", source["url"]])

    branch_head = observe_branch_head(source)
    if branch_head != TOKEN_VAZIO and branch_head != source["commit"].lower():
        print(
            "[bootstrap-materialize] OBSERVED branch-head-drift "
            f"branch={source['branch']} pinned={source['commit']} observed={branch_head}"
        )

    run(
        [
            "git",
            "-C",
            str(checkout),
            "fetch",
            "--quiet",
            "--depth=1",
            "--filter=blob:none",
            "origin",
            source["commit"],
        ]
    )
    fetched = run(["git", "-C", str(checkout), "rev-parse", "FETCH_HEAD^{commit}"]).lower()
    if fetched != source["commit"].lower():
        raise ContractError(f"FETCH_HEAD divergente: esperado {source['commit']}, obtido {fetched}")
    return fetched, branch_head


def materialize(manifest_path: Path) -> dict[str, Any]:
    manifest = load_json(manifest_path)
    source, assets = validate_manifest(manifest)

    output_dir = ROOT / manifest.get("output_directory", "app/build/generated/bootstrapAssets/bootstrap")
    receipt_path = ROOT / manifest.get("receipt", "app/build/reports/bootstrap/bootstrap-materialization.json")
    output_dir.mkdir(parents=True, exist_ok=True)

    branch_head_observed = TOKEN_VAZIO
    with tempfile.TemporaryDirectory(prefix="vectras-bootstrap-source-") as tmp:
        checkout = Path(tmp) / "source"
        _, branch_head_observed = fetch_pinned_commit(checkout, source)

        run(["git", "-C", str(checkout), "sparse-checkout", "init", "--no-cone"])
        sparse_paths = "".join(f"/{asset['source_path']}\n" for asset in assets)
        run(
            ["git", "-C", str(checkout), "sparse-checkout", "set", "--no-cone", "--stdin"],
            stdin=sparse_paths,
        )
        run(["git", "-C", str(checkout), "checkout", "--quiet", "--detach", source["commit"]])

        resolved_head = run(["git", "-C", str(checkout), "rev-parse", "HEAD^{commit}"]).lower()
        if resolved_head != source["commit"].lower():
            raise ContractError(f"checkout não permaneceu no pin: {resolved_head}")

        receipt_assets: list[dict[str, Any]] = []
        for asset in assets:
            source_file = checkout / asset["source_path"]
            if not source_file.is_file():
                raise ContractError(f"asset ausente no commit pinado: {asset['source_path']}")

            tar_info = inspect_tar(source_file)
            actual_sha = sha256_file(source_file)
            expected_sha = asset.get("expected_sha256", TOKEN_VAZIO)
            if expected_sha != TOKEN_VAZIO and actual_sha != expected_sha.lower():
                raise ContractError(
                    f"SHA-256 divergente para {asset['output_name']}: esperado {expected_sha}, obtido {actual_sha}"
                )

            destination = output_dir / asset["output_name"]
            atomic_copy(source_file, destination)
            if sha256_file(destination) != actual_sha:
                raise ContractError(f"cópia não preservou SHA-256: {destination}")

            detail = {
                "abi": asset["abi"],
                "source_path": asset["source_path"],
                "output_path": str(destination.relative_to(ROOT)),
                "size_bytes": destination.stat().st_size,
                "sha256": actual_sha,
                "expected_sha256": expected_sha,
                "sha256_enforced": expected_sha != TOKEN_VAZIO,
                **tar_info,
            }
            receipt_assets.append(detail)
            print(
                f"[bootstrap-materialize] OK {detail['abi']} size={detail['size_bytes']} "
                f"sha256={actual_sha} entries={detail['entries']}"
            )

    receipt = {
        "schema_version": "1.1.0",
        "record_type": "bootstrap_asset_materialization",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source": {
            "repository": source.get("repository"),
            "url": source["url"],
            "branch": source["branch"],
            "branch_head_observed": branch_head_observed,
            "branch_head_matches_pin": branch_head_observed == source["commit"].lower(),
            "branch_head_authoritative": False,
            "commit": source["commit"],
            "commit_verified": True,
            "transport": "git_exact_sha_fetch_partial_sparse_checkout",
        },
        "output_directory": str(output_dir.relative_to(ROOT)),
        "assets": receipt_assets,
        "all_assets_valid_tar": True,
        "all_paths_safe": True,
        "claim_allowed": False,
        "next_gate": (
            "promote discovered SHA-256 values into tools/ci/bootstrap-assets.v1.json, "
            "rerun Android build, then execute on device"
        ),
    }
    receipt_path.parent.mkdir(parents=True, exist_ok=True)
    receipt_path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"[bootstrap-materialize] receipt={receipt_path.relative_to(ROOT)}")
    return receipt


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--validate-manifest-only", action="store_true")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    manifest_path = args.manifest if args.manifest.is_absolute() else ROOT / args.manifest
    try:
        manifest = load_json(manifest_path)
        validate_manifest(manifest)
        if args.validate_manifest_only:
            print(f"[bootstrap-materialize] manifesto OK: {manifest_path.relative_to(ROOT)}")
            return 0
        materialize(manifest_path)
        return 0
    except ContractError as exc:
        print(f"[bootstrap-materialize] FALHA: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
