# 每天未处理任务提醒通知

> 对应 task_plan.md 未处理任务提醒
> 设计文档：[../docs/superpowers/specs/2026-07-25-daily-unprocessed-check-design.md](../docs/superpowers/specs/2026-07-25-daily-unprocessed-check-design.md)
> 实现计划：[../docs/superpowers/plans/2026-07-25-daily-unprocessed-check.md](../docs/superpowers/plans/2026-07-25-daily-unprocessed-check.md)

# 阶段规划、决策记录

## 定位和功能描述

用户当天还未开始过任何任务时，在每天最后一个时段结束前后的两个时机点，通过闹钟+通知提醒查看任务。

## 整体规划和决策

### 需求（brainstorming 确认）

- **时机1**：最后一个时段结束前 30 分钟，可展示任务不为空 → 通知
- **时机2**：最后一个时段结束时，可展示琐碎任务不为空 → 通知
- 两个时机独立判定，各自一天最多触发一次
- "当天已处理"：`task_executions.date == today AND startMs > 0` 有记录
- 过滤排除标签筛选（用户交互），其余与主界面/Widget 一致

### 通知文案

- 时机1：*今天还没处理任务，抽空看看？*
- 时机2：*还有些琐碎小事，趁今天处理掉？*

### 技术决策

- 复用 AlarmManager `RTC_WAKEUP` + `setExactAndAllowWhileIdle`，与现有提醒机制一致
- 复用 `task_reminder` 通知渠道，新增通知 ID 3000
- 从 `TaskFilterHelper.computeFilteredTasks` 提取核心过滤为静态方法 `filterDisplayableTasks`，原方法调用之（不复制）
- 注册幂等：`FLAG_NO_CREATE` 探测已有则跳过
- 不主动取消闹钟（一天一次，到点自然判定跳过）
- 首次注册：TaskRepository insertSync/updateSync 后触发
- 每日续期：凌晨 3 点 `refreshToday()` 末尾触发

### 闹钟调度

新增 `ACTION_DAILY_UNFINISHED_CHECK` action，extra `check_phase` 区分时机 1/2。
`ReminderScheduler.scheduleUnprocessedCheckIfNeeded()`：
1. `FLAG_NO_CREATE` 幂等检查 → 已有则跳过
2. 获取当前时段组，取最后时段 `endMinute`
3. 调用 `filterDisplayableTasks` → 空则跳过
4. 注册 phase 1（endMinute-30）和 phase 2（endMinute）

### 闹钟触发

`AlarmReceiver.handleUnfinishedCheck()`：
1. 检查当天是否已开始任务 → 是则跳过
2. 跑过滤管线
3. 时机1：可展示任务不为空 → 通知
4. 时机2：可展示琐碎任务不为空 → 通知

### 涉及文件

| 改动 | 文件 |
|------|------|
| 提取静态过滤方法 + 重构原方法 | `ui/base/TaskFilterHelper.java` |
| 新增 power 检查调度 | `scheduler/ReminderScheduler.java` |
| 新增 action 处理 | `broadcast/AlarmReceiver.java` |
| 新增通知发送 | `broadcast/ReminderNotifier.java` |
| 保存任务后触发 | `data/repository/TaskRepository.java` |

### 边界

- 当天无任务：不注册闹钟
- 跨日后凌晨 3 点：旧闹钟过期，重新判断
- 时段结束前 30 分钟已过：triggerMs < now，不注册过期闹钟
- 闹钟触发时已处理：判定跳过，不发通知
- 不主动取消闹钟

# 研究发现、技术决策

### 过滤复用：抽取而非复制

原 `computeFilteredTasks` 76 行中包含过滤管线（自动完成过期、隐藏短完成专注任务、隐藏已完成琐碎任务、完成模式配额）。提取为 `public static filterDisplayableTasks()` 约 50 行，原方法改为调用它 + 补标签过滤 + 存缓存。净增约 4 行。

通知侧直接调用同一静态方法，不需另写筛选逻辑。

### settings.gradle.kts 仓库顺序（伴随发现）

阿里云 `public` 仓库只聚合 mavenCentral + jcenter，不含 Google Maven。需单独 `repository/google` 镜像。已将镜像前置、直连源后置兜底。
