# RAFHV — authorial VM control nucleus v0.1

This is newly authored freestanding C11 **VM control logic**, not yet a functioning hypervisor or VMM. CPU ISA emulation, KVM, MMU, IRQ, device models, ELF boot and Android runtime wiring are NOT IMPLEMENTED.

Use make test audit in this directory. Only rafhv.c/h are freestanding. The test harness uses hosted stdio; the compiler-provided stdint.h does not add libc linkage.

The backend callback is trusted host code and must be supplied by an independently validated CPU backend; a mock HALT is not guest execution. Borrowed buffers must remain alive and cover the mapped length, with synchronization and zeroization owned by the caller. Reset clears only VM metadata. Single-threaded use only. The X permission bit is declarative, not CPU-enforced. Backend failure leaves PC uncommitted.

Local scope before GitHub CI: GCC + Clang each 23 assertions pass; nm -u on freestanding objects empty; Clang ARMv7 none-eabi compiles ELF32 EABI5. Android/guest NOT_RUN. Licensing: new files marked GPL-2.0-only for inclusion in this fork; original upstream author and obligations retained. No claim of legally certified clean-room.

Navigate to NOVOexport/VECTRA_HYPERVISOR_ATLAS_V1.md for gates, provenance and rollback.
