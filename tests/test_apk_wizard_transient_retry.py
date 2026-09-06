from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "ci" / "run_apk_wizard_with_transient_retry.py"
spec = importlib.util.spec_from_file_location("run_apk_wizard_with_transient_retry", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def test_transient_reset_retries_then_succeeds() -> None:
    outcomes = iter(
        [
            (1, "urllib.error.URLError: <urlopen error [Errno 104] Connection reset by peer>"),
            (1, "ConnectionResetError: [Errno 104] Connection reset by peer"),
            (0, "BUILD SUCCESSFUL"),
        ]
    )
    calls = []
    sleeps = []

    def runner(command):
        calls.append(tuple(command))
        return next(outcomes)

    rc = module.run_with_retry(
        ["./tools/ci/build_apk_wizard.sh"],
        attempts=4,
        base_backoff_seconds=1,
        runner=runner,
        sleeper=sleeps.append,
    )
    assert rc == 0
    assert len(calls) == 3
    assert sleeps == [1, 2]
    assert len(set(calls)) == 1


def test_http_503_is_retryable() -> None:
    outcomes = iter([(1, "HTTP Error 503: Service Unavailable"), (0, "ok")])
    calls = []

    def runner(command):
        calls.append(tuple(command))
        return next(outcomes)

    rc = module.run_with_retry(
        ["same", "pinned", "command"],
        attempts=3,
        base_backoff_seconds=0,
        runner=runner,
        sleeper=lambda _delay: None,
    )
    assert rc == 0
    assert len(calls) == 2
    assert calls[0] == calls[1]


def test_http_404_fails_without_retry() -> None:
    calls = []

    def runner(command):
        calls.append(tuple(command))
        return 1, "HTTP Error 404: Not Found"

    rc = module.run_with_retry(
        ["same", "pinned", "command"],
        attempts=4,
        runner=runner,
        sleeper=lambda _delay: None,
    )
    assert rc == 1
    assert len(calls) == 1


def test_integrity_failure_fails_without_retry() -> None:
    calls = []

    def runner(command):
        calls.append(tuple(command))
        return 1, "SHA-256 mismatch alpine19/armeabi-v7a: expected=a actual=b"

    rc = module.run_with_retry(
        ["same", "pinned", "command"],
        attempts=4,
        runner=runner,
        sleeper=lambda _delay: None,
    )
    assert rc == 1
    assert len(calls) == 1


def test_transient_exhaustion_is_bounded() -> None:
    calls = []
    sleeps = []

    def runner(command):
        calls.append(tuple(command))
        return 7, "TimeoutError: transport timed out"

    rc = module.run_with_retry(
        ["same", "pinned", "command"],
        attempts=3,
        base_backoff_seconds=2,
        runner=runner,
        sleeper=sleeps.append,
    )
    assert rc == 7
    assert len(calls) == 3
    assert sleeps == [2, 4]


def run_standalone() -> None:
    test_transient_reset_retries_then_succeeds()
    test_http_503_is_retryable()
    test_http_404_fails_without_retry()
    test_integrity_failure_fails_without_retry()
    test_transient_exhaustion_is_bounded()
    print("APK_WIZARD_TRANSIENT_RETRY_TESTS: PASS cases=5")


if __name__ == "__main__":
    run_standalone()
