# 代码审查报告 — 2026-06-04

- 审查范围：今日 5 个提交（b0cb464, 488d372, f3d4b1f, 05e0c0f, 9f96185），全项目
- 审查时间：2026-06-04
- 发现问题：8 个

---

## 问题列表

### important

| 编号 | 文件 | 问题 |
|------|------|------|
| #1 | `PeriodGroupRuleResolver.java` | `canMatchInNextThreeMonths` 跨年边界 MMDD 比较错误 |
| #2 | `MainViewModel.java` | `recomputeSync` 与 `computeQuadrantOverviewSync` 大量重复代码 |

### suggestion

| 编号 | 文件 | 问题 |
|------|------|------|
| #3 | `TaskScheduleFragment.java` | `getGroupDisplayName` 字符串 switch，应使用 Map |
| #4 | `bg_widget_root.xml` | 硬编码颜色 `#F2FAFAFA`，应使用 `@color/` 引用 |
| #5 | `dimens.xml` | `widget_compact_padding_vertical = 1dp` 偏小 |

### nit

| 编号 | 文件 | 问题 |
|------|------|------|
| #6 | `PeriodConfigViewModel.java` | `hasSpringFestivalFutureDataSync` 混用 `Calendar` 与 `LocalDate` |
| #7 | 资源文件 | `bg_monthly_cell` / `chip_month_cell_text` 孤儿资源未清理 |
| #8 | `TimelineBuilder.java` | `build()` 中对 `activeTasks` 双重遍历 |

---

## 详情

### [x] #1 — `canMatchInNextThreeMonths` 跨年边界 MMDD 比较错误

**文件**: `app/src/main/java/com/nearby/justnow/data/model/PeriodGroupRuleResolver.java` (新增方法 `canMatchInNextThreeMonths`)

**问题**: 当"未来 3 个月"窗口跨年份时，`windowStart` 与 `windowEnd` 的 MMDD 数值比较会失效。

```java
// 例：当前 11 月 15 日
int windowStart = (today.get(Calendar.MONTH) + 1) * 100 + today.get(Calendar.DAY_OF_MONTH);
// → 1115
Calendar threeMonthsLater = ...;
threeMonthsLater.add(Calendar.MONTH, 3);
int windowEnd = (threeMonthsLater.get(Calendar.MONTH) + 1) * 100 + threeMonthsLater.get(Calendar.DAY_OF_MONTH);
// → 215（次年 2 月 15 日）
```

此时 `windowStart(1115) > windowEnd(215)`，`vacationCanMatch` 的 `windowStart <= groupEnd` 条件对次年 Q1 的假期组始终返回 false。同一文件内的 `isInMonthDayRange` 已正确处理跨年（`rangeStart <= rangeEnd` 分支 + `||` 兜底），`vacationCanMatch` 应复用同一逻辑。

**影响**: 每年 11-12 月安排页中，"未来 3 个月可命中"的假期组过滤会错误排除次年 1-2 月的假期组，导致这些时段组不出现在安排页 RadioGroup 中。

**审核**: 已修复。改 PeriodGroupRuleResolver.java，补 3 个跨年测试用例。编译通过，34 passed。

---

### [x] #2 — `recomputeSync` 与 `computeQuadrantOverviewSync` 大量重复代码

**文件**: `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java`

**问题**: 两个方法约 80% 的代码重复——获取 scheduleProfile、activePeriodGroup、periods、timelinePeriods、status、statusText、priorityTagIds；加载 tasks、autoComplete 过滤、标签过滤、tagMap、executions、timelineItems、executingTasks、degradeMap。差异仅：
- recomputeSync 有今日隐藏过滤 + `TimelineBuilder.hideCompletedChoresForToday`，quadrant 无
- recomputeSync 调用 `mDisplayEngine.compute`，quadrant 调用 `computeByQuadrant`

此前提的 `ComputeContext` 虽已删除（有意让 quadrant 过滤规则独立），但公共部分（时段上下文、自动完成、标签过滤、degradeMap、executingTasks）仍可提取为 private 方法，避免修改过滤逻辑时需同步两处。

**审核**: 误报。已多次优化，剩余相似调用错开、参数不同，无法自然提取。项目规范已加"有合理方法才提取""禁止硬造数据结构"约束。已记录到 `docs/code-review-ignore.md`。

**重审（2026-07-30）**: 问题已进一步优化解决。`recomputeSync` 现已将任务获取、过滤、autoComplete、tagMap、executions 等全部委托给 `TaskFilterHelper.refreshSync()`，方法体从 80+ 行压缩至约 20 行核心逻辑；原有约 80% 重复降至约 30%（仅剩时段上下文头部与 `assembleDisplayItems` 末尾调用形式相近，属合理共性）。`computeQuadrantOverviewSync` 保留独立路径有业务依据（四象限不应用完成模式配额隐藏）。已从 `docs/code-review-ignore.md` 移除。

---

### [x] #3 — `getGroupDisplayName` 字符串 switch

**文件**: `app/src/main/java/com/nearby/justnow/ui/taskschedule/TaskScheduleFragment.java`

```java
private String getGroupDisplayName(String groupType) {
    switch (groupType) {
        case PeriodGroupType.WORKDAY: return getString(R.string.s_period_group_workday);
        case PeriodGroupType.SPRING_FESTIVAL: return getString(R.string.s_period_group_spring_festival);
        case PeriodGroupType.LONG_VACATION: return getString(R.string.s_period_group_long_vacation);
        case PeriodGroupType.SUMMER_VACATION: return getString(R.string.s_period_group_summer_vacation);
        case PeriodGroupType.WINTER_VACATION: return getString(R.string.s_period_group_winter_vacation);
        default: return groupType;
    }
}
```

按项目编码规范"避免用 if/else 链或 switch 对固定值集合做遍历映射，改用数据驱动方式"，应使用 `Map<String, Integer>` 映射 groupType -> stringResId。6 个 case 不会增长，但代码一致性应遵守项目规范。

**审核**: 已修复。改为 static `Map<String, Integer>` 数据驱动。

---

### [x] #4 — `bg_widget_root.xml` 硬编码颜色

**文件**: `app/src/main/res/drawable/bg_widget_root.xml` (新增)

```xml
<solid android:color="#F2FAFAFA" />
```

应使用 `@color/` 资源引用，支持暗色主题适配。当前颜色为硬编码的 ARGB 值，暗色主题下不会自动切换。

**审核**: 已修复。提取到 `colors.xml`，drawable 引用 `@color/widget_root_bg`。

---

### [x] #5 — `widget_compact_padding_vertical = 1dp` 偏小

**文件**: `app/src/main/res/values/dimens.xml`

Widget 紧凑模式下垂直内边距仅 1dp。虽然 Widget RemoteViews 只展示不交互（点击走 PendingIntent 整行），且配合 `minHeight=42dp` 减去 2dp 后仍有约 40dp 内容区，但在低密度屏幕上 1dp 几乎不可见。建议至少 4dp 以维持视觉呼吸感。

**审核**: 过度吹毛求疵。1dp→2dp 已调整，但 Widget 非触控目标，原值不算 bug。已记录到 `docs/code-review-ignore.md`。

---

### [x] #6 — `hasSpringFestivalFutureDataSync` 混用新旧日期 API

**文件**: `app/src/main/java/com/nearby/justnow/ui/period/PeriodConfigViewModel.java`

```java
java.util.Calendar cal = java.util.Calendar.getInstance();
int thisYear = cal.get(java.util.Calendar.YEAR);
java.time.LocalDate today = java.time.LocalDate.now();
```

同一方法内 `Calendar` 和 `java.time.LocalDate` 混用。建议统一使用 `java.time` API（API 26+），项目 minSdk=33 完全支持。减少旧 API 的使用可降低理解成本。

**审核**: 已修复。统一为 `LocalDate.now().getYear()`。

---

### [x] #7 — `bg_monthly_cell` / `chip_month_cell_text` 孤儿资源未清理

MONTHLY 功能已移除（`MonthlyDayPickerDialog.java`、`dialog_monthly_day_picker.xml`、`item_monthly_day_cell.xml` 已删除），验证确认 `bg_monthly_cell` drawable 和 `chip_month_cell_text` color 资源已无任何引用，可安全删除。

**审核**: 部分正确，已修复。`bg_monthly_cell.xml` 已删除；`chip_month_cell_text` 此前已清理。

---

### [x] #8 — `TimelineBuilder.build()` 对 `activeTasks` 双重遍历

**文件**: `app/src/main/java/com/nearby/justnow/ui/main/TimelineBuilder.java`

`build()` 先遍历 `activeTasks` 检测 `hasRunning`（用于缓存签名），后续再遍历构建 running items。可合并为单次遍历：计算缓存签名时同时收集 running items。N 规模很小（几十条任务），实际性能影响可忽略。

**审核**: 误报。第一次遍历是缓存签名检测命中后 `return`，第二次是 miss 后构建，标准缓存模式。已记录到 `docs/code-review-ignore.md`。

---

## 结果概要

- [blocking]: 0
- [important]: 2
- [suggestion]: 3
- [nit]: 3
- 总计: 8

---

## 未处理项汇总

无。全部 8 项已关闭（含 5 项修复、2 项误报、1 项过度吹毛求疵）。
