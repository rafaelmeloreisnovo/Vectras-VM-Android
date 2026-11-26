#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-only
"""Static regression gate for merged PR #1186 review P1/P2.
Does not replace Android instrumentation, attribution verification or device tests.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "app/src/main/java/com/vectras/qemu/MainSettingsManager.java"
RECORD = ROOT / "docs/provenance/PR1186_BENCH_COMPONENT_LINEAGE_PENDING.md"
NOTICE = ROOT / "THIRD_PARTY_NOTICES.md"

def strip_java_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return re.sub(r"//[^\n]*", "", source)

def assert_safe_worker(source: str) -> None:
    src = strip_java_comments(source)
    marker = 'new Thread(() -> {'
    end_marker = '}, "VectrasFreestandingMicrobench").start();'
    if marker not in src or end_marker not in src:
        raise AssertionError("missing bounded benchmark thread scope")
    worker = src.split(marker, 1)[1].split(end_marker, 1)[0]
    if re.search(r"\brequireActivity\s*\(", worker):
        raise AssertionError("Fragment Activity access on worker thread (detach race)")
    post_marker = "new Handler(Looper.getMainLooper()).post(() -> {"
    if post_marker not in worker:
        raise AssertionError("benchmark completion is not dispatched to the main looper")
    on_main = worker.split(post_marker, 1)[1]
    for required in (
        "if (!isAdded()) return;",
        "preference.setSummary(result);",
        "preference.setEnabled(true);",
        "rafBenchRunning = false;",
    ):
        if required not in on_main:
            raise AssertionError(f"missing main-thread attachment/state check: {required}")
    if on_main.index("if (!isAdded()) return;") > on_main.index("preference.setSummary(result);"):
        raise AssertionError("attachment guard must precede UI changes")

def main() -> None:
    safe = JAVA.read_text(encoding="utf-8")
    assert "import android.os.Looper;" in safe
    assert_safe_worker(safe)
    old_racy_pattern = (
        'new Thread(() -> { if (isAdded()) {'
        ' requireActivity().runOnUiThread(() -> {'
        ' if (!isAdded()) return; preference.setSummary(result);'
        ' }); } }, "VectrasFreestandingMicrobench").start();'
    )
    try:
        assert_safe_worker(old_racy_pattern)
    except AssertionError:
        pass
    else:
        raise AssertionError("falsifier failure: old worker Activity race was accepted")
    provenance = RECORD.read_text(encoding="utf-8")
    notice = NOTICE.read_text(encoding="utf-8")
    for component in (
        "raf_bench_probe.c",
        "raf_bench_probe.h",
        "raf_bench_jni.c",
        "FreestandingMicrobench.java",
        "RuntimeAbiSnapshot.java",
    ):
        if component not in provenance:
            raise AssertionError(f"no file/span record for {component}")
    for gate in ("PRO_OFFICIAL_PENDING", "TOKEN_VAZIO", "claim_allowed=false",
                 "about_required=true", "distribution_allowed=false"):
        if gate not in provenance:
            raise AssertionError(f"missing provenance gate: {gate}")
    if "PR1186_BENCH_COMPONENT_LINEAGE_PENDING.md" not in notice:
        raise AssertionError("NOTICE does not link the pending provenance register")
    print("PASS_SCOPED_STATIC: P2 dispatch+detach regression; adversarial old-pattern rejection; P1 pending-file records + NOTICE link")
    print("NOT_PROVEN: actual Fragment lifecycle race on device, provenance rights, About UI, APK or VM boot")

if __name__ == "__main__":
    main()
