cmake_minimum_required(VERSION 3.22.1)
project("securenet" C CXX)

# Hardened C core: ChaCha20 (RFC 8439), SHA-256 (FIPS 180-4), key clamping.
add_library(securenet SHARED
        securenet-jni.c
        chacha20.c
        sha256.c)

find_library(log-lib log)

target_link_libraries(securenet ${log-lib})

target_compile_options(securenet PRIVATE
        -O2
        -fvisibility=hidden
        -Wall)

set_target_properties(securenet PROPERTIES
        C_STANDARD 11)
