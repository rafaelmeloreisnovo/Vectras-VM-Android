# Vectra Ω — S2: authorial A32 instruction execution (bounded subset)

Supersedes only the V1 atlas assertion that no CPU backend has been implemented: a **limited software interpreter** now exists in hypervisor/autoral/rafhv_a32.{c,h}; **a complete CPU engine and hypervisor still do not**.

Supported: A32 AL-condition instructions MOV/ADD/SUB immediate, pre-index positive immediate LDR/STR word, unconditional B and SVC. Embedded encoded A32 test sequence writes R0=5; ADD=12; stores/loads word at guest addr64 and yields SVC IO#1. Failure fixtures: memory X denied, misaligned PC, readonly STR, self-loop bounded by step budget, conditional instruction unsupported. No QEMU, JVM, syscalls, heap or libc in interpreter.

Evidence: on local GCC/Clang, each passes 23 control plus 22 A32 test assertions; linked relocatable core has no undefined symbols; Clang ARMv7 none-eabi object generates ELF32 EABI5. This is NOT an Android executable, not a Linux boot, not a privilege-separated security boundary and not cycle-accurate ARMv7 conformance.

Missing: CPSR/flags/condition codes, all Thumb, ARM exceptions, supervisor modes, MMU/stage2, full ISA, devices, disks, dynamic translation, resource isolation, kernel entry, Android ABI binding, tested actual Moto. SVC is an observable simulation event only, not implemented syscall or exception path. Backend is trusted and buffers borrowed, so untrusted guests must not be exposed.

Next falsifiable gate: add differential vectors for instruction semantics and alignment, privilege and fault-model tests, then independently implement MMU/exception/IRQ semantics; isolate host/guest memory and obtain host/physical receipts. No merge or legacy removal until feature parity and device evidence.

Branch: feat/vectra-hypervisor-autoral-core-v1-20261107. Earlier V1 state retained for audit. SOURCE != ARTIFACT != EXECUTION != EVIDENCE != CLAIM; claim_allowed=false. Rollback: close draft PR/revert only new files.
