# CODEX Execution Yard — VECTRA / AndroidX / QEMU

Correction: the project axis is **VECTRA**, not Lectra.

## Purpose

This file gives Codex a precise work yard for the `Vectras-VM-Android` repository.

The documentation is a map. The operational truth is the code path.

```text
Android / AndroidX layer
  -> VM/runtime orchestration
  -> QEMU integration boundary
  -> native/JNI optional path
  -> managed fallback path
  -> logs, errors and evidence
```

## Reading rule

Codex must begin