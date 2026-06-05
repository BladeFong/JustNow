# 安排任务感知的剩余时间设计

## 背景

当前"剩余时间"的计算逻辑只看时段结束时间（`endMinute - nowMinute`），未考虑时段内已安排的任务块。导致：

1. 时间线液体色块画到时段结束，而非最近安排任务开始位置
2. 底部栏显示的剩余时间未扣除安排占用
3. 智能展示引擎的 `fitsTime` 判断使用未截断的剩余时间，可能误判任务可容纳
4. 任务开始守卫同样使用未截断值

## 设计方案

### 1. TaskScheduleRepository 新增缓存

沿用 `TaskRepository` 的 `volatile CopyOnWriteArrayList` 模式。

- 新增 `private volatile CopyOnWriteArrayList<TaskScheduleEntity> mCachedEnabledSchedules`
- `getAllEnabledSchedulesSync()`：首次查 DB 写入缓存，后续返回副本
- 写操作（`insert` / `update` / `disableScheduleSync` / `disableForTaskSync` / `disableExpiredOnceToday` / `disableExpiredOnceSchedules`）后清缓存

### 2. TimeRemainingCalculator 改造

**新增重载：**

```java
static PeriodStatus compute(List<TimePeriodEntity> periods,
                            List<TaskScheduleEntity> todaySchedules,
                            Calendar cal)
```

原 `compute(periods, cal)` 保留不动。

**PeriodStatus 新增字段：**

```java
public int effectiveEndMinute;  // 有效截止分钟（最近安排开始 or 时段结束）
public int effectiveRemaining;  // 有效剩余分钟
```

原 `remainingMinutes` / `endMinute` 不动。`getRemainingText()` 改为基于 `effectiveRemaining`。

**新增 private 方法：**

```java
private static void applyScheduleTruncation(
        PeriodStatus status,
        List<TaskScheduleEntity> todaySchedules,
        int nowMinute)
```

逻辑：
1. 遍历 `todaySchedules`，用 `TaskScheduleMatcher.matchesToday()` 过滤今日命中
2. 找 `scheduledTime > nowMinute` 的最小值 → `nearestStart`
3. 找到 → `effectiveRemaining = nearestStart - nowMinute`，`effectiveEndMinute = nearestStart`
4. 未找到 → `effectiveRemaining = remainingMinutes`，`effectiveEndMinute = endMinute`

不需要 `TaskEntity` / `focusMinutes`，不需要兜底负值。

### 3. 消费方变更

**MainViewModel.recomputeSync()：**
- 获取 `todaySchedules = mScheduleRepo.getAllEnabledSchedulesSync()`
- 传入 `TimeRemainingCalculator.compute(periods, todaySchedules, cal)`

**EngineResult 新增字段：**
- `effectiveEndMinute`，从 `status.effectiveEndMinute` 填充

**MainFragment → TimelineView：**
- `effectiveEndMinute` 传入 TimelineView 设为成员变量
- 液体色块 `liquidBottom` 改用 `effectiveEndMinute` 对应的 Y 坐标
- 若 `effectiveEndMinute <= nowMinute`，不绘制液体色块

**DisplayEngine：**
- `MainViewModel` 传入 `status.effectiveRemaining` 替代 `status.remainingMinutes`

**TaskStartGuard：**
- 调用新重载 `compute(periods, todaySchedules, cal)`
- 使用 `status.effectiveRemaining` 替代 `status.remainingMinutes`

**MainViewModel.evaluateTaskStartSync()：**
- 同 TaskStartGuard，调用新重载 + 使用 `effectiveRemaining`

### 4. 边界情况

| 场景 | effectiveRemaining | effectiveEndMinute |
|------|-------------------|-------------------|
| 无今日安排 | = remainingMinutes | = endMinute |
| 当前在安排范围内（无 > nowMinute 的安排） | = remainingMinutes | = endMinute |
| 当前在安排之前 | = nearestStart - nowMinute | = nearestStart |
| 时段结束前无安排 | = remainingMinutes | = endMinute |

### 5. 涉及文件

| 文件 | 变更 |
|------|------|
| `TaskScheduleRepository.java` | 新增缓存 |
| `TimeRemainingCalculator.java` | 新增重载 + applyScheduleTruncation + effectiveRemaining/effectiveEndMinute |
| `MainViewModel.java` | 传入 todaySchedules + effectiveRemaining 传给 DisplayEngine |
| `MainFragment.java` | 传 effectiveEndMinute 给 TimelineView |
| `TimelineView.java` | 液体色块用 effectiveEndMinute 截断 |
| `TaskStartGuard.java` | 调用新重载 + 使用 effectiveRemaining |
