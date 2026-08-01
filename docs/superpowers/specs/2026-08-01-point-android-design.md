# Point Android 学生端设计规格

## 背景与契约

Point Android 是从零创建的原生 Android 学生端。服务端契约和接入规则分别来自：

- `/Users/ushopal/workspace/myself/point/openapi/openapi.json`
- `/Users/ushopal/workspace/myself/point/docs/api/android-integration.md`

`openapi.json` 是唯一接口契约源。Android 工程不提交手写的 Retrofit 接口或契约 DTO，也不复制服务端的题目判定、积分、库存和订单状态规则。

## 目标

构建一个 API 26 及以上可运行的完整学生端，支持：

- 学生注册、登录、会话恢复和退出。
- 随机首次答题、结果反馈和无题完成状态。
- 待练错题分页、错题重练和掌握后移除。
- 练习摘要、积分余额和积分流水。
- 商品搜索、分页、详情和积分兑换。
- 我的订单分页和订单详情。
- Bearer 鉴权、Refresh Token 轮换、并发刷新合并、幂等写请求、稳定错误码和服务端分页元数据。

## 非目标

- 不实现任何 `/api/v1/admin/*` 管理端接口或页面。
- 不提供离线题目、商品、订单或积分缓存。
- 不针对平板制作专用双栏布局。
- 不配置正式发布签名、不内置生产凭据、不执行应用商店发布。
- 不使用 Cookie 或 CSRF 完成 Android 鉴权。

## 产品和终端约束

- 应用名称：`Point Quest`。
- Application ID：`com.pointquest.android`。
- 最低版本：Android 8.0，API 26。
- 编译和目标版本：API 35。
- 主要设备：手机竖屏。
- 横屏和大屏必须可用且不溢出，但沿用单栏内容宽度约束。
- 业务采用在线优先模式。网络不可用时显示错误和重试，不展示持久化的旧业务数据。
- 默认界面语言为简体中文。

## 视觉与交互方向

采用已确认的“明快课堂”方向：

- 主色 `#1677FF`，强调色 `#FFC83D`。
- 页面背景 `#F7FBFF`，卡片 `#FFFFFF`，主要文字 `#16304A`。
- 成功色 `#238B57`，错误色 `#D64545`。
- 使用 Material 3、圆润卡片、清晰层级和直接的学习反馈。
- 正文字体使用 Android 系统无衬线中文字体；不下载运行时字体。
- 卡片默认圆角 16dp，主要按钮高度不低于 48dp。
- 支持系统字体缩放、TalkBack 语义和至少 48dp 的触控目标。
- 正误、库存和订单状态不能只靠颜色表达，必须同时使用文字或图标。

主导航使用四栏底部导航：

1. 首页
2. 练习
3. 商城
4. 我的

四个顶层页面保存各自导航和滚动状态。答题、详情、注册和确认兑换属于沉浸式子页面，不显示底部导航。

## 构建基线

- Android Gradle Plugin `8.9.2`
- Gradle `8.11.1`
- JDK `17`
- Kotlin `2.1.20`
- Kotlin Compose Compiler Plugin `2.1.20`
- OpenAPI Generator Gradle Plugin `7.24.0`
- `compileSdk = 35`
- `targetSdk = 35`
- `minSdk = 26`
- `versionCode = 1`
- `versionName = "0.1.0"`

使用 Gradle Version Catalog 锁定 AndroidX、Compose BOM、Retrofit、OkHttp、Moshi、Coroutines、Navigation Compose、Coil、JUnit、Turbine、MockWebServer 和 Android 测试依赖。

工程保持单 `app` 模块，通过包边界隔离职责。依赖注入使用手工 `AppContainer`，不引入 Hilt。

## OpenAPI 代码生成

Gradle 属性 `pointOpenApiSpec` 指定契约文件，默认值为 `../point/openapi/openapi.json`。构建在文件不存在时立即失败并输出设置方法。

生成配置：

- `generatorName = kotlin`
- `library = jvm-retrofit2`
- API 包：`com.pointquest.android.generated.api`
- 模型包：`com.pointquest.android.generated.model`
- 生成目录：`app/build/generated/openapi`
- 启用协程接口和 Moshi 序列化。
- 生成任务先执行 OpenAPI 校验。
- Kotlin 编译依赖生成任务。

生成产物不提交 Git。业务代码不能直接把生成 DTO 暴露给 ViewModel 或 Compose，而是通过网关适配器映射为应用领域模型。

OpenAPI 中的接口路径已经包含 `/api/v1`，因此 Retrofit Base URL 只使用服务根地址：

- Debug：`http://10.0.2.2:3000/`
- Release：Gradle 属性 `pointApiBaseUrl`，必须显式提供且使用 `https://`

商品图片固定使用相同 API Origin 下的 `/uploads/{imageKey}`。客户端校验 `imageKey` 为受信任的相对路径，不接受绝对 URL 或路径穿越片段。

## 代码结构

```text
com.pointquest.android
├── app                 应用入口、AppContainer、根导航和会话路由
├── core
│   ├── auth            会话状态、安全存储、刷新协调器
│   ├── network         API 客户端、错误解析、受保护调用执行器
│   ├── model           与生成 DTO 隔离的领域模型
│   └── ui              主题、通用加载/空态/错误组件
├── data
│   ├── auth            AuthRepository
│   ├── practice        PracticeRepository
│   ├── points          PointsRepository
│   ├── products        ProductsRepository
│   └── orders          OrdersRepository
├── feature
│   ├── auth            登录和注册
│   ├── home            首页学习概览
│   ├── practice        练习、答题结果和错题
│   ├── shop            商品列表、详情和兑换
│   ├── orders          订单列表和详情
│   ├── points          积分流水
│   └── profile         我的和退出登录
└── generated           build 目录中的 OpenAPI 生成代码
```

依赖方向固定为：Compose 页面 → ViewModel → Repository → Core Network/Auth → 生成客户端。UI 不直接依赖 Retrofit Response、OkHttp Response 或生成 DTO。

## 导航和页面

### 启动与认证

启动页读取安全会话：

1. 没有 Refresh Token：进入登录页。
2. Refresh Token 已本地过期：清除会话并进入登录页。
3. Refresh Token 可用：调用 `/api/v1/auth/refresh`。
4. 刷新成功：进入主导航首页。
5. 刷新失败：清除会话并进入登录页。

登录使用 `/api/v1/auth/token`。注册使用 `/api/v1/auth/register`；该接口只返回用户信息，不返回 Token，因此注册成功后回到登录页，预填用户名并显示成功提示。

### 首页

首页使用 `/api/v1/practice/summary` 展示：

- 当前积分。
- 有效题目总数、首次已答数和未答数。
- 待练错题和已掌握错题数量。
- 开始答题主按钮。
- 错题、订单和积分流水快捷入口。

### 练习

练习顶层页提供“首次答题”和“错题重练”两个入口。

首次答题使用 `/api/v1/practice/random`。单次页面会维护最多 50 个已展示题目 ID，并通过 `excludeIds` 避免当前会话重复展示。

答题流程：

1. 展示题干、基础积分和按 `position` 排序的选项。
2. 用户选择选项后点击提交。
3. 提交时冻结题目 ID、选项 ID、幂等键和序列化请求体，并锁定选择控件。
4. 成功后展示对错、所选答案、正确答案、解析、错误次数、获得积分和最新余额。
5. 用户点击下一题后重新获取随机题目。
6. `NO_UNANSWERED_QUESTIONS` 显示全部完成状态。

错题列表使用 `/api/v1/practice/wrong-questions`，每页 20 条。错题重练使用专用 answer 接口。`QUESTION_ALREADY_MASTERED` 或成功掌握后从当前列表移除并修正分页状态。

### 商城

商品列表调用 `/api/v1/products`，固定 `isActive=true`，支持 `search`、分页和下拉刷新。搜索文本变化后使用短延迟去抖并回到第 1 页。

商品详情展示图片、名称、描述、库存、所需积分和当前余额。兑换前弹出二次确认；确认后调用 `POST /api/v1/orders`。成功返回订单及最新余额，并导航至订单详情。

### 我的、订单和积分

“我的”展示用户名、角色和当前积分，包含订单、积分流水和退出登录入口。

订单列表调用 `/api/v1/orders`，每页 20 条。订单详情展示订单号、商品快照、积分快照、创建时间以及：

- `PENDING_PICKUP`：待领取
- `COMPLETED`：已完成
- `CANCELLED`：已取消

积分流水调用 `/api/v1/points/ledger`，每页 20 条。类型显示为：

- `ANSWER_REWARD`：答题奖励
- `ORDER_REDEEM`：兑换支出
- `ORDER_REFUND`：订单退款

每条记录展示积分增减、变动后余额和时间。

## UI 状态模型

每个 ViewModel 暴露单一不可变 `StateFlow<UiState>`，并通过意图方法接收用户动作。页面至少覆盖：

- 初次加载
- 有内容
- 空状态
- 首次加载错误
- 已有内容时的非致命错误
- 下拉刷新
- 下一页加载
- 提交处理中
- 提交成功或稳定业务错误

已有内容时的错误使用 Snackbar；首次加载错误使用整页错误和重试按钮；字段错误贴近输入框。导航事件使用一次性事件通道，不能塞入可重复消费的持久 UiState。

## 会话与安全存储

### 内存会话

Access Token 只保存在进程内，包含：

- `accessToken`
- 根据 `accessTokenExpiresIn` 计算的过期时刻
- 当前用户
- Token generation，用于识别等待刷新期间令牌是否已经更新

Access Token 不写入文件、SharedPreferences、日志、崩溃报告或 URL。

### Refresh Token 存储

使用 Android Keystore 生成不可导出的 AES-256-GCM 密钥。每次写入使用新的随机 IV，将 Refresh Token 和过期时间序列化、加密后写入 `noBackupFilesDir` 中的版本化会话文件。

使用 `AtomicFile` 保证密文整体替换。成功登录或刷新时：

1. 先加密并原子写入新的 Refresh Token 和过期时间。
2. 写入成功后再发布新的内存 Access Token 和用户状态。
3. 若安全写入失败，清除本地会话并保持未登录，不能继续使用已被服务端撤销的旧 Refresh Token。

安全文件和密钥不参与 Android Backup。密文损坏、密钥失效或解密失败都按无会话处理并清理残留。

### 公开和受保护客户端

创建两个 API 客户端：

- 公开客户端：注册、登录、刷新和退出，不带 Cookie、CSRF 或 Authorization。
- 受保护客户端：只通过拦截器读取当前内存 Access Token 并添加 `Authorization: Bearer ...`。

两个客户端都不配置 CookieJar，不发送 `pq_access`、`pq_refresh`、`pq_csrf` 或 `X-CSRF-Token`。

退出登录先对服务端做 best-effort 注销，随后无条件清除本地内存会话、安全文件和导航历史。

## Refresh single-flight 与 401 恢复

所有受保护调用通过协程级 `AuthorizedCallExecutor`，不使用会阻塞 OkHttp 线程的同步 `Authenticator`。

调用前若 Access Token 缺失或将在 30 秒内过期：

1. `RefreshCoordinator` 获取 `Mutex`。
2. 获取锁后比较 Token generation；若其他调用已经完成刷新，直接使用新 Token。
3. 否则使用公开客户端和当前 Refresh Token 调用 `/api/v1/auth/refresh`。
4. 按“先安全落盘、后发布内存”的顺序替换会话。
5. 等待者统一复用刷新结果。

受保护请求收到 `401 AUTH_TOKEN_EXPIRED` 时，整个业务操作最多再触发一次刷新和一次重放。其他 401 不盲目刷新。刷新过期、撤销、解密或安全存储失败时，清除会话并发出全局重新登录事件。

## 幂等写请求与重试

以下请求必须携带 `Idempotency-Key`：

- `POST /api/v1/practice/questions/{questionId}/answer`
- `POST /api/v1/practice/wrong-questions/{questionId}/answer`
- `POST /api/v1/orders`

一次用户操作开始时生成 UUID，并冻结序列化后的请求体。初次请求和所有自动重试复用同一 Key 与同一载荷。

自动重试范围：

- 网络 I/O 失败
- HTTP 5xx
- `CONCURRENT_MODIFICATION`

一次用户操作最多发送 3 次业务尝试，使用指数退避和少量随机抖动。鉴权刷新恢复在整个操作中最多发生一次。`IDEMPOTENCY_CONFLICT`、未知 4xx 和其他稳定业务错误不自动重试。

用户修改答案、改选商品或在错误状态明确点击重新提交时，才创建新的操作、Key 和冻结载荷。

## 分页

错题、商品、订单和积分流水统一使用：

- `page` 从 1 开始。
- `pageSize = 20`。
- 客户端以服务端 `meta.page`、`meta.pageSize`、`meta.total` 和 `meta.totalPages` 为准。
- 搜索或筛选变化后回到第 1 页并清空旧结果。
- 下一页按资源 ID 去重，防止服务端状态变化造成重复。
- 当前页超过新的 `totalPages` 时，回到最后一个有效页并重新请求；`totalPages = 0` 时显示空状态。

不引入 Room 或其他本地业务数据库，因此不存在离线同步和 SQL 查询路径。

## 错误模型与 UI 行为

所有失败映射为统一 `AppError`：

- HTTP 状态
- 稳定 `code`
- 展示用 `message`
- `requestId`
- `details`

UI 只按稳定 `code` 分支，不解析中文 `message`：

| 错误码 | 行为 |
| --- | --- |
| `AUTH_INVALID_CREDENTIALS` | 保留用户名、清空密码并显示表单错误 |
| `AUTH_TOKEN_EXPIRED` | 自动刷新并最多重放一次 |
| `FORBIDDEN` | 停止重试并隐藏或禁用无权限入口 |
| `VALIDATION_FAILED` | 将 `details` 映射到对应字段，不自动重试 |
| `QUESTION_ALREADY_ANSWERED` | 刷新首次答题队列 |
| `QUESTION_ALREADY_MASTERED` | 从待练错题列表移除 |
| `NO_UNANSWERED_QUESTIONS` | 显示首次答题完成状态 |
| `INSUFFICIENT_POINTS` | 使用 `details.balance` 更新余额并显示差额 |
| `OUT_OF_STOCK` | 将库存更新为 0 并禁用兑换 |
| `PRODUCT_INACTIVE` | 从商品列表移除并返回商城 |
| `ORDER_INVALID_STATUS` | 刷新订单详情 |
| `IDEMPOTENCY_CONFLICT` | 停止重试并允许用户重新发起操作 |
| `CONCURRENT_MODIFICATION` | 在上限内复用 Key 与载荷重试 |

未知错误使用服务端 `message` 作为辅助展示并附带 `requestId`；逻辑判断仍只使用 `code`。

## 网络与日志安全

- Debug 变体只允许访问 `10.0.2.2` 的 HTTP。
- Release 变体禁止明文网络，并在配置阶段拒绝非 HTTPS Base URL。
- 不启用会输出 Header、Body 或 Token 的 HTTP 日志拦截器。
- 失败日志只记录 HTTP 状态、稳定错误码和 `requestId`。
- 密码输入使用安全键盘类型，失败后清除密码字段。
- 商品图片只从配置的 API Origin 加载，并对 `imageKey` 做格式校验。
- 所有网络超时都有明确上限；取消协程时不转换成可重试网络错误。

## 测试策略

### JVM 单元测试

- OpenAPI 校验、生成任务连接和生成代码编译。
- 生成 DTO 到领域模型的完整字段映射。
- `ApiError` 解析和未知错误回退。
- 登录安全存储失败时不发布内存会话。
- Refresh Token 原子替换顺序。
- 多个并发请求只触发一次 Refresh。
- 401 只刷新并重放一次。
- Refresh 失败清空会话。
- 幂等 Key 和序列化载荷在网络、5xx 和并发冲突重试中保持不变。
- `IDEMPOTENCY_CONFLICT` 和未知 4xx 不重试。
- 服务端分页元数据、ID 去重、筛选重置和越界回退。
- 各 ViewModel 的加载、内容、空态、错误、刷新、分页和提交状态。
- 稳定错误码到 UI 动作的映射。

使用 JUnit、Coroutines Test、Turbine 和 MockWebServer。时间、随机抖动、UUID、安全存储和网络网关都通过接口注入，保证测试确定性。

### Android 设备测试

- Android Keystore AES-GCM 加解密、原子覆盖、损坏文件和退出清除。
- 登录态与未登录态根导航。
- 注册成功回登录并预填用户名。
- 随机答题结果、无题空态和错题掌握移除。
- 商品缺货、积分不足和兑换成功导航。
- 订单和积分分页页面。
- 字体缩放、主要 TalkBack 描述和关键触控目标。

设备测试在有模拟器或真机时运行。设计确认时 `adb devices` 为空，因此完成报告必须如实区分已执行和未执行的测试。

## 验证命令

实现完成前至少执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew openApiValidate
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:lintDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleDebug
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleRelease -PpointApiBaseUrl=https://api.example.invalid/
adb devices
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:connectedDebugAndroidTest
```

只有存在设备时才要求最后一条命令成功；没有设备时在交付报告中明确说明未运行原因。

## 交付物

- 可构建的完整 Android 源码和 Gradle Wrapper。
- OpenAPI 校验与生成配置。
- 学生端全部页面、状态和导航。
- 网络、鉴权、安全存储、分页、幂等和错误处理实现。
- JVM 单元测试和 Android 设备测试源码。
- 中文 `README.md` 和 API 接入说明。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 验收标准

- 工程在 JDK 17 和本机 Android SDK 35 下通过 OpenAPI 校验、单元测试、Lint、Debug 构建和 Release 构建。
- 学生端注册、登录、会话恢复、首页、首次答题、错题重练、积分、商品兑换和订单流程完整。
- 不包含管理端接口和页面。
- 公开接口不发送 Cookie、CSRF 或 Authorization；受保护接口发送 Bearer Header。
- Refresh Token 由 Android Keystore 密钥加密，Access Token 不持久化。
- 成功刷新先安全落盘，再替换内存会话；并发刷新合并为一次。
- 单次业务操作最多刷新恢复一次，瞬时错误重试有明确上限。
- 三类写请求正确使用和复用幂等 Key 与冻结载荷。
- 所有分页列表以服务端 `meta` 为准。
- UI 使用稳定错误码做逻辑分支，不解析中文消息。
- Debug HTTP 和 Release HTTPS 策略生效，日志不泄露凭据。
- 关键网络、鉴权、幂等、分页、ViewModel 和 UI 行为有自动化测试保护。
