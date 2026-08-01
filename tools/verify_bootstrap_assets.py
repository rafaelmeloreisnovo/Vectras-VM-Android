#!/usr/bin/env python3
"""Valida presença, proveniência operacional e integridade dos bootstraps.

Os TARs podem estar versionados no source tree ou materializados no diretório de
assets gerados a partir de commit Git pinado. Quando as duas cópias existem, elas
precisam ser byte-identical por SHA-256.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import sys
import tarfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
SOURCE_BOOTSTRAP_DIR = ROOT / "app" / "src" / "main" / "assets" / "bootstrap"
GENERATED_BOOTSTRAP_DIR = ROOT / "app" / "build" / "generated" / "bootstrapAssets" / "bootstrap"
BOOTSTRAP_DIRS = (SOURCE_BOOTSTRAP_DIR, GENERATED_BOOTSTRAP_DIR)
REQUIRED_BOOTSTRAPS = [
    "arm64-v8a.tar",
    "armeabi-v7a.tar",
    "x86.tar",
    "x86_64.tar",
]
LOADER_APK_NAME = "loader.apk"
TERMUX_MARKERS = [
    ROOT / "app" / "src" / "main" / "java" / "com" / "termux",
    ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
]
STRICT_ENV_VAR = "VERIFY_BOOTSTRAP_STRICT_GENERATED_ASSETS"
CI_ENV_VARS = ("CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILDKITE")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_tar_member(name: str) -> bool:
    pure = PurePosixPath(name)
    return not pure.is_absolute() and ".." not in pure.parts


def validate_tar(path: Path) -> tuple[int, str]:
    with tarfile.open(path, "r:*") as archive:
        members = archive.getmembers()
        if not members:
            raise RuntimeError("arquivo tar vazio")
        unsafe = [member.name for member in members if not safe_tar_member(member.name)]
        if unsafe:
            raise RuntimeError(f"caminhos inseguros: {unsafe[:5]}")
        return len(members), members[0].name


def resolve_bootstrap(name: str) -> tuple[Path | None, list[str]]:
    candidates = [directory / name for directory in BOOTSTRAP_DIRS]
    existing = [candidate for candidate in candidates if candidate.is_file()]
    if not existing:
        rendered = ", ".join(str(candidate.relative_to(ROOT)) for candidate in candidates)
        return None, [f"ausente: {name}; esperado em {rendered}"]

    if len(existing) > 1:
        hashes = {sha256_file(candidate) for candidate in existing}
        if len(hashes) != 1:
            rendered = ", ".join(
                f"{candidate.relative_to(ROOT)}={sha256_file(candidate)}" for candidate in existing
            )
            return None, [f"cópias conflitantes de {name}: {rendered}"]

    generated = GENERATED_BOOTSTRAP_DIR / name
    selected = generated if generated in existing else existing[0]
    return selected, []


def is_termux_enabled() -> bool:
    termux_dir = TERMUX_MARKERS[0]
    if termux_dir.exists():
        return True

    manifest_path = TERMUX_MARKERS[1]
    if manifest_path.exists():
        manifest_text = manifest_path.read_text(encoding="utf-8", errors="ignore")
        return "com.termux" in manifest_text
    return False


def env_flag_enabled(name: str) -> bool:
    value = os.environ.get(name, "").strip().lower()
    return value in {"1", "true", "yes", "on"}


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Valida o contrato oficial de bootstrap (TAR assets + loader.apk)."
    )
    parser.add_argument(
        "--strict-generated-assets",
        action="store_true",
        help=(
            "Exige loader.apk no caminho gerado/versionado mesmo fora de CI. "
            f"Também pode ser ativado por {STRICT_ENV_VAR}=1."
        ),
    )
    return parser.parse_args(argv)


def should_require_generated_loader(strict_generated_assets: bool) -> tuple[bool, str]:
    if strict_generated_assets:
        return True, "modo estrito habilitado"

    for var_name in CI_ENV_VARS:
        if os.environ.get(var_name):
            return True, f"ambiente de CI detectado ({var_name})"

    if GENERATED_BOOTSTRAP_DIR.exists():
        return True, (
            f"diretório de gerados já existe ({GENERATED_BOOTSTRAP_DIR.relative_to(ROOT)}), "
            "indicando que syncShellLoaderBootstrap já deveria ter copiado o loader"
        )

    return False, "checkout limpo sem execução prévia de syncShellLoaderBootstrap"


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or [])
    strict_generated_assets = args.strict_generated_assets or env_flag_enabled(STRICT_ENV_VAR)
    print("[verify_bootstrap_assets] Validando bootstraps do repositório/gerados...")

    failures: list[str] = []

    for name in REQUIRED_BOOTSTRAPS:
        path, resolution_failures = resolve_bootstrap(name)
        failures.extend(resolution_failures)
        if path is None:
            continue

        size = path.stat().st_size
        if size <= 0:
            failures.append(f"vazio: {path.relative_to(ROOT)}")
            continue

        try:
            member_count, first_member = validate_tar(path)
        except (tarfile.TarError, RuntimeError, OSError) as exc:
            failures.append(f"inválido: {path.relative_to(ROOT)} ({exc})")
            continue

        print(
            f"  - OK {path.relative_to(ROOT)} size={size} entries={member_count} "
            f"sha256={sha256_file(path)} first_entry={first_member}"
        )

    if is_termux_enabled():
        loader_candidates = [
            SOURCE_BOOTSTRAP_DIR / LOADER_APK_NAME,
            GENERATED_BOOTSTRAP_DIR / LOADER_APK_NAME,
        ]
        loader_path = next((candidate for candidate in loader_candidates if candidate.exists()), None)
        if loader_path is None:
            require_loader, reason = should_require_generated_loader(strict_generated_assets)
            message = (
                "CONTRATO VIOLADO (Termux habilitado): loader.apk obrigatório no caminho TAR; esperado em "
                f"{(SOURCE_BOOTSTRAP_DIR / LOADER_APK_NAME).relative_to(ROOT)} "
                f"ou {(GENERATED_BOOTSTRAP_DIR / LOADER_APK_NAME).relative_to(ROOT)}; "
                "a cópia para gerados ocorre na task app:syncShellLoaderBootstrap"
            )
            if require_loader:
                failures.append(f"{message} (falha fatal: {reason})")
            else:
                print(f"  - AVISO {message} (não fatal neste contexto: {reason})")
        else:
            loader_size = loader_path.stat().st_size
            if loader_size <= 0:
                failures.append(
                    f"CONTRATO VIOLADO (Termux habilitado): loader.apk vazio em {loader_path.relative_to(ROOT)}"
                )
            else:
                print(f"  - OK {loader_path.relative_to(ROOT)} size={loader_size} (Termux habilitado)")

    if failures:
        print("\n[verify_bootstrap_assets] FALHAS:")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print("\n[verify_bootstrap_assets] OK: TARs e loader atendem ao contrato observável.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
