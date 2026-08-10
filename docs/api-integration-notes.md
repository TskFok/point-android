# Android API 接入说明

本文描述当前 Android 实现的真实契约和安全边界。API 根地址是 origin 根路径，例如 `https://api.example.invalid/`；OpenAPI 生成的方法包含 `/api/v1`。本文出现的 `example.invalid` 是保留的无效域名，仅用于格式或构建示例，不能用于生产。

## 契约生成与调用分层

- 唯一契约源默认是 `../point/openapi/openapi.json`。
- `./gradlew openApiValidate openApiGenerate` 使用固定的 OpenAPI Generator 版本生成 Kotlin、Retrofit、Moshi 客户端到 `app/build/generated/openapi`。
- 可用 `-PpointOpenApiSpec=/absolute/path/openapi.json` 覆盖契约位置；CI 应显式使用受版本控制的文件。
- `GeneratedPublicAuthGateway` 和 `GeneratedStudentGateway` 是生成 DTO/API 与领域模型之间的边界。Repository、ViewModel 和 UI 不得直接调用 `DefaultApi`，也不得复制服务端积分、库存、答案判定或订单规则。
- 生成任务会为生成模型中的枚举加入 `UNKNOWN_DEFAULT_OPEN_API`，Moshi 仅对含该哨兵的生成枚举启用未知值适配。已知 wire value 保持原映射；服务端未来新增枚举值时先反序列化为哨兵，再由 Gateway 映射到领域 `UNKNOWN`，不会让整个响应解析失败。

## Bearer 认证，不使用 Cookie/CSRF

登录调用 Android token 接口，成功后得到 Access Token、可轮换 Refresh Token、过期时间和学生用户资料。公开客户端与受保护客户端是两个独立的 OkHttp 实例：

- 两者都使用 `CookieJar.NO_COOKIES`，不会保存或回发 Web 会话 Cookie。
- 两者在发出请求前都移除 `Cookie` 和 `X-CSRF-Token`；公开客户端还移除任何 `Authorization`。
- 受保护客户端先移除旧认证头，再只从当前内存会话写入 `Authorization: Bearer <access-token>`。
- Android Gateway 对生成方法的 `xCSRFToken` 参数固定传 `null`。源码中出现 `X-CSRF-Token`、`CookieJar` 或 `xCSRFToken = null` 是主动移除/禁用 Web 认证材料的防护，不是凭据泄露。

Access Token 只存在 `SessionState` 的进程内活动会话中。密码、Access Token、Refresh Token、`Authorization`、Cookie 和请求/响应 body 均不得写入日志、崩溃报告、URL 或普通存储。

## Keystore 与本地会话

只有 Refresh Token 及其过期时间需要持久化：

- 数据先序列化，再使用 Android Keystore 中别名 `point_refresh_token_v1` 的 256 位 AES-GCM 密钥加密；随机 IV 为 12 字节，认证标签为 128 位。
- 加密信封通过 `AtomicFile` 写入 `noBackupFilesDir/point_session_v1.json`，不使用 `SharedPreferences`，也不会进入 Android 备份。
- 写入、读取和清理都由同一互斥锁串行化；解析、解密或存储失败会使内存会话失效，并尽力删除本地材料。
- `TokenBundle`、`ActiveSession`、`StoredRefreshSession` 的字符串表示必须保持令牌脱敏。

不要把 Keystore 加密文件视作可跨设备恢复的登录态；密钥不可用或密文损坏时应回到登录页。

## Refresh 轮换、epoch 与 single-flight

每次登录或刷新成功都会先原子写入新的 Refresh Token，再发布新的内存 Access Token，并递增会话 epoch/generation。旧 Refresh Token 已被服务端轮换撤销，不得再次使用。

刷新流程由一个互斥锁合并为 single-flight：

1. 调用者记录观察到的 generation。
2. 若另一个请求已经安装了不同 generation 的会话，等待者直接复用新会话，不再发第二个刷新请求。
3. 持锁者取得包含 epoch 的刷新租约，校验 Refresh Token 未过期后调用刷新接口。
4. 只有租约 epoch 仍与当前 epoch 一致时才允许提交新令牌；登出、重新登录或其他失效动作已改变 epoch 时，迟到响应返回 `AUTH_SESSION_CHANGED`，不得覆盖新会话。
5. 刷新返回非学生账号、失败、过期、取消后的不确定结果、网络异常或无效响应时，当前租约会失效并清理本地材料；不会盲目重试可能已经完成轮换的刷新请求。

同一次用户操作只有一个刷新预算：

- 正常调用前，Access Token 剩余有效期不超过 30 秒时先刷新。
- 若预刷新已经发生，后续业务调用仍返回 401，则原样返回，不做第二次刷新或重放。
- 若预刷新未发生，只有精确的 `401 AUTH_TOKEN_EXPIRED` 会触发一次强制刷新，并把原业务调用重放一次。
- 其他 401（例如 `AUTH_INVALID_TOKEN`）不刷新、不重放。因此一次受保护调用最多刷新一次、业务调用最多执行两次。

认证预算包住 Repository 的整个有界重试过程，而不是在每次重试时重置。一次用户操作最多刷新一次，且最多实际发送 3 次业务请求；认证重放也计入这 3 次。混合序列（例如 `401 → 刷新重放 → 503 → 外层重试 → 401`）不会获得第二次刷新，也不会突破发送上限。

## 有界重试与三类幂等写

通用重试器只接受 GET 和带幂等键的写请求；Refresh Token 轮换不进入该重试器。默认最多尝试 3 次，退避基数为 250ms、500ms，每次附加 0–100ms 抖动。

三类幂等写请求是：

1. 首次答题；
2. 错题重练；
3. 商品兑换/创建订单。

一次用户操作开始时生成 UUID `Idempotency-Key`，并冻结请求载荷；该操作发生网络错误、5xx 或 `CONCURRENT_MODIFICATION` 有界重试时，所有尝试复用同一 key 和完全相同的载荷。用户明确开始新的答题或兑换操作时才生成新 key。

`IDEMPOTENCY_CONFLICT`、任何 `AUTH_*`、401、403 和普通 4xx 均不自动重试；`CONCURRENT_MODIFICATION` 是唯一允许有界重试的 4xx。读取请求只对网络错误和 5xx 做有界重试。

## 分页

商品、订单、积分流水和错题列表每页固定请求 20 条，页码从 1 开始。客户端以响应 `meta.page`、`pageSize`、`total`、`totalPages` 为准：

- 第 1 页替换本地集合，后续页按业务 ID 去重后追加，保持首次出现顺序。
- 搜索/筛选变化回到第 1 页。
- 服务端返回的页码或本地请求页超出有效范围时，重新请求最接近的有效页；总页数为 0 时呈现空集合。
- footer 加载失败保留已有数据，允许用户重试；加载更多期间禁止并发重复请求。
- 商品列表支持用户下拉刷新。刷新第 1 页期间保留当前商品；失败只显示非致命提示，成功后用服务端最新第 1 页替换当前集合。

## 跨页面数据一致性

答题、兑换和商品下架结果通过 `AppDataSync` 发布进程内失效信号：

- 同步状态绑定当前 `(userId, sessionGeneration)`。`SignedOut` 或新的会话 generation 发布时，余额、刷新 revision 与临时下架标记在同一快照中清空；旧会话请求的迟到结果会因会话键不匹配而被丢弃。
- 答题或兑换响应携带的新余额会立即更新仍存活的首页/个人中心状态。
- 答题后首页重新在线拉取练习摘要与积分余额。
- 兑换成功后首页与商店重新在线拉取；商店以服务端商品库存/状态为准。
- `PRODUCT_INACTIVE` 会先从当前商品列表移除对应商品，再触发商店在线刷新。该标记是带 shop revision 的临时 tombstone：只有在该 revision 或更高 revision 下启动的权威第 1 页请求成功后才清除；请求失败时保留，供后续重试继续过滤。
- 初始加载、加载更多或下拉刷新在途时若收到新的 shop revision，商城会提升请求 generation、取消旧请求并以最新 revision 重启 online-first 第 1 页；即使旧请求忽略取消并迟到返回，也不能覆盖新响应。

这些更新是 UI 一致性优化，不是本地权威缓存；应用重启或会话恢复后仍以服务端响应为准。

## 错误结构与 UI 行为

服务端错误解析为 `AppError(httpStatus, code, message, requestId, details)`。业务分支只依赖稳定 `code`；`requestId` 可展示给用户并供服务端排障。无法解析的错误体按状态码退化为 `HTTP_ERROR` 或 `SERVER_ERROR`，网络异常映射为 `NETWORK_ERROR`，无效响应映射为 `INVALID_RESPONSE`。

当前关键行为包括：

| 错误码 | 客户端行为 |
| --- | --- |
| `AUTH_INVALID_CREDENTIALS` | 保留用户名并提示重新输入密码，不自动重试 |
| `AUTH_TOKEN_EXPIRED` | 按上一节使用一次刷新预算 |
| `FORBIDDEN` | 清理/拒绝非学生会话并显示无权限信息 |
| `VALIDATION_FAILED` | 映射字段问题，不自动重试 |
| `NO_UNANSWERED_QUESTIONS` | 显示首次答题完成状态 |
| `QUESTION_ALREADY_MASTERED` | 从待练错题中移除并返回列表 |
| `INSUFFICIENT_POINTS` | 仅接受有限、非负、整数且不超出 `Int` 的 `details.balance`；非法详情使余额变为未知并禁用兑换 |
| `OUT_OF_STOCK` | 将当前商品库存更新为 0 并禁用兑换 |
| `PRODUCT_INACTIVE` | 标记下架并返回商店 |
| `ORDER_INVALID_STATUS` | 只额外读取一次订单详情，不重放写请求 |
| `IDEMPOTENCY_CONFLICT` | 停止自动重试，由用户确认后发起新操作 |
| `CONCURRENT_MODIFICATION` | 仅幂等写使用原 key/载荷做有界重试 |

未知错误必须有安全兜底，不能解析中文 `message` 来决定业务逻辑，也不能向用户显示令牌、密码或原始响应 body。

## 商品图片

客户端不接受任意图片 URL。`ProductImageUrlFactory` 只接收严格的小写 `products/<UUID>.(jpg|png|webp)` key，并仅在配置为无用户名、密码、查询、片段或额外路径的 HTTP(S) 根 origin 时，生成同 origin 的 `/uploads/products/<UUID>.<扩展名>`。

无效 key、无效根地址或路径解析异常均返回 `null`，UI 使用应用内占位图和文字 `contentDescription`。合法同源 URL 由 Coil 3 的 OkHttp 网络 fetcher 加载，并复用应用受控的公开 `OkHttpClient`；网络或解码失败才使用错误占位图。Release 图片 origin 从已校验的 HTTPS API 根地址派生；不得让服务端字段绕过 factory 直接交给 Coil。

## 网络安全配置

- 主清单显式声明 `android.permission.INTERNET`。
- Debug 网络安全配置默认禁止明文，只允许精确主机 `10.0.2.2`，且不包含子域名。
- Release 网络安全配置始终禁止明文；Release API 参数必须是带尾部 `/` 的纯 HTTPS 根 origin，不允许子路径、用户信息、查询参数或片段。
- `./gradlew verifyReleaseApiBaseUrlValidation verifyNetworkSecurityConfig -PpointApiBaseUrl=https://api.example.invalid/` 会自动验证以上边界。

## 日志与排障

- OkHttp 客户端不得安装 `HttpLoggingInterceptor` 的 `BODY` 或 `HEADERS` 级别；当前实现完全不安装 HTTP 日志拦截器。
- 允许记录的网络排障字段仅限稳定错误码、HTTP 状态和 `requestId`，且在确有需要时使用。
- 禁止记录用户名对应的密码、Access/Refresh Token、`Authorization`、Cookie、CSRF 值、幂等请求 body、完整错误 `details` 或服务端原始 body。
- 安全扫描命中防护语句时必须阅读语义：`removeHeader("X-CSRF-Token")`、`CookieJar.NO_COOKIES` 和 `xCSRFToken = null` 表示主动消除认证材料，不应被误报为泄露。
