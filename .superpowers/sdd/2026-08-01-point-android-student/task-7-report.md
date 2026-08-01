# Task 7 报告：商品兑换与订单数据仓库

## 状态

实现与验证已完成。

## 实现

- 新增 `ProductsRepository` 与 `OrdersRepository` 接口及默认实现。
- 商品与订单读取均组合 `RetryExecutor.executeRead`（外层）和
  `AuthorizedCallExecutor.execute`（内层）；每次授权执行只调用一次 `StudentGateway`。
- 商品列表固定 `pageSize=20`，空白搜索值规范为 `null`；网关已固定 `isActive=true`。
- 兑换通过 `RetryExecutor.executeIdempotent` 冻结 `RedeemPayload(productId)` 与 UUID key，
  因此并发修改重试、以及授权层的 401 重放均复用同一 payload/key。稳定业务错误和
  `IDEMPOTENCY_CONFLICT` 由重试执行器原样返回，不额外重试。
- `ProductImageUrlFactory` 默认采用 `BuildConfig.IMAGE_BASE_URL`。它仅接收
  `products/<小写 canonical UUID>.png`，并要求图片 base URL 为带尾斜杠的根 HTTP(S) origin。
  它用 OkHttp `HttpUrl.resolve` 解析，且复核最终路径精确为
  `/uploads/products/<uuid>.png`。

## TDD 与验证

- RED：先添加三份任务测试；过滤测试在生产类型不存在时于测试编译阶段失败，错误为
  `ProductImageUrlFactory`、`DefaultProductsRepository`、`DefaultOrdersRepository` 与接口方法未解析。
- GREEN：最小实现后，任务过滤命令通过，11 个测试、0 失败。
- 全量：`ANDROID_HOME=/Users/ushopal/Library/Android/sdk JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:testDebugUnitTest`
  通过，130 个测试、0 failure、0 error、0 skipped。

## 覆盖范围

- 图片 URL：合法 UUID key，绝对 URL、路径穿越、编码路径、反斜杠、query/fragment、seed、
  大小写或扩展名错误、额外路径段、非规范 UUID，以及非法、普通或编码路径、无尾斜杠的 base URL。
- 商品：空白搜索、固定分页参数、非空搜索、读取网络重试与稳定业务错误原样透传。
- 订单：并发修改重试时 UUID key 与 productId 不变；`INSUFFICIENT_POINTS`、`OUT_OF_STOCK`、
  `PRODUCT_INACTIVE`、`IDEMPOTENCY_CONFLICT` 不重试且原样透传；固定分页参数及三种订单状态。

## Concerns

- 无已知阻塞项。
