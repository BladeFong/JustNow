# 剩余时间计算模块

# 阶段规划、决策记录

## 定位和功能描述

独立模块，计算当前时段剩余时间。被主界面时间线、Widget、任务推荐引擎、延迟判断等多处复用。

## 整体规划和决策

### 核心逻辑

```java
public class TimeRemainingCalculator {

    /** 计算时段剩余时间（分钟） */
    public int getRemainingMinutes(LocalTime now, TimePeriod period);

    /** 任务能否在时段剩余时间内完成（含15分钟容差） */
    public boolean canFit(TimePeriod period, LocalTime now, int focusMinutes);

    /** 延迟30分钟后是否超出时段（含15分钟容差） */
    public boolean canDelay(TimePeriod period, LocalTime now, int delayMinutes);
}
```

### 使用场景

| 场景 | 调用方 | 用途 |
|------|--------|------|
| 主界面左侧栏 | 时间线 UI | 显示剩余时间文字 + 液体动画 |
| Widget 顶部 | Widget Provider | 显示剩余时间 |
| 任务推荐 | 智能展示引擎 | 判断任务能否放入当前时段 |
| 任务延迟 | 任务执行逻辑 | 判断延迟30分钟是否超出限制 |

### 输出格式
```java
public String formatRemaining(int totalMinutes) {
    if (totalMinutes >= 60) {
        return (totalMinutes / 60) + "小时" + (totalMinutes % 60 > 0 ? (totalMinutes % 60) + "分钟" : "");
    }
    return totalMinutes + "分钟";
}
```

### 文件结构
```
ui/engine/
├── TimeRemainingCalculator.java  # 剩余时间计算
└── TimeRemainingFormatter.java   # 格式化输出
```

### 依赖
- [时间段计算](time-period.md)（获取当前时段起止时间）

# 研究发现、技术决策

- `TimeRemainingCalculator.compute()` 判断当前时段 + 剩余分钟数
- `getRemainingText(Resources)` 格式化显示（小时/分钟），字符串资源化
- 供 MainViewModel、TimelineView、JustNowWidgetProvider 三处复用
- 延迟30分钟判断（canDelay30Min），已落地到 `AlarmReceiver` 提醒延迟模块
