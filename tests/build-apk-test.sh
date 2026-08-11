#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

install_fake_build_tools() {
  local fixture="$1"
  local version="$2"
  local build_tools_dir="$fixture/sdk/build-tools/$version"
  mkdir -p "$build_tools_dir"
  cat > "$build_tools_dir/aapt2" <<'AAPT2'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$#" -ne 3 || "$1" != "dump" || "$2" != "badging" || ! -f "$3" ]]; then
  exit 64
fi
tool_version="$(basename "$(dirname "$0")")"
printf 'aapt2:%s:%s\n' "$tool_version" "$3" >> "${FAKE_TOOL_LOG:?}"
exit "${FAKE_AAPT2_EXIT:-0}"
AAPT2
  cat > "$build_tools_dir/apksigner" <<'APKSIGNER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$#" -ne 2 || "$1" != "verify" || ! -f "$2" ]]; then
  exit 64
fi
tool_version="$(basename "$(dirname "$0")")"
printf 'apksigner:%s:%s\n' "$tool_version" "$2" >> "${FAKE_TOOL_LOG:?}"
exit "${FAKE_APKSIGNER_EXIT:-0}"
APKSIGNER
  chmod +x "$build_tools_dir/aapt2" "$build_tools_dir/apksigner"
}

new_fixture() {
  local name="$1"
  local fixture="$TEMP_ROOT/$name"
  mkdir -p "$fixture/app/build/outputs/apk/release"
  cp "$PROJECT_ROOT/build-apk.sh" "$fixture/build-apk.sh"
  cat > "$fixture/gradlew" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/outputs/apk/release app/build/outputs/apk/debug
case "${FAKE_APK_MODE:?}" in
  unsigned) printf 'unsigned' > app/build/outputs/apk/release/app-release-unsigned.apk ;;
  signed) printf 'signed' > app/build/outputs/apk/release/app-release.apk ;;
  debug) printf 'debug' > app/build/outputs/apk/debug/app-debug.apk ;;
esac
GRADLE
  install_fake_build_tools "$fixture" "35.0.0"
  chmod +x "$fixture/build-apk.sh" "$fixture/gradlew"
  printf '%s\n' "$fixture"
}

test_release_rejects_unsigned_apk() {
  local fixture
  fixture="$(new_fixture unsigned)"
  if (cd "$fixture" && FAKE_APK_MODE=unsigned ./build-apk.sh release >/dev/null 2>&1); then
    echo "FAIL: release 接受了 unsigned APK" >&2
    return 1
  fi
  [[ ! -e "$fixture/release.apk" ]]
}

test_release_rejects_unparseable_apk() {
  local fixture
  fixture="$(new_fixture unparseable)"
  printf 'previous' > "$fixture/release.apk"
  if (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=signed \
    FAKE_AAPT2_EXIT=1 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh release >/dev/null 2>&1); then
    echo "FAIL: release 接受了包信息不可解析的 APK" >&2
    return 1
  fi
  [[ "$(cat "$fixture/release.apk")" == "previous" ]]
}

test_release_rejects_invalid_signature() {
  local fixture
  fixture="$(new_fixture invalid-signature)"
  printf 'previous' > "$fixture/release.apk"
  if (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=signed \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=1 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh release >/dev/null 2>&1); then
    echo "FAIL: release 接受了签名无效的 APK" >&2
    return 1
  fi
  [[ "$(cat "$fixture/release.apk")" == "previous" ]]
}

test_release_validates_before_copying() {
  local fixture
  fixture="$(new_fixture valid-release)"
  (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=signed \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh release >/dev/null)
  [[ "$(cat "$fixture/release.apk")" == "signed" ]]
  [[ "$(cat "$fixture/tool.log")" == \
    $'aapt2:35.0.0:app/build/outputs/apk/release/app-release.apk\napksigner:35.0.0:app/build/outputs/apk/release/app-release.apk' ]]
}

test_debug_validates_before_copying() {
  local fixture
  fixture="$(new_fixture valid-debug)"
  (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=debug \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh debug >/dev/null)
  [[ "$(cat "$fixture/debug.apk")" == "debug" ]]
  [[ "$(cat "$fixture/tool.log")" == \
    $'aapt2:35.0.0:app/build/outputs/apk/debug/app-debug.apk\napksigner:35.0.0:app/build/outputs/apk/debug/app-debug.apk' ]]
}

test_release_prefers_stable_build_tools() {
  local fixture
  fixture="$(new_fixture stable-over-rc)"
  install_fake_build_tools "$fixture" "35.0.0-rc1"
  (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=signed \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh release >/dev/null)
  [[ "$(cat "$fixture/tool.log")" == \
    $'aapt2:35.0.0:app/build/outputs/apk/release/app-release.apk\napksigner:35.0.0:app/build/outputs/apk/release/app-release.apk' ]]
}

test_release_skips_incomplete_newer_build_tools() {
  local fixture
  fixture="$(new_fixture incomplete-newer)"
  install_fake_build_tools "$fixture" "36.0.0"
  rm "$fixture/sdk/build-tools/36.0.0/apksigner"
  if ! (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=signed \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh release >/dev/null); then
    echo "FAIL: release 未跳过工具不完整的更高版本 Build Tools" >&2
    return 1
  fi
  [[ "$(cat "$fixture/tool.log")" == \
    $'aapt2:35.0.0:app/build/outputs/apk/release/app-release.apk\napksigner:35.0.0:app/build/outputs/apk/release/app-release.apk' ]]
}

test_release_rejects_missing_sdk() {
  local fixture
  fixture="$(new_fixture missing-sdk)"
  printf 'previous' > "$fixture/release.apk"
  if (cd "$fixture" && \
    ANDROID_HOME="$fixture/missing-sdk" \
    ANDROID_SDK_ROOT="$fixture/missing-sdk" \
    FAKE_APK_MODE=signed \
    ./build-apk.sh release >/dev/null 2>&1); then
    echo "FAIL: release 在 Android SDK 缺失时仍成功" >&2
    return 1
  fi
  [[ "$(cat "$fixture/release.apk")" == "previous" ]]
}

test_release_rejects_missing_complete_build_tools() {
  local fixture
  fixture="$(new_fixture missing-build-tool)"
  rm "$fixture/sdk/build-tools/35.0.0/apksigner"
  printf 'previous' > "$fixture/release.apk"
  if (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    ANDROID_SDK_ROOT="$fixture/sdk" \
    FAKE_APK_MODE=signed \
    ./build-apk.sh release >/dev/null 2>&1); then
    echo "FAIL: release 在缺少完整 Build Tools 时仍成功" >&2
    return 1
  fi
  [[ "$(cat "$fixture/release.apk")" == "previous" ]]
}

test_release_rejects_unsigned_apk
test_release_rejects_unparseable_apk
test_release_rejects_invalid_signature
test_release_validates_before_copying
test_debug_validates_before_copying
test_release_prefers_stable_build_tools
test_release_skips_incomplete_newer_build_tools
test_release_rejects_missing_sdk
test_release_rejects_missing_complete_build_tools
echo "PASS: build-apk.sh"
