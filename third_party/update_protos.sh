#! /bin/bash

# Script to regenerate _pb.h files from libprio protos.

protoc \
  --proto_path=libprio \
  --cpp_out=proto_gen \
  libprio/prio/proto/algorithm_parameters.proto \
  libprio/prio/jni/message.proto
