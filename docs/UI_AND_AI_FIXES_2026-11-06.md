# 0.16.0：看板滚动、键盘留白与 AI 选项校验

## 问题与修正

### 看板可滚动面积

原先日期、今日进度、视图切换、日期筛选和行动标题都位于任务列表外，固定占据高度。现在概览和筛选都是 LazyColumn 的普通条目，能随任务滚出屏幕；所有计划也使用同一个列表容纳概览、筛选与计划卡片，没有内外嵌套竖向滚动。应用顶栏和底部导航保留。

### 输入法上方留白

原先 Scaffold 的 padding 没有向内消费，聊天内容又单独应用 imePadding，底部导航在键盘弹出后继续占位。现在由页面 Scaffold 统一负责 IME 避让，内容消费 Scaffold padding，移除聊天子层重复的 imePadding；聊天键盘弹出时隐藏底部导航。

参照 [Android 窗口 Insets 指南](https://developer.android.com/develop/ui/compose/system/insets-ui)，并阅读共享源码中的 `ScaffoldSamples.kt`、`WindowInsetsPaddingSample.kt`。实际编译仍使用项目现有的 Compose 1.12.0 / Material 3 1.5.0-beta01，未因共享源码更新而升级应用依赖。

### AI 报错“value 内容无效”

该错误来自提问选项的 value 字段校验，可能为空白或超长。用户截图没有原始模型 JSON，因此本轮使用相同错误形态的模拟响应复现，没有声称读取了那次真实返回。

修正覆盖三个边界：

- 内置提示词明确 task 未知文字使用空字符串、未知日期使用 null；推荐选项的 label/value 必须是非空字符串。Schema 也加入与校验一致的长度限制。
- 对有明确文字但漏填、留空或返回 null 的选项，使用模型提供的原始 label 作为答案值，不推测日期或补造用户事实。空 value 的“自定义/其他”占位项移除，用户继续使用原有自定义答案输入框。
- 非法对象值、超长内容、缺失有效推荐选项仍被拒绝；错误携带具体字段路径，修正请求指向该字段，用户界面显示可理解的选项错误。仍最多自动修正一次，不循环调用模型。诊断只记录错误码、字段路径、模型和时间，不记录密钥或完整对话。

[DeepSeek JSON 模式文档](https://api-docs.deepseek.com/guides/json_mode/)保证 JSON 形态的配置不能代替应用字段校验；应用继续检查计划日期、归属、操作目标、必需信息及用户确认。

## 本地验证范围

- 实际渲染看板与所有计划，确认滚动后概览离开屏幕，列表使用主要可用高度。
- 使用实际聊天输入框，模拟 300px IME 和系统栏 Insets，确认输入框与键盘之间仅保留 12px 的输入区 padding，并在键盘收起后恢复底部导航。
- 模拟空白/null/缺失 value、自定义占位、非法对象、超长值及未知 task 文字；验证格式兼容不会把不完整任务变成可确认计划。
- 使用已有导航、长计划、提示词隔离和操作校验回归测试。不操作手机，不调用付费模型。

共享 AndroidX 源码现位于 `D:\PROGRAM\BUILD\AndroidX-MD3E`，公共使用说明为 `D:\PROGRAM\MD3E-README.md`，更新脚本为 `D:\PROGRAM\Update-MD3E.ps1`；该副本和更新脚本均已验证。

最终构建、168 项本地测试全部通过（0 失败、0 错误），Lint 为 0 错误、47 项警告。APK 核实为 Routine / `com.routine`，版本 0.16.0（26），可覆盖升级 0.15.0。本轮未安装手机或发布新版本。

安装包：`dist/Routine-0.16.0.apk`；SHA-256：`C4349016587219ECB1F9BE1BCDE553E3F91EACDFDE71414C7B2F7D932AC78AE0`。
