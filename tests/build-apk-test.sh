#!/usr/bin/env bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

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
  mkdir -p "$fixture/sdk/build-tools/35.0.0"
  cat > "$fixture/sdk/build-tools/35.0.0/aapt2" <<'AAPT2'
#!/usr/bin/env bash
printf 'aapt2\n' >> "${FAKE_TOOL_LOG:?}"
exit "${FAKE_AAPT2_EXIT:-0}"
AAPT2
  cat > "$fixture/sdk/build-tools/35.0.0/apksigner" <<'APKSIGNER'
#!/usr/bin/env bash
printf 'apksigner\n' >> "${FAKE_TOOL_LOG:?}"
exit "${FAKE_APKSIGNER_EXIT:-0}"
APKSIGNER
  chmod +x "$fixture/build-apk.sh" "$fixture/gradlew"
  chmod +x "$fixture/sdk/build-tools/35.0.0/aapt2" \
    "$fixture/sdk/build-tools/35.0.0/apksigner"
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
    FAKE_APK_MODE=signed \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh release >/dev/null)
  [[ "$(cat "$fixture/release.apk")" == "signed" ]]
  [[ "$(cat "$fixture/tool.log")" == $'aapt2\napksigner' ]]
}

test_debug_validates_before_copying() {
  local fixture
  fixture="$(new_fixture valid-debug)"
  (cd "$fixture" && \
    ANDROID_HOME="$fixture/sdk" \
    FAKE_APK_MODE=debug \
    FAKE_AAPT2_EXIT=0 \
    FAKE_APKSIGNER_EXIT=0 \
    FAKE_TOOL_LOG="$fixture/tool.log" \
    ./build-apk.sh debug >/dev/null)
  [[ "$(cat "$fixture/debug.apk")" == "debug" ]]
  [[ "$(cat "$fixture/tool.log")" == $'aapt2\napksigner' ]]
}

test_release_rejects_unsigned_apk
test_release_rejects_unparseable_apk
test_release_rejects_invalid_signature
test_release_validates_before_copying
test_debug_validates_before_copying
echo "PASS: build-apk.sh"
