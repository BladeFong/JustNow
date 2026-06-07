# 任务编辑页已有标签 Chip 紧凑模式

## 背景

任务编辑页（`fragment_task_edit.xml`）的"已有标签"ChipGroup 固定高度 72dp，目标承载两行 Chip。实际观察两行 Chip 之间的垂直间距明显偏大；将 `chipSpacingVertical` 改为 `0dp` 后，两行 Chip 间距没有明显变化。

## 根因

Material `Chip` 默认 `ensureMinTouchTargetSize = true`，强制 Chip 触摸区域为 48dp。这会在 Chip 视觉边界外撑出隐形 padding，导致两行 Chip 的视觉间距由触摸区 padding 决定，而非由 `chipSpacingVertical` 决定。所以将 `chipSpacingVertical` 置 0 没有视觉效果。

## 设计

仅任务编辑页需要紧凑视觉，其他使用 `TagChipHelper` 的页面（主页筛选覆盖层、未使用标签管理页、选标签对话框）保留默认触摸区。

### 接口变更：`TagChipHelper`

新增公共重载，支持紧凑参数；原有方法委托到新重载，保持向后兼容。

```java
// 已存在 - 保留，默认非紧凑
public static Chip createSelectableChip(Context context, TagEntity tag) {
    return createSelectableChip(context, tag, false);
}

// 新增 - 可指定紧凑
public static Chip createSelectableChip(Context context, TagEntity tag, boolean compact) {
    Chip chip = new Chip(context);
    // ... 原有视觉样式设置
    if (compact) {
        chip.setEnsureMinTouchTargetSize(false);
    }
    return chip;
}
```

符合 CLAUDE.md "公共重载 + 统一委托" 规范。两个重载共用核心逻辑，差异仅在最后一行可选调用，不必额外抽 private 方法。

### 调用点变更

| 调用点 | 紧凑参数 |
|--------|----------|
| `TaskEditFragment.setupTagChips()` | `true` |
| `MainFragment`（主页筛选覆盖层）| 不传（默认 false）|
| `UnusedTagFragment` | 不传 |
| `dialog_select_tags`（如有代码侧引用）| 不传 |

## 影响范围

- 任务编辑页两行 Chip 视觉间距由 `chipSpacingVertical` 决定，预期间距贴近 0。
- 任务编辑页 Chip 触摸区缩到视觉边界，命中率下降；考虑 Chip 字号为 Body（18sp），实际可点击面积仍可接受。
- 其他页面不受影响。

## 验证

- 任务编辑页：构造两行 Chip 场景，确认两行视觉间距消除。
- 其他页面：确认 Chip 视觉与触摸区无变化。
