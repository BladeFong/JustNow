# 四象限降级恢复模块（已废弃 → 任务完成模式）

> **2026-07-10 已废弃**，由 [task-completion-mode.md](task-completion-mode.md) 替代。保留本文档作为历史记录。
>
> 新设计文档：[docs/superpowers/specs/2026-07-10-task-completion-mode-design.md](../docs/superpowers/specs/2026-07-10-task-completion-mode-design.md)
> 新实现计划：[docs/superpowers/plans/2026-07-10-task-completion-mode.md](../docs/superpowers/plans/2026-07-10-task-completion-mode.md)

> 对应 task_plan.md M10

# 阶段规划、决策记录

## 定位和功能描述

高频周期任务完成后自动降一级象限（0→1, 1→2, 2→3, 3→保持），按次日/下周/下月恢复原象限，避免反复占据推荐引擎顶部。

降级周期与闹钟安排独立：无安排的任务也可以降级恢复。

## 整体规划和决策

> 设计文档：[docs/superpowers/specs/2026-05-29-quadrant-degrade-design.md](../docs/superpowers/specs/2026-05-29-quadrant-degrade-design.md)
> 补齐设计：[docs/superpowers/specs/2026-06-03-quadrant-degrade-widget-completion-design.md](../docs/superpowers/specs/2026-06-03-quadrant-degrade-widget-completion-design.md)
> 追加审查报告：[docs/code-review-20260603-v2.md](../docs/code-review-20260603-v2.md)

- [x] DB 迁移：`tasks` 表加 `degrade_period` DEFAULT 0；新建 `task_quadrant_degrade` 表
- [x] Entity + DAO：`TaskQuadrantDegradeEntity` + `TaskQuadrantDegradeDao`
- [x] `AppDatabase` 升级：版本号 +1，migration，注册新 DAO
- [x] `TaskRepository`：完成后写降级记录；delete/archive 时清理
- [x] `QuadrantFragment`：顶部 chip 行 [次日] [下周] [下月] [不降级]，默认次日
- [x] `TaskInputViewModel`：保存象限时处理降级记录清理（象限变更则删）
- [x] `DisplayEngine.buildSortedItems`：读降级表，未到期降权 +1000，到期自删
- [x] 降级记录到期自动删除（recompute 时自然触发）
- [x] `TaskExecutionAutoCompleter.completeRunningTaskSync`：完成后写降级记录
- [x] 单元测试：降级 DAO（5 用例）+ DisplayEngine 降权计算（4 用例）
- [x] compileDebugJavaWithJavac 编译通过

### 数据模型

**`tasks` 表**：新增 `degrade_period` INTEGER DEFAULT 0

| 值 | 含义 | 恢复时间 |
|----|------|----------|
| 0 | 不降级 | — |
| 1 | 次日恢复 | 明天 00:00 |
| 2 | 下周恢复 | 下周一 00:00 |
| 3 | 下月恢复 | 下月 1 日 00:00 |

**新表 `task_quadrant_degrade`**

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | INTEGER PK | 关联 tasks.id |
| `original_quadrant` | INTEGER | 降级前象限值 (0-3) |
| `recover_ms` | INTEGER | 恢复时间戳（epoch millis） |

- 仅降级中任务有行，恢复后行删除
- 同一任务多次完成，每次覆盖降级记录

### 生命周期

| 事件 | 操作 |
|------|------|
| 任务完成 | 若 `degrade_period > 0`，写降级记录 |
| 恢复到期 | recompute 时自然检测 + 删行 |
| 编辑任务象限变更 | 删旧降级记录 |
| 编辑任务不换象限 | 保留降级记录 |
| 任务删除/归档 | 删降级记录（DAO 内联清理） |
| 象限 3 任务降级 | 保持在 3，仍写降级记录 |

### 文件结构

```
data/entity/
└── TaskQuadrantDegradeEntity.java    — 新增

data/dao/
└── TaskQuadrantDegradeDao.java       — 新增
└── TaskDao.java                      — 修改：delete 时清理降级记录

data/db/
└── AppDatabase.java                  — 修改：DB version +1, migration

data/repository/
└── TaskRepository.java               — 修改：完成/delete/archive 联动

ui/quadrant/
├── QuadrantFragment.java             — 修改：chip 行
└── fragment_quadrant.xml             — 修改：chip 行布局

ui/taskinput/
└── TaskInputViewModel.java           — 修改：degradePeriod 持久化 + 清理

ui/engine/
└── DisplayEngine.java                — 修改：读降级表调整权重
```

# 研究发现、技术决策

### 降级与 schedule 的关系（2026-05-29）

降级恢复周期和闹钟安排是两个独立维度。降级恢复周期放象限选择页，不碰安排页——无安排的任务也可以降级。

### 降级表 vs tasks 表字段（2026-05-29）

降级周期（`degrade_period`）是任务持久属性，放 `tasks` 表。降级临时状态（`task_quadrant_degrade`）是瞬态数据，自清理，独立表。

### 2026-06-03 v2 追加审查

追加审查发现降级恢复链路仍有未覆盖路径。复核后确认：手动完成任务未写降级记录属实；四象限概览按原始象限分组为设计如此，四象限任务管理模块用于管理；Widget 未读取降级记录属实。已补齐手动完成写降级记录、有效象限字段、主界面右侧栏和 Widget 色标一致性，以及 Widget 降级排序接入。
