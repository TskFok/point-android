# 登录页远端 Host 配置设计规格

## 背景

当前 Android 客户端在 `AppContainer` 初始化时直接读取 `BuildConfig.API_BASE_URL`，Retrofit、OkHttp 和商品图片地址因此在进程生命周期内固定。用户无法在登录前切换到其他服务端环境。

本次改动为登录页增加服务端根地址配置。用户可以输入完整的根地址，例如 `https://api.example.com/` 或 `http://192.168.1.10:3000/`，保存后立即用于后续登录、注册和商品图片请求，并在下次启动时保留。

## 目标

- 在登录页完成服务端 Host 的查看、编辑和应用。
- 持久化用户最后一次成功应用的 Host。
- 应用 Host 后无需重启 App 或重建 `AppContainer` 即可影响下一次网络请求。
- 保持现有 `/api/v1/...` 接口路径、Bearer 认证、无 Cookie 和图片同源安全校验不变。
- Debug 支持开发环境 HTTP；Release 继续只允许 HTTPS。
- 提供单元测试和 Compose 测试覆盖校验、持久化、请求路由和用户交互。

## 非目标

- 不增加服务端连通性探测或健康检查按钮。
- 不在注册页重复提供 Host 配置；用户可返回登录页调整。
- 不改变登录、注册、令牌刷新或退出登录的业务协议。
- 不允许用户配置接口子路径、代理认证信息、查询参数或片段。

## 设计方案

### 组件职责

新增 `RemoteHostStore` 作为运行时 Host 的唯一来源。它使用 `SharedPreferences` 保存当前地址，并在没有合法持久值时回退到 `BuildConfig.API_BASE_URL`。内存中的当前值需要线程安全地更新，供 UI、网络客户端和图片地址工厂读取。

新增纯逻辑 `RemoteHostValidator`，负责：

- 去除首尾空格；
- 解析 `http`/`https` 根地址；
- 检查 Host 和端口合法；
- 只允许根路径；
- 拒绝用户名、密码、查询参数和片段；
- 在缺少末尾 `/` 且其余部分为合法根地址时补全 `/`；
- 根据构建环境决定是否允许 HTTP。

`RemoteHostViewModel` 只负责登录页 Host 草稿、当前已应用值、校验错误、应用成功提示和应用中状态。`AuthViewModel` 继续只负责登录和注册。

`AppContainer` 初始化一个 `RemoteHostStore`，并把读取当前 Host 的函数注入：

- `ApiClients` 的公开和受保护客户端；
- `ProductImageUrlFactory`。

这样 `AppContainer`、Repository、认证状态和图片加载器都可以保持单例，不需要因 Host 变化而重建。

### 网络请求路由

Retrofit 仍使用构建默认根地址创建生成的 `DefaultApi`，保证生成客户端始终拥有合法的初始 Base URL。公开和受保护 OkHttp 客户端各增加一个动态 Host 拦截器，在请求发出前读取 `RemoteHostStore` 的最新值，只替换请求 URL 的协议、域名和端口，保留生成接口的路径、查询参数以及认证拦截器的行为。

Host 应用成功后，已经发出的请求不被中断；之后创建的请求使用新地址。登录页在提交登录或注册时锁定 Host 配置，避免应用操作和认证请求同时发生。

商品图片工厂从不可变字符串改为支持 Host 提供器。每次生成 `/uploads/{imageKey}` 时读取当前 Host，并继续执行现有的图片 key、UUID、同源路径和无查询/片段校验。

### 登录页交互

登录页认证表单顶部显示“服务端地址”输入框和“应用地址”按钮：

1. 初始值是当前已应用 Host，优先来自本地持久化值，否则使用构建默认值。
2. 用户编辑文本时只更新草稿，不改变实际网络地址。
3. 点击应用地址后执行校验；成功时标准化、保存并更新内存 Host，显示成功提示，用户名输入保持不变。
4. 校验失败时在输入框附近显示简体中文错误，不保存且保留旧的已应用 Host。
5. 用户编辑了未应用的 Host 时，点击登录应提示先应用，不能使用未校验的地址发请求。
6. 登录或注册提交期间 Host 输入和应用按钮禁用。

输入格式为完整根地址：`http(s)://host[:port]/`。允许 `localhost`、IPv4、IPv6 和域名，只要满足 URL 规则；不允许根路径之外的路径。

### 持久化与异常处理

Host 与认证会话分开保存，退出登录不清除 Host。应用启动时读取持久值；如果该值不合法或不符合当前构建的协议策略，则忽略并使用构建默认值。

应用 Host 只做格式校验，不主动探测网络。因此地址可格式正确但服务不可达，后续登录失败时继续使用现有网络错误映射和提示。

### 明文流量边界

Release 继续使用禁止明文流量的网络安全配置，并在运行时拒绝 HTTP Host。Debug 为支持用户输入任意开发服务器地址，Debug 网络安全配置的基础策略允许明文；Release 配置和构建期 HTTPS 校验保持不变。现有 `verifyNetworkSecurityConfig` 任务需要同步调整为验证 Debug/Release 的新边界。

## 测试设计

- `RemoteHostValidatorTest`：合法 HTTPS/HTTP、端口、IPv4/IPv6、末尾斜杠标准化，以及非法协议、路径、认证信息、查询参数、片段和 Release HTTP 拒绝。
- `RemoteHostStoreTest`：默认值、合法持久值读取、非法持久值回退和成功应用后的内存/持久化更新。存储依赖使用可替换的测试实现，避免 JVM 单元测试依赖真实 Android 偏好存储。
- `ApiClientsTest`：请求使用最新 Host，同时保留 `/api/v1/...` 路径；公开客户端仍清除认证头，受保护客户端仍只添加当前 Bearer Token。
- `ProductImageUrlFactoryTest`：Host 更新后生成的新图片 URL 使用新 Origin，并保留现有非法图片 key 拒绝行为。
- `RemoteHostViewModelTest`：草稿编辑、成功应用、失败不覆盖旧值、未应用变更状态和提交锁定。
- 登录 Compose 测试：输入框、应用按钮、错误提示、成功提示、未应用变更阻止登录和至少 48dp 的可访问控件。
- Gradle 网络安全验证：Debug 允许开发 HTTP，Release 仍禁止明文流量。

## 验收标准

- 首次启动登录页显示构建默认 Host。
- 输入合法 Host 并应用后，下一次登录/注册请求发送到该 Host，接口路径仍正确。
- 重启 App 后仍显示上次成功应用的 Host。
- 非法 Host 不会覆盖旧值，也不会发起认证请求。
- Debug 可连接合法 HTTP 开发地址；Release 不能使用 HTTP Host。
- 商品图片请求跟随当前 Host。
- 现有认证、会话、业务页面和测试不回归。
