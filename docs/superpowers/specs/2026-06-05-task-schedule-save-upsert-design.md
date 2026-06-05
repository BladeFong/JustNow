# 任务安排保存链路 upsert 修复设计

日期：2026-06-05

## 背景

用户在任务安排页设置当天稍后的安排，到点没有通知。随后尝试给两个不同任务分别设置安排，第二个任务保存时闪退。

`logs/crash.log` 显示崩溃为：

```text
SQLiteConstraintException: UNIQUE constraint failed: task_schedules.task_id
```

崩溃发生在 `TaskScheduleRepository.insert()`，说明保存链路走了插入路径，但 DB 中已经存在相同 `task_id` 的安排记录。

## 根因

### 通知不触发

`TaskScheduleRepository.insert()` 调用 Room `mDao.insert(schedule)` 后，没有把返回的主键写回 `schedule.id`。

保存成功后的闹钟注册链路继续使用同一个 `schedule` 对象：

`TaskScheduleViewModel.insertSchedule()` → `scheduleTaskAlarm(schedule)` → `ReminderScheduler.schedule()` → `setAlarm()`

`ReminderScheduler` 会把 `schedule.id` 写入广播 extra，并以它生成 `PendingIntent` requestCode。若新建后的 `schedule.id` 仍为 0，到点后 `AlarmReceiver` 用 `scheduleId=0` 查询 `task_schedules`，查不到有效安排，因此不会发送通知。

### 保存闪退

`task_schedules` 通过 `task_id UNIQUE` 约束保证每个任务最多一条安排。当前保存分支依赖 UI 层的 `mExistingSchedule` 判断新建/更新。一旦 UI 状态没有拿到已有安排，或保存动作再次以新建路径进入，仓库层会直接插入，撞唯一约束并导致后台线程未捕获异常，进程崩溃。

仓库层需要承担“一任务一安排”的最终一致性，不应只依赖 Fragment 状态做分流。

## 设计

### 保存语义

保留外部调用入口 `insert()` / `update()`，但把新建路径收敛为安全保存：

1. `insert(schedule, onComplete)` 在同一事务内按 `taskId` 查询已有 enabled 安排。
2. 若已有安排存在：
   - 沿用已有 `id` 和 `createdAt`
   - 写入新字段内容
   - 执行 `update`
   - 回填 `schedule.id`
3. 若没有已有安排：
   - 清理同任务 disabled 残留
   - 执行 `insert`
   - 把 Room 返回主键写回 `schedule.id`
4. 成功后统一 `notifyTaskDataChanged()`，再调用 `onComplete`

`update(schedule, onComplete)` 维持显式更新语义，但同样保证 `updatedAt`、`enabled` 等字段一致。

### UI 和调度

`TaskScheduleViewModel.insertSchedule()` 不再需要猜测新建后主键。仓库回调执行时 `schedule.id` 必须已经是 DB 主键。

闹钟注册继续保持现有流程：

`scheduleTaskAlarm(schedule)` → `ReminderScheduler.computeNextMatch()` → `ReminderScheduler.schedule()`

这次修复只保证传入调度层的 `schedule.id` 正确，不重写调度规则。

### 失败边界

本次不吞掉真实 DB 异常。除 `task_id UNIQUE` 这类可通过保存语义避免的问题外，其他 Room/SQLite 异常仍按现状暴露，方便发现真实数据问题。

本次不改变“每个任务最多一条安排”的业务规则，也不放开多安排并存。

## 测试

新增 `TaskScheduleRepositoryTest`，覆盖：

1. 新建安排后，传入对象的 `schedule.id` 被回填，且可按该 id 查到 DB 记录。
2. 两个不同任务各自保存安排，得到不同 `scheduleId`，不会互相覆盖。
3. 同一任务已有安排时再次走 `insert()`，不会崩溃；原记录被更新，表中仍只有该任务一条 enabled 安排。
4. 旧的 disabled 残留仍会被清理，不影响同任务重新保存。

## 验证

实现后运行：

```bash
~/gradlew-wsl.sh --no-daemon testDebugUnitTest
~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac
```

如需真机复测：

1. 创建两个不同专注任务。
2. 分别设置当天至少 5 分钟后的安排。
3. 保存不闪退。
4. 到点收到通知。
