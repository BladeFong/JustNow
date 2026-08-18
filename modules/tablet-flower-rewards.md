# 阶段规划、决策记录 (时光胶囊与七朵花花瓣奖励机制)

## 1. 概述与定位
为平板端（及非平板端）引入“时光胶囊照片回顾”与“七朵花点亮”的游戏化奖励机制。通过统一的主题色系统消除界面突兀色（如默认的 Material 3 紫色），规范两大祝贺弹窗（单任务完成及周大奖通关）的视觉与发声行为。

## 2. 规划步骤
- **第一阶段：主题色 SharedPreferences 存储与设置弹窗实现**
  - 在 SharedPreferences 中定义全局主题色存储。
  - 新增“设置主题色”菜单，点击弹出单选题对话框，支持“活力蓝”与“童趣粉”切换。
- **第二阶段：主界面及花朵已点亮花瓣颜色动态绑定**
  - 提取当前全局激活的主题色，动态对主界面的“添加任务按钮”和“补拍按钮”进行染色覆盖。
  - 修改 `FlowerCapsuleView`，使其已点亮花瓣的颜色（`mActiveColor`）与此主题色完全统一。
- **第三阶段：两大祝贺对话框去紫色与像素级原型对齐**
  - 重构 `CongratulationDialog`，添加顶部高 48dp 贴边 Header 栏，并在 Java 中动态修改按钮和字体的颜色为任务象限色，消除紫色。
  - 重构 `CongratulationsDialog`，使其我知道啦按钮的背景与字体色随全局主题色变化，消除紫色。
  - 消除紫色突兀色与主题色对齐（注：此前规划的 TTS 复杂语音播报已在精简中取消）。

---

# 研究发现、技术决策、需求分析

## 2026-08-18 平板端任务扫码传图系统设计 (手机免安装局域网直传)

- **业务场景解耦**：针对户外活动不便携带平板的场景，支持手机在无需安装任何 APP 的前提下，通过局域网（家庭 Wi-Fi 或手机热点）扫码打开平板内置 H5 页面，将照片（可多选）极速同步给平板并与任务关联。
- **技术决策**：
  - **嵌入式零依赖服务**：采用 Android 原生内置 `com.sun.net.httpserver.HttpServer`，绑定 `QrUploadDialog` 生命周期（即开即关，零常驻后台）。
  - **对等 5 秒心跳感知**：手机端 1 秒 ping 探测，双端统一在 5 秒无心跳时对等判定离线；平板休眠时长以 `min(systemTimeout, 3分钟)` 为准，手机连接期间临时保持常亮。
  - **手机端 Canvas 智能压缩**：大于 3MB 或超高分辨率图片通过 HTML5 Canvas 等比缩放到 2048px 高清（质量 0.92，体积 800KB~1.8MB 极速秒传），小于 3MB 原图直传。
  - **单任务 5 张上限控制**：多端严格对齐 `task_photos` 每任务 5 张照片配额限制，上传成功自动触发 `RewardBarFragment.refreshWeeklyFlowers()`。
  - **规范化澄清**：历史文档中关于花瓣点亮复杂动画及 TTS 语音播报确认已在精简中取消，本次设计保持代码与文档事实一致。

## 2026-08-01 儿童奖励系统升级 (每周通关天数 + 工作日花芯默认填充)

- **菜单入口升级**：右上角 `查看内置图标` 重命名为 `儿童奖励设置`，弹窗合成了每周通关目标天数单选器 (2~5天，默认3天) 与内置图标参考网格。目标天数保存在 SharedPreferences (`weekly_reward_target_days`)。
- **动态通关判定**：`RewardBarFragment` 读取配置的目标天数 `targetDays`，当每周连续达成天数 `activeCount >= targetDays` 时触发通关动画与高亮边框。
- **非寒暑假工作日花芯默认填充**：在 `refreshWeeklyFlowers()` 中结合 `PeriodGroupRuleResolver.isWorkdaySync(cal)` 与寒暑假判定，对于非寒暑假期间的工作日（含法定补班日，不受工作日时段组启用状态影响），预先初始化 `centerFilled[i] = true`，当天的紧急重要任务可以直接累计花瓣。

## 2026-07-26 拍照按钮修复 + 任务完成统一流程

- 拍照按钮搬回底栏 `fragment_main_page0.xml`，恢复固定 44dp 高度，通过 `setTakePhotoButton()` 注入实例
- 查询拆为 `getTodayCompletedTasks` + `getRunningTaskSync` 两独立查询 Java 合并
- Android 13+ CAMERA 权限：Manifest 声明 + `ActivityResultLauncher` 运行时申请
- 统一完成流程 `completeTaskUnified(keepTimelineRecord)`，短完成写 status=3
- `isChildTask` = `isTablet && iconName != null`，封装在 JustNowApplication
- 移除 `ChoreHiddenTodayStore.hideForToday`

## 1. 缺陷分析与根因（历史）
* **无声问题根因**：
  1. `TextToSpeech` 实例化时使用了 Dialog 的 `getContext()`。在 Service 绑定时 Dialog Context 易导致失效，需一律使用全局的 `getApplicationContext()`。
  2. 部分 Android 测试设备（尤其是模拟器）关闭了 Notification 音量通道，或者未安装中文离线 TTS 包。
* **紫色字体与突兀色根因**：默认使用 Material 3 组件（如 `MaterialButton` 的 TextButton 样式）时，字体颜色会默认套用 App 主题中定义的主色（Primary Color，即紫色）。必须在 Java 中通过 `setTextColor()` 或 `setBackgroundTintList()` 显式覆盖。

## 2. 技术选型决策
* **音效强行映射**：铃声音效在播放时强行通过 `setLegacyStreamType(AudioManager.STREAM_MUSIC)` 路由至多媒体音乐通道，绕过通知音静音屏蔽，保障 100% 能够发出声音。
* **主题色自适应机制**：
  - 非平板模式默认：`🔵 活力蓝 (#1A73E8)`。
  - 平板模式默认：`🌸 童趣粉 (#FF4081)`。
  - 允许用户手动覆盖，并在确认修改时立即通知主界面及自定义 View 进行 `invalidate()` 重绘刷新。
* **沉浸式标准 Activity 照片墙**：
  - **避开 Dialog 状态栏变色失效**：Dialog 本身的 WindowTheme 机制会锁定顶部状态栏的亮度或加设半透明蒙层。在实现“状态栏与标题栏颜色一致的无缝融和”时，必须使用标准的 `Activity`（即 `TimeCapsuleWallActivity`），通过 `window.setStatusBarColor()` 完美修改状态栏颜色。
  - **规范化标题栏与只读性**：标题栏左侧配置显式白色返回键，右侧禁设关闭小叉。并且，照片卡片上的右上角删除叉号及后台对应删除数据库行为全部物理移除，保持时光胶囊成果墙纯粹只读。
* **周一计算抗 Locale 漂移机制**：使用 `Calendar` 对象的 `set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)` 进行本周一零点计算时，会由于设备时区、国家 Locale、系统 FirstDayOfWeek 的差异产生 7 天级别的跳跃漂移。必须改用纯数学相对偏移减法（`daysOffset = (dayOfWeek + 5) % 7`）进行本周时间戳的物理回缩，确保 100% 仅过滤当周成果照片。
* **通关状态直角描边背景**：通关时只在 Java 层动态创建直角（`cornerRadius = 0`）的 `GradientDrawable`，配置 2dp 粗的主题色描边与极浅底色，废除多余九宫格图片，实现高度规整、高复用度的自适应平铺直角边框。
* **内置活动分类调整与映射对齐**：
  - **泛益智界定去歧**：将原“益智”分类更名为更具体的“桌游”（英文为 Board Game），避开与日常益智玩具、屏幕早教等范畴产生认知边界重合。
  - **屏幕与 animation 映射修复**：早期代码中 UI 显示“屏幕”但 DAO 过滤及多语言网关关联词却写为“动画”，导致内置“屏幕”标签无法被正确从常用标签栏拦截剔除。已全系统统一改用“屏幕”（繁体为“螢幕”，英文为“Screen”），在 TagLocalizer 和 TagDao 中同步修正过滤词。
