# RAFCODE-Φ optional Linux syscall ABI V2

SPDX-License-Identifier: MIT. Original standalone code, **not** copied from
PRoot. This directory lives outside the portable core and is never implicitly
linked by the core. Vectras existing GPL-2.0 and PRoot upstream GPL-2.0-or-later
remain separate; combining/linking modules may invoke copyleft obligations.

## Exact boundary

- `raf_linux_abi.h`: self-contained ABI declarations, zero includes or dependency on
  the portable core; 64-bit Linux x86_64, 64-bit AArch64 and 32-bit ARMv7 ARM
  state only; explicit machine selection, reject unsupported ABI and Thumb.
- `raf_linux_sys.c`: Linux `write(2)` through CPU trap (`syscall` on x86_64,
  `svc #0` on ARM), returning kernel result directly, no `errno` wrapper.
- `raf_linux_demo.c`: single call into pure core normalization and CRC32C then
  one kernel write of `/alpha/gamma\n`, returns a clear code on failure.
- `start_*.S`: custom ELF `_start`, invokes demo then kernel `exit` directly.
- `ci/check_kernel_boundary.sh`: native execution plus link audit; optional
  ARMv7/AArch64 cross-link (compile/link ONLY, not phone execution).

Runtime: **no libc, malloc, getenv, JNI, PRoot, QEMU, shell, printf, dynamic
ELF interpreter, shared object dependencies, kernel modules or network**.
Build requires a C/ASM compiler and ELF linker; running requires compatible
Linux kernel/CPU ABI. `void*` passes caller-owned pointers; not a source of
permissions. The Linux kernel itself is a real external execution dependency.

## Run from root of freestanding module

```sh
CC=gcc sh ci/check_kernel_boundary.sh
CC=clang RAF_CROSS_CHECK=1 sh ci/check_kernel_boundary.sh
```

The freestanding toolchain flags forbid compiler builtins and stack protector;
link enforces `-nostdlib -nodefaultlibs -nostartfiles -static -no-pie`, checks
undefined symbols, `DT_NEEDED`, `PT_INTERP` and executes x86_64 Linux ELF.
Kernel syscall numbers and register conventions are OS ABI, not a portable
standard; Android process context, SELinux/seccomp policies and distribution
licenses apply. This does **not** replace PRoot, ptrace, QEMU, guest boot,
filesystem isolation, permissions, mount namespace, or implement a VM.
`claim_allowed=false` for device/runtime equivalence until physical evidence.

Do not force inline ASM for an unsupported target. ASM is used **only** for
architecture-critical syscall/trap boundaries. The rest stays portable C.
Comments and formatting never instruct the compiler; flags, source tokens,
linker script and target ABI do.
