# Bootstrap / PRoot hotfix delivery

## Scope

This hotfix hardens the Android bootstrap path that prepares `usr/bin/proot` and the Alpine rootfs before VM launch.
It targets the runtime path used by the app (`Context.getFilesDir()`), not a fixed external Termux package path.

## Delivered behavior

- PRoot command construction and runtime preflight resolve the binary from the app files directory at runtime: `${filesDir}/usr/bin/proot`.
- Bootstrap detection is split into two gates:
  - **core PRoot gate**: `usr/bin/proot` executable plus writable `usr/tmp`;
  - **full bootstrap gate**: core PRoot plus rootfs shell and BusyBox.
- Asset extraction now stages the previous live tree before untar:
  - `bootstrap` protects `${filesDir}/usr`;
  - distro extraction protects the extraction target, normally `${filesDir}/distro`.
- On copy, tar, process, or post-check failure, the hotfix deletes the partial tree and restores the staged tree.
- On success, the rollback backup is removed after post-checks pass.

## Failsafe / failover / rollback contract

| Phase | Failure mode | Mitigation |
| --- | --- | --- |
| ABI asset resolution | no matching `bootstrap/<abi>.tar` | abort before filesystem mutation |
| rollback staging | unsafe path or failed rename | abort before extraction |
| asset copy | missing/corrupt asset stream | restore staged tree |
| tar launch | non-zero exit / timeout / invalid process result | restore staged tree |
| post-check | missing proot/tmp/distro executable | restore staged tree |
| success | post-check OK | commit by deleting rollback backup |

## Notes

- The hot path stays allocation-light and outside native/assembly kernels; no `.S` files were changed.
- Attractor #22 / VOID paradox and the AArch64 register contract are not modified by this patch.
- The Gradle unit-test command could not run in this container because Android SDK location is not configured.
