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

test_release_rejects_unsigned_apk
echo "PASS: build-apk.sh"
