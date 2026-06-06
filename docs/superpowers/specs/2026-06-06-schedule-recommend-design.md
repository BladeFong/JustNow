# 安排任务推荐化重构设计

## 定位变更

**旧版**：安排任务"占用"时间槽，截断剩余时间，阻断其他任务开始，到点需处理执行中任务冲突。边界情况多，逻辑复杂。

**新版**：安排任务仅做"到点优先推荐"，不占用时间槽，不截断剩余时间，不阻断其他任务。到点通知，30 分钟优先窗口，用户自主决定。

## 核心变更

### 1. 移除时间截断逻辑

- `TimeRemainingCalculator.applyScheduleTruncation()` → 移除
- `PeriodStatus.effectiveRemaining` / `effectiveEndMinute` → 移除
- `getRemainingText()` → 回归使用 `remainingMinutes`
- 底部栏"X分钟后有安排任务" → 移除
- `s_schedule_remaining_format` 字符串 → 移除

### 2. 移除时间线安排任务块

- `TimelineBuilder` 中安排任务占位逻辑 → 移除
- 时间线不再显示安排任务块（纯视觉指示也移除）

### 3. 移除槽位占位判断

- 槽位表粒度改为 30 分钟（整点/半点）
- 每行 4 格
- 移除槽位冲突/占位判断逻辑

### 4. 移除自动完成执行中任务

- 安排任务到点时，不再自动完成执行中任务
- `TaskExecutionAutoCompleter` 保持原有逻辑（时段结束自动完成），不扩展到安排任务

### 5. 移除时间检查中的安排感知

- `evaluateTaskStartSync` / `TaskStartGuard` → 移除安排范围检测
- 去掉 15 分钟容差：`effectiveRemaining >= focusMinutes` 才允许开始
- 回归原始逻辑：`remainingMinutes + 15 < focusMinutes` 时拦截

### 6. 推荐引擎优先排序

**新增逻辑**：安排任务到点后 30 分钟内，`DisplayEngine` 将其排到最前面。

- 判断条件：`nowMinute >= scheduledTime && nowMinute < scheduledTime + 30`
- 优先级权重：在现有排序基础上，满足条件的任务 `weight -= 300`（高于优先标签的 -200）
- 30 分钟窗口到期后，回归正常排序

### 7. 通知选项重构

**三个选项**：

| 选项 | 行为 |
|------|------|
| 忽略 | 立即取消推荐优先级提高 |
| 延迟 30 分钟 | 30 分钟后再通知，优先级提高顺延 30 分钟（仅限一次） |
| 开始 | 任务实际开始后，立即取消推荐优先级提高 |

**延迟机制**：
- 记录延迟时间戳到 `TaskScheduleEntity`（新增字段 `postponedUntilMs`）
- 优先窗口从 `scheduledTime` 改为 `postponedUntilMs`
- 延迟标记仅限一次：已有 `postponedUntilMs` 时不显示延迟选项

### 8. 跨时段处理

- 时段结束时，取消该时段内安排任务的推荐优先级提高
- 不影响其他时段的安排任务
- 实现：`recomputeSync` 中检查当前时段，清除已过时段的 `postponedUntilMs`

## 涉及文件

| 文件 | 变更 |
|------|------|
| `TimeRemainingCalculator.java` | 移除 `applyScheduleTruncation`、`effectiveRemaining`/`effectiveEndMinute`、`getRemainingText` 回归 |
| `TimelineView.java` | 移除 `mPeriodStatus`、液体色块回归原始逻辑 |
| `TimelineBuilder.java` | 移除安排任务占位 |
| `DisplayEngine.java` | 新增安排任务 30 分钟优先排序 |
| `MainViewModel.java` | 移除安排截断调用、移除安排范围检测、去 15 分钟容差 |
| `TaskStartGuard.java` | 移除安排范围检测、去 15 分钟容差 |
| `WidgetUpdateHelper.java` | 移除安排截断调用 |
| `MainFragment.java` | 移除底部栏安排提示 |
| `TaskScheduleEntity.java` | 新增 `postponedUntilMs` 字段 |
| `AlarmReceiver.java` | 通知选项行为变更 |
| `ReminderNotifier.java` | 通知选项变更 |
| `TaskScheduleFragment.java` | 槽位表 30 分钟粒度 + 每行 4 格 |
| `strings.xml`（四语言） | 移除 `s_schedule_remaining_format` |
