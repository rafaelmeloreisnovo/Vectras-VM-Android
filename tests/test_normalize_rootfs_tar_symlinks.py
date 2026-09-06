from __future__ import annotations

import importlib.util
import io
import tarfile
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "bootstrap" / "normalize_rootfs_tar_symlinks.py"
spec = importlib.util.spec_from_file_location("normalize_rootfs_tar_symlinks", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def _regular(tf: tarfile.TarFile, name: str, data: bytes, mode: int = 0o755) -> None:
    info = tarfile.TarInfo(name)
    info.size = len(data)
    info.mode = mode
    info.mtime = 1
    tf.addfile(info, io.BytesIO(data))


def _symlink(tf: tarfile.TarFile, name: str, target: str) -> None:
    info = tarfile.TarInfo(name)
    info.type = tarfile.SYMTYPE
    info.linkname = target
    info.mode = 0o777
    info.mtime = 1
    tf.addfile(info)


def _build_alpine_tar(path: Path) -> None:
    with tarfile.open(path, "w", format=tarfile.PAX_FORMAT) as tf:
        _regular(tf, "bin/busybox", b"busybox")
        _symlink(tf, "bin/sh", "/bin/busybox")
        _symlink(tf, "usr/bin/env", "/bin/busybox")


def test_alpine_absolute_rootfs_symlinks_become_host_safe_relative_links(tmp_path: Path) -> None:
    tar_path = tmp_path / "armeabi-v7a.tar"
    _build_alpine_tar(tar_path)

    first = module.rewrite_tar(tar_path, "alpine19")

    assert first["changed"] is True
    assert first["rewritten_symlinks"] == 2
    assert first["before_sha256"] != first["after_sha256"]

    with tarfile.open(tar_path, "r:*") as tf:
        members = {member.name: member for member in tf.getmembers()}
        assert members["bin/sh"].linkname == "busybox"
        assert members["usr/bin/env"].linkname == "../../bin/busybox"
        assert not members["bin/sh"].linkname.startswith("/")
        assert not members["usr/bin/env"].linkname.startswith("/")
        assert tf.extractfile("bin/busybox").read() == b"busybox"

    second = module.rewrite_tar(tar_path, "alpine19")
    assert second["changed"] is False
    assert second["rewritten_symlinks"] == 0
    assert second["before_sha256"] == first["after_sha256"]
    assert second["after_sha256"] == first["after_sha256"]


def test_rejects_escaping_relative_symlink(tmp_path: Path) -> None:
    tar_path = tmp_path / "bad.tar"
    with tarfile.open(tar_path, "w", format=tarfile.PAX_FORMAT) as tf:
        _regular(tf, "bin/busybox", b"busybox")
        _symlink(tf, "bin/sh", "../../outside")
        _symlink(tf, "usr/bin/env", "/bin/busybox")

    try:
        module.rewrite_tar(tar_path, "alpine19")
    except ValueError as exc:
        assert "escaping symlink target rejected" in str(exc)
    else:
        raise AssertionError("escaping symlink was not rejected")


def run_standalone() -> None:
    with tempfile.TemporaryDirectory(prefix="vectras-rootfs-normalizer-test-") as tmp:
        root = Path(tmp)
        test_alpine_absolute_rootfs_symlinks_become_host_safe_relative_links(root / "case-ok")
    with tempfile.TemporaryDirectory(prefix="vectras-rootfs-normalizer-test-") as tmp:
        root = Path(tmp)
        test_rejects_escaping_relative_symlink(root / "case-reject")
    print("ROOTFS_NORMALIZER_TESTS: PASS cases=2")


if __name__ == "__main__":
    # Keep this file compatible with pytest while also making the CI gate valid
    # on a bare Python installation.
    with tempfile.TemporaryDirectory(prefix="vectras-rootfs-normalizer-test-") as tmp:
        case = Path(tmp) / "case-ok"
        case.mkdir(parents=True)
        test_alpine_absolute_rootfs_symlinks_become_host_safe_relative_links(case)
    with tempfile.TemporaryDirectory(prefix="vectras-rootfs-normalizer-test-") as tmp:
        case = Path(tmp) / "case-reject"
        case.mkdir(parents=True)
        test_rejects_escaping_relative_symlink(case)
    print("ROOTFS_NORMALIZER_TESTS: PASS cases=2")
