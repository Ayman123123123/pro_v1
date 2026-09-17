cmake_minimum_required(VERSION 3.10.2)
project("rnnoise_jni")

# Include RNNoise source (assumes rnnoise submodule or source in third_party/rnnoise)
add_subdirectory(${CMAKE_SOURCE_DIR}/../third_party/rnnoise)

# JNI wrapper
add_library(
    rnnoise_jni
    SHARED
    rnnoise_jni.cpp
)

target_link_libraries(
    rnnoise_jni
    rnnoise
    log
)