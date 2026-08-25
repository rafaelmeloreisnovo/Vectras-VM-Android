cmake_minimum_required(VERSION 3.22.1)
project(vectra_omega_freestanding_deploy C ASM)

if(NOT ANDROID)
    message(FATAL_ERROR "freestanding deployment requires the Android NDK toolchain")
endif()
if(NOT ANDROID_ABI STREQUAL "armeabi-v7a")
    message(FATAL_ERROR "freestanding full v2 currently targets armeabi-v7a; ANDROID_ABI=${ANDROID_ABI}")
endif()
if(NOT CMAKE_STRIP)
    message(FATAL_ERROR "NDK llvm-strip is required for the final zero-symbol carrier")
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

get_filename_component(VECTRA_REPO_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(VECTRA_APP_CPP "${VECTRA_REPO_ROOT}/app/src/main/cpp")

# Reuse only the canonical freestanding ABI archive. Hosted/JNI modules remain
# outside this executable boundary and therefore cannot become runtime deps.
set(RMR_ENABLE_POLICY_MODULE OFF CACHE BOOL "" FORCE)
set(RMR_ENABLE_BITRAF_MODULE OFF CACHE BOOL "" FORCE)
set(RMR_ASM_CORE_EXPERIMENTAL OFF CACHE BOOL "" FORCE)
set(VECTRA_BUILD_FREESTANDING_LINK_PROBE OFF CACHE BOOL "" FORCE)
set(VECTRA_REQUIRE_FREESTANDING_CORE ON CACHE BOOL "" FORCE)
add_subdirectory("${VECTRA_APP_CPP}" "${CMAKE_BINARY_DIR}/vectra-core")
if(NOT TARGET abi_core_freestanding)
    message(FATAL_ERROR "canonical abi_core_freestanding target is unavailable")
endif()

set(OMEGA_OUTPUT_DIR "${CMAKE_BINARY_DIR}/out")
set(OMEGA_RAW_ELF "${OMEGA_OUTPUT_DIR}/omega-core-armv7.unstripped.elf")
set(OMEGA_ELF "${OMEGA_OUTPUT_DIR}/omega-core-armv7.elf")
set(OMEGA_MAP "${OMEGA_OUTPUT_DIR}/omega-core-armv7.map")

add_library(omega_deploy_objects OBJECT
    omega_deploy_main.c
    omega_deploy_start_armv7.S)

target_compile_definitions(omega_deploy_objects PRIVATE
    VECTRA_LL_NO_STDLIB_TYPES=1
    OMEGA_FREESTANDING_DEPLOYMENT=2)

target_include_directories(omega_deploy_objects PRIVATE
    "${VECTRA_APP_CPP}"
    "${VECTRA_APP_CPP}/core")

# One translation unit intentionally contains the specialized void micromodules.
# This avoids proliferating source files/symbols while letting LTO fold every
# helper into the single entry path. The only intentional runtime loop is the
# fixed-size binary command loop; there is no tail-fragment path.
target_compile_options(omega_deploy_objects PRIVATE
    -march=armv7-a
    -mfpu=neon
    -mfloat-abi=softfp
    $<$<COMPILE_LANGUAGE:C>:-Oz>
    $<$<COMPILE_LANGUAGE:C>:-flto=thin>
    $<$<COMPILE_LANGUAGE:C>:-ffreestanding>
    $<$<COMPILE_LANGUAGE:C>:-fno-builtin>
    $<$<COMPILE_LANGUAGE:C>:-fno-stack-protector>
    $<$<COMPILE_LANGUAGE:C>:-fno-unwind-tables>
    $<$<COMPILE_LANGUAGE:C>:-fno-asynchronous-unwind-tables>
    $<$<COMPILE_LANGUAGE:C>:-fno-optimize-sibling-calls>
    $<$<COMPILE_LANGUAGE:C>:-fno-pic>
    $<$<COMPILE_LANGUAGE:C>:-fno-pie>
    $<$<COMPILE_LANGUAGE:C>:-fomit-frame-pointer>
    $<$<COMPILE_LANGUAGE:C>:-fno-ident>
    $<$<COMPILE_LANGUAGE:C>:-fno-common>
    $<$<COMPILE_LANGUAGE:C>:-fdata-sections>
    $<$<COMPILE_LANGUAGE:C>:-ffunction-sections>
    $<$<COMPILE_LANGUAGE:C>:-fvisibility=hidden>
    $<$<COMPILE_LANGUAGE:C>:-Wall>
    $<$<COMPILE_LANGUAGE:C>:-Wextra>
    $<$<COMPILE_LANGUAGE:C>:-Werror>
    $<$<COMPILE_LANGUAGE:C>:-Wshadow>
    $<$<COMPILE_LANGUAGE:C>:-Wconversion>
    $<$<COMPILE_LANGUAGE:C>:-Wsign-conversion>
    $<$<COMPILE_LANGUAGE:C>:-Wundef>
    $<$<COMPILE_LANGUAGE:C>:-Wstrict-prototypes>
    $<$<COMPILE_LANGUAGE:C>:-Wmissing-prototypes>
    $<$<COMPILE_LANGUAGE:C>:-Wmissing-declarations>
    $<$<COMPILE_LANGUAGE:C>:-Wformat=2>
    $<$<COMPILE_LANGUAGE:C>:-Wvla>)

# Link an auditable image first, then strip the carrier completely. The audit
# reads symbols only from OMEGA_RAW_ELF while APK/device receive OMEGA_ELF.
add_custom_command(
    OUTPUT "${OMEGA_RAW_ELF}" "${OMEGA_MAP}"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${OMEGA_OUTPUT_DIR}"
    COMMAND "${CMAKE_LINKER}"
        -static
        --gc-sections
        --icf=all
        --lto-O2
        --build-id=none
        --no-undefined
        -e _start
        "-Map=${OMEGA_MAP}"
        -z max-page-size=16384
        -z noexecstack
        -o "${OMEGA_RAW_ELF}"
        $<TARGET_OBJECTS:omega_deploy_objects>
        $<TARGET_FILE:abi_core_freestanding>
    DEPENDS omega_deploy_objects abi_core_freestanding
    COMMAND_EXPAND_LISTS
    VERBATIM)

add_custom_command(
    OUTPUT "${OMEGA_ELF}"
    COMMAND "${CMAKE_COMMAND}" -E copy_if_different "${OMEGA_RAW_ELF}" "${OMEGA_ELF}"
    COMMAND "${CMAKE_STRIP}" --strip-all "${OMEGA_ELF}"
    DEPENDS "${OMEGA_RAW_ELF}"
    VERBATIM)

add_custom_target(omega_freestanding_deploy_armv7
    DEPENDS "${OMEGA_RAW_ELF}" "${OMEGA_ELF}" "${OMEGA_MAP}")

if(TARGET lowlevel_abi_contract_check)
    add_dependencies(omega_freestanding_deploy_armv7 lowlevel_abi_contract_check)
endif()

set_property(TARGET omega_freestanding_deploy_armv7 PROPERTY
    OMEGA_DEPLOYMENT_STATE "FULL_STANDALONE_BINARY_PROTOCOL_NOT_DEVICE_VERIFIED")
