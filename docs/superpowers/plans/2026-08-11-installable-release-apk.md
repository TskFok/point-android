# 可直接安装的 Release APK 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `./build-apk.sh release` 只生成包信息可解析、签名有效并可直接安装的 `release.apk`。

**Architecture:** Android Gradle Plugin 使用 Debug signingConfig 直接签署 Release 变体；构建脚本拒绝 unsigned 回退，并在覆盖根目录产物之前调用 SDK Build Tools 校验包信息和签名。Shell 回归测试在临时项目中注入假 Gradle、`aapt2` 与 `apksigner`，独立验证失败边界和成功路径。

**Tech Stack:** Bash 3.2、Android Gradle Plugin、Kotlin DSL、Android SDK `aapt2`/`apksigner`、ADB（可选实机验收）

## Global Constraints

- 默认在当前 `master` 分支修改，不新建分支。
- Release 使用 Debug keystore 仅限本地或内部测试，不提交 keystore、口令或其他密钥。
- 保持 Release 构建类型、`com.pointquest.android`、版本号、API 地址和业务代码不变。
- `build-apk.sh` 不得回退复制 `app-release-unsigned.apk`。
- 验证失败时不得覆盖根目录目标 APK。
- 所有 Git 提交信息使用简体中文。

---

### Task 1: Release 变体生成已签名 APK

**Files:**
- Create: `tests/build-apk-test.sh`
- Modify: `app/build.gradle.kts:56-65`
- Modify: `build-apk.sh:27-43`
- Test: `tests/build-apk-test.sh`

**Interfaces:**
- Consumes: Android Gradle Plugin 内置的 `signingConfigs.debug`。
- Produces: `app/build/outputs/apk/release/app-release.apk`；Release 脚本只接受该路径。

- [ ] **Step 1: 写入拒绝 unsigned 回退的失败测试**

创建可执行脚本 `tests/build-apk-test.sh`。测试把生产脚本复制到临时项目，假 Gradle 只生成 unsigned 文件，并断言 Release 构建失败且不产生根目录 APK：

```bash
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
```

- [ ] **Step 2: 运行测试并确认因旧回退逻辑失败**

Run: `chmod +x tests/build-apk-test.sh && tests/build-apk-test.sh`

Expected: FAIL，输出 `release 接受了 unsigned APK`。

- [ ] **Step 3: 配置 Release 签名并删除 unsigned 回退**

在 `app/build.gradle.kts` 的 Release 构建类型中加入：

```kotlin
getByName("release") {
    signingConfig = signingConfigs.getByName("debug")
    buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
    buildConfigField("String", "IMAGE_BASE_URL", "\"$releaseImageBaseUrl\"")
}
```

把 `build-apk.sh` 的 Release 分支收敛为固定路径：

```bash
if [[ "$VARIANT" == "release" ]]; then
  ./gradlew :app:assembleRelease -PpointApiBaseUrl=https://api.example.invalid/
  APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
  ./gradlew :app:assembleDebug
  APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi
```

- [ ] **Step 4: 运行 Shell 测试并确认通过**

Run: `tests/build-apk-test.sh`

Expected: PASS，输出 `PASS: build-apk.sh`。

- [ ] **Step 5: 提交最小签名修复**

```bash
git add tests/build-apk-test.sh app/build.gradle.kts build-apk.sh
git commit -m "修复发布包签名配置"
```

---

### Task 2: 复制前验证包信息和签名

**Files:**
- Modify: `tests/build-apk-test.sh`
- Modify: `build-apk.sh:8-52`
- Test: `tests/build-apk-test.sh`

**Interfaces:**
- Consumes: `${ANDROID_SDK_ROOT}`，否则 `${ANDROID_HOME}`；SDK 下版本目录中的 `aapt2` 和 `apksigner`。
- Produces: 仅在 `aapt2 dump badging "$APK_PATH"` 与 `apksigner verify "$APK_PATH"` 都成功后复制 `${VARIANT}.apk`。

- [ ] **Step 1: 为假 SDK 工具和调用日志扩展测试夹具**

在 `new_fixture` 中创建 `sdk/build-tools/35.0.0/aapt2` 和 `apksigner`。两个工具将名称追加到 `${FAKE_TOOL_LOG}`，并读取独立退出码：

```bash
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
chmod +x "$fixture/sdk/build-tools/35.0.0/aapt2" \
  "$fixture/sdk/build-tools/35.0.0/apksigner"
```

- [ ] **Step 2: 写入 Release 失败/成功路径及 Debug 回归测试**

追加以下完整测试函数，并在每次运行脚本时传入 `ANDROID_HOME="$fixture/sdk"` 与 `FAKE_TOOL_LOG="$fixture/tool.log"`：

```bash
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
```

- [ ] **Step 3: 运行测试并确认旧脚本没有调用校验工具**

Run: `tests/build-apk-test.sh`

Expected: FAIL；至少“不可解析 APK”和“无效签名”用例错误地成功。

- [ ] **Step 4: 在复制前定位 Build Tools 并执行校验**

在 APK 存在性检查后、`cp` 前加入：

```bash
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" || ! -d "$SDK_ROOT/build-tools" ]]; then
  echo "未找到 Android SDK Build Tools，请检查 ANDROID_SDK_ROOT 或 ANDROID_HOME。"
  exit 1
fi

BUILD_TOOLS_VERSION="$(
  find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; |
    sort -t. -k1,1n -k2,2n -k3,3n |
    tail -n 1
)"
BUILD_TOOLS_DIR="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
AAPT2="$BUILD_TOOLS_DIR/aapt2"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

if [[ ! -x "$AAPT2" || ! -x "$APKSIGNER" ]]; then
  echo "Android SDK Build Tools 缺少 aapt2 或 apksigner: $BUILD_TOOLS_DIR"
  exit 1
fi

"$AAPT2" dump badging "$APK_PATH" >/dev/null
"$APKSIGNER" verify "$APK_PATH"
```

保留 `DEST_PATH` 和 `cp` 在上述校验之后，确保失败不会覆盖目标文件。删除未使用的 `TIMESTAMP`。

- [ ] **Step 5: 运行 Shell 测试并确认所有路径通过**

Run: `tests/build-apk-test.sh`

Expected: PASS；unsigned、包信息无效、签名无效均被拒绝，合法产物在两项校验之后复制。

- [ ] **Step 6: 提交产物校验**

```bash
git add tests/build-apk-test.sh build-apk.sh
git commit -m "增加安装包有效性校验"
```

---

### Task 3: 真实构建与安装验收

**Files:**
- Verify: `release.apk`
- Verify: `app/build/outputs/apk/release/app-release.apk`

**Interfaces:**
- Consumes: 本机 Android SDK、Gradle 缓存、可选的已授权 Android 设备。
- Produces: 根目录签名有效的 `release.apk` 和可复核的验证输出。

- [ ] **Step 1: 运行脚本级回归测试和 Gradle 单元测试**

Run: `tests/build-apk-test.sh`

Expected: PASS。

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL，现有 JVM 测试全部通过。

- [ ] **Step 2: 删除诊断阶段的旧产物并真实构建 Release**

先确认目标是仓库根目录中由本任务生成的未跟踪 `release.apk`，再删除它；随后运行：

Run: `./build-apk.sh release`

Expected: BUILD SUCCESSFUL，脚本校验成功并生成新的 `release.apk`。

- [ ] **Step 3: 独立复核包信息和签名证书**

先定位本机最高版本 Build Tools：

```bash
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
BUILD_TOOLS_VERSION="$(
  find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; |
    sort -t. -k1,1n -k2,2n -k3,3n |
    tail -n 1
)"
BUILD_TOOLS_DIR="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
```

Run: `"$BUILD_TOOLS_DIR/aapt2" dump badging release.apk`

Expected: 输出包含 `package: name='com.pointquest.android'`、`versionCode='1'`、`versionName='0.1.0'`。

Run: `"$BUILD_TOOLS_DIR/apksigner" verify --verbose --print-certs release.apk`

Expected: 校验成功，`Verified using v2 scheme` 或更高版本为 true，并显示 Android Debug 证书信息。

- [ ] **Step 4: 在有设备时执行实际安装**

Run: `adb devices -l`

如果存在状态为 `device` 的已授权设备，Run: `adb install -r release.apk`。

Expected: `Success`。如果没有设备，明确记录“包信息和签名已验证，未执行实机安装”，不得声称已实机安装。

- [ ] **Step 5: 检查最终差异和仓库状态**

Run: `git diff --check && git status --short && git log -3 --oneline`

Expected: 无格式错误；代码和测试提交均存在；`release.apk` 作为交付产物保持未跟踪，不被误提交。
