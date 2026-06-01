# 数据统计模块

> 对应 task_plan.md M6

# 阶段规划、决策记录 （拆分自 task_plan.md）

## 定位和功能描述

按四象限统计每日完成任务数，展示月度趋势图表。后续加入调侃评价和增长鼓励。

## 整体规划和决策

### 当前实现
- 四象限维度：每日每个象限的完成数量
- 月度趋势：折线图或柱状图展示各象限月趋势
- 数据来源：`task_executions` 表中 `status = 0`（已完成）的记录

### 后续扩展

**调侃评价**：根据用户完成任务类型的倾向生成评价
- 示例："你这个月紧急不重要的事做了 80%——典型的救火队长"
- 示例："不紧急重要的事占比最高，稳扎稳打"
- 评价规则可配置，不应是硬编码

**增长鼓励**：
- 月度环比增长提示
- 连续完成天数记录
- 鼓励文案如"比上月多完成了 15 件事"
- 避免负面评价——只鼓励不批评

### 接口
```java
public class StatsCalculator {
    public LiveData<QuadrantStats> getDailyStats(String date);
    public LiveData<List<MonthlyTrend>> getMonthlyTrend(int year, int month);
}

public class CommentaryEngine {
    public String generate(QuadrantStats stats);
}
```

### 依赖
- `TaskExecutionDao`（执行记录查询）
- 图表库（MPAndroidChart 等）
- `CommentaryEngine` 独立于统计计算，方便后续替换规则

# 研究发现、技术决策 （拆分自 findings.md）

- StatsFragment + 菜单入口已搭建
- 统计维度：每日四象限完成量 + 月度趋势图表
- 调侃评价设计要点：规则可配置、不硬编码、只鼓励不批评

# 进度日志 （拆分自 progress.md）

- [x] StatsFragment + 菜单入口已搭建
- [ ] 每日四象限完成量统计
- [ ] 月度趋势图表
- [ ] 调侃评价 / 增长鼓励

**状态**：⬚ 待开始
