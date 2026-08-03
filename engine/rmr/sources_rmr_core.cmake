# Canonical RMR source manifest.
#
# Group contract used by all build systems:
#   - core
#   - optional-policy
#   - android-only
#   - host-only
#   - asm-per-arch

set(RMR_SOURCE_GROUP_CORE
  engine/rmr/src/rmr_vectra_os.c
  engine/rmr/src/bitomega.c
  engine/rmr/src/rmr_cycles.c
  engine/rmr/src/rmr_external_engine.c
  engine/rmr/src/rmr_hw_detect.c
  engine/rmr/src/rmr_asset_guard.c
  engine/rmr/src/rmr_isorf.c
  engine/rmr/src/rmr_apk_module.c
  engine/rmr/src/rmr_qemu_bridge.c
  engine/rmr/src/rmr_math_fabric.c
  engine/rmr/src/rmr_torus_flow.c
  engine/rmr/src/rmr_stability.c
  engine/rmr/src/rmr_visual_prototype.c
  engine/rmr/src/rafaelia_formulas_core.c
  engine/rmr/src/rmr_corelib.c
  engine/rmr/src/rmr_ll_ops.c
  engine/rmr/src/rmr_ll_tuning.c
  engine/rmr/src/rmr_casm_bridge.c
  engine/rmr/src/rmr_unified_kernel.c
  engine/rmr/src/rmr_unified_jni_bridge.c
  engine/rmr/src/rmr_host_compat.c
  engine/rmr/src/rmr_zipraf_core.c
  engine/rmr/src/rmr_visual_zipraf.c
  engine/rmr/src/topological_guard.c
  engine/rmr/src/rmr_lowlevel_portable.c
  engine/rmr/src/rmr_lowlevel_mix.c
  engine/rmr/src/rmr_lowlevel_reduce.c
  engine/rmr/src/rmr_vector_field.c
  engine/rmr/src/raf_b7_orchestrator.c
)

set(RMR_SOURCE_GROUP_OPTIONAL_POLICY
  engine/rmr/src/rmr_policy_kernel.c
)

# Android JNI/library-only units. Intentionally excluded from hosted targets.
set(RMR_SOURCE_GROUP_ANDROID_ONLY
  engine/rmr/src/rmr_tcg_cache.c
  engine/rmr/src/rmr_virtio_blk.c
  engine/rmr/src/rmr_attractor.c
  engine/rmr/src/rmr_vhw_model.c
  engine/rmr/src/rmr_ethica_loss.c
)

# Hosted/root-only units. Intentionally excluded from Android shared library.
set(RMR_SOURCE_GROUP_HOST_ONLY
  engine/rmr/src/rmr_baremetal_compat.c
  engine/rmr/src/rmr_bench.c
  engine/rmr/src/rmr_bench_suite.c
)

set(RMR_SOURCE_GROUP_ASM_X86_64
  engine/rmr/interop/rmr_lowlevel_x86_64.S
  engine/rmr/interop/rmr_casm_x86_64.S
  engine/rmr/interop/rmr_vectra_os_x86_64.S
)

set(RMR_SOURCE_GROUP_ASM_ARM64
  engine/rmr/interop/rmr_casm_arm64.S
  engine/rmr/interop/rmr_vectra_os_arm64.S
)

# NEON/SIMD source must be ABI-scoped to ARM to avoid accidental cross-ABI
# compile when manifests are consumed by Android multi-ABI builds.
set(RMR_SOURCE_GROUP_ASM_ARM64_NEON
  engine/rmr/src/rmr_neon_simd.c
)

set(RMR_SOURCE_GROUP_ASM_RISCV64
  engine/rmr/interop/rmr_casm_riscv64.S
  engine/rmr/interop/rmr_vectra_os_riscv64.S
)

set(RMR_SOURCE_GROUP_ASM_ARM32
  engine/rmr/interop/rmr_stability_armv7.S
  engine/rmr/interop/rmr_vectra_os_armv7.S
)

# The pre-existing stability ASM was previously listed but not consumed by the
# APK CMake path. Add only that narrow backend to the core list on armeabi-v7a;
# rmr_stability.c weak-dispatches to it and remains the bit-identical fallback.
if(ANDROID)
  set(_rmr_android_abi "${ANDROID_ABI}")
  if(_rmr_android_abi STREQUAL "" AND DEFINED CMAKE_ANDROID_ARCH_ABI)
    set(_rmr_android_abi "${CMAKE_ANDROID_ARCH_ABI}")
  endif()
  if(_rmr_android_abi STREQUAL "armeabi-v7a")
    list(APPEND RMR_SOURCE_GROUP_CORE engine/rmr/interop/rmr_stability_armv7.S)
  endif()
endif()

function(rmr_manifest_apply_base OUT_VAR)
  set(_rmr_manifest_out)
  foreach(_rmr_manifest_src IN LISTS ARGN)
    if(DEFINED RMR_SOURCE_BASE AND NOT RMR_SOURCE_BASE STREQUAL "")
      list(APPEND _rmr_manifest_out "${RMR_SOURCE_BASE}${_rmr_manifest_src}")
    else()
      list(APPEND _rmr_manifest_out "${_rmr_manifest_src}")
    endif()
  endforeach()
  set(${OUT_VAR} "${_rmr_manifest_out}" PARENT_SCOPE)
endfunction()
