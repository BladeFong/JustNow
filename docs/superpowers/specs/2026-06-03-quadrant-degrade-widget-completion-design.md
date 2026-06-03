# 四象限降级恢复补齐设计

## 背景

2026-06-03 v2 追加审查指出三条降级链路问题。复核后确认：

- 手动完成任务未写降级记录：属实，需要修复。
- 四象限概览按原始象限分组：设计如此，不作为缺陷。四象限任务管理模块的目的在管理，继续按任务原始象限归类。
- Widget 未接入降级：属实，需要修复。Widget 的任务排序和色标应与主界面右侧栏一致。

## 目标

1. 所有“正常完成”路径统一写入降级记录，避免手动完成和自动完成行为不一致。
2. 主界面右侧栏和 Widget 使用同一套有效象限逻辑，降级中任务按降级后象限排序并显示降级后色标。
3. 保持四象限任务管理模块按原始象限管理，不引入降级分组。

## 非目标

- 不修改 `DisplayEngine.computeByQuadrant()` 的语义。
- 不让四象限概览或单象限管理页按降级后象限迁移任务。
- 不新增数据库表或迁移。

## 方案

### 完成链路

`TaskExecutionAutoCompleter.completeRunningTaskSync()` 已包含完整正常完成副作用：

1. 写 `task_executions`
2. 清除执行状态
3. 当 `task.degradePeriod > 0` 时写 `task_quadrant_degrade`

手动完成路径 `BaseTaskViewModel.completeRunningTaskSync()` 不再复制完成逻辑，改为委托该共享方法。这样主界面、详情页和自动补偿完成都通过同一核心完成逻辑。

短完成路径仍保持现有语义：不写执行记录，不触发降级。

### 有效象限

在 `DisplayItem` 增加 `effectiveQuadrant` 字段：

- 默认值为 `task.quadrant`
- 当存在未过期降级记录时，值为 `min(3, originalQuadrant + 1)`
- `DisplayEngine.compute(..., degradeMap)` 在计算排序权重时同步设置该字段

该字段用于展示层读取色标，避免主界面和 Widget 重复计算降级状态。

### 主界面右侧栏

`TaskAdapter` 的色标从 `item.task.quadrant` 改为 `item.effectiveQuadrant`。排序已经由 `DisplayEngine.compute(..., degradeMap)` 处理，因此主界面右侧栏的排序和色标都来自同一有效象限。

### Widget

`WidgetUpdateHelper.updateWidget()` 在读取任务后同步读取 `taskRepo.getNonExpiredDegradeMapSync()`，并传给 `computeItems()`。

`WidgetUpdateHelper.computeItems()` 增加接收 `degradeMap` 的重载或参数，内部调用 `DisplayEngine.compute(..., priorityTagIds, degradeMap)`。Widget 当前没有优先标签配置时传空集合即可。

`WidgetUpdateHelper.buildTaskRow()` 的色标从 `item.task.quadrant` 改为 `item.effectiveQuadrant`，与主界面右侧栏一致。

## 错误处理

- `degradeMap` 可为 `null`；为 `null` 时全部任务使用原始象限。
- `effectiveQuadrant` 展示前做 `0..3` 边界保护，避免异常数据导致数组越界。
- Widget 更新流程保留现有 `try/catch` 和 fallback 展示；降级读取失败时进入现有异常兜底。

## 测试

1. `BaseTaskViewModel` 正常手动完成：任务有 `degradePeriod` 时写入降级记录。
2. `DisplayEngine` 降级排序：返回的 `DisplayItem.effectiveQuadrant` 为降级后象限。
3. `TaskAdapter` 色标读取 `effectiveQuadrant` 的逻辑通过可测辅助方法或最小单元测试覆盖。
4. `WidgetUpdateHelper.computeItems()`：传入降级记录后排序与主界面右侧栏一致。
5. Widget 色标逻辑读取 `effectiveQuadrant`，不再直接使用 `task.quadrant`。

## 文档处置

`docs/code-review-20260603-v2.md` 保留原审查记录。后续实现完成后，在模块文档记录处理结果：

- #1 标记为修复。
- #2 标记为设计如此，不修复。
- #3 标记为修复。
