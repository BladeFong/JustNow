# Code Review Report — 2026-07-18

## 审查范围

- **Git 基线**: `8fe298eb..HEAD`（2026年7月全部提交）
- **提交消息**: 94 commits（涵盖任务完成模式、平板适配、儿童图标、时光胶囊、主题色、拍照流程等重大功能迭代）
- **模块**: 全部主模块（MainFragment、MainActivity、TimeCapsuleWallActivity、TaskEditFragment、QuadrantFragment、TaskFilterHelper、TaskRepository、ReminderScheduler、DisplayEngine 等）
- **变更规模**: 187 文件变更，13992 行新增，905 行删除
- **审查标准**: 代码逻辑正确性、数据一致性、性能、国际化、设计可维护性、项目规范符合度

## 结果概要

| 级别 | 数量 | 决策 |
|------|:----:|:----:|
| blocking | 2 | 必修复 |
| important | 5 | 应讨论修复 |
| suggestion | 4 | 建议改进 |
| nit | 4 | 轻微问题 |
| **合计** | **15** | **Comment** |

## 审核记录 — 2026-07-18 提交 ea1db3d

| 状态 | 数量 |
|:----:|:----:|
| 已修复 | 12 |
| 部分修复 | 2 |
| 未修复 | 0 |

## 审核记录 — 2026-07-18 第二轮（工作区未提交改动）

| 状态 | 数量 |
|:----:|:----:|
| 已修复 | 2（#7 字号修复完毕、#10 TaskDialogFactory 提取） |
| 新增问题 | 0 |
| 未修复 | 0 |

---

## 发现详情

### [x] #1 [blocking] 主题色存储不一致，TimeCapsuleWallActivity 无法反映用户设定的主题色

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`getGlobalThemeColor/setGlobalThemeColor`）
- `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`（`getGlobalThemeColor`）

**问题**: `MainFragment.getGlobalThemeColor()` 从 SharedPreferences 文件 `"capsule_settings"` 读取 int 型 key `"theme_color"`，而 `TimeCapsuleWallActivity.getGlobalThemeColor()` 从不同文件 `"justnow_prefs"` 读取 string 型 key `"global_theme"`（值 "pink"/"blue"）。两种存储机制完全不互通，用户在 MainFragment 设定的主题色不会反映在照片墙页面中。

**建议**: 统一为同一 SharedPreferences 文件 + 同一 key 格式。TimeCapsuleWallActivity 应复用 MainFragment 的 `getGlobalThemeColor()` 方法（或提取为共享工具方法）。

**审核结果**: 已修复。`getGlobalThemeColor()` / `setGlobalThemeColor()` 统一读写 `capsule_settings` SP 的 `theme_color` (int) key。`resolveThemeStyle()` 方法供 `TimeCapsuleWallActivity.onCreate()` 在 `setContentView()` 前调用，选择 `Theme.JustNow`（蓝）或 `Theme.JustNow.Pink`（粉）。照片墙标题栏和状态栏也使用 `MainFragment.getGlobalThemeColor()` 着色。

---

### [x] #2 [blocking] 动画图标标签 "屏幕" 与 TagLocalizer key "动画" 不匹配，导致错误标签写入数据库

**文件**:
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`（`setupIconSelector`）
- `app/src/main/java/com/nearby/justnow/util/TagLocalizer.java`（`NAME_TO_RES_MAP`）

**问题**: `setupIconSelector()` 中 `IconItem("animation", ..., "屏幕")` 的 label 为 "屏幕"，但 `TagLocalizer.NAME_TO_RES_MAP` 的 key 为 "动画"。选择动画图标时：
1. `TagLocalizer.getLocalizedName(requireContext(), "屏幕")` 找不到匹配 → 返回 "屏幕" 原值
2. 保存时 `getDbTagName(context, "屏幕")` 无匹配 → 存储 "屏幕" 而非正确的 "动画"
3. 十个图标中只有 animation 的 label（"屏幕"）与 TagLocalizer key（"动画"）不统一，导致该图标关联的标签数据损坏

**建议**: 将 `NAME_TO_RES_MAP` 的 key 从 "动画" 改为 "屏幕"，并与英文翻译 `tag_animation=Screen` 对齐；或统一 icon label 为 "动画"。English strings.xml 中 `tag_animation` 译为 "Screen" 而非 "Animation"，也需同步核对语义一致性。

**审核结果**: 已修复。`TagLocalizer.NAME_TO_RES_MAP` 的 key 从 "动画" 改为 "屏幕"（第 24 行）。`getLocalizedName("屏幕")` 正确返回对应翻译。英文 `tag_animation=Screen` 与 DB key "屏幕" 语义一致（均指屏幕/影视类活动）。

---

### [x] #3 [important] Tag filter chip 硬编码 `#` 前缀，未使用 `s_tag_name_format` 资源

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`populateFilterChips`）

**问题**: `populateFilterChips` 中 `chip.setText("#" + TagLocalizer.getLocalizedName(...))` 在 Java 代码中硬编码 `#` 前缀，而非使用 `R.string.s_tag_name_format`（`"#%s"`）。项目另两处已有使用该格式字符串的先例（`QuadrantTaskListFragment` 和 `QuadrantTaskListAdapter`），新代码不一致。此外硬编码 `#` 无法根据不同 locale 定制前缀格式。

**建议**: 使用 `getString(R.string.s_tag_name_format, localizedName)` 统一调用方式。

**审核结果**: 已修复。第 577 行使用 `getString(R.string.s_tag_name_format, ...)`。

---

### [x] #4 [important] 补拍弹窗列表项副标题无 ID，始终显示固定中文文本

**文件**: 
- `app/src/main/res/layout/item_retroactive_task.xml`

**问题**: 副标题 `TextView`（"任务已做完，快点击拍照记录成果吧！"）无 `android:id`，`RetroactiveAdapter` 中未给该 View 赋值，因此每行都显示相同的硬编码中文提示。虽然提示语本身不变化，但不可国际化且无法按需定制。

**建议**: 为副标题添加 `android:id`，将文本提取到 `strings.xml` 并使用 `@string/...` 引用。

**审核结果**: 已修复。`item_retroactive_task.xml` 添加 `android:id="@+id/tv_retroactive_hint"`，文本引用 `@string/s_retroactive_item_hint`，字号使用 `TextAppearance.JustNow.Caption`。

---

### [x] #5 [important] 配额输入框无最大值检查，`maxVal` 标签未使用

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantFragment.java`（`setupCompletionModeChips`）

**问题**: `etQuota.setTag(maxVal)` 保存了周期最大配额数，但 `afterTextChanged` 中 `Integer.parseInt(s.toString())` 后直接 `mViewModel.setQuota(val)`，从未检查 `maxVal` 约束。用户可输入超出合理范围的值。

**建议**: 在 `afterTextChanged` 中添加 `maxVal` 校验：`int quota = Math.min(val, maxVal)`。

**审核结果**: 已修复。`TextWatcher` 读取 `etQuota.getTag()` 获取 `maxVal`，执行 `val = Math.min(val, maxVal)`。

---

### [x] #6 [important] 新布局文件全部使用硬编码中文文本，未国际化为 string 资源

**文件**（6 个新布局）:
- `app/src/main/res/layout/activity_time_capsule_wall.xml`
- `app/src/main/res/layout/dialog_congratulation.xml`
- `app/src/main/res/layout/dialog_congrats.xml`
- `app/src/main/res/layout/dialog_retroactive_list.xml`
- `app/src/main/res/layout/item_time_capsule_card.xml`
- `app/src/main/res/layout/item_retroactive_task.xml`

**问题**: 上述布局文件中所有用户可见文本均为硬编码中文（如 "🖼️ 本周时光胶囊成果墙"、"🏆 任务完成祝贺"、"快去让爸爸妈妈帮忙，\n拍照记录成果吧！"、"我知道啦"、"📸 待补拍任务" 等），未使用 `@string/...` 引用。项目支持 EN / zh-CN / zh-TW / zh-HK 四种语言，硬编码文本在非中文环境下既不自动翻译也无法维护。

**建议**: 将所有用户可见文本提取到 `strings.xml`，布局中引用 `@string/...`。

**审核结果**: 已修复。6 个布局用户可见文本全部改用 `@string/...` 资源。`values/strings.xml`（EN）、`values-zh-rCN/strings.xml`（简体）、`values-zh-rTW/strings.xml`（繁体台湾）、`values-zh-rHK/strings.xml`（繁体香港）均已翻译。`item_time_capsule_card.xml` 和 `item_retroactive_task.xml` 中的 "任务标题"、"周一 10:15 AM" 为设计时占位符（运行时被代码覆盖），不影响国际化覆盖。

---

### [x] #7 [important] 布局文件中硬编码字号，未使用 textAppearance 系统

**文件**:
- `app/src/main/res/layout/activity_time_capsule_wall.xml`（`android:textSize="18sp"`、`"24sp"`）
- `app/src/main/res/layout/dialog_retroactive_list.xml`（`android:textSize="20sp"`、`"18sp"`）
- `app/src/main/res/layout/item_retroactive_task.xml`（`android:textSize="16sp"`、`"12sp"`）

**问题**: 项目规范要求字号统一在 `values/dimens.xml` 定义（`text_size_title=22sp`、`text_size_body=18sp`、`text_size_caption=16sp`），新布局直接硬编码 `android:textSize`，且超范围使用 12sp（低于最小 16sp 档位）。

**建议**: 改用 `textAppearance="@style/TextAppearance.JustNow.Title/Body/Caption"`。

**审核结果**: 已修复。
- 已修复：`activity_time_capsule_wall.xml` 标题改用 `TextAppearance.JustNow.Body`；`dialog_retroactive_list.xml` 标题改用 `TextAppearance.JustNow.Title`、关闭按钮改用 `TextAppearance.JustNow.Body`；`dialog_congratulation.xml` 和 `dialog_congrats.xml` 各文本已用 textAppearance；`item_retroactive_task.xml` 副标题改用 `TextAppearance.JustNow.Caption`；`item_retroactive_task.xml` 标题 `textSize="16sp"`→`TextAppearance.JustNow.Caption`；`item_time_capsule_card.xml` 标题 `textSize="15sp"`→`TextAppearance.JustNow.Caption`、时间 `textSize="12sp"`→`TextAppearance.JustNow.Caption`；`fragment_main_page0.xml` `textSize="16sp"`（▲指示符）→`TextAppearance.JustNow.Caption`。
- 注释说明保留：`activity_time_capsule_wall.xml` 返回箭头 24sp（展示性图标）、`dialog_congratulation.xml` 赞美标题 26sp（展示性标语）保留硬编码并加注释。

---

### [x] #8 [suggestion] TimelineView.onDraw() 每帧创建 Paint 对象

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/TimelineView.java`（`onDraw` 方法）

**问题**: 在 `onDraw()` 中每帧 `new Paint(Paint.ANTI_ALIAS_FLAG)` 创建 `completedStripPaint` 和 `ongoingPaint` 两个新对象，造成 GC 压力。`onDraw()` 在动画/滚动期间每秒调用数十次。

**建议**: 将这两个 Paint 提升为成员变量（`mCompletedStripPaint`、`mOngoingPaint`），在 `init()` 中初始化，`onDraw()` 仅更改颜色值。

**审核结果**: 已修复。`mCompletedStripPaint`（第 57 行）和 `mOngoingPaint`（第 58 行）声明为成员变量在构造方法中初始化，`onDraw()` 通过 `setColor()`/`setStyle()` 修改属性，不再创建新对象。

---

### [x] #9 [suggestion] CongratulationsDialog / CongratulationDialog 导入未使用的 TTS 和音频类

**文件**:
- `app/src/main/java/com/nearby/justnow/ui/main/CongratulationsDialog.java`
- `app/src/main/java/com/nearby/justnow/ui/dialog/CongratulationDialog.java`

**问题**: 两个 Dialog 均导入 `TextToSpeech`、`Ringtone`、`RingtoneManager`、`AudioAttributes`、`AudioManager`、`Locale` 等类，但自 commit `fc66298`（"完全剥离了两个祝贺对话框中的所有 TextToSpeech 发音与注销逻辑"）后已不再使用任何 TTS/音频功能。遗留导入造成混淆。

**建议**: 清理未使用的 import 语句。

**审核结果**: 已修复。两个 Dialog 各移除 8 行未使用的 TTS/音频导入。

---

### [x] #10 [suggestion] MainFragment 达 1730 行，严重违反单一职责原则

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**问题**: MainFragment 囊括了时间线、任务过滤、标签管理、花朵收集栏布局/方向重建、相机拍照、主题色管理、图标预览弹窗、补拍弹窗、生命周期管理、onActivityResult 图片写入等十多个职责。同一方法内部代码量过大（如 `refreshWeeklyFlowers` 约 80 行，在 `AppDatabase.execute` 中嵌套闭包操作 UI 线程）。

**建议**: 将花朵收集栏、相机拍照流程、主题色管理、图标预览拆分为独立的 Fragment/Helper/Manager 类。已存在主界面碎片迹象，继续叠加将难以维护。

**审核结果**: 进一步修复（两阶段）。
- 第一阶段（RewardBarFragment）：花朵收集栏（布局/方向重构/补拍/相机/通关祝贺）拆至新 `RewardBarFragment`（537 行），MainFragment 从 ~1730 行降至 ~1298 行。
- 第二阶段（TaskDialogFactory）：新建 `TaskDialogFactory.java`（412 行）承载全部对话框逻辑，包括 `showTaskDetailDialog`、`handleTimelineScheduledTaskClick`、`handleOnlyTitleTaskComplete`、`showChecklistStateConfirmDialog`、`handleTaskStart`、`showTimelineCompletionDialog`、`showChoreCompletionDialog` 及全部私有辅助方法。`getFocusText`/`getStartBlockReason` 迁入工厂内部；3 个扩展点（`onTimelineCompletionDialogShown`、`onChoreCompletionDialogShown`、`onFocusTaskCompleted`）设为 `default` 空方法。MainFragment 实现 `TaskDialogFactory.Callback` 接口（14 个委托方法），移除约 400 行对话框代码。MainFragment 当前约 890 行，较初始 1730 行减少约 48%。
- 仍承担时间线、标签管理、主题色调度、观察者注册等职责，后续可继续拆分。

---

### [x] #11 [suggestion] `verifyAndCleanupPhotos` 定义但从未被调用

**文件**: 
- `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`

**问题**: `verifyAndCleanupPhotos(Context)` 方法提供了外部 URI 物理存在性校验与失效记录清理功能，但没有在任何地方调用。该方法可能是有意保留供后续使用，但当前状态下是死代码。

**建议**: 确认是否需要此方法，若不需要则移除；若需要则在合适时机（如启动时或每周刷新时）调用。

**审核结果**: 已修复。`verifyAndCleanupPhotos` 方法已从 `TaskPhotoRepository.java` 移除（删除 30 行），仓库精简为 58 行的纯 CRUD 方法。

---

### [x] #12 [suggestion] TimeCapsuleWallActivity 全屏 Dialog 使用平台主题

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`（`showFullScreenPhoto`）

**问题**: `showFullScreenPhoto()` 使用 `new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)` 创建全屏图片预览 Dialog。该平台主题不会继承 AppCompat/Material 主题属性，可能导致字体、颜色、行为不一致。

**建议**: 使用 `R.style.ThemeOverlay_AppCompat_Dark` 或应用自定义全屏 Dialog 主题以确保 Material 组件兼容性。

**审核结果**: 已修复。第 231 行使用 `R.style.ThemeOverlay_JustNow_FullscreenDialog`（继承 `ThemeOverlay.MaterialComponents`，设置 `windowFullscreen=true`、`windowNoTitle=true`、背景黑色）。在 `themes.xml` 中定义。

---

### [x] #13 [nit] Tag 名称本地化后在 filter 场景未使用 `getDbTagName` 逆向回写

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantTaskListFragment.java`（第 328 行）
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantTaskListAdapter.java`（第 84 行）

**问题**: 这两处使用 `getString(R.string.s_tag_name_format, tag.name)` 显示 tag name，但未经过 `TagLocalizer.getLocalizedName()` 转换。当用户选择儿童图标绑定的内置标签时，数据库中存储的是中文 key（如 "美术"），在英文环境下会直接显示 "美术" 而非 "Art"。

**建议**: 使用 `TagLocalizer.getLocalizedName(requireContext(), tag.name)` 包装显示的 tag name。

**审核结果**: 已修复。`QuadrantTaskListFragment.java` 第 329 行和 `QuadrantTaskListAdapter.java` 第 84-85 行均使用 `TagLocalizer.getLocalizedName(requireContext(), tag.name)` 包装标签名称。

---

### [x] #14 [nit] 补拍按钮文本使用 Emoji + 中文硬编码

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`refreshWeeklyFlowers` 中 `setText("📸 补拍 (...)")`）
- `app/src/main/res/layout/fragment_main_page0.xml`（`android:text="📸 补拍"`）

**问题**: 补拍按钮文本既有布局中的硬编码默认值，也有 Java 代码中的动态更新。两处均使用硬编码中文 + Emoji，未通过 string 资源。

**建议**: 布局中默认值为 `tools:text` 或 `@string/...`，代码中使用 `getString(R.string.s_retroactive_photo_format, count)`。

**审核结果**: 已修复。补拍按钮移至 `fragment_reward_bar.xml`，默认文本引用 `@string/s_retroactive_photo`。`RewardBarFragment.refreshWeeklyFlowers()` 使用 `getString(R.string.s_retroactive_photo_count, count)`。提醒弹窗标题/消息/按钮文本均使用 string 资源。EN / zh-CN / zh-TW / zh-HK 四种语言均已翻译。

---

### [x] #15 [nit] `#` 前缀在过滤弹窗中被硬编码用于本地化标签

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`populateFilterChips`，第 549 行）

**问题**: `chip.setText("#" + TagLocalizer.getLocalizedName(requireContext(), tag.name))` 对本地化后的名称硬加 `#` 前缀。日文/中文/其他地区可能使用不同标签前缀规范（如 `#` 不通用）。

**建议**: 复用 `R.string.s_tag_name_format` 资源。

**审核结果**: 已修复。同 #3，第 577 行使用 `getString(R.string.s_tag_name_format, ...)`。

---

## 未处理项汇总

| 编号 | 级别 | 问题摘要 | 文件 | 处理状态 |
|:----:|:----:|----------|------|:--------:|
| #10 | suggestion | MainFragment 890 行仍较大，时间线/标签/主题色等职责尚未完全拆分 | MainFragment.java | 进一步修复 |
