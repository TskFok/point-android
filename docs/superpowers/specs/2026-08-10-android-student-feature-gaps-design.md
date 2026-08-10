# Android 学生端功能缺口补齐设计

## 1. 背景与目标

本设计用于实现 `docs/requirements/2026-08-10-android-student-feature-gaps.md` 中的 REQ-001 至 REQ-005：

1. 增加独立的预习流程。
2. 增加学习语言偏好，并将筛选条件贯穿首页摘要、首次练习、预习和错题。
3. 将首次练习从单题状态扩展为可回看的题目队列。
4. 在个人中心展示当前用户账号 ID。
5. 为主要业务空状态增加上下文操作。

实现范围限定在 Android 客户端，不新增 Point 服务端 API，不手工修改 OpenAPI 生成代码，不改变积分计算、答题判定、订单和库存规则。

## 2. 现状与约束

当前工程采用 Compose 页面、ViewModel 状态、Repository 数据层和 `GeneratedStudentGateway` 网关。生成的 `DefaultApi` 已经包含：

- `practiceGetPreviewQuestions(count, langCode)`；
- `practiceGetSummary(langCode)`；
- `practiceGetRandomQuestion(excludeIds, langCode)`；
- `practiceListWrongQuestions(page, pageSize, langCode)`。

因此本次只补齐领域模型、网关映射、Repository、ViewModel、导航和 UI。当前工程没有 DataStore 依赖，但已有 SharedPreferences 持久化服务端地址的实现，可使用同样的本地持久化方式。

## 3. 方案选择

### 3.1 采用方案

采用“共享语言偏好 StateFlow + 现有 Repository 扩展 + 独立预习会话 + 首次练习队列”的组合方案。

### 3.2 未采用的方案

- 仅在页面进入时读取语言偏好：改动较少，但语言切换后容易保留旧摘要、旧题目或旧分页，无法满足数据失效要求。
- 新增全局学习会话协调器：可以集中管理状态，但会让首页、练习、预习、错题和个人中心共享过多生命周期，增加耦合，不符合本需求的最小改动原则。

## 4. 架构设计

### 4.1 语言偏好领域模型

新增客户端领域枚举 `LearnerLanguage`：

| 值 | API code | 显示文本 |
|---|---|---|
| `ALL` | `null` | 全部语言 |
| `EN` | `en` | 英语 |
| `JA` | `ja` | 日语 |
| `IT` | `it` | 意大利语 |
| `FR` | `fr` | 法语 |
| `DE` | `de` | 德语 |

`ALL` 是默认值，调用生成客户端时省略 `langCode` 查询参数；绝不发送空字符串或非法值。

新增 `LearnerLanguageStore` 接口，暴露：

- `StateFlow<LearnerLanguage>` 当前选择；
- 设置新语言的方法。

由 `SharedPreferencesLearnerLanguageStore` 实现持久化。读取时无法识别的历史值按 `ALL` 处理；保存 `ALL` 时移除键。`AppContainer` 创建单例并通过 `AppDependencies` 注入，个人中心和各学习 ViewModel 使用同一实例。该偏好是客户端设置，不因退出登录清除，因此应用重启或重新登录后仍可恢复。

### 4.2 数据层与生成客户端边界

`StudentGateway` 和 `PracticeRepository` 使用 Android 领域类型，不向业务层暴露生成 DTO 或生成的接口枚举。`GeneratedStudentGateway` 内部将 `LearnerLanguage` 映射到每个生成 API 方法对应的枚举类型：

- `DefaultApi.LangCodePracticeGetSummary`；
- `DefaultApi.LangCodePracticeGetRandomQuestion`；
- `DefaultApi.LangCodePracticeGetPreviewQuestions`；
- `DefaultApi.LangCodePracticeListWrongQuestions`。

Repository 的读操作携带语言参数：摘要、随机题、预习题和错题列表。答题仍复用已有首次答题接口和错题重练接口。答题请求保留当前 `AuthorizedCallExecutor`、`RetryExecutor` 和错误映射边界。

为使 UI 的“提交失败后重试”保持同一幂等语义，答题会话项在第一次提交前生成并保存一个幂等键；Repository/`RetryExecutor` 支持传入已有键，网络重试和 UI 重试均复用该键。已经得到结果的题目不会再次调用答题接口。

### 4.3 语言变更的数据失效

- `HomeViewModel` 观察语言 StateFlow。语言改变时清空旧摘要和旧错误，重新请求摘要与余额；旧请求通过请求代次检查，不能覆盖新语言结果。
- `WrongQuestionsViewModel` 观察语言 StateFlow。语言改变时清空已合并的分页、选中错题和加载更多错误，从第一页重新加载。
- `QuestionViewModel` 为首次练习和预习会话记录创建时的语言。语言改变时取消当前读取/提交任务，清空旧队列和旧结果，并按新语言重新开始该会话；不能在新语言下继续显示旧题。
- 个人中心只负责写入偏好并展示当前选择，保存失败时保留内存状态并显示可重试错误；本地 SharedPreferences 正常写入时无需服务端请求。

## 5. 预习流程设计

### 5.1 导航与状态

新增 `AppRoute.Preview`，从 `PracticeHubScreen` 的“预习”按钮进入。预习是独立路由，不复用首次练习或错题重练的入口语义。

新增 `PreviewUiState`，阶段为：

- `SETUP`：显示数量选择；
- `QUIZ`：显示题目队列和当前索引；
- `SUMMARY`：显示本轮统计。

状态另包含加载中、提交中、加载错误、提交错误、无可预习题、题目列表、当前索引和完成统计所需的数据。

### 5.2 数量与取题

- 数量合法范围为 1–50；非法数量不可开始。
- 默认提供 5、10、20 三个快捷选项，并允许输入自定义数量。
- 点击开始时调用 `practiceGetPreviewQuestions`，传递数量和当前语言；题目按服务端返回顺序保存，不在客户端重新排序。
- `NO_UNANSWERED_QUESTIONS` 映射为预习业务空状态，而非通用网络错误。

### 5.3 答题与重试

每个 `PreviewItem` 保存：题目、选择项、幂等键、提交选项、提交结果、提交错误和“已在其他地方完成”的跳过标记。

- 未提交且有选择时才允许提交。
- 提交成功后保存完整 `AnswerResult`，显示正确性、正确答案、解析、积分和余额；再次切换回来仍为只读。
- `QUESTION_ALREADY_ANSWERED` 标记该题为跳过，不重复发放积分，并允许继续下一题。
- 其他提交失败保留选择、幂等键和错误，显示“重试提交”；重试不创建新的会话项。
- 上一题允许回看；下一题只在当前题已完成、已跳过或提交错误可重试的状态下按 Web 语义推进。已加载题目之间只改变索引，不发网络请求。

### 5.4 完成统计与再来一轮

所有题目均已成功提交或标记跳过后进入完成统计，展示：

- 总题数与正确题数；
- 跳过题数；
- 本轮所有成功答题的 `pointsAwarded` 之和；
- “再来一轮预习”按钮；
- 返回练习/学习页按钮。

再来一轮清空题目、结果、错误和当前索引，回到数量选择，不复用上一轮已提交状态；当前语言继续从共享偏好读取。

## 6. 首次练习队列设计

### 6.1 会话模型

将当前单题字段重构为队列模型：

```text
QuestionQueueItem
  question
  selectedOptionId
  submissionKey
  submissionOptionId
  result
  submitError

QuestionUiState
  mode
  queue: List<QuestionQueueItem>
  currentIndex
  loadingInitial
  loadingNext
  submitting
  completed
  error
```

为降低 UI 迁移成本，`QuestionUiState` 可提供当前题目的派生属性，但真实数据只保存在队列项中。

### 6.2 前进、后退与取题

- 第一题禁用上一题。
- 已加载队列内的上一题/下一题只改变 `currentIndex`，不读取网络。
- 当前题的已选选项、已提交结果、正确答案、解析、积分和错误均保留在对应队列项。
- 到达队尾点击下一题时，才调用随机题接口；`excludeIds` 使用当前会话全部已加载题目 ID，防止未提交题目也被重复取回。
- 随机题成功后追加队列并前进；取题失败保留当前题和错误，可单独重试，不清空队列。
- `NO_UNANSWERED_QUESTIONS` 设置 `completed`。已有队列时保留最后一道题供回看并显示“已到达队尾”；初始即无题时显示完成/空状态页及下一步操作。

### 6.3 提交约束

- 每个队列项最多产生一个逻辑提交；已有 `result` 的题目不可再次提交。
- 提交中的题目锁定选项和导航相关动作，避免并发提交。
- 提交失败只更新当前队列项的错误和提交状态，允许使用原幂等键重试。
- 切换到已提交历史题目不会调用答题接口，不会重复加分或扣分。
- 新建 `QuestionViewModel`/新建首次练习路由时创建全新队列和结果。

## 7. 个人中心与空状态

### 7.1 个人中心

账户卡片展示用户名、学生角色、积分和完整 `User.id`，明确标识为“账号 ID”。使用可选择文本或等价的完整展示方式，满足复制需求。用户信息来自 `SessionState`；退出或账号切换时用户整体置空/替换，不能沿用旧账号 ID。

语言选择控件显示当前 `LearnerLanguage`，修改后立即更新共享 StateFlow 和本地持久化。

### 7.2 上下文空状态

扩展 `AsyncContent` 支持页面传入自定义空状态内容，同时保留通用默认空状态。错误分支仍使用现有重试入口。

- 首页没有待练错题时：提供首次练习和预习入口。
- 错题列表为空时：提供首次练习或预习入口。
- 订单为空时：提供进入商城入口。
- 预习没有可用题目时：提供返回练习入口，并在适用时提供进入个人中心切换语言入口。
- 首次练习没有未答题时：提供错题/预习等下一步入口。

所有空状态至少有说明文本和一个按钮；点击导航使用现有 `NavController`，不改变底部导航的选中和恢复策略。

## 8. 错误、并发与生命周期

- 所有网络调用继续通过现有授权刷新、读重试、幂等写重试和 `UiErrorMapper`。
- ViewModel 对加载和提交使用请求代次/会话代次检查，旧请求完成后不得覆盖语言切换、账号切换或新一轮会话的状态。
- 协程取消继续向上抛出，不被转换为业务错误。
- 预习、首次练习和错题分页的错误状态与业务空状态分别建模；错误只能重试，不能误导用户跳转。
- 语言偏好解析失败回退到 `ALL`，不会导致启动崩溃。

## 9. 测试设计

按 TDD 逐项执行“先写一个会因缺少行为而失败的测试，再写最小实现”：

1. 语言模型和 SharedPreferences：默认全部语言、合法值恢复、非法值回退、全部语言移除存储键。
2. Gateway：预习数量/语言查询、摘要/随机题/错题的语言查询，以及全部语言不带参数；维持 DTO 到领域模型映射。
3. Repository：语言参数透传、预习读取、幂等键在网络重试和 UI 重试间保持一致。
4. 预习 ViewModel：数量校验、题目顺序、成功结果、解析、跳过、提交失败重试、完成统计、再来一轮清空状态。
5. 首次练习 ViewModel：队列追加、排除已加载 ID、上一题/下一题回看、选项保留、历史题不重复提交、无题完成态、新会话清空。
6. Home/Wrong/Profile ViewModel：语言变更重载、旧请求丢弃、分页重置和当前用户 ID 更新。
7. Compose/导航：预习路由可序列化，练习入口可达，上一题/下一题和空状态操作具有可识别语义标签；错误状态保留重试。

最终运行相关 JVM 单元测试、`assembleDebug`/编译检查和已有的相关 Android Compose 测试，并按需求验收标准逐条核对。

## 10. 文件影响范围

预计新增或修改以下业务文件，OpenAPI 生成目录不纳入手工变更：

- 领域/本地设置：`core/model`、`data/preferences`、`AppContainer.kt`、`AppDependencies.kt`；
- 数据层：`StudentGateway.kt`、`GeneratedStudentGateway.kt`、`PracticeRepository.kt`、`DefaultPracticeRepository.kt`；
- 练习：`Routes.kt`、`AppNavHost.kt`、`PracticeHubScreen.kt`、`QuestionUiState.kt`、`QuestionViewModel.kt`、`QuestionScreen.kt`、预习相关新文件；
- 跨页面：`HomeViewModel.kt`、`WrongQuestionsViewModel.kt`、`WrongQuestionsScreen.kt`、`ProfileScreen.kt`、`AsyncContent.kt`、字符串资源；
- 测试：对应 Repository、Gateway、ViewModel、导航和 Compose 测试。

不修改根目录已有的 `task_plan.md`、`findings.md`、`progress.md` 差异盘点文件。

## 11. 需求验收映射

| 需求 | 设计覆盖 |
|---|---|
| REQ-001 | 第 5 节预习路由、数量、答题、结果、错误和再来一轮 |
| REQ-002 | 第 4 节语言模型/持久化/失效，第 7.1 节选择控件 |
| REQ-003 | 第 6 节队列、回看、排重、幂等和完成态 |
| REQ-004 | 第 7.1 节完整账号 ID 和账号切换清理 |
| REQ-005 | 第 7.2 节自定义空状态与错误分支 |
