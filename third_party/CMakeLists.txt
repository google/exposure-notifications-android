cmake_minimum_required(VERSION 3.4.1)

set(protobuf_BUILD_PROTOC_BINARIES OFF)
set(protobuf_BUILD_TESTS OFF CACHE BOOL "Build protobuf tests" FORCE)
add_definitions(-DBUILD_TESTING=OFF)
set(CMAKE_EXE_LINKER_FLAGS  "${CMAKE_EXE_LINKER_FLAGS} -llog" )
set(CMAKE_SHARED_LINKER_FLAGS  "${CMAKE_EXE_LINKER_FLAGS} -llog" )

add_subdirectory(abseil-cpp EXCLUDE_FROM_ALL)
add_subdirectory(boringssl EXCLUDE_FROM_ALL)
add_subdirectory(protobuf/cmake EXCLUDE_FROM_ALL)

file(GLOB protosrc proto_gen/prio/proto/*.cc proto_gen/prio/jni/*.cc)
find_library(log-lib log)
find_library(android-lib android)

add_library(
  prioclient SHARED
  libprio/prio/client.cc
  libprio/prio/data.cc
  libprio/prio/encryption.cc
  libprio/prio/finite_field.cc
  libprio/prio/jni/prio_client_jni.cc
  libprio/prio/poly.cc
  libprio/prio/prng/aes_128_ctr_seeded_prng.cc
  libprio/prio/randomized_response.cc
  libprio/prio/serialization.cc
  ${protosrc}
)

include_directories(
  prioclient
  libprio
  proto_gen
  boringssl/include
  protobuf/src
)

target_link_libraries(
  prioclient
  ssl
  libprotobuf
  ${log-lib}
  ${android-lib}
  absl::strings
  absl::status
  absl::statusor
  absl::cord
  absl::memory
  absl::meta
  absl::numeric
  absl::base
)
