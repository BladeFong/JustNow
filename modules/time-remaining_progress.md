# time-remaining 进度日志

### 2026-06-06 — 审查修复：安排推荐过期清理 + cutoff 跨天

- `TYPE_ONCE` 清理口径改为日期已过或所属时段已结束，不再按 `scheduledTime + focusMinutes` 预计完成时间禁用；`JustNowApplication` 为仓库注入 `PeriodGroupRuleResolver` 以还原单次安排的当天时段组。
- 推迟提醒只重新设置闹钟，不写入/延长推荐优先窗口；推荐优先始终只看原始 `scheduledTime` 后 30 分钟，`postponedUntilMs` 不再参与排序。
- 延迟边界收紧为 `delayed < period.endMinute`，避免延迟闹钟正好落在时段结束点后被过期清理竞态吞掉。
- `CutoffTimeStore` 追加保存 `cutoff_date_ms`，读取时若不是今天自动清除，避免昨天的截止时间影响今天。
- **状态**：定向单元测试通过，`compileDebugJavaWithJavac` 通过；文档整理后未再编译。

### 2026-06-06 — 安排任务推荐化重构

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-recommend-design.md](../docs/superpowers/specs/2026-06-06-schedule-recommend-design.md)
> 详见：[time-remaining.md](time-remaining.md)

**状态**：编译通过，测试通过。旧版"占用"时间槽改为"到点优先推荐"。本模块移除 `applyScheduleTruncation`、`effectiveRemaining`/`effectiveEndMinute`、底部栏安排提示、`s_schedule_remaining_format`。

### 2026-06-06 — 安排任务感知的剩余时间

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md](../docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md)
> 详见：[time-remaining.md](time-remaining.md)

**状态**：编译通过，用户验证通过。（注：后续被推荐化重构方案替代）

**修复**：
- `getRemainingText()` else 分支误用 `remainingMinutes`（effectiveRemaining < 60 时）
- 安排任务到点后右侧栏仍可开始：新增范围检测 `nowMinute >= startMinute && nowMinute < startMinute + focusMinutes`，在范围内时 `effectiveRemaining = 0`
- `TaskScheduleEntity` 加 `@Ignore @ColumnInfo(name = "focus_minutes")` 缓存，Repository 层 POJO 转换填充
- `TaskScheduleDao` 新增 `ScheduleWithFocusMinutesFull` POJO + `getEnabledSchedulesWithFocusSync()` JOIN 查询，恢复 `ScheduleWithFocusMinutes` + `getEnabledOnceSchedulesWithFocusSync()`
- `disableExpiredOnceSchedules()` 恢复用 `getEnabledOnceSchedulesWithFocusSync()` POJO
- `findTimelineItemAt` 条件误跳过安排任务，改为 `!item.running && item.actualMinutes > 0`
- 底部栏剩余时间：安排到点时显示原始时段剩余，安排之前显示"X分钟后有安排任务"
- 新增字符串 `s_schedule_remaining_format`（四语言）
