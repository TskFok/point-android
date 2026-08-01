# Point Android Student Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android student app for Point Quest with registration, login, session restore, practice, wrong-question retry, points, products, checkout, and orders.

**Architecture:** Create a single-module Kotlin Android app using Compose, Retrofit/OkHttp/Moshi, coroutines, and Jetpack Security. Keep API DTOs and network behavior in `core/network`, token/session logic in `core/auth`, feature repositories in `data`, and screens/ViewModels in `feature/*`.

**Tech Stack:** Android Gradle Plugin 8.6.1, Kotlin 2.0.20, compileSdk 35, minSdk 26, Jetpack Compose Material 3, Navigation Compose, Retrofit, OkHttp, Moshi, Kotlin Coroutines, AndroidX Security Crypto, JUnit, Turbine, MockWebServer.

## Global Constraints

- Always modify the current branch; do not create a new branch unless the user explicitly asks.
- GitHub commit messages must be in Simplified Chinese.
- Do not query SQL inside loops.
- Student scope only: exclude `/api/v1/admin/*`.
- API contract source: `/Users/ushopal/workspace/myself/point/openapi/openapi.json`.
- Integration guide source: `/Users/ushopal/workspace/myself/point/docs/api/android-integration.md`.
- Default dev base URL: `http://10.0.2.2:3000/api/v1/`.
- Production base URL must use HTTPS.
- Public token endpoints must not use Cookie or CSRF.
- Protected requests must send `Authorization: Bearer <accessToken>`.
- Access token stays in process memory.
- Refresh token is stored only in Android Keystore-backed encrypted storage.
- Successful refresh atomically replaces access token, refresh token, and refresh expiry.
- Concurrent refresh uses single-flight coordination.
- One business request can trigger at most one refresh replay.
- `POST /practice/questions/{questionId}/answer`, `POST /practice/wrong-questions/{questionId}/answer`, and `POST /orders` must send `Idempotency-Key`.
- Retries for timeout or `CONCURRENT_MODIFICATION` must reuse the same idempotency key and request body.
- UI logic branches on stable error `code`, not Chinese `message`.
- Logs may include only HTTP status, error code, and `requestId`.

---

## File Structure

- Create `settings.gradle.kts`: Gradle plugin management and module include.
- Create `build.gradle.kts`: root plugin versions locked to AGP 8.6.1 and Kotlin 2.0.20.
- Create `gradle.properties`: AndroidX, Kotlin, JVM, and Compose build flags.
- Create `app/build.gradle.kts`: Android app module, dependencies, test configuration, `BuildConfig.API_BASE_URL`.
- Create `app/src/main/AndroidManifest.xml`: application declaration and internet permission.
- Create `app/src/main/java/com/pointquest/android/MainActivity.kt`: Compose entrypoint.
- Create `app/src/main/java/com/pointquest/android/PointQuestApp.kt`: app-level composition, nav host, dependency wiring.
- Create `app/src/main/java/com/pointquest/android/core/network/*.kt`: DTOs, Retrofit API, error parsing, result wrappers, interceptors.
- Create `app/src/main/java/com/pointquest/android/core/auth/*.kt`: token model, encrypted token storage, in-memory token state, refresh coordinator, session repository.
- Create `app/src/main/java/com/pointquest/android/data/*.kt`: student repositories for auth, practice, points, products, orders, and pagination.
- Create `app/src/main/java/com/pointquest/android/feature/*/*.kt`: ViewModels and Compose screens.
- Create `app/src/main/java/com/pointquest/android/ui/theme/*.kt`: Material 3 theme.
- Create `app/src/test/java/com/pointquest/android/**`: JVM tests for network, auth, repository, pagination, error mapping.
- Create `app/src/androidTest/java/com/pointquest/android/**`: Compose navigation and core screen tests.

---

### Task 1: Android Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/pointquest/android/MainActivity.kt`
- Create: `app/src/main/java/com/pointquest/android/PointQuestApp.kt`
- Create: `app/src/main/java/com/pointquest/android/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/pointquest/android/SmokeTest.kt`

**Interfaces:**
- Produces: Android application id `com.pointquest.android`.
- Produces: `@Composable fun PointQuestApp()`.
- Produces: `BuildConfig.API_BASE_URL: String`.

- [ ] **Step 1: Write the failing smoke test**

Create `app/src/test/java/com/pointquest/android/SmokeTest.kt`:

```kotlin
package com.pointquest.android

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun apiBaseUrlEndsWithVersionedApiPath() {
        assertEquals("http://10.0.2.2:3000/api/v1/", BuildConfig.API_BASE_URL)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.SmokeTest
```

Expected: fail because `./gradlew`, the app module, or `BuildConfig.API_BASE_URL` does not exist.

- [ ] **Step 3: Create the Gradle scaffold**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PointAndroid"
include(":app")
```

Create `build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}
```

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

Create `app/build.gradle.kts` with compile SDK 35, min SDK 26, Compose enabled, unit tests enabled, and dependencies for Activity Compose, Compose BOM, Material 3, Navigation Compose, Lifecycle ViewModel Compose, Retrofit, Moshi, OkHttp, AndroidX Security Crypto, coroutines test, Turbine, MockWebServer, JUnit, AndroidX test, and Compose UI test.

Create a Gradle wrapper using a Gradle version compatible with AGP 8.6.1:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) gradle wrapper --gradle-version 8.7
```

If system `gradle` still fails to start, copy a wrapper from another local Android project only if its `gradle-wrapper.properties` points to Gradle 8.7 or newer and its wrapper jar is present.

- [ ] **Step 4: Create the minimal app entrypoint**

Create `AndroidManifest.xml` with internet permission and `MainActivity`.

Create `MainActivity.kt`:

```kotlin
package com.pointquest.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PointQuestApp() }
    }
}
```

Create `PointQuestApp.kt`:

```kotlin
package com.pointquest.android

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.pointquest.android.ui.theme.PointQuestTheme

@Composable
fun PointQuestApp() {
    PointQuestTheme {
        Text("Point Quest")
    }
}
```

Create a small Material 3 theme in `Theme.kt` with `PointQuestTheme(content: @Composable () -> Unit)`.

- [ ] **Step 5: Run the scaffold test**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.SmokeTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle app
git commit -m "初始化安卓项目脚手架"
```

---

### Task 2: Network Contract and Error Parsing

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/network/ApiModels.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/PointApi.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/ApiError.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/NetworkResult.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/RetrofitFactory.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/ApiErrorParserTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/ApiModelsJsonTest.kt`

**Interfaces:**
- Produces: `data class ApiError(val httpStatus: Int?, val code: String, val message: String, val requestId: String?, val details: Map<String, Any?>)`.
- Produces: `sealed interface NetworkResult<out T>`.
- Produces: `interface PointApi` with all student endpoints.
- Produces: `object RetrofitFactory { fun create(baseUrl: String, okHttpClient: OkHttpClient, moshi: Moshi): PointApi }`.

- [ ] **Step 1: Write the failing API error parser test**

Create `ApiErrorParserTest.kt`:

```kotlin
package com.pointquest.android.core.network

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class ApiErrorParserTest {
    @Test
    fun parsesStableErrorCodeAndRequestIdFromApiErrorBody() {
        val body = """{"code":"INSUFFICIENT_POINTS","message":"积分不足","requestId":"req_123","details":{"balance":7}}"""
            .toResponseBody()
        val response = Response.error<Unit>(409, body)

        val error = ApiErrorParser.parse(response)

        assertEquals(409, error.httpStatus)
        assertEquals("INSUFFICIENT_POINTS", error.code)
        assertEquals("积分不足", error.message)
        assertEquals("req_123", error.requestId)
        assertEquals(7.0, error.details["balance"])
    }
}
```

- [ ] **Step 2: Run the parser test to verify it fails**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.core.network.ApiErrorParserTest
```

Expected: fail because `ApiErrorParser` does not exist.

- [ ] **Step 3: Implement API models and parser**

Create DTOs matching the OpenAPI student schemas:

```kotlin
data class LoginRequestDto(val username: String, val password: String)
data class RegisterRequestDto(val username: String, val password: String)
data class RefreshRequestDto(val refreshToken: String?)
data class PublicUserDto(val id: String, val username: String, val role: String, val pointsBalance: Int)
data class TokenResponseDto(
    val user: PublicUserDto,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresAt: String
)
data class UserResponseDto(val user: PublicUserDto)
data class LearnerQuestionOptionDto(val id: String, val label: String, val content: String, val position: Int)
data class LearnerQuestionDto(val id: String, val stem: String, val basePoints: Int, val options: List<LearnerQuestionOptionDto>)
data class AnswerQuestionRequestDto(val selectedOptionId: String)
data class AnswerResultDto(
    val correct: Boolean,
    val selectedOptionId: String,
    val correctOptionId: String,
    val explanation: String,
    val errorCount: Int,
    val pointsAwarded: Int,
    val balance: Int
)
data class PageMetaDto(val page: Int, val pageSize: Int, val total: Int, val totalPages: Int)
data class WrongQuestionItemDto(
    val question: LearnerQuestionDto,
    val errorCount: Int,
    val firstAnsweredAt: String,
    val masteredAt: String?
)
data class WrongQuestionListResponseDto(val data: List<WrongQuestionItemDto>, val meta: PageMetaDto)
data class PracticeSummaryDto(
    val activeTotal: Int,
    val firstAnsweredCount: Int,
    val unansweredCount: Int,
    val pendingWrongCount: Int,
    val masteredWrongCount: Int,
    val balance: Int
)
data class PointBalanceDto(val balance: Int)
data class PointLedgerDto(
    val id: String,
    val userId: String,
    val type: String,
    val delta: Int,
    val balanceAfter: Int,
    val answerAttemptId: String?,
    val orderId: String?,
    val createdAt: String
)
data class PointLedgerListResponseDto(val data: List<PointLedgerDto>, val meta: PageMetaDto)
data class ProductDto(
    val id: String,
    val name: String,
    val description: String,
    val imageKey: String,
    val stock: Int,
    val pointsCost: Int,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)
data class ProductListResponseDto(val data: List<ProductDto>, val meta: PageMetaDto)
data class CreateOrderRequestDto(val productId: String)
data class OrderDto(
    val id: String,
    val orderNo: String,
    val userId: String,
    val productId: String,
    val productNameSnapshot: String,
    val productImageKeySnapshot: String,
    val pointsCostSnapshot: Int,
    val status: String,
    val createdAt: String,
    val completedAt: String?,
    val cancelledAt: String?,
    val updatedBy: String?,
    val balance: Int
)
data class OrderListResponseDto(val data: List<OrderDto>, val meta: PageMetaDto)
```

Create `ApiErrorParser` using Moshi's `Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)` for `details`.

- [ ] **Step 4: Write the failing DTO JSON test**

Create `ApiModelsJsonTest.kt`:

```kotlin
package com.pointquest.android.core.network

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiModelsJsonTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun tokenResponseKeepsAndroidTokenFields() {
        val json = """{"user":{"id":"u1","username":"student","role":"student","pointsBalance":3},"accessToken":"access","refreshToken":"refresh","accessTokenExpiresIn":900,"refreshTokenExpiresAt":"2026-08-02T00:00:00.000Z"}"""

        val token = moshi.adapter(TokenResponseDto::class.java).fromJson(json)

        assertEquals("access", token?.accessToken)
        assertEquals("refresh", token?.refreshToken)
        assertEquals(900L, token?.accessTokenExpiresIn)
        assertEquals("student", token?.user?.username)
    }
}
```

- [ ] **Step 5: Implement `PointApi` and `RetrofitFactory`**

Define all student endpoints with Retrofit annotations. Do not include admin endpoints. Add `@Header("Idempotency-Key")` on answer and order write calls. `RetrofitFactory.create` must register Moshi converter and use the passed OkHttp client.

- [ ] **Step 6: Run network tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.network.*'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pointquest/android/core/network app/src/test/java/com/pointquest/android/core/network app/build.gradle.kts
git commit -m "添加学生端接口契约"
```

---

### Task 3: Token Storage and Session State

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/auth/AuthTokens.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/TokenStorage.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/EncryptedTokenStorage.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/InMemoryTokenState.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/SessionRepository.kt`
- Test: `app/src/test/java/com/pointquest/android/core/auth/InMemoryTokenStateTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/auth/SessionRepositoryTest.kt`

**Interfaces:**
- Consumes: `PointApi`, `TokenResponseDto`, `PublicUserDto`.
- Produces: `data class AuthTokens(...)`.
- Produces: `interface TokenStorage { suspend fun read(): AuthTokens?; suspend fun replace(tokens: AuthTokens); suspend fun clear() }`.
- Produces: `class InMemoryTokenState { val tokens: StateFlow<AuthTokens?>; fun accessToken(): String?; suspend fun replace(tokens: AuthTokens); suspend fun clear() }`.
- Produces: `class SessionRepository`.

- [ ] **Step 1: Write the failing atomic replacement test**

Create `SessionRepositoryTest.kt` with a fake storage that records write order:

```kotlin
@Test
fun loginWritesSecureStorageBeforePublishingAccessToken() = runTest {
    val events = mutableListOf<String>()
    val storage = RecordingTokenStorage(events)
    val tokenState = InMemoryTokenState()
    val api = FakeAuthApi(
        token = TokenResponseDto(
            user = PublicUserDto("u1", "student", "student", 0),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            accessTokenExpiresIn = 900,
            refreshTokenExpiresAt = "2026-08-02T00:00:00.000Z"
        )
    )
    val repository = SessionRepository(api, storage, tokenState)

    repository.login("student", "Student123!")

    assertEquals(listOf("storage.replace", "state.replace"), events)
    assertEquals("access-1", tokenState.accessToken())
}
```

Production change caught: updating memory before secure storage succeeds.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.core.auth.SessionRepositoryTest
```

Expected: fail because auth classes do not exist.

- [ ] **Step 3: Implement token model and storage abstraction**

Implement `AuthTokens`:

```kotlin
data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAtEpochMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: PublicUserDto
)
```

Implement `TokenStorage` and `EncryptedTokenStorage` using `EncryptedSharedPreferences` and one JSON blob key named `auth_tokens`. Use Moshi to encode/decode `AuthTokens`.

- [ ] **Step 4: Implement in-memory state and session repository**

`SessionRepository.login` calls `api.issueAndroidToken(LoginRequestDto(username, password))`, maps `accessTokenExpiresIn` to epoch millis using `Clock.System.now()` or a test-injected clock, then calls `storage.replace(tokens)` before `tokenState.replace(tokens)`.

`SessionRepository.register` calls register and stores returned tokens if the API returns `TokenResponseDto`; if the endpoint returns a user-only response, it immediately calls login with the same credentials.

`SessionRepository.restore` reads encrypted tokens. If no tokens exist, it clears memory. If refresh token exists, it publishes only after a successful refresh or after validating `/auth/me` with a live access token.

`SessionRepository.logout` calls `/auth/logout` when refresh token exists, then clears storage and memory even if the network call fails.

- [ ] **Step 5: Run auth state tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.auth.*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/pointquest/android/core/auth app/src/test/java/com/pointquest/android/core/auth app/build.gradle.kts
git commit -m "实现令牌存储和会话状态"
```

---

### Task 4: Bearer Interceptor and Single-Flight Refresh

**Files:**
- Create: `app/src/main/java/com/pointquest/android/core/network/AuthInterceptor.kt`
- Create: `app/src/main/java/com/pointquest/android/core/auth/RefreshCoordinator.kt`
- Create: `app/src/main/java/com/pointquest/android/core/network/TokenRefreshingAuthenticator.kt`
- Modify: `app/src/main/java/com/pointquest/android/core/network/RetrofitFactory.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/AuthInterceptorTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/auth/RefreshCoordinatorTest.kt`
- Test: `app/src/test/java/com/pointquest/android/core/network/TokenRefreshingAuthenticatorTest.kt`

**Interfaces:**
- Consumes: `InMemoryTokenState`, `TokenStorage`, `PointApi`.
- Produces: `class AuthInterceptor(private val tokenState: InMemoryTokenState) : Interceptor`.
- Produces: `class RefreshCoordinator { suspend fun refreshIfNeeded(failedAccessToken: String?): Result<AuthTokens> }`.
- Produces: `class TokenRefreshingAuthenticator : Authenticator`.

- [ ] **Step 1: Write the failing Bearer header test**

```kotlin
@Test
fun protectedRequestSendsBearerAccessToken() = runTest {
    val tokenState = InMemoryTokenState()
    tokenState.replace(sampleTokens(accessToken = "access-1"))
    val interceptor = AuthInterceptor(tokenState)
    val chain = RecordingInterceptorChain("https://example.test/api/v1/practice/summary")

    interceptor.intercept(chain)

    assertEquals("Bearer access-1", chain.proceededRequest.header("Authorization"))
}
```

Production change caught: missing Authorization header on protected requests.

- [ ] **Step 2: Write the failing public endpoint test**

```kotlin
@Test
fun tokenEndpointsDoNotReceiveBearerHeader() = runTest {
    val tokenState = InMemoryTokenState()
    tokenState.replace(sampleTokens(accessToken = "access-1"))
    val interceptor = AuthInterceptor(tokenState)
    val chain = RecordingInterceptorChain("https://example.test/api/v1/auth/token")

    interceptor.intercept(chain)

    assertEquals(null, chain.proceededRequest.header("Authorization"))
}
```

Production change caught: leaking Bearer headers to public token endpoints.

- [ ] **Step 3: Run interceptor tests to verify they fail**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.core.network.AuthInterceptorTest
```

Expected: fail because `AuthInterceptor` does not exist.

- [ ] **Step 4: Implement `AuthInterceptor`**

Skip `Authorization` for `/auth/token`, `/auth/register`, and `/auth/refresh`. For other paths, add `Authorization: Bearer <token>` only when memory has a token.

- [ ] **Step 5: Write the failing single-flight refresh test**

```kotlin
@Test
fun concurrentRefreshCallsShareOneNetworkRefresh() = runTest {
    val api = CountingRefreshApi(successTokens = sampleTokenResponse(access = "access-2", refresh = "refresh-2"))
    val storage = InMemoryRecordingTokenStorage()
    val tokenState = InMemoryTokenState()
    tokenState.replace(sampleTokens(accessToken = "access-1", refreshToken = "refresh-1"))
    val coordinator = RefreshCoordinator(api, storage, tokenState, testClock)

    val results = awaitAll(
        async { coordinator.refreshIfNeeded("access-1") },
        async { coordinator.refreshIfNeeded("access-1") },
        async { coordinator.refreshIfNeeded("access-1") }
    )

    assertEquals(1, api.refreshCalls)
    assertEquals(listOf("access-2", "access-2", "access-2"), results.map { it.getOrThrow().accessToken })
}
```

Production change caught: each failed request starts its own refresh and revokes already-rotated refresh tokens.

- [ ] **Step 6: Implement `RefreshCoordinator`**

Use a `Mutex`. Inside the lock, if current access token differs from `failedAccessToken`, return current tokens without a new network call. Otherwise call `/auth/refresh` with the current refresh token. On success, store then publish the new token group. On failure, clear storage and memory.

- [ ] **Step 7: Write and pass one-replay authenticator test**

Use MockWebServer with responses: first protected call returns `401` with `AUTH_TOKEN_EXPIRED`, refresh returns new tokens, replay returns `200`. Assert the protected endpoint is requested exactly twice and refresh exactly once. Also add a test where replay returns `401`; assert no second refresh occurs for the same response chain.

- [ ] **Step 8: Run refresh tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.core.auth.RefreshCoordinatorTest' --tests 'com.pointquest.android.core.network.*AuthenticatorTest' --tests 'com.pointquest.android.core.network.AuthInterceptorTest'
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/pointquest/android/core/auth app/src/main/java/com/pointquest/android/core/network app/src/test/java/com/pointquest/android/core/auth app/src/test/java/com/pointquest/android/core/network
git commit -m "实现Bearer鉴权和令牌刷新"
```

---

### Task 5: Repositories, Pagination, Idempotency, and Error Actions

**Files:**
- Create: `app/src/main/java/com/pointquest/android/data/PagedState.kt`
- Create: `app/src/main/java/com/pointquest/android/data/ApiCall.kt`
- Create: `app/src/main/java/com/pointquest/android/data/ErrorAction.kt`
- Create: `app/src/main/java/com/pointquest/android/data/AuthRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/PracticeRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/PointsRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/ProductRepository.kt`
- Create: `app/src/main/java/com/pointquest/android/data/OrderRepository.kt`
- Test: `app/src/test/java/com/pointquest/android/data/PagedStateTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/ErrorActionTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/PracticeRepositoryTest.kt`
- Test: `app/src/test/java/com/pointquest/android/data/OrderRepositoryTest.kt`

**Interfaces:**
- Consumes: `PointApi`, DTOs, `ApiError`.
- Produces: `data class PagedState<T>(val items: List<T>, val meta: PageMetaDto, val isLoading: Boolean, val error: AppError?)`.
- Produces: `enum class ErrorAction`.
- Produces repositories used by ViewModels.

- [ ] **Step 1: Write the failing pagination test**

```kotlin
@Test
fun outOfRangePageFallsBackToLastServerPage() = runTest {
    val api = FakeProductsApi(
        first = ProductListResponseDto(emptyList(), PageMetaDto(page = 5, pageSize = 20, total = 45, totalPages = 3)),
        second = ProductListResponseDto(listOf(sampleProduct("p1")), PageMetaDto(page = 3, pageSize = 20, total = 45, totalPages = 3))
    )
    val repository = ProductRepository(api)

    val state = repository.loadPage(page = 5, pageSize = 20, search = null)

    assertEquals(3, state.meta.page)
    assertEquals(listOf("p1"), state.items.map { it.id })
    assertEquals(listOf(5, 3), api.requestedPages)
}
```

Production change caught: trusting stale local page instead of service metadata.

- [ ] **Step 2: Write the failing idempotency retry test**

```kotlin
@Test
fun answerRetryReusesSameIdempotencyKeyAndPayloadAfterConcurrentModification() = runTest {
    val api = RecordingPracticeApi(
        failures = listOf(apiError("CONCURRENT_MODIFICATION", 409)),
        success = sampleAnswerResult(correct = true)
    )
    val repository = PracticeRepository(api, idGenerator = { "fixed-key" })

    repository.answerFirstQuestion("q1", "o1")

    assertEquals(listOf("fixed-key", "fixed-key"), api.idempotencyKeys)
    assertEquals(listOf(AnswerQuestionRequestDto("o1"), AnswerQuestionRequestDto("o1")), api.answerBodies)
}
```

Production change caught: generating a new key or changing payload during a retry.

- [ ] **Step 3: Run repository tests to verify they fail**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.*'
```

Expected: fail because repository classes do not exist.

- [ ] **Step 4: Implement pagination and error action mapping**

Implement `ErrorAction.from(error: ApiError): ErrorAction` with explicit mappings for every stable code in the design spec. Implement `PagedState` helpers that preserve server meta and trigger a single fallback request when `meta.totalPages > 0 && requestedPage > meta.totalPages`.

- [ ] **Step 5: Implement repositories**

`PracticeRepository` exposes `summary`, `randomQuestion(excludeIds)`, `answerFirstQuestion`, `wrongQuestions`, and `answerWrongQuestion`.

`ProductRepository` exposes `products`, `product`, and `removeInactiveProductFromCache(productId)`.

`OrderRepository` exposes `orders`, `order`, and `createOrder`.

`PointsRepository` exposes `balance` and `ledger`.

`AuthRepository` delegates login, registration, restore, and logout to `SessionRepository`.

For idempotent writes, implement a helper:

```kotlin
suspend fun <T> withIdempotentRetry(
    key: String,
    block: suspend (String) -> T
): T
```

Retry only timeout/network failures and `CONCURRENT_MODIFICATION`, with a maximum of three attempts. Return immediately on `IDEMPOTENCY_CONFLICT`.

- [ ] **Step 6: Run repository tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.data.*'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pointquest/android/data app/src/test/java/com/pointquest/android/data
git commit -m "实现学生端数据仓库"
```

---

### Task 6: Navigation, App State, and Auth UI

**Files:**
- Create: `app/src/main/java/com/pointquest/android/AppGraph.kt`
- Create: `app/src/main/java/com/pointquest/android/AppContainer.kt`
- Modify: `app/src/main/java/com/pointquest/android/PointQuestApp.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/auth/LoginScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/auth/RegisterScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/home/HomeScreen.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/auth/AuthViewModelTest.kt`
- Test: `app/src/androidTest/java/com/pointquest/android/AuthNavigationTest.kt`

**Interfaces:**
- Consumes: `AuthRepository`, `PracticeRepository`, `PointsRepository`.
- Produces: `sealed interface AppRoute`.
- Produces: `class AppContainer`.
- Produces: login, register, home composables.

- [ ] **Step 1: Write the failing auth ViewModel test**

```kotlin
@Test
fun invalidCredentialsKeepsUsernameAndShowsPasswordError() = runTest {
    val repository = FakeAuthRepository(loginErrorCode = "AUTH_INVALID_CREDENTIALS")
    val viewModel = AuthViewModel(repository)

    viewModel.updateUsername("student")
    viewModel.updatePassword("wrong")
    viewModel.login()

    val state = viewModel.uiState.value
    assertEquals("student", state.username)
    assertEquals("", state.password)
    assertEquals("用户名或密码不正确", state.errorMessage)
}
```

Production change caught: clearing the username or keeping a failed password after invalid credentials.

- [ ] **Step 2: Run the auth ViewModel test to verify it fails**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests com.pointquest.android.feature.auth.AuthViewModelTest
```

Expected: fail because `AuthViewModel` does not exist.

- [ ] **Step 3: Implement app dependency container**

Create `AppContainer` that wires Moshi, OkHttp, Retrofit API, token storage, token state, refresh coordinator, session repository, and student repositories. Use `BuildConfig.API_BASE_URL`.

- [ ] **Step 4: Implement routes and navigation shell**

Create routes: `login`, `register`, `home`, `practice`, `wrongQuestions`, `points`, `products`, `product/{productId}`, `orders`, `order/{orderId}`.

`PointQuestApp` observes session state. If no user is authenticated, start at `login`; otherwise start at `home`.

- [ ] **Step 5: Implement login, register, and home screens**

Use compact Material 3 forms with username and password fields, error text, loading state, and navigation links. Home displays balance, practice counts, wrong-question count, and entry buttons for practice, wrong questions, points, products, and orders.

- [ ] **Step 6: Run auth and navigation tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.auth.*'
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pointquest.android.AuthNavigationTest
```

Expected: unit tests PASS; instrumentation PASS when an emulator or device is available.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/pointquest/android app/src/test/java/com/pointquest/android/feature/auth app/src/androidTest/java/com/pointquest/android/AuthNavigationTest.kt
git commit -m "实现登录注册和首页导航"
```

---

### Task 7: Practice, Wrong Questions, Products, Orders, and Points UI

**Files:**
- Create: `app/src/main/java/com/pointquest/android/feature/practice/PracticeViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/PracticeScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/WrongQuestionsViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/practice/WrongQuestionsScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductListViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductListScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductDetailViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/shop/ProductDetailScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderListViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderListScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderDetailViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/orders/OrderDetailScreen.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/points/PointsLedgerViewModel.kt`
- Create: `app/src/main/java/com/pointquest/android/feature/points/PointsLedgerScreen.kt`
- Modify: `app/src/main/java/com/pointquest/android/AppGraph.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/practice/PracticeViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/shop/ProductDetailViewModelTest.kt`
- Test: `app/src/test/java/com/pointquest/android/feature/orders/OrderListViewModelTest.kt`

**Interfaces:**
- Consumes: student repositories and route definitions.
- Produces: feature ViewModels and screens for the remaining student flows.

- [ ] **Step 1: Write the failing practice result test**

```kotlin
@Test
fun submittingAnswerLocksSelectionAndShowsResult() = runTest {
    val repository = FakePracticeRepository(answer = sampleAnswerResult(correct = false, pointsAwarded = 0))
    val viewModel = PracticeViewModel(repository)

    viewModel.loadRandomQuestion()
    viewModel.selectOption("o2")
    viewModel.submitAnswer()

    val state = viewModel.uiState.value
    assertEquals("o2", state.selectedOptionId)
    assertEquals(false, state.answerResult?.correct)
    assertEquals(true, state.answerSubmitted)
}
```

Production change caught: allowing state to lose the selected option after answer submission.

- [ ] **Step 2: Write the failing product checkout error test**

```kotlin
@Test
fun outOfStockDisablesCheckoutAndSetsVisibleError() = runTest {
    val repository = FakeProductRepository(createOrderErrorCode = "OUT_OF_STOCK")
    val viewModel = ProductDetailViewModel(productId = "p1", repository = repository)

    viewModel.load()
    viewModel.exchange()

    val state = viewModel.uiState.value
    assertEquals(0, state.product?.stock)
    assertEquals(false, state.canExchange)
    assertEquals("商品库存不足", state.errorMessage)
}
```

Production change caught: keeping a stale enabled exchange button after `OUT_OF_STOCK`.

- [ ] **Step 3: Run feature ViewModel tests to verify they fail**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.*'
```

Expected: fail because feature ViewModels do not exist.

- [ ] **Step 4: Implement practice and wrong-question screens**

Practice screen states: loading, question, answer result, no unanswered questions, error. Wrong-question screen states: loading page, list, empty, retry flow, mastered removal, error.

- [ ] **Step 5: Implement product and order screens**

Product list supports search and next-page loading. Product detail shows name, description, stock, points cost, exchange action, insufficient points, out of stock, and inactive-product behavior. Orders show paged list and detail.

- [ ] **Step 6: Implement points ledger screen**

Points ledger shows balance at top and paged ledger entries with type, delta, balance after, and created time.

- [ ] **Step 7: Run feature tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.feature.*'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/pointquest/android/feature app/src/main/java/com/pointquest/android/AppGraph.kt app/src/test/java/com/pointquest/android/feature
git commit -m "实现学生端核心页面"
```

---

### Task 8: Final Verification and Developer Notes

**Files:**
- Create: `README.md`
- Create: `docs/api-integration-notes.md`
- Modify: any files needed to fix verification failures.

**Interfaces:**
- Consumes: complete app.
- Produces: local run instructions and integration notes.

- [ ] **Step 1: Write developer run notes**

Create `README.md` with:

```markdown
# Point Android

原生 Android 学生端。

## 本地运行

1. 启动 Point Quest API 服务。
2. Android 模拟器使用默认基址 `http://10.0.2.2:3000/api/v1/`。
3. 运行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
```

## 范围

包含学生端注册、登录、练题、错题、积分、商品、兑换和订单功能。不包含 `/api/v1/admin/*`。
```
```

Create `docs/api-integration-notes.md` listing the Bearer, refresh, idempotency, pagination, and error-code rules implemented by the app.

- [ ] **Step 2: Run unit tests**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Run lint**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:lintDebug
```

Expected: PASS or only documented non-blocking warnings.

- [ ] **Step 4: Build debug APK**

Run:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
```

Expected: PASS and APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Run instrumentation tests when a device is available**

Run:

```bash
adb devices
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest
```

Expected: PASS when an emulator or device is listed by `adb devices`. If no device is available, record that instrumentation tests were not run.

- [ ] **Step 6: Inspect git status**

Run:

```bash
git status --short
```

Expected: only intended source, test, README, and docs files changed.

- [ ] **Step 7: Commit**

```bash
git add README.md docs app settings.gradle.kts build.gradle.kts gradle.properties gradle
git commit -m "完成学生端安卓接入"
```

---

## Self-Review

- Spec coverage: all student scope from the design is covered by tasks: scaffold, network contract, token storage, refresh, idempotency, pagination, error mapping, auth UI, practice, wrong questions, points, products, orders, final verification, and docs.
- Placeholder scan: the plan contains no open placeholders for implementation choices. Version, package, base URL, endpoint classes, test commands, and commit messages are explicit.
- Type consistency: DTO, token, repository, and ViewModel names are introduced before later tasks consume them. Route names and package paths are consistent across tasks.
