# 进度日志

### 2026-07-28 — 每时段结束通知 v2：实现完成

- 重写 ReminderScheduler（schedulePeriodEndChecks）、ReminderNotifier（sendPeriodEnd）、AlarmReceiver（handlePeriodEnd）
- 全部通过 BleNotificationSDK 发送通知
- Release 编译安装成功

### 2026-07-28 — 每时段结束通知：需求变更 v2 设计完成

- 改为每时段结束通知（三个时段）+ 最后时段琐碎叠加 + 超时提示，设计文档和模块文档已更新

### 2026-07-25 — 每天未处理任务提醒通知：实现完成

- 5 个文件修改：TaskFilterHelper（提取静态方法）、ReminderNotifier（通知发送）、ReminderScheduler（调度）、AlarmReceiver（接收处理）、TaskRepository（保存触发）
- 通知通过 BleNotificationSDK 发送，复用 task_reminder 渠道
- Release 编译安装成功

### 2026-07-25 — 每天未处理任务提醒通知：设计完成

- 需求确认：两个时机独立判定（时段结束前30分钟/时段结束后），过滤复用主界面逻辑（排除标签筛选），幂等注册
- 模块文档：[modules/unprocessed_reminder.md](modules/unprocessed_reminder.md)
- 进度日志：[modules/unprocessed_reminder_progress.md](modules/unprocessed_reminder_progress.md)
- 附加修正：settings.gradle.kts 添加阿里云 Google Maven 镜像并调整仓库顺序

### 2026-07-24 — 简化蓝牙设备同步菜单入口文本

- 在中/英文及繁体多国语言 `strings.xml` 中将同步菜单入口名称由“绑定接收通知设备”简化为“绑定通知设备”（英文同步简化为“Bind Notification Device”），对齐设计文档。

### 2026-07-24 — 菜单入口文本国际化与模块设计文档状态对齐

- 在 `menu_main.xml` 中将蓝牙设备管理菜单的名称修改为 `@string/menu_ble_device_manager`。
- 在 `values`、`values-zh-rCN`、`values-zh-rHK` 和 `values-zh-rTW` 的 `strings.xml` 中分别添加对应的国际化翻译（“绑定接收通知设备”）。
- 更新并对齐 `modules/ble_sync.md` 设计文档，补充运行时权限绑定、混淆 NPE 踩坑以及菜单国际化的设计细节与技术决策。

### 2026-07-24 — 升级 SDK 依赖并验证 SDK 自动合入混淆规则的完整性

- 从 `app/proguard-rules.pro` 移除 `ML Kit` 和 `CameraX` 的本地规则。
- 升级依赖至包含最新混淆保护 AAR 发布的 `bc32846ed17e0691a015789bc720a5d9b6ee5077`。
- 重新编译 Release 混淆包验证，R8 完美通过，证明 SDK 内部混淆配置自动合并成功。

### 2026-07-24 — 整理应该在 SDK 侧处理的混淆保护规则至配置底部并加注 TODO 标记

- 在 `app/proguard-rules.pro` 底部，将原本为了修复闪退而在 App 侧加入的 SDK 依赖（`ML Kit` 与 `CameraX`）混淆保护规则集中收拢，并添加 `TODO` 详细备忘注释，以方便之后让 SDK 侧将其挪入其自身的 `consumer-rules.pro` 中。

### 2026-07-24 — 补充 ML Kit 扫码与 CameraX 的混淆保护规则以修复运行闪退

- 在 `app/proguard-rules.pro` 写入 ML Kit 扫码库底层类的 keep 保护，解决 Release 混淆下 `BarcodeScanning.getClient()` 触发的 `getClass()` 空指针闪退问题。
- 一并追加 `androidx.camera` 包的完整 Proguard 保护配置，确保相机的生命周期和初始化稳定。

### 2026-07-24 — 补全 MainActivity 的 SDK 权限检查与 Launcher 注册

- 在 `MainActivity.java` 导入 `BleNotificationSDK`。
- 在 `MainActivity.onCreate()` 阶段调用 `registerPermissionLaunchers` 统一注册所需的所有位置及蓝牙权限 Launcher。
- 在 `MainActivity.onResume()` 阶段调用 `ensurePermissions` 触发应用启动时的权限动态检查与引导。

### 2026-07-24 — 蓝牙通知同步集成与编译验证全部完成

- 解决 SDK 依赖在 JitPack 上由于 git submodule 和 maven-publish 引起的编译失败问题，锁定最新可用版本 `fc91c446c0eb346eec980d930800d5841ac14012`。
- 完成主界面 `MainFragment` 的菜单逻辑重构及平板隐藏，点击拉起设备管理绑定。
- 重构 `ReminderNotifier`，将到期提醒和超时提醒原生 `notify` 成功代理给 SDK 的 `sendNotification` 发送，实现 Native 通知正常弹出且蓝牙后台静默同步。
- 完整通过项目 `./gradlew compileDebugJavaWithJavac` 编译验证，所有接口参数和类型完全匹配。

### 2026-07-24 — 蓝牙通知同步集成设计完成与实现启动

- 写入设计文档 `docs/superpowers/specs/2026-07-24-ble-notification-sync-integration-design.md`
- 确定 JitPack 依赖引入方案，以及使用 `sendNotification(builder, notificationId, null)` 进行通知同步代理的决策
- 主界面右上角添加设备管理菜单，且通过 isTablet 在平板形态上予以过滤

### 2026-07-26 — Robolectric 测试修复（多用户适配）

- AppDatabase.setTestInstance 统一注入入口，28 测试全通过

### 2026-07-26 — 死代码清理 + 过滤逻辑精简

- 删除 ChoreHiddenTodayStore、hideCompletedChoresForToday、无调用方旧查询
- 两侧过滤统一：todayCompletedIds（全 status）一次构建

### 2026-07-26 — 任务完成统一流程 + isChildTask 封装 + 拍照按钮修复

- completeTaskUnified 统一完成 + 短完成 status=3 + isChildTask 拍照条件
- 拍照按钮搬回底栏 44dp 固定高度 + CAMERA 权限修复
- 查询拆分为两独立查询 + 去 status=0 限制
- 移除 ChoreHiddenTodayStore.hideForToday

### 2026-07-21 — 成果墙全屏浏览限定时间范围 + 无效照片自愈 + 拍照按钮显隐

- 全屏浏览限定当前周期、无效照片过滤自愈
- 拍照按钮当天有任务才显示

### 2026-07-21 — 任务多照片支持与完成前拍照实现完成

- 每任务最多 5 张照片，右下角补拍按钮改为通用拍照按钮
- 花瓣只计每任务每周首张（getFirstPhotoPerTaskInRange + Set 去重）
- 成果墙按任务去重 + ViewPager2 左右划动
- RetroactivePhotoDialog → TaskPhotoListDialog
- CongratulationDialog 满5张拒绝

### 2026-07-21 — 任务多照片支持与完成前拍照设计规范

- 写入设计文档 `docs/superpowers/specs/2026-07-21-task-photo-multi-and-precompletion-design.md`

### 2026-07-20 — 奖励栏平板限制 + 旧数据迁移 + 内置图标菜单屏蔽

### 2026-07-19 — 成就墙趋势图修复：onMeasure 定高 + 裁前导零 + 主题色

### 2026-07-19 — 成果墙花瓣统计 + 假期提醒修复 + 趋势图 实现完成

- D: updateGroupAndPeriods 假期组保存时写 lastReviewedKey，修复"假期安排确认了吗？"误提示
- B: TimeCapsuleWallActivity 横屏 3 列；C: 标题栏花瓣统计 + 周/月/暑假/寒假下拉切换
- A: PetalTrendChartView 折线+圆点趋势图，竖屏底部，10 周/月或寒暑假按实际周数

### 2026-07-19 — 成果墙花瓣统计 + 假期提醒修复 + 趋势图 设计完成

- spec：[docs/superpowers/specs/2026-07-19-timecapsule-petal-stats-design.md](docs/superpowers/specs/2026-07-19-timecapsule-petal-stats-design.md)
- D: save 时写 lastReviewedKey，修复"假期安排确认了吗？"误提示
- B: 成果墙横屏 3 列；C: 标题栏花瓣统计 + 周/月/寒暑假下拉切换；A: 竖屏底部花瓣趋势折线图

### 2026-07-19 — 暑假开关 disabled + 时段重复修复

- **ViewHolder 复用**：春节 blocked 组 setEnabled(false) 后复用到暑假，else 分支未恢复 → 补 setEnabled(true) + setClickable(true)
- **时段重复**：initVacationDefaultsIfNeeded / initSpringFestivalPeriodsIfNeeded 无条件调 copyPeriodsFromTemplate → 改为仅在 fill*DefaultsCore 返回非 null（无已有数据）时才复制
- **历史数据清理**：新增 deduplicatePeriods()，同 group_type + name_key 保留 MIN(id) 删其余；在 fillVacationDefaultsCore / fillSpringFestivalDefaultsCore / ensureDefaultsAndLoadPeriods 三入口调用

### 2026-07-18 — 奖励栏花芯填充逻辑：首个紧急重要任务填花芯不计花瓣

- FlowerCapsuleView 新增加 setCenterFilled(boolean)，未填充时花芯显示虚线圆圈
- refreshWeeklyFlowers: 照片按时序排列，当天首个 Q0 填花芯（不计花瓣），后续 Q0 才计 3 瓣

### 2026-07-18 — maxDisplayItems: 乘列数 + OnGlobalLayoutListener 自适应布局变化

- calcMaxDisplayItems 乘以 spanCount（1/2/4），平板横屏 4 列 × 行数不再缺量
- post → addOnGlobalLayoutListener，奖励栏出现/消失等布局变化时自动重新估算

### 2026-07-18 — 横屏防闪：改主题色不重建 Activity + 消除 viewPager 嵌套 post 延迟
- 改主题色改为原地刷新 Chrome/btnAddTask/奖励栏颜色，不再走 recreate()，消除奖励栏闪中间
- viewPager.post() 内嵌套 getView().post() → 同步 setCurrentItem 后直接 setupPage0Content，减少首帧延迟

### 2026-07-18 — 四象限花瓣数加权 3/2/2/1 落到计数和成果墙
- refreshWeeklyFlowers() 改用 getPhotosWithTaskInWeek() JOIN 任务表，按象限加权花瓣
- TimeCapsuleWallActivity 硬编码→按任务象限动态显示花瓣数，四语 s_flower_reward_hint 改为 %d 格式

### 2026-07-18 — 全局主题色收尾 + 创建用户对话框 M2 风格化
- themes.xml 加 colorControlActivated：全局光标、RadioButton 选中态跟随主题色
- UserSwitcherManager 创建用户对话框：MaterialAlertDialogBuilder + EditText 焦点下划线主题色 + 24dp 水平收窄

### 2026-07-18 — 多用户支持数据层 + UI + 测试落地
- 数据层：每用户独立 DB（justnow_u<id>.db），UserStore/UserPrefs/AppDatabase 多实例，Repository 每用户缓存
- UI：UserSwitcherManager 独立模块，平板 Toolbar 用户名下拉切换+创建对话框，手机自动默认用户
- 测试：UserStoreTest（14 用例）+ UserPrefsTest（5 用例）全部通过
- 详见 modules/multi-user.md、modules/multi-user_progress.md

### 2026-07-18 — 审查修复收尾：TaskDialogFactory 提取、全 Activity 主题色统一、#7 字号收尾
- TaskDialogFactory 提取：从 MainFragment 迁出 7 个公开对话框方法 + 辅助方法（~400 行），Callback 接口 17 个方法含 3 个 default 扩展点；getFocusText/getStartBlockReason 内聚为工厂 private 方法。MainFragment 1730→~890 行
- 10 个 Activity 全部在 super.onCreate 前加 setTheme(MainFragment.resolveThemeStyle(this))，所有界面标题栏/状态栏跟随用户主题色
- #7 收尾：item_time_capsule_card.xml（15sp/12sp→Caption）、item_retroactive_task.xml（16sp→Caption）、fragment_main_page0.xml tv_cutoff_arrow（16sp→Caption）
- 审查报告 15 项全部标记已修复：docs/code-review-20260718.md

### 2026-07-18 — 审查修复：主题系统去紫色、布局国际化、花朵栏拆 Fragment、平板横屏适配
- 主题系统：themes.xml colorPrimary 从 purple_500 改为 theme_blue；新增 Theme.JustNow.Pink 变体；MainActivity/TimeCapsuleWallActivity 在 onCreate 前 setTheme()；统一 SP 存储（capsule_settings + int type）；用 sp.contains() 区分"未设置"与"明确选蓝色"，修复平板默认粉色覆盖用户选择
- styles.xml 对话框按钮文字色改为 ?attr/colorPrimary；fragment_main_page0.xml 中 btn_add_task/btn_retroactive_photo 改为 ?attr/colorPrimary
- 审查 15 项逐条修复：T3 #前缀→s_tag_name_format、T5 配额 maxVal 校验、T8 TimelineView.onDraw() Paint→成员变量、T9/T11 清理 TTS import 和死代码、T12 全屏 Dialog→AppCompat 主题、T13 Tag 名→TagLocalizer 本地化、T14 补拍按钮→字符串资源
- 6 个布局文件硬编码中文→strings.xml 四语翻译；3 个布局 textSize→textAppearance
- 花朵收集栏拆为 RewardBarFragment（~340 行），使用 FrameLayout 占位+程序化挂载，避免 FragmentContainerView 平板测量异常
- 恢复 adjustRightPanelForOrientation()：平板横屏 rightPanel→HORIZONTAL（任务列表左+花朵栏右），calcMaxDisplayItems() 移到方向调整之后
- 审查报告：docs/code-review-20260718.md

### 2026-07-18 — 内置活动分类更名与屏幕映射 Bug 彻底修复
- 落实方案 A，将内置活动分类“益智”更名为更具体的“桌游”（英文 Board Game），以避开与玩具重叠的泛益智界定。
- 彻底修复了“屏幕”与 `animation` 之间因早期硬编码为“动画”产生的映射 Bug。统一了 `TagLocalizer` 的解析网关、`TagDao` 的数据库拦截范围（将 '动画' 改为 '屏幕'）、以及简繁体 `strings.xml`（繁体为 '螢幕'）的翻译对齐，确保常用标签栏能够 100% 正确拦截和剔除“屏幕”内置标签。

### 2026-07-18 — 代码审查报告记录
- 完成 7 月份全部 94 个提交的代码审查，发现 15 个问题（含 2 blocking、5 important、4 suggestion、4 nit）。
- 报告路径：`docs/code-review-20260718.md`

### 2026-07-18 — 成果栏直角主题色边框与沉浸式标准 Activity 照片墙交付
- 移除了多余的属性动画和所有九宫格雕花图片资源，通关时只在 Java 层动态通过 GradientDrawable 渲染 2dp 直角主题色描边，完美符合与主界面直边拼贴的设计规范。
- 重构成果照片墙为标准沉浸式 TimeCapsuleWallActivity 页面，使系统状态栏颜色与顶部标题栏完全一致（无缝融合一体），支持左上角白色返回箭头返回，去除多余关闭按钮，提供彻底的只读功能。
- 彻底移除了任何删除照片红小叉及相关的删除数据库逻辑，保障成果墙照片只读、不可被删除。
- 修复了相册返回时由于首次加载判定缺陷导致的偶发性误弹出通关祝贺对话框 Bug。
- 重构了 Monday 开始时间戳计算算法，使用相对数学偏移避开了 Java Calendar 处于不同 Locale / WeekDay 规则下的计算漂移，保障成果墙精确仅显示当周的照片记录。

### 2026-07-17 — 时光胶囊与设置主题色全局对齐及去紫色重构
- 确立统一主题色设计，去除多余无用设置菜单，仅保留“设置主题色”菜单。
- 确立花瓣颜色与主题色动态跟随的逻辑，拟重构 FlowerCapsuleView。
- 确立两大对话框像素级贴边 Header 对齐及强制去紫色字体的动态配置方案。

### 2026-07-17 — 主界面底部时段栏 Insets 适配与陈旧文档清理
- 在 `MainActivity.java` 中为 `FragmentContainerView` (navHostFragment) 添加 `ViewCompat.setOnApplyWindowInsetsListener` 监听。根据 `android-view-systembar` 的最佳实践，在 WindowInsets 发生变化时，动态将 `navigationBars().bottom` 设定为其 `paddingBottom`，从而精确、全局地分发导航栏/手势区 inset，避开底部遮挡。
- 移除了先前在 `MainFragment.java` 中单独对 `bottom_period_bar` 设置的 insets 监听器，防范双重消费（double padding）。
- 清理并删除了已被 7-16 迭代文档完全覆盖的陈旧平板 M2 初始设计文档 `docs/superpowers/specs/2026-07-15-tablet-m2-layout-design.md`。

### 2026-07-17 — 编辑页面专注时长选项平板单行自适应排版
- 在 `TaskEditFragment.java` 的 `setupFocusMinutes()` 中引入 `is_tablet` 判定，动态设置 `itemsPerRow` 变量（平板为选项全集大小，手机默认为 3）。
- 使得平板设备上，五个专注时长选项按钮在一行内平铺排列，不再进行折行，大幅缩减了平板上不必要的页面垂直高度。

### 2026-07-17 — 编辑任务界面布局微调（选择图标前置与横屏单行标签自适应）
- 修改 `app/src/main/res/values/bools.xml` 和 `app/src/main/res/values/dimens.xml`，新增 `existing_tags_single_line`（默认值为 `false`）和 `existing_tags_height`（默认值为 `88dp`）。
- 新建 `app/src/main/res/values-land/bools.xml` 和 `app/src/main/res/values-land/dimens.xml`，使得在横屏（landscape）模式下，`existing_tags_single_line` 为 `true`，且 `existing_tags_height` 调整为 `44dp`。
- 调整 `app/src/main/res/layout/fragment_task_edit.xml` 布局，将原有置底的图标选择器 `card_icon_selector` 剪切移动至内容输入框 `card_markdown` 下方、标签输入框 `card_tag` 上方。
- 调整 `cg_existing_tags` 控件，使其高度及 `singleLine` 属性使用资源引用，从而支持横屏下单行横向排布。

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
- 扩展 TaskEntity 实体类 `icon_name` 属性，并将数据库升级为版本 8，编写 `MIGRATION_7_8` 支持无损升级和 Schema 导出。
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

### 2026-07-16 — 主界面时间线 M2 视觉优化
- 编写时间线 M2 优化方案视觉设计 spec。
- 已完成任务块的左侧灰色状态条宽度加宽为 2 倍（8dp）并填充，文本对齐与执行中卡片统一调整为 16dp；执行中任务卡片整体高亮填充为象限主题色背景，文字全白，去除 Ongoing 标签。
- 解决在 Android 12/13/14+ 系统上启动任务时，因调用精确闹钟而报 `SecurityException` 导致崩溃的问题。在 `ReminderScheduler.java` 中封装 `setAlarmSafe` 方法，捕获 `SecurityException` 并在无权限时优雅降级为非精确闹钟。
- 在 `MainFragment.java` 的 `onResume` 生命周期中增加对精确闹钟权限的检查和申请，提示未授权用户去设置页面开启，避免执行/添加任务闹钟时权限不足。
- 编译成功，且成功解决真机安装与启动任务时的崩溃问题。

### 2026-07-14 — 任务完成模式：实现完成 + 防抖修复 + UI 打磨

完成模式代码落地：27 files, +615/-645。编译通过，TaskCompletionCounterDaoTest 11 tests PASS。修复防抖导致完成操作不刷新（refreshSync + compute 即时执行 + 延迟兜底通知）。修复 DB 迁移遗漏 DROP COLUMN degrade_period。配额输入 UI 五轮打磨：chip 行合并、移除每天 chip、EditText 占满余宽、间距按语言区分 dimens、中文 hint 改为"1次(最多N)"。

### 2026-07-10 — 任务完成模式：设计与实现计划

废弃四象限降级恢复策略，改为任务完成模式（日/周/月/年配额）。每个任务完成一次当天即隐藏，次日重现；周/月/年模式叠加周期配额控制显示。brainstorming 完成 → 设计文档 + 实现计划 + planning-with-files 结构更新。

> 设计文档：[docs/superpowers/specs/2026-07-10-task-completion-mode-design.md](docs/superpowers/specs/2026-07-10-task-completion-mode-design.md)
> 实现计划：[docs/superpowers/plans/2026-07-10-task-completion-mode.md](docs/superpowers/plans/2026-07-10-task-completion-mode.md)
> 详见：[modules/task-completion-mode.md](modules/task-completion-mode.md)

### 2026-06-17 — 审查修复：防抖逻辑、国际化、清理

修复 code-review-20260617.md 中 #3-#7 共 5 项，#1/#2 确认为误报。重审 20260531 #6（主界面 vs Widget 业务对齐）确认已通过 TaskFilterHelper 妥善解决。

- **#3 防抖逻辑重写**：`mDelayedCompute` 从构造器创建（final），`mPendingFilterTagIds` 字段替代 lambda 捕获。首次即时执行 + 1秒兜底，防抖窗口内重置为 500ms
- **#4 DisplayEngine 复用**：提升为成员变量
- **#5 异常日志**：`AppLaunchCatalogCache.loadInBackground` catch 块加 `Log.e`
- **#6 分身标识国际化**：`"（分身）"` 改为 `getString(R.string.s_app_clone_suffix)`，4 语言 strings.xml
- **#7 清理未使用方法**：删除 `findByPackageName` 和 `findByPackageNameAndUserId`

### 2026-06-17 — 代码审查：TaskFilterHelper + 应用分身

审查6月12日之后的修改（TaskFilterHelper 提取、应用分身支持），发现 7 个问题（1 important / 2 suggestion / 4 nit），主要是 TaskFilterHelper 的线程安全和防抖逻辑问题。重审 #2 | 20260604 确认为误报，ignore 文件判断正确。

> 审查报告：[docs/code-review-20260617.md](docs/code-review-20260617.md)

### 2026-06-17 — TaskFilterHelper 提取业务逻辑

提取主界面右侧栏和 Widget 共用的任务数据获取和过滤逻辑到 `TaskFilterHelper`。单实例 + 防抖（实时执行 + 1 秒延迟再执行）+ 缓存。解决业务代码重复问题，Widget 和主界面共享同一套过滤逻辑。编译通过，待真机验证。

### 2026-06-16 — 应用分身支持实现

APP 跳转功能支持小米等设备的应用分身。查询阶段通过反射调用 `queryIntentActivitiesAsUser()` + `getIdentifier()` 遍历所有用户；存储阶段复用 `deepLink` 字段存储带 `S.launch_user_id` extra 的 intent URI；显示阶段分身应用名后加"（分身）"；跳转使用系统选择器（普通应用无 `INTERACT_ACROSS_USERS` 权限）。编译通过，待真机验证。

> 设计文档：[docs/superpowers/specs/2026-06-16-app-clone-support-design.md](docs/superpowers/specs/2026-06-16-app-clone-support-design.md)
> 实现计划：[docs/superpowers/specs/2026-06-16-app-clone-support-plan.md](docs/superpowers/specs/2026-06-16-app-clone-support-plan.md)

### 2026-06-10 — 专注时长小时化展示

**状态**：代码实现完成，定向测试和 Java 编译通过。专注时长展示统一收敛到 `FocusDurationOptions.format()`：`0` 仍显示琐碎，`30` 仍显示分钟，`60/90/120/150` 分别显示为 `1小时/1.5小时/2小时/2.5小时`（英文环境对应 `1h/1.5h/2h/2.5h`）。主界面任务、Widget、任务详情、时间线预期时长、四象限概览/列表、任务输入选项、四象限筛选选项和 `focus_max_minutes` 降档冲突提示均复用同一格式化逻辑；剩余时间和实际已执行时长仍保留分钟精度。

**验证**：`testDebugUnitTest --tests FocusDurationOptionsTest --tests DisplayPolicyRepositoryTest --tests WidgetUpdateHelperTest` 通过；`compileDebugJavaWithJavac` 通过。

> 详见：[modules/smart-display.md](modules/smart-display.md)

### 2026-06-10 — 智能展示策略 YAML 配置化实现

**状态**：代码实现完成，定向测试和 Java 编译通过。新增默认 YAML、私有 YAML 原文存储、导入/导出/文本编辑页面；`DisplayEngine`、主界面、Widget、四象限概览/列表改为读取 `DisplayPolicy` 中各自用到的配置；`focus_max_minutes` 仅允许 `120/150`，升档不限制，降档只在当前未归档任务已有更高专注时长时拦截；任务编辑和四象限筛选按 `FOCUS_SLOT_MINUTES` 动态生成专注时长档位。

**验证**：`testDebugUnitTest --tests DisplayPolicyParserTest --tests DisplayPolicyRepositoryTest --tests FocusDurationOptionsTest --tests DisplayEngineTest --tests QuadrantRatioFilterTest` 通过；`compileDebugJavaWithJavac` 通过。

> 详见：[modules/smart-display.md](modules/smart-display.md)

### 2026-06-10 — 智能展示策略 YAML 配置化实现计划

已完成智能展示策略 YAML 配置化实现计划，拆分为依赖/模型、解析与私有文件、引擎策略化、专注时长动态档位、策略页面、刷新通知、测试验证 7 个实施阶段。

> 实现计划：[docs/superpowers/specs/2026-06-10-display-policy-yaml-plan.md](docs/superpowers/specs/2026-06-10-display-policy-yaml-plan.md)
> 详见：[modules/smart-display.md](modules/smart-display.md)

### 2026-06-10 — 智能展示策略 YAML 配置化设计

已完成智能展示策略 YAML 配置化设计文档，覆盖默认/用户私有 YAML、导入/编辑/导出、优先级顺序配置、四象限比例配置、专注时长动态档位、`focus_max_minutes` 降档限制、错误回退和测试范围。

> 详见：[modules/smart-display.md](modules/smart-display.md)

### 2026-06-09 — 审查修复：节假日备用源与 Widget onUpdate

修复 Apple Calendar ICS 解析口径、多源 fallback 失败语义、Widget `onUpdate()` 子集 ID 清理风险。Apple 源改为只信 `X-APPLE-SPECIAL-DAY`，HK/MO 传统 ICS 保持全事件假日模式；无效节假日响应改为抛 `IOException` 继续尝试备用源；Widget `onUpdate()` 移除筛选清理，保留数量变化刷新优化。相关单元测试通过。

### 2026-06-09 — 代码审查报告记录

已记录今日修改审查报告，覆盖节假日备用数据源、Widget onUpdate 刷新/筛选状态、小组件触发节假日同步等改动。审查报告：docs/code-review-20260609.md

### 2026-06-09 — Widget onUpdate 优化 + scheduleNextMinuteBoundary 收敛

`onUpdate()` 仅 widget 数量变化时触发 `updateAllWidgets`，避免系统周期回调冗余刷新。`scheduleNextMinuteBoundary` 改为 private，链式闹钟由 `updateAllWidgets` 内部自续。

### 2026-06-09 — Widget 触发节假日同步

Widget `onUpdate()` 新增 `triggerHolidaySync()` 调用，解决用户只看 Widget 不打开 App 导致节假日数据不更新的问题。`triggerHolidaySync()` 加日内节流：已有缓存时同一天内不重复检查，无缓存时不节流。

详见：[modules/holiday-data.md](modules/holiday-data.md)

### 2026-06-09 — Apple Calendar 备用数据源适配

Apple Calendar 公开订阅适配为 CN 地区备用数据源。`IcsParser` 新增 `X-APPLE-SPECIAL-DAY` 解析，`HolidaySourceFactory` 支持多源 fallback（GitHub 优先，失败才用 Apple）。编译 + 测试通过。

详见：[modules/holiday-data.md](modules/holiday-data.md)

### 2026-06-09 — 时间线刻度色值统一

任务安排业务重构后，时间线不再区分"活跃时段"与"非活跃时段"的刻度样式。原 `mTickPaint`/`mHourTickPaint` 使用 `#CCCCCC`（`timeline_tick`）看不清，与 `mActiveTickPaint`/`mActiveHourTickPaint`/`mActiveLinePaint` 使用的 `#999da2`（`timeline_active_tick`）形成两套 Paint 体系。

清理内容：删除 3 个 active Paint 字段及 `drawActivePeriodMarks()` 方法；`mTickPaint`(1.8f) / `mHourTickPaint`(3.6f) / 新增 `mLinePaint`(2.2f) 统一使用 `#999da2`；`timeline_active_tick` 资源改回 `timeline_tick` 名称；`HourColumnView` 整点数字同步对齐。

### 2026-06-08 — 代码审查 + 修复

代码审查发现 12 项问题（important 3 / suggestion 7 / nit 2），全部处理完毕：10 项修复、1 项误报、1 项确认关闭。涉及 intent-capture、task-input、task-edit 模块。

> 审查报告：[docs/code-review-20260608.md](docs/code-review-20260608.md)

### 2026-06-08 — 编辑入口返回死循环修复 + EXTRA_EDIT_TASK_ID 迁移

> 详见：[modules/task-input.md](modules/task-input.md)

**状态**：代码修复完成，待编译验证。四象限→任务详情→编辑页→按返回→又回到编辑页无法退出：根因是 `TaskInputFragment.onViewCreated()` 每次执行都从 Intent 读 `EXTRA_EDIT_TASK_ID` 并自动导航到 `TaskEditFragment`，pop 回来后再次触发形成死循环。修复：`TaskInputActivity.navigateBackOrFinish()` 中判断编辑入口 + 当前在 `taskEditFragment` 时直接 `finish()`；编辑入口注册 `OnBackPressedCallback` 拦截系统返回键/手势返回，统一走 `navigateBackOrFinish()`。`EXTRA_EDIT_TASK_ID` 常量从 `ReminderDetailActivity` 迁移到 `TaskInputActivity`，消除反向依赖。

### 2026-06-08 — 笔记分享模块补齐 + UI 收尾

> 设计文档：[docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md](docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md)（6/8 修订追加第九、十节）
> 详见：[modules/intent-capture.md](modules/intent-capture.md)

**状态**：`compileDebugJavaWithJavac` 通过，待真机验证。

新增"笔记分享"附加模块（`task_note_shares` 表，AppDatabase v5→v6 migration）：`TaskNoteShare` 实体 + `TaskNoteShareDao` + `TaskNoteShareRepository`（数据层 1:1 对齐 APP 操作）；`TaskInputNoteShareSheet` + `item_note_share.xml`（独立 sheet，只读展示 + 删除，无手动添加/编辑）；任务编辑页"附加模块"选择器加入"笔记分享"文档图标按钮（`ic_module_note_share.xml`）；`TaskInputViewModel` 加 `stagePendingNoteSharePrefill` / `consumePendingOpenNoteShareSheet` / `consumePendingNoteSharePrefill` / `hasEffectiveNoteShares` + `tagNamesMap` LiveData；`TaskInputActivity` 解析 3 个笔记分享 extras；`TaskEditFragment.maybeAutoOpenNoteShareSheet`；`ReminderDetailActivity` 加笔记分享区块（标题+列表+点击 startActivity+失败 toast）。

CapturePicker 改造：底部两按钮横向并排（`weight=1`）；Toolbar 白色标题"选择APP操作任务"；按钮文案动态切换（SEND URL 流 `+笔记分享任务` → `onNewWithNoteShare`；MODE_NOTE 保持原"新建含笔记任务"）。

任务项渲染统一：`item_search_result.xml` 改单行 `30分钟 #<标签> <任务标题>` 格式（CapturePicker + TaskInputFragment 同步）；搜索框去 stroke 改 `search_box_bg` 浅灰填充。

捕获流保存成功后引导用户留在 JustNow（`mFromCapture` 标记 → `QuadrantFragment` 回调里 `startActivity(MainActivity)` 再 `finish()`）。4 语言新增 9 条 + 改 2 条字符串。

### 2026-06-07 — WebView 探针剥离到独立项目

> 关联调研项目：`/mnt/d/Documents/AndroidStudioProjects/WebViewProbe`
> 详见：[modules/intent-capture.md](modules/intent-capture.md)

**状态**：本项目已删除 `WebViewProbeActivity` / `activity_webview_probe.xml` / `WebViewProbeLauncher` activity-alias 与 4 个 `s_probe_*` 字符串（4 语言）。代码与文档完整迁到独立 demo 项目 `WebViewProbe`（同级目录），含 README 记录背景 / UA 伪装 / 拦截后冻结页面 / 腾讯视频可行性验证 / 合规性评估。本项目不再维护 WebView 拦截方案。

### 2026-06-07 — 跨应用 Intent 捕获实现完成

> 设计文档：[docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md](docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md)
> 详见：[modules/intent-capture.md](modules/intent-capture.md)

**状态**：`compileDebugJavaWithJavac` 通过，待真机验证。新建 `AppActionCaptureActivity`（Manifest VIEW 通配 scheme + SEND text/plain，运行期黑名单 http/https）+ `CapturePickerActivity`（任务列表 + 搜索 + 底部两按钮）+ `TaskInputActivity` 7 个新 extras + `TaskInputViewModel` 一次性消费语义（applyDraftPrefill / stagePendingAppActionPrefill / consumePending*）+ `TaskEditFragment` 自动打开 sheet + `TaskInputAppActionSheet` 顶部插入预填项与 item 编辑能力。`ReminderDetailActivity` 启动失败 toast 兜底。4 语言新增 11 字符串。零 schema 变更，复用 `TaskAppAction.deepLink`。

### 2026-06-07 — 任务编辑页已有标签 Chip 紧凑模式

> 设计文档：[docs/superpowers/specs/2026-06-07-task-edit-chip-compact-design.md](docs/superpowers/specs/2026-06-07-task-edit-chip-compact-design.md)
> 详见：[modules/task-edit.md](modules/task-edit.md)

**状态**：已修改。`TagChipHelper.createSelectableChip` 新增 `compact` 重载，紧凑模式下关闭 `ensureMinTouchTargetSize`；`TaskEditFragment.setupTagChips` 改为传 `true`。其他调用点保留默认非紧凑，触摸区不变。

### 2026-06-07 — 添加任务返回 + APP 跳转附加模块优化

> 设计文档：[docs/superpowers/specs/2026-06-07-task-input-app-action-polish-design.md](docs/superpowers/specs/2026-06-07-task-input-app-action-polish-design.md)
> 详见：[modules/task-input.md](modules/task-input.md)

**状态**：定向测试通过，`compileDebugJavaWithJavac` 通过。添加任务首屏 Toolbar 改为始终显示返回箭头并用 ActionBar 更新标题；APP 跳转附加模块改为主 Sheet 顶部添加入口 + 居中添加对话框，Application 级缓存异步加载 APP 列表和图标，主界面 `onResume` 清理缓存；Sheet 旧 APP 项按包名单条查询图标/名称，新添加项复用缓存数据并置顶；保存前按有效条目归一化模块状态，空 APP / 空 todo 不再保存为附加模块，编辑旧模块删空会清理旧子表。

### 2026-06-06 — 审查修复：安排推荐过期清理 + cutoff 跨天

- `TYPE_ONCE` 清理口径改为日期已过或所属时段已结束，不再按 `scheduledTime + focusMinutes` 预计完成时间禁用；`JustNowApplication` 为仓库注入 `PeriodGroupRuleResolver` 以还原单次安排的当天时段组。
- 推迟提醒只重新设置闹钟，不写入/延长推荐优先窗口；推荐优先始终只看原始 `scheduledTime` 后 30 分钟，`postponedUntilMs` 不再参与排序。
- 延迟边界收紧为 `delayed < period.endMinute`，避免延迟闹钟正好落在时段结束点后被过期清理竞态吞掉。
- `CutoffTimeStore` 追加保存 `cutoff_date_ms`，读取时若不是今天自动清除，避免昨天的截止时间影响今天。
- **状态**：定向单元测试通过，`compileDebugJavaWithJavac` 通过；文档整理后未再编译。

### 2026-06-06 — 审查修复：PREFS_NAME 统一 + AlarmReceiver 清除时序

- **#3**：新建 `PrefsConfig.java` 统一 `PREFS_NAME` 常量，7 处中间变量声明 + 1 处硬编码改为直接引用 `PrefsConfig.PREFS_NAME`
- **#6**：`AlarmReceiver.handleAlarm()` 中 `clearCutoffEndMinute` 移到 task 有效性校验之后，避免 task 无效时误清截止时间
- **状态**：编译通过，定向测试通过

### 2026-06-06 — 时间选择 PopupWindow 显示修复

**状态**：代码改动编译通过；文档整理后未再编译。时间段编辑与主界面底部截止时间复用的 `popup_time_picker` 改为可配置最小宽度（当前 160dp）并保持标题、滚轮、按钮组居中；`NumberPicker` 时间字号提升到 `text_size_title`；时间段编辑入口增加屏幕边缘偏移限制，避免靠右点击时弹窗被裁切。

### 2026-06-06 — 代码审查修复

> 审查报告：[docs/code-review-20260606.md](docs/code-review-20260606.md)

**状态**：编译通过。审查今日 3 个提交，发现 3 项（1 important + 1 nit + 1 suggestion）。#1 提取 `TaskScheduleRepository.skipOrDisable()` 消除 AlarmReceiver/MainViewModel 重复逻辑；#2 误报（Room 对 POJO 自动做 boolean 转换）；#3 确认关闭（v3 中间版本未发布）。

### 2026-06-06 — 安排任务推荐化重构

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-recommend-design.md](docs/superpowers/specs/2026-06-06-schedule-recommend-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**状态**：编译通过，431 测试 0 新增失败。旧版安排任务"占用"时间槽改为"到点优先推荐"。移除截断逻辑、时间线安排块、槽位占位判断；新增推荐引擎 30 分钟优先排序（weight -= 300）；通知选项重构（忽略/延迟30分钟/开始，`postponedUntilMs` 字段）；槽位表 30 分钟粒度每行 4 格；跨时段清理已过时段的延迟标记。涉及文件：TimeRemainingCalculator、TimelineView、TimelineBuilder、MainFragment、MainViewModel、TaskStartGuard、WidgetUpdateHelper、DisplayEngine、TaskScheduleEntity、AppDatabase（v4→v5）、TaskScheduleDao、TaskScheduleRepository、AlarmReceiver、ReminderNotifier、TaskScheduleFragment、字符串资源（四语言）。

### 2026-06-06 — 安排任务感知的剩余时间

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md](docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**状态**：编译通过，用户验证通过。剩余时间计算未考虑时段内安排任务块，导致液体色块/底部栏/展示引擎/任务开始守卫四处不一致。方案：TimeRemainingCalculator 新增重载接收今日安排，applyScheduleTruncation 截断到最近安排开始 + 范围内检测（effectiveRemaining=0），PeriodStatus 新增 effectiveRemaining/effectiveEndMinute。TaskScheduleEntity 新增 @Ignore focusMinutes 字段由 Repository 层 POJO 转换填充。涉及文件：TaskScheduleRepository、TaskScheduleEntity、TaskScheduleDao、TimeRemainingCalculator、MainViewModel、TimelineView、TaskStartGuard、WidgetUpdateHelper。（注：后续被推荐化重构方案替代）

### 2026-06-06 — 6月4日起文档补录检查

> 详见：[task_plan.md](task_plan.md)、[findings.md](findings.md)

**状态**：文档维护完成，未编译。检查 6/4 起已落盘提交和模块文档，补齐根目录 `task_plan.md` / `findings.md` 的 6/4-6/5 摘要，并补录 `task-execution`、`reminder-detail`、`task-input` 三个模块正文漏项。

### 2026-06-06 — 忽略交互修复+对话框分流+TYPE_ONCE 超时

> 审查报告：[docs/code-review-20260605.md](docs/code-review-20260605.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过。

**审查**：对 6/5 的 8 个提交做代码审查，发现 3 个问题（1 blocking + 1 important + 1 nit）。

### 2026-06-06 — 跳过表简化：单行模式

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过。`task_schedule_skips` 从每次忽略一行改为每个 schedule 一行（lastSkippedDateMs + skipCount），DAO 改 upsert，`ReminderScheduler` 去掉 365 天遍历逻辑，数据库迁移 v3→v4。

**修复**：
- `update()` 无条件设 `enabled=true` 回归：TYPE_ONCE 忽略改用 `disableScheduleSync`
- 对话框分流：右侧栏 `showTaskDetailDialog`（开始/安排/取消）与时间线 `handleTimelineScheduledTaskClick`（开始/忽略/取消）分离，互不影响
- `configureScheduleButton` 恢复 `schedule` 参数，有可命中安排时显示"调整安排"
- `matchesToday` 判断：`configureScheduleButton`、`onTimelineItemClicked`、`showTaskDetailDialog` 三处统一
- 时间线点击路由：执行中走 `resolveAndHandleTaskClick`（按 hasContent 分流），已完成不可点击
- `isScheduleActionable` 辅助方法：`enabled + matchesToday`，去掉时间判断（超时由 disable 机制处理）
- TYPE_ONCE 超时 disable：`disableExpiredOnceSchedules` 通过 TIME_TICK + onResume 触发；历史实现按预计完成时间清理，后续已调整为日期已过或所属时段已结束

### 2026-06-05 — 安排保存链路修复落地

> 设计文档：[docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md](docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过，用户复测通过。仓库层按 `taskId` 兜底安全保存安排并回填真实 `scheduleId`；两个不同任务分别安排不再闪退，保存后自动返回，主界面时间线即时刷新，到点通知恢复。

### 2026-06-05 — 连续安排通知丢失回归修复

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**背景**：原回调只有闹钟注册+缓存刷新，无返回流程，闹钟正常。后来加 `onComplete.run()`（finish）并放在闹钟之前，接连保存多个安排时后续闹钟未注册。
**状态**：仅代码改动，未编译。回调顺序恢复为先闹钟后返回，避免 finish 早于 AlarmManager 注册导致通知丢失。

### 2026-06-05 — 安排页槽粒缓冲：当日临近槽位禁用

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：仅代码改动，未编译。`SLOT_INTERVAL_MINUTES` 常量统一槽粒粒度，`isPast` 加一个槽粒缓冲，避免选临近时间的槽位后保存回调到 `schedule()` 时触发时间已过导致静默丢弃闹钟。

### 2026-06-05 — 代码审查修复：跨年bug+风格修正+资源清理

> 审查报告：[docs/code-review-20260604.md](docs/code-review-20260604.md)
> 详见：[modules/time-period.md](modules/time-period.md)、[modules/widget.md](modules/widget.md)

**状态**：编译+测试通过。431 tests / 6 预存失败。vacationCanMatch 跨年窗口修复、switch 改 Map、硬编码颜色提取、日期 API 统一、残留资源删除。

### 2026-06-05 — 安排调整&忽略交互

> 设计文档：[docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md](docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过，82 相关测试通过。按钮文字区分有无未触发安排、安排页加载 bug 修复、通知忽略+跳过记录表、超时统一为忽略逻辑、时间线已安排任务可点击弹窗。

### 2026-06-04 — 安排页槽位表占用/过去时间过滤修复

> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：编译通过。时段组每天/每周模式不再查占用、不过去时间，`effectiveDateMs` 统一驱动；长假类时段组单次日期芯片去掉"仅本次："前缀。

### 2026-06-04 — 假期组弹窗时段预写入修复 + 春节无数据锁灰

> 设计文档：[docs/superpowers/specs/2026-06-04-period-group-rule-update.md](docs/superpowers/specs/2026-06-04-period-group-rule-update.md)
> 详见：[modules/time-period.md](modules/time-period.md)

**状态**：编译通过。`fillVacationDefaultsCore`/`fillSpringFestivalDefaultsCore` 开弹窗不再写 DB，改保存时统一写入；春节组无未来数据时开关锁灰、禁止编辑。

### 2026-06-04 — Widget 打磨

> 设计文档：[docs/superpowers/specs/2026-06-04-widget-polish-design.md](docs/superpowers/specs/2026-06-04-widget-polish-design.md)
> 详见：[modules/widget.md](modules/widget.md)

**状态**：编译通过。顶部栏点击跳转主界面、圆角半透背景、字体三档定档 + 行高 dimens 化。

### 2026-06-04 — 安排页时段组选项 3 个月窗口过滤

> 设计文档：[docs/superpowers/specs/2026-06-04-task-schedule-redesign.md](docs/superpowers/specs/2026-06-04-task-schedule-redesign.md)（"时段组选项的 3 个月窗口过滤"小节）
> 详见：[modules/time-period.md](modules/time-period.md)

**状态**：编译通过。安排页 `buildEnabledPeriodGroups()` 增加 `canMatchInNextThreeMonths()` 过滤，假期时段组未来 3 个月不能命中则不显示。

### 2026-06-04 — 安排功能重构

> 设计文档：[docs/superpowers/specs/2026-06-04-task-schedule-redesign.md](docs/superpowers/specs/2026-06-04-task-schedule-redesign.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：编译 + 全量 434 测试 0 失败。MONTHLY 砍掉，安排关联时段组，动态类型选择器，时间线去最大集。

### 2026-06-04 — 5 项 Bug 修复

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-detail.md](modules/reminder-detail.md)、[modules/task-input.md](modules/task-input.md)、[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)

**状态**：编译+测试通过。时间线缓存/真实耗时、完成按钮文案、IME 遮挡、单象限删除刷新。

### 2026-06-04 — 四象限概览编辑图标 + 设置页返回箭头修复

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)、[modules/time-period.md](modules/time-period.md)、[modules/stats.md](modules/stats.md)、[modules/tag.md](modules/tag.md)

**状态**：完成。编辑图标白色、设置页 Toolbar 返回箭头。

### 2026-06-03 — 小米真机安排页槽位空白修复

> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：完成。槽位字体 18sp→16sp。

### 2026-06-03 — MainViewModel 消除 prepareComputeContext 共享上下文

> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：完成。删除 `prepareComputeContext()` + `ComputeContext`。

### 2026-06-03 — 无标签任务选四象限 NPE 闪退修复

> 详见：[modules/smart-display.md](modules/smart-display.md)

**状态**：完成。`tagMap.get(null)` NPE 判空。

### 2026-06-03 v2 — 全面代码追加审查

> 详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md)

**状态**：编译+374 测试通过。手动完成/Widget 接入补齐。

### 2026-06-03 — 全面代码审查与修复

> 详见：[modules/task-execution.md](modules/task-execution.md) 等 8 个模块

**状态**：编译+371 测试通过（+75 新增）。

### 2026-06-02 — 全面代码审查

> 详见：[modules/smart-display.md](modules/smart-display.md) 等 8 个模块

**状态**：编译+282 测试通过。

### 2026-06-01 — 重复业务逻辑全面重构

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/holiday-data.md](modules/holiday-data.md)

**状态**：编译+251 测试通过。BaseTaskViewModel 模板方法、HolidayDataSource 抽象类。

### 2026-05-31 — 代码审查遗留问题跟进

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译通过，遗留问题全部关闭。

N8 正当设计关闭；补齐 runInTransaction；HTTP 404/403 不修；DB 版本回退到 1。

详见：[modules/task-execution.md](modules/task-execution.md)

### 2026-05-30 — 全项目代码审查与修复

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译 + 全量测试通过。47 项发现 → 排除 5 误报 → 分 8 批修复 24 项。

后续验证建议：[ ] 节假日数据真机验证、[ ] Widget 标签筛选真机验证、[ ] logcat assertNotMainThread 检查

详见：[modules/task-execution.md](modules/task-execution.md)、[modules/holiday-data.md](modules/holiday-data.md)、[modules/widget.md](modules/widget.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/smart-display.md](modules/smart-display.md)、[modules/time-period.md](modules/time-period.md)、[modules/tag.md](modules/tag.md)、[modules/task-input.md](modules/task-input.md)

### 2026-05-29 — 四象限降级恢复

> 详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md)

**状态**：已完成，编译通过 + 227 用例 0 失败（+9 新增测试）

### 2026-05-29 — Widget 添加权限中转主界面化

> 详见：[modules/widget.md](modules/widget.md) — 进度日志

**状态**：代码完成，未编译；待桌面 Launcher 添加 widget 实测。

Widget 配置页改为纯中转：已授权时直接初始化并返回添加成功；未授权时打开 `MainActivity` 的 widget 权限模式，由主界面弹出精确闹钟权限引导。主界面授权结果通过 `WidgetConfigureResultBridge` 回传给中转页，再由中转页向 Launcher 返回 `RESULT_OK` 或 `RESULT_CANCELED`。

### 2026-05-28 — Widget 顶部状态与执行中行高亮

> 详见：[modules/widget.md](modules/widget.md) — 进度日志

Widget 顶部剩余时间超过 60 分钟时改为小时展示；非时段固定将“休息中”和“下个时段”分两行显示；任务列表中执行中任务行增加浅色背景高亮。
Widget 添加改为配置中转页检查精确闹钟权限：未授权时打开主界面弹权限引导，结果回传中转页；已授权继续添加，未授权或返回未通过则取消添加。

### 2026-05-28 — 四象限任务管理 Toolbar/状态栏颜色收尾

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — 进度日志

单象限列表页 Toolbar / 状态栏改为 `MainActivity` 按导航目的地统一管理：进入 `quadrantTaskListFragment` 使用所选象限色，返回主界面恢复默认主题色。Android 15+ 走透明状态栏 + `AppBarLayout` 背景透出，Android 14 及以下保留 legacy `setStatusBarColor()`。同步清理模块对齐问题：菜单改 `MenuProvider`、复用 `ViewModelFactory`、标题/标签格式资源化、概览任务行 XML 化、命名规范修正。`compileDebugJavaWithJavac` 最终 BUILD SUCCESSFUL。

## 会话记录

### 2026-05-28 — 遗留问题梳理 + 文档同步 + 交互修复

- **状态**：完成（未编译验证，被其他改动卡住）。

**文档同步**：
- [modules/tag.md](modules/tag.md)：Widget 标签筛选交互说明统一
- [modules/search-engine.md](modules/search-engine.md)：标签检索分离→已实现
- [modules/app-icon.md](modules/app-icon.md)：第一版图标进度更新

**交互修复**：
- [modules/reminder-delay.md](modules/reminder-delay.md)：通知"开始"有执行中任务时自动完成再开始，修复静默失败

**更新后遗留**：四象限状态栏颜色、Widget 真机验证、数据统计（M6）、Nager.Date API、APP 图标真机遮罩验证

### 2026-05-27 — 四象限任务管理

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — 进度日志

- 设计确认（brainstorming）
- 5 阶段串行实现 OK
- 多轮 UI 修复 OK
- 状态栏颜色遗留 X

### 2026-05-27 — 全局横竖屏锁定

- **状态**：完成。
- **设计文档**：[2026-05-27-screen-orientation-lock-design.md](docs/superpowers/specs/2026-05-27-screen-orientation-lock-design.md)

### 2026-05-27 — 模块文档三段结构重构

- **状态**：完成。
- **内容**：共 18 个模块文档统一为标准三段结构；task_plan.md / findings.md / progress.md 瘦身为引用风格。

### 2026-05-26 — MainActivity nav_graph 全面拆分

- **状态**：完成，`compileDebugJavaWithJavac BUILD SUCCESSFUL`。
- **内容**：MainActivity 导航图全部独立业务线拆为独立 Activity，MainActivity 只保留 MainFragment。
- **改动**：新建 5 Activity 类 + 5 layout + 5 nav_graph；修改 9 个文件。
- **关键决策**：`TaskScheduleActivity` 程序化 `setGraph()` 传参，其余 XML `app:navGraph`。

### 2026-05-26 — 桌面 Widget 实现

> 详见：[modules/widget.md](modules/widget.md) — 进度日志

- **状态**：实现完成，编译通过；标签筛选需真机/桌面 Launcher 复验。
- 4 步分发（F1~F4）+ 测试代理 V1。
- 后续修复：Widget 任务点击改走主界面统一 `resolveAndHandleTaskClick()`；返回栈复用；精确闹钟权限保护；高度 dp 计算修正；空状态占位修正；APP 侧主动刷新经 `DataChangeDispatcher` 解耦；标签点击 per-widget 筛选落地。
- 当前待验证：标签点击筛选、再次点击取消、多 Widget 独立筛选。

### 2026-05-26 — 主界面时间线最大集与生效时段强调

- **状态**：完成，编译通过。
- 时间线保留真实 `periods` 与显示用 `timelinePeriods` 两套输入；生效时段更深更粗强调刻度。

### 2026-05-24 — 安排页 UI 细节调整 + 选择器视觉统一

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

- 切换类型不抖动（minHeight=42dp）、每周 chip 字号统一、保存按钮圆角填充。
- 三种选择器对象 chip 样式按钮、每月月历 Dialog、DAILY 类型不留空白。

### 2026-05-23 — 安排任务模块重设计 + 实现

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

- 设计完成（brainstorming 7 反馈点）。实现覆盖 15 文件：数据层重建 + 槽位视图 + 闹钟调度 + 通知链路。
- 延后：多任务碰撞 DialogActivity。

### 2026-05-24 — 安排模块排查修复（4 Pack）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

- TaskScheduleMatcher 权威实现 + TaskStartGuard 统一校验 + UNIQUE 策略 + v10->v11 DROP+CREATE。
- `TaskScheduleMatcherTest` 34 用例。全量 218 用例 0 失败。

### 2026-05-24 — 安排页槽位视图重做 + 点击拦截 + 主线程 DB 修复

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

10min 粒度 + 6 列 GridLayout + 范围选中。执行中专注任务右侧栏点击拦截。缓存预热修复主线程 Room 崩溃。

### 2026-05-22 — 任务详情页 UI 修复系列

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — 进度日志

- Toolbar 复用 Activity、APP 项视觉对齐、整行点击跳转。
- 底部按钮 Space 均匀分布。
- APP 项跳转后 click handler 顺序修正。

### 2026-05-21 — 专注任务 < 15min 完成引导 & 时间线已完成条按实际耗时

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

ShortCompletionDialog 三按钮 + ChoreHiddenTodayStore。`testDebugUnitTest` 158 用例全绿（+22 新增）。

### 2026-05-21 — 任务详情页样式 + 事件 LiveData 残留修复

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — 进度日志

底部按钮风格统一到对话框规范；三个事件 LiveData 改为 `SingleLiveEvent` 修复返回重复触发。

### 2026-05-21 — APP 操作编辑修复 + 任务点击分流回归修复

> 详见：[modules/task-input.md](modules/task-input.md) — 进度日志

自定义 Filter + 下标错位 + 包可见性。`resolveAndHandleTaskClick` 顶层按 isExecuting 分支。`TimelineTaskState` 重构。

### 2026-05-20 — 精确闹钟权限崩溃修复与统一权限引导

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

PermissionHelper 统一工具。启动时无权限静默跳过。保存安排时无权限弹引导对话框。

### 2026-05-19 — D020 提醒延迟模块实现

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

数据层 + 调度层 + 广播层 + UI 层。AlarmReceiver Bug 修复（scheduleId->taskId）。53 个新增测试用例。130 用例 0 失败。

### 2026-05-18 — 代码审查问题修复

- **状态**：完成，编译+测试通过。
- 删除死代码、消除重复查询、TimelineBuilder 抽取、防抖处理。

### 2026-05-18 — 主界面非时段直接开始保护

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

非时段按钮视觉状态补齐、selector 统一色值、安排按钮点击前关闭对话框。

### 2026-05-18 — 任务录入四象限保存崩溃修复

> 详见：[modules/task-input.md](modules/task-input.md) — 进度日志

无标签写 null + loadTaskForEdit 清旧标签 + Robolectric 镜像配置。

### 2026-05-17/18 — 时间段组编辑对话框布局优化 + 统一样式收尾

> 详见：[modules/time-period.md](modules/time-period.md) — 进度日志

日期完整年份、时间行固定时间区、浅蓝编辑块+黑色文字。Dialog/TimeEdit 统一样式。AAPT 样式父级问题记录。

### 2026-05-16 — 节假日数据月度下载频率控制

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 进度日志

架构重构：fetch() 返回 Entity + throws IOException + parser 填 fill()。compileDebugJavaWithJavac + testDebugUnitTest BUILD SUCCESSFUL。

### 2026-05-16 附录 — 时间段 UI 微调

WORKDAY 开关首次启用不弹回；非假日组无预设时段才弹编辑窗；常规组不再等待缓存异步。

### 2026-05-15 — 节假日数据驱动工作日判断 + 无数据兜底 + androidTest 维护

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 进度日志
> 详见：[modules/testing.md](modules/testing.md) — 进度日志

完整业务流程测试（77 用例 0 失败）。无节假日数据兜底逻辑实现。androidTest 源码修正。

### 2026-05-14 — 节假日数据源模块实现

数据源接口 + IcsParser + ChinaGovSource + HolidayCacheManager + HolidaySyncWorker。时段组改造（删新年、增长假）。

### 2026-05-14 — 任务执行链路重构

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

主体代码已落地，Java 编译通过。待真机/模拟器运行与迁移验证。

### 2026-05-15 — 任务执行规则收敛 + 琐碎任务展示规则实现

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

琐碎任务不进入左侧时间线、不能安排、完成后当天不列入右侧栏。

### 2026-05-15 — 测试策略记录

> 详见：[modules/testing.md](modules/testing.md)

### 2026-05-15 — 时段组编辑功能 & 数据源修复

崩溃修复（HolidayCacheManager 主线程异步化）、编辑对话框、开关逻辑、初始化默认填充。

### 2026-05-14 — 时间段模块地区与互斥规则落地

RegionSettings 主线入口、证券从业地区过滤、春节/新年互斥。

### 2026-05-13 — 主界面底部时段栏

> 详见：[modules/ui-design.md](modules/ui-design.md) — 进度日志

紧凑版实现 -> 非紧凑调整（72dp + 1dp 分隔线 + 独立背景色 + 按钮替代 FAB）。跨天提示文案调整。

### 2026-05-12 — 主界面下一步工作

主界面多标签筛选弹层交互调整：长按预选当前标签、未选禁用确定。

### 2026-05-12 — 左侧栏时间线全面重做

TimelineView + HourColumnView 全面重写：时钟式刻度 + 液体色块 + 指南针浮标。字体规范整理。

### 2026-05-11/12 — 标签模块全线完成 + 项目规范化

> 详见：[modules/tag.md](modules/tag.md) — 进度日志

单/多标签筛选 + 优先标签持久化 + TagManageFragment + UnusedTagFragment。字体规范统一三档。

### 2026-05-08 — 硬编码字符串全面资源化

6 文件 16 处修复。编译 + 26 单元测试全部通过。

### 2026-05-08 — 任务输入流程重构

DB 迁移 2->3 + jieba 分词 + TextTokenizer + 双屏 UI。26 单元测试通过。

### 2026-05-08 — 标签 UI 交互打磨 + 阶段 2-6 完成 + 打磨测试

超链接标签交互、Bug 修复 7 项、资源提取、UI 打磨、死代码清理。

### 2026-05-07 — 需求分析与模块拆分

创建规划文件结构，12 条需求 -> 8 大模块。阶段 1 基础设施搭建完成。

---

## 模块文档进度总览

| 模块 | 文档 | 状态 |
|------|------|------|
| 检索引擎 | [modules/search-engine.md](modules/search-engine.md) | 已打磨 |
| 智能展示引擎 | [modules/smart-display.md](modules/smart-display.md) | 已打磨 |
| 时间段计算 | [modules/time-period.md](modules/time-period.md) | 已实现 |
| 剩余时间计算 | [modules/time-remaining.md](modules/time-remaining.md) | 已实现 |
| 节假日数据 | [modules/holiday-data.md](modules/holiday-data.md) | 已实现 |
| 桌面 Widget | [modules/widget.md](modules/widget.md) | 已实现 |
| 数据统计 | [modules/stats.md](modules/stats.md) | 待开始 |
| 任务执行 | [modules/task-execution.md](modules/task-execution.md) | 已实现 |
| 提醒延迟 | [modules/reminder-delay.md](modules/reminder-delay.md) | 已完成 |
| 提醒详情页 | [modules/reminder-detail.md](modules/reminder-detail.md) | 已完成 |
| 标签 | [modules/tag.md](modules/tag.md) | 已打磨 |
| 任务编辑页 | [modules/task-edit.md](modules/task-edit.md) | 已完成 |
| 任务录入 | [modules/task-input.md](modules/task-input.md) | 已完成 |
| 四象限任务管理 | [modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) | 已完成 |
| 四象限降级恢复 | [modules/quadrant-degrade.md](modules/quadrant-degrade.md) | 设计中 |
| UI 设计 | [modules/ui-design.md](modules/ui-design.md) | 已打磨 |
| APP 图标 | [modules/app-icon.md](modules/app-icon.md) | 待开始 |
| 品牌体系 | [modules/brand.md](modules/brand.md) | 纯规范 |
| 测试策略 | [modules/testing.md](modules/testing.md) | 已记录 |
