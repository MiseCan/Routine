# AndroidX / Material 3 Expressive 检查记录

日期：2026-11-05；本地版本：0.13.0（21）。按项目 `AGENTS.md` 执行：检查现状和构建 → 查官方源码及 Samples → 规划结构 → 修正实现 → 本地验证。

## 版本与 API 核实

| 项目 | 实际版本 |
| --- | --- |
| Gradle / Android Gradle Plugin | 9.8.1 / 9.4.1 |
| Kotlin Compose 编译插件 | 2.4.21 |
| Compose UI / Foundation | 1.12.0 |
| Material 3 | 1.5.0-beta01 |
| Navigation Compose | 2.10.2 |
| Lifecycle Runtime Compose | 2.11.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

在本机 Gradle 缓存的 `material3-android-1.5.0-beta01-sources.jar` 中核对了 `MaterialTheme.kt`、`MotionScheme.kt`、`Button.kt` 和 `IconButton.kt`。现有版本实际提供 `MaterialExpressiveTheme`、`MotionScheme.expressive()`、spatial/effects 动画规格和按钮交互形状 API，因此保留该版本。技能资料推荐的 beta02 不作为未经验证的整体升级理由。

MD3E 的主题与交互形状需要公开的 `ExperimentalMaterial3ExpressiveApi`；当前项目显式 opt-in，并通过实际依赖编译检查。部分布局使用公开的 `ExperimentalLayoutApi`。没有引用 AndroidX internal/tokens 包，也没有复制其内部实现。后续升级需重新核对实验 API。

## 官方源码与 Samples

以下阅读的是实现或样例代码，而非只读 README；主分支用于理解模式，具体调用签名以项目固定版本的源码及编译结果为准。

| 模块 | 来源 | 用于本次检查 |
| --- | --- | --- |
| Foundation | [LazyDsl.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyDsl.kt)、[LazyDslSamples.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/LazyDslSamples.kt) | 按需布局、稳定 key、contentType |
| Material 3 | [MaterialTheme.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialTheme.kt)、[NavigationBarSamples.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/NavigationBarSamples.kt) | 主题入口、底部导航与组件状态 |
| Animation | [AnimatedContent.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/src/commonMain/kotlin/androidx/compose/animation/AnimatedContent.kt)、[AnimatedContentSamples.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt) | targetState 内容、进入退出、弹簧规格 |
| Adaptive | [ThreePaneScaffold.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/adaptive/adaptive-layout/src/commonMain/kotlin/androidx/compose/material3/adaptive/layout/ThreePaneScaffold.kt) | 阅读布局适配思路；未复制内部实现，未宣称实现三栏 |
| Navigation | [NavigationSamples.kt](https://github.com/androidx/androidx/blob/androidx-main/navigation/navigation-compose/samples/src/main/java/androidx/navigation/compose/samples/NavigationSamples.kt) | 返回栈、顶层页面状态保存和恢复 |
| Integration tests | [DemoActivity.kt](https://github.com/androidx/androidx/blob/androidx-main/compose/integration-tests/demos/src/main/java/androidx/compose/integration/demos/DemoActivity.kt) | 主题与状态组织方式 |

官方镜像：[androidx/androidx](https://github.com/androidx/androidx)；[Google AndroidX 源码](https://android.googlesource.com/platform/frameworks/support/)。

## 页面结构与实施结果

保持手机优先的看板、AI 聊天、设置结构。看板右上角进入设置；设置可进入关于；所有计划进入计划详情。宽屏内容居中，最大宽度 840dp；本次没有新增三栏导航。

- **状态层**：新增 `RoutineState` 与 `PlannerUiState`，任务、外观、AI 状态通过 ViewModel + StateFlow 暴露，界面按生命周期收集。AI 协程采用 `viewModelScope`。
- **导航**：使用 Navigation Compose 管理顶层页面和详情返回栈；切换看板与聊天保存状态，设置和关于按实际来源返回。
- **看板与计划**：今日任务、所有计划改为 LazyColumn，使用任务或计划 ID 作为稳定 key。日期、进度、筛选和列表有不同层级；筛选可换行。
- **聊天**：消息使用 LazyColumn 和持久消息 ID，相同时间戳不会导致 key 冲突。长期计划每页预览七天，支持前后翻页，确认仍保存完整每日任务。
- **滚动**：移除计划选择弹窗的内外双重滚动。对话按钮可换行；不把长任务列表一次性放进普通 Column。
- **主题与动效**：沿用真实的 MaterialExpressiveTheme、动态取色、深浅主题和 MotionScheme；导航使用同一套弹簧与透明度规格，补齐自定义深色容器的文字配色。

业务行为保持：计划先预览再确认，写操作有确认，完成任务需要说明，删除计划同步删除每日任务。密钥不加入普通状态保存或测试资料。

## 验证范围

本地执行：

```powershell
.\gradlew.bat --offline --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

- 应用 APK 和 Android 测试 APK 编译通过。
- 152 项 JVM 单元测试通过，0 失败、0 错误；覆盖计划格式、重复任务、完成记录、操作协议、提示词隔离等。
- Lint 无错误；仍有依赖更新、既有图片方向读取及代码简化等警告，未将其描述为全部清零。
- 新增 6 项 Android UI 检查源码，覆盖导航恢复、1000 项任务、300 个计划、相同时间戳的长聊天、个性化设置及只读提示词；只使用隔离的合成资料。
- 实机检查启动后，用户要求“不用在手机测试”，已停止，不计入通过结果，也不声称完成全部页面的视觉、触摸或动画验收。停止测试导致的 instrumentation 进程中断不是本地编译失败。
- 未调用真实付费 AI；没有以测试结果代替真实模型的端到端验收。

安装包：`历史版本 APK（0.13.0）`。这是调试签名的本地内测包；本次未发布到 GitHub。

最终 APK SHA-256：`B340CC0CDCA9A35E7ED26CBAAA46164B50767CFAD07DB7E79F0D6822BC10B9BA`。
