# 每天未处理任务提醒通知 — 设计文档

## 需求概述

用户当天还未开始过任何任务，在以下时机通知提示：

| 时机 | 触发时间 | 条件 |
|------|---------|------|
| **时机1** | 当天最后一个时段结束前 30 分钟 | 可展示任务不为空 |
| **时机2** | 当天最后一个时段结束时 | 可展示琐碎任务不为空（`focusMinutes == 0`） |

两个时机独立判定，各自一天最多触发一次（每天只有一个最后时段，天然保证）。

## 判断"当天已处理"

查询 `task_executions` 表当天（`date == today`）是否存在 `startMs > 0` 的记录。有则跳过通知。

## 过滤复用

"可展示任务"与主界面/Widget 一致，但排除用户交互相关的标签筛选。

从 `computeFilteredTasks` 中提取核心过滤逻辑为静态方法，原方法调用之：

```
filterDisplayableTasks(app, tasks, allPeriods, sortedPeriods, todayExecutions)
  1. 自动完成过期任务
  2. 隐藏短时间完成的专注任务（ChoreHiddenTodayStore）
  3. 隐藏今日已完成的琐碎任务
  4. 完成模式日/周/月/年配额过滤
  → 返回过滤后的 List<TaskEntity>
```

原 `computeFilteredTasks` 调用该静态方法后，再补标签过滤 + 保存缓存，不重复代码。

通知侧直接调用静态方法。时机1 判空结果，时机2 再筛 `focusMinutes == 0`。

## 闹钟调度

### 新增 action

```java
ACTION_DAILY_UNFINISHED_CHECK = "com.nearby.justnow.ACTION_DAILY_UNFINISHED_CHECK"
```

Extra 用 `int check_phase` 区分时机1/2。

### 注册方法（ReminderScheduler）

```java
public void scheduleUnprocessedCheckIfNeeded(List<TimePeriodEntity> sortedPeriods)
```

内部逻辑：
1. 用 `PendingIntent.FLAG_NO_CREATE` 检查闹钟是否已存在 → 已存在则跳过
2. 跑过滤管线，可展示任务不为空 → 注册时机1和时机2两个闹钟
3. 可展示任务为空 → 不注册

### 闹钟触发处理（AlarmReceiver）

收到 `ACTION_DAILY_UNFINISHED_CHECK`：
1. 检查当天是否已开始过任务 → 是则跳过
2. 对应时机跑过滤管线 → 可展示任务不为空 → 发通知

## 通知发送

复用现有 `task_reminder` 渠道。新增通知 ID `UNFINISHED_NOTIFY_ID = 3000`。点击跳转主界面，无操作按钮。

### 文案

- **时机1**（结束前 30 分钟）：*今天还没处理任务，抽空看看？*
- **时机2**（时段结束后）：*还有些琐碎小事，趁今天处理掉？*

## 注册时机

### 首次注册

`TaskRepository` 保存任务成功后直接调用 `ReminderScheduler.scheduleUnprocessedCheckIfNeeded()`。方法内通过 `FLAG_NO_CREATE` 做幂等，已有闹钟则跳过。

### 每日续期

凌晨 3 点 `ACTION_DAILY_REFRESH` 走到 `refreshToday()`，末尾调用同一个 `scheduleUnprocessedCheckIfNeeded()`。跨天后旧闹钟已过期，`FLAG_NO_CREATE` 返回 null，重新注册新一天的闹钟。

## 文件清单

| 改动 | 文件 |
|------|------|
| 新增过滤静态方法 | `ui/base/TaskFilterHelper.java` |
| 新增调度方法 | `scheduler/ReminderScheduler.java` |
| 新增 action + 处理 | `broadcast/AlarmReceiver.java` |
| 发送通知新增重载 | `broadcast/ReminderNotifier.java` |
| 保存任务后触发 | `data/repository/TaskRepository.java` |

## 边界情况

- **当天无任务**：不注册闹钟
- **跨日后凌晨 3 点**：旧闹钟过期，重新判断，有任务则注册
- **时段结束前 30 分钟已过**：`triggerMs < now`，`setAlarmSafe` 不注册过期闹钟
- **闹钟触发时用户已处理任务**：判定"已处理"跳过，不重复通知
- **不主动取消闹钟**：一天一次，到点自然判定跳过，无价值取消
