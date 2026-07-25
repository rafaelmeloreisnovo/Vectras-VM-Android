# Android platform module for JNI builds.
if(NOT ANDROID)
  return()
endif()

message(STATUS "[VECTRAS-Android] Registering target-scoped C flags for ${CMAKE_ANDROID_ARCH_ABI}")

# This project is C/ASM. Keep C++-only switches (-fno-rtti/-fno-exceptions)
# out of this module and apply every flag to an explicit target. Optimization
# level remains owned by the caller's build profile so Android does not silently
# override -O2/-O3 decisions made elsewhere.
function(vectra_apply_android_platform_c_flags target_name)
  if(NOT TARGET ${target_name})
    message(FATAL_ERROR
      "vectra_apply_android_platform_c_flags: unknown target '${target_name}'")
  endif()

  set(_vectra_android_cflags
    -fno-fast-math)

  if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    list(APPEND _vectra_android_cflags -march=armv8-a+crc+simd)
  elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    list(APPEND _vectra_android_cflags
      -march=armv7-a
      -mthumb
      -mfpu=neon
      -mfloat-abi=softfp)
  elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    list(APPEND _vectra_android_cflags -msse4.2 -mpopcnt)
  elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    list(APPEND _vectra_android_cflags -mssse3 -msse4.2 -mpopcnt)
  else()
    message(WARNING
      "[VECTRAS-Android] No explicit ISA baseline for ABI='${CMAKE_ANDROID_ARCH_ABI}'; using toolchain defaults")
  endif()

  target_compile_options(${target_name} PRIVATE
    $<$<COMPILE_LANGUAGE:C>:${_vectra_android_cflags}>)
  target_compile_definitions(${target_name} PRIVATE RMR_JNI_BUILD=1)
endfunction()
