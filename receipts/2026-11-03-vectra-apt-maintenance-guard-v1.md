# μRECEIPT — Vectra APT maintenance verification (2026-11-03)

source_repo=rafaelmeloreisnovo/Vectras-VM-Android
parent_master_sha=9468ace1ca7a851377ba71565f2fd6f05c47c01c
producer_repo=rafaelmeloreisnovo/termux-app-rafacodephi
producer_reference_master=2d1d0c5b7b20e9240611515d7c51e90304a6b2e8
producer_pending_security_fix=PR#518@8a04d852189b043ac508530ee89bffdaf1d6a71d
authority=consumer maintenance state machine and static contract gate
kind=FEATURE_SAFETY_HOTFIX; urgency=P1; risk=missing_Apt_status
delta=add receipt-driven APT_VERIFY before PRoot/Ninja/QEMU version tests
command=rafproot-fs --run apt-get --version (fixed command, no shell)
state=SOURCE_IMPLEMENTED_BRANCH / EXACT_CI_PENDING
unknown.apt_installed=TOKEN_VAZIO_NOT_RUN
unknown.apt_package_signature=TOKEN_VAZIO_MISSING_EVIDENCE
unknown.apt_repository_provenance=TOKEN_VAZIO_MISSING_SOURCE
unknown.proot_ninja_qemu=TOKEN_VAZIO_NOT_RUN
unknown.android_art_dex_device=TOKEN_VAZIO_NOT_RUN
gap_registry=local bounded receipt only, no global closure claim
falsifier=APT exit!=0 or missing/mismatched pending receipt must block advance
claim_allowed=false; release_allowed=false
rollback=close draft PR or reviewed revert; master source preserved
F_ok=new maintenance stage and static verifier scoped
F_gap=provider+consumer exact CI, artifacts and physical E2E
F_next=static contract exact HEAD, independent review, real APT/PRoot/Ninja/QEMU receipts
