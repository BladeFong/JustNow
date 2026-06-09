# 琐碎任务时段外可开始

## 背景

当前 `TaskStartGuard.evaluate()` 和 `MainViewModel.evaluateTaskStartSync()` 对所有任务统一检查 `isInPeriod()`，时段外一律返回 `BLOCKED_OUT_OF_PERIOD`。

琐碎任务（focusMinutes == 0）在项目中已多处被当作"时间无关"处理：
- DisplayEngine 永远归入"时间够用"组
- TimelineBuilder 跳过，不占时间线
- 完成后当天自动隐藏
- 走短完成流程，不写执行记录

唯独 TaskStartGuard 的 `isInPeriod()` 检查将其当作需要时段的专注任务，属于不一致。

## 设计决策

**琐碎任务不受时段限制，任何时段外均可开始。**

理由：
- 琐碎任务 focusMinutes == 0，不存在"占用时段"的问题
- 用户主动打开 App、主动点开始，不存在"鼓励熬夜"的推送行为
- 睡前场景是合理触发点：晚上时段结束后仍可处理琐碎

## 改动范围

### 1. TaskStartGuard.evaluate()

文件：`app/src/main/java/com/nearby/justnow/scheduler/TaskStartGuard.java`

将时段检查条件从 `!status.isInPeriod()` 改为 `!status.isInPeriod() && task.focusMinutes > 0`：

```java
// 改动前
if (status == null || !status.isInPeriod()) {
    return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
}

// 改动后
if (!status.isInPeriod() && task.focusMinutes > 0) {
    return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
}
```

### 2. MainViewModel.evaluateTaskStartSync()

文件：`app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java`

同样将时段检查条件改为 `!status.isInPeriod() && task.focusMinutes > 0`，琐碎任务自然落到已有的 `startWhenAllowed` 流程：

```java
// 改动前
if (!status.isInPeriod()) {
    return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
}

// 改动后
if (!status.isInPeriod() && task.focusMinutes > 0) {
    return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
}
```

## 不受影响的部分

| 组件 | 原因 |
|------|------|
| 时段 UI 状态（剩余时间、截止箭头） | 绑定 `isInPeriod()`，不受影响 |
| TaskExecutionAutoCompleter | 琐碎在时段外开始后 `findPeriodByMinute` 返回 null 直接跳过，不会被误自动完成 |
| 专注任务 | 仍受 `isInPeriod()` 约束，行为不变 |
| 对话框按钮逻辑 | 琐碎返回 OK 后 `canStart = true`，"开始"按钮正常启用 |
| Widget | Widget 的任务开始也走 TaskStartGuard，同样受益 |
