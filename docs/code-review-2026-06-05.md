# Code Review Report — 2026-06-05

- 审查范围：今日全部代码提交（3f7b4ef..7fa547b）
- 审查时间：2026-06-05
- 审查人：code-auditor

## 审查范围

今日提交涵盖以下功能：
1. 安排调整&忽略交互（feat：新增 `TaskScheduleSkipEntity`、通知忽略按钮、闹钟跳过调度）
2. 安排保存链路 upsert 修复（`TaskScheduleRepository.insert()` 改为 upsert 模式）
3. 连续安排任务通知回归 bug 修复（`TaskScheduleFragment` 保存流程重构）

涉及文件：数据库迁移（v2->v3）、新增跳过记录表、`AlarmReceiver`、`ReminderNotifier`、`ReminderScheduler`、`TaskScheduleRepository`、`TaskScheduleFragment`、`TaskScheduleViewModel`、`TimelineBuilder`、`TimelineView`、`DateUtils` 等。

## 结果概要

| 级别 | 数量 |
|------|:--:|
| blocking | 1 |
| important | 1 |
| nit | 1 |
| praise | 3 |
| **合计** | **6** |

---

## 问题详情

### [x] #1 [blocking] `TaskScheduleRepository.update()` 无条件设置 `enabled = true`，破坏单次安排的忽略逻辑

**文件**：`app/src/main/java/com/nearby/justnow/data/repository/TaskScheduleRepository.java`，第 85-86 行

`update()` 方法强制 `schedule.enabled = true` 并清空 `disableReason`。`AlarmReceiver.handleIgnore()` 对单次安排先设 `schedule.enabled = false` 再调 `scheduleRepo.update(schedule, null)`，但 update 会立即将其重新启用。

```java
// TaskScheduleRepository.update() 第 85-86 行
schedule.enabled = true;
schedule.disableReason = null;
```

```java
// AlarmReceiver.handleIgnore() 第 207-210 行
if (schedule.scheduleType == TaskScheduleEntity.TYPE_ONCE) {
    schedule.enabled = false;
    scheduleRepo.update(schedule, null);  // update 会覆盖 enabled=false
    return;
}
```

**结果**：单次安排点"忽略"后通知被取消（`ReminderNotifier.cancel` 生效），但数据库中的 schedule 记录仍然是 `enabled=true`，下次 `refreshToday()` 会重新注册闹钟。

**建议**：`handleIgnore` 应使用 `scheduleRepo.disableScheduleSync(scheduleId, null)` 代替 `update()`；或 `update()` 改为不强制覆盖 enabled 状态。

**审核结果**：已修复。`handleIgnore` 改用 `disableScheduleSync`，不再经过 `update()` 的 enabled 强制覆盖路径。

---

### [x] #2 [important] 跳过记录无清理机制，长期累积

**文件**：`TaskScheduleSkipDao.java`

`task_schedule_skips` 表只有 insert 和按条件 delete，无定期清理逻辑。对于每天重复的安排，每天忽略会写入一条记录（32 字节），一年约 11.5KB。数据量可控，但随时间无限增长。

**建议**：在 `refreshToday()` 或 `scheduleNextAfterSkip()` 中清理 `dateMs` 早于 90 天的记录。

**审核结果**：已修复。跳过表从每次忽略一行重构为每个 schedule 一行（`lastSkippedDateMs` + `skipCount`），数据量从 O(忽略次数) 降到 O(安排数)，不再需要清理机制。数据库迁移 v3->v4。

---

### [x] #3 [nit] `TaskScheduleSkipEntity` 缺少 `(schedule_id, date_ms)` 联合唯一约束

**文件**：`TaskScheduleSkipEntity.java`

当前 `schedule_id` 和 `date_ms` 各有独立索引，但无联合唯一约束。快速连续点击"忽略"按钮可能写入重复的跳过记录。虽然功能上不影响正确性（`Set<Long>` 去重），但浪费存储。

**建议**：增加 `@Index(value = {"schedule_id", "date_ms"}, unique = true)` 并对应迁移。

**审核结果**：已修复。跳过表重构为单行模式后 `schedule_id` 为 PrimaryKey，天然唯一约束，不存在重复记录问题。

---

## 值得肯定

### #4 [praise] Repository upsert 模式设计合理

`TaskScheduleRepository.insert()` 使用 `synchronized(mSaveLock)` + 先查后写的 upsert 模式，正确处理了同任务已有安排的场景。`deleteDisabledByTaskId` 清理残留行配合 `task_id UNIQUE` 约束，避免了冲突。

### #5 [praise] `computeNextMatchExcludingSkips` 的 365 天上限保护

`ReminderScheduler.computeNextMatchExcludingSkips()` 设置了 365 次迭代上限，防止极端情况下死循环。跳过日期用 `Set<Long>` 查询，O(1) 查找效率好。

### #6 [praise] 通知操作按钮的 requestCode 隔离正确

`ReminderNotifier` 使用 `requestCode(schedule.id) + offset`（+1=开始, +2/+3=延迟, +4=忽略）区分不同操作的 PendingIntent，避免了同 schedule 的不同按钮互相覆盖。

---

## 审核记录（2026-06-06）

审核人：code-auditor

| 编号 | 级别 | 审核结果 | 说明 |
|------|------|----------|------|
| #1 | blocking | 已修复 | `handleIgnore` 改用 `disableScheduleSync`，不再经过 `update()` 的 enabled 强制覆盖路径 |
| #2 | important | 已修复 | 跳过表重构为单行模式（lastSkippedDateMs + skipCount），数据量 O(安排数)，无需清理机制 |
| #3 | nit | 已修复 | 单行模式下 `schedule_id` 为 PrimaryKey，天然唯一约束 |

误报：无。3 个发现均确认为真实问题，全部已修复。

额外修复项（审核期间发现并修复）：
- 对话框分流：右侧栏与时间线已安排任务弹框分离
- `configureScheduleButton` 的 `matchesToday` + `isScheduleActionable` 统一
- 时间线执行中/已完成任务点击路由修正
- TYPE_ONCE 超时 disable（deadline = scheduledTime + focusMinutes，TIME_TICK + onResume 触发）

## 未处理项汇总

无。全部 3 项均已修复。
