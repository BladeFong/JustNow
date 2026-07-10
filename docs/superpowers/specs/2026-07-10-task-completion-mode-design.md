# 任务完成模式设计文档

## 目标

废弃四象限降级恢复策略，替换为任务完成模式（日/周/月/年配额）。每个任务完成一次当天即隐藏，次日重新出现；周/月/年模式叠加周期配额，配额满后当周期不再显示。

## 第一节：核心规则

### 基础规则（所有任务）

> 任务完成 → 当天不再显示 → 次日 00:00 重新出现

这是不可逾越的底层逻辑。

### 四种模式

| 模式 | `completionMode` | 配额 | 消失规则 |
|------|:---:|:---:|---|
| 每天 | 0 | 1（固定） | 当天完成即消失 |
| 每周 | 1 | 用户设 N（1~6） | 单日完成当天消失；本周累计完成 N 次后整周消失，下周一 00:00 重置 |
| 每月 | 2 | 用户设 N（1~27） | 同上，下月 1 日 00:00 重置 |
| 每年 | 3 | 用户设 N（1~11） | 同上，下年 1 月 1 日 00:00 重置 |

配额必须严格小于周期天数，否则应直接用日模式。

### 显隐范围

| 视图 | 受显隐影响？ |
|---|---|
| 主界面右侧栏 | ✅ 隐藏已完成/满配额任务 |
| Widget | ✅ 同上 |
| 四象限概览 | ❌ 始终显示 |
| 单象限管理 | ❌ 始终显示 |

---

## 第二节：数据模型

### TaskEntity 变更

移除 `degradePeriod`，新增：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `completionMode` | `int` | 0 | 0=日 1=周 2=月 3=年 |
| `quota` | `int` | 1 | 周/月/年的配额，日模式固定为 1 |

### 新表 `task_completion_counter`

| 列 | 类型 | 说明 |
|---|---|---|
| `task_id` | `LONG` (PK) | 关联 tasks.id |
| `period_key` | `TEXT` (PK) | 周期标识：`2026-W28` / `2026-07` / `2026` |
| `completed` | `INT` | 当前周期已累计完成次数 |

- 联合主键 `(task_id, period_key)`，`OnConflictStrategy.REPLACE`
- 跨周期自然新起记录

### period_key 计算

| 模式 | 格式 | 示例 |
|---|---|---|
| 日 | 不需要计数器 | — |
| 周 | `yyyy-Www` | `2026-W28` |
| 月 | `yyyy-MM` | `2026-07` |
| 年 | `yyyy` | `2026` |

日模式不需要计数器——"完成当天消失"由 `task_executions` 当天记录判定即可。

### 移除

- `task_quadrant_degrade` 表
- `TaskQuadrantDegradeEntity`
- `TaskQuadrantDegradeDao`
- 所有降级相关逻辑

---

## 第三节：引擎与过滤逻辑

### 判定"是否隐藏"

recompute 时对每个活跃任务：

```
1. 查 task_executions，今天有完成记录 → 进入下一步（日模式直接判定隐藏）
2. 周/月/年模式：查 task_completion_counter，period_key 匹配当前周期：
   - 无记录 → 不隐藏
   - completed >= task.quota → 隐藏
   - 否则 → 不隐藏
```

日模式：只走第 1 步，不查计数器表。

周/月/年模式：第 1 步 + 第 2 步同时成立才隐藏（今天完成过 **且** 周期累计已达配额）。

### TaskFilterHelper 变更

`computeFilteredTasks()` 中替换降级逻辑为上述判定，对主界面右侧栏和 Widget 生效。

移除：
- `getNonExpiredDegradeMapSync()`
- DisplayEngine 中的 degrade 权重计算

### DisplayEngine 变更

`buildDisplayItem()` 中 `effectiveQuadrant` 直接等于 `task.quadrant`（保留字段但不做降级变换）。清理所有 degrade 相关参数。

### completed 写入

每次任务完成（含短完成）→ `task_completion_counter` 对应 `period_key` 行 `completed += 1`，与写 `task_executions` 同时在事务内完成。

---

## 第四节：UI 变更

### QuadrantFragment 完成模式 chip 行

替代旧降级 chip 行：

```
[每天] [每周] [每月] [每年]  [  配额: ___ ] (最大 N)
```

- 默认选中"每天"，配额 EditText 和提示隐藏
- 选中周/月/年时，右侧出现 EditText（默认 1）和提示文字
- 提示：每周 `(最大 6)`、每月 `(最大 27)`、每年 `(最大 11)`
- 超出限制 Toast 提示
- chip 样式沿用现有降级 chip 样式

### 数据流

```
QuadrantFragment chip 行
  → TaskInputViewModel.setCompletionMode(mode)
  → TaskInputViewModel.setQuota(quota)
  → saveTask() 写入 TaskEntity
```

### 移除

- 旧降级 chip 行 UI 和逻辑
- `degradePeriod` 相关 ViewModel 字段

---

## 第五节：生命周期与边界

### 生命周期

| 事件 | 操作 |
|---|---|
| 任务完成（含短完成） | 事务内写 `task_executions` + `completed += 1` |
| 新周期开始 | recompute 自然匹配新 `period_key`，旧记录保留不删 |
| 编辑任务改模式/配额 | 计数器保留；周期配额即时生效（下次 recompute 用新值判定） |
| 编辑任务减配额 ≤ 已完成 | 判定为已达配额，当天消失 |
| 任务删除/归档 | 删计数器记录（DAO 内联清理） |

### 边界

- 日模式不写计数器表
- 计数器表不主动清理旧周期记录（自然积累）
- 配额变更不重置已计数，也不重置单日隐藏规则

### 数据库迁移

- `tasks` 表：删 `degrade_period`，加 `completion_mode` + `quota`
- `task_quadrant_degrade` 表：DROP
- 新建 `task_completion_counter` 表
- DB version +1

---

## 第六节：测试

| # | 测试点 | 类型 |
|---|------|------|
| 1 | 日模式任务完成后当天隐藏，次日重新出现 | DAO + Engine |
| 2 | 周模式配额 N，本周完成 N 次后整周隐藏，下周一重置 | DAO + Engine |
| 3 | 月模式同理 | DAO |
| 4 | 年模式同理 | DAO |
| 5 | 短完成（<15min）计入完成配额 | Repository |
| 6 | 编辑任务减配额后即时判定已达配额 | Engine |
| 7 | 跨周期 `period_key` 自动切换新记录 | DAO |
| 8 | 主界面右侧栏隐藏，管理视图不隐藏 | Engine/ViewModel |
| 9 | Widget 隐藏逻辑与主界面一致 | WidgetUpdateHelper |
| 10 | 数据库迁移：旧 degrade 数据清理 + 新表创建 | Migration |

---

## 第七节：任务详情底部趋势图

### 显示条件

- 任务设了周/月/年配额（日模式不显示）
- 至少存在 2 个周期的记录（仅当前周期不显示图表）

### 图表规格

- **类型**：圆点连线折线图（类似基金业绩走势）
- **数据**：最近 10 个周期，每个周期的完成率 = `completed / quota`
- **布局**：各周期水平均分空间，纯图表，底部不标注周期名
- **位置**：详情页底部，与按钮栏同层，固定不随 ScrollView 滚动
- **Y 轴**：5 档（0%、25%、50%、75%、100%）
  - 0% 线：浅色实线 + 底部竖线刻度（坐标轴基线）
  - 其他 4 档：4 条浅色横虚线
- **数据点**：每周期完成率对应圆点，相邻点之间直线连接

### 数据来源

查询 `task_completion_counter` 表 `task_id` 匹配、按 `period_key DESC` 排序、LIMIT 10。

---

## 文件变更

```
data/entity/
├── TaskEntity.java                     — 修改：degradePeriod → completionMode + quota
├── TaskCompletionCounterEntity.java    — 新增：taskId + periodKey + completed
└── TaskQuadrantDegradeEntity.java      — 删除

data/dao/
├── TaskCompletionCounterDao.java       — 新增：insertOrIncrement / queryByTaskId / deleteByTaskId
├── TaskQuadrantDegradeDao.java         — 删除
└── TaskDao.java                        — 修改：删除 degrade 清理逻辑

data/db/
└── AppDatabase.java                    — 修改：DB version +1, migrate, 注册新 DAO

data/repository/
└── TaskRepository.java                 — 修改：完成时写计数器；删除 degrade 逻辑

ui/quadrant/
└── QuadrantFragment.java               — 修改：降级 chip 行 → 完成模式 chip 行

ui/taskinput/
└── TaskInputViewModel.java             — 修改：degradePeriod → completionMode + quota

ui/engine/
└── DisplayEngine.java                  — 修改：移除 degrade 权重；effectiveQuadrant 退化
└── DisplayItem.java                    — 修改：effectiveQuadrant 不再依赖降级

ui/base/
└── TaskFilterHelper.java               — 修改：替换降级过滤为完成模式过滤

ui/detail/
└── TaskDetailFragment.java             — 修改：底部新增趋势图（周/月/年模式）

app/src/main/res/layout/
└── (相关布局文件)                       — 修改：chip 行 + 趋势图
```
