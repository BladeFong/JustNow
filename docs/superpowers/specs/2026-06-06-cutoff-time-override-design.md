# 截止时间覆盖设计

## 概述

用户在时段内可设置一个截止时间点（早于时段 endMinute），推荐引擎以此为时间基准计算剩余时间，影响任务排序和筛选。截止时间过后自动回退到原始时段，时段结束后清除。

**动机**：安排任务推荐化重构后，不再有"占用时间槽"的概念。但用户有时需要告诉系统"我的时间比时段定义的更短"（例如提前离开、临时有事）。截止时间覆盖提供一个轻量级的方式让用户调整时间预算，无需进入安排界面。

## 存储

SharedPreferences 存储：

| Key | 类型 | 说明 |
|-----|------|------|
| `cutoff_end_minute` | int | 当天分钟数（如 10:30 = 630），0 = 未设置 |

读写通过一个工具类封装（如 `CutoffTimeStore`），主界面和 Widget 统一调用。

## UI 入口

### 主界面底部栏

- 在 `bottom_period_status`（时段名 + 剩余时间）右侧加一个三角箭头指示器（`▲`），仅在时段内可见
- 点击整个 `bottom_period_status` 区域触发时间选择器
- 休息状态（Resting / Tomorrow）下不显示箭头，不可点击

### Widget 顶部栏

- `tv_widget_status` 保持现有行为（打开 MainActivity），不弹出时间选择器
- 截止时间仅从主界面设置，Widget 只负责读取和显示

## 时间选择器

复用 `PeriodConfigFragment` 中的步进按钮 + PopupWindow 滚轮模式，简化约束：

- 范围：`[当前时间向上取整到下一个 15 分钟, 时段 endMinute]`
  - 例：10:07 → 最小值 10:15；10:15 → 最小值 10:30
- 步进粒度：15 分钟（◀ / ▶ 按钮）
- 点击 HH:MM 弹出 NumberPicker 精确选择（分钟滚轮仅显示 00/15/30/45）
- 仅一个时间点（无 start/end 双控）
- 确认后写入 SP，触发 `WidgetDataChangeNotifier` 刷新 Widget

**约束**：
- 截止时间必须 > 当前时间（不允许设为"现在"）
- 截止时间必须 <= 时段 endMinute
- 如果当前时间 >= 时段 endMinute（时段即将结束），不弹出选择器

## 刷新机制

### 协调入口

```java
// MainViewModel 和 WidgetUpdateHelper 共用
void lazyRefreshState() {
    refreshExpiredOnceSchedules();  // 守卫 + 调用原方法
    refreshExpiredCutoff();         // 守卫 + 清理
}
```

### 调用点

| 触发点 | 位置 |
|--------|------|
| 主界面 TIME_TICK（每分钟） | `MainViewModel.refreshTimeState()` |
| 主界面 onResume | `MainViewModel.refreshTimeState()` |
| Widget 每分钟刷新 | `WidgetUpdateHelper` 渲染流程 |
| Widget 重载（onUpdate 等） | `WidgetUpdateHelper` 渲染流程 |
| 数据变更通知 | `WidgetUpdateHelper` 渲染流程 |

### 守卫方法

```java
// 守卫：轻量检查是否有启用的 TYPE_ONCE 安排，有则调用原方法
void refreshExpiredOnceSchedules() {
    if (!hasEnabledOnceSchedules()) return;  // 快速查询，无候选项则跳过
    disableExpiredOnceSchedules();            // 原方法不动
}

// 守卫：判断截止时间是否过期
void refreshExpiredCutoff() {
    int cutoff = CutoffTimeStore.getCutoffEndMinute();
    if (cutoff == 0) return;  // 未设置，跳过
    // 未过期且在时段内，跳过
    if (nowMinute < cutoff && isInActivePeriod()) return;
    CutoffTimeStore.clearCutoffEndMinute();
    // 通知方式与现有 notifyTaskDataChanged() 一致，
    // 触发 WidgetDataChangeNotifier 刷新 Widget + 主界面 recompute
    notifyTaskDataChanged();
}
```

### 安排任务到点重置

安排任务到点（闹钟触发）时，`AlarmReceiver` 直接调用 `CutoffTimeStore.clearCutoffEndMinute(context)` 清除截止时间。不走 `refreshExpiredCutoff()` 的条件判断，而是在闹钟处理流程中直接重置。

## 推荐引擎集成

### TimeRemainingCalculator.compute()

新增重载，由调用方读取 cutoff 值传入，避免将 Context 引入 Calculator：

```java
static PeriodStatus compute(List<TimePeriodEntity> periods, int cutoffEndMinute)
```

核心逻辑变更（找到命中的时段后）：

```java
int effectiveEnd = (cutoffEndMinute > nowMinute && cutoffEndMinute <= p.endMinute)
    ? cutoffEndMinute : p.endMinute;
status.endMinute = effectiveEnd;
status.remainingMinutes = effectiveEnd - nowMinute;
status.isCutoff = (effectiveEnd != p.endMinute);
```

调用方（MainViewModel、WidgetUpdateHelper）各自从 `CutoffTimeStore` 读取 cutoff 值传入。

### PeriodStatus 扩展

新增字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `isCutoff` | boolean | 是否使用了截止时间 |

`remainingMinutes` 基于有效结束时间（截止时间或原始 endMinute）计算，现有消费方无需改动。

### DisplayEngine

不需要改动。`DisplayEngine` 已经基于 `PeriodStatus.remainingMinutes` 做时间容纳分组和排序，截止时间覆盖后 `remainingMinutes` 自动变短，引擎行为自然调整。

## 显示一致性

| 状态 | 主界面底部栏 | Widget 顶部栏 |
|------|------------|-------------|
| 未设置截止时间 | `时段名` + `原始剩余` | `时段名 前始剩余` |
| 已设置截止时间 | `时段名` + `截止前剩余` | `时段名 截止前剩余` |
| 截止时间已过 | 回退显示原始时段剩余 | 回退显示原始时段剩余 |

底部栏在截止时间生效时，剩余时间文本可使用不同颜色（如 `text_secondary`）区分。

## 实现范围

### 新增

- `CutoffTimeStore`：SP 读写封装
- `CutoffTimePickerDialog`：时间选择器（复用步进 + 滚轮模式）
- `refreshExpiredOnceSchedules()`：守卫方法
- `refreshExpiredCutoff()`：截止时间清理
- `lazyRefreshState()`：协调入口
- 底部栏三角箭头指示器和点击事件
- 字符串资源（4 种语言）

### 修改

- `MainViewModel.refreshTimeState()`：调用 `lazyRefreshState()` 替代直接调用 `disableExpiredOnceSchedules()`
- `WidgetUpdateHelper` 渲染流程：调用 `lazyRefreshState()` 替代直接调用 `disableExpiredOnceSchedules()`
- `TimeRemainingCalculator.compute()`：新增带 `cutoffEndMinute` 参数的重载，返回含 `isCutoff` 的 `PeriodStatus`
- `MainFragment.updateBottomPeriodBar()`：根据 `isCutoff` 调整显示样式
- `WidgetUpdateHelper.renderWidgetStatus()`：根据 `isCutoff` 调整显示
- `PeriodStatus`：新增 `isCutoff` 字段
- `AlarmReceiver`：安排任务到点时直接清除截止时间

### 不改动

- `disableExpiredOnceSchedules()`：原方法不动，由守卫方法在外层控制调用
- `DisplayEngine`：不需要改动
- Widget 点击行为：保持打开 MainActivity

### 不在范围内

- `disableExpiredOnceSchedules()` 的 `postponedUntilMs` 修复（审查报告 #1 blocking）：单独处理
