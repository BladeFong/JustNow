# task-edit 进度日志

### 2026-07-30 — 修复编辑任务界面软键盘弹出挤压隐藏任务内容编辑框问题

- 将 fragment_task_edit.xml 内容表单外层切换为 NestedScrollView（fillViewport=true）
- 将任务内容 card_markdown 调整为 layout_height=0dp + layout_weight=1 + minHeight=120dp，既保持平时自动填满屏幕余下空间，又解决输入法弹出（adjustResize）时编辑框被压平隐藏的问题
- Release 编译并安装到设备验证成功

### 2026-07-18 — 代码审查
- 代码审查记录于 `docs/code-review-20260718.md`，涉及图标选择器标签国际化相关问题共 2 项。

### 2026-07-17 — 编辑页面专注时长选项平板单行自适应排版
- 在 `TaskEditFragment.java` 的 `setupFocusMinutes()` 中引入 `is_tablet` 判定，动态设置 `itemsPerRow` 变量（平板为选项全集大小，手机默认为 3）。
- 使得平板设备上，五个专注时长选项按钮在一行内平铺排列，不再进行折行，大幅缩减了平板上不必要的页面垂直高度。

### 2026-07-17 — 编辑任务界面布局微调（选择图标前置与横屏单行标签自适应）
- 修改 `app/src/main/res/values/bools.xml` 和 `app/src/main/res/values/dimens.xml`，新增 `existing_tags_single_line`（默认值为 `false`）和 `existing_tags_height`（默认值为 `88dp`）。
- 新建 `app/src/main/res/values-land/bools.xml` 和 `app/src/main/res/values-land/dimens.xml`，使得在横屏（landscape）模式下，`existing_tags_single_line` 为 `true`，且 `existing_tags_height` 调整为 `44dp`。
- 调整 `app/src/main/res/layout/fragment_task_edit.xml` 布局，将原有置底的图标选择器 `card_icon_selector` 剪切移动至内容输入框 `card_markdown` 下方、标签输入框 `card_tag` 上方。
- 调整 `cg_existing_tags` 控件，使其高度及 `singleLine` 属性使用资源引用，从而支持横屏下单行横向排布。

### 2026-06-07 — 任务编辑页已有标签 Chip 紧凑模式

> 设计文档：[docs/superpowers/specs/2026-06-07-task-edit-chip-compact-design.md](../docs/superpowers/specs/2026-06-07-task-edit-chip-compact-design.md)

**状态**：已修改。`TagChipHelper.createSelectableChip` 新增 `compact` 重载，紧凑模式下关闭 `ensureMinTouchTargetSize`；`TaskEditFragment.setupTagChips` 改为传 `true`。其他调用点保留默认非紧凑，触摸区不变。
