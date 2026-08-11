#!/usr/bin/env bash

set -euo pipefail

# 切到项目根目录（脚本所在目录）
cd "$(dirname "$0")"

# 命令行环境常无 ANDROID_HOME；自动探测 macOS 默认 SDK，供 Gradle 使用。
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -d "${HOME}/Library/Android/sdk" ]]; then
    export ANDROID_HOME="${HOME}/Library/Android/sdk"
    export ANDROID_SDK_ROOT="${ANDROID_HOME}"
  fi
fi

VARIANT="${1:-release}"  # 可选参数：release（默认）或 debug

if [[ "$VARIANT" != "release" && "$VARIANT" != "debug" ]]; then
  echo "用法: $0 [release|debug]"
  exit 1
fi

# macOS Bash 3.2 + set -u：紧邻非 ASCII 字符时必须使用 ${VARIANT}，
# 否则会把后续字节误解析进变量名。
echo "===> 使用变体: ${VARIANT}，开始编译 APK..."

if [[ "$VARIANT" == "release" ]]; then
  ./gradlew :app:assembleRelease -PpointApiBaseUrl=https://api.example.invalid/
  APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew :app:assembleDebug
  APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "未找到 APK 文件: $APK_PATH"
  exit 1
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT/build-tools" ]]; then
  echo "未找到 Android SDK Build Tools，请检查 ANDROID_SDK_ROOT 或 ANDROID_HOME。"
  exit 1
fi

BUILD_TOOLS_VERSION="$(
  for CANDIDATE_DIR in "$SDK_ROOT"/build-tools/*; do
    [[ -d "$CANDIDATE_DIR" ]] || continue
    CANDIDATE_VERSION="$(basename "$CANDIDATE_DIR")"
    [[ "$CANDIDATE_VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+$ ]] || continue
    [[ -x "$CANDIDATE_DIR/aapt2" && -x "$CANDIDATE_DIR/apksigner" ]] || continue
    printf '%s\n' "$CANDIDATE_VERSION"
  done |
    sort -t. -k1,1n -k2,2n -k3,3n |
    tail -n 1
)"
if [[ -z "$BUILD_TOOLS_VERSION" ]]; then
  echo "未找到同时包含 aapt2 和 apksigner 的稳定版 Android SDK Build Tools。"
  exit 1
fi
BUILD_TOOLS_DIR="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
AAPT2="$BUILD_TOOLS_DIR/aapt2"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

"$AAPT2" dump badging "$APK_PATH" >/dev/null
"$APKSIGNER" verify "$APK_PATH"

APK_NAME="${VARIANT}.apk"
DEST_PATH="./${APK_NAME}"

cp "$APK_PATH" "$DEST_PATH"

echo "===> 编译完成，APK 已复制到项目根目录:"
echo "     $DEST_PATH"
