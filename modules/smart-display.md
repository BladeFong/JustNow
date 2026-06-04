# 智能展示引擎

> 对应 task_plan.md M4

# 阶段规划、决策记录

## 定位和功能描述

根据四象限、时间段、专注时长三个维度，对未归档任务进行排序输出。Widget 和 APP 主界面复用同一引擎。

## 整体规划和决策

### 排序规则

所有任务先按剩余时间能否容纳分两组（A组=能容纳，含超出≤15分钟容差；B组=不能容纳）。A组整体在前，B组在后。组内排序如下：

| 优先级 | 规则 | 说明 |
|--------|------|------|
| 1 | 标签优先 | 匹配当前优先标签的任务排前 |
| 2 | 四象限级别 | 日间：紧急重要 > 紧急不重要 > 不紧急重要 > 不紧急不重要 |
| 2' | 四象限级别（反转） | 晚上时段：不紧急不重要 > 不紧急重要 > 紧急不重要 > 紧急重要 |
| 3 | 专注时长微调 | 同优先级内专注时长从长到短 |

### 比例截取（QuadrantRatioFilter）

4:2:2:1 比例迭代回填：
- A组总数 ≤ 容纳数 → A组全收，剩余位置在B组按比例挑选
- A组总数 > 容纳数 → 仅A组按比例挑选，不碰B组
- 每轮按活跃象限动态折算比例，本轮未填满配额的象限标记耗尽，后续轮次不再分配配额
- 循环直到填满或无可取

### 标签优先机制（通用化设计）

核心模型：条件 -> 优先标签 ID 集合。引擎不关心条件来源，只接收当前生效的优先标签 ID 列表。

```
DisplayEngine.compute(tasks, periodType, remainingMinutes, priorityTagIds)
```

- `priorityTagIds`：由外部根据当前条件计算后传入
- 引擎将任务分为两组：匹配优先标签的任务在前，其余在后；每组内部独立按四象限排序

**当前开放场景**：工作日工作时段优先
**预留场景**：当前时段优先

持久化（SharedPreferences）：
- `pref_priority_tag_ids`：优先标签 ID 集合
- `pref_work_time_priority_enabled`：工作时段优先开关

### 时段特殊规则
- **午休 / 晚餐**：优先展示"片刻"任务
- **其他时段**：底部至少展示 1 条"片刻"任务
- **晚上**：四象限优先级反转

### 展示比例
按四象限 4:2:2:1 比例迭代截取：A 组耗尽后尚有位置才取 B 组，配额空位由后续轮次回填，避免界面空白。

### 全项目审查修复（2026-05-30）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F6/F8

- [x] `DisplayEngine` 改静态单例，避免多次 new 开销
- [x] `DisplayEngine.mEngineFailed` 删除（所有调用场景单线程，无需标记）

### 降级策略（错误处理）
1. 降级为列表模式——按创建时间倒序展示所有未归档任务
2. 在列表顶部显示提示条："排序暂不可用，显示全部任务"
3. 不阻塞用户操作（依然可执行任务、录入等）

### 输出格式
```
"标签名 任务内容"
```

### 标签展开
点击标签展示同标签所有匹配任务——复用引擎逻辑，仅增加 `tagId` 过滤条件。

### 死参数清理（2026-06-03 审查修复）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #4 #13

- [x] `computeByQuadrant()` 移除 `reverseQuadrant` 参数：象限内排序不计象限权重，该参数无实际作用
- [x] `computeByQuadrant()` 移除 `degradeMap` 参数：四象限管理页面不需要降级规则
- [x] 更新调用方 `MainViewModel.computeQuadrantOverviewSync()`、`QuadrantTaskListViewModel`
- [x] `MainViewModel` 删除 `prepareComputeContext()` + `ComputeContext` 内部类，`recomputeSync()` / `computeQuadrantOverviewSync()` 各自独立加载

### 接口
```java
public class DisplayEngine {
    // 无优先标签（Widget 等复用）
    public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                      int periodType, int remainingMin, boolean isEvening,
                                      int maxDisplayItems);

    // 含优先标签（MainViewModel 使用）
    public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                      int periodType, int remainingMin, boolean isEvening,
                                      int maxDisplayItems, Set<Long> priorityTagIds);
}
```

### 文件结构

```
ui/engine/
├── DisplayEngine.java       # 排序计算核心
├── DisplayItem.java         # 展示项模型（task + tag + 排序权重）
├── QuadrantRatioFilter.java # 4:2:2:1 比例截取
├── PeriodRuleProvider.java  # 时段特殊规则（午休/晚餐优先片刻、晚上反转）
└── FallbackListProvider.java # 降级策略（引擎异常时的列表模式）
```

### 依赖
- 时间段计算（获取当前时段类型和剩余时间）
- 任务数据（未归档，含象限和专注时长）
- 标签数据

# 研究发现、技术决策

- DisplayEngine 排序算法（时间容纳分组 A/B，组内 优先标签→四象限→专注时长微调）
- buildSortedItemsForQuadrant：四象限任务管理专用排序（时间容纳→优先标签→专注时长微调，不计象限权重）
- QuadrantRatioFilter 迭代多轮回填：A组不超容量时全收免筛选；耗尽象限标记后动态折算活跃象限比例，跳过无货象限
- buildSortedGroups 返回两组列表供 filter 分别处理
- 晚上时段四象限反转逻辑
- 方法重载：原 6 参数委托 7 参数版本，`doCompute` 统一处理 priorityTagIds 偏移

### 全项目审查修复（2026-05-30）

- `DisplayEngine` 改静态单例复用，Widget 和 APP 主界面共享同一实例
- `mEngineFailed` 标记删除，所有调用场景均为单线程

### QuadrantRatioFilter ceil 溢出（2026-06-04）

`collectLoop()` 中 `Math.ceil` 按象限独立向上取整，四象限配额合计可能超过 `remaining`，导致返回 item 数 > `maxDisplayItems`。Widget 侧 `computeItems()` 加 `subList` 截断兜底，引擎侧暂未修复。

### 死参数清理（2026-06-03）

- `computeByQuadrant()` 的 `reverseQuadrant` 和 `degradeMap` 参数在四象限管理专用方法中无实际作用，已移除
