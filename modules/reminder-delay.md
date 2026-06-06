# 提醒延迟模块

> 对应 task_plan.md D020、task-execution.md 安排任务到点延迟

# 阶段规划、决策记录

## 定位和功能描述

已安排专注时长任务到点提醒，执行中时支持延迟；建立统一权限引导流程。

## 整体规划和决策

### 设计确认（brainstorming）
- 全链路 AlarmManager（`setExactAndAllowWhileIdle`），不依赖 WorkManager
- 每日凌晨 3 点全量刷新当天全部闹钟；保存新安排时单独注册闹钟
- 常驻通知（`setOngoing(true)`），动态按钮：无执行中->"开始"；执行中=专注->"+15"/"+30"（满足条件时）；执行中=琐碎->仅"开始"置灰
- 延迟最多 30 分钟（+15/+30 两档），且不超出时段结束+15min 容差；每次提醒只允许延迟一次
- 通知点击打开 `ReminderDetailFragment`

### 全项目审查修复（2026-05-30）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F5/F6

- [x] `ReminderDetailActivity` Adapter 非静态内部类 → 静态，移除隐式 Activity 引用
- [x] `AlarmReceiver` `ACTION_START_TASK`/`ACTION_CHECK_ALARM` 加 `goAsync()` WakeLock
- [x] `canPostpone()` 复用 activeGroup/periods 对象，消除重复构造
- [x] `cancelMinuteBoundary` 补 `FLAG_NO_CREATE`

### 实现阶段（task_plan.md D020 提醒延迟阶段）

### AlarmReceiver goAsync 补全（2026-06-03 审查修复）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #5

- [x] `ACTION_POSTPONE` 和 `ACTION_DAILY_REFRESH` handler 补充 `goAsync()` + `pendingResult.finish()`，防止进程在后台 DB 操作完成前被系统回收
- [x] 现在全部 4 个 action handler 统一使用 `goAsync()` 模式
- [x] 数据层：`TaskSchedulePostponeEntity` + DAO + Repository + DB 迁移 8->9
- [x] 调度层：`ReminderScheduler`（schedule/cancel/postpone/refreshToday）
- [x] 广播层：`AlarmReceiver` + `ReminderNotifier`（动态按钮、常驻通知）
- [x] 占位详情页：`ReminderDetailFragment` + ViewModel + 导航路由
- [x] 业务集成：保存安排注册闹钟、凌晨 3 点全量刷新、完成/归档取消闹钟
- [x] 权限：`SCHEDULE_EXACT_ALARM` 声明 + `POST_NOTIFICATIONS` 声明
- [x] Bug 修复：`AlarmReceiver.getActiveScheduleSync(scheduleId)` -> `taskId` 参数错误
- [x] 崩溃修复：启动时 `SecurityException` -> 权限检查后静默跳过
- [x] 权限引导：`PermissionHelper` 统一工具 + 安排页无权限弹引导
- [x] 单元测试：53 个新用例（数据层 14 + Shadow 闹钟/通知/Receiver 39）
- [x] 编译验证 + 测试验证（130 用例 0 失败）
- [ ] 安排页时间未约束落入时段内（已记录到模块文档，后续收束）
- [ ] 自动插入安排任务到时间线
- [ ] 通知点击详情页交互细节

### 已确认规则

**提醒触发**
- 全链路使用 AlarmManager，不依赖 WorkManager。
- 每日凌晨 3 点全量刷新当天全部闹钟。
- 保存新安排时，单独注册该安排的闹钟，不触发全量刷新。

**通知**
- 到点后 AlarmReceiver 发常驻通知（`setOngoing(true)`），用户不可滑动清除。
- 通知按钮动态生成：

| 当前状态 | 通知按钮 |
|----------|----------|
| 无执行中任务 | "忽略" + "开始" |
| 执行中 = 专注类 | "忽略" + "开始" + "+15"（满足条件）+ "+30"（满足条件） |
| 执行中 = 琐碎任务 | "忽略" + "开始" |

- "开始"始终可用：有执行中任务时自动完成它，再开始到点任务。

**延迟**
- 延迟最多 30 分钟（+15 / +30 两档），且 `now + delayMinutes <= 当前时段结束 + 15min 容差`。
- 每次提醒只允许延迟一次（按天门控）。
- 延迟不修改长期安排模板。

**闹钟生命周期**
| 事件 | 闹钟处理 |
|------|----------|
| 保存新安排 | 立即注册该安排闹钟 |
| 凌晨 3 点刷新 | 取消全部旧闹钟 + 注册今天全部有效安排 |
| 仅本次安排完成 | 次日不再注册 |
| 长期安排被停止 | 次日不再注册 |
| 任务归档/删除 | 级联清理安排（disableForTaskSync + cancel 单 schedule，UNIQUE 约束下等价全清） |
| 延迟 | 取消当前 + 注册延迟闹钟 |
| 忽略 | 单次→禁用安排；重复→upsert 跳过记录 + 重调度下次 |
| 超时（过期） | TIME_TICK + onResume 检测，日期已过或所属时段已结束时单次→禁用 |
| 时段结束 / 任务已开始 | 取消对应闹钟 + 清除通知 |

**2026-06-01 重构**：`cancelForTask` 确认 task_schedules UNIQUE 约束后功能等价单条 cancel → 删除方法。`ReminderNotifier.cancel` 保持两参。详见 [../docs/code-review-20260531.md](../docs/code-review-20260531.md)。

### 数据模型

**`task_schedule_postpones`**
- `schedule_id`：关联 `task_schedules.id`
- `task_id`：被延迟的任务
- `blocked_by_task_id`：阻塞它的执行中任务
- `postpone_minutes`：延迟时长
- `date_ms`：延迟发生日期，用于当天延迟一次门控

**`task_schedule_skips`**（v3→v4 重构为单行模式）
- `schedule_id`：PK，关联 `task_schedules.id`
- `last_skipped_date_ms`：最后跳过日期（todayStartMs）
- `skip_count`：累计跳过次数
- `updated_at`：更新时间
- 每个 schedule 一行，upsert 更新；单次安排忽略时直接禁用不写此表

### 新增组件

```
broadcast/
├── AlarmReceiver.java          # 接收闹钟，调 ReminderNotifier
└── ReminderNotifier.java       # 构建通知、动态按钮

scheduler/
├── ReminderScheduler.java      # 注册/取消单个闹钟 + postpone

data/entity/
└── TaskSchedulePostponeEntity.java

data/dao/
├── TaskSchedulePostponeDao.java
└── TaskScheduleSkipDao.java

data/entity/
└── TaskScheduleSkipEntity.java

data/repository/
└── TaskSchedulePostponeRepository.java
```

# 研究发现、技术决策

### 安排忽略与超时统一（2026-06-05）

**忽略按钮**：通知最左新增"忽略"操作。单次安排→直接禁用；重复安排→写入 `task_schedule_skips` 跳过记录（upsert 更新 lastSkippedDateMs + skipCount），`scheduleNextAfterSkip()` 从 `lastSkippedDateMs + 1天` 开始计算下次触发。

**超时统一**：凌晨 3 点 `refreshToday()` 中过期处理去掉 `REASON_EXPIRED` 特殊标记，与忽略走同一逻辑——TYPE_ONCE 禁用，无专属原因。`REASON_EXPIRED` 常量已移除。

**数据库迁移**：v2→v3 新增 `task_schedule_skips` 表；v3→v4 重构为单行模式（schedule_id PK, last_skipped_date_ms, skip_count, updated_at）。

### 审查修复：安排推迟与 TYPE_ONCE 过期边界（2026-06-06）

**过期口径调整**：推荐化重构后，安排任务不再占用时段、也不再代表一段必须完成的时间块；因此 `TYPE_ONCE` 不能继续按 `scheduledTime + focusMinutes` 的预计完成时间禁用。当前口径改为：单次日期早于今天则禁用；日期是今天时，只有能解析到所属时段且当前分钟已到达该时段结束，才禁用。

**时段解析**：`TaskScheduleRepository` 通过 `TaskScheduleDao.OnceScheduleExpiryCandidate` 获取单次安排候选，优先使用安排关联的 `linkedPeriodGroupType`；若没有，则用注入的 `PeriodGroupRuleResolver` 按单次安排日期还原当天生效的时段组，再查找 `scheduledTime` 所属时段。

**推迟语义**：通知推迟只写 `task_schedule_postpones` 门控并重新设置本次闹钟，不再写 `postponedUntilMs`。推荐优先始终只看原始 `scheduledTime` 后 30 分钟，推迟不会延长或屏蔽推荐窗口。

**延迟边界**：`canDelay()` 改为要求 `scheduledTime + 30 < period.endMinute`。若延迟闹钟刚好等于时段结束点，TIME_TICK / onResume / Widget 刷新可能先把单次安排按时段结束禁用，导致闹钟到点后 `handleAlarm()` 读到 disabled 直接返回；严格小于时段结束可以避开该残留竞态。

### 忽略交互修复+对话框分流+TYPE_ONCE 超时（2026-06-06）

**审查修复**：`update()` 无条件设 `enabled=true` 回归，TYPE_ONCE 忽略改用 `disableScheduleSync`；`configureScheduleButton` 恢复 `matchesToday` 判断；时间线执行中/已完成点击路由修正。

**对话框分流**：右侧栏 `showTaskDetailDialog`（开始/安排/取消）与时间线 `handleTimelineScheduledTaskClick`（开始/忽略/取消）分离，通过 `mTimelineScheduledTaskClickEvent` 事件驱动。`isScheduleActionable` 统一判断 `enabled + matchesToday`。

**TYPE_ONCE 超时（历史口径，已被后续审查修复替代）**：当时 `disableExpiredOnceSchedules` 通过 TIME_TICK + onResume 触发，按任务预计完成时间清理。后续推荐化语义确认后，清理口径已调整为日期已过或所属时段已结束。

### 安排通知主键链路修复落地（2026-06-05）

实现后复测确认：安排保存后到点通知正常出现。

关键收口：保存链路在回调和调度前保证 `schedule.id` 是真实 DB 主键；`ReminderScheduler` 继续使用 `schedule.id` 作为广播 extra 和 `PendingIntent` requestCode 的来源。保存完成回调不再被闹钟调度、缓存刷新等副作用阻挡，通知注册和页面返回互不阻塞。

### 新建安排主键回填与通知触发（2026-06-05）

现象：设置当天至少 5 分钟后的安排，到点没有通知。

根因：新建安排后 `TaskScheduleRepository.insert()` 未把 Room 返回的主键写回 `schedule.id`。随后 `ReminderScheduler` 用 `schedule.id` 写广播 extra 和生成 `PendingIntent` requestCode；若 `schedule.id` 仍为 0，`AlarmReceiver` 到点按 `scheduleId=0` 查不到安排，不会发送通知。

决策：安排保存仓库层必须在回调前保证 `schedule.id` 是真实 DB 主键。调度层继续使用现有 `schedule.id` 传递链路，不新增替代键。

### 精确闹钟权限崩溃（2026-05-20）

### 全项目审查修复（2026-05-30）

- `AlarmReceiver` goAsync() WakeLock：ACTION_START_TASK / ACTION_CHECK_ALARM 统一使用 goAsync() + pendingResult.finish()
- `canPostpone()` 消除 PeriodGroupRuleResolver 重复查库
- `WidgetUpdateHelper.cancelMinuteBoundary` 加 FLAG_NO_CREATE
- `ReminderDetailActivity` Adapter 改静态内部类

### AlarmReceiver goAsync 补全（2026-06-03）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- `ACTION_POSTPONE` 和 `ACTION_DAILY_REFRESH` 缺少 `goAsync()`，BroadcastReceiver 进程可能在后台 DB 操作完成前被系统 kill。已统一补全。

**现象**：D020 提醒功能上线后，App 启动即闪退。日志：`SecurityException: Caller needs SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM`。崩溃点：`ReminderScheduler.scheduleDailyRefresh()` -> `AlarmManager.setExactAndAllowWhileIdle()`。

**根因**：Android 12+（API 31+）`SCHEDULE_EXACT_ALARM` 是运行时受限权限。Android 14+ 默认不给精确定时闹钟权限。AndroidManifest 声明权限不等于已授权，必须运行时调用 `AlarmManager.canScheduleExactAlarms()` 检测。

**方案**：
- 新增 `util/PermissionHelper.java` 统一权限工具类
- 启动时和保存安排时：无权限静默跳过闹钟注册，不崩溃
- 用户保存安排时：主动检测无权限 -> 对话框引导跳转 `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`
- 对话框模式预留通知权限等后续权限，同流程复用
