# 任务编辑页模块

> 对应 task_plan.md 编辑页样式修复与录入/编辑拆分阶段 / 任务详情页系统阶段

# 阶段规划、决策记录

## 定位和功能描述

TaskEditFragment 承载任务详情编辑：标题、标签、专注时长、Markdown 正文、附加模块（todo 清单 / APP 跳转），底部固定"下一步：选择象限"按钮。

与 TaskInputFragment 共用 `TaskInputViewModel`（Activity scope），编辑页从 ViewModel 恢复状态。

## 整体规划和决策

### 样式修复与拆分阶段（task_plan.md）
- [x] 国际化补齐：zh-CN/zh-TW/zh-HK 新增 21 个字符串翻译
- [x] 字体规范化：AutoCompleteTextView / RadioButton 补 `textAppearance.JustNow.Body`
- [x] 模块图标更换：`ic_menu_edit/share` -> 自绘 `ic_module_checklist` / `ic_module_app_action`，viewport 32dp
- [x] 图标选中态：`bg_module_button`（ripple + selected 灰色底）+ `selector_module_icon_tint`
- [x] 编辑页间距压缩：card 内边距 16->12dp
- [x] 专注时长两行布局：RadioButton，第一行 3 列第二行 2 列+占位 View 对齐，手动互斥管理
- [x] 底部象限按钮：MaterialButton + cornerRadius=16dp + wrap_content 居中
- [x] BottomSheet 按钮字体：`Widget.JustNow.Dialog.Button` / `.Secondary`
- [x] BottomSheet 高度撑满：`setOnShowListener` 中设置 `MATCH_PARENT`
- [x] 清单编辑器：首行 check box 不可删（三种缺失情况分别修复）；非首行删 check box 后空格 -> 删整行+换行
- [x] APP 跳转编辑器：`AppSearchAdapter` 带图标下拉；RecyclerView 新增图标 ImageView
- [x] 录入/编辑页面拆分：新建 `TaskEditFragment` + `fragment_task_edit.xml`；`TaskInputFragment` 精简为纯搜索页

### 已确认规则

**页面结构**
- 无 Toolbar 全屏布局：ScrollView（weight=1）+ 底部固定 MaterialButton。
- ScrollView 内竖向排列，16dp 内边距。

**标题**：MaterialCardView 内 EditText，`textAppearance.JustNow.Title`（22sp）。

**标签**：AutoCompleteTextView + ChipGroup 展示 top 6 已用标签。保存时：按名称查找已有标签 -> 查不到则新建；无标签->写 null。

**专注时长**
- 两行固定布局（非自动折行）：第一行 3 列 `[琐碎] [30分钟] [60分钟]`，第二行 2 列 `[90分钟] [120分钟] + 占位 View`。
- RadioButton 互斥由 Fragment 手动管理；默认选中 30 分钟。

**Markdown 正文**：MaterialCardView 内 EditText，Body 18sp，占据标签至附加模块之间的全部剩余空间。

**附加模块**
- 两个 ImageButton（48dp）：清单 / APP 跳转，自绘图标 36dp viewport。
- 背景 `bg_module_button`：圆角 8dp 矩形 ripple；选中态下沉动画（`module_button_state_list`）。
- 图标着色：`selector_module_icon_tint`。
- 单选逻辑：点击已选中->取消；点击未选中->弹出对应 BottomSheet 编辑器。
- 提示行固定 `layout_height=28dp`，避免抖动。

**底部按钮**
- MaterialButton + `cornerRadius=16dp` + `wrap_content` 居中。
- `textAppearance.JustNow.Body`（18sp）。
- 点击校验标题非空 -> 写回 ViewModel -> 导航至 QuadrantFragment。

### 按钮样式规范

| 按钮角色 | style | textAppearance |
|---------|-------|----------------|
| 确认 | `Widget.JustNow.Dialog.Button` | 18sp，主色 |
| 取消/添加 | `Widget.JustNow.Dialog.Button.Secondary` | 18sp，次色 |
| Sheet 内确认 | `Widget.JustNow.Sheet.Button` | 同上 + `focusable=false` |
| Sheet 内取消/添加 | `Widget.JustNow.Sheet.Button.Secondary` | 同上 + `focusable=false` |

### 文件结构

详见 task-input.md 共用文件结构。

# 研究发现、技术决策

> 详见：[findings.md](../findings.md) — 编辑页样式修复与录入/编辑拆分（2026-05-20）

### 专注时长布局方案
- 曾尝试 ChipGroup Filter 单选用 flex-wrap 自动折行，外观与 RadioButton 差异大难以接受。
- 最终采用两行固定 RadioButton：行1 3列、行2 2列+占位 View 等宽对齐。

### 清单编辑器防删问题
- ReplacementSpan 方案：替换首字符导致输入首字丢失 -> 废弃。
- 最终方案：首行 check box 不可删（三种缺失分别修复）；非首行 check box 后无空格->删整行+换行；空行清理。

### 图标选择
- 勾选清单：`check_circle`（圆圈+勾）
- APP 跳转：`rocket_launch`（火箭）-> 后改 Material `apps` 九宫格
- 尺寸：viewport 36dp

### 录入/编辑页面拆分原因
- 原 `TaskInputFragment` 复用同一布局双屏切换，两场景需求冲突（编辑需紧凑/录入需步进宽松）。
- 拆分为 `TaskInputFragment`（纯搜索）+ `TaskEditFragment`（纯编辑），共用 Activity 级 ViewModel。

### 已有标签两行间距过大根因（2026-06-07）
- 现象：`fragment_task_edit.xml` 中 `cg_existing_tags` 固定高度 72dp 承载两行 Chip，`chipSpacingVertical` 改为 0dp 仍无效。
- 根因：Material `Chip` 默认 `ensureMinTouchTargetSize=true`，强制触摸区域 48dp，给 Chip 视觉边界外撑出隐形 padding，行间距由触摸区决定而非 `chipSpacingVertical`。
- 决策：`TagChipHelper.createSelectableChip` 新增 `compact` 重载，仅任务编辑页传 `true` 关闭最小触摸区；其他页面（主页筛选、未使用标签、选标签对话框）保持默认，保留触摸命中率。
- 设计文档：[docs/2026-06-07-task-edit-chip-compact-design.md](../docs/2026-06-07-task-edit-chip-compact-design.md)
