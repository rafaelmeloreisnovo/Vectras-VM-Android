# Android platform module for JNI builds.
if(NOT ANDROID)
  return()
endif()

message(STATUS "[VECTRAS-Android] Applying JNI platform flags for ${CMAKE_ANDROID_ARCH_ABI}")

add_compile_definitions(RMR_JNI_BUILD=1)

set(_vectra_android_cflags -O2 -fno-fast-math -fno-exceptions -fno-rtti)
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
  list(APPEND _vectra_android_cflags -march=armv8-a+crc+simd)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
  list(APPEND _vectra_android_cflags -march=armv7-a -mfpu=neon)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
  list(APPEND _vectra_android_cflags -msse4.2 -mpopcnt)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
  list(APPEND _vectra_android_cflags -mssse3 -msse4.2 -mpopcnt)
endif()
set(VECTRA_ANDROID_PLATFORM_CXX_FLAGS ${_vectra_android_cflags} PARENT_SCOPE)
