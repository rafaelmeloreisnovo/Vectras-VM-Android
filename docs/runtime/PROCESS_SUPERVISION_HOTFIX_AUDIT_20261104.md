# Vectras Runtime Process Supervision — scoped hotfix audit (2026-11-04)

## Source of truth

Producer: `rafaelmeloreisnovo/Vectras-VM-Android`. Physical device screenshot on Moto E7 Power (Android 10) proves only extraction `chown 0:0` failure and three missing runtime checks; it does **not** prove a running guest or a zombie on that device. Source-level review reveals additional lifecycle risks independent of the screenshot.

## Dependency chain (stacked, reversible, nonpromoting)

- PR #1180 (`fix/android-tar-no-same-owner-20261104`): `tar -o -x -f` avoids privileged ownership restoration during private APK asset extraction. `android-ci` and `APK Wizard` success scoped at HEAD 6e6918b4db57eaeada1fad901d913dc51b75521e; orchestrator matrix FAIL (upstream test debt + git history gate). No device E2E.
- PR #1181 (`fix/vectras-zombie-supervision-20261104`, based on #1180): unsuccessful KILL cannot mark STOP; prevent removal/eviction of live process; one budget slot per registered VM; symmetric release on confirmed exit; regression tests.
- This branch (`fix/vectras-process-launch-teardown-20261104`, based on #1181): bounded collector joins, daemon collector threads, teardown before unregister, unique registry IDs, interrupted-wait child teardown and tests.

## Source-level invariants

1. `STOP` means process termination observed, not inferred from signal dispatch, timeout, or stale state label. `DEGRADED` means still-live after kill timeout and remains registered.
2. Never forget a live VM because its logs finish, a watcher is interrupted, a stop fails, or a capacity eviction is attempted.
3. Register one process => reserve exactly one slot => release on confirmed exit. A budget-full condition fails closed.
4. A log collector cannot join indefinitely because a descendant inherited stdout/stderr. Bounded timeout fails closed with `stream_collector_drain_incomplete`.
5. Timeout and interruption classification are retained; interrupt attempts bounded child teardown and restores thread interrupt bit.
6. Registry IDs include a monotonic sequence; simultaneous launches cannot alias merely because system clock repeats.
7. No claim that killing the PRoot parent guarantees QEMU grandchildren and guest are stopped: `CHILD_TREE_TERMINATION=TOKEN_VAZIO` until device process-group evidence.

## Unresolved tests/CI from exact #1180 orchestrator

Run [41773817152](https://github.com/rafaelmeloreisnovo/Vectras-VM-Android/actions/runs/41773817152):
- Host/ABI preflight SUCCESS, multiple official Android native jobs FAILURE with `831 tests completed, 56/57 failed` in compared native variants. Failures include reflected methods not found, NPEs in setup tests, and FileUtils tests. Baseline status before this patch must be independently checked; do not label them preexisting without exact-base evidence.
- Quality Gates failure includes `fatal: Invalid symmetric difference expression 9468ace...6e6918...` because checkout did not fetch both refs sufficiently for `git diff base...head`. Separate minimal workflow patch required.
- These failures block `release_allowed`; individual green workflows do not constitute a passing whole pipeline.

## Additional gaps (typed TOKEN_VAZIO)

| Key | Gap | Evidence needed |
| --- | --- | --- |
| G-ABI | Exact APK digest/ARMv7 placement and extracted binaries on physical device | APK SHA, asset SHA, chmod/file stat, post-check |
| G-TREE | Grandchild PRoot/QEMU termination & true zombie state | PID/PPID/cgroup snapshot before/after stop, two watchdog samples |
| G-QMP | Real QMP powerdown response and guest liveness | QMP protocol trace + guest console/VNC |
| G-BUILD | Exact HEAD CI for #1181 and current branch | Gradle unit tests + artifact hashes |
| G-TESTS | 56/57 native unit failures scope and baseline delta | exact master vs patched paired test run |
| G-SPACE | Free storage and decompressed archive working set on Android | actual available bytes and tar expanded size |
| G-RUN | Permission and SELinux denial distinctions | runtime errno/avc audit, not generic storage request |
| G-CHILD | Pipe inherited by descendants after parent exit | device drainer timeout and child FD proof |
| G-RELEASE | Independent review and provider gate | reviewed exact SHA and release workflow receipt |

`SOURCE != ARTIFACT != EXECUTION != EVIDENCE != CLAIM`; `claim_allowed=false`, `release_allowed=false`. Append successors rather than deleting history.

R3 = <F_ok: isolated fixes and source readback, F_gap: CI/device/child-tree evidence and test triage, F_next: review & exact HEAD CI, physical Moto E7 repair, then QEMU guest stop and rollback proof>.
