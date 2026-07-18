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

---

## 发现详情

### [ ] #1 [blocking] 主题色存储不一致，TimeCapsuleWallActivity 无法反映用户设定的主题色

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`getGlobalThemeColor/setGlobalThemeColor`）
- `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`（`getGlobalThemeColor`）

**问题**: `MainFragment.getGlobalThemeColor()` 从 SharedPreferences 文件 `"capsule_settings"` 读取 int 型 key `"theme_color"`，而 `TimeCapsuleWallActivity.getGlobalThemeColor()` 从不同文件 `"justnow_prefs"` 读取 string 型 key `"global_theme"`（值 "pink"/"blue"）。两种存储机制完全不互通，用户在 MainFragment 设定的主题色不会反映在照片墙页面中。

**建议**: 统一为同一 SharedPreferences 文件 + 同一 key 格式。TimeCapsuleWallActivity 应复用 MainFragment 的 `getGlobalThemeColor()` 方法（或提取为共享工具方法）。

---

### [ ] #2 [blocking] 动画图标标签 "屏幕" 与 TagLocalizer key "动画" 不匹配，导致错误标签写入数据库

**文件**:
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`（`setupIconSelector`）
- `app/src/main/java/com/nearby/justnow/util/TagLocalizer.java`（`NAME_TO_RES_MAP`）

**问题**: `setupIconSelector()` 中 `IconItem("animation", ..., "屏幕")` 的 label 为 "屏幕"，但 `TagLocalizer.NAME_TO_RES_MAP` 的 key 为 "动画"。选择动画图标时：
1. `TagLocalizer.getLocalizedName(requireContext(), "屏幕")` 找不到匹配 → 返回 "屏幕" 原值
2. 保存时 `getDbTagName(context, "屏幕")` 无匹配 → 存储 "屏幕" 而非正确的 "动画"
3. 十个图标中只有 animation 的 label（"屏幕"）与 TagLocalizer key（"动画"）不统一，导致该图标关联的标签数据损坏

**建议**: 将 `NAME_TO_RES_MAP` 的 key 从 "动画" 改为 "屏幕"，并与英文翻译 `tag_animation=Screen` 对齐；或统一 icon label 为 "动画"。English strings.xml 中 `tag_animation` 译为 "Screen" 而非 "Animation"，也需同步核对语义一致性。

---

### [ ] #3 [important] Tag filter chip 硬编码 `#` 前缀，未使用 `s_tag_name_format` 资源

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`populateFilterChips`）

**问题**: `populateFilterChips` 中 `chip.setText("#" + TagLocalizer.getLocalizedName(...))` 在 Java 代码中硬编码 `#` 前缀，而非使用 `R.string.s_tag_name_format`（`"#%s"`）。项目另两处已有使用该格式字符串的先例（`QuadrantTaskListFragment` 和 `QuadrantTaskListAdapter`），新代码不一致。此外硬编码 `#` 无法根据不同 locale 定制前缀格式。

**建议**: 使用 `getString(R.string.s_tag_name_format, localizedName)` 统一调用方式。

---

### [ ] #4 [important] 补拍弹窗列表项副标题无 ID，始终显示固定中文文本

**文件**: 
- `app/src/main/res/layout/item_retroactive_task.xml`

**问题**: 副标题 `TextView`（"任务已做完，快点击拍照记录成果吧！"）无 `android:id`，`RetroactiveAdapter` 中未给该 View 赋值，因此每行都显示相同的硬编码中文提示。虽然提示语本身不变化，但不可国际化且无法按需定制。

**建议**: 为副标题添加 `android:id`，将文本提取到 `strings.xml` 并使用 `@string/...` 引用。

---

### [ ] #5 [important] 配额输入框无最大值检查，`maxVal` 标签未使用

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantFragment.java`（`setupCompletionModeChips`）

**问题**: `etQuota.setTag(maxVal)` 保存了周期最大配额数，但 `afterTextChanged` 中 `Integer.parseInt(s.toString())` 后直接 `mViewModel.setQuota(val)`，从未检查 `maxVal` 约束。用户可输入超出合理范围的值。

**建议**: 在 `afterTextChanged` 中添加 `maxVal` 校验：`int quota = Math.min(val, maxVal)`。

---

### [ ] #6 [important] 新布局文件全部使用硬编码中文文本，未国际化为 string 资源

**文件**（6 个新布局）:
- `app/src/main/res/layout/activity_time_capsule_wall.xml`
- `app/src/main/res/layout/dialog_congratulation.xml`
- `app/src/main/res/layout/dialog_congrats.xml`
- `app/src/main/res/layout/dialog_retroactive_list.xml`
- `app/src/main/res/layout/item_time_capsule_card.xml`
- `app/src/main/res/layout/item_retroactive_task.xml`

**问题**: 上述布局文件中所有用户可见文本均为硬编码中文（如 "🖼️ 本周时光胶囊成果墙"、"🏆 任务完成祝贺"、"快去让爸爸妈妈帮忙，\n拍照记录成果吧！"、"我知道啦"、"📸 待补拍任务" 等），未使用 `@string/...` 引用。项目支持 EN / zh-CN / zh-TW / zh-HK 四种语言，硬编码文本在非中文环境下既不自动翻译也无法维护。

**建议**: 将所有用户可见文本提取到 `strings.xml`，布局中引用 `@string/...`。

---

### [ ] #7 [important] 布局文件中硬编码字号，未使用 textAppearance 系统

**文件**:
- `app/src/main/res/layout/activity_time_capsule_wall.xml`（`android:textSize="18sp"`、`"24sp"`）
- `app/src/main/res/layout/dialog_retroactive_list.xml`（`android:textSize="20sp"`、`"18sp"`）
- `app/src/main/res/layout/item_retroactive_task.xml`（`android:textSize="16sp"`、`"12sp"`）

**问题**: 项目规范要求字号统一在 `values/dimens.xml` 定义（`text_size_title=22sp`、`text_size_body=18sp`、`text_size_caption=16sp`），新布局直接硬编码 `android:textSize`，且超范围使用 12sp（低于最小 16sp 档位）。

**建议**: 改用 `textAppearance="@style/TextAppearance.JustNow.Title/Body/Caption"`。

---

### [ ] #8 [suggestion] TimelineView.onDraw() 每帧创建 Paint 对象

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/TimelineView.java`（`onDraw` 方法）

**问题**: 在 `onDraw()` 中每帧 `new Paint(Paint.ANTI_ALIAS_FLAG)` 创建 `completedStripPaint` 和 `ongoingPaint` 两个新对象，造成 GC 压力。`onDraw()` 在动画/滚动期间每秒调用数十次。

**建议**: 将这两个 Paint 提升为成员变量（`mCompletedStripPaint`、`mOngoingPaint`），在 `init()` 中初始化，`onDraw()` 仅更改颜色值。

---

### [ ] #9 [suggestion] CongratulationsDialog / CongratulationDialog 导入未使用的 TTS 和音频类

**文件**:
- `app/src/main/java/com/nearby/justnow/ui/main/CongratulationsDialog.java`
- `app/src/main/java/com/nearby/justnow/ui/dialog/CongratulationDialog.java`

**问题**: 两个 Dialog 均导入 `TextToSpeech`、`Ringtone`、`RingtoneManager`、`AudioAttributes`、`AudioManager`、`Locale` 等类，但自 commit `fc66298`（"完全剥离了两个祝贺对话框中的所有 TextToSpeech 发音与注销逻辑"）后已不再使用任何 TTS/音频功能。遗留导入造成混淆。

**建议**: 清理未使用的 import 语句。

---

### [ ] #10 [suggestion] MainFragment 达 1730 行，严重违反单一职责原则

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**问题**: MainFragment 囊括了时间线、任务过滤、标签管理、花朵收集栏布局/方向重建、相机拍照、主题色管理、图标预览弹窗、补拍弹窗、生命周期管理、onActivityResult 图片写入等十多个职责。同一方法内部代码量过大（如 `refreshWeeklyFlowers` 约 80 行，在 `AppDatabase.execute` 中嵌套闭包操作 UI 线程）。

**建议**: 将花朵收集栏、相机拍照流程、主题色管理、图标预览拆分为独立的 Fragment/Helper/Manager 类。已存在主界面碎片迹象，继续叠加将难以维护。

---

### [ ] #11 [suggestion] `verifyAndCleanupPhotos` 定义但从未被调用

**文件**: 
- `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`

**问题**: `verifyAndCleanupPhotos(Context)` 方法提供了外部 URI 物理存在性校验与失效记录清理功能，但没有在任何地方调用。该方法可能是有意保留供后续使用，但当前状态下是死代码。

**建议**: 确认是否需要此方法，若不需要则移除；若需要则在合适时机（如启动时或每周刷新时）调用。

---

### [ ] #12 [suggestion] TimeCapsuleWallActivity 全屏 Dialog 使用平台主题

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`（`showFullScreenPhoto`）

**问题**: `showFullScreenPhoto()` 使用 `new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)` 创建全屏图片预览 Dialog。该平台主题不会继承 AppCompat/Material 主题属性，可能导致字体、颜色、行为不一致。

**建议**: 使用 `R.style.ThemeOverlay_AppCompat_Dark` 或应用自定义全屏 Dialog 主题以确保 Material 组件兼容性。

---

### [ ] #13 [nit] Tag 名称本地化后在 filter 场景未使用 `getDbTagName` 逆向回写

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantTaskListFragment.java`（第 328 行）
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantTaskListAdapter.java`（第 84 行）

**问题**: 这两处使用 `getString(R.string.s_tag_name_format, tag.name)` 显示 tag name，但未经过 `TagLocalizer.getLocalizedName()` 转换。当用户选择儿童图标绑定的内置标签时，数据库中存储的是中文 key（如 "美术"），在英文环境下会直接显示 "美术" 而非 "Art"。

**建议**: 使用 `TagLocalizer.getLocalizedName(requireContext(), tag.name)` 包装显示的 tag name。

---

### [ ] #14 [nit] 补拍按钮文本使用 Emoji + 中文硬编码

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`refreshWeeklyFlowers` 中 `setText("📸 补拍 (...)")`）
- `app/src/main/res/layout/fragment_main_page0.xml`（`android:text="📸 补拍"`）

**问题**: 补拍按钮文本既有布局中的硬编码默认值，也有 Java 代码中的动态更新。两处均使用硬编码中文 + Emoji，未通过 string 资源。

**建议**: 布局中默认值为 `tools:text` 或 `@string/...`，代码中使用 `getString(R.string.s_retroactive_photo_format, count)`。

---

### [ ] #15 [nit] `#` 前缀在过滤弹窗中被硬编码用于本地化标签

**文件**: 
- `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`populateFilterChips`，第 549 行）

**问题**: `chip.setText("#" + TagLocalizer.getLocalizedName(requireContext(), tag.name))` 对本地化后的名称硬加 `#` 前缀。日文/中文/其他地区可能使用不同标签前缀规范（如 `#` 不通用）。

**建议**: 复用 `R.string.s_tag_name_format` 资源。

---

## 未处理项汇总

| 编号 | 级别 | 问题摘要 | 文件 |
|:----:|:----:|----------|------|
| #1 | blocking | 主题色存储不一致，TimeCapsuleWallActivity 无法反映用户设定的主题色 | MainFragment.java / TimeCapsuleWallActivity.java |
| #2 | blocking | 动画图标标签 "屏幕" 与 TagLocalizer key "动画" 不匹配，错误标签写入 DB | TaskEditFragment.java / TagLocalizer.java |
| #3 | important | Tag filter chip 硬编码 `#` 前缀，未使用 `s_tag_name_format` 资源 | MainFragment.java |
| #4 | important | 补拍弹窗副标题无 ID，始终显示固定中文文本 | item_retroactive_task.xml |
| #5 | important | 配额输入框无最大值检查，`maxVal` 标签未使用 | QuadrantFragment.java |
| #6 | important | 6 个新布局全部硬编码中文文本，未国际化为 string 资源 | 多布局文件 |
| #7 | important | 新布局硬编码字号，未使用 textAppearance 系统 | activity_time_capsule_wall.xml / dialog_retroactive_list.xml / item_retroactive_task.xml |
| #8 | suggestion | TimelineView.onDraw() 每帧创建 Paint 对象 | TimelineView.java |
| #9 | suggestion | CongratulationsDialog / CongratulationDialog 导入未使用的 TTS 和音频类 | CongratulationsDialog.java / CongratulationDialog.java |
| #10 | suggestion | MainFragment 达 1730 行，严重违反单一职责原则 | MainFragment.java |
| #11 | suggestion | `verifyAndCleanupPhotos` 定义但从未被调用 | TaskPhotoRepository.java |
| #12 | suggestion | TimeCapsuleWallActivity 全屏 Dialog 使用平台主题而非 AppCompat 主题 | TimeCapsuleWallActivity.java |
| #13 | nit | Tag 名称本地化后在 filter 场景未使用 TagLocalizer 转换 | QuadrantTaskListFragment.java / QuadrantTaskListAdapter.java |
| #14 | nit | 补拍按钮文本 Emoji + 中文硬编码 | MainFragment.java / fragment_main_page0.xml |
| #15 | nit | `#` 前缀在过滤弹窗中被硬编码用于本地化标签 | MainFragment.java |
