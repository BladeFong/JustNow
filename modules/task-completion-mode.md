# 任务完成模式模块

> 对应 task_plan.md M10
> 替代 [quadrant-degrade.md](quadrant-degrade.md)

# 阶段规划、决策记录

## 定位和功能描述

废弃四象限降级恢复策略，改为任务完成模式。每个任务完成一次当天即隐藏、次日 00:00 重新出现；周/月/年模式叠加周期配额，配额满后当周期不再显示。四象限分类框架保持不变，只移除"完成一次降一级"的动态变化。

显隐范围：主界面右侧栏 + Widget 生效；四象限概览和单象限管理不受影响。

## 整体规划和决策

> 设计文档：[docs/superpowers/specs/2026-07-10-task-completion-mode-design.md](../docs/superpowers/specs/2026-07-10-task-completion-mode-design.md)
> 实现计划：[docs/superpowers/plans/2026-07-10-task-completion-mode.md](../docs/superpowers/plans/2026-07-10-task-completion-mode.md)

- [ ] 数据层：Entity + DAO + DB 迁移 6→7
- [ ] TaskRepository：移除降级，接入计数器
- [ ] 完成流程：计数器替代降级记录写入
- [ ] TaskFilterHelper：完成模式可见性判定替代降级过滤
- [ ] DisplayEngine：移除降级权重参数链
- [ ] UI：QuadrantFragment 完成模式 chip 行
- [ ] TaskInputViewModel：degradePeriod → completionMode + quota
- [ ] 详情页底部趋势图
- [ ] Widget 过滤一致性
- [ ] 清理与收尾
- [ ] 测试

### 数据模型

**`tasks` 表变更**：

| 操作 | 字段 | 类型 | 默认 |
|------|------|------|------|
| 删除 | `degrade_period` | — | — |
| 新增 | `completion_mode` | INTEGER | 0 |
| 新增 | `quota` | INTEGER | 1 |

**completionMode 枚举**：

| 值 | 模式 | 配额范围 |
|----|------|:---:|
| 0 | 每天 | 1（固定） |
| 1 | 每周 | 1~6 |
| 2 | 每月 | 1~27 |
| 3 | 每年 | 1~11 |

**新表 `task_completion_counter`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | LONG (PK) | 关联 tasks.id |
| `period_key` | TEXT (PK) | 周期标识 |
| `completed` | INT | 当前周期已完成次数 |

- 联合主键 `(task_id, period_key)`
- period_key 格式：周 `yyyy-Www`、月 `yyyy-MM`、年 `yyyy`
- 日模式不写此表

**删除**：
- `task_quadrant_degrade` 表
- `TaskQuadrantDegradeEntity`
- `TaskQuadrantDegradeDao`

### 引擎逻辑

```
1. 查 task_executions，今天有完成记录 → 进入下一步（日模式直接隐藏）
2. 周/月/年模式：查 task_completion_counter，period_key 匹配：
   - completed >= quota → 隐藏
   - 否则 → 显示
```

### 生命周期

| 事件 | 操作 |
|------|------|
| 任务完成（含短完成） | 事务内写 task_executions + completed += 1 |
| 新周期开始 | recompute 匹配新 period_key |
| 编辑任务改模式/配额 | 计数器保留；即时生效 |
| 减配额 ≤ 已完成 | 判定已达配额，当天隐藏 |
| 任务删除/归档 | 删计数器记录 |

### 文件结构

```
data/entity/
├── TaskEntity.java                     — 修改：degradePeriod → completionMode + quota
├── TaskCompletionCounterEntity.java    — 新增
└── TaskQuadrantDegradeEntity.java      — 删除

data/dao/
├── TaskCompletionCounterDao.java       — 新增
├── TaskQuadrantDegradeDao.java         — 删除
└── TaskDao.java                        — 修改：删除 degrade 清理

data/db/
└── AppDatabase.java                    — 修改：v6→v7

data/repository/
└── TaskRepository.java                 — 修改：移除降解+接入计数器
└── TaskExecutionAutoCompleter.java     — 修改：写计数器

ui/base/
└── TaskFilterHelper.java               — 修改：完成模式过滤

ui/engine/
├── DisplayEngine.java                  — 修改：移除降解权重
└── DisplayItem.java                    — 修改：effectiveQuadrant 退化

ui/quadrant/
└── QuadrantFragment.java               — 修改：完成模式 chip 行

ui/taskinput/
└── TaskInputViewModel.java             — 修改：degradePeriod → completionMode

ui/detail/
└── ReminderDetailActivity.java         — 修改：趋势图

ui/trendchart/
└── TrendChartView.java                 — 新增：折线图

widget/
└── WidgetUpdateHelper.java             — 修改：移除 degradeMap
```

# 研究发现、技术决策

### 降级 vs 完成模式（2026-07-10）

旧降级策略完成一次降一级象限，语义不直观——用户难以理解"为什么这个任务颜色变了"。四象限是分类框架，降级混淆了"分类"与"展示频次"两个独立关注点。用户自然需求是"今天做过就不用再提醒"，即日完成即消失。

### 日模式不写计数器（2026-07-10）

日模式只走 `task_executions` 当天记录判定，不需要写 `task_completion_counter`。这减少了不必要的写入，且日模式没有"周期累计"概念。

### 配额 ≤ 已完成即时判定（2026-07-10）

编辑任务减配额到已完成次数以下时，新周期判定即时生效——下次 recompute 即隐藏。不需要额外清理计数器的操作。

### 显隐分离（2026-07-10）

主界面右侧栏 + Widget 跟随显隐规则，四象限概览和单象限管理始终显示全部任务——管理视图不受完成模式影响。这一分离保证了任务管理功能的完整性。

### 趋势图设计（2026-07-10）

详情页底部固定不随 ScrollView 滚动的折线图，最近 10 个周期完成率。Y 轴 5 档（0%/25%/50%/75%/100%），0% 线为浅色实线+刻度，其他为浅色横虚线。圆点连线，风格类似基金业绩走势。

### 修复周期配额任务当天完成过仍显示在任务列表的逻辑（2026-07-30）

- **现象**：周/月/年周期的配额任务，在当天被完成过一次后，只要总配额未满，当天依然显示在主界面右侧栏和 Widget 任务列表中。
- **根因**：`TaskFilterHelper.filterDisplayableTasks` 中仅在 `completionMode == 0`（日模式）下检查了 `todayCompletedIds.contains(task.id)`；周/月/年模式直接走到了 `else` 仅判断 `completed >= quota`。
- **修复**：对所有模式一律先检查 `todayCompletedIds.contains(task.id)`，只要当天完成过即在当天的待办任务列表中隐藏；若当天未完成过，再判断周/月/年模式是否达到总配额。
