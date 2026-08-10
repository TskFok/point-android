# Point Quest Android 学生端

Point Quest Android 是积分闯关平台的学生端，覆盖登录注册、首页、首次答题、错题重练、商品兑换、订单和积分明细。客户端通过 OpenAPI 生成的 Kotlin/Retrofit 接口访问服务端，不包含管理端能力。

当前应用显示名为 `Point Quest`，`versionName` 为 `0.1.0`。

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

API 默认监听 `http://localhost:3000`，Swagger UI 位于 `http://localhost:3000/api/docs`。Android 模拟器不能把宿主机当作自身的 `localhost`，因此 Debug 构建默认使用根地址 `http://10.0.2.2:3000/`。也可以在登录页把 Host 改为任意合法的开发 HTTP 根地址（例如同一局域网中的服务），应用后下一次登录、注册和商品图片请求会使用新 Host。登录页运行时 Host 可省略尾部 `/`，校验器应用时会自动补齐；Host 只能填写服务根 origin，生成接口会自行追加 `/api/v1/...`，不要把 `/api/v1` 写入 Host。

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

# Release 根地址、联网权限与明文流量边界的自动校验
./gradlew verifyReleaseApiBaseUrlValidation verifyNetworkSecurityConfig \
  -PpointApiBaseUrl=https://api.example.invalid/

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

- 主清单显式声明 `android.permission.INTERNET`；`verifyNetworkSecurityConfig` 会同时检查 Debug/Release 合并清单。
- Debug：默认根地址为 `http://10.0.2.2:3000/`，登录页允许配置任意合法的开发 HTTP 或 HTTPS 根 origin；Debug 网络安全配置允许开发环境明文流量。
- Release：构建时必须显式传入 `-PpointApiBaseUrl=https://api.example.invalid/`，且该 Gradle 参数必须自带尾部 `/`，否则构建失败；登录页运行时 Host 只接受 HTTPS，但可省略尾部 `/`，由校验器自动补齐。两者都必须是纯服务根 origin；子路径（包括 `/api/v1`）、用户信息、查询参数和片段均不允许。
- 主 Manifest 和 Release 网络安全配置继续禁止明文流量；Debug 的 HTTP 放行不会削弱 Release 边界。
- 商品图片始终跟随当前 API Host 的 origin，不接受独立或跨源图片地址。
- `example.invalid`、`api.example.invalid` 等示例域名仅作格式或构建验证，严禁用于生产。

首页、个人中心和商店通过进程内失效信号同步答题/兑换结果；首页和商店收到信号后会 online-first 重新拉取。商品列表支持下拉刷新，失败时保留当前内容并给出非致命提示。

认证、令牌轮换、重试、幂等、分页、错误、图片与日志规则见 [API 接入说明](docs/api-integration-notes.md)。
