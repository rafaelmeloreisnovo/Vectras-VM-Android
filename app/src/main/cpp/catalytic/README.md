# VCAT — Catalytic Freestanding Runtime v1

VCAT is the pure computation layer for the Vectras bootstrap/runtime path.

## Contract

The VCAT core is intentionally narrower than PRoot itself:

- no libc headers or libc calls;
- no heap/allocator;
- no JNI/Java/Android APIs;
- no threads, atomics, fork/clone/exec or process APIs;
- no syscalls;
- no dynamic loader or runtime symbol-name lookup;
- no recursion and no loop statement inside `vcat_core.inc`;
- tail-call optimization disabled on the public catalytic entry points;
- `-Wshadow` promoted to an error inside the specialist module;
- fixed upper width of 16 deterministic lanes.

This is a **pure in-memory catalyst**. It classifies and routes already-provided
bytes. It does not pretend that filesystem installation or process execution can
occur without a kernel boundary.

## 16-lane deterministic mapping

For input index `i in [0,15]`:

```
a = (mix(seed) | 1) mod 16
b = (mix(seed xor 0x9e3779b9) >> 4) mod 16
lane(i) = (a*i + b) mod 16
```

`a` is always odd, therefore `gcd(a,16)=1`. Multiplication by `a` is invertible
modulo 16, so `lane(i)` is a permutation: all 16 inputs receive distinct lanes.
There is no scheduler race and no timing-dependent ordering inside the core.

**Important:** a lane is a deterministic work descriptor, not an OS process.
Turning a lane into a real process requires a kernel-backed executor.

## Specialist classifiers

The in-memory header classifier recognizes:

1. ELF (ELF32/ELF64, little-endian, executable/shared flags);
2. DEX;
3. ZIP;
4. TAR (`ustar`);
5. OAT;
6. VDEX;
7. QCOW2;
8. Android sparse images.

No path strings or symbol names are used to decide routing. Runtime identity is
numeric (`id`, `phase`, `kind`, `flags`, digest word).

## PRoot / distro boundary

A usable PRoot-backed distro still requires operations that VCAT deliberately
does not fake:

- executable PRoot payload for the target ABI;
- rootfs/bootstrap payload;
- file creation/extraction/chmod/symlink/rename;
- process creation and kernel-mediated execution;
- for Vectras VM launch, a QEMU executable/runtime path.

Those are kernel/host-bound operations and must remain separate evidence gates.
`TOKEN_VAZIO` is the correct state until their payloads and receipts exist.

## Build integration

`vcat_core.inc` is amalgamated into `lowlevel_abi.c`. This deliberately reuses
`abi_core_freestanding` instead of creating a second hosted target. The existing
`vectra_freestanding_link_probe` references `vcat_plan16`, so the 16-lane core is
part of the no-libc final-link witness rather than merely dead source.

## Evidence boundary

Passing the freestanding link probe supports only the claim:

> the VCAT computation core can be linked without libc/runtime dependencies.

It does **not** certify Android installation, PRoot execution, distro boot, QEMU
execution or physical VM launch.
