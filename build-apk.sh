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
  # 未配置签名时产物为 app-release-unsigned.apk；若已签名则优先用 signed。
  if [[ -f "app/build/outputs/apk/release/app-release.apk" ]]; then
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
  else
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
  fi
else
  ./gradlew :app:assembleDebug
  APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "未找到 APK 文件: $APK_PATH"
  exit 1
fi

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
APK_NAME="${VARIANT}.apk"
DEST_PATH="./${APK_NAME}"

cp "$APK_PATH" "$DEST_PATH"

echo "===> 编译完成，APK 已复制到项目根目录:"
echo "     $DEST_PATH"
