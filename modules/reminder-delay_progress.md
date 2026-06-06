# reminder-delay 进度日志

### 2026-06-06 — 代码审查修复

> 审查报告：[docs/code-review-2026-06-06.md](docs/code-review-2026-06-06.md)

- **#1 [important]**：提取 `TaskScheduleRepository.skipOrDisable()`，消除 `AlarmReceiver.handleIgnore` 与 `MainViewModel.ignoreSchedule` 重复逻辑
- **#2 [nit]**：`ScheduleWithFocusMinutesFull.enabled` 从 `boolean` 改为 `int`，`toEntity()` 加 `(enabled == 1)` 转换
- **#3 [suggestion]**：确认关闭，v3 中间版本未发布，无用户数据丢失
- **状态**：编译通过

### 2026-06-06 — 忽略交互修复+对话框分流+TYPE_ONCE 超时

> 审查报告：[docs/code-review-2026-06-05.md](docs/code-review-2026-06-05.md)

- **审查**：对 6/5 的 8 个提交做代码审查，发现 3 个问题（1 blocking + 1 important + 1 nit）
- **update() 回归**：`TaskScheduleRepository.update()` 无条件设 `enabled=true`，TYPE_ONCE 忽略改用 `disableScheduleSync`
- **对话框分流**：右侧栏 `showTaskDetailDialog` 与时间线 `handleTimelineScheduledTaskClick` 分离，互不影响；右侧栏保持"开始/安排/取消"，时间线已安排任务弹"开始/忽略/取消"
- **调整安排恢复**：`configureScheduleButton` 恢复 `schedule` 参数，`isScheduleActionable`（`enabled + matchesToday`）判断按钮文字
- **时间线点击修正**：执行中走 `resolveAndHandleTaskClick` 按 hasContent 分流，已完成不可点击
- **TYPE_ONCE 超时**：`disableExpiredOnceSchedules` 通过 TIME_TICK + onResume 触发，deadline = `scheduledTime + focusMinutes`；DAO JOIN tasks 表获取 focusMinutes
- **状态**：编译通过

### 2026-06-06 — 跳过表简化：单行模式

- **结构**：`task_schedule_skips` 从每次忽略一行（id, schedule_id, date_ms, created_at）改为每个 schedule 一行（schedule_id PK, last_skipped_date_ms, skip_count, updated_at）
- **DAO**：`insert` + `getSkippedDates` + `deleteSkip` → `upsert` + `getLastSkippedDateMs` + `getSkip`
- **调度**：`ReminderScheduler` 去掉 `getSkippedDateSet` + `computeNextMatchExcludingSkips`（365 天遍历），`scheduleNextAfterSkip` 直接从 `lastSkippedDateMs + 1天` 开始计算
- **忽略**：`AlarmReceiver.handleIgnore` 和 `MainViewModel.ignoreSchedule` 改为 upsert（get → 更新 dateMs/count → upsert）
- **迁移**：v3→v4，DROP + CREATE 新表
- **状态**：编译通过

### 2026-06-05 — 安排调整&忽略交互实现

> 设计文档：[docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md](docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md)

- **按钮文字**：`configureScheduleButton()` 根据是否有 `enabled=1` 安排显示"安排"或"调整安排"
- **安排页加载 bug**：`restoreExistingSchedule()` 槽位恢复到 `onTypeSelected()` 之后，避免被重置
- **通知忽略**：新增 `task_schedule_skips` 表 + DAO + 迁移；通知最左加"忽略"按钮；单次→禁用，重复→记录跳过+重调度
- **超时统一**：`disableExpiredOnceToday` 去掉 `REASON_EXPIRED`，与忽略走同一逻辑
- **时间线交互**：已安排任务弹窗仅"开始"+"取消"；`TimelineView` 修复 `findRunningItemAt` → `findTimelineItemAt` 支持点击非 running 任务
- **状态**：编译通过，82 相关测试通过。

### 2026-06-05 — 连续安排通知丢失回归修复

- **背景**：原回调只有 `scheduleTaskAlarm` + `refreshCaches`，无返回流程，闹钟正常。后来加上 `onComplete.run()`（finish）并放在闹钟之前，接连保存多个安排时后续闹钟未注册。
- **根因**：`onComplete.run()` 在 `scheduleTaskAlarm()` 之前执行，若 `requireActivity()` 抛异常则后续闹钟注册被跳过；且 finish 早于 AlarmManager 注册存在时序隐患。
- **修复**：`insertSchedule()` / `updateSchedule()` 恢复原顺序：`scheduleTaskAlarm()` → `refreshCaches()` → `onComplete.run()`。确保闹钟在 Activity finish 前已注册到 AlarmManager。
- **状态**：仅代码改动，未编译验证。

### 2026-06-05 — 槽粒缓冲：当日临近槽位禁用

- **改动**：`TaskScheduleFragment` 新增 `SLOT_INTERVAL_MINUTES = 10` 常量，槽位遍历/占用检查中硬编码 `10` 全部替换为常量；`isPast` 判定由 `min <= nowMinute` 改为 `min <= nowMinute + SLOT_INTERVAL_MINUTES`。
- **效果**：当天模式下，当前时间所在槽粒及下一个槽粒均不可选（如 10:05 时 10:00 和 10:10 禁用，最早可选 10:20），避免保存回调延迟导致 `ReminderScheduler.schedule()` 静默丢弃闹钟。
- **状态**：仅代码改动，未编译验证。

### 2026-06-05 — 安排通知触发修复落地

> 设计文档：[docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md](docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过，用户复测通过。新建安排保存后回填真实 `scheduleId`，到点通知已恢复；保存链路卡顿和无响应问题一并消失。

### 2026-06-05 — 安排通知主键回填修复设计

> 设计文档：[docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md](docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：设计完成，未编译。确认新建安排后必须回填 Room 主键，否则闹钟广播使用 `scheduleId=0` 到点查不到安排，无法发送通知。

### 2026-06-01 — 重复业务逻辑全面重构

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/holiday-data.md](modules/holiday-data.md)

**状态**：编译+251 测试通过。BaseTaskViewModel 模板方法、HolidayDataSource 抽象类。

### 2026-05-30 — 全项目代码审查与修复

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译 + 全量测试通过。47 项发现 → 排除 5 误报 → 分 8 批修复 24 项。

后续验证建议：[ ] 节假日数据真机验证、[ ] Widget 标签筛选真机验证、[ ] logcat assertNotMainThread 检查

详见：[modules/task-execution.md](modules/task-execution.md)、[modules/holiday-data.md](modules/holiday-data.md)、[modules/widget.md](modules/widget.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/smart-display.md](modules/smart-display.md)、[modules/time-period.md](modules/time-period.md)、[modules/tag.md](modules/tag.md)、[modules/task-input.md](modules/task-input.md)

### 2026-05-28 — 遗留问题梳理 + 文档同步 + 交互修复

- **状态**：完成（未编译验证，被其他改动卡住）。

**文档同步**：
- [modules/tag.md](modules/tag.md)：Widget 标签筛选交互说明统一
- [modules/search-engine.md](modules/search-engine.md)：标签检索分离→已实现
- [modules/app-icon.md](modules/app-icon.md)：第一版图标进度更新

**交互修复**：
- [modules/reminder-delay.md](modules/reminder-delay.md)：通知"开始"有执行中任务时自动完成再开始，修复静默失败

**更新后遗留**：四象限状态栏颜色、Widget 真机验证、数据统计（M6）、Nager.Date API、APP 图标真机遮罩验证

### 2026-05-23 — 安排任务模块重设计 + 实现

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

- 设计完成（brainstorming 7 反馈点）。实现覆盖 15 文件：数据层重建 + 槽位视图 + 闹钟调度 + 通知链路。
- 延后：多任务碰撞 DialogActivity。

### 2026-05-20 — 精确闹钟权限崩溃修复与统一权限引导

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

PermissionHelper 统一工具。启动时无权限静默跳过。保存安排时无权限弹引导对话框。

### 2026-05-19 — D020 提醒延迟模块实现

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

数据层 + 调度层 + 广播层 + UI 层。AlarmReceiver Bug 修复（scheduleId->taskId）。53 个新增测试用例。130 用例 0 失败。
