# 任务执行模块

> 对应 task_plan.md M5

# 阶段规划、决策记录 （拆分自 task_plan.md）

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

# 研究发现、技术决策 （拆分自 findings.md）

### 安排任务模块重设计核心决策（2026-05-23）

一个任务只能有一条有效安排，字段统一表达。详见 D024。

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

### 优先标签状态行（2026-05-25）
主界面右侧栏顶部增加优先标签生效状态标注，支持临时关闭/恢复。详见 modules/tag.md。

### 时间段编辑约束（2026-05-25/26）
步进按钮 + PopupWindow 浮层滚轮 + 磁盘分区联动。详见 modules/time-period.md。

### Repository 缓存线程安全（2026-06-03 审查）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- Repository 层新增内存缓存以减少 Room 同步查询，但 `ArrayList`/`HashMap` 在线程池中无同步保护。volatile 只保证引用可见性，不保护集合内部状态 → 改用 `CopyOnWriteArrayList` / `ConcurrentHashMap`
- `AlarmReceiver` 部分 handler 缺少 `goAsync()`，BroadcastReceiver 进程可能在后台 DB 操作完成前被系统回收
- `CONFIRM_TYPE_CHECKLIST_STATE` 等常量和 `PreCompleteConfirmCallback` 接口从 `MainViewModel` 移至 `BaseTaskViewModel`，消除 `ReminderDetailViewModel` 对 `MainViewModel` 的反向依赖

# 进度日志 （拆分自 progress.md）

> 详见：[progress.md](../progress.md) — 2026-05-14 任务执行链路重构、2026-05-23 安排模块重设计、2026-05-24 排查修复+槽位重做、2026-05-24 右侧栏点击拦截+主线程 DB 崩溃修复、2026-06-03 审查修复

### 2026-06-03 审查修复

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- [x] TaskExecutionRepository / TaskRepository 缓存集合改为并发安全类
- [x] AlarmReceiver ACTION_POSTPONE / ACTION_DAILY_REFRESH 补 goAsync()
- [x] CONFIRM_TYPE_CHECKLIST_STATE / SHORT_DURATION_THRESHOLD_MINUTES / PreCompleteConfirmCallback 移至 BaseTaskViewModel
- [x] 编译通过 + testDebugUnitTest 371 用例全通过（新增 75 测试）

### 2026-05-30/31 审查修复

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md)

- [x] AlarmReceiver 主线程 DB 修复；6 处事务原子化；CAS 排队模式；BaseViewModel 架构收口；DB 回退 v1
- [x] 编译 + 全量测试通过

---

- [x] 主列表点击改为任务详情弹窗
- [x] 直接开始执行改为统一校验后写入执行中状态
- [x] 非时段下右侧任务详情禁止直接开始
- [x] 左侧时间线改用 `TimelineItem`
- [x] 新增 `task_schedules` 基础表与安排页面
- [x] 琐碎任务不能安排；完全退出左侧时间线
- [x] 自动提醒、通知触发（AlarmManager + 常驻通知）
- [x] 安排页槽位视图重做：10min 粒度 + 6 列 GridLayout + 范围选中
- [x] 安排模块排查修复 4 Pack + 34 测试用例
- [x] 右侧栏执行中专注任务点击拦截
- [x] < 15min 完成引导：`ShortCompletionDialog` + `ChoreHiddenTodayStore`
- [x] 安排页选择器视觉统一：chip 样式按钮 + 月历 Dialog
- [x] 安排页类型切换不抖动 + 保存按钮圆角填充
- [x] Java 编译验证 + testDebugUnitTest 218 用例 0 失败

## 验证状态

- compileDebugJavaWithJavac 通过
- testDebugUnitTest 218 用例 0 失败
- UI 自动化验证不作为当前收尾要求
- 建议后续真机/模拟器验证 migration 链路

**状态**：🔨 已实现

### 2026-06-02 — code-review-20260602 修复

- [x] BaseTaskViewModel 全部改用 `mApp.getXxxRepository()`，消除方法体内 `new TaskRepository(mDb)` 等
- [x] `cleanExpiredDegrades()` 从 recomputeSync 移入 `onPostComplete()`，降级表清理仅任务完成时触发
- [x] TimelineTaskState 构造函数从自行执行 Room I/O 改为接收数据参数，I/O 在调用方 `loadTimelineTaskState()` 完成
- [x] TaskRepository 新增 `getNonExpiredDegradeMapSync()`，封装过期过滤+建 Map
