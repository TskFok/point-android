# 可直接安装的 Release APK 设计规格

## 背景

`./build-apk.sh release` 当前执行 `assembleRelease` 后，在没有 Release 签名配置时回退复制 `app-release-unsigned.apk`。该文件的 ZIP 结构和 Android Manifest 均可正常解析，但 `apksigner verify` 报告缺少签名信息，因此 Android 安装器无法安装，部分调用方表现为 `PackageInfo is null`。

本项目当前的 Release APK 用于本地或内部测试，用户已确认可以使用 Android Debug keystore 签名。

## 目标

- `./build-apk.sh release` 生成的根目录 `release.apk` 具有有效签名并可直接安装。
- Release 仍使用 Release 构建类型，包括现有 API 地址和网络安全配置。
- 构建脚本不再静默回退到未签名 APK。
- 自动验证产物的包信息和签名，避免再次输出不可安装文件。

## 非目标

- 不引入或管理正式发布证书。
- 不把 Release 构建替换成 Debug 构建。
- 不改变应用 ID、版本号、API 地址或业务代码。
- 不处理 Google Play、应用商店或生产发布流程。

## 设计方案

在 `app/build.gradle.kts` 中让 `release` 构建类型使用 Android Gradle Plugin 已提供的 Debug signingConfig。Gradle 将直接产出已对齐、已签名的 `app-release.apk`，避免在 shell 脚本中重复实现 `zipalign`、`apksigner` 和密钥路径探测。

`build-apk.sh` 的 Release 分支只接受 `app/build/outputs/apk/release/app-release.apk`。删除对 `app-release-unsigned.apk` 的回退：如果 Gradle 未生成已签名产物，脚本应明确失败，不能把无法安装的文件复制为 `release.apk`。

脚本在复制前使用 Android SDK Build Tools 中的 `aapt2` 和 `apksigner` 校验源 APK：

- `aapt2 dump badging` 必须成功，证明包信息可解析；
- `apksigner verify` 必须成功，证明 APK 具有 Android 可接受的签名。

Build Tools 版本从 Android SDK 的已安装目录中选择最高版本，沿用脚本现有的 SDK 环境变量与 macOS 默认 SDK 探测。缺少 SDK、`aapt2` 或 `apksigner` 时输出明确错误并终止。

Debug 分支保持现有构建和产物路径不变，但共用包信息与签名验证，确保脚本的两种输出都满足“可安装 APK”的最低条件。

## 错误处理

- Gradle 构建失败：保留原始非零退出行为。
- 未找到预期的已签名 APK：报告具体路径并退出。
- 未找到可用 Build Tools：提示检查 Android SDK 安装和环境变量。
- 包信息解析或签名验证失败：不覆盖根目录目标 APK，并以非零状态退出。

验证必须发生在复制之前，以免一次失败构建留下名称正确但不可安装的新产物。

## 测试设计

先增加针对 `build-apk.sh` 的 shell 回归测试，使用临时目录和可控的假 Gradle/SDK 工具验证脚本行为：

- Release 仅复制 `app-release.apk`，不接受只有 unsigned 产物的情况；
- 包信息解析失败时构建失败且不复制目标文件；
- 签名校验失败时构建失败且不复制目标文件；
- 两项校验通过时复制为根目录 `release.apk`。

随后运行真实构建并验证：

1. `./build-apk.sh release` 成功；
2. `aapt2 dump badging release.apk` 能读出 `com.pointquest.android`；
3. `apksigner verify --verbose --print-certs release.apk` 成功，并显示 Debug 证书；
4. 若存在已连接且已授权的 Android 设备，执行 `adb install -r release.apk` 验证实际安装。

## 安全边界

Debug keystore 仅提供安装所需的开发签名，不具备生产发布安全性。未来接入正式发布流程时，应改为从受保护配置提供独立 Release signingConfig；本次设计不会把任何密钥、口令或本地 keystore 提交到仓库。

## 验收标准

- `./build-apk.sh release` 不再选择 `app-release-unsigned.apk`。
- 根目录 `release.apk` 的包名为 `com.pointquest.android` 且签名验证通过。
- APK 可被 Android 包安装器直接安装，不再出现 `PackageInfo is null`。
- `./build-apk.sh debug` 的既有行为不回归。
