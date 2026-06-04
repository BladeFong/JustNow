# Widget 打磨设计

## 1. 顶部栏文本点击跳转主界面

`tv_widget_status` 没有 PendingIntent，点击无反应。

**改动**：`WidgetUpdateHelper.updateWidget()` 中给 `tv_widget_status` 设 PendingIntent，跳 `MainActivity`（不带 taskId，直接进首页）。

## 2. Widget 圆角 + 半透背景

小米桌面不给 Widget 加圆角裁剪。当前根布局 `android:background="@color/background_surface"` 是纯色。

**改动**：
- 新建 `drawable/bg_widget_root.xml`，圆角 16dp，填充 `#F2FAFAFA`
- `widget_justnow.xml` 根布局 `android:background` 改为引用该 drawable

## 3. 字体定档

小米系统字体缩放导致 RemoteViews 文本变大，Widget 渲染溢出。

**运作方式**：`buildTaskRow()` 中读取 `context.getResources().getConfiguration().fontScale`，按三档通过 `RemoteViews.setTextViewTextSize()` 动态设字号。

**现状**（布局 `textAppearance`）：

| 视图 | 字号 | 来源样式 |
|------|------|----------|
| tv_task_content | 18sp | TextAppearance.JustNow.Body |
| tv_tag | 18sp | TextAppearance.JustNow.Body |
| tv_focus_badge | 16sp | TextAppearance.JustNow.Caption |

**定档后**（`RemoteViews.setTextViewTextSize()` 覆盖）：

| 档位 | fontScale | tv_task_content | tv_tag | tv_focus_badge |
|------|-----------|-----------------|--------|----------------|
| 1 | ≤ 1.0 | 18sp | 18sp | 16sp |
| 2 | 1.0~1.15 | 16sp | 14sp | 14sp |
| 3 | > 1.15 | 14sp | 12sp | 12sp |

> 档位 1 与现状一致，仅档位 2/3 做缩放。

**配套**：
- 新增 `dimen name="task_content_row_height"` = 72dp，替代硬编码 `TASK_ROW_HEIGHT_DP`
- `buildTaskRow()` 通过 `RemoteViews` 给 `ll_task_item` 设固定高度 `task_content_row_height`——仅影响 Widget
- `calculateMaxItems()` 读 `task_content_row_height` dimens
- 删 `WidgetUpdateHelper.TASK_ROW_HEIGHT_DP` 常量

---

无新增字符串、无布局结构变更。
