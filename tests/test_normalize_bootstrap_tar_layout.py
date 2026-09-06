from __future__ import annotations

import importlib.util
import io
import tarfile
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "bootstrap" / "normalize_bootstrap_tar_layout.py"
spec = importlib.util.spec_from_file_location("normalize_bootstrap_tar_layout", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def _root(tf: tarfile.TarFile) -> None:
    info = tarfile.TarInfo("./")
    info.type = tarfile.DIRTYPE
    info.mode = 0o755
    info.uid = 0
    info.gid = 0
    info.mtime = 0
    tf.addfile(info)


def _regular(tf: tarfile.TarFile, name: str, data: bytes, mode: int = 0o755) -> None:
    info = tarfile.TarInfo(name)
    info.size = len(data)
    info.mode = mode
    info.uid = 0
    info.gid = 0
    info.mtime = 1
    tf.addfile(info, io.BytesIO(data))


def _build_bootstrap(path: Path, *, include_tmp: bool) -> None:
    with tarfile.open(path, "w", format=tarfile.PAX_FORMAT) as tf:
        _root(tf)
        _regular(tf, "usr/bin/proot", b"proot-binary", 0o755)
        if include_tmp:
            directory = tarfile.TarInfo("usr/tmp/")
            directory.type = tarfile.DIRTYPE
            directory.mode = 0o771
            directory.uid = 0
            directory.gid = 0
            directory.mtime = 0
            tf.addfile(directory)


def test_injects_usr_tmp_and_preserves_proot_bytes(tmp_path: Path) -> None:
    tar_path = tmp_path / "armeabi-v7a.tar"
    _build_bootstrap(tar_path, include_tmp=False)

    first = module.derive(tar_path)
    assert first["changed"] is True
    assert first["injected_usr_tmp"] is True
    assert first["before_sha256"] != first["after_sha256"]

    with tarfile.open(tar_path, "r:*") as tf:
        members = {member.name.rstrip("/"): member for member in tf.getmembers()}
        assert "." in members
        assert "usr/bin/proot" in members
        assert "usr/tmp" in members
        assert members["usr/tmp"].isdir()
        assert members["usr/tmp"].mode & 0o777 == 0o771
        assert tf.extractfile("usr/bin/proot").read() == b"proot-binary"

    second = module.derive(tar_path)
    assert second["changed"] is False
    assert second["injected_usr_tmp"] is False
    assert second["after_sha256"] == first["after_sha256"]


def test_existing_usr_tmp_is_idempotent(tmp_path: Path) -> None:
    tar_path = tmp_path / "arm64-v8a.tar"
    _build_bootstrap(tar_path, include_tmp=True)
    before = module.sha256_file(tar_path)
    result = module.derive(tar_path)
    assert result["changed"] is False
    assert result["after_sha256"] == before


def _run_standalone() -> None:
    with tempfile.TemporaryDirectory(prefix="vectras-bootstrap-layout-test-") as tmp:
        case = Path(tmp) / "inject"
        case.mkdir(parents=True)
        test_injects_usr_tmp_and_preserves_proot_bytes(case)
    with tempfile.TemporaryDirectory(prefix="vectras-bootstrap-layout-test-") as tmp:
        case = Path(tmp) / "existing"
        case.mkdir(parents=True)
        test_existing_usr_tmp_is_idempotent(case)
    print("BOOTSTRAP_LAYOUT_TESTS: PASS cases=2")


if __name__ == "__main__":
    _run_standalone()
