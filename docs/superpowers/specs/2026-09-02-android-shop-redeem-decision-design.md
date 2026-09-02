# Android 商城兑换决策补齐设计

## 1. 背景与目标

对照 Web 学生端商城后，Android 已能完成兑换，但学生在决策时看不到余额、差额、兑换后余额，失败重试文案也不明确。本次只补齐这些决策信息，不改兑换入口。

目标：

1. 商品列表展示当前积分、描述、库存状态，以及积分不足时的「还差 N 积分」。
2. 无搜索词的空列表使用「商城正在补充奖励」专用空状态。
3. 商品详情在积分不足时展示差额，兑换按钮保持禁用。
4. 确认框展示当前积分、本次花费、兑换后余额。
5. 可重试失败后确认按钮改为「重试兑换」，并继续复用同一幂等键。

## 2. 约束

- 只改 Android 客户端；不改 Web、服务端 API、OpenAPI 生成代码。
- 不改变积分计算、库存扣减、订单状态规则。
- 兑换仍只在详情页发起；列表点击仍进入详情。
- 成功后仍跳转订单详情。
- 网络/未知错误重试必须复用同一幂等键；取消、成功、积分不足、售罄、下架、冲突仍清除 key。
- 不在循环中查询数据库。

## 3. 方案

采用「列表补决策信息 + 详情补确认明细」：

- 列表并行加载商品与积分余额，差额由 `pointsCost - balance` 在 UI state 计算。
- 详情保留现有 `canRedeem`：积分不足、售罄、下架、余额未知时不能打开确认框。
- 确认框增加三项明细；用 `redeemRetryPending` 区分首次确认与可重试失败后的再次确认。

未采用：

- 列表直接兑换：会复制详情的幂等/错误处理，超出本次范围。
- 积分不足仍可点兑换按钮：与已确认的「禁用确认框」方案不一致。

## 4. 列表

`ProductListViewModel` 增加可选 `PointsRepository`。`initialize()` 与下拉刷新并行请求余额；搜索换页不重新请求余额。

`ProductListUiState` 增加：

- `balance: Int?`
- `balanceFailed: Boolean`

`pointsDeficit(product)`：仅当余额已知、商品上架且有库存、`pointsCost > balance` 时返回差额；否则 `null`。

`AppDataSync.balance` 与当前会话匹配时覆盖列表余额，便于详情兑换成功后列表立即更新。

余额失败不得写入列表主 `error`。从未拿到余额时顶部显示「当前积分：暂不可用」；刷新失败则保留上次余额。

UI：

- 搜索框下方展示当前积分。
- 卡片展示名称、描述、积分、库存文案：`库存 N` / `已售罄` / `已下架`。
- 有差额时展示「还差 N 积分」。
- `search` 空白且 `empty`：标题「商城正在补充奖励」，说明「暂时没有上架商品，继续积累积分，新的奖励很快就会出现。」
- 有搜索词且无结果：继续使用通用空状态。

## 5. 详情与确认框

`ProductDetailUiState` 增加：

- `pointsDeficit: Int?`：余额与售价已知且售价更高时为差额。
- `redeemRetryPending: Boolean`：仅在可重试失败后重开确认框时为 `true`。

积分不足时在兑换按钮附近显示「还差 N 积分」，按钮保持禁用。

确认框文案：

- 当前积分：N
- 本次兑换：N 积分
- 兑换后余额：N 积分（`balance - pointsCost`，不为负）

确认按钮：`redeemRetryPending` 为 true 时显示「重试兑换」，否则「确认兑换」。

`confirmRedeem()` 开始时关闭确认框、`redeemRetryPending = false`。网络/未知错误重开确认框并设 `redeemRetryPending = true`。`dismissRedeemConfirmation()`、成功、积分不足、售罄、下架、冲突将 `redeemRetryPending` 设为 false。

`INSUFFICIENT_POINTS` 若带合法 `details.balance`，更新余额后 `pointsDeficit` 必须立即反映新差额。

## 6. 测试

ViewModel：

- 列表初始化并发加载余额，并对买不起的商品给出差额。
- 余额失败不挡住商品列表。
- `AppDataSync` 余额覆盖列表余额。
- 详情积分不足后 `pointsDeficit` 正确。
- 网络失败后 `redeemRetryPending == true` 且重试复用同一 key；取消后该标记清除。

Compose：

- 无搜索空列表显示「商城正在补充奖励」。
- 卡片显示描述与「还差 N 积分」。
- 确认框显示三项明细。
- `redeemRetryPending` 时确认按钮为「重试兑换」。
