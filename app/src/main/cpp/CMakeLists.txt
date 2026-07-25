cmake_minimum_required(VERSION 3.22.1)
project(vectra_core_accel C ASM)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

option(RMR_ENABLE_POLICY_MODULE "Enable RMR policy module" ON)
option(RMR_ENABLE_BITRAF_MODULE "Enable BITRAF module" ON)
option(RMR_ASM_CORE_EXPERIMENTAL "Enable experimental ASM core hot paths" ON)
option(RMR_ASM_CORE_X86_64_VALIDATED "Enable ASM hot paths on x86_64 (validated ABI)" ON)
option(RMR_ASM_CORE_ARM64_VALIDATED "Enable ASM hot paths on arm64-v8a (validated ABI)" ON)
option(RMR_ASM_CORE_RISCV64_VALIDATED "Enable RV64I-compatible CASM path on riscv64 (validated ABI)" ON)
option(VECTRA_REQUIRE_FREESTANDING_CORE "Fail configuration when freestanding ABI core is not active" ON)

set(VECTRA_REPO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../)
set(VECTRA_RMR_INCLUDE_DIR ${VECTRA_REPO_ROOT}/engine/rmr/include)
set(RMR_SOURCE_BASE "${VECTRA_REPO_ROOT}/")
set(VECTRA_PLATFORM_ANDROID_COMPAT_SOURCE ${VECTRA_REPO_ROOT}/engine/platform/android/rmr_android_compat.c)

include(${VECTRA_REPO_ROOT}/engine/rmr/sources_rmr_core.cmake)

rmr_manifest_apply_base(VECTRA_ENGINE_CORE_SOURCES ${RMR_SOURCE_GROUP_CORE} ${RMR_SOURCE_GROUP_ANDROID_ONLY})
list(REMOVE_ITEM VECTRA_ENGINE_CORE_SOURCES ${VECTRA_REPO_ROOT}/engine/rmr/src/rmr_baremetal_compat.c)

rmr_manifest_apply_base(VECTRA_ENGINE_POLICY_SOURCES ${RMR_SOURCE_GROUP_OPTIONAL_POLICY})
rmr_manifest_apply_base(VECTRA_ENGINE_ASM_X86_64_SOURCES ${RMR_SOURCE_GROUP_ASM_X86_64})
rmr_manifest_apply_base(VECTRA_ENGINE_ASM_ARM64_SOURCES ${RMR_SOURCE_GROUP_ASM_ARM64})
rmr_manifest_apply_base(VECTRA_ENGINE_ASM_RISCV64_SOURCES ${RMR_SOURCE_GROUP_ASM_RISCV64})
rmr_manifest_apply_base(VECTRA_ENGINE_ARM64_NEON_SOURCES ${RMR_SOURCE_GROUP_ASM_ARM64_NEON})

set(VECTRA_LOWLEVEL_SOURCES)
set(VECTRA_CASM_SOURCES)
set(VECTRA_NEON_SOURCES)

set(VECTRA_ANDROID_ABI "${ANDROID_ABI}")
if(ANDROID AND (NOT DEFINED VECTRA_ANDROID_ABI OR VECTRA_ANDROID_ABI STREQUAL ""))
    set(VECTRA_ANDROID_ABI "${CMAKE_ANDROID_ARCH_ABI}")
    message(STATUS "[RAFCoder] ANDROID_ABI not provided, using compatibility fallback CMAKE_ANDROID_ARCH_ABI=${VECTRA_ANDROID_ABI}")
endif()

set(RAFCODER_PRIMITIVES_C_SOURCE ${CMAKE_CURRENT_LIST_DIR}/core/arch/primitives.c)
set(RAFCODER_PRIMITIVES_ARM64_ASM_SOURCE ${CMAKE_CURRENT_LIST_DIR}/core/arch/primitives.S)
set(RAFCODER_PRIMITIVES_BACKEND_SOURCES)
set(RAFCODER_PRIMITIVES_BACKEND_NAME "portable-c")

if(RMR_ASM_CORE_EXPERIMENTAL AND ANDROID AND VECTRA_ANDROID_ABI STREQUAL "x86_64" AND RMR_ASM_CORE_X86_64_VALIDATED)
    list(APPEND VECTRA_LOWLEVEL_SOURCES ${VECTRA_ENGINE_ASM_X86_64_SOURCES})
    list(APPEND VECTRA_CASM_SOURCES ${VECTRA_ENGINE_ASM_X86_64_SOURCES})
endif()

if(RMR_ASM_CORE_EXPERIMENTAL AND NOT ANDROID AND (CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64") AND RMR_ASM_CORE_X86_64_VALIDATED)
    list(APPEND VECTRA_LOWLEVEL_SOURCES ${VECTRA_ENGINE_ASM_X86_64_SOURCES})
    list(APPEND VECTRA_CASM_SOURCES ${VECTRA_ENGINE_ASM_X86_64_SOURCES})
endif()

if(RMR_ASM_CORE_EXPERIMENTAL AND ANDROID AND VECTRA_ANDROID_ABI STREQUAL "arm64-v8a" AND RMR_ASM_CORE_ARM64_VALIDATED)
    list(APPEND VECTRA_CASM_SOURCES ${VECTRA_ENGINE_ASM_ARM64_SOURCES})
endif()

if(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "arm64-v8a")
    list(APPEND VECTRA_NEON_SOURCES ${VECTRA_ENGINE_ARM64_NEON_SOURCES})
endif()

# Roadmap explícito: ABI riscv64 permanece INATIVA por política Android
# (APP_ABI_POLICY/SUPPORTED_ABIS). A rotina CASM riscv64 já é RV64I-compatible
# (sem extensão M) e depende apenas de habilitação dessa política ABI.
# if(RMR_ASM_CORE_EXPERIMENTAL AND ANDROID AND VECTRA_ANDROID_ABI STREQUAL "riscv64" AND RMR_ASM_CORE_RISCV64_VALIDATED)
#     list(APPEND VECTRA_CASM_SOURCES ${VECTRA_ENGINE_ASM_RISCV64_SOURCES})
# endif()

if(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "armeabi-v7a")
    list(APPEND RAFCODER_PRIMITIVES_BACKEND_SOURCES ${RAFCODER_PRIMITIVES_C_SOURCE})
    set(RAFCODER_PRIMITIVES_BACKEND_NAME "armv7-c-fallback")
elseif(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "arm64-v8a")
    set(RAFCODER_USE_ARM64_PRIMITIVES_ASM OFF)
    if(EXISTS ${RAFCODER_PRIMITIVES_ARM64_ASM_SOURCE})
        if(DEFINED CMAKE_ANDROID_NDK_VERSION AND CMAKE_ANDROID_NDK_VERSION VERSION_GREATER_EQUAL "26")
            set(RAFCODER_USE_ARM64_PRIMITIVES_ASM ON)
        endif()
    endif()
    if(RAFCODER_USE_ARM64_PRIMITIVES_ASM)
        list(APPEND RAFCODER_PRIMITIVES_BACKEND_SOURCES ${RAFCODER_PRIMITIVES_ARM64_ASM_SOURCE})
        set(RAFCODER_PRIMITIVES_BACKEND_NAME "arm64-asm")
    else()
        list(APPEND RAFCODER_PRIMITIVES_BACKEND_SOURCES ${RAFCODER_PRIMITIVES_C_SOURCE})
        set(RAFCODER_PRIMITIVES_BACKEND_NAME "arm64-c-fallback")
    endif()
endif()

if(ANDROID AND (VECTRA_ANDROID_ABI STREQUAL "armeabi-v7a" OR VECTRA_ANDROID_ABI STREQUAL "arm64-v8a"))
    message(STATUS "[RAFCoder] ABI=${VECTRA_ANDROID_ABI} selected primitives backend: ${RAFCODER_PRIMITIVES_BACKEND_NAME}")
endif()

if(DEFINED Python3_EXECUTABLE AND NOT Python3_EXECUTABLE STREQUAL "")
    execute_process(
        COMMAND ${Python3_EXECUTABLE} --version
        RESULT_VARIABLE VECTRA_PYTHON3_OK
        OUTPUT_QUIET
        ERROR_QUIET
    )
    if(NOT VECTRA_PYTHON3_OK EQUAL 0)
        message(WARNING "Ignoring unusable Python3_EXECUTABLE from environment/cache: ${Python3_EXECUTABLE}")
        unset(Python3_EXECUTABLE CACHE)
        unset(Python3_EXECUTABLE)
    endif()
endif()

if(NOT DEFINED Python3_EXECUTABLE OR Python3_EXECUTABLE STREQUAL "")
    find_program(VECTRA_PYTHON3_FALLBACK NAMES python3 python REQUIRED)
    set(Python3_EXECUTABLE "${VECTRA_PYTHON3_FALLBACK}" CACHE FILEPATH "Python interpreter used by lowlevel ABI checks" FORCE)
endif()

find_package(Python3 COMPONENTS Interpreter REQUIRED)
add_custom_target(lowlevel_abi_contract_check
    COMMAND ${Python3_EXECUTABLE} ${VECTRA_REPO_ROOT}/tools/ci/validate_lowlevel_abi_contract.py
    WORKING_DIRECTORY ${VECTRA_REPO_ROOT}
    COMMENT "Validating lowlevel ABI contract + no implicit libc/stdlib policy")

add_library(abi_core_freestanding STATIC
    lowlevel_abi.c
    core/sector.c
    core/arch/primitives.c
    vectra_lowlevel_backend_fallback.c
    vectra_lowlevel_backend_arm64.c
    vectra_lowlevel_backend_armv7.c
    vectra_lowlevel_backend_x86_64.c
    vectra_lowlevel_backend_x86.c
    vectra_lowlevel_backend_riscv64.c
    ${RAFCODER_PRIMITIVES_BACKEND_SOURCES}
    ${VECTRA_LOWLEVEL_SOURCES}
    ${VECTRA_CASM_SOURCES}
    ${VECTRA_NEON_SOURCES})

add_library(vectra_core_accel SHARED
    vectra_core_accel.c
    lowlevel_bridge.c
    hardware_profile_bridge.c
    ${VECTRA_PLATFORM_ANDROID_COMPAT_SOURCE}
    ${VECTRA_ENGINE_CORE_SOURCES})

add_dependencies(vectra_core_accel lowlevel_abi_contract_check)

set(TERMUX_BOOTSTRAP_SUPPORTED_ANDROID_ABIS "arm64-v8a;armeabi-v7a;x86;x86_64" CACHE STRING "ABIs that should build termux-bootstrap")
set(TERMUX_BOOTSTRAP_REQUIRE_EMBEDDED_PAYLOAD OFF CACHE BOOL "Fail configure when termux-bootstrap payload symbols are not wired")
set(TERMUX_BOOTSTRAP_PAYLOAD_SOURCE "" CACHE FILEPATH "Optional C/ASM source file that defines TERMUX_BOOTSTRAP_PAYLOAD_DATA and TERMUX_BOOTSTRAP_PAYLOAD_SIZE")
set(TERMUX_BOOTSTRAP_MODE "assets-loader" CACHE STRING "Bootstrap mode: assets-loader or embedded-zip")
if(NOT TERMUX_BOOTSTRAP_MODE STREQUAL "assets-loader" AND NOT TERMUX_BOOTSTRAP_MODE STREQUAL "embedded-zip")
    message(FATAL_ERROR "Invalid TERMUX_BOOTSTRAP_MODE='${TERMUX_BOOTSTRAP_MODE}'; expected 'assets-loader' or 'embedded-zip'.")
endif()

set(TERMUX_BOOTSTRAP_BUILD_ENABLED OFF)

if(ANDROID)
    list(FIND TERMUX_BOOTSTRAP_SUPPORTED_ANDROID_ABIS "${VECTRA_ANDROID_ABI}" TERMUX_BOOTSTRAP_ABI_INDEX)
    if(NOT TERMUX_BOOTSTRAP_ABI_INDEX EQUAL -1)
        set(TERMUX_BOOTSTRAP_BUILD_ENABLED ON)
    else()
        message(STATUS "termux-bootstrap disabled for unsupported ABI: ${VECTRA_ANDROID_ABI}")
    endif()
endif()

if(NOT ANDROID OR VECTRA_ANDROID_ABI IN_LIST TERMUX_BOOTSTRAP_SUPPORTED_ANDROID_ABIS)
    add_library(termux-bootstrap SHARED
        termux_bootstrap_bridge.c)

    set(TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_CANDIDATES
        "${TERMUX_BOOTSTRAP_PAYLOAD_SOURCE}"
        "${CMAKE_CURRENT_SOURCE_DIR}/generated/termux_bootstrap_payload.c"
        "${CMAKE_CURRENT_SOURCE_DIR}/generated/termux_bootstrap_payload.S")

    set(TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_FOUND "")
    foreach(_candidate IN LISTS TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_CANDIDATES)
        if(_candidate AND EXISTS "${_candidate}")
            set(TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_FOUND "${_candidate}")
            break()
        endif()
    endforeach()

    if(TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_FOUND)
        target_sources(termux-bootstrap PRIVATE "${TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_FOUND}")
        target_compile_definitions(termux-bootstrap PRIVATE
            TERMUX_BOOTSTRAP_PAYLOAD_DATA=TERMUX_BOOTSTRAP_PAYLOAD_DATA
            TERMUX_BOOTSTRAP_PAYLOAD_SIZE=TERMUX_BOOTSTRAP_PAYLOAD_SIZE)
        message(STATUS "termux-bootstrap payload source: ${TERMUX_BOOTSTRAP_PAYLOAD_SOURCE_FOUND}")
    elseif(TERMUX_BOOTSTRAP_MODE STREQUAL "embedded-zip")
        if(NOT TERMUX_BOOTSTRAP_REQUIRE_EMBEDDED_PAYLOAD)
            message(WARNING "TERMUX_BOOTSTRAP_MODE=embedded-zip without payload requirement; forcing strict mode")
        endif()
        message(FATAL_ERROR
            "termux-bootstrap payload missing: configure TERMUX_BOOTSTRAP_PAYLOAD_SOURCE or generate "
            "app/src/main/cpp/generated/termux_bootstrap_payload.c(.S). This prevents shipping libtermux-bootstrap.so "
            "without nativeGetZip() payload.")
    elseif(TERMUX_BOOTSTRAP_REQUIRE_EMBEDDED_PAYLOAD)
        message(FATAL_ERROR
            "TERMUX_BOOTSTRAP_REQUIRE_EMBEDDED_PAYLOAD=ON requires TERMUX_BOOTSTRAP_PAYLOAD_SOURCE or generated payload source.")
    else()
        message(STATUS "termux-bootstrap payload not wired; mode=${TERMUX_BOOTSTRAP_MODE} (nativeGetZip fallback remains disabled).")
    endif()
else()
    message(STATUS "Skipping termux-bootstrap for unsupported ABI: ${VECTRA_ANDROID_ABI}")
endif()

if(VECTRA_CASM_SOURCES)
    target_compile_definitions(vectra_core_accel PRIVATE VECTRA_HAS_CASM_MARKER=1)
else()
    target_compile_definitions(vectra_core_accel PRIVATE VECTRA_HAS_CASM_MARKER=0)
endif()

if(RMR_ENABLE_POLICY_MODULE)
    add_library(rmr_policy_static STATIC
        ${VECTRA_ENGINE_POLICY_SOURCES})
endif()

if(RMR_ENABLE_BITRAF_MODULE)
    add_library(bitraf_static STATIC
        ../../../../engine/rmr/src/bitraf.c)
endif()

if(ANDROID)
    find_library(log-lib log REQUIRED)
else()
    find_package(JNI REQUIRED COMPONENTS JVM)
    set(log-lib "")
endif()

set(VECTRA_COMMON_COMPILE_OPTIONS -O2 -fno-fast-math -fvisibility=hidden -DRMR_JNI_BUILD=1 -DRMR_BUILD_HOST_TOOLING=1)
set(VECTRA_FREESTANDING_COMPILE_OPTIONS
    -O2
    -fno-fast-math
    -fvisibility=hidden
    -ffreestanding
    -fno-builtin
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -fdata-sections
    -ffunction-sections
    -Werror=implicit-function-declaration
    -DRMR_BUILD_HOST_TOOLING=1)
set(VECTRA_ABI_COMPILE_OPTIONS)
set(VECTRA_LTO_MODE "${VECTRA_LTO_MODE}" CACHE STRING "LTO mode from CI perf decisions")
if(VECTRA_LTO_MODE AND NOT VECTRA_LTO_MODE STREQUAL "thin" AND NOT VECTRA_LTO_MODE STREQUAL "full")
    message(FATAL_ERROR "Invalid VECTRA_LTO_MODE='${VECTRA_LTO_MODE}'; expected 'thin', 'full', or empty.")
endif()
set(VECTRA_INLINE_POLICY "${VECTRA_INLINE_POLICY}" CACHE STRING "Inline policy from CI perf decisions")

# Flag arm64 vinda do Gradle: baseline (-march=armv8-a) e tuning opcional (-mcpu=<valor>).
set(VECTRA_ARM64_CPP_FLAGS "${VECTRA_ARM64_CPP_FLAGS}" CACHE STRING "Extra arm64 C/C++ flags from Gradle")
set(VECTRA_ARM64_CPP_FLAGS_LIST)
if(VECTRA_ARM64_CPP_FLAGS)
    separate_arguments(VECTRA_ARM64_CPP_FLAGS_LIST NATIVE_COMMAND "${VECTRA_ARM64_CPP_FLAGS}")
endif()

# CMake já define -march=armv8-a+crc+simd para arm64-v8a; evita sobrescrever esse targeting.
set(VECTRA_ARM64_TUNING_FLAGS)
foreach(_flag IN LISTS VECTRA_ARM64_CPP_FLAGS_LIST)
    if(NOT _flag MATCHES "^-march=")
        list(APPEND VECTRA_ARM64_TUNING_FLAGS ${_flag})
    endif()
endforeach()

if(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "arm64-v8a")
    # +crc enables CRC32 intrinsics | +simd enables NEON | +sha2 enables SHA hw
    set(VECTRA_ABI_COMPILE_OPTIONS
        -march=armv8-a+crc+simd
        -DRMR_ARCH_ARM64=1
        -DRMR_FEAT_CRC32_HW=1
        -DRAF_ARCH_AARCH64=1)
    list(APPEND VECTRA_ABI_COMPILE_OPTIONS ${VECTRA_ARM64_TUNING_FLAGS})
endif()
if(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "x86_64")
    set(VECTRA_ABI_COMPILE_OPTIONS -msse4.2 -mpopcnt -DRMR_ARCH_X86_64=1)
endif()

if(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "x86")
    set(VECTRA_ABI_COMPILE_OPTIONS -mssse3 -msse4.2 -mpopcnt -DRMR_ARCH_X86=1)
endif()
if(ANDROID AND VECTRA_ANDROID_ABI STREQUAL "armeabi-v7a")
    # arm32-v7 baseline com NEON explícito para evitar regressões no NDK r27+
    # em toolchains que oscilam entre soft/softfp ao integrar JNI.
    set(VECTRA_ABI_COMPILE_OPTIONS
        -march=armv7-a
        -mthumb
        -mfpu=neon
        -mfloat-abi=softfp
        -DRMR_ARCH_ARM32=1
        -DRMR_FEAT_NEON=1
        -DRAF_ARCH_ARMV7=1)
endif()

if(VECTRA_INLINE_POLICY STREQUAL "aggressive")
    list(APPEND VECTRA_ABI_COMPILE_OPTIONS -finline-functions)
elseif(VECTRA_INLINE_POLICY STREQUAL "controlled")
    list(APPEND VECTRA_ABI_COMPILE_OPTIONS -fno-inline-functions)
endif()

if(VECTRA_LTO_MODE STREQUAL "thin" OR VECTRA_LTO_MODE STREQUAL "full")
    list(APPEND VECTRA_ABI_COMPILE_OPTIONS -flto)
endif()

if(TARGET rmr_policy_static)
    target_compile_options(rmr_policy_static PRIVATE
        ${VECTRA_COMMON_COMPILE_OPTIONS}
        ${VECTRA_ABI_COMPILE_OPTIONS})
    target_include_directories(rmr_policy_static PRIVATE
        ${VECTRA_RMR_INCLUDE_DIR})
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_POLICY_MODULE=1)
else()
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_POLICY_MODULE=0)
endif()

if(TARGET rmr_policy_static)
    target_link_libraries(vectra_core_accel PRIVATE rmr_policy_static)
endif()

if(TARGET bitraf_static)
    target_compile_options(bitraf_static PRIVATE
        ${VECTRA_COMMON_COMPILE_OPTIONS}
        ${VECTRA_ABI_COMPILE_OPTIONS})
    target_include_directories(bitraf_static PRIVATE
        ${VECTRA_RMR_INCLUDE_DIR})
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_BITRAF_MODULE=1)
else()
    target_compile_definitions(vectra_core_accel PRIVATE RMR_ENABLE_BITRAF_MODULE=0)
endif()

if(TARGET bitraf_static)
    target_link_libraries(vectra_core_accel PRIVATE bitraf_static)
endif()

if(VECTRA_REQUIRE_FREESTANDING_CORE AND NOT TARGET abi_core_freestanding)
    message(FATAL_ERROR "Freestanding ABI core target missing. Silent downgrade is forbidden.")
endif()

if(NOT VECTRA_REQUIRE_FREESTANDING_CORE)
    message(FATAL_ERROR "VECTRA_REQUIRE_FREESTANDING_CORE must remain ON for release-safe builds.")
endif()

target_compile_options(abi_core_freestanding PRIVATE
    ${VECTRA_FREESTANDING_COMPILE_OPTIONS}
    ${VECTRA_ABI_COMPILE_OPTIONS})
target_compile_definitions(abi_core_freestanding PRIVATE
    VECTRA_LL_NO_STDLIB_TYPES=1)
target_include_directories(abi_core_freestanding PRIVATE
    ${VECTRA_RMR_INCLUDE_DIR}
    ${CMAKE_CURRENT_LIST_DIR}
    ${CMAKE_CURRENT_LIST_DIR}/core)

# abi_core_freestanding is a STATIC archive. It has a compile/source contract,
# but no final linker invocation of its own; assigning -nostdlib or linker-only
# switches here created a false proof surface. The actual Android JNI artifact
# is vectra_core_accel and remains hosted by design. A true freestanding final
# link must be a dedicated executable/shared probe with its own entry point.
set_property(TARGET abi_core_freestanding PROPERTY
    VECTRA_FREESTANDING_LINK_STATE "TOKEN_VAZIO_DEDICATED_LINK_PROBE")

if(ANDROID)
    # Compatibilidade Android 15/16 (dispositivos com page size de 16 KiB).
    target_link_options(vectra_core_accel PRIVATE -Wl,-z,max-page-size=16384)
    if(TARGET termux-bootstrap)
        target_link_options(termux-bootstrap PRIVATE -Wl,-z,max-page-size=16384)
    endif()
endif()

target_compile_options(vectra_core_accel PRIVATE
    ${VECTRA_COMMON_COMPILE_OPTIONS}
    ${VECTRA_ABI_COMPILE_OPTIONS})
if(TARGET termux-bootstrap)
    target_compile_options(termux-bootstrap PRIVATE
        ${VECTRA_COMMON_COMPILE_OPTIONS}
        ${VECTRA_ABI_COMPILE_OPTIONS})
endif()

target_compile_definitions(vectra_core_accel PRIVATE
    RMR_ASM_CORE_EXPERIMENTAL=$<BOOL:${RMR_ASM_CORE_EXPERIMENTAL}>
    RMR_ASM_CORE_X86_64_VALIDATED=$<BOOL:${RMR_ASM_CORE_X86_64_VALIDATED}>
    RMR_ASM_CORE_ARM64_VALIDATED=$<BOOL:${RMR_ASM_CORE_ARM64_VALIDATED}>
    RMR_ASM_CORE_RISCV64_VALIDATED=$<BOOL:${RMR_ASM_CORE_RISCV64_VALIDATED}>)

target_link_libraries(vectra_core_accel PRIVATE ${log-lib})
target_link_libraries(vectra_core_accel PRIVATE abi_core_freestanding)
if(TARGET termux-bootstrap)
    target_link_libraries(termux-bootstrap PRIVATE ${log-lib})
endif()

target_include_directories(vectra_core_accel PRIVATE
    ${VECTRA_RMR_INCLUDE_DIR}
    ${CMAKE_CURRENT_LIST_DIR}
    ${CMAKE_CURRENT_LIST_DIR}/core)

if(TARGET termux-bootstrap)
    target_include_directories(termux-bootstrap PRIVATE
        ${VECTRA_RMR_INCLUDE_DIR}
        ${CMAKE_CURRENT_LIST_DIR}
        ${CMAKE_CURRENT_LIST_DIR}/core)
endif()

if(NOT ANDROID)
    target_include_directories(vectra_core_accel PRIVATE ${JNI_INCLUDE_DIRS})
    if(TARGET termux-bootstrap)
        target_include_directories(termux-bootstrap PRIVATE ${JNI_INCLUDE_DIRS})
    endif()
endif()

# Terminal JNI bridge (promoted from _incoming/termux.c — null-safety fixed in PR #1038)
add_library(termux_terminal_jni SHARED
    termux_jni.c)
target_link_libraries(termux_terminal_jni PRIVATE ${log-lib})
if(ANDROID)
    target_link_options(termux_terminal_jni PRIVATE -Wl,-z,max-page-size=16384)
endif()
if(NOT ANDROID)
    target_include_directories(termux_terminal_jni PRIVATE ${JNI_INCLUDE_DIRS})
endif()

if(CMAKE_ANDROID_NDK)
    target_include_directories(vectra_core_accel PRIVATE
        ${CMAKE_ANDROID_NDK}/sysroot/usr/include
        ${CMAKE_ANDROID_NDK}/sysroot/usr/include/${CMAKE_ANDROID_ARCH_TRIPLE})
    if(TARGET termux-bootstrap)
        target_include_directories(termux-bootstrap PRIVATE
            ${CMAKE_ANDROID_NDK}/sysroot/usr/include
            ${CMAKE_ANDROID_NDK}/sysroot/usr/include/${CMAKE_ANDROID_ARCH_TRIPLE})
    endif()
endif()
