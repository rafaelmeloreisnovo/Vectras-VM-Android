cmake_minimum_required(VERSION 3.22.1)
project(vectra_omega_freestanding_deploy C ASM)

# Deployment boundary: this lane consumes the existing verified static ABI core,
# but the final ELF is linked directly by ld.lld with no CRT, libc, JNI or
# Android shared-library dependency. Android is only the build/host boundary.
if(NOT ANDROID)
    message(FATAL_ERROR "Omega freestanding deployment lane requires the Android NDK toolchain")
endif()

if(NOT ANDROID_ABI STREQUAL "armeabi-v7a")
    message(FATAL_ERROR "Omega deployment v1 is intentionally ARMv7-only; ANDROID_ABI=${ANDROID_ABI}")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

get_filename_component(VECTRA_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(VECTRA_APP_CPP "${VECTRA_REPO_ROOT}/app/src/main/cpp")

# Keep the source/archive contract from the canonical Android CMake project,
# while disabling unrelated optional modules for this bounded deployment lane.
set(RMR_ENABLE_POLICY_MODULE OFF CACHE BOOL "" FORCE)
set(RMR_ENABLE_BITRAF_MODULE OFF CACHE BOOL "" FORCE)
set(RMR_ASM_CORE_EXPERIMENTAL OFF CACHE BOOL "" FORCE)
set(VECTRA_BUILD_FREESTANDING_LINK_PROBE OFF CACHE BOOL "" FORCE)
set(VECTRA_REQUIRE_FREESTANDING_CORE ON CACHE BOOL "" FORCE)
add_subdirectory("${VECTRA_APP_CPP}" "${CMAKE_BINARY_DIR}/vectra-core")

if(NOT TARGET abi_core_freestanding)
    message(FATAL_ERROR "Canonical abi_core_freestanding target is unavailable")
endif()

set(OMEGA_OUTPUT_DIR "${CMAKE_BINARY_DIR}/out")
set(OMEGA_ELF "${OMEGA_OUTPUT_DIR}/omega-core-armv7.elf")
set(OMEGA_MAP "${OMEGA_OUTPUT_DIR}/omega-core-armv7.map")

add_library(omega_deploy_objects OBJECT
    omega_deploy_main.c
    omega_deploy_start_armv7.S)

target_compile_definitions(omega_deploy_objects PRIVATE
    VECTRA_LL_NO_STDLIB_TYPES=1
    OMEGA_FREESTANDING_DEPLOYMENT=1)

target_include_directories(omega_deploy_objects PRIVATE
    "${VECTRA_APP_CPP}"
    "${VECTRA_APP_CPP}/core"
    "${VECTRA_REPO_ROOT}/engine/rmr/include")

target_compile_options(omega_deploy_objects PRIVATE
    -O2
    -ffreestanding
    -fno-builtin
    -fno-stack-protector
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -fdata-sections
    -ffunction-sections
    -fvisibility=hidden
    -march=armv7-a
    -mfpu=neon
    -mfloat-abi=softfp)

# Direct linker invocation is deliberate. It avoids driver-injected CRT objects,
# PIE interpreter paths and default libraries. The resulting artifact must be a
# static ELF32/ARM executable with no PT_INTERP, DT_NEEDED or undefined symbol.
add_custom_command(
    OUTPUT "${OMEGA_ELF}" "${OMEGA_MAP}"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${OMEGA_OUTPUT_DIR}"
    COMMAND "${CMAKE_LINKER}"
        -static
        --gc-sections
        --build-id=none
        --no-undefined
        -e _start
        "-Map=${OMEGA_MAP}"
        -z max-page-size=16384
        -z noexecstack
        -o "${OMEGA_ELF}"
        $<TARGET_OBJECTS:omega_deploy_objects>
        $<TARGET_FILE:abi_core_freestanding>
    DEPENDS omega_deploy_objects abi_core_freestanding
    COMMAND_EXPAND_LISTS
    VERBATIM
    COMMENT "Linking Omega ARMv7 freestanding deployment ELF (no CRT/libc/JNI)")

add_custom_target(omega_freestanding_deploy_armv7
    DEPENDS "${OMEGA_ELF}" "${OMEGA_MAP}")

if(TARGET lowlevel_abi_contract_check)
    add_dependencies(omega_freestanding_deploy_armv7 lowlevel_abi_contract_check)
endif()

set_property(TARGET omega_freestanding_deploy_armv7 PROPERTY
    OMEGA_DEPLOYMENT_STATE "BUILDABLE_NOT_DEVICE_VERIFIED")
