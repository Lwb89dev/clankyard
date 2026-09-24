#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-/home/antona89/Android/Sdk}/ndk/27.1.12297006}"
CLANG="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
API=29
TARGET="aarch64-linux-android${API}"
mkdir -p "$OUT"
"$CLANG" --target="$TARGET" -O2 -fPIE -pie -o "$OUT/libclankyard_hello.so" "$ROOT/native/exec/hello.c"
"$CLANG" --target="$TARGET" -O2 -fPIE -pie -o "$OUT/libclankyard_exec_helper.so" "$ROOT/native/exec/helper.c"
"$CLANG" --target="$TARGET" -O2 -fPIC -shared -o "$OUT/libclankyard_exec.so" "$ROOT/native/exec/interceptor.c" -ldl
echo "wrote $OUT"
ls -l "$OUT"
