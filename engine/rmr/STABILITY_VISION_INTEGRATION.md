# RMR stability and visual-difference integration

## What was consolidated

The implementation joins mechanisms that were previously split across CTI/GBS3, experimental image tools and an orphan ARMv7 assembly unit:

```text
bottom: bytes/events -> four-word state step + CRC32C
middle: pixels/angles -> Otsu + 8-bin direction profile + difference score/hash
top: TRIAD trace -> DeltaP = P(stable|peaks) - P(stable|nonpeaks)
```

These are three different measurements. They must not be collapsed into one unnamed “AI score”.

## Canonical files

- `include/rmr_stability.h`
- `src/rmr_stability.c`
- `interop/rmr_stability_armv7.S`
- `demo_cli/src/rmr_stability_selftest.c`
- `tools/test_rmr_stability.sh`

`src/rmr_stability.c` is the portable source of truth. On armeabi-v7a it weak-dispatches to the existing assembly symbol when that object is linked; every other ABI uses the same portable operations.

## Build integration

`engine/rmr/sources_rmr_core.cmake` now includes the portable source in the canonical core list. On Android `armeabi-v7a`, it also appends the narrow `rmr_stability_armv7.S` backend. Because `app/src/main/cpp/CMakeLists.txt` consumes the canonical source manifest, the module enters `libvectra_core_accel.so` without a duplicate private source list.

The generated Make manifest also contains `rmr_stability.c` and the ARM32 group, preserving alignment between build systems.

## Corrected defects

1. **Missing trace versus measured zero:** status flags distinguish no samples, no peak samples and no non-peak samples.
2. **Small-sample angular chi-square:** the earlier `e=n/8` integer path returned zero for `n<8`. The new rational formula uses `sum((8*obs-n)^2)/(8*n)`.
3. **Difference custody:** the descriptor CRC consumes complete canonical 32-bit fields; it does not truncate every value to one byte.
4. **Architecture divergence:** portable C and ARMv7 assembly share a golden vector. The selftest compares state and result bit-for-bit.

## Claim boundary

- `DeltaP` is a measured association in a supplied trace.
- The visual descriptor measures grayscale separation and angular distribution.
- CRC32C and the difference hash establish deterministic identity, not semantic equality.
- Dog/car/tree recognition remains a higher layer requiring a detector or multimodal embedding plus labeled prototype tests.

## Verification

```sh
sh tools/test_rmr_stability.sh
```

Expected:

```text
rmr_stability_selftest: OK
```

For ARMv7, run the same vectors in the Android/Termux target and confirm the assembly-dispatched state equals the portable state before marking that backend validated.
