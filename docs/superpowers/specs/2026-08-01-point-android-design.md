# Point Android 学生端设计规格

## 背景

这是一个全新的 Android 项目，用于接入 Point Quest 版本化 REST API。接口契约以 `/Users/ushopal/workspace/myself/point/openapi/openapi.json` 为唯一来源，接入规则来自 `/Users/ushopal/workspace/myself/point/docs/api/android-integration.md`。

首版实现学生端完整功能，不包含 `/api/v1/admin/*` 管理端能力。

## 目标

构建一个原生 Android 学生端应用，支持注册、登录、会话恢复、练题、错题重练、积分查看、商品兑换和订单查看。应用必须正确处理 Bearer 鉴权、Refresh Token 轮换、幂等写请求、分页元数据和稳定错误码。

## 非目标

- 不实现管理端 dashboard、题库、商品、订单管理等 `/api/v1/admin/*` 接口。
- 不复制服务端题目判定、积分、库存或订单状态业务规则。
- 不使用 Cookie 或 CSRF 机制完成 Android Bearer 鉴权。
- 不把 token 写入日志、崩溃报告、URL 或普通 SharedPreferences。

## 技术方案

使用 Kotlin、Jetpack Compose、Material 3、Navigation Compose、Retrofit、OkHttp、Moshi、Kotlin Coroutines 和 Jetpack Security。项目使用 Gradle Android 插件构建。

学生端 API 层手写轻量 DTO 和 Retrofit interface，字段与 OpenAPI 中学生端 schema 保持一致。暂不接入 OpenAPI Generator，避免新项目首版被生成器配置和产物结构拖慢；后续可以在 API 稳定后把手写 DTO 替换为生成模型。

默认开发 API 基址为 `http://10.0.2.2:3000/api/v1/`，便于 Android 模拟器访问宿主机服务。生产构建必须切换为 HTTPS 基址。

## 应用架构

采用分层但保持轻量：

- `core/network`：Retrofit、OkHttp、错误解析、Bearer header、401 refresh 恢复、API DTO。
- `core/auth`：token 内存状态、安全存储、single-flight refresh、登录状态观察。
- `feature/auth`：登录、注册、退出。
- `feature/home`：练习摘要和积分余额入口。
- `feature/practice`：随机未答题、首次答题、错题列表、错题重练。
- `feature/shop`：商品列表、商品详情、兑换商品。
- `feature/orders`：订单列表、订单详情。
- `feature/points`：积分流水。

ViewModel 只协调 UI 状态和仓库调用。Repository 负责 API 调用、错误码转换、分页状态和幂等重试。UI 不直接依赖 Retrofit response。

## 鉴权与 Token 处理

登录使用 `POST /auth/token`，注册使用 `POST /auth/register`。受保护请求统一发送 `Authorization: Bearer <accessToken>`。

Access Token 仅保存在进程内状态中。Refresh Token 和必要的过期信息通过 Jetpack Security 的 EncryptedSharedPreferences 存储。成功登录或刷新后，必须先把整组 token 原子写入安全存储，再更新进程内 access token。

刷新使用 `POST /auth/refresh`，请求体携带当前 `refreshToken`。每次刷新成功都会替换 access token 和 refresh token，旧 refresh token 不再使用。

并发刷新由 `Mutex` 实现 single-flight：

- 第一个遇到过期 token 的请求执行刷新。
- 其他请求等待同一个刷新结果。
- 刷新成功后，原请求最多重放一次。
- 刷新失败、refresh token 过期或撤销时清空 token 并进入未登录状态。

## 幂等写请求

以下操作必须携带 `Idempotency-Key`：

- `POST /practice/questions/{questionId}/answer`
- `POST /practice/wrong-questions/{questionId}/answer`
- `POST /orders`

Repository 为每次用户操作生成 UUID。网络超时或 `CONCURRENT_MODIFICATION` 后的有界重试复用原 key 和完全相同的请求体。用户重新选择答案、切换商品或重新发起操作时生成新 key。遇到 `IDEMPOTENCY_CONFLICT` 时停止重试，并让 UI 回到可重新提交状态。

## 分页

分页列表统一使用 `page` 和 `pageSize`，默认 `page=1`、`pageSize=20`。客户端以服务端返回的 `meta.page`、`meta.pageSize`、`meta.total`、`meta.totalPages` 为准。

筛选条件变化时回到第 1 页。若服务端状态变化导致当前页超过 `totalPages`，列表仓库切回最后一个有效页并重新请求。

## 错误处理

所有 API 错误解析为统一的应用错误对象，包含 HTTP 状态、稳定 `code`、展示用 `message`、`requestId` 和 `details`。

UI 分支只依赖稳定 `code`：

- `AUTH_INVALID_CREDENTIALS`：保留用户名，提示重新输入密码。
- `AUTH_TOKEN_EXPIRED`：尝试一次 refresh token 轮换。
- `FORBIDDEN`：停止重试，并隐藏或禁用无权限入口。
- `VALIDATION_FAILED`：展示字段或表单问题，不自动重试。
- `QUESTION_ALREADY_ANSWERED`：刷新首次答题队列。
- `QUESTION_ALREADY_MASTERED`：从错题列表移除该题。
- `NO_UNANSWERED_QUESTIONS`：展示首次答题完成状态。
- `INSUFFICIENT_POINTS`：使用 `details.balance` 刷新余额并展示余额不足。
- `OUT_OF_STOCK`：把商品库存视为 0 并刷新详情。
- `PRODUCT_INACTIVE`：从可兑换列表移除商品。
- `ORDER_INVALID_STATUS`：刷新订单状态。
- `IDEMPOTENCY_CONFLICT`：停止重试，等待用户重新发起操作。
- `CONCURRENT_MODIFICATION`：使用相同 key 和 payload 做有界重试。

未知 4xx 不自动重试。5xx 和网络错误使用有限次数指数退避。日志只能记录 HTTP 状态、错误码和 `requestId`。

## 主要用户流程

未登录用户进入登录页，可以切换到注册页。登录成功后进入首页。

首页展示练习统计和积分余额，并提供练题、错题、商品、订单、积分流水入口。

随机练题页加载一题未答题目。用户选择选项后提交答案，提交期间锁定本次选择。结果页显示对错、正确选项、解析、错误次数、获得积分和最新余额。没有未答题时展示完成状态。

错题页分页展示待练错题。用户进入错题重练流程后，提交答案并根据结果更新列表。若题目已掌握，从待练错题列表移除。

商品页分页展示可兑换商品，支持搜索。商品详情展示库存、所需积分和兑换按钮。兑换成功后进入订单详情或展示新订单摘要。

订单页分页展示我的订单，并可查看订单详情。积分流水页分页展示积分变动记录。

## 测试策略

单元测试覆盖：

- Token 原子替换：安全存储写入成功后才更新进程内 token。
- Refresh single-flight：并发 401 只触发一次 refresh。
- 401 恢复：单个业务请求最多 refresh 并重放一次。
- Refresh 失败：清空 token 并进入未登录状态。
- 幂等写请求：超时和 `CONCURRENT_MODIFICATION` 重试复用同一 key 和 payload。
- `IDEMPOTENCY_CONFLICT`：停止重试并返回可恢复错误。
- 分页状态：使用服务端 meta，越界后回退到最后有效页。
- 错误码映射：稳定错误码转换为明确 UI 动作。

UI 层测试覆盖登录态路由、随机练题结果展示、空题状态、商品兑换错误状态和订单列表分页加载。

## 验收标准

- Android 项目可以通过 Gradle 构建。
- 学生端页面和导航完整，覆盖注册、登录、练题、错题、积分、商品和订单。
- 所有受保护请求携带 Bearer header，公开 token 接口不发送 Cookie 或 CSRF。
- Refresh token 存储在加密存储中，成功刷新后原子替换整组 token。
- 幂等写请求携带并按重试规则复用 `Idempotency-Key`。
- 列表页面遵循服务端分页元数据。
- UI 按稳定错误码做业务分支，不解析中文 message。
- 关键网络、鉴权、幂等和分页行为有自动化测试保护。
