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
| 无执行中任务 | "开始" |
| 执行中 = 专注类 | "开始" + "+15"（满足条件）+ "+30"（满足条件） |
| 执行中 = 琐碎任务 | "开始" |

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
| 时段结束 / 任务已开始 | 取消对应闹钟 + 清除通知 |

**2026-06-01 重构**：`cancelForTask` 确认 task_schedules UNIQUE 约束后功能等价单条 cancel → 删除方法。`ReminderNotifier.cancel` 保持两参。详见 [../docs/code-review-20260531.md](../docs/code-review-20260531.md)。

### 数据模型

**`task_schedule_postpones`**
- `schedule_id`：关联 `task_schedules.id`
- `task_id`：被延迟的任务
- `blocked_by_task_id`：阻塞它的执行中任务
- `postpone_minutes`：延迟时长
- `date_ms`：延迟发生日期，用于当天延迟一次门控

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
└── TaskSchedulePostponeDao.java

data/repository/
└── TaskSchedulePostponeRepository.java
```

# 研究发现、技术决策

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
