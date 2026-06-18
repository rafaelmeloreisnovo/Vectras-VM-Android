# Baseline único de CMake alinhado ao pipeline Android JNI/Gradle.
cmake_minimum_required(VERSION 3.22.1)
project(rafaelia_rmr C ASM)
include(CMakeParseArguments)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

include(${CMAKE_SOURCE_DIR}/engine/rmr/sources_rmr_core.cmake)

option(RMR_JNI_BUILD "Use hosted/JNI libc path for root builds" ON)
set(RMR_BUILD_HOST_TOOLING "AUTO" CACHE STRING "Host-tooling compatibility path (AUTO mirrors RMR_JNI_BUILD)" )
set_property(CACHE RMR_BUILD_HOST_TOOLING PROPERTY STRINGS AUTO ON OFF)
option(RMR_ENABLE_POLICY_MODULE "Enable RMR policy module for root builds" ON)
option(RMR_ASM_CORE_EXPERIMENTAL "Enable experimental ASM core hot paths" ON)
option(RMR_ASM_CORE_X86_64_VALIDATED "Enable ASM hot paths on x86_64 (validated ABI)" ON)
option(RMR_ASM_CORE_ARM64_VALIDATED "Enable ASM hot paths on arm64-v8a (validated ABI)" ON)
option(RMR_ASM_CORE_RISCV64_VALIDATED "Enable RV64I-compatible CASM path on riscv64 (validated ABI)" ON)

function(rmr_bool_to_int INPUT_BOOL OUTPUT_VAR)
  if(${INPUT_BOOL})
    set(${OUTPUT_VAR} 1 PARENT_SCOPE)
  else()
    set(${OUTPUT_VAR} 0 PARENT_SCOPE)
  endif()
endfunction()

rmr_bool_to_int(RMR_JNI_BUILD RMR_JNI_BUILD_VALUE)

if(RMR_BUILD_HOST_TOOLING STREQUAL "AUTO")
  set(RMR_BUILD_HOST_TOOLING_VALUE ${RMR_JNI_BUILD_VALUE})
elseif(RMR_BUILD_HOST_TOOLING)
  set(RMR_BUILD_HOST_TOOLING_VALUE 1)
else()
  set(RMR_BUILD_HOST_TOOLING_VALUE 0)
endif()

rmr_bool_to_int(RMR_ENABLE_POLICY_MODULE RMR_ENABLE_POLICY_MODULE_VALUE)

message(STATUS "RMR_JNI_BUILD resolved to ${RMR_JNI_BUILD_VALUE}")
message(STATUS "RMR_BUILD_HOST_TOOLING input=${RMR_BUILD_HOST_TOOLING} resolved=${RMR_BUILD_HOST_TOOLING_VALUE}")
message(STATUS "RMR_ENABLE_POLICY_MODULE resolved to ${RMR_ENABLE_POLICY_MODULE_VALUE}")

file(WRITE "${CMAKE_BINARY_DIR}/rmr_build_config.env"
  "RMR_JNI_BUILD=${RMR_JNI_BUILD_VALUE}\n"
  "RMR_BUILD_HOST_TOOLING=${RMR_BUILD_HOST_TOOLING_VALUE}\n"
  "RMR_ENABLE_POLICY_MODULE=${RMR_ENABLE_POLICY_MODULE_VALUE}\n"
)

rmr_bool_to_int(RMR_ASM_CORE_EXPERIMENTAL RMR_ASM_CORE_EXPERIMENTAL_VALUE)
rmr_bool_to_int(RMR_ASM_CORE_X86_64_VALIDATED RMR_ASM_CORE_X86_64_VALIDATED_VALUE)
rmr_bool_to_int(RMR_ASM_CORE_ARM64_VALIDATED RMR_ASM_CORE_ARM64_VALIDATED_VALUE)
rmr_bool_to_int(RMR_ASM_CORE_RISCV64_VALIDATED RMR_ASM_CORE_RISCV64_VALIDATED_VALUE)

if(ANDROID AND EXISTS "${CMAKE_SOURCE_DIR}/engine/platform/android/CMakeLists.txt")
  include(${CMAKE_SOURCE_DIR}/engine/platform/android/CMakeLists.txt)
elseif(CMAKE_SYSTEM_NAME STREQUAL "Linux" AND EXISTS "${CMAKE_SOURCE_DIR}/engine/platform/linux/CMakeLists.txt")
  include(${CMAKE_SOURCE_DIR}/engine/platform/linux/CMakeLists.txt)
endif()

function(rmr_apply_common_flags target_name)
  target_compile_options(${target_name} PRIVATE
    $<$<C_COMPILER_ID:MSVC>:/O2 /W4>
    $<$<NOT:$<C_COMPILER_ID:MSVC>>:
      -O3 -Wall -Wextra -pedantic
      # gc-sections methodology: each symbol in own section → dead sections
      # generate -Wunused-* warnings → --gc-sections removes them from binary.
      # Combined with -fvisibility=hidden: reduces .dynsym to exported-only set.
      # Net effect: 60-70% binary reduction vs naive build.
      -ffunction-sections
      -fdata-sections
      -fvisibility=hidden
    >
  )
  target_link_options(${target_name} PRIVATE
    $<$<NOT:$<C_COMPILER_ID:MSVC>>:
      # Remove unreferenced sections identified by -Wunused-* warning pipeline.
      -Wl,--gc-sections
      # Strip symbol table entries for hidden-visibility symbols from .dynsym.
      -Wl,--exclude-libs,ALL
    >
  )
  target_compile_definitions(${target_name} PRIVATE
    RMR_JNI_BUILD=${RMR_JNI_BUILD_VALUE}
    RMR_BUILD_HOST_TOOLING=${RMR_BUILD_HOST_TOOLING_VALUE}
    RMR_ENABLE_POLICY_MODULE=${RMR_ENABLE_POLICY_MODULE_VALUE}
    RMR_ASM_CORE_EXPERIMENTAL=${RMR_ASM_CORE_EXPERIMENTAL_VALUE}
    RMR_ASM_CORE_X86_64_VALIDATED=${RMR_ASM_CORE_X86_64_VALIDATED_VALUE}
    RMR_ASM_CORE_ARM64_VALIDATED=${RMR_ASM_CORE_ARM64_VALIDATED_VALUE}
    RMR_ASM_CORE_RISCV64_VALIDATED=${RMR_ASM_CORE_RISCV64_VALIDATED_VALUE}
  )
  target_include_directories(${target_name} PRIVATE ${CMAKE_SOURCE_DIR}/engine/rmr/include)
endfunction()



rmr_manifest_apply_base(RMR_SOURCES ${RMR_SOURCE_GROUP_CORE} ${RMR_SOURCE_GROUP_HOST_ONLY})

if(RMR_ENABLE_POLICY_MODULE)
  rmr_manifest_apply_base(RMR_POLICY_SOURCES ${RMR_SOURCE_GROUP_OPTIONAL_POLICY})
  list(APPEND RMR_SOURCES ${RMR_POLICY_SOURCES})
endif()

set(RMR_HAS_CASM FALSE)
set(RMR_HAS_NEON_SELFTEST FALSE)
set(RMR_HAS_NEON_SOURCES FALSE)

if(RMR_ASM_CORE_EXPERIMENTAL AND CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64" AND RMR_ASM_CORE_X86_64_VALIDATED)
  list(APPEND RMR_SOURCES
    ${RMR_SOURCE_GROUP_ASM_X86_64}
  )
  set(RMR_HAS_CASM TRUE)
endif()

if(RMR_ASM_CORE_EXPERIMENTAL AND CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "riscv64" AND RMR_ASM_CORE_RISCV64_VALIDATED)
  list(APPEND RMR_SOURCES
    ${RMR_SOURCE_GROUP_ASM_RISCV64}
  )
  set(RMR_HAS_CASM TRUE)
endif()

if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
  rmr_manifest_apply_base(RMR_ARM32_ASM_SOURCES ${RMR_SOURCE_GROUP_ASM_ARM32})
  list(APPEND RMR_SOURCES ${RMR_ARM32_ASM_SOURCES})
endif()

if((ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a") OR
   (CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)$" AND RMR_ASM_CORE_ARM64_VALIDATED))
  rmr_manifest_apply_base(RMR_ARM64_ASM_SOURCES ${RMR_SOURCE_GROUP_ASM_ARM64})
  list(APPEND RMR_SOURCES ${RMR_ARM64_ASM_SOURCES})
  set(RMR_HAS_CASM TRUE)
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "Linux" AND CMAKE_SYSTEM_PROCESSOR MATCHES "^(aarch64|arm64)$")
  rmr_manifest_apply_base(RMR_NEON_SIMD_SOURCES ${RMR_SOURCE_GROUP_ASM_ARM64_NEON})
  list(APPEND RMR_SOURCES ${RMR_NEON_SIMD_SOURCES})
  set(RMR_HAS_NEON_SELFTEST TRUE)
  set(RMR_HAS_NEON_SOURCES TRUE)
endif()

if(RMR_HAS_CASM OR (NOT ANDROID AND RMR_ASM_CORE_EXPERIMENTAL))
  set(VECTRA_HAS_CASM_MARKER 1)
  message(STATUS "VECTRA_HAS_CASM_MARKER=1 (ASM core available)")
else()
  set(VECTRA_HAS_CASM_MARKER 0)
  message(STATUS "VECTRA_HAS_CASM_MARKER=0 (ASM core disabled)")
endif()
add_compile_definitions(VECTRA_HAS_CASM_MARKER=${VECTRA_HAS_CASM_MARKER})

add_library(rmr STATIC ${RMR_SOURCES})
rmr_apply_common_flags(rmr)

function(rmr_add_host_executable target_name source_file)
  set(options)
  set(oneValueArgs)
  set(multiValueArgs LINK_LIBS COMPILE_DEFINITIONS)
  cmake_parse_arguments(RMR_EXE "${options}" "${oneValueArgs}" "${multiValueArgs}" ${ARGN})

  add_executable(${target_name} ${source_file})
  target_link_libraries(${target_name} PRIVATE rmr ${RMR_EXE_LINK_LIBS})
  if(RMR_EXE_COMPILE_DEFINITIONS)
    target_compile_definitions(${target_name} PRIVATE ${RMR_EXE_COMPILE_DEFINITIONS})
  endif()
  rmr_apply_common_flags(${target_name})
endfunction()

set(RMR_REQUIRED_SYMBOLS
  RmR_MathFabric_AutodetectPlan
  RmR_MathFabric_VectorMix
  RmR_TorusFlow_RunDeterministic
)

add_custom_command(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/verify_librmr_symbols.stamp
  COMMAND ${CMAKE_SOURCE_DIR}/tools/verify_librmr_symbols.sh
          $<TARGET_FILE:rmr>
          ${RMR_REQUIRED_SYMBOLS}
  COMMAND ${CMAKE_COMMAND} -E touch ${CMAKE_CURRENT_BINARY_DIR}/verify_librmr_symbols.stamp
  DEPENDS rmr ${CMAKE_SOURCE_DIR}/tools/verify_librmr_symbols.sh
  VERBATIM
)

add_custom_target(verify_librmr_symbols
  DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/verify_librmr_symbols.stamp
)

add_custom_target(verify_contracts
  DEPENDS verify_librmr_symbols
)

if(NOT ANDROID)
rmr_add_host_executable(rafaelia_demo demo_cli/src/main.c)
rmr_add_host_executable(rmr_bench bench/src/rmr_benchmark_main.c COMPILE_DEFINITIONS RMR_JNI_BUILD=1)
rmr_add_host_executable(vectra_policy_benchmark bench/src/vectra_policy_benchmark_main.c COMPILE_DEFINITIONS RMR_JNI_BUILD=1)
rmr_add_host_executable(bitomega_smoketest demo_cli/src/bitomega_smoketest.c)
rmr_add_host_executable(bitraf_core engine/rmr/src/rafaelia_bitraf_core.c COMPILE_DEFINITIONS RAF_HOSTED_TEST=1)

add_library(bitraf_static STATIC engine/rmr/src/bitraf.c)
set_target_properties(bitraf_static PROPERTIES OUTPUT_NAME bitraf)
rmr_apply_common_flags(bitraf_static)

target_link_libraries(rmr PUBLIC bitraf_static)

add_library(bitraf_shared SHARED engine/rmr/src/bitraf.c)
set_target_properties(bitraf_shared PROPERTIES OUTPUT_NAME bitraf_shared)
rmr_apply_common_flags(bitraf_shared)

rmr_add_host_executable(bitraf_selftest demo_cli/src/bitraf_selftest.c LINK_LIBS bitraf_static)
rmr_add_host_executable(policy_kernel_demo demo_cli/src/policy_kernel_demo.c)
rmr_add_host_executable(policy_kernel_selftest demo_cli/src/policy_kernel_selftest.c)
rmr_add_host_executable(math_fabric_selftest demo_cli/src/math_fabric_selftest.c)
rmr_add_host_executable(rmr_torus_flow_selftest demo_cli/src/rmr_torus_flow_selftest.c)
rmr_add_host_executable(rmr_vector_field_selftest demo_cli/src/rmr_vector_field_selftest.c)
rmr_add_host_executable(determinism_signature_selftest demo_cli/src/determinism_signature_selftest.c)
rmr_add_host_executable(apk_module_demo demo_cli/src/apk_module_demo.c)
rmr_add_host_executable(rmr_qemu_bridge_demo demo_cli/src/rmr_qemu_bridge_demo.c)
rmr_add_host_executable(rmr_qemu_bridge_selftest demo_cli/src/rmr_qemu_bridge_selftest.c)
rmr_add_host_executable(rafa_cti_scan engine/rmr/src/rafa_cti_scan.c LINK_LIBS m)

if(RMR_HAS_CASM)
  rmr_add_host_executable(rmr_casm_bridge_selftest demo_cli/src/rmr_casm_bridge_selftest.c)
endif()

rmr_add_host_executable(rmr_asm_equivalence_selftest demo_cli/src/rmr_asm_equivalence_selftest.c)
rmr_add_host_executable(rmr_unified_arena_selftest demo_cli/src/rmr_unified_arena_selftest.c LINK_LIBS bitraf_static)
rmr_add_host_executable(rmr_legacy_kernel_selftest demo_cli/src/rmr_legacy_kernel_selftest.c)
rmr_add_host_executable(rmr_hw_detect_selftest demo_cli/src/rmr_hw_detect_selftest.c)
rmr_add_host_executable(zipraf_core_selftest demo_cli/src/zipraf_core_selftest.c)
# Cache TCG é grupo ANDROID_ONLY; o selftest host compila as fontes direto.
add_executable(rmr_tcg_cache_selftest
  demo_cli/src/rmr_tcg_cache_selftest.c
  engine/rmr/src/rmr_tcg_cache.c
  engine/rmr/src/rmr_attractor.c
)
target_link_libraries(rmr_tcg_cache_selftest PRIVATE rmr)
rmr_apply_common_flags(rmr_tcg_cache_selftest)

rmr_add_host_executable(rmr_vectra_os_contract_selftest demo_cli/src/rmr_vectra_os_contract_selftest.c)
rmr_add_host_executable(vectra_os_mvp_bench bench/src/vectra_os_mvp_bench_main.c)
rmr_add_host_executable(vectra_external_engine_selftest demo_cli/src/vectra_external_engine_selftest.c)
rmr_add_host_executable(rmr_external_engine_selftest demo_cli/src/rmr_external_engine_selftest.c)
rmr_add_host_executable(rmr_policy_benchmark demo_cli/src/rmr_policy_benchmark.c)

if(RMR_HAS_NEON_SELFTEST)
  rmr_add_host_executable(rmr_neon_simd_selftest demo_cli/src/neon_simd_selftest.c)
endif()


set(RMR_RUN_SELFTEST_COMMANDS
  bitraf_selftest
  math_fabric_selftest
  rmr_torus_flow_selftest
  rmr_vector_field_selftest
  determinism_signature_selftest
  policy_kernel_selftest
  rmr_qemu_bridge_selftest
  rmr_unified_arena_selftest
  rmr_legacy_kernel_selftest
  rmr_hw_detect_selftest
  rmr_asm_equivalence_selftest
  zipraf_core_selftest
  rmr_tcg_cache_selftest
  rmr_vectra_os_contract_selftest
  vectra_external_engine_selftest
  bitomega_smoketest
  rmr_external_engine_selftest
)

set(RMR_RUN_SELFTEST_DEPENDS
  bitraf_selftest
  math_fabric_selftest
  rmr_torus_flow_selftest
  rmr_vector_field_selftest
  determinism_signature_selftest
  policy_kernel_selftest
  rmr_qemu_bridge_selftest
  rmr_unified_arena_selftest
  rmr_legacy_kernel_selftest
  rmr_hw_detect_selftest
  rmr_asm_equivalence_selftest
  zipraf_core_selftest
  rmr_tcg_cache_selftest
  rmr_vectra_os_contract_selftest
  vectra_external_engine_selftest
  bitomega_smoketest
  rmr_external_engine_selftest
)

if(RMR_HAS_CASM)
  list(INSERT RMR_RUN_SELFTEST_COMMANDS 3 rmr_casm_bridge_selftest)
  list(INSERT RMR_RUN_SELFTEST_DEPENDS 3 rmr_casm_bridge_selftest)
endif()

if(RMR_HAS_NEON_SELFTEST)
  list(APPEND RMR_RUN_SELFTEST_COMMANDS rmr_neon_simd_selftest)
  list(APPEND RMR_RUN_SELFTEST_DEPENDS rmr_neon_simd_selftest)
endif()

add_custom_target(run_selftest
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/bench/results
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  DEPENDS verify_contracts ${RMR_RUN_SELFTEST_DEPENDS}
)

foreach(RMR_SELFTEST_CMD IN LISTS RMR_RUN_SELFTEST_COMMANDS)
  add_custom_command(TARGET run_selftest POST_BUILD
    COMMAND $<TARGET_FILE:${RMR_SELFTEST_CMD}>
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  )
endforeach()
add_custom_target(run_bitomega_smoketest
  COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/bench/results
  COMMAND $<TARGET_FILE:bitomega_smoketest>
  WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
  DEPENDS bitomega_smoketest
)

endif()

if(NOT ANDROID)
find_package(Python3 COMPONENTS Interpreter QUIET)
find_program(GRAPHVIZ_DOT_EXECUTABLE dot)
if(Python3_Interpreter_FOUND AND GRAPHVIZ_DOT_EXECUTABLE)
  add_custom_target(bitomega_transition_graph
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/reports
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_SOURCE_DIR}/tools/bitomega_csv_to_dot.py
            ${CMAKE_SOURCE_DIR}/bench/results/bitomega_transitions.csv
            ${CMAKE_SOURCE_DIR}/reports/bitomega_transition_graph.dot
            ${CMAKE_SOURCE_DIR}/reports/bitomega_summary.md
    COMMAND ${GRAPHVIZ_DOT_EXECUTABLE} -Tpng
            ${CMAKE_SOURCE_DIR}/reports/bitomega_transition_graph.dot
            -o ${CMAKE_SOURCE_DIR}/reports/bitomega_transition_graph.png
    WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
    DEPENDS run_bitomega_smoketest
  )
else()
  set(RMR_REPORT_MISSING_TOOLS "")
  if(NOT Python3_Interpreter_FOUND)
    list(APPEND RMR_REPORT_MISSING_TOOLS "Python3 interpreter")
  endif()
  if(NOT GRAPHVIZ_DOT_EXECUTABLE)
    list(APPEND RMR_REPORT_MISSING_TOOLS "Graphviz dot")
  endif()
  string(JOIN ", " RMR_REPORT_MISSING_TOOLS_TEXT ${RMR_REPORT_MISSING_TOOLS})
  message(STATUS "Skipping bitomega_transition_graph target: missing ${RMR_REPORT_MISSING_TOOLS_TEXT}")
endif()
endif()
