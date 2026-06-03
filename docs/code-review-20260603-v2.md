# 代码审查报告 2026-06-03 v2

## 审查范围

整个当前 Android APP 项目（`app/src/main`、关键测试、项目文档与既有审查报告）。

## 审查时间

2026-06-03

## 结果概要

本轮为 2026-06-03 追加审查 v2，未覆盖已有 `docs/code-review-20260603.md`。已读取 `docs/code-review-ignore.md`，跳过已确认不处理/误报项；已对照旧报告，以下均为本轮新发现或旧项未实际闭环的回归风险。

发现问题 3 个：`important` 3 个。集中在四象限降级恢复链路，影响手动完成、四象限概览和 Widget 展示一致性。

## [important] 逻辑问题

- [x] ### #1 手动完成任务不会写入降级记录
- **文件**: `app/src/main/java/com/nearby/justnow/ui/base/BaseTaskViewModel.java:36`
- **问题**: `BaseTaskViewModel.completeRunningTaskSync()` 只写 `task_executions` 并清除执行状态，没有在 `task.degradePeriod > 0` 时写 `task_quadrant_degrade`。而自动完成路径 `TaskExecutionAutoCompleter.completeRunningTaskSync()` 会在 `TaskExecutionAutoCompleter.java:70` 写降级记录。设计文档明确“任务完成”应按 `degrade_period` 写降级记录，且降级与 schedule 无关。因此用户从主界面/详情页手动完成周期任务时，任务不会降级，仍停留在原象限顶部。
- **建议**: 将“写执行记录 + 清执行状态 + 按 `degradePeriod` 写降级记录”的核心逻辑收口到一个共享方法，或让 `BaseTaskViewModel.completeRunningTaskSync()` 委托 `TaskExecutionAutoCompleter.completeRunningTaskSync()`，并补充手动完成降级单元测试。
- **修复结果**: 已修复。
- **审核意见**: `BaseTaskViewModel.completeRunningTaskSync()` 已委托 `TaskExecutionAutoCompleter.completeRunningTaskSync()`，手动完成与自动完成共用同一降级写入逻辑；`BaseTaskViewModelSyncTest.completeRunningTaskSync_withDegradePeriod_writesDegradeRecord()` 覆盖了手动完成写降级记录。

- [x] ### #2 四象限概览仍按原始象限分组，未应用降级后象限
- **文件**: `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java:431`
- **问题**: `prepareComputeContext()` 已读取 `ctx.degradeMap`，但 `computeQuadrantOverviewSync()` 调用 `DisplayEngine.computeByQuadrant()` 时没有传入该 map。`DisplayEngine.computeByQuadrant()` 当前签名也不接收 `degradeMap`，并在 `DisplayEngine.java:195` 直接用 `t.quadrant == quadrant` 分组。结果是主界面推荐列表会按降级后权重排序，但四象限概览/单象限入口仍把降级中的任务放回原象限。此问题与 `docs/code-review-20260603.md` #13 的关闭状态冲突，属于修复未真正覆盖四象限分组行为。
- **建议**: 给 `computeByQuadrant()` 增加 `degradeMap` 参数，分组时计算 `effectiveQuadrant = min(3, originalQuadrant + 1)`；`MainViewModel.computeQuadrantOverviewSync()` 传入 `ctx.degradeMap`。补充测试覆盖“降级中 Q0 任务出现在 Q1 结果、不出现在 Q0 结果”。
- **修复结果**: 设计如此，已关闭。
- **审核意见**: 用户已确认“四象限任务管理模块的目的在管理”，因此四象限概览/单象限管理页应按原始象限分组，不应用降级后象限。该项按正当设计关闭，已记录到 `docs/code-review-ignore.md`。

- [x] ### #3 Widget 智能展示没有读取降级记录
- **文件**: `app/src/main/java/com/nearby/justnow/widget/WidgetUpdateHelper.java:155`
- **问题**: Widget 更新时读取任务、时段和标签后调用 `computeItems(tasks, tagMap, status, maxItems)`，该方法在 `WidgetUpdateHelper.java:404` 只调用 `DisplayEngine.compute(..., maxItems)` 的无 `degradeMap` 重载。因此 Widget 完全忽略 `task_quadrant_degrade`，降级中的任务仍按原象限排序；同时 `buildTaskRow()` 在 `WidgetUpdateHelper.java:257` 也直接用 `task.quadrant` 渲染色标，Widget 与 APP 主列表展示不一致。
- **建议**: Widget 更新路径读取 `taskRepo.getNonExpiredDegradeMapSync()`，传入 DisplayEngine 的降级重载；如产品要求 Widget 色标体现降级后象限，也同步用 effective quadrant 渲染。补充 `WidgetUpdateHelper.computeItems` 降级排序测试。
- **修复结果**: 已修复。
- **审核意见**: `WidgetUpdateHelper.updateWidget()` 已读取 `taskRepo.getNonExpiredDegradeMapSync()` 并传入 `computeItems()`；`computeItems()` 已调用带 `degradeMap` 的 `DisplayEngine.compute()`；`buildTaskRow()` 已用 `DisplayItem.effectiveQuadrant` 渲染色标。`WidgetUpdateHelperTest.computeItems_degradeMap_passedToEngine()` 覆盖了 Widget 降级排序路径。

## 待确认问题

无。

## 测试建议

- 增加 `BaseTaskViewModel.completeRunningTaskSync()` 或共享完成服务的降级写入测试。
- 增加 `DisplayEngine.computeByQuadrant()` 降级分组测试。
- 增加 `WidgetUpdateHelper.computeItems()` 降级排序测试。

## 本轮未执行

未运行编译或测试。本轮为只读审查 + 文档记录。

## 修复审核 2026-06-03

### 验证结果

- `~/gradlew-wsl.sh --no-daemon testDebugUnitTest`：BUILD SUCCESSFUL。

### 未处理项汇总

无。

### 不处理项原因

#2：用户已确认设计如此。四象限任务管理模块的目的在管理，四象限概览/单象限管理页按原始象限分组，不应用降级后象限；已记录到 `docs/code-review-ignore.md`。
