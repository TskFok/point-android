# B「纸感专注」实施计划

> **For agentic workers:** 使用 superpowers:subagent-driven-development 执行；用户已选择 B，实施期间自主推进。

**Goal:** 在现有 Android 学生端完整落地暖纸白、陶土红、衬线标题与克制列表组成的 B 方案。

**Architecture:** 沿用 Compose Material 3，以完整语义主题、共享组件和页面排版共同替换旧视觉。屏幕状态、导航回调、Repository 与 ViewModel 保持原有契约。

**Tech Stack:** Kotlin 2.1.20、Compose BOM 2025.05.01、Material 3、Android API 26–35。

**Spec:** docs/design/2026-09-15-modern-style-options.md（用户已确认 B）。

## 全局约束

- 直接在 master 当前分支修改，不创建分支、不提交、不推送。
- 保留 AppNavHost.kt 中会话更新不重置导航的已有修改及 SessionUpdateNavigationTest.kt。
- 仅改视觉；保留现有回调、testTag、字段验证、积分不足/缺货、分页、错误和重试逻辑。
- 主色 #A33E26，背景 #F6F2E9，表面 #FFFCF5，正文 #292924，次级 #686155，分隔线 #DBD4C8。
- 标题使用有许可的中文衬线字体，正文为系统无衬线；主卡圆角 12dp，按钮 8dp；按钮最小 48dp、选项最小 64dp。
- B 以已选浅纸色为默认，不增加主题选择设置；系统栏应与浅背景匹配。
- 不加入签到、排行榜、头像上传、分类、购物车等不存在的业务。

## Task 1：主题与基础组件（主代理）

Files: core/ui/theme/{Color,Theme,Type,Shape}.kt，core/ui/components/PointScaffold.kt、AsyncContent.kt、PagedListFooter.kt，AppNavHost.kt（仅底栏）、字体资源和许可。

- [x] 完整映射 ColorScheme 的容器、边线、逆色和表面角色；状态颜色适配纸白。
- [x] 引入静态衬线标题字族，设置 display/headline/title 的明确字阶与行高。
- [x] PointCard 去掉投影，使用细边框和 12dp 圆角；PointPrimaryButton 使用 8dp 圆角、陶土主色。
- [x] PointScaffold 统一安静顶栏、细分隔线与可选返回动作；保持现有调用兼容。
- [x] 底栏用纸色与顶边选中指示，保留 4 个目的地、导航策略与图标语义。

共享接口保留：PointScaffold(title, modifier, bottomBar, content)、PointCard(modifier, content)、PointPrimaryButton(text,onClick,modifier,enabled)。新增返回动作是可选参数。各页面使用 MaterialTheme，避免自建颜色常量。

## Task 2：首页、个人中心、登录注册（代理）

Files: feature/home/HomeScreen.kt、feature/profile/ProfileScreen.kt、feature/auth/{LoginScreen,RegisterScreen}.kt；专属 paper_account_strings.xml。

- [x] 首页实现预览 B 的手记抬头、大号首次答题进度、分隔统计行、单一主操作和紧凑积分条。
- [x] 个人中心用设置行与选择控件表达语言、积分和记录，保留流水及退出入口。
- [x] 登录注册使用衬线标题、分组表单与明确主次动作，保留 Host 配置与全部测试定位。
- [x] 自审状态分支与可滚动性，报告变更和风险；不自行启动 Gradle。

## Task 3：练习系列（代理）

Files: feature/practice 中的 Screen、QuestionContent.kt、AnswerResultCard.kt；专属 paper_practice_strings.xml。不修改 PracticeStatusColors 或 ViewModel。

- [x] 练习入口改为纸感目录，明确首次作答、错题重练、预习的层次。
- [x] 题目与选项用衬线题干、细边框、温和选中背景，保留单选语义与答对/答错文字图标。
- [x] 统一预习数量、上下题、错题状态和空状态的排版，保留全部状态及定位标签。
- [x] 自审公开参数兼容性和交互逻辑；不自行启动 Gradle。

## Task 4：商城、订单、积分（代理）

Files: feature/shop/{ProductListScreen,ProductDetailScreen}.kt、feature/orders/{OrderListScreen,OrderDetailScreen}.kt、feature/points/PointsScreen.kt；专属 paper_commerce_strings.xml。

- [x] 商品列表改为左图右文的纸感列表，突出名称与积分，保留搜索/刷新/分页/差额。
- [x] 商品详情强调商品、库存、余额及兑换确认，保留全部中间状态。
- [x] 订单与积分采用安静的记录列表、对齐数字和状态标签；保持 PointLedgerRow 公共接口供 Profile 使用。
- [x] 自审空/错误/禁用与长文本；不自行启动 Gradle。

## Task 5：集成与验证（主代理与独立审查）

- [x] 运行现有 JVM 测试、编译 instrumentation、打包 Debug、Lint；出现缺陷时增加有意义的回归测试。
- [x] 检查主题实际文字配色对比度与选项状态色，避免只有主色换肤。
- [x] 在可用模拟器上运行现有 UI 测试及关键页面视觉检查；无设备时明确区分编译和设备执行。
- [x] 独立审查全部差异，修复行为与布局回归，记录实际验证结果。
- [x] 更新方案说明与工作记录，交付可安装 Debug APK 和变更摘要。

## 最终验证结果（2026-09-15）

- JDK 17，Android 35 ARM64 / Pixel 6 模拟器。
- `:app:testDebugUnitTest`：301 项，全部通过。
- `:app:connectedDebugAndroidTest`：47 项，全部通过；包含原有会话更新导航回归与新增大字体低高度错误状态滚动测试。
- `:app:lintDebug`：0 错误、47 警告（依赖更新、参数规范及未使用资源等）。
- `./build-apk.sh debug`：通过编译、aapt2 与 apksigner 校验，生成 `debug.apk`。
- 已核验[原生页面截图](../../design/qa/paper/README.md)，并确认正式 MainActivity 冷启动成功。
- 保留用户原有导航变更；未创建分支、未提交或推送。
