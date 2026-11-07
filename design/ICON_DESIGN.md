# Routine应用图标

原创的“日光＋完成”标记：圆润的日程页、右上角日光圆点与镂空勾选。纯矢量路径，不含文字、阴影或预先裁切的外框。

- 前景和背景均为108dp画布，关键图形位于中心66dp安全区域内。蒙版由启动器处理。
- Android 12及以上，普通adaptive-icon的背景、主体和点缀直接引用系统主色板，与本地PurposeGate实现一致。浅色分别使用accent1的100/700/600色阶，深色为800/100/300；不是写死的蓝色或绿色。
- Android 13及以上同时提供纯白透明的monochrome图层。桌面采用主题图标时，最终色调仍由桌面决定；普通层动态色是兼容回退，不保证覆盖第三方图标包自行指定的颜色。
- 桌面图标与应用内动态取色开关独立。图标颜色改变后，部分桌面可能需要刷新图标缓存。
- SVG与预览图展示的是造型和示意配色，运行时颜色以设备系统色板为准。

参考：[Android主题图标规范](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)、[官方动态色资源说明](https://codelabs.developers.google.com/codelabs/apply-dynamic-color)。
