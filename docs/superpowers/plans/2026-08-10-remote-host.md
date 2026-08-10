# 登录页远端 Host 配置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在登录页提供可持久化、可立即生效的服务端根地址配置，并让认证与商品图片请求使用最新 Host。

**Architecture:** 保持单例 `AppContainer`，新增 `RemoteHostStore` 作为运行时 Host 唯一来源。OkHttp 在每次请求前动态替换 Origin；登录页由独立的 `RemoteHostViewModel` 管理草稿和应用状态，认证逻辑继续由 `AuthViewModel` 负责。

**Tech Stack:** Kotlin 2.1.20、Jetpack Compose、Material 3、Android SharedPreferences、OkHttp、Retrofit、Coil、JUnit、MockWebServer、Compose UI Test。

## Global Constraints

- 默认在当前 `master` 分支修改，不创建新分支。
- 所有用户可见文本使用简体中文；提交消息使用简体中文。
- 最低 Android API 26，compile/target SDK 35，JDK 17。
- Host 必须是 `http(s)://host[:port]/` 形式的纯服务根地址；不得包含 `/api/v1`、其他路径、用户信息、查询参数或片段。
- Debug 允许开发环境 HTTP；Release 只允许 HTTPS，并继续禁止明文流量。
- OpenAPI 生成路径已包含 `/api/v1`，动态 Host 只能替换 Origin，不能修改接口路径。
- 不在循环遍历中查询 SQL；本功能不新增 SQL 或数据库访问。
- 不记录 Host、密码、令牌、Cookie、Authorization 或请求/响应 body。

---

### Task 1: Host 校验与持久化核心

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/network/RemoteHostValidator.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/RemoteHostStore.kt`
- Create: `app/src/test/java/com/pointquest/android/core/network/RemoteHostValidatorTest.kt`
- Create: `app/src/test/java/com/pointquest/android/core/network/RemoteHostStoreTest.kt`

**Interfaces:**
- `RemoteHostValidator(allowHttp: Boolean).validate(raw: String): RemoteHostValidation`
- `RemoteHostValidation.Valid(normalized: String)` 和 `RemoteHostValidation.Invalid(code: RemoteHostErrorCode)`。
- `RemoteHostStore(defaultHost: String, persistence: RemoteHostPersistence, validator: RemoteHostValidator)`。
- `RemoteHostStore.currentHost: String`、`hostFlow: StateFlow<String>`、`apply(raw: String): RemoteHostApplyResult`。
- `RemoteHostApplyResult.Applied(host: String)`、`RemoteHostApplyResult.Rejected(code: RemoteHostErrorCode)` 和 `RemoteHostApplyResult.PersistenceFailed`。
- `RemoteHostPersistence.read(): String?`、`write(value: String)`；`SharedPreferencesRemoteHostPersistence` 封装 Android 偏好存储。

- [ ] **Step 1: 写失败的 Host 校验测试**

覆盖：

```kotlin
assertEquals(
    RemoteHostValidation.Valid("https://api.example.test/"),
    RemoteHostValidator(allowHttp = false).validate(" https://api.example.test "),
)
assertEquals(
    RemoteHostValidation.Valid("http://192.168.1.10:3000/"),
    RemoteHostValidator(allowHttp = true).validate("http://192.168.1.10:3000/"),
)
assertEquals(
    RemoteHostErrorCode.HTTPS_REQUIRED,
    (RemoteHostValidator(allowHttp = false).validate("http://dev.example.test/") as RemoteHostValidation.Invalid).code,
)
```

另测空字符串、非法协议、非根路径、`/api/v1/`、用户信息、查询参数、片段、非法端口和 IPv6 根地址。

- [ ] **Step 2: 运行校验测试确认先失败**

运行：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.network.RemoteHostValidatorTest'
```

预期：因类型和校验器尚未存在而编译失败。

- [ ] **Step 3: 实现最小校验器**

使用 `java.net.URI` 检查原始路径、用户信息、查询和片段，再使用 OkHttp `HttpUrl` 检查协议、Host 和端口。允许原始路径为空或 `/`，输出统一为带尾部 `/` 的 Origin；`allowHttp=false` 时拒绝 HTTP。

```kotlin
sealed interface RemoteHostValidation {
    data class Valid(val normalized: String) : RemoteHostValidation
    data class Invalid(val code: RemoteHostErrorCode) : RemoteHostValidation
}

enum class RemoteHostErrorCode {
    REQUIRED, INVALID_FORMAT, ROOT_PATH_ONLY, HTTPS_REQUIRED,
}
```

- [ ] **Step 4: 运行校验测试确认通过**

运行同一条 `RemoteHostValidatorTest` 命令，预期全部通过。

- [ ] **Step 5: 写失败的 Store 测试**

使用内存 `RemoteHostPersistence` 验证默认值、合法持久值读取、非法持久值回退、应用成功更新内存和持久化值、应用失败不覆盖旧值。

- [ ] **Step 6: 实现 Store 和 SharedPreferences 适配器**

`RemoteHostStore` 在初始化时校验持久值；非法值或读取异常回退到已校验的 `defaultHost`。`apply` 只有在校验成功且持久化成功后才更新 `MutableStateFlow`；失败返回结构化结果，不抛给 UI。

使用名称明确的偏好文件和 key，例如 `point_quest_settings` 与 `remote_host`，不得复用会话加密存储。

- [ ] **Step 7: 运行 Store 测试确认通过**

运行：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.network.RemoteHostStoreTest'
```

- [ ] **Step 8: 提交核心配置变更**

```bash
git add app/src/main/java/com/pointquest/android/core/network/RemoteHostValidator.kt \
  app/src/main/java/com/pointquest/android/core/network/RemoteHostStore.kt \
  app/src/test/java/com/pointquest/android/core/network/RemoteHostValidatorTest.kt \
  app/src/test/java/com/pointquest/android/core/network/RemoteHostStoreTest.kt
git commit -m "新增远端 Host 校验与持久化"
```

### Task 2: 动态网络 Origin 与图片地址

**Files:**
- Modify: `app/src/main/java/com/pointquest/android/core/network/ApiClients.kt`
- Modify: `app/src/main/java/com/pointquest/android/data/products/ProductImageUrlFactory.kt`
- Modify: `app/src/test/java/com/pointquest/android/core/network/ApiClientsTest.kt`
- Modify: `app/src/test/java/com/pointquest/android/data/products/ProductImageUrlFactoryTest.kt`

**Interfaces:**
- `ApiClients(baseUrl: String, sessionState: SessionState, hostProvider: () -> String = { baseUrl })`。
- `ProductImageUrlFactory(imageBaseUrl: String = BuildConfig.IMAGE_BASE_URL)` 保持现有调用兼容，并新增 `ProductImageUrlFactory(imageBaseUrlProvider: () -> String)`。

- [ ] **Step 1: 为动态请求写失败测试**

给 `ApiClientsTest` 增加一个可变 Host provider，构造请求并记录最终 URL，断言初始请求使用 `https://first.example.test/`，更新 provider 后使用 `https://second.example.test:8443/`，且 `/api/v1/auth/token` 路径不变。保留公开/受保护认证头、Cookie 和超时现有断言。

- [ ] **Step 2: 运行网络测试确认先失败**

运行：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.network.ApiClientsTest'
```

预期：动态路由测试失败，因为客户端目前不读取 Host provider。

- [ ] **Step 3: 实现动态 Host 拦截器**

在 `ApiClients` 中增加私有 `RemoteHostInterceptor`。请求发出前将当前 Host 解析为 `HttpUrl`，用 `request.url.newBuilder()` 只替换 `scheme`、`host`、`port`，再继续原请求。`ApiClients` 的公开和受保护 Builder 都安装它；无参数的 companion builder 保持现有单元测试用途，不强制引入 provider。

- [ ] **Step 4: 运行网络测试确认通过**

运行同一条 `ApiClientsTest` 命令，预期动态 Host、路径、认证头和安全客户端约束全部通过。

- [ ] **Step 5: 为动态图片地址写失败测试**

让图片 Host provider 从 `https://images-first.example.test/` 切换到 `https://images-second.example.test/`，断言第二次 `urlOrNull` 使用第二个 Origin，并继续断言非法 key 返回 `null`。

- [ ] **Step 6: 实现图片 Host provider**

将 `ProductImageUrlFactory` 内部的固定 `HttpUrl` 改为每次调用 `urlOrNull` 时从 provider 解析当前根地址；保留字符串构造函数和既有根 Origin、路径穿越、UUID、扩展名校验。

- [ ] **Step 7: 运行图片测试确认通过**

运行：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.products.ProductImageUrlFactoryTest'
```

- [ ] **Step 8: 提交网络路由变更**

```bash
git add app/src/main/java/com/pointquest/android/core/network/ApiClients.kt \
  app/src/main/java/com/pointquest/android/data/products/ProductImageUrlFactory.kt \
  app/src/test/java/com/pointquest/android/core/network/ApiClientsTest.kt \
  app/src/test/java/com/pointquest/android/data/products/ProductImageUrlFactoryTest.kt
git commit -m "支持网络请求与图片动态切换 Host"
```

### Task 3: AppContainer 与测试依赖接线

**Files:**
- Modify: `app/src/main/java/com/pointquest/android/app/AppDependencies.kt`
- Modify: `app/src/main/java/com/pointquest/android/app/AppContainer.kt`
- Modify: `app/src/androidTest/java/com/pointquest/android/AppNavigationTestShell.kt`
- Modify: `app/src/androidTest/java/com/pointquest/android/app/ApplicationContainerTest.kt`

**Interfaces:**
- `AppDependencies` 新增 `val remoteHostStore: RemoteHostStore`。
- `AppContainer` 使用 `SharedPreferencesRemoteHostPersistence(applicationContext)` 和 `RemoteHostValidator(BuildConfig.DEBUG)` 创建唯一 Store。

- [ ] **Step 1: 先更新依赖契约和测试假实现**

给 `FakeAppDependencies` 增加内存持久化的 `RemoteHostStore`，使所有现有 Android 导航测试继续拥有稳定默认 Host。编译测试应先因 `AppContainer` 尚未提供新依赖而失败。

- [ ] **Step 2: 接入真实 AppContainer**

在 `AppContainer` 中先创建 `remoteHostStore`，再将 `remoteHostStore::currentHost` 传给 `ApiClients` 和 `ProductImageUrlFactory`。删除不再需要的固定 `imageBaseUrl` 构造参数，图片 Origin 与 API Host 统一。

- [ ] **Step 3: 添加容器接线断言并运行测试**

在 `ApplicationContainerTest` 断言真实容器的 `remoteHostStore.currentHost` 非空且重复读取稳定；运行：

```bash
./gradlew :app:compileDebugAndroidTestKotlin
```

预期 Android 测试源码可编译通过。

- [ ] **Step 4: 提交容器接线变更**

```bash
git add app/src/main/java/com/pointquest/android/app/AppDependencies.kt \
  app/src/main/java/com/pointquest/android/app/AppContainer.kt \
  app/src/androidTest/java/com/pointquest/android/AppNavigationTestShell.kt \
  app/src/androidTest/java/com/pointquest/android/app/ApplicationContainerTest.kt
git commit -m "接入运行时远端 Host 配置"
```

### Task 4: Host ViewModel 与登录前置校验

**Files:**
- Create: `app/src/main/java/com/pointquest/android/feature/auth/RemoteHostViewModel.kt`
- Create: `app/src/test/java/com/pointquest/android/feature/auth/RemoteHostViewModelTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- `RemoteHostUiState(activeHost: String, draftHost: String, error: UiText? = null, message: UiText? = null, applying: Boolean = false)`。
- `RemoteHostViewModel(store: RemoteHostStore, scopeOverride: CoroutineScope? = null)`。
- `updateHost(value: String)`、`apply(): Job?`、`requireAppliedForLogin(): Boolean`。

- [ ] **Step 1: 写失败的 ViewModel 测试**

覆盖：

```kotlin
viewModel.updateHost("https://new.example.test")
assertTrue(viewModel.uiState.value.draftHost != viewModel.uiState.value.activeHost)
viewModel.apply()!!.join()
assertEquals("https://new.example.test/", viewModel.uiState.value.activeHost)
assertTrue(viewModel.requireAppliedForLogin())
```

另测非法应用保留旧值并产生错误、未应用草稿使 `requireAppliedForLogin()` 返回 `false`、应用期间重复调用返回 `null`。

- [ ] **Step 2: 运行 ViewModel 测试确认先失败**

运行：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.auth.RemoteHostViewModelTest'
```

- [ ] **Step 3: 添加中文资源和最小 ViewModel 实现**

增加服务端地址标签、提示、应用按钮、成功、必填、格式错误、根路径错误、Release HTTPS 错误、保存失败和“请先应用新的服务端地址”等字符串。ViewModel 将 `RemoteHostErrorCode` 映射为对应 `UiText.Resource`；编辑时清除旧错误和成功提示。

- [ ] **Step 4: 运行 ViewModel 测试确认通过**

运行同一条 `RemoteHostViewModelTest` 命令，预期全部通过。

- [ ] **Step 5: 提交 Host 状态层变更**

```bash
git add app/src/main/java/com/pointquest/android/feature/auth/RemoteHostViewModel.kt \
  app/src/test/java/com/pointquest/android/feature/auth/RemoteHostViewModelTest.kt \
  app/src/main/res/values/strings.xml
git commit -m "新增登录页 Host 状态管理"
```

### Task 5: 登录页 UI 与导航接线

**Files:**
- Modify: `app/src/main/java/com/pointquest/android/feature/auth/LoginScreen.kt`
- Modify: `app/src/main/java/com/pointquest/android/app/AppNavHost.kt`
- Modify: `app/src/androidTest/java/com/pointquest/android/feature/auth/AuthScreenTest.kt`
- Modify: `app/src/androidTest/java/com/pointquest/android/AppNavigationTest.kt`

**Interfaces:**
- `LoginScreen` 新增 `hostState: RemoteHostUiState`、`onHostChange: (String) -> Unit`、`onApplyHost: () -> Unit`，保留现有认证参数。

- [ ] **Step 1: 先扩展 Compose 测试**

在 `AuthScreenTest` 验证登录页展示 Host 输入框和应用按钮，应用按钮至少 48dp；在导航测试中输入新 Host、点击应用、检查成功提示，再完成原有注册登录流程。此时测试应因参数和 UI 尚不存在而失败。

- [ ] **Step 2: 实现 LoginScreen Host 区域**

在认证字段前加入服务端地址 `AuthTextField`，使用 `login_host` test tag；加入 `login_host_apply` test tag 的 `OutlinedButton`，最小高度 48dp。显示 `hostState.error` 作为字段错误，显示 `hostState.message` 作为 polite live region；认证提交或 Host 应用期间禁用相关控件。

- [ ] **Step 3: 在 AppNavHost 创建并连接两个 ViewModel**

登录路由中使用 `container.remoteHostStore` 创建 `RemoteHostViewModel`，收集其 `uiState` 并传给 `LoginScreen`。`onLogin` 先执行 `requireAppliedForLogin()`，成功后才调用 `AuthViewModel.login()`；Host 应用按钮调用 `apply()`。`container == null` 的预览分支传入空的 Host 状态和 no-op 回调。

- [ ] **Step 4: 运行 Compose 和导航测试确认通过**

运行：

```bash
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:connectedDebugAndroidTest --tests 'com.pointquest.android.feature.auth.AuthScreenTest'
./gradlew :app:connectedDebugAndroidTest --tests 'com.pointquest.android.AppNavigationTest'
```

若设备不可用，至少完成编译，并明确区分“编译通过”和“设备测试通过”。

- [ ] **Step 5: 提交登录 UI 变更**

```bash
git add app/src/main/java/com/pointquest/android/feature/auth/LoginScreen.kt \
  app/src/main/java/com/pointquest/android/app/AppNavHost.kt \
  app/src/androidTest/java/com/pointquest/android/feature/auth/AuthScreenTest.kt \
  app/src/androidTest/java/com/pointquest/android/AppNavigationTest.kt
git commit -m "在登录页增加远端 Host 设置"
```

### Task 6: Debug/Release 网络边界、文档与全量验证

**Files:**
- Modify: `app/src/debug/res/xml/network_security_config.xml`
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `docs/api-integration-notes.md`

- [ ] **Step 1: 更新失败的网络安全校验预期**

先将 `verifyNetworkSecurityConfig` 的 Debug 断言改为期望基础 `cleartextTrafficPermitted=true` 且不要求固定 `10.0.2.2` 域名；运行任务确认资源尚未同步时失败。

- [ ] **Step 2: 放宽 Debug、保持 Release 安全边界**

将 Debug 网络安全 XML 改为允许开发环境明文；主配置和 Release 仍为 `cleartextTrafficPermitted=false`。不要修改 Release Host 构建期 HTTPS 校验或主 Manifest 的禁止明文声明。

- [ ] **Step 3: 更新 README 和 API 接入说明**

说明 Debug 可在登录页配置任意合法开发 HTTP 根地址，Release 只接受 HTTPS；说明 Host 不应包含 `/api/v1`，并明确商品图片跟随当前 API Origin。

- [ ] **Step 4: 运行构建边界验证**

```bash
./gradlew verifyReleaseApiBaseUrlValidation verifyNetworkSecurityConfig \
  -PpointApiBaseUrl=https://api.example.invalid/
```

预期 Release HTTPS 校验和 Debug/Release 网络安全边界均通过。

- [ ] **Step 5: 运行 JVM 全量测试**

```bash
./gradlew :app:testDebugUnitTest
```

预期所有 JVM 单元测试通过。

- [ ] **Step 6: 编译 Android 测试和 Debug APK**

```bash
./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

预期 Android 测试源码和 Debug APK 均成功生成；没有可用设备时不声称 instrumentation 已通过。

- [ ] **Step 7: 运行 Debug Lint**

```bash
./gradlew :app:lintDebug
```

修复本次改动引入的警告后重新运行，确认无新增 lint 错误。

- [ ] **Step 8: 检查变更和提交最终文档**

运行 `git diff --check`、`git status --short`，确认只包含 Host 功能相关文件；再提交：

```bash
git add app/src/debug/res/xml/network_security_config.xml app/build.gradle.kts \
  README.md docs/api-integration-notes.md
git commit -m "完善远端 Host 的网络安全边界与文档"
```

## 完成检查

- [ ] 规格中的所有验收标准均有对应测试或构建验证。
- [ ] 登录页首次显示默认/持久 Host，非法输入不覆盖旧值。
- [ ] 下一次登录、注册和商品图片请求使用最新 Host。
- [ ] Debug HTTP 可用，Release HTTP 被拒绝且明文流量仍禁止。
- [ ] JVM 测试、Android 测试编译、Debug APK 和 Lint 的实际结果已记录。
