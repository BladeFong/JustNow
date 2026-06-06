# 进度日志

### 2026-06-06 — 代码审查修复

> 审查报告：[docs/code-review-20260606.md](docs/code-review-20260606.md)

**状态**：编译通过。审查今日 3 个提交，发现 3 项（1 important + 1 nit + 1 suggestion）。#1 提取 `TaskScheduleRepository.skipOrDisable()` 消除 AlarmReceiver/MainViewModel 重复逻辑；#2 误报（Room 对 POJO 自动做 boolean 转换）；#3 确认关闭（v3 中间版本未发布）。

### 2026-06-06 — 安排任务感知的剩余时间

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md](docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**状态**：编译通过，用户验证通过。剩余时间计算未考虑时段内安排任务块，导致液体色块/底部栏/展示引擎/任务开始守卫四处不一致。方案：TimeRemainingCalculator 新增重载接收今日安排，applyScheduleTruncation 截断到最近安排开始 + 范围内检测（effectiveRemaining=0），PeriodStatus 新增 effectiveRemaining/effectiveEndMinute。TaskScheduleEntity 新增 @Ignore focusMinutes 字段由 Repository 层 POJO 转换填充。涉及文件：TaskScheduleRepository、TaskScheduleEntity、TaskScheduleDao、TimeRemainingCalculator、MainViewModel、TimelineView、TaskStartGuard、WidgetUpdateHelper。

### 2026-06-06 — 6月4日起文档补录检查

> 详见：[task_plan.md](task_plan.md)、[findings.md](findings.md)

**状态**：文档维护完成，未编译。检查 6/4 起已落盘提交和模块文档，补齐根目录 `task_plan.md` / `findings.md` 的 6/4-6/5 摘要，并补录 `task-execution`、`reminder-detail`、`task-input` 三个模块正文漏项。

### 2026-06-06 — 忽略交互修复+对话框分流+TYPE_ONCE 超时

> 审查报告：[docs/code-review-20260605.md](docs/code-review-20260605.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过。

**审查**：对 6/5 的 8 个提交做代码审查，发现 3 个问题（1 blocking + 1 important + 1 nit）。

### 2026-06-06 — 跳过表简化：单行模式

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过。`task_schedule_skips` 从每次忽略一行改为每个 schedule 一行（lastSkippedDateMs + skipCount），DAO 改 upsert，`ReminderScheduler` 去掉 365 天遍历逻辑，数据库迁移 v3→v4。

**修复**：
- `update()` 无条件设 `enabled=true` 回归：TYPE_ONCE 忽略改用 `disableScheduleSync`
- 对话框分流：右侧栏 `showTaskDetailDialog`（开始/安排/取消）与时间线 `handleTimelineScheduledTaskClick`（开始/忽略/取消）分离，互不影响
- `configureScheduleButton` 恢复 `schedule` 参数，有可命中安排时显示"调整安排"
- `matchesToday` 判断：`configureScheduleButton`、`onTimelineItemClicked`、`showTaskDetailDialog` 三处统一
- 时间线点击路由：执行中走 `resolveAndHandleTaskClick`（按 hasContent 分流），已完成不可点击
- `isScheduleActionable` 辅助方法：`enabled + matchesToday`，去掉时间判断（超时由 disable 机制处理）
- TYPE_ONCE 超时 disable：`disableExpiredOnceSchedules` 通过 TIME_TICK + onResume 触发，deadline = `scheduledTime + focusMinutes`

### 2026-06-05 — 安排保存链路修复落地

> 设计文档：[docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md](docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过，用户复测通过。仓库层按 `taskId` 兜底安全保存安排并回填真实 `scheduleId`；两个不同任务分别安排不再闪退，保存后自动返回，主界面时间线即时刷新，到点通知恢复。

### 2026-06-05 — 连续安排通知丢失回归修复

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**背景**：原回调只有闹钟注册+缓存刷新，无返回流程，闹钟正常。后来加 `onComplete.run()`（finish）并放在闹钟之前，接连保存多个安排时后续闹钟未注册。
**状态**：仅代码改动，未编译。回调顺序恢复为先闹钟后返回，避免 finish 早于 AlarmManager 注册导致通知丢失。

### 2026-06-05 — 安排页槽粒缓冲：当日临近槽位禁用

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：仅代码改动，未编译。`SLOT_INTERVAL_MINUTES` 常量统一槽粒粒度，`isPast` 加一个槽粒缓冲，避免选临近时间的槽位后保存回调到 `schedule()` 时触发时间已过导致静默丢弃闹钟。

### 2026-06-05 — 代码审查修复：跨年bug+风格修正+资源清理

> 审查报告：[docs/code-review-20260604.md](docs/code-review-20260604.md)
> 详见：[modules/time-period.md](modules/time-period.md)、[modules/widget.md](modules/widget.md)

**状态**：编译+测试通过。431 tests / 6 预存失败。vacationCanMatch 跨年窗口修复、switch 改 Map、硬编码颜色提取、日期 API 统一、残留资源删除。

### 2026-06-05 — 安排调整&忽略交互

> 设计文档：[docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md](docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**状态**：编译通过，82 相关测试通过。按钮文字区分有无未触发安排、安排页加载 bug 修复、通知忽略+跳过记录表、超时统一为忽略逻辑、时间线已安排任务可点击弹窗。

### 2026-06-04 — 安排页槽位表占用/过去时间过滤修复

> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：编译通过。时段组每天/每周模式不再查占用、不过去时间，`effectiveDateMs` 统一驱动；长假类时段组单次日期芯片去掉"仅本次："前缀。

### 2026-06-04 — 假期组弹窗时段预写入修复 + 春节无数据锁灰

> 设计文档：[docs/superpowers/specs/2026-06-04-period-group-rule-update.md](docs/superpowers/specs/2026-06-04-period-group-rule-update.md)
> 详见：[modules/time-period.md](modules/time-period.md)

**状态**：编译通过。`fillVacationDefaultsCore`/`fillSpringFestivalDefaultsCore` 开弹窗不再写 DB，改保存时统一写入；春节组无未来数据时开关锁灰、禁止编辑。

### 2026-06-04 — Widget 打磨

> 设计文档：[docs/superpowers/specs/2026-06-04-widget-polish-design.md](docs/superpowers/specs/2026-06-04-widget-polish-design.md)
> 详见：[modules/widget.md](modules/widget.md)

**状态**：编译通过。顶部栏点击跳转主界面、圆角半透背景、字体三档定档 + 行高 dimens 化。

### 2026-06-04 — 安排页时段组选项 3 个月窗口过滤

> 设计文档：[docs/superpowers/specs/2026-06-04-task-schedule-redesign.md](docs/superpowers/specs/2026-06-04-task-schedule-redesign.md)（"时段组选项的 3 个月窗口过滤"小节）
> 详见：[modules/time-period.md](modules/time-period.md)

**状态**：编译通过。安排页 `buildEnabledPeriodGroups()` 增加 `canMatchInNextThreeMonths()` 过滤，假期时段组未来 3 个月不能命中则不显示。

### 2026-06-04 — 安排功能重构

> 设计文档：[docs/superpowers/specs/2026-06-04-task-schedule-redesign.md](docs/superpowers/specs/2026-06-04-task-schedule-redesign.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：编译 + 全量 434 测试 0 失败。MONTHLY 砍掉，安排关联时段组，动态类型选择器，时间线去最大集。

### 2026-06-04 — 5 项 Bug 修复

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-detail.md](modules/reminder-detail.md)、[modules/task-input.md](modules/task-input.md)、[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)

**状态**：编译+测试通过。时间线缓存/真实耗时、完成按钮文案、IME 遮挡、单象限删除刷新。

### 2026-06-04 — 四象限概览编辑图标 + 设置页返回箭头修复

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)、[modules/time-period.md](modules/time-period.md)、[modules/stats.md](modules/stats.md)、[modules/tag.md](modules/tag.md)

**状态**：完成。编辑图标白色、设置页 Toolbar 返回箭头。

### 2026-06-03 — 小米真机安排页槽位空白修复

> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：完成。槽位字体 18sp→16sp。

### 2026-06-03 — MainViewModel 消除 prepareComputeContext 共享上下文

> 详见：[modules/task-execution.md](modules/task-execution.md)

**状态**：完成。删除 `prepareComputeContext()` + `ComputeContext`。

### 2026-06-03 — 无标签任务选四象限 NPE 闪退修复

> 详见：[modules/smart-display.md](modules/smart-display.md)

**状态**：完成。`tagMap.get(null)` NPE 判空。

### 2026-06-03 v2 — 全面代码追加审查

> 详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md)

**状态**：编译+374 测试通过。手动完成/Widget 接入补齐。

### 2026-06-03 — 全面代码审查与修复

> 详见：[modules/task-execution.md](modules/task-execution.md) 等 8 个模块

**状态**：编译+371 测试通过（+75 新增）。

### 2026-06-02 — 全面代码审查

> 详见：[modules/smart-display.md](modules/smart-display.md) 等 8 个模块

**状态**：编译+282 测试通过。

### 2026-06-01 — 重复业务逻辑全面重构

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/holiday-data.md](modules/holiday-data.md)

**状态**：编译+251 测试通过。BaseTaskViewModel 模板方法、HolidayDataSource 抽象类。

### 2026-05-31 — 代码审查遗留问题跟进

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译通过，遗留问题全部关闭。

N8 正当设计关闭；补齐 runInTransaction；HTTP 404/403 不修；DB 版本回退到 1。

详见：[modules/task-execution.md](modules/task-execution.md)

### 2026-05-30 — 全项目代码审查与修复

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译 + 全量测试通过。47 项发现 → 排除 5 误报 → 分 8 批修复 24 项。

后续验证建议：[ ] 节假日数据真机验证、[ ] Widget 标签筛选真机验证、[ ] logcat assertNotMainThread 检查

详见：[modules/task-execution.md](modules/task-execution.md)、[modules/holiday-data.md](modules/holiday-data.md)、[modules/widget.md](modules/widget.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/smart-display.md](modules/smart-display.md)、[modules/time-period.md](modules/time-period.md)、[modules/tag.md](modules/tag.md)、[modules/task-input.md](modules/task-input.md)

### 2026-05-29 — 四象限降级恢复

> 详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md)

**状态**：已完成，编译通过 + 227 用例 0 失败（+9 新增测试）

### 2026-05-29 — Widget 添加权限中转主界面化

> 详见：[modules/widget.md](modules/widget.md) — 进度日志

**状态**：代码完成，未编译；待桌面 Launcher 添加 widget 实测。

Widget 配置页改为纯中转：已授权时直接初始化并返回添加成功；未授权时打开 `MainActivity` 的 widget 权限模式，由主界面弹出精确闹钟权限引导。主界面授权结果通过 `WidgetConfigureResultBridge` 回传给中转页，再由中转页向 Launcher 返回 `RESULT_OK` 或 `RESULT_CANCELED`。

### 2026-05-28 — Widget 顶部状态与执行中行高亮

> 详见：[modules/widget.md](modules/widget.md) — 进度日志

Widget 顶部剩余时间超过 60 分钟时改为小时展示；非时段固定将“休息中”和“下个时段”分两行显示；任务列表中执行中任务行增加浅色背景高亮。
Widget 添加改为配置中转页检查精确闹钟权限：未授权时打开主界面弹权限引导，结果回传中转页；已授权继续添加，未授权或返回未通过则取消添加。

### 2026-05-28 — 四象限任务管理 Toolbar/状态栏颜色收尾

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — 进度日志

单象限列表页 Toolbar / 状态栏改为 `MainActivity` 按导航目的地统一管理：进入 `quadrantTaskListFragment` 使用所选象限色，返回主界面恢复默认主题色。Android 15+ 走透明状态栏 + `AppBarLayout` 背景透出，Android 14 及以下保留 legacy `setStatusBarColor()`。同步清理模块对齐问题：菜单改 `MenuProvider`、复用 `ViewModelFactory`、标题/标签格式资源化、概览任务行 XML 化、命名规范修正。`compileDebugJavaWithJavac` 最终 BUILD SUCCESSFUL。

## 会话记录

### 2026-05-28 — 遗留问题梳理 + 文档同步 + 交互修复

- **状态**：完成（未编译验证，被其他改动卡住）。

**文档同步**：
- [modules/tag.md](modules/tag.md)：Widget 标签筛选交互说明统一
- [modules/search-engine.md](modules/search-engine.md)：标签检索分离→已实现
- [modules/app-icon.md](modules/app-icon.md)：第一版图标进度更新

**交互修复**：
- [modules/reminder-delay.md](modules/reminder-delay.md)：通知"开始"有执行中任务时自动完成再开始，修复静默失败

**更新后遗留**：四象限状态栏颜色、Widget 真机验证、数据统计（M6）、Nager.Date API、APP 图标真机遮罩验证

### 2026-05-27 — 四象限任务管理

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — 进度日志

- 设计确认（brainstorming）
- 5 阶段串行实现 OK
- 多轮 UI 修复 OK
- 状态栏颜色遗留 X

### 2026-05-27 — 全局横竖屏锁定

- **状态**：完成。
- **设计文档**：[2026-05-27-screen-orientation-lock-design.md](docs/superpowers/specs/2026-05-27-screen-orientation-lock-design.md)

### 2026-05-27 — 模块文档三段结构重构

- **状态**：完成。
- **内容**：共 18 个模块文档统一为标准三段结构；task_plan.md / findings.md / progress.md 瘦身为引用风格。

### 2026-05-26 — MainActivity nav_graph 全面拆分

- **状态**：完成，`compileDebugJavaWithJavac BUILD SUCCESSFUL`。
- **内容**：MainActivity 导航图全部独立业务线拆为独立 Activity，MainActivity 只保留 MainFragment。
- **改动**：新建 5 Activity 类 + 5 layout + 5 nav_graph；修改 9 个文件。
- **关键决策**：`TaskScheduleActivity` 程序化 `setGraph()` 传参，其余 XML `app:navGraph`。

### 2026-05-26 — 桌面 Widget 实现

> 详见：[modules/widget.md](modules/widget.md) — 进度日志

- **状态**：实现完成，编译通过；标签筛选需真机/桌面 Launcher 复验。
- 4 步分发（F1~F4）+ 测试代理 V1。
- 后续修复：Widget 任务点击改走主界面统一 `resolveAndHandleTaskClick()`；返回栈复用；精确闹钟权限保护；高度 dp 计算修正；空状态占位修正；APP 侧主动刷新经 `DataChangeDispatcher` 解耦；标签点击 per-widget 筛选落地。
- 当前待验证：标签点击筛选、再次点击取消、多 Widget 独立筛选。

### 2026-05-26 — 主界面时间线最大集与生效时段强调

- **状态**：完成，编译通过。
- 时间线保留真实 `periods` 与显示用 `timelinePeriods` 两套输入；生效时段更深更粗强调刻度。

### 2026-05-24 — 安排页 UI 细节调整 + 选择器视觉统一

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

- 切换类型不抖动（minHeight=42dp）、每周 chip 字号统一、保存按钮圆角填充。
- 三种选择器对象 chip 样式按钮、每月月历 Dialog、DAILY 类型不留空白。

### 2026-05-23 — 安排任务模块重设计 + 实现

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

- 设计完成（brainstorming 7 反馈点）。实现覆盖 15 文件：数据层重建 + 槽位视图 + 闹钟调度 + 通知链路。
- 延后：多任务碰撞 DialogActivity。

### 2026-05-24 — 安排模块排查修复（4 Pack）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

- TaskScheduleMatcher 权威实现 + TaskStartGuard 统一校验 + UNIQUE 策略 + v10->v11 DROP+CREATE。
- `TaskScheduleMatcherTest` 34 用例。全量 218 用例 0 失败。

### 2026-05-24 — 安排页槽位视图重做 + 点击拦截 + 主线程 DB 修复

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

10min 粒度 + 6 列 GridLayout + 范围选中。执行中专注任务右侧栏点击拦截。缓存预热修复主线程 Room 崩溃。

### 2026-05-22 — 任务详情页 UI 修复系列

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — 进度日志

- Toolbar 复用 Activity、APP 项视觉对齐、整行点击跳转。
- 底部按钮 Space 均匀分布。
- APP 项跳转后 click handler 顺序修正。

### 2026-05-21 — 专注任务 < 15min 完成引导 & 时间线已完成条按实际耗时

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

ShortCompletionDialog 三按钮 + ChoreHiddenTodayStore。`testDebugUnitTest` 158 用例全绿（+22 新增）。

### 2026-05-21 — 任务详情页样式 + 事件 LiveData 残留修复

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — 进度日志

底部按钮风格统一到对话框规范；三个事件 LiveData 改为 `SingleLiveEvent` 修复返回重复触发。

### 2026-05-21 — APP 操作编辑修复 + 任务点击分流回归修复

> 详见：[modules/task-input.md](modules/task-input.md) — 进度日志

自定义 Filter + 下标错位 + 包可见性。`resolveAndHandleTaskClick` 顶层按 isExecuting 分支。`TimelineTaskState` 重构。

### 2026-05-20 — 精确闹钟权限崩溃修复与统一权限引导

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

PermissionHelper 统一工具。启动时无权限静默跳过。保存安排时无权限弹引导对话框。

### 2026-05-19 — D020 提醒延迟模块实现

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 进度日志

数据层 + 调度层 + 广播层 + UI 层。AlarmReceiver Bug 修复（scheduleId->taskId）。53 个新增测试用例。130 用例 0 失败。

### 2026-05-18 — 代码审查问题修复

- **状态**：完成，编译+测试通过。
- 删除死代码、消除重复查询、TimelineBuilder 抽取、防抖处理。

### 2026-05-18 — 主界面非时段直接开始保护

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

非时段按钮视觉状态补齐、selector 统一色值、安排按钮点击前关闭对话框。

### 2026-05-18 — 任务录入四象限保存崩溃修复

> 详见：[modules/task-input.md](modules/task-input.md) — 进度日志

无标签写 null + loadTaskForEdit 清旧标签 + Robolectric 镜像配置。

### 2026-05-17/18 — 时间段组编辑对话框布局优化 + 统一样式收尾

> 详见：[modules/time-period.md](modules/time-period.md) — 进度日志

日期完整年份、时间行固定时间区、浅蓝编辑块+黑色文字。Dialog/TimeEdit 统一样式。AAPT 样式父级问题记录。

### 2026-05-16 — 节假日数据月度下载频率控制

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 进度日志

架构重构：fetch() 返回 Entity + throws IOException + parser 填 fill()。compileDebugJavaWithJavac + testDebugUnitTest BUILD SUCCESSFUL。

### 2026-05-16 附录 — 时间段 UI 微调

WORKDAY 开关首次启用不弹回；非假日组无预设时段才弹编辑窗；常规组不再等待缓存异步。

### 2026-05-15 — 节假日数据驱动工作日判断 + 无数据兜底 + androidTest 维护

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 进度日志
> 详见：[modules/testing.md](modules/testing.md) — 进度日志

完整业务流程测试（77 用例 0 失败）。无节假日数据兜底逻辑实现。androidTest 源码修正。

### 2026-05-14 — 节假日数据源模块实现

数据源接口 + IcsParser + ChinaGovSource + HolidayCacheManager + HolidaySyncWorker。时段组改造（删新年、增长假）。

### 2026-05-14 — 任务执行链路重构

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

主体代码已落地，Java 编译通过。待真机/模拟器运行与迁移验证。

### 2026-05-15 — 任务执行规则收敛 + 琐碎任务展示规则实现

> 详见：[modules/task-execution.md](modules/task-execution.md) — 进度日志

琐碎任务不进入左侧时间线、不能安排、完成后当天不列入右侧栏。

### 2026-05-15 — 测试策略记录

> 详见：[modules/testing.md](modules/testing.md)

### 2026-05-15 — 时段组编辑功能 & 数据源修复

崩溃修复（HolidayCacheManager 主线程异步化）、编辑对话框、开关逻辑、初始化默认填充。

### 2026-05-14 — 时间段模块地区与互斥规则落地

RegionSettings 主线入口、证券从业地区过滤、春节/新年互斥。

### 2026-05-13 — 主界面底部时段栏

> 详见：[modules/ui-design.md](modules/ui-design.md) — 进度日志

紧凑版实现 -> 非紧凑调整（72dp + 1dp 分隔线 + 独立背景色 + 按钮替代 FAB）。跨天提示文案调整。

### 2026-05-12 — 主界面下一步工作

主界面多标签筛选弹层交互调整：长按预选当前标签、未选禁用确定。

### 2026-05-12 — 左侧栏时间线全面重做

TimelineView + HourColumnView 全面重写：时钟式刻度 + 液体色块 + 指南针浮标。字体规范整理。

### 2026-05-11/12 — 标签模块全线完成 + 项目规范化

> 详见：[modules/tag.md](modules/tag.md) — 进度日志

单/多标签筛选 + 优先标签持久化 + TagManageFragment + UnusedTagFragment。字体规范统一三档。

### 2026-05-08 — 硬编码字符串全面资源化

6 文件 16 处修复。编译 + 26 单元测试全部通过。

### 2026-05-08 — 任务输入流程重构

DB 迁移 2->3 + jieba 分词 + TextTokenizer + 双屏 UI。26 单元测试通过。

### 2026-05-08 — 标签 UI 交互打磨 + 阶段 2-6 完成 + 打磨测试

超链接标签交互、Bug 修复 7 项、资源提取、UI 打磨、死代码清理。

### 2026-05-07 — 需求分析与模块拆分

创建规划文件结构，12 条需求 -> 8 大模块。阶段 1 基础设施搭建完成。

---

## 模块文档进度总览

| 模块 | 文档 | 状态 |
|------|------|------|
| 检索引擎 | [modules/search-engine.md](modules/search-engine.md) | 已打磨 |
| 智能展示引擎 | [modules/smart-display.md](modules/smart-display.md) | 已打磨 |
| 时间段计算 | [modules/time-period.md](modules/time-period.md) | 已实现 |
| 剩余时间计算 | [modules/time-remaining.md](modules/time-remaining.md) | 已实现 |
| 节假日数据 | [modules/holiday-data.md](modules/holiday-data.md) | 已实现 |
| 桌面 Widget | [modules/widget.md](modules/widget.md) | 已实现 |
| 数据统计 | [modules/stats.md](modules/stats.md) | 待开始 |
| 任务执行 | [modules/task-execution.md](modules/task-execution.md) | 已实现 |
| 提醒延迟 | [modules/reminder-delay.md](modules/reminder-delay.md) | 已完成 |
| 提醒详情页 | [modules/reminder-detail.md](modules/reminder-detail.md) | 已完成 |
| 标签 | [modules/tag.md](modules/tag.md) | 已打磨 |
| 任务编辑页 | [modules/task-edit.md](modules/task-edit.md) | 已完成 |
| 任务录入 | [modules/task-input.md](modules/task-input.md) | 已完成 |
| 四象限任务管理 | [modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) | 已完成 |
| 四象限降级恢复 | [modules/quadrant-degrade.md](modules/quadrant-degrade.md) | 设计中 |
| UI 设计 | [modules/ui-design.md](modules/ui-design.md) | 已打磨 |
| APP 图标 | [modules/app-icon.md](modules/app-icon.md) | 待开始 |
| 品牌体系 | [modules/brand.md](modules/brand.md) | 纯规范 |
| 测试策略 | [modules/testing.md](modules/testing.md) | 已记录 |
