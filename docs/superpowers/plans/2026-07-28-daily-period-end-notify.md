# 每时段结束通知 — 实现计划 v2

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 MORNING/AFTERNOON/EVENING 三个时段结束时发送休息提醒（不设门控）；时段切换时如有超时自动完成任务，通知中追加提示；最后时段叠加琐碎提醒；时机1保留。

**Architecture:** 在 v1 代码基础上重写调度和通知逻辑。`scheduleUnprocessedCheckIfNeeded` → `schedulePeriodEndChecks`，新增 `ACTION_PERIOD_END` 处理无门控时段通知，保持 `ACTION_DAILY_UNFINISHED_CHECK` 给时机1。`TaskFilterHelper` 不变。

**Tech Stack:** Java, Android AlarmManager (RTC_WAKEUP), NotificationCompat, Room (同步查询), BleNotificationSDK

## Global Constraints

- 复用 `task_reminder` 渠道
- 通知通过 BleNotificationSDK 发送
- 过滤复用 `TaskFilterHelper.filterDisplayableTasks`（不变）
- 午休（NOON）和晚餐（DINNER）跳过
- 时段 key 用 PeriodNameKey 常量（小写）
- 时段结束通知不设门控，到点即发
- 时机1 保留，仍走 `ACTION_DAILY_UNFINISHED_CHECK`

---

### Task 1: ReminderScheduler — 重写为 schedulePeriodEndChecks

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java:211-273`

**Interfaces:**
- Produces: `public static final String ACTION_PERIOD_END` — 新 action
- Produces: `public static final String EXTRA_PERIOD_KEY` — extra，值为 PeriodNameKey 常量
- Produces: `public void schedulePeriodEndChecks()` — 替代原 scheduleUnprocessedCheckIfNeeded
- Removes: `setUnfinishedCheckAlarm` → 替换为 `setPeriodEndAlarm`

- [ ] **Step 1: 添加新常量，清理旧常量**

`EXTRA_CHECK_PHASE` 和 `ACTION_DAILY_UNFINISHED_CHECK` 保留（时机1仍需）。新增：

```java
// 在 ACTION_DAILY_UNFINISHED_CHECK 下方
public static final String ACTION_PERIOD_END = "com.nearby.justnow.ACTION_PERIOD_END";
public static final String EXTRA_PERIOD_KEY = "period_key";
```

`UNFINISHED_CHECK_REQUEST_CODE` 保留继续用。新增：

```java
private static final int PERIOD_END_REQUEST_CODE_BASE = 5100;
```

- [ ] **Step 2: 替换 scheduleUnprocessedCheckIfNeeded 为 schedulePeriodEndChecks**

删除第 211-273 行（`scheduleUnprocessedCheckIfNeeded`、`triggerMsFromMinute`、`setUnfinishedCheckAlarm`），替换为：

```java
/** 注册每时段结束通知闹钟（幂等：已有则跳过）。跳 NOON/DINNER。 */
public void schedulePeriodEndChecks() {
    // 幂等检查
    Intent probeIntent = new Intent(mAppContext, AlarmReceiver.class);
    probeIntent.setAction(ACTION_PERIOD_END);
    probeIntent.putExtra(EXTRA_PERIOD_KEY, PeriodNameKey.MORNING);
    PendingIntent probePi = PendingIntent.getBroadcast(mAppContext,
        PERIOD_END_REQUEST_CODE_BASE, probeIntent,
        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    if (probePi != null) return;

    // 获取时段
    JustNowApplication app = (JustNowApplication) mAppContext;
    TimePeriodRepository periodRepo = app.getTimePeriodRepository();
    ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
    if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty()) return;
    List<TimePeriodEntity> sortedPeriods = com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(
        activeGroup.periods);

    // 跑过滤管线（供后续超时和琐碎判定用）
    List<TaskEntity> allTasks = mTaskRepo.getAllActiveTasksSync();
    List<TimePeriodEntity> allPeriods = periodRepo.getAllPeriodsSync();
    List<TaskExecutionEntity> todayExecutions = app.getTaskExecutionRepository().getTodayExecutionsSync();
    List<TaskEntity> displayable = com.nearby.justnow.ui.base.TaskFilterHelper.filterDisplayableTasks(
        app, allTasks, allPeriods, sortedPeriods, todayExecutions);
    boolean hasDisplayableChores = false;
    if (displayable != null) {
        for (TaskEntity t : displayable) {
            if (t.focusMinutes == 0) { hasDisplayableChores = true; break; }
        }
    }

    // 遍历时段注册
    for (TimePeriodEntity p : sortedPeriods) {
        // 跳过午休和晚餐
        if (PeriodNameKey.NOON.equals(p.periodKey) || PeriodNameKey.DINNER.equals(p.periodKey)) continue;

        // 时段结束闹钟
        long endMs = triggerMsFromMinute(p.endMinute);
        if (endMs > System.currentTimeMillis()) {
            setPeriodEndAlarm(p.periodKey, p.endMinute);
        }

        // 最后一个时段（EVENING）额外注册时机1
        if (PeriodNameKey.EVENING.equals(p.periodKey)) {
            long phase1Ms = triggerMsFromMinute(p.endMinute - 30);
            if (phase1Ms > System.currentTimeMillis()) {
                setUnfinishedCheckAlarm(1, phase1Ms);
            }
        }
    }
}

/** 将当天分钟数转为毫秒时间戳。若已过则加到明天。 */
private long triggerMsFromMinute(int minuteOfDay) {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
    cal.set(Calendar.MINUTE, minuteOfDay % 60);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long ms = cal.getTimeInMillis();
    if (ms <= System.currentTimeMillis()) ms += AlarmManager.INTERVAL_DAY;
    return ms;
}

private void setPeriodEndAlarm(String periodKey, int endMinute) {
    Intent intent = new Intent(mAppContext, AlarmReceiver.class);
    intent.setAction(ACTION_PERIOD_END);
    intent.putExtra(EXTRA_PERIOD_KEY, periodKey);
    int requestCode = PERIOD_END_REQUEST_CODE_BASE + Math.abs(periodKey.hashCode() & 0x7FFF);
    PendingIntent pi = PendingIntent.getBroadcast(mAppContext, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMsFromMinute(endMinute), pi);
}

private void setUnfinishedCheckAlarm(int phase, long triggerMs) {
    Intent intent = new Intent(mAppContext, AlarmReceiver.class);
    intent.setAction(ACTION_DAILY_UNFINISHED_CHECK);
    intent.putExtra(EXTRA_CHECK_PHASE, phase);
    PendingIntent pi = PendingIntent.getBroadcast(mAppContext,
        UNFINISHED_CHECK_REQUEST_CODE + phase, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMs, pi);
}
```

需要新增 import：

```java
import com.nearby.justnow.data.model.PeriodNameKey;
```

- [ ] **Step 3: 更新 refreshToday 中的调用**

将 `refreshToday()` 末尾的 `scheduleUnprocessedCheckIfNeeded();` 改为：

```java
        schedulePeriodEndChecks();
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java
git commit -m "refactor: schedulePeriodEndChecks 替代原方法，支持多时段结束通知

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: ReminderNotifier — 重构通知方法

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java`

**Interfaces:**
- Removes: `sendUnprocessedCheck(Context, int)`
- Produces: `public static void sendPeriodEnd(Context context, String periodKey, List<String> autoCompletedTaskNames)` — 时段结束通知
- Produces: `public static void sendUnfinishedChoreCheck(Context context)` — 时机1（保留）

- [ ] **Step 1: 删除 sendUnprocessedCheck，添加新方法**

删除 `sendUnprocessedCheck` 方法（第 166-191 行），替换为：

```java
/** 发送时段结束通知。 */
public static void sendPeriodEnd(Context context, String periodKey,
                                  List<String> autoCompletedTaskNames) {
    String title = getPeriodEndMessage(periodKey);

    // 超时任务提示
    String body = null;
    if (autoCompletedTaskNames != null && !autoCompletedTaskNames.isEmpty()) {
        if (autoCompletedTaskNames.size() == 1) {
            body = "「" + autoCompletedTaskNames.get(0) + "」已自动标记完成";
        } else {
            body = "「" + autoCompletedTaskNames.get(0) + "」等 "
                + autoCompletedTaskNames.size() + " 个任务已自动标记完成";
        }
    }

    // 琐碎叠加（仅 EVENING）
    String appendedChore = getChoreReminderIfNeeded(context, periodKey);
    if (appendedChore != null) {
        title = title + appendedChore;
    }

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setOngoing(false)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

    if (body != null) builder.setContentText(body);

    // 点击跳转主界面
    Intent tapIntent = new Intent(context,
        com.nearby.justnow.ui.main.MainActivity.class);
    tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent tapPi = PendingIntent.getActivity(context,
        UNFINISHED_NOTIFY_ID + Math.abs(periodKey.hashCode() & 0xFFF),
        tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    builder.setContentIntent(tapPi);

    BleNotificationSDK.Companion.getInstance().sendNotification(
        builder,
        UNFINISHED_NOTIFY_ID + Math.abs(periodKey.hashCode() & 0xFFF),
        null
    );
}

/** 时段结束语文案映射。 */
private static String getPeriodEndMessage(String periodKey) {
    switch (periodKey) {
        case "morning": return "午休了，休息一下吧。";
        case "afternoon": return "快晚上了，休整休整。";
        case "evening": return "一天结束了，好好休息。";
        default: return "";
    }
}

/**
 * EVENING 时段检查琐碎任务叠加提醒。
 * @return 叠加文案（如 "还有些琐碎小事，趁今天处理掉？"），不需要时返回 null
 */
private static String getChoreReminderIfNeeded(Context context, String periodKey) {
    if (!"evening".equals(periodKey)) return null;

    JustNowApplication app = (JustNowApplication) context.getApplicationContext();

    // 检查当天是否开始过琐碎任务
    List<com.nearby.justnow.data.entity.TaskExecutionEntity> todayExecs =
        app.getTaskExecutionRepository().getTodayExecutionsSync();
    if (todayExecs != null) {
        java.util.Set<Long> startedTaskIds = new java.util.HashSet<>();
        for (com.nearby.justnow.data.entity.TaskExecutionEntity e : todayExecs) {
            if (e.startMs > 0) startedTaskIds.add(e.taskId);
        }
        if (!startedTaskIds.isEmpty()) {
            // 有 task_execution 记录且是琐碎任务的才计数
            boolean startedChore = false;
            for (com.nearby.justnow.data.entity.TaskExecutionEntity e : todayExecs) {
                if (e.startMs > 0) {
                    com.nearby.justnow.data.entity.TaskEntity task =
                        app.getTaskRepository().getTaskByIdSync(e.taskId);
                    if (task != null && task.focusMinutes == 0) {
                        startedChore = true;
                        break;
                    }
                }
            }
            if (startedChore) return null; // 已开始过琐碎 → 不叠加
        }
    }

    // 检查是否有可展示琐碎任务
    com.nearby.justnow.data.repository.TimePeriodRepository periodRepo =
        app.getTimePeriodRepository();
    com.nearby.justnow.data.model.ActivePeriodGroup activeGroup =
        periodRepo.getActivePeriodGroupSync();
    if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty())
        return null;
    List<com.nearby.justnow.data.entity.TimePeriodEntity> sortedPeriods =
        com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(activeGroup.periods);
    List<com.nearby.justnow.data.entity.TimePeriodEntity> allPeriods =
        periodRepo.getAllPeriodsSync();
    List<com.nearby.justnow.data.entity.TaskEntity> tasks =
        app.getTaskRepository().getAllActiveTasksSync();
    List<com.nearby.justnow.data.entity.TaskEntity> displayable =
        com.nearby.justnow.ui.base.TaskFilterHelper.filterDisplayableTasks(
            app, tasks, allPeriods, sortedPeriods, todayExecs);

    if (displayable != null) {
        for (com.nearby.justnow.data.entity.TaskEntity t : displayable) {
            if (t.focusMinutes == 0) return "还有些琐碎小事，趁今天处理掉？";
        }
    }

    return null;
}

/** 发送时机1通知（最后时段结束前30分钟）。 */
public static void sendUnfinishedCheck(Context context) {
    Intent tapIntent = new Intent(context,
        com.nearby.justnow.ui.main.MainActivity.class);
    tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent tapPi = PendingIntent.getActivity(context, UNFINISHED_NOTIFY_ID,
        tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("今天还没处理任务，抽空看看？")
        .setOngoing(false)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(tapPi);

    BleNotificationSDK.Companion.getInstance().sendNotification(
        builder,
        UNFINISHED_NOTIFY_ID,
        null
    );
}
```

需要新增 import：
```java
import java.util.List;
```
（已有）
```java
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TimePeriodRepository;
```
检查是否已存在。

- [ ] **Step 2: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java
git commit -m "refactor: 多时段结束通知文案 + 琐碎叠加 + 超时提示

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: AlarmReceiver — ACTION_PERIOD_END 处理

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java`

**Interfaces:**
- Consumes: `ACTION_PERIOD_END`, `EXTRA_PERIOD_KEY`
- Consumes: `ReminderNotifier.sendPeriodEnd(Context, String, List<String>)`
- Consumes: `ReminderNotifier.sendUnfinishedCheck(Context)`

- [ ] **Step 1: onReceive 添加 ACTION_PERIOD_END 分支**

在 `ACTION_DAILY_UNFINISHED_CHECK` 分支之前插入：

```java
        } else if (ReminderScheduler.ACTION_PERIOD_END.equals(action)) {
            String periodKey = intent.getStringExtra(ReminderScheduler.EXTRA_PERIOD_KEY);
            if (periodKey != null) {
                PendingResult pendingResult = goAsync();
                AppDatabase.execute(() -> {
                    try {
                        handlePeriodEnd(context, periodKey);
                    } finally {
                        pendingResult.finish();
                    }
                });
            }
```

- [ ] **Step 2: 更新 handleUnfinishedCheck 为时机1专用**

将 `handleUnfinishedCheck` 方法体简化（删除 phase 2 逻辑），保留 phase 1：

```java
/** 时机1：最后时段结束前30分钟，当天未开始任何任务且有可展示任务 → 提醒。 */
private void handleUnfinishedCheck(Context context, int phase) {
    // phase 始终为 1，保留签名兼容
    JustNowApplication app = (JustNowApplication) context.getApplicationContext();

    List<TaskExecutionEntity> todayExecutions = app.getTaskExecutionRepository().getTodayExecutionsSync();
    if (todayExecutions != null) {
        for (TaskExecutionEntity e : todayExecutions) {
            if (e.startMs > 0) return;
        }
    }

    TimePeriodRepository periodRepo = app.getTimePeriodRepository();
    ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
    if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty()) return;
    List<TimePeriodEntity> sortedPeriods = com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(
        activeGroup.periods);
    List<TimePeriodEntity> allPeriods = periodRepo.getAllPeriodsSync();

    List<TaskEntity> tasks = app.getTaskRepository().getAllActiveTasksSync();
    List<TaskEntity> displayable = com.nearby.justnow.ui.base.TaskFilterHelper.filterDisplayableTasks(
        app, tasks, allPeriods, sortedPeriods, todayExecutions);
    if (displayable == null || displayable.isEmpty()) return;

    ReminderNotifier.createChannel(context);
    ReminderNotifier.sendUnfinishedCheck(context);
}
```

- [ ] **Step 3: 添加 handlePeriodEnd 方法**

在类的末尾（`}` 之前）添加：

```java
/** 时段结束通知：不设门控。获取超时自动完成的任务 → 拼通知。 */
private void handlePeriodEnd(Context context, String periodKey) {
    JustNowApplication app = (JustNowApplication) context.getApplicationContext();

    // 获取时段数据
    TimePeriodRepository periodRepo = app.getTimePeriodRepository();
    ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
    if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty()) return;
    List<TimePeriodEntity> sortedPeriods = com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(
        activeGroup.periods);
    List<TimePeriodEntity> allPeriods = periodRepo.getAllPeriodsSync();

    // 跑过滤管线获取可展示任务（给超时自动完成用）
    List<TaskEntity> tasks = app.getTaskRepository().getAllActiveTasksSync();
    List<TaskExecutionEntity> todayExecutions = app.getTaskExecutionRepository().getTodayExecutionsSync();

    // 自动完成过期任务，获取刚完成的 ID
    Set<Long> autoCompletedIds = com.nearby.justnow.data.repository.TaskExecutionAutoCompleter
        .completeExpiredRunningTasksSync(
            app.getTaskRepository(), app.getTaskExecutionRepository(),
            tasks, sortedPeriods, allPeriods);

    // 获取刚自动完成的任务名称（从 DB 按 ID 查）
    List<String> autoCompletedNames = new ArrayList<>();
    for (long taskId : autoCompletedIds) {
        TaskEntity autoTask = app.getTaskRepository().getTaskByIdSync(taskId);
        if (autoTask != null && autoTask.content != null) {
            autoCompletedNames.add(autoTask.content);
        }
    }

    ReminderNotifier.createChannel(context);
    ReminderNotifier.sendPeriodEnd(context, periodKey, autoCompletedNames);
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java
git commit -m "feat: ACTION_PERIOD_END 处理 + 时机1简化为独立方法

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: TaskRepository — 更新调用方

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java`

- [ ] **Step 1: 更新 insertSync 调用**

将 `scheduleUnprocessedCheckIfNeeded()` 改为 `schedulePeriodEndChecks()`：

```java
        if (mApp != null) new ReminderScheduler(mApp).schedulePeriodEndChecks();
```

- [ ] **Step 2: 更新 updateSync 调用**

同上。

- [ ] **Step 3: 编译验证 + 提交**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java
git commit -m "refactor: 保存任务后调用 schedulePeriodEndChecks

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 安装验证

- [ ] **Step 1: 编译 Release 并安装**

```bash
./gradlew assembleRelease && adb -t 12 install -r app/build/outputs/apk/release/app-release.apk
```
Expected: BUILD SUCCESSFUL + Install Success
