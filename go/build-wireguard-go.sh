#!/usr/bin/env bash
#
# Builds the wireguard-go userspace backend as libwgcore.so for every
# Android ABI and drops it into app/src/main/jniLibs so WgNative picks it
# up at runtime.
#
# Prerequisites:
#   - Go 1.21+
#   - Android NDK r26+ with $ANDROID_NDK_HOME exported
#
# Usage:
#   ./go/build-wireguard-go.sh
#
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/app/src/main/jniLibs"
MODULE_DIR="$PROJECT_ROOT/go/libwg"

: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to your Android NDK path}"

declare -A ABI_TO_TRIPLE=(
  ["arm64-v8a"]="aarch64-linux-android21"
  ["armeabi-v7a"]="armv7a-linux-androideabi21"
  ["x86_64"]="x86_64-linux-android21"
)

echo "==> Fetching Go module dependencies"
cd "$MODULE_DIR"
go mod tidy

for abi in arm64-v8a armeabi-v7a x86_64; do
  triple="${ABI_TO_TRIPLE[$abi]}"
  cc="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/${triple}-clang"
  mkdir -p "$OUTPUT_DIR/$abi"
  echo "==> Building $abi"
  GOOS=android GOARCH=$(
    case "$abi" in
      arm64-v8a) echo arm64 ;;
      armeabi-v7a) echo arm ;;
      x86_64) echo amd64 ;;
    esac
  ) \
  CGO_ENABLED=1 \
  CC="$cc" \
  go build -buildmode=c-shared \
    -o "$OUTPUT_DIR/$abi/libwgcore.so" \
    .
done

echo "==> Done. libwgcore.so installed to:"
find "$OUTPUT_DIR" -name "libwgcore.so"
