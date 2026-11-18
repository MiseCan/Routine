# Routine动效

## AndroidX 页面标题栏

看板使用 Material 3 `MediumTopAppBar` 和 `exitUntilCollapsedScrollBehavior`，与 Purpose Gate 一致。上滑时标题收起到设置按钮所在行。AI 聊天、设置、关于和计划详情使用 `TopAppBar`。各页由 `Scaffold` 布局，标题栏随整个页面参与 Miuix 转场。下文 0.13.x 的自定义顶栏动效为历史记录。

## 0.15.0：Routine / com.routine

应用显示名称、源码类型与命名空间、提醒 Action、私有偏好文件、密钥别名、AI 内置提示词、辅助脚本和本地工程名称统一为 Routine。Android applicationId 与 namespace 为 `com.routine`，源码目录为 `app/src/*/java/com/routine`，GitHub 仓库为 [MiseCan/Routine](https://github.com/MiseCan/Routine)。

包名改变后 Android 会将其识别为另一个应用，旧应用的计划、聊天和密钥不自动迁入。本轮未操作手机，也没有修改历史安装包的二进制内容。

已通过 APK 元数据检查：应用名称为 `Routine`，包名为 `com.routine`，启动类为 `com.routine.MainActivity`，版本为 0.15.0（25）。清理后重新构建、159 项 Android 本地测试及 15 项历史服务测试全部通过；Lint 为 0 错误、47 项警告。当前源码、资源、脚本和文档已无旧品牌或旧包名；Git 历史和历史 APK 保留原始内容。

本地安装包：`dist/Routine-0.15.0.apk`；SHA-256：`9186941546094DAD6E1510360159434A78DDC85C15B9F7EBA612ABCD59DBDFAD`。尚未安装手机或发布新版本。

## 0.14.0：直接接入 Miuix Nav

按用户 2026-11-06 的明确要求，导航运行时改为 `top.yukonga.miuix.kmp:miuix-nav-android:0.9.4`。以下 0.13.x 部分保留为历史记录，其自写页面转场已移除。

- `NavDisplay` 直接使用库内置 `NavTransitions.MiuixDefault`；程序切页、前后层视差、遮罩、系统圆角裁切、手势进度及释放后的物理动画均由 Miuix 处理。
- `NavDisplayEffects` 使用库的系统圆角读取，背景跟随 Material 配色，其余效果沿用库默认配置。
- 返回手势通过库提供的 `swipeDismiss` 开启并按 RTL 镜像；系统预测式返回也由库处理，没有添加第二个手势驱动器。
- 看板与 AI 聊天共享同一个根条目，互切使用 Compose 原生 `Crossfade`；不会增加 Miuix 栈深度，也不显示返回箭头。根条目之外才是设置、关于及计划详情。
- 应用只保存顶层标签、路由和稳定 ID；Miuix 管理条目状态及生命周期。两页分别保存列表和草稿状态，恢复后仍能返回到原来的调用页。
- 删除 `PageTransitions.kt`、`MotionPages` 及 Navigation Compose 依赖，应用中没有复制 Miuix 的内部渲染、位移或物理实现。普通 Material 控件继续使用 MD3E。
- 依赖包的 class major version 为 65（Java 21）；编译目标由 17 调整为 21，minSdk 仍为 26。

接入参考：[Miuix Nav 文档](https://compose-miuix-ui.github.io/miuix/guide/miuix-nav)、[Miuix 官方源码及许可证](https://github.com/compose-miuix-ui/miuix)、本地拾页项目的导航接法。实际编译依赖固定为 0.9.4，不以主分支签名代替依赖验证。

验证在电脑上的 Robolectric 原生绘制环境中进行，使用合成界面，不操作手机，不读取用户计划和密钥。

最终构建（应用及 Android 测试 APK）、159 项本地测试通过，0 失败；Lint 为 0 错误、47 项警告。7 项导航界面测试覆盖整页同步移动、顶层状态保留、系统手势两侧预览/取消/提交、Miuix 拖动返回取消/提交、根页面返回桌面、保存恢复、特殊计划 ID 和快速切页。

本地安装包：`历史版本 APK（0.14.0）`；SHA-256：`B14BC06C12D8C95AF31846DE06D8B5911DC5ECD968D0EA4481B8B5CFB19D2C0D`。本轮未覆盖安装手机，未发布到 GitHub。

## 0.13.2 同级页面

看板与 AI 聊天属于同级顶层目的地，互切使用原位交叉淡入淡出，不使用前景覆盖、底页变暗和四分之一屏视差；两个页面都不显示返回箭头。导航沿用官方的 `launchSingleTop`、`saveState` 和 `restoreState` 方案，保留各自草稿和列表状态，反复切换不累计页面。

设置、关于、计划详情的打开与返回继续使用下文的分层侧滑和预测式返回。新增本地界面检查，在切换中途确认同级页面没有横移和返回键，并验证聊天草稿恢复。官方参考：[顶层目的地的状态保存与恢复](https://developer.android.com/guide/navigation/backstack/multi-back-stacks)。

0.13.2 构建与 156 项本地测试通过（0 失败），Lint 为 0 错误、47 项警告；未操作手机。

## 0.13.1 页面转场：与拾页统一

2026-11-06，根据用户指定的本地信息收录应用 **拾页**（`D:\PROGRAM\BUILD\Collection`）核对了 `ui/AppNavigation.kt` 和 `domain/AppearanceSettings.kt`。其默认样式是 `NavTransitions.MiuixDefault`。进一步阅读本地 `Collection-reference-Miuix/miuix-nav` 的 `NavTransitions.kt`、`NavDriver.kt`、`NavSettleEasing.kt`，核对了页面距离和程序切页曲线。

Routine使用现有 **AndroidX Navigation Compose 2.10.2** 的公开 API 表达同类分层侧滑，不新增第三方导航框架：

| 场景 | 当前实现 |
| --- | --- |
| 打开页面 | 前景从右侧全屏宽度进入；底页向左移动四分之一屏，逐步变暗 |
| 点击返回 | 前景退出到右侧；底页从左侧四分之一屏的位置复原 |
| 进入设置、关于、计划详情 | 同一套 `PageTransitions`；顶栏、底栏和内容属于各自页面，共同移动；看板与聊天互切按 0.13.2 同级规则处理 |
| RTL 布局 | 左右镜像 |
| 程序切页 | 500ms；采用与参考项目相同的阻尼阶跃参数 response=.8、damping=.95 |
| 系统预测式返回 | 使用独立 `predictivePopEnterTransition` / `predictivePopExitTransition`；线性跟随系统报告的进度，预览完整上一页 |
| 取消手势 | 由 NavHost 回到原位置，保持当前返回栈和草稿 |
| 完成手势 | 由 NavHost 执行一次返回；返回键采用相同的空间路径 |
| 返回桌面 | 根页面不拦截；清单显式启用 `enableOnBackInvokedCallback`，交给系统处理 |

页面滑动和层间变暗的参数来自用户指定的拾页；手势提交、取消、返回栈及根页面行为由 AndroidX 管理。底页的 90% 透明度与 50% 黑色遮罩合成为等效透明度，前景始终保持不透明。当前实现没有移植 Miuix 的自定义边缘拖动、屏幕圆角裁切和释放速度物理引擎；预测手势的恢复节奏由 AndroidX 决定。

官方指南与实际依赖源码均已核对：

- [Compose 预测式返回配置](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- [预测式返回设计](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back)
- [官方 NavHost API](https://developer.android.com/reference/kotlin/androidx/navigation/compose/package-summary)
- [AndroidX NavHost 源码](https://github.com/androidx/androidx/blob/androidx-main/navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/NavHost.kt)
- 本地从 Google Maven 下载并阅读 **2.10.2 sources.jar** 中的 `NavHost.kt`、`DefaultNavTransitions.android.kt`，确认预测式返回与普通 pop 有独立回调，未使用旧版签名推测当前行为。

本地构建、155 项测试及 Lint 通过（0 错误、47 项警告）。新增 3 项 Robolectric 检查实际运行 AndroidX 导航及页面框架，覆盖顶栏与内容同向同步、实际来源返回、两侧系统手势预览与取消、提交后保留聊天草稿、根页面不拦截系统返回。没有连接手机运行测试或调用真实 AI。

## 组件动效与历史实现

统一使用 AndroidX Compose Material 3 `MotionScheme.expressive()`。位移、缩放与尺寸使用 spatial spring，颜色、透明度与进度使用 effects spring，不另设独立计时器，跟随系统动画时长缩放。

| 场景 | 实现 |
| --- | --- |
| 页面进入、返回、底部导航切换 | 见上述 0.13.1 页面转场；返回栈与页面状态由 Navigation Compose 保存 |
| 顶栏标题与返回键 | 标题前置宽度由同一个 `Transition` 连续变化；返回键保留在布局中，以淡入淡出、缩放和横向裁剪进退，避免导航槽重算间距 |
| 顶栏右侧操作 | 固定48dp槽位，新聊天/设置按钮在同一中心淡入淡出和缩放，不因退场元素移除改变位置 |
| 底部导航显示/隐藏 | 随所属页面一起侧滑，不单独展开导致页面尺寸变化 |
| 看板视图、日期筛选、详情记录切换 | `AnimatedContent` 与 `SizeTransform` |
| 新消息、计划卡片、列表项目增删 | 稳定键、`MutableTransitionState`、进入与收缩退场；退场结束才移除组合 |
| 提示词编辑展开/收起、AI 草稿切换 | 尺寸与透明度过渡 |
| 完成/删除确认、任务/计划编辑弹窗 | 使用原生 `Dialog`，通过 Material spring 同步控制表面缩放、透明度、窗口遮罩；关闭结束后执行一次回调 |
| 按钮与发送图标 | 原生 Material 点击涟漪、Expressive 按压形状变化 |
| 计划卡片点击 | 原生涟漪与轻微 spring 按压缩放 |
| 开关、复选框、筛选按钮、输入框焦点、导航选中态 | Material 3 原生状态动效，共用 MotionScheme |
| 任务进度、主题与动态配色变化 | `animateFloatAsState` / `animateColorAsState` |
| 聊天输入框增加行数 | `animateContentSize` |
| 日期/时间选择器 | 保留 Android 原生窗口与控件动效 |

设置图标改用透明的标准 `IconButton`，取消原先 `FilledTonalIconButton` 的常驻圆形底色，保留触控区域和按下反馈。

## 官方依据

- [Android Compose 动画指南](https://developer.android.com/develop/ui/compose/animation/quick-guide)
- [Material Motion](https://m3.material.io/styles/motion)
- [AndroidX GitHub：MotionScheme](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MotionScheme.kt)
- [AndroidX GitHub：AnimatedContent 示例](https://github.com/androidx/androidx/blob/androidx-main/compose/animation/animation/samples/src/main/java/androidx/compose/animation/samples/AnimatedContentSamples.kt)
- [AndroidX GitHub：IconButton 按压形变示例](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/IconButtonSamples.kt)

## 历史验证（0.6）

原有22项逻辑测试与静态检查通过。新增4项设备界面测试：弹窗回调只在退场后执行一次、列表退场后移除、快速反向切页保存草稿、完成说明必填并保存。测试使用独立测试 Activity 的虚构数据，不读写用户任务和密钥。

设备测试命令：安装主程序和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 后运行 `adb shell am instrument -w -r com.routine.test/androidx.test.runner.AndroidJUnitRunner`。
