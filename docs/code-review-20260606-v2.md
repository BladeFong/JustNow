# 代码审查报告 2026-06-06 v2

- 审查范围：`9483fda..28e663c`（从 `c648cdc feat: 安排任务推荐化重构` 到当前 HEAD）
- 审查时间：2026-06-06
- 审查人：code-auditor

## 审查范围

本轮覆盖“安排任务推荐化重构”及之后的修改：

1. 安排任务推荐化重构（移除占用时间槽，改为 30 分钟推荐优先）
2. Widget 刷新时同步 disable 过期 TYPE_ONCE 安排
3. 截止时间覆盖设计与实现
4. 时间线显示休息时段修复
5. 截止时间覆盖功能增强与时间选择弹窗裁切修复

重点查看：`AlarmReceiver`、`ReminderNotifier`、`TaskScheduleRepository`、`TaskScheduleDao`、`TaskStartGuard`、`DisplayEngine`、`TimeRemainingCalculator`、`MainViewModel`、`MainFragment`、`TimelineView`、`WidgetUpdateHelper`、截止时间覆盖相关布局和字符串资源。

## 结果概要

| 级别 | 数量 |
|------|:--:|
| blocking | 1 |
| important | 3 |
| nit | 1 |
| **合计** | **5** |

## 审核记录（2026-06-06）

| 编号 | 级别 | 审核结果 | 说明 |
|------|------|----------|------|
| #1 | blocking | 已修复 | `postponedUntilMs` 不再参与延迟与推荐窗口；TYPE_ONCE 清理改为按日期或所属时段结束，延迟闹钟不会再被原始 `scheduledTime + focusMinutes` 提前禁用 |
| #2 | important | 已修复 | 推荐窗口回归只看原始 `scheduledTime`；推迟只重新设闹钟，不延长或屏蔽推荐优先窗口 |
| #3 | important | 已修复 | `CutoffTimeStore` 新增保存日期，读取时若不是今天立即清除，避免昨天的 cutoff 影响今天 |
| #4 | important | 误报 | 用户确认：业务设计就是“安排任务不跟随 cutoff 逻辑”，所以 `TaskStartGuard` 不接入 cutoff 符合业务口径 |
| #5 | nit | 先不处理 | 用户确认：该符号作为图标用途，当前先不按硬编码文本/字号问题处理 |

---

## 问题详情

### [x] #1 [blocking] 延迟后的 TYPE_ONCE 安排仍按原始时间过期，会在延迟闹钟触发前被禁用

**文件**：
- `app/src/main/java/com/nearby/justnow/data/repository/TaskScheduleRepository.java` 第 196-213 行
- `app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java` 第 188-193 行
- `app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java` 第 72-81 行

`handlePostpone()` 会写入 `postponedUntilMs = now + 30min` 并注册新的延迟闹钟，但 `disableExpiredOnceSchedules()` 仍只用原始 `scheduled_time + focus_minutes` 判断单次安排是否过期，完全没有读取 `postponedUntilMs`。

复现场景：

1. 单次安排 09:00，任务时长 30 分钟。
2. 09:00 通知触发，用户点“延迟30分钟”，新闹钟约 09:30。
3. TIME_TICK / Widget 刷新在 09:30 左右调用 `refreshExpiredOnceSchedules()`。
4. `deadlineMs = 09:00 + 30min`，满足 `deadlineMs <= now`，安排被禁用。
5. 延迟闹钟到点后 `handleAlarm()` 读取到 `!schedule.enabled`，直接返回，用户收不到延迟后的提醒。

**建议**：过期判断改用有效开始时间：

```java
long effectiveStartMs = s.postponedUntilMs > 0
    ? s.postponedUntilMs
    : todayStartMs + s.scheduledTime * 60000L;
long deadlineMs = effectiveStartMs + s.focusMinutes * 60000L;
```

同时补一个仓库层单元测试：延迟后的 TYPE_ONCE 在原始 deadline 后、延迟 deadline 前不应被禁用。

**审核结果**：已修复。修复方案没有继续让 `postponedUntilMs` 参与安排生命周期，而是改为“推迟只重新设闹钟，不延长推荐窗口”；TYPE_ONCE 清理改为按日期已过或所属时段结束禁用，避免原始 `scheduledTime + focusMinutes` 提前禁用延迟闹钟。

---

### [x] #2 [important] 重复安排的 `postponedUntilMs` 过期后不兜底清理，后续日期会失去原始推荐窗口

**文件**：
- `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java` 第 575-596 行
- `app/src/main/java/com/nearby/justnow/data/dao/TaskScheduleDao.java` 第 101-103 行
- `app/src/main/java/com/nearby/justnow/data/repository/TaskScheduleRepository.java` 第 229-233 行

`computeSchedulePriorityIds()` 只要看到 `postponedUntilMs > 0`，就只按延迟窗口判断优先级，不再回退到原始 `scheduledTime` 窗口。这个字段目前只在“开始”“通知忽略”和主界面跨时段时清除；如果用户延迟后没有再点通知，且主界面没有经历跨时段清理，字段会一直留在数据库。

结果是：每日/每周重复安排第二天仍然 `matchesToday()`，但因为 `postponedUntilMs` 是昨天的绝对时间，`nowMs < postponedUntilMs + 30min` 永远不成立，原始到点后的 30 分钟推荐优先也不会生效。

**建议**：

- 增加按绝对时间清理过期延迟标记的方法，例如 `clearExpiredPostponesByTimestamp(now - 30min)`。
- 在 `MainViewModel.lazyRefreshState()`、`WidgetUpdateHelper.refreshExpiredState()`、`ReminderScheduler.refreshToday()` 中统一调用。
- 或至少在 `computeSchedulePriorityIds()` 中将已过期的 `postponedUntilMs` 视为无效，回退到原始窗口；但数据库仍建议清理。

**审核结果**：已修复。`computeSchedulePriorityIds()` 已改为只看原始 `scheduledTime` 的 30 分钟窗口，推迟时间戳不再制造或屏蔽推荐窗口；并补充了对应测试。

---

### [x] #3 [important] 截止时间只存分钟不存日期，跨天后可能把昨天的截止时间套到今天

**文件**：
- `app/src/main/java/com/nearby/justnow/data/store/CutoffTimeStore.java` 第 17-30 行
- `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java` 第 239-247 行
- `app/src/main/java/com/nearby/justnow/widget/WidgetUpdateHelper.java` 第 91-100 行

`CutoffTimeStore` 只保存当天分钟数；清理逻辑只判断 `nowMinute < cutoff` 就直接保留。若用户昨天设置 18:00 截止，应用/Widget 到今天 09:00 才刷新，因为 `09:00 < 18:00`，旧值不会被清掉。若今天当前时段结束时间大于等于 18:00，`TimeRemainingCalculator.compute(periods, cutoff)` 会继续把剩余时间截断到 18:00。

这会导致今天的推荐排序、底部栏显示、Widget 状态和开始任务校验都被昨天的设置影响。

**建议**：截止时间存储改为“日期 + 分钟”或直接存绝对时间戳；刷新时若保存日期不是 `DateUtils.todayStartMs()`，立即清除。清理逻辑也应按设计文档补上“仍在当前时段内”的判断，而不是仅比较分钟。

**审核结果**：已修复。`CutoffTimeStore` 现在同时保存 `cutoff_end_minute` 和 `cutoff_date_ms`；读取时发现保存日期不是 `DateUtils.todayStartMs()` 会清除旧值并返回未设置。该修复覆盖主界面、Widget 和任务开始校验读取 cutoff 的入口。

---

### [x] #4 [important] 通知“开始”入口没有应用截止时间覆盖，和主界面开始校验不一致

**文件**：
- `app/src/main/java/com/nearby/justnow/scheduler/TaskStartGuard.java` 第 46-53 行
- `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java` 第 552-561 行
- `app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java` 第 122-135 行

主界面 `evaluateTaskStartSync()` 会读取 `CutoffTimeStore` 并用截断后的 `remainingMinutes` 判断任务能否开始。通知按钮走 `AlarmReceiver.handleStartTask()` -> `TaskStartGuard.evaluate()`，但 `TaskStartGuard` 调用的是 `TimeRemainingCalculator.compute(periods)`，没有传入截止时间。

结果是：同一个任务在主界面可能因截止时间覆盖显示“时间不够”，但从通知“开始”按钮仍可启动。更糟的是 `handleStartTask()` 会先自动完成当前执行中任务，再做校验；校验口径不一致会放大用户可见差异。

**建议**：`TaskStartGuard` 读取 `CutoffTimeStore.getCutoffEndMinute(context)` 并调用 `compute(periods, cutoffEndMinute)`。同时补一个测试或至少手工验证：设置截止时间后，主界面开始和通知开始返回同一 `TaskStartResult`。

---

### [x] #5 [nit] 新增截止箭头使用硬编码文本和字号，违反资源化与字号规范

**文件**：`app/src/main/res/layout/fragment_main_page0.xml` 第 285-294 行

新增的 `tv_cutoff_arrow` 直接写了：

```xml
android:text="▲"
android:textSize="16sp"
```

项目规范要求非测试代码用户可见字符串资源化，布局中禁止硬编码 `android:textSize`。这里虽然是符号指示器，但仍是可见 UI 文本；字号也应走 `TextAppearance` 或 `@dimen/text_size_*`。

**建议**：改成字符串资源（四语言同值也可以）+ `TextAppearance.JustNow.Caption`，或改用 `ImageView`/矢量 drawable 作为指示图标。

**审核结果**：先不处理。用户确认该符号作为图标用途，当前先不按硬编码文本/字号问题处理。

---

## 未处理项汇总

无。#4 为用户确认的业务设计差异，#5 用户确认当前作为图标用途先不处理。

## 验证

已由主代理在修复后运行定向单元测试和 Java 编译验证：

- `~/gradlew-wsl.sh --no-daemon testDebugUnitTest --tests com.nearby.justnow.broadcast.AlarmReceiverTest --tests com.nearby.justnow.data.store.CutoffTimeStoreTest --tests com.nearby.justnow.data.repository.TaskScheduleRepositoryTest --tests com.nearby.justnow.ui.main.MainViewModelTest`
- `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`

结果均为 `BUILD SUCCESSFUL`。
