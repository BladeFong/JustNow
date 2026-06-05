# time-remaining 进度日志

### 2026-06-06 — 安排任务感知的剩余时间

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md](../docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md)
> 详见：[time-remaining.md](time-remaining.md)

**状态**：编译通过，用户验证通过。

**修复**：
- `getRemainingText()` else 分支误用 `remainingMinutes`（effectiveRemaining < 60 时）
- 安排任务到点后右侧栏仍可开始：新增范围检测 `nowMinute >= startMinute && nowMinute < startMinute + focusMinutes`，在范围内时 `effectiveRemaining = 0`
- `TaskScheduleEntity` 加 `@Ignore @ColumnInfo(name = "focus_minutes")` 缓存，Repository 层查 tasks 表填充
- `disableExpiredOnceSchedules()` 改用 `getAllEnabledSchedulesSync()` + `s.focusMinutes`，移除 `ScheduleWithFocusMinutes` POJO
