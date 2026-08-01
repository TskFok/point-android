# Point Quest Android 学生端

Point Quest Android 是积分闯关平台的学生端，覆盖登录注册、首页、首次答题、错题重练、商品兑换、订单和积分明细。客户端通过 OpenAPI 生成的 Kotlin/Retrofit 接口访问服务端，不包含管理端能力。

## 环境要求

- JDK 17；Gradle 编译目标和 Kotlin JVM target 均为 17。
- Android SDK 35（`compileSdk`/`targetSdk` 为 35），最低支持 Android 8.0（API 26）。
- Android SDK Platform Tools；只有执行设备测试时才需要 `adb` 和已连接设备/模拟器。
- 服务端本地开发另需 Node.js 24、pnpm 10.28.2、Docker 与 Docker Compose。

可先确认本机环境：

```bash
java -version
adb version
```

## 启动本地服务端

Android 工程默认与同级 `../point` 服务端仓库配合。首次从空数据库启动：

```bash
cd ../point
cp .env.example .env
docker compose up -d db
pnpm install
pnpm db:migrate
pnpm db:seed
pnpm dev
```

API 默认监听 `http://localhost:3000`，Swagger UI 位于 `http://localhost:3000/api/docs`。Android 模拟器不能把宿主机当作自身的 `localhost`，因此 Debug 构建使用根地址 `http://10.0.2.2:3000/`；生成接口会自行追加 `/api/v1/...`，不要把 `/api/v1` 写入根地址。

## OpenAPI 契约

默认契约文件是同级服务端仓库的 `../point/openapi/openapi.json`。先在服务端修改并重新生成契约，再在 Android 工程校验和生成客户端：

```bash
cd ../point
pnpm api:spec
cd ../point-android
./gradlew openApiValidate openApiGenerate
```

需要使用其他契约文件时，通过绝对路径覆盖 `pointOpenApiSpec`：

```bash
./gradlew openApiValidate openApiGenerate \
  -PpointOpenApiSpec=/absolute/path/to/openapi.json
```

生成结果位于 `app/build/generated/openapi`，属于构建产物，不应手工编辑。生产业务代码只通过 Gateway/Repository 消费生成的 `DefaultApi`，页面和 ViewModel 不直接依赖生成接口。

## 构建与测试

下面命令均从本仓库根目录执行。示例 Release 根地址 `https://api.example.invalid/` 使用保留的 `.invalid` 域，仅用于本地构建验证，**不可用于生产部署，也不是可访问服务**。

```bash
# 契约
./gradlew openApiValidate openApiGenerate

# Debug 与 Release JVM 单元测试
./gradlew :app:testDebugUnitTest
./gradlew :app:testReleaseUnitTest \
  -PpointApiBaseUrl=https://api.example.invalid/

# Android instrumentation 编译与测试 APK 打包（不需要设备）
./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest

# Debug 与 Release Lint
./gradlew :app:lintDebug
./gradlew :app:lintRelease \
  -PpointApiBaseUrl=https://api.example.invalid/

# 应用 APK
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease \
  -PpointApiBaseUrl=https://api.example.invalid/
```

主要产物：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 未签名 Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`
- Debug instrumentation APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- JVM 测试报告：`app/build/reports/tests/`
- Lint 报告：`app/build/reports/lint-results-debug.html`、`lint-results-release.html`

有设备时再运行：

```bash
adb devices -l
./gradlew :app:connectedDebugAndroidTest
```

`adb devices -l` 若没有状态为 `device` 的目标，不应把 instrumentation 编译或 APK 打包描述成“设备测试通过”。

## 网络配置

- Debug：固定根地址 `http://10.0.2.2:3000/`，只在 Debug manifest/网络安全配置中允许明文流量。
- Release：必须显式传入 `-PpointApiBaseUrl=https://api.example.invalid/`；值必须是带尾部 `/` 的有效 HTTPS 根地址，且不得包含 `/api/v1`。
- Release 主清单禁止明文流量；图片根地址从 Release API 根 origin 派生。
- `example.invalid`、`api.example.invalid` 等示例域名仅作格式或构建验证，严禁用于生产。

认证、令牌轮换、重试、幂等、分页、错误、图片与日志规则见 [API 接入说明](docs/api-integration-notes.md)。
