# 任务执行模块

> 对应 task_plan.md M5

# 阶段规划、决策记录

## 定位和功能描述

任务执行模块负责把"右侧任务选择"和"左侧时间线执行记录"分开：右侧只查看、安排、开始；左侧只处理执行中的完成动作。非时段下右侧任务仍可查看详情，但不能直接开始执行。

## 整体规划和决策

### 全项目审查修复（2026-05-30）+ 遗留跟进（2026-05-31）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F2/F3/F5/F7

- [x] `AlarmReceiver` 主线程 DB → `AppDatabase.execute()` 分发
- [x] 6 处多步写操作 `runInTransaction` 原子化
- [x] `MainViewModel.recompute()` CAS 防重入 → CAS 排队模式
- [x] `TaskStartGuard` null 检查
- [x] `AppDatabase.sDatabaseWriteExecutor` 私有化，API 收口 `execute()` / `runInBackground()`
- [x] 新建 `BaseViewModel`（mApp/mDb/runInBackground/runOnUiThread），8 个 ViewModel 统一继承
- [x] Fragment/Activity 的 10 处直接 executor 调用移到 ViewModel
- [x] `archiveTaskSync` / `replaceAllByTaskIdSync` 补齐 `runInTransaction()`，删除死方法
- [x] DB 版本回退到 1，清掉全部迁移（项目未发布第一版）

### 重复业务逻辑重构（2026-06-01）

> 原报告：[../docs/code-review-20260531.md](../docs/code-review-20260531.md)

- [x] `BaseTaskViewModel` 从 `BaseViewModel` 分离，承载 `completeRunningTaskSync` / `performShortCompletionSync` / `archiveTaskSync` / `checkListStateNeedsConfirm` 4 个 Sync 方法
- [x] 三个模板方法 (`completeTaskFlow` / `shortCompleteFlow` / `archiveTaskFlow`) 统一封装 cancel + disable + onPostComplete hook
- [x] `MainViewModel` / `ReminderDetailViewModel` 改为继承 `BaseTaskViewModel`
- [x] `cancelForTask` 确认 UNIQUE 约束后等价单条 cancel → 删除方法
- [x] `ReminderNotifier.cancel` 保持两参，调用方清理

### Repository 缓存线程安全 + 架构收口（2026-06-03 审查修复）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #1 #3 #5 #8 #12

- [x] `TaskExecutionRepository.mCachedTodayExecutions`：`ArrayList` → `volatile CopyOnWriteArrayList`
- [x] `TaskRepository.mCachedActiveTasks`、`mCachedDegrades`：`ArrayList` → `volatile CopyOnWriteArrayList`
- [x] `AlarmReceiver` `ACTION_POSTPONE`、`ACTION_DAILY_REFRESH` 补充 `goAsync()` + `pendingResult.finish()`，防止进程在 DB 操作完成前被 kill
- [x] `CONFIRM_TYPE_CHECKLIST_STATE`、`SHORT_DURATION_THRESHOLD_MINUTES` 常量从 `MainViewModel` 移至 `BaseTaskViewModel`
- [x] `PreCompleteConfirmCallback` 接口从 `MainViewModel` 移至 `BaseTaskViewModel`
- [x] `ReminderDetailViewModel` 不再反向依赖 `MainViewModel`

### 安排任务模块重设计（task_plan.md 2026-05-23）
- **一个任务只能有一条有效安排**（UNIQUE），已有则进入编辑模式
- **字段统一表达**：`scheduleType`(0/1/2/3) + `scheduleValue`(long) + `scheduleTime`(int)，去掉 groupType/periodNameKey
- **DAO 变更**：insert+update 替代 upsert；新增 getScheduleById/disableSchedule/disableExpiredOnceToday
- **安排界面**：单次/重复共用槽位视图，按需计算 + 内存缓存
- **闹钟调度**：schedule 接受外部 triggerMs；每日 3 点三步骤刷新
- **通知"开始"**：直接 startExecution
- **详情页**："完成本次"不碰安排；"停止安排"只停当前那一条
- **安排入口**：双权限检查（SCHEDULE_EXACT_ALARM + POST_NOTIFICATIONS），不给不进

### 安排任务模块重设计实现（task_plan.md）
- [x] DB 迁移 v10->v11：`TaskScheduleEntity` 字段重构
- [x] `TaskScheduleDao`：insert+update；新增 getScheduleById/disableSchedule/disableExpiredOnceToday
- [x] `TaskScheduleRepository`：新增 insert/update/disableSchedule/disableExpiredOnceToday
- [x] `ReminderScheduler`：schedule 接受外部 triggerMs；computeNextMatch；refreshToday 三步骤
- [x] `ReminderNotifier`："开始"按钮->ACTION_START_TASK 广播
- [x] `AlarmReceiver`：新增 ACTION_START_TASK 处理
- [x] `TaskScheduleFragment` + `TaskScheduleViewModel`：重建
- [x] 小米真机槽位空白修复：`Body`(18sp) → `Caption`(16sp)
- [x] `ReminderDetailViewModel`："完成本次"不碰安排；"停止安排"只 disable 单条
- [x] 字符串：4 语言新增 15 个 key
- [x] compileDebugJavaWithJavac BUILD SUCCESSFUL

### 安排模块排查修复（task_plan.md 2026-05-24）
- **Pack 1（匹配/调度）**：抽 `TaskScheduleMatcher` 作为权威实现（bit0=周日）；`cancelForTask` 遍历全表；`refreshToday` 三步骤修正；`TimelineBuilder` 日期过滤
- **Pack 2（UNIQUE+迁移）**：`sMigration10To11` 改为 DROP+CREATE；`deleteDisabledByTaskId`；insert 前清 disabled
- **Pack 3（权限/校验）**：新增 `TaskStartGuard.evaluate(Context, taskId)`；双权限前置门禁；`MainActivity.handleReminderIntent` 清 action
- **Pack 4（缓存+清理）**：双层缓存；通知 ID 去模；删死代码
- **测试**：`TaskScheduleMatcherTest` 34 用例；全量 218 pass

### 已确认规则

**右侧任务项**
- 点击任务项只打开任务详情弹窗，不直接开始或完成。
- 执行中琐碎任务留在右侧栏，点击弹窗确认完成；完成后当天不再列入右侧栏。

**任务点击分流**
| 任务状态 | 事件 | Fragment 处理 |
|---------|------|---------------|
| 未执行 | `mTaskStartEvent` | showTaskDetailDialog（开始/安排） |
| 执行中 + 有详情 | `mTaskCompleteToDetailEvent` | navigate 到详情页 |
| 执行中 + 仅标题 | `mOnlyTitleTaskCompleteEvent` | 完成弹窗 |

**开始按钮可用条件**
- 当前没有其他执行中任务
- 当前处于有效时段内
- 专注时长任务满足"剩余时间 + 15 分钟容差 >= 专注时长"
- 非时段时开始按钮禁用并显示原因

**任务安排**
- 槽位：10 分钟粒度，每行 6 列 GridLayout，范围选中
- 类型切换控件：minHeight=42dp 防抖动
- 选择器视觉统一：chip 样式按钮（浅蓝/蓝色实底 8dp 圆角）
- 每周 chip 走 TextView + state_selected；每月月历 Dialog（GridLayout 7x5）
- 缓存：双层（schedules + periods + task）

**左侧时间线**
- 专注时长任务开始执行后插入左侧时间线；琐碎任务完全不进入。
- 所有执行中任务过时段后自动完成，不弹询问、不改安排状态。
- 专注任务条：执行中按 focusMinutes、已完成按实际耗时。
- < 15min 完成引导：`ShortCompletionDialog` 三按钮。

**时间线绘制**
- 执行中任务显示为灰色小圆角方块。
- 有执行中任务时隐藏液体块，浮标停止抖动。

### 数据模型

**`tasks`**
- `executing_start_ms` / `executing_end_ms`

**`task_executions`**
- `start_ms` / `end_ms` / `actual_minutes`

**`task_schedules`**
- `scheduleType`（0=TYPE_ONCE / 1=DAILY / 2=WEEKLY / 3=MONTHLY）
- `scheduleValue`：ONCE->日期 ms / daily->0 / weekly->bitmask bit0=周日 / monthly->1-31
- `scheduledTime`（0-1439 分钟）
- `enabled` + `disableReason`

### 文件结构

```
broadcast/
├── AlarmReceiver.java
└── ReminderNotifier.java

scheduler/
├── ReminderScheduler.java
├── TaskScheduleMatcher.java       — 匹配/触发计算权威实现（bit0=周日）
└── TaskStartGuard.java            — 任务开始统一校验（前台+后台共用）

data/entity/
├── TaskExecutionEntity.java
└── TaskScheduleEntity.java

data/dao/
├── TaskExecutionDao.java
└── TaskScheduleDao.java

data/repository/
├── TaskExecutionAutoCompleter.java
├── TaskExecutionRepository.java
├── TaskRepository.java
└── TaskScheduleRepository.java

data/store/
└── ChoreHiddenTodayStore.java

ui/main/
├── MainFragment.java
├── MainViewModel.java
├── ShortCompletionDialog.java
├── TaskAdapter.java
├── TaskStartResult.java
├── TimelineBuilder.java
├── TimelineItem.java
└── TimelineView.java

ui/taskschedule/
├── TaskScheduleFragment.java
├── TaskScheduleViewModel.java
└── MonthlyDayPickerDialog.java
```

# 研究发现、技术决策

### 安排保存与当前进程刷新收口（2026-06-05）

实现后复测确认：两个不同任务分别设置安排不再撞 `task_id UNIQUE`，保存后主界面时间线即时出现安排任务。

关键收口：
- `TaskScheduleRepository.insert()` 按 `taskId` 串行安全保存；已有记录复用 `id/createdAt` 更新，无记录插入并回填 `schedule.id`。
- 主界面观察安排表变化，安排保存后触发当前进程内重算。
- `TimelineBuilder` 缓存签名加入 schedule 数据，避免任务和执行记录未变时继续复用旧时间线。
- 单页 `TaskScheduleActivity` 保存成功后用 `finish()` 关闭页面，避免根 Fragment `popBackStack()` 无法返回。

### 安排保存链路 upsert 兜底（2026-06-05）

现象：两个不同任务分别设置安排时，第二个保存闪退。`logs/crash.log` 显示 `SQLiteConstraintException: UNIQUE constraint failed: task_schedules.task_id`，崩溃点在 `TaskScheduleRepository.insert()`。

根因：保存链路依赖 UI 层 `mExistingSchedule` 判断新建/更新；一旦 UI 状态没有拿到已有安排，仓库层直接插入，撞 `task_id UNIQUE`。仓库层应承担“一任务一安排”的最终一致性。

决策：`TaskScheduleRepository.insert()` 改为按 `taskId` 安全保存。事务内先查 active schedule；已有则沿用原 `id` 更新，无则插入并回填新主键。这样保持一任务一安排规则，同时避免保存分支状态失效导致崩溃。

### 安排任务模块重设计核心决策（2026-05-23）

一个任务只能有一条有效安排，字段统一表达。详见 D024。

### 槽位表占用/过去时间过滤修复（2026-06-04）

回溯 `05e0c0f` 重构前的逻辑，发现最初实现时，非单次安排（每天/每周/每月）就一直调 `getOccupiedSlots(today)` 查今天占用，这个检查对周期性安排不合理——周期任务是对未来多个日期生效的模板，不应被今天的占用或过去时间约束。

修复：`effectiveDateMs` 统一驱动。顶层单次→所选日期，时段组单次→单次日期，时段组每天/每周→0（关闭占用+过去时间过滤）。时段间槽粒只有 `exceedsPeriodEnd`（时段末尾时长不够）一种过滤。

长假类时段组单次日期芯片同步去掉"仅本次："前缀，直接显示日期。

### 安排功能重构核心决策（2026-06-04）

安排与时段组挂钩：
- MONTHLY 砍掉，不符合项目定位
- 安排类型：单次（自动匹配时段组）+ 各开启时段组（工作日/长假类）
- 时段组关闭 → 关联安排不触发（调已有命中接口判断，安排侧不自己判断 WHY）
- 工作日时段组模式：标准（5 天）/ 6 天，周 chip 数量动态渲染
- 6 天周六行为由命中算法决定，安排系统只调 `isGroupActive()`，自然对接
- 槽位取关联时段组的时段，时间线只看当前生效时段组

数据层：
- `TaskScheduleEntity` 新增 `linkedPeriodGroupType`（String，@NonNull）+ `scheduleSubType`（0=每天/1=每周/2=单次）
- `isRecurring()`：`scheduleSubType != 2`（每天和每周均为重复）
- `scheduleType`/`scheduleSubType` 短期共存，旧记录不清洗
- DB migration 1→2：加两列 + 删 MONTHLY 记录
- `PeriodGroupRuleResolver` 新增 `WorkdayMode` 枚举 + get/set
- DB 操作全部后台线程，`refreshSlotView` 零 Room 查询

### 全项目审查修复（2026-05-30/31）

- AlarmReceiver 主线程 DB 操作 → AppDatabase.execute() 分发到后台线程池
- 多项写操作非原子 → 统一包裹 runInTransaction
- MainViewModel.recompute() CAS 防重入存在静默丢更新 → 改为 CAS 排队模式
- AppDatabase 架构收口：sDatabaseWriteExecutor 私有化，API 收口为 execute/runInBackground
- BaseViewModel 新建共享基类：提供 mApp/mDb/runInBackground/runOnUiThread
- DB 版本回退到 1（未发布第一版，无需迁移）

### 排查发现（2026-05-23）
- 停用后复安排的 `task_id UNIQUE` 冲突
- weekly bitmask 错位（文档/UI 约定 bit0=周日，`computeNextMatch` 使用错误映射）
- `TimelineBuilder` 未按日期过滤
- 通知"开始"绕过统一开始校验
- 安排入口权限门禁不完整

### 修复关键决策（2026-05-24）
- **匹配/触发计算单点权威**：抽 `TaskScheduleMatcher`，所有调用方一律走它
- **weekly bitmask 约定 `bit0=周日`**：统一到 Matcher 后所有路径自动对齐
- **开始校验单点权威**：抽 `TaskStartGuard.evaluate(Context, taskId)`，前台+后台共用
- **UNIQUE 策略**：保留 + insert 前 `deleteDisabledByTaskId` 清理
- **v10->v11 迁移直接清空**：旧语义不可无损映射
- **权限门禁前置到入口**：`navigateToSchedule` 双权限检查
- **cancel 先于 disable**：保证能取到记录和 alarm

### Bug 1 二层缓存根因（2026-06-04）

`TimelineBuilder.build()` 第 47 行用 `(taskIds, execIds)` 做缓存键。任务开始执行后 `executingStartMs` 变化但 ID 不变，缓存命中返回旧结果——执行中任务块不出现。此前只修了 `TaskRepository` 缓存层（`mCachedActiveTasks = null`），遗漏了 `TimelineBuilder` 自身缓存，导致"返回任意界面都没用、只有重启 APP 才恢复"。

修复：缓存键增加 `hasRunning` 标志位（遍历 `activeTasks` 时计算 `executingStartMs > 0 && executingEndMs == 0`），执行状态从 0→1 或 1→0 时自动穿透缓存。

### 已完成任务真实耗时显示（2026-06-04）

`TimelineView` 已按真实开始/结束时间绘制已完成任务条长度，但元文字仍取 `focusMinutes`，造成条形长度和文字不一致。修复：`TimelineItem` 增加 `actualMinutes` 字段，`TimelineBuilder` 从 `TaskExecutionEntity.actualMinutes` 传入；`TimelineView.getTaskMetaText()` 对已完成项显示真实耗时，执行中仍显示计划时长。

### 连续安排通知丢失回归修复（2026-06-05）

接连保存多个安排时，保存完成回调曾先执行 `onComplete.run()`（页面 `finish()`）再注册闹钟。若回调异常或 Activity 提前结束，后续 `scheduleTaskAlarm()` 被跳过，表现为只有第一个安排到点通知。修复：`insertSchedule()` / `updateSchedule()` 回调顺序恢复为先 `scheduleTaskAlarm()`、`refreshCaches()`，最后执行页面返回回调。

### 优先标签状态行（2026-05-25）
主界面右侧栏顶部增加优先标签生效状态标注，支持临时关闭/恢复。详见 modules/tag.md。

### 时间段编辑约束（2026-05-25/26）
步进按钮 + PopupWindow 浮层滚轮 + 磁盘分区联动。详见 modules/time-period.md。

### Repository 缓存线程安全（2026-06-03 审查）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- Repository 层新增内存缓存以减少 Room 同步查询，但 `ArrayList`/`HashMap` 在线程池中无同步保护。volatile 只保证引用可见性，不保护集合内部状态 → 改用 `CopyOnWriteArrayList` / `ConcurrentHashMap`
- `AlarmReceiver` 部分 handler 缺少 `goAsync()`，BroadcastReceiver 进程可能在后台 DB 操作完成前被系统回收
- `CONFIRM_TYPE_CHECKLIST_STATE` 等常量和 `PreCompleteConfirmCallback` 接口从 `MainViewModel` 移至 `BaseTaskViewModel`，消除 `ReminderDetailViewModel` 对 `MainViewModel` 的反向依赖
