# 四象限降级恢复 设计文档

## 目标

高频周期任务完成后自动降级，避免反复占据推荐引擎顶部。降级后按周期恢复原象限，不做催促。

## 数据模型

### tasks 表加字段

`degrade_period` INTEGER DEFAULT 0

| 值 | 含义 | 恢复时间 |
|----|------|----------|
| 0 | 不降级 | — |
| 1 | 次日恢复 | 明天 00:00 |
| 2 | 下周恢复 | 下周一 00:00 |
| 3 | 下月恢复 | 下月 1 日 00:00 |

### 新表 `task_quadrant_degrade`

| 字段 | 类型 | 说明 |
|------|------|------|
| `task_id` | INTEGER, PK | 关联 tasks.id |
| `original_quadrant` | INTEGER | 降级前象限值 (0-3) |
| `recover_ms` | INTEGER | 恢复时间戳（epoch millis），到期自清理 |

- 仅降级中任务有行，恢复后行删除
- 无外键约束，清理时顺带删孤儿行

## 交互

### 象限选择页 (QuadrantFragment)

草稿摘要下方、2×2 象限网格上方，新增一行 chip：

```
[次日] [下周] [下月] [不降级]
```

- 默认选中"次日"
- chip 样式：复用安排页选择器 chip 样式（浅蓝/蓝色实底 8dp 圆角，`@style/TextAppearance.JustNow.Body`）
- 选中值通过 ViewModel 透传，写入 `tasks.degrade_period`

### 任务完成

执行完成 → 读 `tasks.degrade_period`：
- 0（不降级）→ 不操作降级表
- > 0 → 写 `task_quadrant_degrade`（`original_quadrant`=当前象限, `recover_ms`=计算所得恢复时间），无视已有降级记录（覆盖）

**恢复时间计算**（Java `Calendar` 计算次日/下周一/下月1日 00:00:00.000）

### recompute

`DisplayEngine.buildSortedItems`：
1. 查 `task_quadrant_degrade` 表（一次查询所有降级记录，Map<taskId, DegradeRecord>）
2. 对于有降级记录的任务：
   - `now < recover_ms` → 象限权重按 `Math.max(0, originalQuadrant - 1)`（降级后象限：0→1, 1→2, 2→3, 3→3）
   - `now >= recover_ms` → 删降级记录，使用 `tasks.quadrant`（已恢复原象限）
3. 降级中任务的象限色条显示降级后颜色

## 生命周期

| 事件 | 操作 |
|------|------|
| 任务完成 | 若 `degrade_period > 0`，写降级记录 |
| 恢复到期 | recompute 时自然检测 + 删行 |
| 编辑任务象限变更 | 删旧降级记录（旧象限已无效） |
| 编辑任务不换象限 | 保留降级记录 |
| 任务删除/归档 | 删降级记录（DAO 内联清理） |
| 象限 3 任务降级 | 保持在 3，仍写降级记录（维持恢复周期） |

## 文件变更

```
data/entity/
└── TaskQuadrantDegradeEntity.java    — 新增：taskId + originalQuadrant + recoverMs

data/dao/
└── TaskQuadrantDegradeDao.java       — 新增：insert / deleteByTaskId / queryAll / deleteByTaskIds
└── TaskDao.java                      — 修改：delete 时同步删降级记录

data/db/
└── AppDatabase.java                  — 修改：DB version +1, migrate, 注册新 DAO

data/repository/
└── TaskRepository.java               — 修改：完成后写降级记录；delete/archive 时清理

ui/quadrant/
└── QuadrantFragment.java             — 修改：顶部加 chip 行，保存时写 degradePeriod
└── QuadrantFragment (layout)         — 修改：chip 行 UI

ui/taskinput/
└── TaskInputViewModel.java           — 修改：保存象限时处理降级记录清理

ui/engine/
└── DisplayEngine.java                — 修改：buildSortedItems 读降级表调整权重
```

## 边界

- 降级周期与 schedule 无关：无安排的任务也可以降级（安排和降级是两个独立维度）
- 不降级（degrade_period=0）的任务完成后不写降级表
- 同一任务多次完成，每次覆盖降级记录（不累积）

## 测试

1. **降级记录到期自动删除**：DAO 层单元测试，构造过期/未过期时间戳，验证 `insert` + `queryAll` + `deleteExpired`
2. **DisplayEngine 降级权重**：构造降级记录，验证未到期→权重 +1000、已到期→恢复原象限
3. **象限变更清降级**：Repository 层验证
