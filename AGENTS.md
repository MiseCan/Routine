# Android 原生应用开发规范：基于 Google AndroidX + Material 3 Expressive

项目名称统一为 **Routine**，Android applicationId 与 namespace 均为 **com.routine**，源码位于 `app/src/*/java/com/routine`。新增代码、资源、提示词、脚本和文档统一使用该名称与包名。

常规构建使用 `build.ps1` 生成未经 R8 裁剪的全量包。推送到手机统一使用 `build.ps1 -Install`，它通过 `:app:installPhone` 构建并安装 R8 裁剪包，沿用现有调试签名以支持覆盖安装。

项目约定更新（2026-11-06）：按用户明确要求，页面导航改用 `miuix-nav` 的 `NavDisplay` 和内置 `NavTransitions.MiuixDefault`，取代下文技术选型中的 Navigation Compose。不得恢复自行绘制的侧滑、遮罩和返回手势；看板与 AI 聊天保持同级，UI 组件继续使用 AndroidX Material 3 Expressive。

你是一名资深 Android 原生开发工程师。请使用 Kotlin + Jetpack Compose 开发 Android 应用，严格遵循 Google 官方 AndroidX 技术体系和 Material 3 Expressive（MD3E）设计规范。

## 1. 核心参考仓库

必须优先参考 Google 官方 AndroidX 源码仓库：

GitHub：
https://github.com/androidx/androidx

官方源码：
https://android.googlesource.com/platform/frameworks/support/

重点研究以下模块，不要只阅读 README：

- Compose Foundation：
  https://github.com/androidx/androidx/tree/androidx-main/compose/foundation

- Compose Material 3：
  https://github.com/androidx/androidx/tree/androidx-main/compose/material3

- Compose Animation：
  https://github.com/androidx/androidx/tree/androidx-main/compose/animation

- Compose Adaptive：
  https://github.com/androidx/androidx/tree/androidx-main/compose/material3/adaptive

- Compose Samples：
  https://github.com/androidx/androidx/tree/androidx-main/compose/integration-tests

## 2. 技术选型

- Kotlin
- Jetpack Compose
- Material 3 / Material 3 Expressive
- AndroidX 官方组件
- MVVM 架构
- ViewModel + StateFlow
- Navigation Compose
- Coroutines
- Gradle Kotlin DSL

优先使用 Google 官方维护的 AndroidX 库，避免不必要的第三方 UI 框架。

## 3. 布局开发规范

重点参考 AndroidX Foundation 中的官方布局实现和 Samples。

根据实际内容选择合适的布局：

- LazyVerticalStaggeredGrid：瀑布流布局
- LazyVerticalGrid：规则网格布局
- LazyColumn：纵向信息流
- LazyRow：横向内容列表
- FlowRow：自适应换行布局
- Scaffold：页面整体结构

布局要求：

1. 不要将所有内容统一设计为相同尺寸的 Card。
2. 合理组合图片、文字、标题、分类、操作按钮。
3. 注重内容密度、视觉层级、留白和滚动体验。
4. 支持不同屏幕尺寸，优先优化手机体验。
5. 避免不必要的嵌套滚动。
6. 列表需要考虑稳定 Key、状态保存和性能。
7. 瀑布流应支持图片不同宽高比，并避免图片加载时产生明显布局跳动。

## 4. Material 3 Expressive 设计规范

优先使用 Google 官方 Material 3 Expressive API。

重点关注：

- MaterialExpressiveTheme（仅在实际依赖提供该 API 时使用）
- Dynamic Color
- Light / Dark Theme
- Material 3 Typography
- Material Shapes
- Expressive Components
- Expressive Motion
- Spring Animation
- 动态形状和组件状态反馈

不要简单通过增加圆角、阴影、卡片和鲜艳颜色来模拟 MD3E。

必须关注：

- 组件不同交互状态
- 视觉层级
- 动态形状
- 弹簧动效
- 页面切换动画
- 滚动行为
- 触摸反馈

## 5. 官方源码使用原则

AndroidX 官方仓库是首要技术参考依据。

开发时：

1. 优先查看对应组件的官方源码和 Samples。
2. 优先使用公开、稳定的 API。
3. 不要直接复制 AndroidX 内部实现到应用代码中。
4. 不要引用内部包、Internal API 或实验性 API，除非明确说明必要性并确认兼容性。
5. 不要凭空编造组件名称、函数、参数或依赖版本。
6. 对于 MD3E 相关 API，必须先检查实际依赖版本和源码。
7. 如果某个 API 不存在，寻找当前版本中最接近的官方替代方案。

## 6. 开发流程

请按照以下顺序执行：

第一步：检查当前项目。
- 查看项目目录。
- 检查 Gradle、Kotlin 和 Android Gradle Plugin 版本。
- 检查 Compose 和 Material 3 依赖。
- 确认当前项目是否能够正常编译。

第二步：研究 AndroidX。
- 检查对应布局的官方源码。
- 检查官方 Samples。
- 确认所需 API 的实际可用性。
- 记录依赖版本和 API 来源。

第三步：设计 UI。
- 先规划页面结构。
- 确定导航方式。
- 确定各页面使用的布局组件。
- 规划主题、颜色、形状、排版和动画。
- 不要在没有规划的情况下直接堆叠组件。

第四步：实现应用。
- 使用 Kotlin + Jetpack Compose。
- 遵循 MVVM。
- 将 UI、状态和业务逻辑合理分离。
- 优先使用官方组件。
- 保持代码模块化和可维护性。

第五步：验证。
- 执行 Gradle 编译。
- 修复编译错误。
- 检查 Material 3 API 兼容性。
- 检查布局溢出、滚动、状态和动画。
- 确保最终项目能够真实运行。

## 7. 最终要求

开发结果必须是可运行的 Android 原生应用，而不是静态 UI 演示。

在每个主要页面完成后，检查实际运行效果，并根据效果调整布局。

如果发现设计或 API 与 Google 官方规范不一致，应优先修正，而不是为了快速完成而使用过时方案。

请先检查项目和官方源码，再开始开发。不要跳过技术验证。
