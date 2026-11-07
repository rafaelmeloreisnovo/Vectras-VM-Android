# RAFHV — Vectra authorial freestanding VM control and experimental A32 subset

Self-contained C11 control core plus a **very limited ARM A32 interpreter**. All pure modules use no libc, heap, syscalls, Android, JNI or QEMU; stdint.h is a compiler freestanding header. The Makefile and tests are hosted, and the external CPU callback still is not a guest-security sandbox.

Implemented A32 subset: AL-condition MOV/ADD/SUB immediates, LDR/STR word (pre-index +offset), B without link, SVC surfaced as IO event. Fetch requires X+R permission. Unsupported conditions, opcodes, misaligned PC, invalid reads/stores fault closed. A32 status/PC snapshot is **not** a full architectural PC model; ARM privilege levels, Thumb/T32, exceptions, MMU, GIC, interrupts, devices, ELF loader, guest OS and Android runtime are not supported. No real hypervisor hardware/EL2 or KVM is accessed.

From this directory: make CC=gcc test audit; make clean; make CC=clang test audit. Local tests: 23 control + 22 A32 assertions on each compiler; combined relocatable core nm -u empty. ARMv7 cross-compile ELF32 EABI5 observed. A32 fixture executes encoded instructions, **not a bootable guest**. Execution receipts and Android hardware gates remain TOKEN_VAZIO.

Caller must keep mapped buffers alive/valid and own zeroization and synchronization. Initial test fixture maps RWX because it writes instructions into the sample buffer, not a hardened production deployment. GPL-2.0-only scoped to new sources in this GPL fork; upstream license and attribution unchanged. Consult NOVOexport/VECTRA_A32_S2.md and the parent V1 Atlas.
