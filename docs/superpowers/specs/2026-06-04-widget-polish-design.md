# Widget 打磨设计

## 1. 顶部栏文本点击跳转主界面

`tv_widget_status` 没有 PendingIntent，点击无反应。

**改动**：`WidgetUpdateHelper.updateWidget()` 中给 `tv_widget_status` 设 PendingIntent，跳 `MainActivity`。

## 2. Widget 圆角 + 半透背景

小米桌面不给 Widget 加圆角裁剪。当前根布局 `android:background="@color/background_surface"` 是纯色。

**改动**：
- 新建 `drawable/bg_widget_root.xml`，圆角 8dp，填充 `#F2FAFAFA`
- `widget_justnow.xml` 根布局 `android:background` 改为引用该 drawable

## 3. 字体 + 行高定档

小米桌面格子高度低于模拟器，同一字号在小容器内显大，默认容纳不下两行任务。

**运作方式**：`updateWidget()` 中取 `widgetHeightDp` 判定档位（阈值 180dp），`compact` 布尔贯穿 `buildTaskRow()` / `renderWidgetStatus()` / `calculateMaxItems()`。字体缩放 `fontScale > 1.0` 也触发紧凑档作为兜底。

**标准档**：零干预，完全走 XML `textAppearance` 和 `wrap_content`，与改动前一致。

**紧凑档**：全部通过 dimen 资源控制，`RemoteViews` 运行时覆盖字号、行高、内边距。

**配套 dimen**：

| dimen | 用途 |
|-------|------|
| `widget_task_row_height` | 标准行高，替代硬编码 `TASK_ROW_HEIGHT_DP` |
| `widget_compact_content_size` | 紧凑标题字号 |
| `widget_compact_tag_size` | 紧凑标签字号 |
| `widget_compact_focus_size` | 紧凑时长字号 |
| `widget_compact_status_size` | 紧凑顶部状态字号 |
| `widget_compact_row_height` | 紧凑行高 |
| `widget_compact_padding_vertical` | 紧凑行内上下内边距 |
| `widget_compact_action_bar_height` | 紧凑顶部栏高度 |
| `widget_compact_action_bar_margin_bottom` | 紧凑顶部栏下边距 |

**其他**：
- `calculateMaxItems()`：紧凑用紧凑行高和紧凑顶栏高度计算可用行数
- `computeItems()`：`DisplayEngine` 返回结果超过 `maxItems` 时 `subList` 截断，兜底 `QuadrantRatioFilter` 的 `ceil` 溢出 bug
- 删 `WidgetUpdateHelper.TASK_ROW_HEIGHT_DP` 常量
