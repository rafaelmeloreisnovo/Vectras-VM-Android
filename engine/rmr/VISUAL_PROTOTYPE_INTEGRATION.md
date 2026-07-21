# RMR visual prototypes, RAFSTORE slots and ZIPRAF capsules

## Implemented flow

```text
image/detector
  -> RmR_VisionDescriptor
  -> labeled octahedral/diagonal view
  -> sealed RmR_VisualPrototype
  -> fixed-slot RmR_VisualStore
  -> score + winner margin
  -> canonical RVC1 capsule
  -> RmR_Zipraf_Execute custody
  -> ZIP STORE payload for ZiprafDirectRuntime
```

## Responsibilities

- `rmr_stability`: Otsu, foreground, angular profile, deterministic difference and CRC32C.
- `rmr_visual_prototype`: label, class id, up to 16 views, comparison, fixed-slot store, unknown/ambiguous gates and canonical serialization.
- `rmr_visual_zipraf`: serializes the sealed prototype and passes the exact capsule bytes to the existing ZIPRAF custody kernel.
- `ZiprafDirectRuntime.kt`: validates the actual ZIP local header, central directory and STORE extent when the capsule is packaged as a ZIP entry.

No component silently trains or changes model weights.

## Geometry

Views `0..5` are front, rear, left, right, top and bottom: the six octahedral axes. Views `6..15` are controlled diagonals/auxiliary positions. Duplicate views are rejected unless explicit replacement is requested.

## RAFSTORE semantics

`RmR_VisualStore` is a fixed-capacity store supplied by the caller. It performs no heap allocation. Upsert replaces the same class id or occupies the next free slot. Classification requires:

- best score above `minimum_score_q16`;
- winner margin above `minimum_margin_q16`.

A weak result is `NO_MATCH`; a close tie is `AMBIGUOUS`. Both preserve `TOKEN_VAZIO` instead of inventing a label.

## Capsule format

The canonical little-endian `RVC1` payload contains magic, version, total length, class id, label, view mask, all view descriptors, prototype CRC32C and a final capsule CRC32C. A two-view `cachorro` fixture serializes to 232 bytes.

The low-level core does not construct a ZIP archive. The capsule becomes an entry such as `cachorro-0001.rvp` inside a method-0 ZIP archive. This preserves separation:

```text
RVC1 = visual memory payload
ZIP STORE = container/direct mapping
ZIPRAF = custody metadata and deterministic routing
BLAKE3/Bitraf = external integrity primitives
```

## Verification performed

Standalone compilation passed with:

```text
-O2 -std=c11 -Wall -Wextra -Werror -pedantic
```

The fixture verified three classes, two dog views, a brightness variation, correct winner/margin, duplicate-view rejection, serialization round-trip and one-byte tamper rejection.

The current descriptor is intentionally small. Real dog/car/tree accuracy remains `TOKEN_VAZIO` until real labeled images are evaluated outside the enrolled prototype set. A multimodal embedding can later be added as a higher descriptor channel without changing the capsule/store contract.
