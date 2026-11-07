# RAFCODE-Φ · núcleo freestanding autoral V1

**Status:** deterministic host-scope prototype, **NOT** a replacement for
PRoot/QEMU, **NOT** a VM implementation, and **NOT** an Android install receipt.

## Scope and provenance

New files in this directory: SPDX `MIT`; copyright notice and terms in
[`LICENSE-MIT`](LICENSE-MIT). This is an independently usable source module,
with new code authored for this repository. It is not relicensed Vectras code
and contains no copied PRoot implementation or code. Repository root Vectras
GPL-2.0 license and existing authorship attribution remain untouched.

The official PRoot license is distinct: upstream `GPL-2.0-or-later` at
https://github.com/proot-me/proot/blob/master/COPYING. See
[`PROOT_BOUNDARY.md`](PROOT_BOUNDARY.md) for the upstream record and distributor
obligations. A `MIT` module MAY be combined compatibly with `GPL-2.0` modules;
the resulting combined/derived program may need to be distributed under GPL.
Separate SPDX identifiers alone do not exempt a combined work from copyleft.

## Dependency-free runtime modules

```text
include/raf_core.h    exact-width target contract, errors, void* ports
src/raf_memory.c      bounded external-storage arena + CRC32C
src/raf_path.c        lexical path normalization and longest-prefix mapping
src/raf_dispatch.c    single nonallocating command gateway
src/raf_port.c        OPTIONAL callback port, no syscall symbols
src/raf_link_probe.c  link-only proof entry, NOT a runnable executable
ci/check.sh           HOSTED build/test infrastructure, not core
```

No `#include <stdlib.h>`, Linux headers, JNI, dynamic allocation, printf,
filesystem, threading, network, `ptrace` or syscalls **inside** the core.
Core objects intentionally require **zero external symbols once linked together**;
no libc runtime is linked in the dedicated final-link proof. Compiler, target
ABI, linker, CPU and (for real I/O) OS remain real prerequisites.

The `void *` type is an opaque pointer for caller-supplied buffers and port
contexts. It does not remove the need for memory bounds or platform authority.
The `RAF_TOKEN_VAZIO` enum is deliberately distinct from `RAF_OK == 0`.

## Build and scoped tests

Run from this directory:

```sh
CC=cc sh ci/check.sh
CC=clang RAF_CROSS_CHECK=1 sh ci/check.sh
```

Strict kernel-core flags: `-std=c11 -O2 -ffreestanding -fno-builtin
-fno-stack-protector -fno-unwind-tables -fno-asynchronous-unwind-tables
-fdata-sections -ffunction-sections -Wall -Wextra -Werror -pedantic`.
The final link adds `-nostdlib -nostartfiles -nodefaultlibs`, and audits `nm`
and ELF `DT_NEEDED`. `CI` uses ordinary host tools **only to compile/test**;
the delivered core does not import those libraries. Cross smoke checks
ARMv7 EABI5, AArch64, and opt-in AArch64 CRC32C instructions; it does not
run those objects on phones.

`RAF_ENABLE_AARCH64_CRC32C=1` is allowed **only** on AArch64 with CRC feature
and must be built with e.g. `-march=armv8-a+crc`. The portable bitwise
CRC32C path is default. Force-enabling on other CPUs deliberately fails
compilation. Do not use CRC32C as a cryptographic digest/receipt: use a
separately verified SHA-256 at the toolchain/service boundary.

Comments, whitespace and formatting document the contract; they do **not**
execute as compiler or linker commands. Only tested source, flags, link
scripts and compilation environment affect binary semantics.

## Limits and adversarial falsifiers

- Path rewriting is **lexical only**, does not inspect inode/symlink, resolve
  mounts, enforce containment, intercept syscalls or emulate `chroot`.
- A missing mapping returns `RAF_NO_MAPPING`; a missing optional OS port
  returns `RAF_TOKEN_VAZIO` (not success). No auto fallback to host root.
- Guest paths must be canonical for translation; rule-prefix comparisons use
  complete path segments, and longest valid prefix wins.
- Bounded arena uses caller memory with alignment; no heap/allocator calls.
- `raf_crc32c` uses an 8-iteration branchless bit step in portable C, with
  optional target-gated AArch64 `crc32cb` inline assembly.
- Host tests compile a **separate hosted executable** (test code only). This
  does not imply that the core needs libc.
- `raf_link_probe` loops forever; it exists for linker inspection only;
  **do not execute it**. It deliberately makes no exit syscall.

**Cannot claim:** replacement of PRoot, ABI-perfect syscall transport,
paravirtualization, VM execution, Android app integration, boot, or performance
improvement versus baseline. Those require separately implemented engines and
real-device receipts. No hidden dependency is invented or assumed.

## Next interface contract

Provider adapters must live **outside** this independent core, provide
explicit `raf_port` callbacks, and record their own origin/license and error
handling. Linux `ptrace` privilege/capability, SELinux and Android limits
cannot be removed by compiling freestanding or by inline assembly. A full
PRoot replacement would need a separately engineered and validated tracing,
register, signal, syscall and filesystem layer. Keep that gap typed.


## Independent bitops lane (V2 additive, opt-in ARM instructions)

`include/raf_bitops.h` + `src/raf_bitops.c` provide
32-bit binary bit-reversal, 32-bit byte swapping, modulo-32 rotate-left and
bounded big-endian 4-byte loads/stores. Inputs are explicit widths/lengths,
without libc, heap, JNI, OS, syscalls, filesystem, PRoot or QEMU. The `void *`
OS callback boundary is **unchanged** and this module does not call it.

Default is the fully portable masked C implementation. Opting in to
`-DRAF_ENABLE_ARM_BITOPS=1` uses auditable GNU/Clang
inline `rbit` and `rev` assembly on ARMv7-A
or AArch64 only (32-bit AArch64 register operands use `%w`).
An incompatible target intentionally fails compilation. Raw instruction
opcodes are **not** embedded: these depend on target ISA/ABI/endianness,
must be assembled by the toolchain, and must not bypass architecture gates.

The independent `ci/check_bitops.sh` tests each `-O2`,
`-Os`, `-O3` profile, audits `nm -u`
and a link-only final ELF (`-nostdlib -nostartfiles -nodefaultlibs`,
zero `DT_NEEDED`), cross-compiles ARMv7/AArch64 opt-in
ASM and verifies the expected emitted instructions plus a wrong-architecture
negative test. The link probe **MUST NOT** be executed.

CI remains two compiler jobs; no matrix fanout by profile and no new OS
dependencies in the core. Profile labels express compiler optimization intent
only, **not** a proven speedup. Execution on Moto E7 Power, runtime memory
behavior, real syscall adapter and QEMU guest boot remain `TOKEN_VAZIO`.
No existing JNI/Android/VM behavior is replaced. Root GPL-2.0, MIT scoped
file notices and the official PRoot license boundary are preserved.
