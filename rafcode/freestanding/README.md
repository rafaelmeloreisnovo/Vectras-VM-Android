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


## V3 restricted source-comment optimizer contract — scoped, fail-closed

An ordinary C comment is inert. A **host build adapter** may intentionally
interpret one exact allowlisted marker in a specific source file:

`/* RAF_OPT:PROFILE=stable;UNROLL=4 */`

The independent MIT C11 freestanding scanner is `include/raf_opt_hint.h`
plus `src/raf_opt_hint.c`. It skips line comments, quoted strings
and character literals. It recognizes exactly one fully bounded block marker,
rejecting duplicates, unknown keys, injection suffixes and malformed syntax.
Missing annotation is `RAF_TOKEN_VAZIO`, **not an implicit build
default**. It maps exclusively to `-O2`, `-Os`,
`-O3` with `-DRAF_OPT_UNROLL=1|4`.

The tiny hosted `ci/raf_opt_cli.c` reads source bytes via
stdio **at build time only**; the standalone C11 parser and algorithm import
no libc, operating-system calls, filesystem, QEMU, PRoot or Android API.
`ci/check_opt_hint.sh` reads exactly the annotated
`src/raf_opt_kernel.c`, emits allowlisted flags into the
compiler, and verifies both unroll variants at O2/Os/O3 against the same
hosted correctness tests. GCC and Clang share the original two CI jobs.
It also rejects deliberately malicious annotations, cross-compiles ARMv7
EABI5 and AArch64, and audits undefined ELF symbols and DT_NEEDED.
The link-only probe must **never** be executed.

For compiler-guided unrolling, GCC/Clang use their own guarded pragma.
Actual emitted machine code, code size, runtime performance, ISA-dependent
semantics and optimization benefit are separate measurements, **not** assumed.
Warnings do not authorize arbitrary compiler flags or code execution.
This lane neither rewrites production source nor changes the root GPL
and upstream PRoot licenses. Do not infer a verified compiler or end-to-end
device proof from its finite host tests. Reference prior-art reading only:
HINTPILOT arXiv:2604.15041; loop semantics arXiv:2602.19868; COMPILOT
arXiv:2511.00592; none is copied into this MIT implementation.


## V4: fail-closed C translation-phase guard + static codegen receipts

Comment metadata is still an inert ordinary C comment except when explicitly
interpreted by the host build adapter. `raf_opt_parse` now
accepts the one exact annotation only **before any executable source token or
preprocessor directive**, ignoring leading ordinary comments, and rejects C
line-splices (`\` + LF/CRLF), trigraphs, and embedded NUL
**anywhere** in its bounded source. This is intentionally stricter than
accepting all legal C11 inputs: phase-1/2 translation and conditional
preprocessing are *not implemented* in the scanner. Rejected files must be
reviewed rather than guessed, silently normalized or passed to a shell.
`RAF_TOKEN_VAZIO` remains distinct from malformed input.

`ci/check_opt_codegen.sh` compiles the sample XOR kernel
with GCC and Clang (the existing two jobs) at O2, Os and O3, unroll 1 and
4. It captures host assembly and disassembly digests and exact
`.text.raf_opt_xor_u32` size in bytes in the job log.
In the Clang lane it also compiles and identifies ARMv7-A EABI and AArch64
ELF objects for the same 3x2 configurations and records static assembly
SHA-256/byte counts. No new CI matrix or runtime package was added.

**Do not interpret differing assembly hashes or section sizes as a
performance improvement or as proof the emitted loop was actually unrolled.**
The emitted instructions, optimization effect and equivalence on a
particular physical ARM device need disassembly review, comparable
on-device benchmarks and independent falsifiers. All device and APK
results remain `TOKEN_VAZIO` until they have their own
receipts. Upstream GPL/PRoot ownership is unchanged.
