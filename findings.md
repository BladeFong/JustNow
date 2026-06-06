# 研究发现

## 2026-06-06 安排任务推荐化重构

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-recommend-design.md](docs/superpowers/specs/2026-06-06-schedule-recommend-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**根因分析**：
- 旧版安排任务"占用"时间槽，截断剩余时间，阻断其他任务开始，到点需处理执行中任务冲突。边界情况多（在安排范围内时 effectiveRemaining=0 导致所有任务不可开始、安排任务自身也无法开始、底部栏显示 0 分钟等）
- "强制插入时间线"模型与"用户自主决定"产品理念矛盾

**技术决策**：
- 改为"到点优先推荐"：不占用时间槽、不截断剩余时间、不阻断其他任务
- 移除 `applyScheduleTruncation`、`effectiveRemaining`/`effectiveEndMinute`、底部栏安排提示、时间线安排块、槽位占位判断
- 推荐引擎：安排任务到点后 30 分钟内 `weight -= 300`（高于优先标签的 -200）
- 通知选项：忽略→取消优先级；延迟30分钟→再通知+顺延（仅一次，记录 `postponedUntilMs`）；开始→取消优先级
- 跨时段：只取消已过时段内的安排任务优先级
- 槽位表：30 分钟粒度，每行 4 格

**误报排除**：无。

## 2026-06-06 安排任务感知的剩余时间（已被推荐化重构替代）

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md](docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**根因分析**：
- `TimeRemainingCalculator.compute()` 只看时段边界（`endMinute - nowMinute`），不知道时段内部的安排占用
- `TimelineView` 液体色块独立实现 `periodBottomY`，与 calculator 无数据关联，是第四条独立代码路径
- `DisplayEngine.fitsTime` 和 `TaskStartGuard` 共享 `TimeRemainingCalculator` 数据源，改 calculator 可同步修复
- `TaskScheduleRepository` 无缓存，每次走 DAO 查询

**技术决策**：
- `compute()` 新增重载接收 `List<TaskScheduleEntity>`，内部 `applyScheduleTruncation` 找 `scheduledTime > nowMinute` 的最小值
- 不需要 `TaskEntity.focusMinutes`：找最近安排开始只需 `schedule.scheduledTime`；判断"在范围内"的结果是用原始 remaining，不需要算范围结束
- `PeriodStatus` 新增 `effectiveRemaining` / `effectiveEndMinute`，原字段不动保持兼容
- `TaskScheduleRepository` 新增 `volatile CopyOnWriteArrayList` 缓存，写操作清缓存

**误报排除**：无。

**实现中发现的问题**：
- `getRemainingText()` else 分支（effectiveRemaining < 60 时）误用 `remainingMinutes` 而非 `effectiveRemaining`，导致底部栏显示原始值
- `applyScheduleTruncation` 只找未来安排，未检测当前是否在安排范围内。安排到点后 effectiveRemaining 回退到原始值，右侧栏任务仍可开始。修复：新增范围检测 `nowMinute >= startMinute && nowMinute < startMinute + focusMinutes`，在范围内时 `effectiveRemaining = 0`
- `@Ignore` 方案失败：Room 对 Entity 的 `@Ignore` 字段完全跳过，不会从 JOIN 查询填充。正确做法是用 POJO 接收查询结果，Repository 层转换为 Entity
- `TaskScheduleDao` 新增 `ScheduleWithFocusMinutesFull` POJO + `getEnabledSchedulesWithFocusSync()` JOIN 查询，恢复 `ScheduleWithFocusMinutes` + `getEnabledOnceSchedulesWithFocusSync()`
- `findTimelineItemAt` 条件 `!item.running && item.endMs > item.startMs` 误跳过安排任务（`endMs = startMs + focusMinutes`），改为 `!item.running && item.actualMinutes > 0`
- 底部栏剩余时间：安排到点时 `effectiveRemaining = 0` 应显示原始时段剩余（`remainingMinutes`），安排之前应显示"X分钟后有安排任务"而非"剩余X"

## 2026-06-06 忽略交互修复+对话框分流+TYPE_ONCE 超时+跳过表简化

> 审查报告：[docs/code-review-20260605.md](docs/code-review-20260605.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**根因分析**：
- `TaskScheduleRepository.update()` 无条件设 `enabled=true`，TYPE_ONCE 忽略后 schedule 未被真正禁用，下次 `refreshToday` 重新注册闹钟
- `disableExpiredOnceToday` SQL 只判断日期（`scheduleValue < todayStartMs`），不判断时间，今天已过的 TYPE_ONCE 不会被 disable
- `showTaskDetailDialog` 被右侧栏和左侧时间线共用，`hasActiveSchedule` 分支隐藏了右侧栏的"安排"按钮

**技术决策**：
- 对话框分流：右侧栏保持原逻辑，时间线已安排任务走独立 `handleTimelineScheduledTaskClick`（开始/忽略/取消），通过 `mTimelineScheduledTaskClickEvent` 事件驱动
- `isScheduleActionable`：只检查 `enabled + matchesToday`，不检查时间——超时由 `disableExpiredOnceSchedules` 机制保证
- TYPE_ONCE 超时：deadline = `scheduledTime + focusMinutes`（任务应完成时间），通过 TIME_TICK + onResume 每分钟触发；DAO JOIN tasks 表获取 focusMinutes
- 跳过表单行模式：`schedule_id` 为 PK，`lastSkippedDateMs + skipCount`，`ReminderScheduler.scheduleNextAfterSkip` 从 `lastSkippedDateMs + 1天` 开始计算，去掉 365 天遍历

**误报排除**：无。审查 3 个发现均确认为真实问题。

## 2026-06-05 安排调整&忽略交互

> 设计文档：[docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md](docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**根因分析**：
- 一任务一安排后，主界面按钮仍固定显示"安排"，无法表达已有安排的调整语义。
- `restoreExistingSchedule()` 先恢复槽位，再触发类型切换；`onTypeSelected()` 会把槽位重置为 `-1`。
- 通知缺少"忽略本次"语义，用户只能开始或延迟。

**技术决策**：
- 按当天可命中安排决定按钮显示"安排"或"调整安排"。
- 槽位恢复放到类型/子类型全部恢复之后，再刷新槽位视图。
- 通知最左侧新增"忽略"操作；单次安排禁用，重复安排写跳过记录并重调度下次。
- 时间线已安排任务点击走独立弹窗，避免右侧栏安排入口与时间线执行入口耦合。

## 2026-06-05 安排保存链路 upsert + 通知刷新

> 设计文档：[docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md](docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)

**根因分析**：
- 新建安排后未把 Room 返回主键回填到 `schedule.id`，闹钟广播使用 `scheduleId=0`，到点查不到安排。
- 保存分支依赖 UI 层 `mExistingSchedule` 判断新建/更新；状态失效时仓库层直接插入，撞 `task_id UNIQUE`。
- 主界面时间线缓存未把 schedule 数据纳入签名，保存后当前进程内可能继续复用旧时间线。

**技术决策**：
- 仓库层 `insert()` 按 `taskId` 串行 upsert，已有记录复用 `id/createdAt`，新记录插入后回填主键。
- 调度链路继续以真实 `schedule.id` 作为唯一主键，不新增替代 key。
- 主界面观察安排表变化，时间线缓存签名加入 schedule 数据。
- 保存成功后在单页 `TaskScheduleActivity` 使用 `finish()` 返回。

## 2026-06-05 连续安排通知回归修复 + 槽粒缓冲

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**根因分析**：
- 保存完成回调中的页面返回曾被放到闹钟注册前，`finish()` 或回调异常会阻断后续 `scheduleTaskAlarm()`。
- 用户选择过近的当天槽位时，保存链路耗时可能让 `triggerMs <= now`，`ReminderScheduler.schedule()` 静默跳过。

**技术决策**：
- 保存回调顺序固定为先注册闹钟、刷新缓存，再执行页面返回。
- 槽粒粒度抽常量 `SLOT_INTERVAL_MINUTES = 10`。
- 当天禁用当前槽粒和下一个槽粒，给保存与闹钟注册留出缓冲。

## 2026-06-05 审查修复：跨年窗口、映射、Widget 资源

> 审查报告：[docs/code-review-20260604.md](docs/code-review-20260604.md)
> 详见：[modules/time-period.md](modules/time-period.md)、[modules/widget.md](modules/widget.md)

**关键发现**：
- `canMatchInNextThreeMonths()` 以 MMDD 简单比较，窗口跨年时会错排次年 Q1 假期组。
- `TaskScheduleFragment.getGroupDisplayName()` 使用固定集合 switch，不符合项目数据驱动映射规范。
- `bg_widget_root.xml` 硬编码颜色不利于资源统一；MONTHLY 移除后残留资源需清理。

**技术决策**：
- 跨年窗口复用 `isInMonthDayRange()` 逻辑处理。
- 时段组显示名改为 `Map<String, Integer>` 映射。
- Widget 背景色提取为 `@color/widget_root_bg`，紧凑模式垂直 padding 微调。

## 2026-06-04 5 项 Bug 修复

> 设计文档：[docs/superpowers/specs/2026-06-04-five-bugs-fix-design.md](docs/superpowers/specs/2026-06-04-five-bugs-fix-design.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-detail.md](modules/reminder-detail.md)、[modules/task-input.md](modules/task-input.md)、[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)

**关键发现**：
- 时间线有两层缓存：Repository 缓存和 `TimelineBuilder` 缓存。仅清 Repository 缓存不足以让执行中任务出现。
- 已完成任务条形长度已按真实耗时绘制，但元文字仍显示计划专注时长。
- Edge-to-edge 后 `adjustResize` 不再可靠，编辑页需主动处理 IME inset。
- 单象限删除使用异步 delete 后立刻 load，可能读到删除前数据。

**技术决策**：
- `TimelineBuilder` 缓存签名加入执行中状态。
- `TimelineItem` 增加 `actualMinutes`，已完成项显示真实耗时。
- `TaskInputActivity` 根布局处理 IME bottom inset。
- 单象限删除改用同步删除 + LiveData/onResume 双路刷新。

## 2026-06-04 安排功能重构

> 详见：[modules/task-execution.md](modules/task-execution.md)

核心决策：MONTHLY 砍掉；安排关联时段组；动态类型选择器；槽位取命中时段组；时间线去最大集；工作日模式标准/6 天；scheduleType/scheduleSubType 共存。

## 2026-06-02 全项目代码审查 — 关键发现

> 审查报告：[docs/code-review-20260602.md](docs/code-review-20260602.md)
> 忽略项：[docs/code-review-ignore.md](docs/code-review-ignore.md)

处理的模块：智能展示引擎、标签、任务执行、节假日数据、桌面Widget、任务录入、时间段、提醒延迟

要点：Repository 实例级缓存+Application 单例化、recomputeSync 按职责拆分（filterTasks+assembleDisplayItems）、WidgetUpdateHelper 按注释块拆分、JSON 解析/构建规范化（JSONObject/JSONArray）、工具类收敛（DateUtils/HolidaySourceFactory）、TimeRemainingCalculator 全静态化。

详见：[modules/smart-display.md](modules/smart-display.md) 2026-06-02 段落、[modules/tag.md](modules/tag.md) 2026-06-02 段落、[modules/task-execution.md](modules/task-execution.md) 2026-06-02 段落、[modules/holiday-data.md](modules/holiday-data.md) 2026-06-02 段落、[modules/widget.md](modules/widget.md) 2026-06-02 段落、[modules/task-input.md](modules/task-input.md) 2026-06-02 段落、[modules/time-period.md](modules/time-period.md) 2026-06-02 段落、[modules/reminder-delay.md](modules/reminder-delay.md) 2026-06-02 段落

## 2026-06-03 全项目代码审查 — 关键发现

> 审查报告：[docs/code-review-20260603.md](../docs/code-review-20260603.md)

处理的模块：任务执行、标签、时间段、智能展示引擎、四象限任务管理、提醒延迟、任务录入、节假日数据

要点：Repository 缓存集合并发安全、AlarmReceiver goAsync 补全、ViewModel 重复代码去重、DisplayEngine 死参数移除、常量/接口架构收口到 BaseTaskViewModel、死代码清理。排除 2 误报（跨进程竞态不成立、双重创建不成立）。

详见：[modules/task-execution.md](modules/task-execution.md) 2026-06-03 段落、[modules/tag.md](modules/tag.md) 2026-06-03 段落、[modules/time-period.md](modules/time-period.md) 2026-06-03 段落、[modules/smart-display.md](modules/smart-display.md) 2026-06-03 段落、[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) 2026-06-03 段落、[modules/reminder-delay.md](modules/reminder-delay.md) 2026-06-03 段落、[modules/task-input.md](modules/task-input.md) 2026-06-03 段落、[modules/holiday-data.md](modules/holiday-data.md) 2026-06-03 段落

## 2026-06-03 v2 全项目追加代码审查 — 关键发现

> 审查报告：[docs/code-review-20260603-v2.md](docs/code-review-20260603-v2.md)
> 补齐设计：[docs/superpowers/specs/2026-06-03-quadrant-degrade-widget-completion-design.md](docs/superpowers/specs/2026-06-03-quadrant-degrade-widget-completion-design.md)

处理的模块：四象限降级恢复、智能展示引擎、桌面 Widget、任务完成流程

要点：手动完成任务未写降级记录属实，已让手动完成复用统一完成逻辑；四象限概览按原始象限分组为设计如此，四象限任务管理模块用于管理；Widget 展示已接入降级，并与主界面右侧栏保持排序和色标一致。

审查子代理已复核修改，报告未处理项为无；不处理项原因已记录到 `docs/code-review-ignore.md`。

详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md) 2026-06-03 v2 段落

## 2026-05-30 全项目代码审查 — 关键发现

> 审查报告：[docs/code-review-20260530.md](../docs/code-review-20260530.md)

处理的模块：节假日数据、任务执行、时间段、智能展示引擎、Widget、标签、提醒延迟、任务录入

要点：47 项发现 → 排除 5 误报 → 分 8 批修复 24 项。数据层（布尔值解析 Bug、事务原子化、BaseRepository 重构、IOException 区分）、UI/广播/Widget（goAsync WakeLock、require* 崩溃、CAS 排队、Adapter 泄漏、WidgetConfigure 强引用）、架构收口（AppDatabase 私有化、BaseViewModel 新建）。

误报排除：RemoteViews 跨线程、mEngineFailed 非线程安全、线程池 shutdown（Android 进程模型）、VM 持有 Application（标准模式）、Detail 拼写。

详见：[modules/task-execution.md](modules/task-execution.md) 2026-05-30 段落、[modules/holiday-data.md](modules/holiday-data.md) 2026-05-30 段落、[modules/widget.md](modules/widget.md) 2026-05-30 段落、[modules/reminder-delay.md](modules/reminder-delay.md) 2026-05-30 段落、[modules/smart-display.md](modules/smart-display.md) 2026-05-30 段落、[modules/time-period.md](modules/time-period.md) 2026-05-30 段落、[modules/tag.md](modules/tag.md) 2026-05-30 段落、[modules/task-input.md](modules/task-input.md) 2026-05-30 段落

## 2026-05-29 四象限降级恢复

> 详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md)

降级周期（`degrade_period`）放 `tasks` 表作为任务持久属性；降级临时状态（`task_quadrant_degrade`）独立表，自清理。降级与 schedule 无关。

## 2026-05-28 四象限任务管理 Toolbar/状态栏颜色收尾

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — Toolbar/状态栏颜色收尾

- `MainActivity` 作为 `nav_graph` 宿主统一管理 App chrome，比 `QuadrantTaskListFragment.onDestroyView()` 恢复全局颜色更稳定。
- Android 15+ 目标应用状态栏透明，`setStatusBarColor` 对状态栏不再可靠；单象限页通过 `AppBarLayout` 背景 + `fitsSystemWindows=true` 让状态栏区域透出同一象限色。
- Android 14 及以下仍保留 legacy `Window.setStatusBarColor()`，覆盖旧系统非透明状态栏。
- `compileDebugJavaWithJavac` 最终通过；期间遇到一次 AGP `mergeDebugResources` 增量缓存 NPE，`clean` 后恢复。

## 2026-05-27 四象限任务管理

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — 研究发现、技术决策

- 现有代码基础（DisplayEngine 无缓存、MainFragment 无 ViewPager2、TaskDao.delete 物理删除）
- 设计决策（ViewPager2 嵌入、computeByQuadrant、不滚动/可滚动、PopupWindow 筛选、MODE_VIEW）

## 2026-05-26 桌面 Widget 实现

> 详见：[modules/widget.md](modules/widget.md) — 研究发现、技术决策

- 架构决策：ReminderDetailActivity 独立、系统原生 GridLayout + addView 行模板、固定列宽对齐、AlarmManager 整分钟刷新
- 后续决策：任务点击走主界面统一语义；数据变更主动刷新经 `DataChangeDispatcher` 解耦；Widget 标签筛选按 `appWidgetId` 持久化并在引擎前预过滤
- Widget 添加权限门禁：Launcher 只可靠接收 `android:configure` Activity 的 `RESULT_OK` / `RESULT_CANCELED`；`BroadcastReceiver.startActivity()` 会被后台启动限制影响。`MainActivity` 是 `singleTop`，直接作为 `startActivityForResult` 目标不稳，因此用 `WidgetPermissionGateActivity` 承接 Launcher 结果，用 `WidgetConfigureResultBridge` 接收主界面权限流程结果
- 已修复 10 个 Bug（RemoteViews 类白名单限制、字号违规、MaterialComponents 不可用、高度 dp 误算、空状态占位、无标签列对齐等）
- RemoteViews 框架限制实测确认（类白名单、布局属性限制）
- 遗留：标签筛选需真机/桌面 Launcher 复验，多 Widget 独立筛选需实机确认

## 2026-05-25 优先标签状态行

> 详见：[modules/tag.md](modules/tag.md) — 优先标签状态行

- 主界面右侧栏顶部优先标签生效状态标注
- 生效态/暂停态双视觉 + `mSuppressPriority` 会话级标记
- 后台恢复：ProcessLifecycleOwner + `resetFiltersAndPriority()`

## 2026-05-25 时间段编辑约束（第二版）

> 详见：[modules/time-period.md](modules/time-period.md) — 编辑约束

- 步进按钮（+/-15min）+ PopupWindow 浮层滚轮 + 磁盘分区联动
- 约束：早上 >= 06:00，晚上 <= 23:00，午休/晚餐 >= 1h，其余 >= 30min
- 移除 PeriodTimePickerDialog.java（第一版跨版本闪退）

## 2026-05-24 安排任务模块重设计修复

> 详见：[modules/task-execution.md](modules/task-execution.md) — 研究发现、技术决策

关键决策：TaskScheduleMatcher 权威实现（bit0=周日）、TaskStartGuard 统一校验、UNIQUE 保留+insert 前清理、v10->v11 DROP+CREATE、权限前置到入口、cancel 先于 disable。

## 2026-05-23 安排任务模块重设计

> 详见：[modules/task-execution.md](modules/task-execution.md) — 研究发现、技术决策

核心决策：一个任务一条有效安排 + 字段统一表达 + DAO 变更 + 闹钟调度三步骤。

## 2026-05-21 APP 图标设计

> 详见：[modules/app-icon.md](modules/app-icon.md) — 研究发现、技术决策

设计目标、方向取舍、关键决策（配色、安全区、任务条形态、不参与 Themed Icons）。

## 2026-05-20 精确闹钟权限崩溃与统一权限引导

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 精确闹钟权限崩溃

`SecurityException` 根因（Android 12+ 运行时权限）+ PermissionHelper 统一方案。

## 2026-05-20 任务详情页系统

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — 研究发现、技术决策

设计约束、数据模型、附加模块、完成前确认机制、统一入口、导航。

## 2026-05-20 编辑页样式修复与录入/编辑拆分

> 详见：[modules/task-edit.md](modules/task-edit.md) — 研究发现、技术决策
> 详见：[modules/task-input.md](modules/task-input.md) — 研究发现、技术决策

专注时长布局方案（两行 RadioButton）、清单编辑器防删问题、按钮样式规范、图标选择、拆分原因。

## 2026-05-18 主界面非时段直接开始保护

> 详见：[modules/task-execution.md](modules/task-execution.md)

UI 状态复盘：非时段将"安排"切为主按钮、"开始"置灰；琐碎任务只保留置灰"开始"。

## 2026-05-18 任务录入四象限保存崩溃修复

> 详见：[modules/task-input.md](modules/task-input.md) — 研究发现、技术决策

无标签统一使用 null（D022）。Robolectric runtime dependency 镜像配置。

## 2026-05-17 时间段组编辑对话框布局优化

> 详见：[modules/time-period.md](modules/time-period.md) — 编辑对话框

已确认视觉方向 + 技术决策（布局+少量显示格式化、保留 MM-dd）。统一样式整理 + AAPT 编译问题记录。

## 2026-05-16 节假日数据月度下载频率控制

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 研究发现、技术决策

架构决策（fetch() 返回 Entity + parser 填 Entity + 月度标记 + 天数比较）。

## 2026-05-15 节假日数据驱动工作日判断

> 详见：[modules/holiday-data.md](modules/holiday-data.md)

共用缓存+共用流程、补班差异、JSON key 改名、测试矩阵（23 用例）。

## 2026-05-15 任务执行规则收敛

> 详见：[modules/task-execution.md](modules/task-execution.md)

琐碎任务完全不进入左侧时间线、不能安排、完成后当天不列入右侧栏。

## 2026-05-15 androidTest 测试维护

> 详见：[modules/testing.md](modules/testing.md)

现有 androidTest 修正（匹配底部添加按钮、双屏录入、点击象限即保存新流程）。

## 2026-05-15 无节假日数据兜底逻辑

> 详见：[modules/holiday-data.md](modules/holiday-data.md)

非 CN/HK/MO 地区禁用 WORKDAY 组，策略回退 STANDARD_WEEK。

## 2026-05-12/13 主界面工作

> 详见：[modules/ui-design.md](modules/ui-design.md) — 底部时段栏

底部时段栏设计确认（紧凑->非紧凑）、黄金比例双栏。

## 品牌体系 -> [详细文档](modules/brand.md)

企业：邻近科技 / Nearby Tech。产品：恰恰有事 / Just Now。标语：刚好，手边有事 / Right here, right now。

## 项目初始状态

- 全新 Android 项目，包名 `com.nearby.justnow`
- Java + View-based 模板，SDK 36，minSdk 33，targetSdk 36

## 技术调研

### 依赖选型
- ViewBinding、Navigation Component、Room、Material 3
- 图表库：待评估 MPAndroidChart

### 节假日数据方案
- 中国境内：holiday-cn；境外：Google Calendar API（预留）
- 按年缓存，月度重新检查
- WorkManager 保障获取不被退出打断

### Room 数据库表设计（初步）
| 表名 | 用途 | 核心字段 |
|------|------|----------|
| `tasks` | 任务主表 | id, content, tag_id, quadrant, focus_minutes, created_at |
| `tags` | 标签表 | id, name, color |
| `time_periods` | 时间段配置 | id, period_type, start_time, end_time |
| `task_executions` | 执行记录 | id, task_id, date, status, period_slot |
| `holiday_config` | 节假日配置 | date, name, is_holiday |

## 已确认决策
1. 节假日：中国 holiday-cn；境外 Google Calendar API（预留）；按年缓存
2. "琐碎"任务：二阶段点击
3. 搜索：仅匹配任务内容；标签填写时关键字推荐
4. 通知：设定了时间的任务需系统通知
5. 常规/工作日默认 5 段时段
6. 晚上四象限反转；午休+晚餐优先"琐碎"
7. Widget 与 APP 复用 M4 智能展示引擎

## 模块拆分文档

模块已拆分为独立文档，存放于 `modules/` 目录：
- [品牌体系](modules/brand.md)
- [检索引擎](modules/search-engine.md) — M1
- [智能展示引擎](modules/smart-display.md) — M4
- [时间段计算](modules/time-period.md) — M3
- [剩余时间计算](modules/time-remaining.md)
- [节假日数据](modules/holiday-data.md) — M8
- [桌面 Widget](modules/widget.md) — M7
- [数据统计](modules/stats.md) — M6
- [UI 设计](modules/ui-design.md)
- [APP 图标](modules/app-icon.md)
- [测试策略](modules/testing.md)
- [提醒延迟](modules/reminder-delay.md) — D020
- [提醒详情页](modules/reminder-detail.md) — M5
- [标签](modules/tag.md)
- [任务编辑页](modules/task-edit.md)
- [任务录入](modules/task-input.md)
- [任务执行](modules/task-execution.md) — M5
- [四象限任务管理](modules/quadrant-task-manage.md) — M9

## 待解决问题
- holiday-cn 是否含国务院公告抓取实现
- 港澳 ICS 数据中假期名称是否匹配中文/英文关键词
- Nager.Date API 接入（其他地区）
