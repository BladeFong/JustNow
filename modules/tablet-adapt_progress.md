# 进度日志

### 2026-07-18 — 审查修复：主题系统去紫色、平板横屏布局恢复
- 主题系统改造：themes.xml colorPrimary 从 purple_500 改为 theme_blue；新增 Theme.JustNow.Pink 变体；MainActivity 在 onCreate 前 setTheme(resolveThemeStyle())；resolveThemeStyle() 用 sp.contains() 区分未设置和明确选蓝色，修复平板默认粉色覆盖用户选择
- 恢复 adjustRightPanelForOrientation()：平板横屏 rightPanel→HORIZONTAL（任务列表左+花朵栏右）；calcMaxDisplayItems() 移到方向调整之后
- 花朵收集栏拆为 RewardBarFragment，用 FrameLayout 占位+程序化挂载替代 FragmentContainerView，避免平板测量异常

### 2026-07-18 — 代码审查
- 代码审查记录于 `docs/code-review-20260718.md`，涉及平板适配和国际化相关问题共 3 项。

### 2026-07-17 — 主界面底部时段栏 Insets 适配与陈旧文档清理
- 在 `MainActivity.java` 中为 `FragmentContainerView` (navHostFragment) 添加 `ViewCompat.setOnApplyWindowInsetsListener` 监听。根据 `android-view-systembar` 的最佳实践，在 WindowInsets 发生变化时，动态将 `navigationBars().bottom` 设定为其 `paddingBottom`，从而精确、全局地分发导航栏/手势区 inset，避开底部遮挡。
- 移除了先前在 `MainFragment.java` 中单独对 `bottom_period_bar` 设置的 insets 监听器，防范双重消费（double padding）。
- 清理并删除了已被 7-16 迭代文档完全覆盖的陈旧平板 M2 初始设计文档 `docs/superpowers/specs/2026-07-15-tablet-m2-layout-design.md`。

### 2026-07-17 — 任务卡片内置图标尺寸由 24dp 升级为 40dp 且与两行行高齐平
- 在 `dimens.xml` 中引入 `task_icon_size` 并定义为 40dp，将 `item_task_content.xml` 里的内置图标 `ImageView` 的宽高升级为该大小。
- 升级后，彩色矢量卡通图标在垂直方向上能够基本占满任务卡片右侧两行文本（第一行时间/标签，第二行标题）的高度，在视觉上极大增强了拟物细节与高质感细节。
- 编译及各侧设备展示逻辑测试完全通过。

### 2026-07-17 — 平板模式横竖屏主页左右栏自适应比例微调与横屏四列重构
- 引入了 `main_left_panel_weight` 和 `main_right_panel_weight` 权重资源限定符。
- 为手机端（默认）设置权重比例为左侧 1.0、右侧 1.618，保持原有视觉比例。
- 为平板端竖屏（`values-sw600dp`）设置比例为左侧 1.0、右侧 3.0，使任务列表占用更多显示宽度（展示 2 列网格）。
- 为平板端横屏（`values-sw600dp-land`）设置比例为左侧 1.0、右侧 5.0，大幅缩窄左侧时间线宽度，使右侧占比提升至 83.3%，以舒适宽敞地容纳 4 列任务网格。
- 修改主页 Page 0 布局 `fragment_main_page0.xml` 以引入此自适应权重，且横屏下 task_grid_span_count 升为 4 列。


### 2026-07-16 — 平板端儿童居家兴趣图标高质感多色矢量化重构
- 重构并替换了 10 个内置儿童兴趣图标，全部重写为高质感、色彩丰富且具有丰富立体/拟物感的多色 XML。
- 经由 `./gradlew assembleDebug` 验证，编译顺利通过。

### 2026-07-16 — 平板端儿童兴趣图标多语言翻译及标签自动关联功能交付
- 实现内置图标在选择时自动填充对应标签、反选时自动清除标签的联动逻辑。
- 排除机制落地：在 `TagDao.java` 层面过滤排除了这 10 个内置标签，使其绝不显示在主界面及录入页常用标签栏中。
- 多语言映射落地：新建 `TagLocalizer.java` 网关，在 strings.xml 简繁英各语系配置标签文本。实现了存库时自动还原为唯一中文简体简称，展示及输入时根据设备语言动态翻译（仅针对内置图标标签，自定义标签保持原样）。
- 编译、中英文转换规则测试和全部 Repository 单元测试均已顺利运行通过。

### 2026-07-16 — 平板端横竖屏放开与儿童兴趣活动图标适配实现完毕
- 落地实现平板屏幕转屏放开及网格自适应（平板横屏3列，竖屏2列，手机1列）。
- 扩展 TaskEntity 实体类 `icon_name` 属性，并将数据库升级为版本 8，编写 `MIGRATION_7_8` 支持无损升级 and Schema 导出。
- 引入了积木、阅读、画笔、乐器、皮球、益智棋牌、手工、动画、作业、家务等 10 个标准的儿童兴趣活动 SVG 矢量图资源。
- 改造列表适配器 `TaskAdapter`，在任务卡片左侧四象限彩色边条后支持 ImageView 动态绑定显示内置图标（无图标则隐藏占位）。
- 在任务编辑界面 `TaskEditFragment` 增加平铺的图标单选网格选择器，平板横屏平铺 10 列，竖屏/手机 2 行 5 列展示；且在运行时判断仅在平板端显示图标选择器，手机端强制隐藏。
- 在主界面右上角菜单中临时加入了“查看内置图标”的选项，点击后会在手机/平板上弹出包含 10 个儿童矢量图和文字释义的平铺预览对话框。
- 编译及单元测试全部通过。

### 2026-07-16 — 平板端横竖屏放开与儿童兴趣活动图标适配脑暴
- 开展平板端转屏放开与儿童兴趣活动图标方案的脑暴设计。
- 规划通过资源限定符 `is_tablet` 配合 Application 生命周期在运行时动态限制手机竖屏、允许平板转屏的方案。
- 规划任务列表 Grid 列数在平板横屏（3列）、平板竖屏（2列）、手机竖屏（1列）的资源自适应方案。
- 精选并定义 10 个儿童居家暑期/寒假场景下的核心兴趣活动内置矢量图标，并设计任务实体扩展 `icon_name` 字段以及 Room 数据库升至版本 8（提供无损 Migration 脚本）。
- 设计新建任务界面中图标选择器在横屏（单行10列）、竖屏（双行5列）下的对称平铺布局，以及图标的单选、反选保存交互。
- 完成设计 Spec 文档编写并更新项目文档。
