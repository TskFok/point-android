# Point Android 学生端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从空目录构建可运行、可测试的 Point Quest 原生 Android 学生端，完整覆盖注册、登录、会话恢复、练习、错题、积分、商城兑换和订单。

**Architecture:** 单 `app` 模块，依赖方向固定为 Compose → ViewModel → Repository → Core Auth/Network → OpenAPI 生成客户端。OpenAPI 生成代码只存在于 `build` 目录；Access Token 只在内存中，Refresh Token 使用 Android Keystore 加密并原子写入不可备份文件。

**Tech Stack:** Kotlin 2.1.20、Jetpack Compose、Material 3、Navigation Compose、AGP 8.9.2、Gradle 8.11.1、OpenAPI Generator 7.24.0、Retrofit、OkHttp、Moshi、Coroutines、Coil、JUnit、Turbine、MockWebServer。

## Global Constraints

- 始终在当前 `master` 分支修改，除非用户明确要求，否则不得新建分支。
- Git 提交信息必须使用简体中文。
- 禁止在循环遍历中查询 SQL；本项目不引入 Room 或其他业务数据库。
- Application ID 固定为 `com.pointquest.android`。
- `minSdk = 26`、`compileSdk = 35`、`targetSdk = 35`。
- 使用 AGP `8.9.2`、Gradle `8.11.1`、JDK `17`、Kotlin `2.1.20`、OpenAPI Generator `7.24.0`。
- 只实现学生端，不调用或暴露 `/api/v1/admin/*`。
- 唯一契约源为 `/Users/ushopal/workspace/myself/point/openapi/openapi.json`。
- Debug API Base URL 为 `http://10.0.2.2:3000/`；Release 必须显式提供 HTTPS Base URL。
- OpenAPI 路径已经包含 `/api/v1`，不得在 Base URL 中重复追加。
- 公开接口不发送 Cookie、CSRF 或 Authorization；受保护接口只发送 Bearer Header。
- Access Token 只保存在进程内；Refresh Token 必须由 Android Keystore AES-256-GCM 密钥保护。
- 成功登录或刷新必须先安全落盘，再发布内存会话。
- 并发刷新合并为一次；一个业务操作最多刷新并重放一次。
- 首次答题、错题重练和商品兑换必须携带并正确复用 `Idempotency-Key`。
- UI 逻辑只按稳定错误 `code` 分支，不解析中文 `message`。
- 失败日志只允许记录 HTTP 状态、错误码和 `requestId`。
- 所有用户可见文案放入 `strings.xml`；主要操作触控区域至少 48dp，状态不能只靠颜色表达。
- 每次生产代码改动必须先有能失败的测试；提交前运行该任务的目标测试。

---

---

## File Structure

### 构建与应用入口

- `settings.gradle.kts`：插件仓库、依赖仓库、Version Catalog 和 `:app`。
- `build.gradle.kts`：根插件版本。
- `gradle/libs.versions.toml`：全部依赖版本。
- `gradle/wrapper/*`、`gradlew`、`gradlew.bat`：Gradle 8.11.1 Wrapper。
- `app/build.gradle.kts`：Android、Compose、OpenAPI、BuildConfig、测试和变体配置。
- `app/src/main/AndroidManifest.xml`：应用、网络权限和入口 Activity。
- `app/src/debug/AndroidManifest.xml`：仅 Debug 允许明文开发流量。
- `app/src/main/res/xml/network_security_config.xml`：Release 禁止明文网络。
- `app/src/main/java/com/pointquest/android/MainActivity.kt`：Compose Activity。
- `app/src/main/java/com/pointquest/android/PointQuestApp.kt`：应用根 Composable。

### Core

- `core/model/*.kt`：用户、练习、积分、商品、订单、分页领域模型。
- `core/network/AppError.kt`：统一错误对象和稳定错误码。
- `core/network/AppResult.kt`：成功/失败结果。
- `core/network/ApiErrorParser.kt`：OpenAPI 错误响应解析。
- `core/network/GeneratedMappers.kt`：生成 DTO → 领域模型。
- `core/network/ApiClients.kt`：公开/受保护 Retrofit 与 OkHttp 客户端。
- `core/network/AuthorizedCallExecutor.kt`：预刷新、401 恢复和单次重放。
- `core/network/RetryExecutor.kt`：GET 与幂等写请求有界重试。
- `core/network/Pagination.kt`：服务端分页状态合并。
- `core/ui/UiErrorMapper.kt`：通用错误、权限错误和 requestId 展示。
- `core/auth/SessionModels.kt`：存储和活动会话模型。
- `core/auth/SessionState.kt`：进程内会话状态。
- `core/auth/SecureSessionStore.kt`：Android Keystore、AES-GCM 和 AtomicFile。
- `core/auth/RefreshCoordinator.kt`：Mutex single-flight 刷新。

### Data

- `data/auth/AuthRepository.kt`：注册、登录、恢复、注销。
- `data/practice/PracticeRepository.kt`：摘要、随机题、首次答题、错题。
- `data/points/PointsRepository.kt`：余额和积分流水。
- `data/products/ProductsRepository.kt`：商品搜索、分页和详情。
- `data/orders/OrdersRepository.kt`：兑换、订单分页和详情。
- `data/gateway/GeneratedStudentGateway.kt`：生成 `DefaultApi` 的唯一业务适配器。

### UI

- `app/AppContainer.kt`：手工依赖注入。
- `app/PointQuestApplication.kt`：进程级 AppContainer 所有者。
- `app/Routes.kt`、`app/AppNavHost.kt`：根路由和四栏导航。
- `core/ui/theme/*`：明快课堂主题。
- `core/ui/components/*`：加载、空态、错误、分页和 Snackbar 容器。
- `feature/auth/*`：登录、注册和 AuthViewModel。
- `feature/home/*`：首页概览。
- `feature/practice/*`：练习中心、答题、结果、错题。
- `feature/shop/*`：商城列表、详情和兑换确认。
- `feature/orders/*`：订单列表和详情。
- `feature/points/*`：积分流水。
- `feature/profile/*`：我的和退出登录。

---

### Task 1: Gradle、Compose 与 OpenAPI 项目骨架

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_product_placeholder.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Create: `app/src/main/java/com/pointquest/android/MainActivity.kt`
- Create: `app/src/main/java/com/pointquest/android/PointQuestApp.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/theme/Color.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/pointquest/android/BuildConfigTest.kt`
- Test: `app/src/test/java/com/pointquest/android/GeneratedContractSmokeTest.kt`

**Interfaces:**
- Consumes: `../point/openapi/openapi.json` or Gradle property `pointOpenApiSpec`.
- Produces: `BuildConfig.API_BASE_URL: String`、`BuildConfig.IMAGE_BASE_URL: String`、`@Composable fun PointQuestApp()`、生成的 `com.pointquest.android.generated.api.DefaultApi`。

- [ ] **Step 1: 创建 Gradle 配置和官方 Wrapper**

先写根构建、Version Catalog 和最小 `app` 配置。使用 JDK 17 下载官方 Gradle 8.11.1 分发包并生成 Wrapper：

```bash
curl -L --fail --output /tmp/point-gradle-8.11.1-bin.zip https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
ditto -x -k /tmp/point-gradle-8.11.1-bin.zip /tmp/point-gradle-8.11.1
JAVA_HOME=$(/usr/libexec/java_home -v 17) /tmp/point-gradle-8.11.1/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

`libs.versions.toml` 固定：Compose BOM `2025.05.01`（Compose 1.8.2 / Material 3 1.3.2）、Core KTX `1.16.0`、Activity Compose `1.10.1`、Lifecycle `2.9.1`、Navigation Compose `2.9.0`、Kotlinx Serialization `1.8.1`、Retrofit `2.11.0`、OkHttp `4.12.0`、Moshi `1.15.2`、Coroutines `1.10.2`、Coil `3.2.0`、Turbine `1.2.1`、JUnit `4.13.2`。该 BOM 已通过 Google Maven 元数据验证，并保持 API 35 构建线。Retrofit 依赖必须包含 `converter-moshi` 和 `converter-scalars`，OkHttp 必须包含 `logging-interceptor` 以满足生成代码的编译引用，但生产客户端绝不启用该拦截器。根构建同时应用 `org.jetbrains.kotlin.plugin.serialization` `2.1.20`，供类型安全 Navigation Compose 路由使用；OpenAPI 模型仍使用 Moshi。

- [ ] **Step 2: 配置 OpenAPI 校验与生成**

在 `app/build.gradle.kts` 应用 `org.openapi.generator`，配置：

```kotlin
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(providers.gradleProperty("pointOpenApiSpec")
        .orElse("${rootDir}/../point/openapi/openapi.json").get())
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.pointquest.android.generated.api")
    modelPackage.set("com.pointquest.android.generated.model")
    packageName.set("com.pointquest.android.generated")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf(
        "sourceFolder" to "src/main/kotlin",
        "serializationLibrary" to "moshi",
        "useCoroutines" to "true",
        "useResponseAsReturnType" to "true",
        "dateLibrary" to "java8",
        "enumPropertyNaming" to "UPPERCASE",
        "modelMutable" to "false",
        "omitGradleWrapper" to "true",
        "omitGradlePluginVersions" to "true",
    ))
}
```

将 `build/generated/openapi/src/main/kotlin` 加入主 source set，让 Kotlin 编译依赖 `openApiValidate` 和 `openApiGenerate`。`.gitignore` 忽略 `.gradle/`、`**/build/`、`local.properties`、`.idea/` 和 `.superpowers/`。

- [ ] **Step 3: 写入会失败的 BuildConfig 测试**

```kotlin
class BuildConfigTest {
    @Test fun debugBaseUrlUsesServiceRootWithoutVersionPath() {
        assertEquals("http://10.0.2.2:3000/", BuildConfig.API_BASE_URL)
        assertFalse(BuildConfig.API_BASE_URL.contains("/api/v1"))
    }
}
```

- [ ] **Step 4: 运行测试并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.BuildConfigTest`

Expected: FAIL，因为 `API_BASE_URL` 尚未在 `BuildConfig` 中定义。

- [ ] **Step 5: 实现变体配置与最小 Compose 入口**

Debug 定义 `API_BASE_URL = "http://10.0.2.2:3000/"`；Release 从 `pointApiBaseUrl` 读取。创建 `validateReleaseApiBaseUrl` 任务，只挂到 `preReleaseBuild`，在属性缺失或非 HTTPS 时抛出 `GradleException`，确保 Debug 构建和 Gradle Sync 不需要生产地址。`IMAGE_BASE_URL` 与 API Origin 相同。主 Manifest 禁止明文流量，Debug Manifest 仅为开发变体启用明文流量。

```kotlin
@Composable
fun PointQuestApp() {
    PointQuestTheme { Text(stringResource(R.string.app_name)) }
}
```

- [ ] **Step 6: 写生成契约烟雾测试并运行校验**

```kotlin
class GeneratedContractSmokeTest {
    @Test fun generatedStudentContractIsOnClasspath() {
        assertEquals("DefaultApi", DefaultApi::class.java.simpleName)
        assertNotNull(TokenResponseDto::class.java)
        assertNotNull(AuthRefresh201Response::class.java)
        assertNotNull(PracticeSummaryDto::class.java)
    }
}
```

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew openApiValidate openApiGenerate
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.*'
```

Expected: PASS。

- [ ] **Step 7: 提交骨架**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "初始化学生端安卓项目"
```

---

### Task 2: 领域模型、错误解析与生成 DTO 映射

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/model/User.kt`
- Create: `app/src/main/java/com/pointquest/android/core/model/PracticeModels.kt`
- Create: `app/src/main/java/com/pointquest/android/core/model/ProductModels.kt`
- Create: `app/src/main/java/com/pointquest/android/core/model/OrderModels.kt`
- Create: `app/src/main/java/com/pointquest/android/core/model/PointModels.kt`
- Create: `app/src/main/java/com/pointquest/android/core/model/Page.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/AppError.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/AppResult.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/ApiErrorParser.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/GeneratedMappers.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/ApiErrorParserTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/GeneratedMappersTest.kt`

**Interfaces:**
- Consumes: OpenAPI 生成 DTO。
- Produces: `sealed interface AppResult<out T>`、`data class AppError`、`fun <T> Response<T>.toAppResult(mapper: (T) -> R): AppResult<R>` 以及全部领域模型。

- [ ] **Step 1: 写错误解析失败测试**

```kotlin
@Test fun parsesStableErrorPayload() {
    val response = Response.error<Unit>(409,
        """{"code":"INSUFFICIENT_POINTS","message":"积分不足","requestId":"req_1","details":{"balance":7}}"""
            .toResponseBody("application/json".toMediaType()))
    val error = ApiErrorParser(Moshi.Builder().build()).parse(response)
    assertEquals("INSUFFICIENT_POINTS", error.code)
    assertEquals("req_1", error.requestId)
    assertEquals(7.0, error.details["balance"])
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.core.network.ApiErrorParserTest`

Expected: FAIL，因为解析器和 `AppError` 不存在。

- [ ] **Step 3: 实现结果、错误和领域模型**

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

data class AppError(
    val httpStatus: Int?,
    val code: String,
    val message: String,
    val requestId: String?,
    val details: Map<String, Any?> = emptyMap(),
    val cause: Throwable? = null,
)
```

网络异常使用 `NETWORK_ERROR`，5xx 无合法 body 时使用 `SERVER_ERROR`，取消异常必须继续抛出。领域时间统一为 `Instant`，枚举对未知服务端值保留 `UNKNOWN` 回退。

- [ ] **Step 4: 写 DTO 映射失败测试并实现映射**

测试必须覆盖 `TokenResponseDto → TokenBundle`、`AuthRefresh201Response → TokenBundle`、题目选项按 `position` 排序、订单三种状态、积分三种类型以及 `PageMetaDto` 全字段。映射入口：

```kotlin
fun TokenResponseDto.toDomain(now: Instant): TokenBundle
fun AuthRefresh201Response.toDomain(now: Instant): TokenBundle
fun LearnerQuestionDto.toDomain(): Question
fun AnswerResultDto.toDomain(): AnswerResult
fun ProductDto.toDomain(): Product
fun OrderDto.toDomain(): Order
fun PointLedgerDto.toDomain(): PointLedgerEntry
fun PageMetaDto.toDomain(): PageMeta
```

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.network.*'`

Expected: PASS。

- [ ] **Step 5: 提交模型和解析器**

```bash
git add app/src/main/java/com/pointquest/android/core app/src/test/java/com/pointquest/android/core
git commit -m "实现接口模型与错误解析"
```

---

### Task 3: Android Keystore 安全会话存储

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/auth/SessionModels.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/SessionStore.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/SessionState.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/AndroidKeystoreCipher.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/SecureSessionStore.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/SessionManager.kt`
- Create: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/pointquest/android/core/auth/SessionManagerTest.kt`
- Test: `app/src/androidTest/java/com/pointquest/android/core/auth/SecureSessionStoreTest.kt`

**Interfaces:**
- Consumes: `TokenBundle`、`Clock`。
- Produces: `StoredRefreshSession`、`ActiveSession`、`SessionStore`、`SessionState`、`SessionManager.install(bundle)`、`SessionManager.clear()`。

- [ ] **Step 1: 写原子发布顺序失败测试**

```kotlin
@Test fun storeFailureNeverPublishesAccessToken() = runTest {
    val store = FakeSessionStore(writeError = IOException("disk"))
    val state = SessionState()
    val manager = SessionManager(store, state)
    val result = manager.install(sampleTokenBundle())
    assertTrue(result is AppResult.Failure)
    assertNull(state.active.value)
    assertTrue(store.clearCalled)
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.core.auth.SessionManagerTest`

Expected: FAIL，因为会话类型不存在。

- [ ] **Step 3: 实现会话接口与内存状态**

```kotlin
data class StoredRefreshSession(val refreshToken: String, val expiresAt: Instant)
data class ActiveSession(
    val user: User,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val generation: Long,
)
interface SessionStore {
    suspend fun read(): StoredRefreshSession?
    suspend fun write(value: StoredRefreshSession)
    suspend fun clear()
}
```

`SessionManager.install` 先调用 `store.write`，成功后 generation 加一并更新 StateFlow；失败时清空 Store 和内存。

- [ ] **Step 4: 实现 Android Keystore + AES-GCM + AtomicFile**

使用 key alias `point_refresh_token_v1`、`AES/GCM/NoPadding`、256 位 Key、每次 12 字节随机 IV 和 128 位认证标签。会话明文由 Moshi 序列化；文件 envelope 包含版本号、Base64 IV 和 Base64 ciphertext。文件位于 `noBackupFilesDir/point_session_v1.json`。

`backup_rules.xml` 排除整个 no-backup 会话路径；Manifest 同时配置 Android 12+ data extraction rules 和旧版 full backup rules。

- [ ] **Step 5: 写设备加解密测试**

```kotlin
@Test fun writeReplacesRefreshTokenAndClearRemovesCiphertext() = runTest {
    store.write(StoredRefreshSession("old", Instant.parse("2030-01-01T00:00:00Z")))
    store.write(StoredRefreshSession("new", Instant.parse("2030-02-01T00:00:00Z")))
    assertEquals("new", store.read()!!.refreshToken)
    store.clear()
    assertNull(store.read())
}
```

Run JVM tests now；设备测试只在 `adb devices` 有设备时运行。

- [ ] **Step 6: 提交安全会话**

```bash
git add app/src/main/java/com/pointquest/android/core/auth app/src/main/res/xml app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "实现安全会话存储"
```

---

### Task 4: API 客户端、登录刷新与 AuthorizedCallExecutor

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/network/ApiClients.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/BearerInterceptor.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/AuthorizedCallExecutor.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/RefreshCoordinator.kt`
- Create: `app/src/main/java/com/pointquest/android/data/gateway/PublicAuthGateway.kt`
- Create: `app/src/main/java/com/pointquest/android/data/gateway/GeneratedPublicAuthGateway.kt`
- Create: `app/src/main/java/com/pointquest/android/data/auth/AuthRepository.kt`
- Test: `app/src/test/java/com/pointquest/android/core/auth/RefreshCoordinatorTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/AuthorizedCallExecutorTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/auth/AuthRepositoryTest.kt`

**Interfaces:**
- Consumes: OpenAPI `DefaultApi`、`SessionManager`、`SessionState`、`ApiErrorParser`。
- Produces: `PublicAuthGateway`、`AuthRepository`、`RefreshCoordinator.refresh(force, observedGeneration)`、`AuthorizedCallExecutor.execute(call)`。

- [ ] **Step 1: 写并发刷新失败测试**

```kotlin
@Test fun concurrentRefreshesUseOneNetworkCall() = runTest {
    val api = FakePublicAuthGateway(refreshDelayMs = 50)
    val coordinator = refreshCoordinator(api)
    coroutineScope { repeat(20) { launch { coordinator.refresh(force = true, observedGeneration = 1) } } }
    assertEquals(1, api.refreshCalls)
}
```

- [ ] **Step 2: 写 401 最多重放一次失败测试**

```kotlin
@Test fun expiredTokenRefreshesAndReplaysOnce() = runTest {
    var calls = 0
    val result = executor.execute {
        calls++
        AppResult.Failure(expiredTokenError())
    }
    assertTrue(result is AppResult.Failure)
    assertEquals(2, calls)
    assertEquals(1, refreshGateway.refreshCalls)
}
```

- [ ] **Step 3: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.auth.RefreshCoordinatorTest' --tests 'com.pointquest.android.core.network.AuthorizedCallExecutorTest'`

Expected: FAIL，因为协调器和执行器不存在。

- [ ] **Step 4: 实现公开/受保护客户端**

`ApiClients` 创建两个没有 CookieJar 的 OkHttpClient.Builder，统一连接 10 秒、读 20 秒、写 20 秒、整次调用 30 秒。公开客户端不添加任何认证 Header；受保护客户端只使用：

```kotlin
class BearerInterceptor(private val sessionState: SessionState) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionState.active.value?.accessToken
            ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder()
            .header("Authorization", "Bearer $token").build())
    }
}
```

使用生成基础设施的精确构造方式创建两个客户端：

```kotlin
fun defaultApi(baseUrl: String, builder: OkHttpClient.Builder): DefaultApi =
    ApiClient(
        baseUrl = baseUrl,
        okHttpClientBuilder = builder,
        serializerBuilder = Serializer.moshiBuilder,
    ).createService(DefaultApi::class.java)
```

公开与受保护 `DefaultApi` 使用相同 Base URL 和不同 Builder。永远不使用生成 `ApiClient` 的默认 Builder，因为默认实现包含 BODY 日志拦截器。

- [ ] **Step 5: 实现刷新协调器与受保护调用执行器**

`RefreshCoordinator` 在 Mutex 内再次比较 generation；若已有更新直接返回。Access Token 距过期少于等于 30 秒时预刷新。刷新网络失败、响应丢失、稳定认证错误或安全写入失败均清会话，不使用旧 Refresh Token重试。

`AuthorizedCallExecutor.execute` 在调用前确保有效 Token；只对 `401 + AUTH_TOKEN_EXPIRED` 强制刷新并重放一次，其他 401 直接返回。

- [ ] **Step 6: 实现 AuthRepository 并测试**

```kotlin
interface AuthRepository {
    suspend fun register(username: String, password: String): AppResult<User>
    suspend fun login(username: String, password: String): AppResult<User>
    suspend fun restore(): AppResult<User>
    suspend fun logout()
}
```

注册不建立会话；登录和刷新先落盘后发布；退出用当前 Refresh Token best-effort 调用公开 logout 后无条件清本地。若登录响应用户角色不是 `STUDENT`，立即清除新会话并返回 `FORBIDDEN`。测试 `AUTH_INVALID_CREDENTIALS`、管理员账号、写存储失败、过期本地 Refresh Token 和注销服务端失败。

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.auth.*' --tests 'com.pointquest.android.core.network.AuthorizedCallExecutorTest' --tests 'com.pointquest.android.data.auth.*'`

Expected: PASS。

- [ ] **Step 7: 提交鉴权网络层**

```bash
git add app/src/main/java/com/pointquest/android/core app/src/main/java/com/pointquest/android/data app/src/test
git commit -m "实现登录刷新与鉴权恢复"
```

---

---

### Task 5: 有界重试与服务端分页基础设施

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/network/RetryPolicy.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/RetryExecutor.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/IdempotentOperation.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/Pagination.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/RetryExecutorTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/PaginationTest.kt`

**Interfaces:**
- Consumes: `AppResult`、`AppError`、可注入 `DelayProvider`、`JitterSource` 和 `IdempotencyKeyFactory`。
- Produces: `RetryExecutor.executeRead`、`RetryExecutor.executeIdempotent`、`IdempotentOperation<T>`、`PagedState<T>.merge(page)`。

- [ ] **Step 1: 写幂等重试失败测试**

```kotlin
@Test fun retriesReuseSameKeyAndPayloadInstance() = runTest {
    val payload = mapOf("selectedOptionId" to "o2")
    val seen = mutableListOf<Pair<String, Any>>()
    val result = executor.executeIdempotent(payload) { operation ->
        seen += operation.key to operation.payload
        if (seen.size < 3) concurrentModification() else AppResult.Success("ok")
    }
    assertEquals("ok", (result as AppResult.Success).value)
    assertEquals(1, seen.map { it.first }.distinct().size)
    assertTrue(seen.all { it.second === payload })
    assertEquals(listOf(250L, 500L), delayProvider.delays)
}
```

- [ ] **Step 2: 写不应重试失败测试**

覆盖 `IDEMPOTENCY_CONFLICT`、未知 4xx、协程取消和刷新端点。协程取消必须重新抛出，不能包装成 `NETWORK_ERROR`。

- [ ] **Step 3: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.core.network.RetryExecutorTest`

Expected: FAIL，因为重试执行器不存在。

- [ ] **Step 4: 实现重试策略**

```kotlin
data class IdempotentOperation<T>(val key: String, val payload: T)

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val baseDelaysMs: List<Long> = listOf(250, 500),
    val maxJitterMs: Long = 100,
)
```

GET 只在网络 I/O 和 5xx 重试；幂等写额外对 `CONCURRENT_MODIFICATION` 重试。每次延迟为 base + 0–100ms 抖动。三次失败后返回最后错误。

- [ ] **Step 5: 写分页失败测试**

```kotlin
@Test fun mergeUsesServerMetaDeduplicatesAndFallsBackFromOutOfRangePage() {
    val state = PagedState(items = listOf(item("1")), meta = meta(page = 1, totalPages = 2))
    val merged = state.merge(page(items = listOf(item("1"), item("2")), meta = meta(page = 2, totalPages = 2))) { it.id }
    assertEquals(listOf("1", "2"), merged.items.map { it.id })
    assertFalse(merged.canLoadMore)
    assertEquals(2, merged.meta.page)
}
```

实现 `PagedState`、`Page<T>` 和越界结果 `PageAdjustment.Reload(lastValidPage)`；`totalPages=0` 产生空态。

- [ ] **Step 6: 运行测试并提交**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.network.*'`

Expected: PASS。

```bash
git add app/src/main/java/com/pointquest/android/core/network app/src/test/java/com/pointquest/android/core/network
git commit -m "实现网络重试与分页基础设施"
```

---

### Task 6: 练习与积分数据仓库

**Files:**
- Create: `app/src/main/java/com/pointquest/android/data/gateway/StudentGateway.kt`
- Create: `app/src/main/java/com/pointquest/android/data/gateway/GeneratedStudentGateway.kt`
- Create: `app/src/main/java/com/pointquest/android/data/practice/PracticeRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/practice/DefaultPracticeRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/points/PointsRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/points/DefaultPointsRepository.kt`
- Test: `app/src/test/java/com/pointquest/android/data/practice/PracticeRepositoryTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/points/PointsRepositoryTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/gateway/GeneratedStudentGatewayTest.kt`

**Interfaces:**
- Consumes: 受保护 `DefaultApi`、`AuthorizedCallExecutor`、`RetryExecutor`、DTO 映射。
- Produces: `StudentGateway`、`PracticeRepository`、`PointsRepository`。

- [ ] **Step 1: 写答题仓库失败测试**

```kotlin
@Test fun firstAnswerUsesFrozenIdempotentOperation() = runTest {
    val result = repository.answerFirst(questionId = "q1", selectedOptionId = "o2")
    assertTrue(result is AppResult.Success)
    assertEquals(1, gateway.answerCalls.size)
    assertTrue(UUID.fromString(gateway.answerCalls.single().idempotencyKey) != null)
    assertEquals("o2", gateway.answerCalls.single().selectedOptionId)
}
```

另写 `QUESTION_ALREADY_ANSWERED`、`NO_UNANSWERED_QUESTIONS`、错题分页和 `QUESTION_ALREADY_MASTERED` 透传测试。

- [ ] **Step 2: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.practice.*'`

Expected: FAIL，因为仓库不存在。

- [ ] **Step 3: 定义 StudentGateway**

```kotlin
interface StudentGateway {
    suspend fun practiceSummary(): AppResult<PracticeSummary>
    suspend fun randomQuestion(excludeIds: List<String>): AppResult<Question>
    suspend fun answerFirst(questionId: String, optionId: String, key: String): AppResult<AnswerResult>
    suspend fun wrongQuestions(page: Int, pageSize: Int): AppResult<Page<WrongQuestion>>
    suspend fun answerWrong(questionId: String, optionId: String, key: String): AppResult<AnswerResult>
    suspend fun pointBalance(): AppResult<Int>
    suspend fun pointLedger(page: Int, pageSize: Int): AppResult<Page<PointLedgerEntry>>
    suspend fun products(search: String?, page: Int, pageSize: Int): AppResult<Page<Product>>
    suspend fun product(id: String): AppResult<Product>
    suspend fun createOrder(productId: String, key: String): AppResult<Order>
    suspend fun orders(page: Int, pageSize: Int): AppResult<Page<Order>>
    suspend fun order(id: String): AppResult<Order>
}
```

`GeneratedStudentGateway` 是唯一调用生成 `DefaultApi` 的业务文件。分别调用 operationId：`practiceGetSummary`、`practiceGetRandomQuestion`、`practiceAnswerQuestion`、`practiceListWrongQuestions`、`practiceRetryWrongQuestion`、`pointsGetBalance`、`pointsListLedger`、`productsList`、`productsGet`、`ordersCreate`、`ordersList`、`ordersGet`。

- [ ] **Step 4: 实现 PracticeRepository**

```kotlin
interface PracticeRepository {
    suspend fun summary(): AppResult<PracticeSummary>
    suspend fun nextQuestion(excludeIds: List<String>): AppResult<Question>
    suspend fun answerFirst(questionId: String, selectedOptionId: String): AppResult<AnswerResult>
    suspend fun wrongQuestions(page: Int): AppResult<Page<WrongQuestion>>
    suspend fun answerWrong(questionId: String, selectedOptionId: String): AppResult<AnswerResult>
}
```

两个 answer 方法通过 `RetryExecutor.executeIdempotent` 创建不可变请求并复用 key。`excludeIds` 过滤重复并截断为最近 50 个，再以逗号传给生成 API。

- [ ] **Step 5: 实现 PointsRepository 并测试**

```kotlin
interface PointsRepository {
    suspend fun balance(): AppResult<Int>
    suspend fun ledger(page: Int): AppResult<Page<PointLedgerEntry>>
}
```

固定 `pageSize=20`，读取请求使用 `executeRead`。MockWebServer 契约测试验证 Bearer Header 存在，且没有 Cookie 和 `X-CSRF-Token`。

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.practice.*' --tests 'com.pointquest.android.data.points.*' --tests 'com.pointquest.android.data.gateway.*'`

Expected: PASS。

- [ ] **Step 6: 提交练习和积分数据层**

```bash
git add app/src/main/java/com/pointquest/android/data app/src/test/java/com/pointquest/android/data
git commit -m "实现练习与积分数据仓库"
```

---

### Task 7: 商品兑换与订单数据仓库

**Files:**
- Create: `app/src/main/java/com/pointquest/android/data/products/ProductsRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/products/DefaultProductsRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/products/ProductImageUrlFactory.kt`
- Create: `app/src/main/java/com/pointquest/android/data/orders/OrdersRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/orders/DefaultOrdersRepository.kt`
- Test: `app/src/test/java/com/pointquest/android/data/products/ProductsRepositoryTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/products/ProductImageUrlFactoryTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/orders/OrdersRepositoryTest.kt`

**Interfaces:**
- Consumes: `StudentGateway`、`RetryExecutor`、`BuildConfig.IMAGE_BASE_URL`。
- Produces: `ProductsRepository`、`OrdersRepository`、`ProductImageUrlFactory.urlOrNull(imageKey)`。

- [ ] **Step 1: 写商品图片安全测试**

```kotlin
@Test fun acceptsOnlyUuidProductImageKeys() {
    val factory = ProductImageUrlFactory("http://10.0.2.2:3000/")
    assertEquals(
        "http://10.0.2.2:3000/uploads/products/550e8400-e29b-41d4-a716-446655440000.png",
        factory.urlOrNull("products/550e8400-e29b-41d4-a716-446655440000.png"),
    )
    assertNull(factory.urlOrNull("https://evil.test/a.png"))
    assertNull(factory.urlOrNull("products/../secret.png"))
    assertNull(factory.urlOrNull("seed/products/demo.png"))
}
```

- [ ] **Step 2: 写兑换幂等失败测试**

```kotlin
@Test fun checkoutRetriesConcurrentModificationWithSameKey() = runTest {
    val result = repository.redeem("p1")
    assertTrue(result is AppResult.Success)
    assertEquals(2, gateway.createOrderCalls.size)
    assertEquals(1, gateway.createOrderCalls.map { it.key }.distinct().size)
    assertTrue(gateway.createOrderCalls.all { it.productId == "p1" })
}
```

- [ ] **Step 3: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.products.*' --tests 'com.pointquest.android.data.orders.*'`

Expected: FAIL，因为仓库和 URL 工厂不存在。

- [ ] **Step 4: 实现商品和订单接口**

```kotlin
interface ProductsRepository {
    suspend fun page(search: String?, page: Int): AppResult<Page<Product>>
    suspend fun detail(id: String): AppResult<Product>
}

interface OrdersRepository {
    suspend fun redeem(productId: String): AppResult<Order>
    suspend fun page(page: Int): AppResult<Page<Order>>
    suspend fun detail(id: String): AppResult<Order>
}
```

商品查询固定 `isActive=true`、`pageSize=20`；空白搜索转成 null。订单创建走幂等执行器；订单读取走 GET 重试。图片 URL 先通过正则和 `UUID.fromString` 校验，再用 `HttpUrl.resolve("uploads/$imageKey")`，禁止字符串直接拼接任意 Origin。

- [ ] **Step 5: 覆盖稳定业务错误并运行测试**

测试 `INSUFFICIENT_POINTS`、`OUT_OF_STOCK`、`PRODUCT_INACTIVE`、`IDEMPOTENCY_CONFLICT` 和订单三种状态不被误映射。

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.products.*' --tests 'com.pointquest.android.data.orders.*'`

Expected: PASS。

- [ ] **Step 6: 提交商城数据层**

```bash
git add app/src/main/java/com/pointquest/android/data app/src/test/java/com/pointquest/android/data
git commit -m "实现商品兑换与订单仓库"
```

---

### Task 8: 应用容器、主题、共享状态组件与根导航

**Files:**
- Create: `app/src/main/java/com/pointquest/android/app/AppContainer.kt`
- Create: `app/src/main/java/com/pointquest/android/app/PointQuestApplication.kt`
- Create: `app/src/main/java/com/pointquest/android/app/Routes.kt`
- Create: `app/src/main/java/com/pointquest/android/app/RootDestinationResolver.kt`
- Create: `app/src/main/java/com/pointquest/android/app/AppNavHost.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/theme/Type.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/components/AsyncContent.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/components/PointScaffold.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/components/PagedListFooter.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/UiErrorMapper.kt`
- Create: `app/src/main/java/com/pointquest/android/core/ui/ViewModelFactory.kt`
- Modify: `app/src/main/java/com/pointquest/android/PointQuestApp.kt`
- Test: `app/src/test/java/com/pointquest/android/app/RootDestinationResolverTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/ui/UiErrorMapperTest.kt`
- Test: `app/src/androidTest/java/com/pointquest/android/core/ui/ThemeAccessibilityTest.kt`

**Interfaces:**
- Consumes: 全部 Core 与 Repository。
- Produces: `AppContainer`、`AppRoute`、`RootDestinationResolver.resolve(sessionStatus)`、`AppNavHost`、通用 UI 组件。

- [ ] **Step 1: 写根路由失败测试**

```kotlin
@Test fun resolvingSessionRoutesNeverShowsMainBeforeRestoreCompletes() {
    assertEquals(AppRoute.Splash, resolver.resolve(SessionStatus.Restoring))
    assertEquals(AppRoute.Login, resolver.resolve(SessionStatus.SignedOut))
    assertEquals(AppRoute.Home, resolver.resolve(SessionStatus.SignedIn(sampleUser())))
}
```

- [ ] **Step 2: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.app.RootDestinationResolverTest`

Expected: FAIL，因为根路由不存在。

- [ ] **Step 3: 实现 AppContainer 与路由**

`PointQuestApplication` 在 `onCreate` 中构建唯一 `AppContainer`；MainActivity 从 Application 取得容器。`AppContainer` 使用 Application Context 单例构建 Moshi、两个 OkHttpClient、两个生成 API、SessionStore、SessionManager、RefreshCoordinator、AuthorizedCallExecutor、RetryExecutor 和所有 Repository。

```kotlin
@Serializable
enum class PracticeMode { FIRST, WRONG }

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Splash : AppRoute
    @Serializable
    data object Login : AppRoute
    @Serializable
    data object Register : AppRoute
    @Serializable
    data object Home : AppRoute
    @Serializable
    data object Practice : AppRoute
    @Serializable
    data object Shop : AppRoute
    @Serializable
    data object Profile : AppRoute
    @Serializable
    data class Question(val mode: PracticeMode, val questionId: String?) : AppRoute
    @Serializable
    data object WrongQuestions : AppRoute
    @Serializable
    data class ProductDetail(val productId: String) : AppRoute
    @Serializable
    data object Orders : AppRoute
    @Serializable
    data class OrderDetail(val orderId: String) : AppRoute
    @Serializable
    data object Points : AppRoute
}
```

根导航观察 `SessionStatus`：会话失效时清空 back stack 到 Login；登录成功清空认证栈进入 Home。四个底栏目标使用 `launchSingleTop`、`saveState`、`restoreState`。

- [ ] **Step 4: 实现主题和共享组件**

主题颜色使用 `#1677FF`、`#FFC83D`、`#F7FBFF`、`#FFFFFF`、`#16304A`、`#238B57`、`#D64545`；卡片 16dp 圆角，按钮最小 48dp。`AsyncContent` 明确 loading/content/empty/error；`PagedListFooter` 明确 idle/loading/error/end。`UiErrorMapper` 对 `FORBIDDEN` 显示无权限文案，对未知错误显示服务端 message，并在 requestId 非空时附加“请求 ID：...”以便排障。

- [ ] **Step 5: 验证主题与根导航**

设备测试检查主要按钮至少 48dp、错误图标有 contentDescription、大字体下卡片不裁切。无设备时先保证 androidTest 编译：

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.app.*'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS。

- [ ] **Step 6: 提交应用壳**

```bash
git add app/src/main/java/com/pointquest/android/app app/src/main/java/com/pointquest/android/core/ui app/src/main/java/com/pointquest/android/PointQuestApp.kt app/src/test app/src/androidTest
git commit -m "实现应用主题与根导航"
```

---

### Task 9: 登录、注册、首页与个人中心 UI

**Files:**
- Create: `app/src/main/java/com/pointquest/android/feature/auth/AuthUiState.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/auth/LoginScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/auth/RegisterScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/home/HomeUiState.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/home/HomeScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/profile/ProfileViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/profile/ProfileScreen.kt`
- Modify: `app/src/main/java/com/pointquest/android/app/AppNavHost.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/auth/AuthViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/home/HomeViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/profile/ProfileViewModelTest.kt`
- Test: `app/src/androidTest/java/com/pointquest/android/feature/auth/AuthScreenTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`、`PracticeRepository`、`SessionState`、根导航回调。
- Produces: `AuthViewModel`、`HomeViewModel`、`ProfileViewModel` 和四个页面 Composable。

- [ ] **Step 1: 写无效凭据失败测试**

```kotlin
@Test fun invalidCredentialsKeepsUsernameClearsPasswordAndShowsFieldError() = runTest {
    val vm = AuthViewModel(fakeAuth(loginError = error("AUTH_INVALID_CREDENTIALS")))
    vm.updateUsername("student")
    vm.updatePassword("wrong-password1")
    vm.login()
    val state = vm.uiState.value
    assertEquals("student", state.username)
    assertEquals("", state.password)
    assertEquals("用户名或密码不正确", state.passwordError)
}
```

- [ ] **Step 2: 写注册成功和首页失败测试**

注册测试断言用户名规则 `^[a-z0-9_]{3,32}$`、密码至少 10 位且同时包含字母和数字、两次密码一致；成功事件携带用户名并回登录。首页测试断言 summary 成功映射积分、未答题、待练错题和快捷入口；失败后保留可重试状态。

- [ ] **Step 3: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.auth.*' --tests 'com.pointquest.android.feature.home.*' --tests 'com.pointquest.android.feature.profile.*'`

Expected: FAIL，因为 ViewModel 不存在。

- [ ] **Step 4: 实现 AuthViewModel 和认证页面**

```kotlin
data class AuthUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val submitting: Boolean = false,
    val message: String? = null,
)
```

登录和注册提交期间禁用输入与按钮。`VALIDATION_FAILED` 映射字段；注册成功返回登录并预填用户名。密码输入使用 `PasswordVisualTransformation`、正确 IME action 和不泄露内容的 semantics。

- [ ] **Step 5: 实现首页与个人中心**

首页采用已确认的明快课堂布局：欢迎语、蓝色积分卡、黄色开始答题按钮、练习进度、错题/订单/积分快捷卡。个人中心展示用户名、学生身份、余额、订单和积分入口；退出先二次确认，再调用 `logout()`。

- [ ] **Step 6: 运行 ViewModel 与 Compose 编译测试**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.auth.*' --tests 'com.pointquest.android.feature.home.*' --tests 'com.pointquest.android.feature.profile.*'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS。

- [ ] **Step 7: 提交认证和首页**

```bash
git add app/src/main/java/com/pointquest/android/feature app/src/main/java/com/pointquest/android/app/AppNavHost.kt app/src/test app/src/androidTest
git commit -m "实现登录注册与首页"
```

---

### Task 10: 首次答题与错题重练 UI

**Files:**
- Create: `app/src/main/java/com/pointquest/android/feature/practice/PracticeHubScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/QuestionUiState.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/QuestionViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/QuestionScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/AnswerResultCard.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/WrongQuestionsUiState.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/WrongQuestionsViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/WrongQuestionsScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/PracticeDraftStore.kt`
- Modify: `app/src/main/java/com/pointquest/android/app/AppContainer.kt`
- Modify: `app/src/main/java/com/pointquest/android/app/AppNavHost.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/practice/QuestionViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/practice/WrongQuestionsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/pointquest/android/feature/practice/QuestionScreenTest.kt`

**Interfaces:**
- Consumes: `PracticeRepository`、`PracticeDraftStore`、导航回调。
- Produces: 首次答题、结果、无题状态、错题分页和重练页面。

- [ ] **Step 1: 写提交锁定与结果失败测试**

```kotlin
@Test fun submitLocksSelectionAndKeepsAnswerResult() = runTest {
    val vm = questionViewModel(answer = sampleAnswer(correct = false, pointsAwarded = 0))
    vm.loadFirstQuestion()
    vm.selectOption("o2")
    vm.submit()
    val state = vm.uiState.value
    assertEquals("o2", state.selectedOptionId)
    assertTrue(state.submitted)
    assertFalse(state.selectionEnabled)
    assertEquals(false, state.result?.correct)
}
```

- [ ] **Step 2: 写题目排除与错题掌握失败测试**

首次答题连续点击下一题时记录已展示 ID，传给仓库的列表去重且最多 50 个。错题返回正确结果或 `QUESTION_ALREADY_MASTERED` 时，从当前列表移除对应 ID，并使用服务端 meta 修正页面。

- [ ] **Step 3: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.practice.*'`

Expected: FAIL，因为练习 ViewModel 不存在。

- [ ] **Step 4: 实现 QuestionViewModel 和答题页面**

```kotlin
data class QuestionUiState(
    val loading: Boolean = true,
    val question: Question? = null,
    val selectedOptionId: String? = null,
    val submitting: Boolean = false,
    val submitted: Boolean = false,
    val result: AnswerResult? = null,
    val completed: Boolean = false,
    val error: UiError? = null,
) {
    val selectionEnabled get() = !submitting && !submitted
}
```

未选答案时提交按钮禁用。提交后用文字、图标和颜色同时标出正确/错误选项，展示解析、积分、错误次数和余额。`QUESTION_ALREADY_ANSWERED` 自动加载下一题；`NO_UNANSWERED_QUESTIONS` 显示完成卡。

- [ ] **Step 5: 实现错题分页与 PracticeDraftStore**

错题列表行进入沉浸式重练页前，将完整 `WrongQuestion` 放入只驻留内存的 `PracticeDraftStore`。进程恢复时若草稿缺失，返回错题列表并显示“题目已失效，请重新选择”，不伪造题目详情。

- [ ] **Step 6: 运行测试和 Android 测试编译**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.practice.*'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS。

- [ ] **Step 7: 提交练习界面**

```bash
git add app/src/main/java/com/pointquest/android/feature/practice app/src/main/java/com/pointquest/android/app app/src/test app/src/androidTest
git commit -m "实现首次答题与错题重练"
```

---

### Task 11: 商城、订单与积分页面

**Files:**
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductListUiState.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductListViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductListScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductDetailUiState.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductDetailViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductDetailScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderListViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderListScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderDetailViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderDetailScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/points/PointsViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/points/PointsScreen.kt`
- Modify: `app/src/main/java/com/pointquest/android/app/AppNavHost.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/shop/ProductListViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/shop/ProductDetailViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/orders/OrderListViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/points/PointsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/pointquest/android/feature/shop/ProductDetailScreenTest.kt`

**Interfaces:**
- Consumes: `ProductsRepository`、`OrdersRepository`、`PointsRepository`、`ProductImageUrlFactory`。
- Produces: 商品搜索/详情/兑换、订单分页/详情和积分流水页面。

- [ ] **Step 1: 写搜索去抖和分页失败测试**

```kotlin
@Test fun searchDebouncesFor300msAndResetsToFirstPage() = runTest {
    val vm = productListViewModel()
    vm.updateSearch("note")
    advanceTimeBy(299)
    assertEquals(0, repository.pageCalls.size)
    advanceTimeBy(1)
    assertEquals("note" to 1, repository.pageCalls.single())
}
```

分页测试使用服务端 meta、ID 去重、已有内容时加载失败保留旧列表并显示 footer retry。

- [ ] **Step 2: 写兑换错误状态失败测试**

```kotlin
@Test fun outOfStockSetsStockToZeroAndDisablesRedeem() = runTest {
    val vm = productDetailViewModel(redeemError = error("OUT_OF_STOCK"))
    vm.load()
    vm.confirmRedeem()
    assertEquals(0, vm.uiState.value.product?.stock)
    assertFalse(vm.uiState.value.canRedeem)
    assertEquals("商品库存不足", vm.uiState.value.message)
}
```

同时测试 `INSUFFICIENT_POINTS` 使用 `details.balance` 更新余额、`PRODUCT_INACTIVE` 发出返回商城事件、`IDEMPOTENCY_CONFLICT` 停止自动重试并允许生成新操作、成功兑换发出订单详情 ID。订单详情遇到 `ORDER_INVALID_STATUS` 时自动重新读取一次详情，不重放写请求。

- [ ] **Step 3: 运行并确认失败**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.shop.*' --tests 'com.pointquest.android.feature.orders.*' --tests 'com.pointquest.android.feature.points.*'`

Expected: FAIL，因为页面 ViewModel 不存在。

- [ ] **Step 4: 实现商城页面**

商品列表使用 `snapshotFlow`/Flow 去抖 300ms；仅在搜索文本实际变化时请求。Coil 只加载 `ProductImageUrlFactory` 返回的 URL，无效 key 显示内置占位图。详情同时加载商品和当前余额，兑换按钮要求库存大于 0、商品有效且余额足够；点击后弹出二次确认。

- [ ] **Step 5: 实现订单与积分页面**

订单状态文案固定为待领取、已完成、已取消。订单详情展示订单号、商品快照、积分快照、创建时间和状态时间。积分类型固定映射答题奖励、兑换支出、订单退款，并显示带符号 delta、变动后余额和本地化时间。

- [ ] **Step 6: 运行测试和 Android 测试编译**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.shop.*' --tests 'com.pointquest.android.feature.orders.*' --tests 'com.pointquest.android.feature.points.*'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: PASS。

- [ ] **Step 7: 提交商城和积分页面**

```bash
git add app/src/main/java/com/pointquest/android/feature app/src/main/java/com/pointquest/android/app/AppNavHost.kt app/src/test app/src/androidTest
git commit -m "实现商城订单与积分页面"
```

---

### Task 12: 设备集成测试、文档与最终验证

**Files:**
- Create: `app/src/androidTest/java/com/pointquest/android/AppNavigationTest.kt`
- Create: `app/src/androidTest/java/com/pointquest/android/SessionExpiryNavigationTest.kt`
- Create: `app/src/androidTest/java/com/pointquest/android/AccessibilitySmokeTest.kt`
- Create: `README.md`
- Create: `docs/api-integration-notes.md`
- Modify: implementation files only when verification exposes a defect.

**Interfaces:**
- Consumes: 完整应用。
- Produces: 可复现的构建说明、API 接入说明、Debug APK 和验证证据。

- [ ] **Step 1: 写应用导航设备测试**

```kotlin
@Test fun signedOutRegisterLoginAndBottomNavigationFlow() {
    composeRule.onNodeWithText("注册账号").performClick()
    composeRule.onNodeWithText("创建账号").assertIsDisplayed()
    fakeSession.signIn(sampleUser())
    composeRule.onNodeWithText("首页").assertIsDisplayed()
    composeRule.onNodeWithText("练习").performClick()
    composeRule.onNodeWithText("练习中心").assertIsDisplayed()
    composeRule.onNodeWithText("商城").performClick()
    composeRule.onNodeWithText("积分商城").assertIsDisplayed()
}
```

- [ ] **Step 2: 写会话失效和无障碍烟雾测试**

会话失效测试从详情深栈发出 SignedOut，断言只能看到登录且系统返回键不能回到受保护页面。无障碍测试将字体缩放设为 1.5，检查主要操作按钮 48dp、关键图标有 contentDescription、错误不只依赖颜色。

- [ ] **Step 3: 编写中文文档**

`README.md` 包含：JDK 17、SDK 35、服务端启动、`pointOpenApiSpec`、Debug 模拟器地址、Release HTTPS 属性、构建/测试命令和 APK 路径。

`docs/api-integration-notes.md` 精确记录：OpenAPI 生成、Bearer、无 Cookie/CSRF、Keystore、Refresh 轮换、single-flight、401 单次重放、三类幂等写、分页、错误码、图片 URL 和日志规则。

- [ ] **Step 4: 执行完整 JVM 测试与契约验证**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew openApiValidate openApiGenerate
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
```

Expected: 所有任务 PASS，测试报告位于 `app/build/reports/tests/testDebugUnitTest/index.html`。

- [ ] **Step 5: 执行 Lint 和 APK 构建**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:lintDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleRelease -PpointApiBaseUrl=https://api.example.invalid/
```

Expected: PASS；Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。Release 构建只验证配置和代码，不宣称示例域名可用于生产。

- [ ] **Step 6: 在设备存在时执行 Android 测试**

Run:

```bash
adb devices -l
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest
```

Expected: 有设备时 PASS；无设备时不运行第二条命令，并在交付报告明确记录“未连接设备”。

- [ ] **Step 7: 检查安全和契约禁区**

Run:

```bash
rg -n "pq_access|pq_refresh|pq_csrf|X-CSRF-Token|CookieJar|HttpLoggingInterceptor.Level.(BODY|HEADERS)" app/src/main
rg -n "/api/v1/admin" app/src/main
rg -n "SharedPreferences" app/src/main
git status --short
```

Expected: 前三组搜索没有生产代码命中；Git 状态只包含本任务预期文档和测试文件。

- [ ] **Step 8: 提交文档和最终修正**

```bash
git add README.md docs/api-integration-notes.md app
git commit -m "完成学生端安卓应用"
```

---

## Plan Self-Review

- 规格覆盖：12 个任务覆盖构建、契约生成、领域映射、安全存储、登录刷新、single-flight、401 单次重放、重试、幂等、分页、全部学生端仓库、四栏导航、所有页面、设备测试、文档和 APK。
- 范围检查：没有管理端页面、离线数据库、平板专用布局、发布签名或商店发布任务。
- 类型一致性：`AppResult`、`AppError`、领域模型、`SessionStore`、`SessionState`、`StudentGateway` 和 Repository 都在首次使用前定义。
- 安全检查：公开/受保护客户端分离，Refresh 结果不确定时不重试，Access Token 不持久化，Release 强制 HTTPS。
- 验证检查：每个实现任务都有失败测试、目标测试命令和简体中文提交；最终任务包含契约、单测、Lint、Debug/Release 构建和条件设备测试。
