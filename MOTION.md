# 日序 0.6 动效

统一使用 AndroidX Compose Material 3 `MotionScheme.expressive()`。位移、缩放与尺寸使用 spatial spring，颜色、透明度与进度使用 effects spring，不另设独立计时器，跟随系统动画时长缩放。

| 场景 | 实现 |
| --- | --- |
| 页面进入、返回、底部导航切换 | `AnimatedContent`，按层级判断方向，短距离位移和淡入淡出；页面状态由 `SaveableStateHolder` 保留 |
| 顶栏标题与返回键 | 标题前置宽度由同一个 `Transition` 连续变化；返回键保留在布局中，以淡入淡出、缩放和横向裁剪进退，避免导航槽重算间距 |
| 顶栏右侧操作 | 固定48dp槽位，新聊天/设置按钮在同一中心淡入淡出和缩放，不因退场元素移除改变位置 |
| 底部导航显示/隐藏 | `AnimatedVisibility`，淡入淡出和尺寸展开/收起 |
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

## 验证

原有22项逻辑测试与静态检查通过。新增4项设备界面测试：弹窗回调只在退场后执行一次、列表退场后移除、快速反向切页保存草稿、完成说明必填并保存。测试使用独立测试 Activity 的虚构数据，不读写用户任务和密钥。

设备测试命令：安装主程序和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` 后运行 `adb shell am instrument -w -r com.daystep.app.test/androidx.test.runner.AndroidJUnitRunner`。
