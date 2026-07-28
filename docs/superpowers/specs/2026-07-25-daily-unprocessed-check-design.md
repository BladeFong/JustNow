# 每时段结束 + 未处理任务提醒通知 — 设计文档

| 版本 | 日期 | 变更 |
|------|------|------|
| v1 | 2026-07-25 | 初始设计：最后时段两个时机通知 |
| v2 | 2026-07-28 | 每时段结束通知、超时提示、最后时段琐碎判定变更 |

## 需求概述

### 每时段结束通知（新）

每天三个时段结束时发送休息提醒，不设门控：

| 时段 | Key | 结束语 |
|------|-----|--------|
| 早上 | MORNING | 午休了，休息一下吧。 |
| 下午 | AFTERNOON | 快晚上了，休整休整。 |
| 晚上 | EVENING | 一天结束了，好好休息。 |

午休（NOON）和晚餐（DINNER）跳过不发。

若时段切换时 `TaskExecutionAutoCompleter` 自动完成了过期任务，通知追加「xxx」等 N 个任务已自动标记完成。

### 最后时段结束前 30 分钟（保留）

最后一个时段结束前 30 分钟，当天未开始任何任务 + 可展示任务不为空 → "今天还没处理任务，抽空看看？"

### 最后时段叠加琐碎提醒（修改）

晚上时段结束通知时，额外判定：
- 当天未开始任何**琐碎任务**（`focusMinutes == 0`），且有可展示琐碎任务 → 合并：时段结束语 + "还有些琐碎小事，趁今天处理掉？"
- 不满足则只发时段结束语

判定从"所有任务"改为"琐碎任务"。

## 判断"已处理"

- 所有任务：`task_executions.date == today AND startMs > 0` 有记录
- 琐碎任务：同上，但只统计 `task.focusMinutes == 0` 的任务执行记录
- 超时任务：在通知发送前，先跑 `TaskExecutionAutoCompleter.completeExpiredRunningTasksSync`，获取刚自动完成的 taskId 集合

## 过滤复用

复用 `TaskFilterHelper.filterDisplayableTasks` 静态方法，不含标签筛选。

## 闹钟调度

### 注册方法（ReminderScheduler）

```java
public void schedulePeriodEndChecks(List<TimePeriodEntity> sortedPeriods)
```

内部逻辑：
1. `FLAG_NO_CREATE` 幂等检查（phase 1 闹钟已存在 → 跳过）
2. 遍历 sortedPeriods，跳过 NOON / DINNER
3. 为每个时段注册时段结束闹钟（ACTION_PERIOD_END）
4. 最后一个时段（EVENING）同时注册时机1（endMinute - 30 分钟）

所有闹钟共用 `ACTION_PERIOD_END`，extra 用 `period_key`（如 "MORNING"）区分。

数据准备：同样跑 `filterDisplayableTasks`，但不做门控——时段结束通知无门控，只用于后续超时任务信息和琐碎判定。

### 闹钟触发（AlarmReceiver）

收到 `ACTION_PERIOD_END`：
1. 跑 `TaskExecutionAutoCompleter` 获取刚完成的任务集合
2. 拼通知文本：时段结束语 + 超时任务提示（如有）
3. 如果是 EVENING → 额外判定琐碎提醒合并
4. 发通知

收到 `ACTION_DAILY_UNFINISHED_CHECK`（时机1）：
- 逻辑不变：判定当天是否已开始任务 → 过滤 → 发通知

## 通知文案

**时段结束语（按 period_key）：**
- MORNING：午休了，休息一下吧。
- AFTERNOON：快晚上了，休整休整。
- EVENING：一天结束了，好好休息。

**超时提示（如有）：**
- 「xxx」等 N 个任务已自动标记完成

**时机1：**
- 今天还没处理任务，抽空看看？

**晚上琐碎叠加：**
- {时段结束语}还有些琐碎小事，趁今天处理掉？

## 通知发送

复用 `task_reminder` 渠道。通知 ID：时段结束 + period 序号 = 3100 起。时机1 保持 3000。

通过 BleNotificationSDK 发送。

## 注册时机

### 首次注册

TaskRepository insertSync/updateSync 后调用 `schedulePeriodEndCheeps()`。

### 每日续期

凌晨 3 点 `refreshToday()` 末尾调用。

## 文件清单

| 改动 | 文件 |
|------|------|
| schedulePeriodEndChecks 替代原方法 | `scheduler/ReminderScheduler.java` |
| ACTION_PERIOD_END 处理 + 超时任务信息获取 | `broadcast/AlarmReceiver.java` |
| 时段结束通知发送 + 文案 | `broadcast/ReminderNotifier.java` |
| 调用方更新 | `data/repository/TaskRepository.java`（不变） |
| 过滤静态方法 | `ui/base/TaskFilterHelper.java`（不变） |

## 边界情况

- **午休/晚餐**：跳过不注册闹钟
- **时段结束时间已过**：triggerMs < now，不注册
- **无超时任务**：不拼超时提示
- **晚上无琐碎任务**：不发琐碎叠加，只发时段结束语
- **跨日**：凌晨 3 点刷新重新注册
