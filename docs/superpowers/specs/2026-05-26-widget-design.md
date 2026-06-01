# 桌面 Widget 设计

> 对应 task_plan.md M7 · 设计日期 2026-05-26

## 概述

Android App Widget，在桌面展示当前时段推荐任务。Widget 与 APP 同进程，直读 Room DB，复用 M4 智能展示引擎和剩余时间计算。

## 架构

```
JustNowWidgetProvider (AppWidgetProvider)
  ├─ 判断宽度是否铺满 → 选 单列/双列 布局
  ├─ TimeRemainingCalculator → 剩余时间
  ├─ DisplayEngine.compute() → 任务列表
  └─ RemoteViews 渲染

MinuteBoundaryReceiver (BroadcastReceiver)
  └─ AlarmManager 整分钟唤醒 → 更新剩余时间

点击目标:
  ├─ 任务条 → ReminderDetailActivity
  └─ "+"  → TaskInputActivity（任务录入独立 Activity）
```

## Widget 配置

- 声明 3×3（`minWidth`/`minHeight`），支持 resize
- 最小尺寸 ≥ 3×3（2×2 空间不足以展示任务列表，不提供）

## 布局

### 顶部栏（单列/双列共用）

左侧：当前时段名 + 剩余时间
右侧："+" 按钮（`#DAE134` 黄绿色圆角方块）

非时段时：左侧替换为"休息中"+ 下个时段提示。

### 单列（默认，3×3）

```
▌#工作  30分钟  整理本周会议纪要
▌#个人  60分钟  跑步
▌       琐碎    回复客户邮件
```

任务行：色标 3dp | 标签 固定宽 | 专注时长 固定宽右对齐 | 标题 bold，flex 撑满
无标签时标签位留空，时长不左移

### 双列（宽度铺满桌面时）

```
▌#工作  30分钟  整理纪要  │  ▌      琐碎    回复邮件
▌#个人  60分钟  跑步      │  ▌#学习  90分钟  读论文
```

触发条件：`currentWidth >= maxWidth`（`onAppWidgetOptionsChanged` 检测）
不关注高度。高度变化不触发布局切换。

左右列各一套相同列结构，列宽独立对齐，中间 16dp 分隔。

## 数据获取

### 剩余时间
- `TimeRemainingCalculator.compute(periods)` — 复用
- `PeriodGroupRuleResolver.getActivePeriodGroupSync()` — 复用
- 非时段时 `isInPeriod() == false`

### 任务列表
- `DisplayEngine.compute(tasks, tagMap, periodType, remainingMin, isEvening, maxDisplayItems)` — 复用
- `maxDisplayItems` 由布局可用高度 ÷ 任务行高动态计算
- `priorityTagIds` 暂不传入（Widget 端无优先标签条件）

### 降级
- 引擎异常 → `FallbackListProvider` 简单列表 + 顶部提示
- 数据库为空 → 任务区显示空状态文案（新增 `s_widget_empty`，4 语言）

## 刷新策略

| 场景 | 机制 | 频率 |
|------|------|------|
| 剩余时间 | `AlarmManager.setExact` 整分钟唤醒 `MinuteBoundaryReceiver` | 每分钟 |
| 任务列表 | 时段切换时 M4 重算 | 时段边界 |
| 进程死后兜底 | `appwidget-provider` `updatePeriodMillis` | 约 30 分钟 |
| APP 内状态变化 | `AppWidgetManager.updateAppWidget()` 主动推送 | 即时 |
| Widget 全部移除 | `onDisabled` 取消 AlarmManager | — |

### 整分钟闹钟调度

```
onUpdate() → 注册下一个整分钟 AlarmManager
MinuteBoundaryReceiver.onReceive() →
  1. 更新所有 Widget 实例的剩余时间
  2. 如果跨时段边界 → 重算任务列表
  3. 注册下一个整分钟 AlarmManager
onDisabled() → 取消所有 AlarmManager
```

## 点击处理

| 目标 | 跳转 | PendingIntent |
|------|------|---------------|
| 任务条目 | `ReminderDetailActivity` | `getActivity()`，携带 `taskId` |
| "+" 按钮 | `TaskInputActivity` | `getActivity()` |

### ReminderDetailActivity
- 从 `ReminderDetailFragment` 拆出，在 `AndroidManifest.xml` 独立注册
- Widget 和 APP 内部统一跳转此 Activity
- `MainActivity` 导航图中 `ReminderDetailFragment` 目标移除

## 边界场景

| 场景 | 处理 |
|------|------|
| 数据库为空 | 顶部栏常驻，任务区显示空状态 |
| 引擎异常 | 降级简单列表（`FallbackListProvider`），顶部提示 |
| 非时段 | 顶部栏"休息中"+ 下个时段提醒，任务列表不变 |
| Widget resize | `onAppWidgetOptionsChanged` 检测宽度，铺满 ↔ 双列 |
| 任务完成/归档 | APP 侧主动 `updateAppWidget()` 即时刷新 |

## 文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `widget/JustNowWidgetProvider.java` | 修改 | 基础框架已有，增强 |
| `widget/MinuteBoundaryReceiver.java` | 新建 | 整分钟闹钟接收器 |
| `widget/WidgetUpdateHelper.java` | 新建 | 统一 updateWidget 逻辑（供 Provider 和 Receiver 共用） |
| `ReminderDetailActivity.java` | 新建 | 从 ReminderDetailFragment 拆为独立 Activity |
| `res/layout/widget_justnow.xml` | 新建 | 单列布局（若无则新建） |
| `res/layout/widget_justnow_dual.xml` | 新建 | 双列布局（宽度铺满时） |
| `res/xml/appwidget_provider.xml` | 修改 | 按 3×3 配置，开启 resize |

## 依赖

- M4 智能展示引擎（`DisplayEngine`）
- 剩余时间计算（`TimeRemainingCalculator`）
- 时间段模块（`TimePeriodRepository`、`PeriodGroupRuleResolver`）
- Room 数据库（直读，同进程）
