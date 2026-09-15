# B · 纸感专注：原生页面截图

2026-09-15 在 Android 35、Pixel 6 配置模拟器中通过 Compose 截图测试生成。这里使用测试仓库中的示例账号、题目、积分和商品，不连接线上业务数据。截图来自原生界面，并非 HTML 概念稿。

| 页面 | 截图 |
| --- | --- |
| 登录（最终安装包） | [login.png](login.png) |
| 首页 | [home.png](home.png) |
| 练习目录 | [practice.png](practice.png) |
| 答题 | [question.png](question.png) |
| 正确答案与解析 | [question-result.png](question-result.png) |
| 商店 | [shop.png](shop.png) |
| 个人中心 | [profile.png](profile.png) |

已目视检查标题字形、纸白与陶土红主题、列表分隔、底栏、选项及结果状态。另检查 1.5 倍字体底栏，并通过低高度窗口与 2 倍字体的滚动回归覆盖长错误提示及重试按钮。登录图来自最终安装包 MainActivity，已核验浅色系统栏与冷启动；其余测试宿主截图的系统区域与正式窗口不同。

临时截图测试已从应用测试目录移除；业务回归测试与字体许可保留在项目中。

说明：此 AOSP 模拟器的系统状态栏图标存在底部裁切，系统桌面也能复现。该现象不来自应用布局；应用内容与导航的检查已完成。
