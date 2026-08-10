# Task 7 Report

## Status

完成。已接通预习路由、导航策略、NavHost destination、PreviewViewModelFactory 注入和学生端入口/空态动作；未处理 Task 8。

## 修改文件

- `app/src/main/java/com/pointquest/android/app/Routes.kt`
- `app/src/main/java/com/pointquest/android/app/AppNavHost.kt`
- `app/src/main/java/com/pointquest/android/feature/practice/PracticeHubScreen.kt`
- `app/src/main/java/com/pointquest/android/feature/practice/PreviewScreen.kt`
- `app/src/main/java/com/pointquest/android/feature/practice/QuestionScreen.kt`
- `app/src/test/java/com/pointquest/android/app/AppRouteSerializationTest.kt`
- `app/src/test/java/com/pointquest/android/app/AppNavigationPolicyTest.kt`
- `app/src/androidTest/java/com/pointquest/android/AppNavigationTest.kt`
- `app/src/androidTest/java/com/pointquest/android/AppNavigationTestShell.kt`
- `app/src/androidTest/java/com/pointquest/android/feature/practice/QuestionScreenTest.kt`
- `app/src/androidTest/java/com/pointquest/android/feature/practice/PreviewScreenTest.kt`
- `.superpowers/sdd/2026-08-10-android-student-feature-gaps/task-7-report.md`

## 红灯测试命令及结果

- `ANDROID_HOME=/Users/ushopal/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.app.AppRouteSerializationTest' --tests 'com.pointquest.android.app.AppNavigationPolicyTest'`
  - 结果：FAIL，`AppRoute.Preview` 未定义导致 `Unresolved reference 'Preview'`。
- `./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.app.AppRouteSerializationTest' --tests 'com.pointquest.android.app.AppNavigationPolicyTest'`
  - 结果：沙箱内首次运行因 `~/.gradle` 锁文件权限失败；升级后未设置 Android SDK 时提示缺少 `ANDROID_HOME`，随后用临时 `ANDROID_HOME` 取得上述红灯。

## 绿灯测试命令及结果

- `ANDROID_HOME=/Users/ushopal/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'com.pointquest.android.app.AppRouteSerializationTest' --tests 'com.pointquest.android.app.AppNavigationPolicyTest'`
  - 结果：PASS，`BUILD SUCCESSFUL in 2s`。
- `ANDROID_HOME=/Users/ushopal/Library/Android/sdk ./gradlew :app:compileDebugKotlin`
  - 结果：PASS，`BUILD SUCCESSFUL in 4s`。
- `ANDROID_HOME=/Users/ushopal/Library/Android/sdk ./gradlew :app:compileDebugAndroidTestKotlin`
  - 结果：PASS，`BUILD SUCCESSFUL in 1s`。

## Android 导航测试

- `ANDROID_HOME=/Users/ushopal/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest --tests 'com.pointquest.android.AppNavigationTest'`
  - 结果：FAIL，当前 AGP 任务不支持 `--tests`：`Unknown command-line option '--tests'`。
- `ANDROID_HOME=/Users/ushopal/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.pointquest.android.AppNavigationTest`
  - 结果：构建和 AndroidTest 编译通过，执行阶段因无连接设备失败：`DeviceException: No connected devices!`。

## commit SHA

待提交后回填。

## concerns

- 未继续等待或重跑 connected AndroidTest；当前环境无连接设备。
- 为覆盖简报中的 `Preview -> Practice/Profile` 和 `First 完成 -> Wrong/Preview/Practice`，除简报列出的文件外，最小修改了 `PreviewScreen`、`QuestionScreen` 及其 Android Compose 测试。
