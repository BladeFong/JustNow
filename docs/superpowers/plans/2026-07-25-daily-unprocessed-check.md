# 每天未处理任务提醒通知 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在每天最后一个时段结束前后的两个时机点，通过闹钟+通知提醒用户处理当天未开始过的任务。

**Architecture:** 复用现有 AlarmManager + ReminderScheduler + ReminderNotifier 机制。从 `computeFilteredTasks` 提取核心过滤为静态方法（原方法调用之，不复制），ReminderScheduler 新增幂等注册方法，AlarmReceiver 新增 action 处理，TaskRepository 保存任务后触发首次注册。

**Tech Stack:** Java, Android AlarmManager (RTC_WAKEUP), NotificationCompat, Room (同步查询)

## Global Constraints

- 不主动取消闹钟（一天一次，不需要）
- 通知复用 `task_reminder` 渠道（CHANNEL_ID = "task_reminder"），新增通知 ID 3000
- 过滤排除用户交互的标签筛选，其余与主界面/Widget 一致
- "今天已处理"判定：`task_executions.date == today AND startMs > 0` 有记录
- 通知文案：时机1"今天还没处理任务，抽空看看？" 时机2"还有些琐碎小事，趁今天处理掉？"
- 注册方法幂等：`FLAG_NO_CREATE` 判断已有则跳过

---

### Task 1: TaskFilterHelper — 提取核心过滤为静态方法

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java`

**Interfaces:**
- Produces: `public static List<TaskEntity> filterDisplayableTasks(JustNowApplication app, List<TaskEntity> tasks, List<TimePeriodEntity> allPeriods, List<TimePeriodEntity> sortedPeriods, List<TaskExecutionEntity> todayExecutions)`

- [ ] **Step 1: 添加静态方法**

在 `computeDisplayItems` 之后、最后 `}` 之前插入：

```java
/**
 * 核心任务过滤（无标签筛选）。包含自动完成过期任务、隐藏短完成专注任务、
 * 隐藏今日已完成琐碎任务、完成模式配额过滤。不含标签过滤和缓存保存。
 */
public static List<TaskEntity> filterDisplayableTasks(
        @NonNull JustNowApplication app,
        @NonNull List<TaskEntity> tasks,
        @NonNull List<TimePeriodEntity> allPeriods,
        @NonNull List<TimePeriodEntity> sortedPeriods,
        @NonNull List<TaskExecutionEntity> todayExecutions) {

    // 1. 自动完成过期任务
    Set<Long> autoCompletedIds = TaskExecutionAutoCompleter.completeExpiredRunningTasksSync(
            app.getTaskRepository(), app.getTaskExecutionRepository(),
            tasks, sortedPeriods, allPeriods);
    if (!autoCompletedIds.isEmpty()) {
        tasks.removeIf(t -> autoCompletedIds.contains(t.id));
        for (long autoId : autoCompletedIds) {
            ReminderScheduler.cancelOvertimeCheck(app, autoId);
        }
    }

    // 2. 隐藏今日已隐藏的任务（短时间完成的专注任务）
    ChoreHiddenTodayStore hiddenStore = new ChoreHiddenTodayStore(app);
    Set<Long> hiddenToday = hiddenStore.getHiddenTodayIds();
    if (!hiddenToday.isEmpty()) {
        tasks.removeIf(t -> hiddenToday.contains(t.id) && t.executingStartMs <= 0);
    }

    // 3. 隐藏今日已完成的琐碎任务
    TimelineBuilder.hideCompletedChoresForToday(tasks, todayExecutions);

    // 4. 完成模式：日/周/月/年隐藏判定
    java.util.HashSet<Long> todayCompletedIds = new java.util.HashSet<>();
    if (todayExecutions != null) {
        for (TaskExecutionEntity e : todayExecutions) {
            todayCompletedIds.add(e.taskId);
        }
    }
    java.util.Iterator<TaskEntity> iter = tasks.iterator();
    while (iter.hasNext()) {
        TaskEntity task = iter.next();
        if (task.completionMode == 0) {
            if (todayCompletedIds.contains(task.id)) {
                iter.remove();
            }
        } else {
            String periodKey = TaskRepository.computePeriodKey(task);
            TaskCompletionCounterEntity counter = app.getTaskRepository()
                    .getCompletionCounterSync(task.id, periodKey);
            if (counter != null && counter.completed >= task.quota) {
                iter.remove();
            }
        }
    }

    return tasks;
}
```

- [ ] **Step 2: 重构 computeFilteredTasks 调用静态方法**

替换第 162-237 行为：

```java
    private void computeFilteredTasks(@Nullable Set<Long> filterTagIds) {
        // 1. 获取所有活跃任务
        List<TaskEntity> tasks = mApp.getTaskRepository().getAllActiveTasksSync();

        // 2. 获取时段数据
        List<TimePeriodEntity> allPeriods = mApp.getTimePeriodRepository().getAllPeriodsSync();
        ActivePeriodGroup activeGroup = mApp.getTimePeriodRepository().getActivePeriodGroupSync();
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);

        // 3. 获取今日执行记录
        List<TaskExecutionEntity> todayExecutions = mApp.getTaskExecutionRepository().getTodayExecutionsSync();

        // 4. 核心过滤（不含标签筛选）
        tasks = filterDisplayableTasks(mApp, tasks, allPeriods, periods, todayExecutions);

        // 5. 标签过滤（用户交互相关）
        if (filterTagIds != null && !filterTagIds.isEmpty()) {
            tasks.removeIf(t -> t.tagId == null || !filterTagIds.contains(t.tagId));
        }

        // 保存缓存
        mFilteredTasks = tasks;
        mTodayExecutions = todayExecutions;
        mTagMap = mApp.getTagRepository().getAllTagsMapSync();
        mPeriods = periods;
        mExecutingTasks = new ArrayList<>();
        for (TaskEntity t : tasks) {
            if (t.executingStartMs > 0) {
                mExecutingTasks.add(t);
            }
        }

        // 时段状态
        int cutoffEndMinute = CutoffTimeStore.getCutoffEndMinute(mApp);
        mStatus = TimeRemainingCalculator.compute(periods, cutoffEndMinute);
    }
```

需要新增 import：`com.nearby.justnow.data.entity.TaskCompletionCounterEntity`（已在现有 import 中）。

- [ ] **Step 3: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java
git commit -m "refactor: 提取核心过滤为静态方法，原方法调用之

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: ReminderNotifier — 新增未处理任务通知方法

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java`

**Interfaces:**
- Produces: `public static void sendUnprocessedCheck(Context context, int phase)`

- [ ] **Step 1: 添加常量和通知方法**

在 `NOTIFICATION_ID_BASE` 下方新增：

```java
static final int UNFINISHED_NOTIFY_ID = 3000;
```

在 `cancelOvertime` 方法之后、最后 `}` 之前添加：

```java
/** 发送未处理任务提醒通知。phase 1=时段结束前30分钟，2=时段结束后。 */
public static void sendUnprocessedCheck(Context context, int phase) {
    String title = phase == 1
        ? "今天还没处理任务，抽空看看？"
        : "还有些琐碎小事，趁今天处理掉？";

    Intent tapIntent = new Intent(context,
        com.nearby.justnow.ui.main.MainActivity.class);
    tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent tapPi = PendingIntent.getActivity(context, UNFINISHED_NOTIFY_ID,
        tapIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
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

- [ ] **Step 2: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java
git commit -m "feat: 新增未处理任务提醒通知发送方法

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: ReminderScheduler — 新增未处理检查闹钟调度

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java`

**Interfaces:**
- Produces: `ACTION_DAILY_UNFINISHED_CHECK`, `EXTRA_CHECK_PHASE`
- Produces: `public void scheduleUnprocessedCheckIfNeeded()`

- [ ] **Step 1: 添加常量**

`EXTRA_OVERTIME_TASK_ID` 下方：

```java
public static final String ACTION_DAILY_UNFINISHED_CHECK = "com.nearby.justnow.ACTION_DAILY_UNFINISHED_CHECK";
public static final String EXTRA_CHECK_PHASE = "check_phase";
```

`OVERTIME_REQUEST_CODE_BASE` 下方：

```java
private static final int UNFINISHED_CHECK_REQUEST_CODE = 5000;
```

- [ ] **Step 2: refreshToday 末尾追加调用**

在 `refreshToday()` 最后 `}` 前插入：

```java
        // 每日续期未处理任务检查闹钟
        scheduleUnprocessedCheckIfNeeded();
```

- [ ] **Step 3: 添加调度方法**

在 `scheduleDailyRefresh()` 之后添加：

```java
/** 注册未处理任务检查闹钟（幂等：已有则跳过）。 */
public void scheduleUnprocessedCheckIfNeeded() {
    // 幂等：phase 1 闹钟已存在则跳过
    Intent probeIntent = new Intent(mAppContext, AlarmReceiver.class);
    probeIntent.setAction(ACTION_DAILY_UNFINISHED_CHECK);
    probeIntent.putExtra(EXTRA_CHECK_PHASE, 1);
    PendingIntent probePi = PendingIntent.getBroadcast(mAppContext,
        UNFINISHED_CHECK_REQUEST_CODE + 1, probeIntent,
        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    if (probePi != null) return;

    // 获取时段
    JustNowApplication app = (JustNowApplication) mAppContext;
    TimePeriodRepository periodRepo = app.getTimePeriodRepository();
    ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
    if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty()) return;
    List<TimePeriodEntity> sortedPeriods = com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(
        activeGroup.periods);
    TimePeriodEntity lastPeriod = sortedPeriods.get(sortedPeriods.size() - 1);

    // 过滤判断是否有可展示任务
    List<TaskEntity> allTasks = mTaskRepo.getAllActiveTasksSync();
    List<TimePeriodEntity> allPeriods = periodRepo.getAllPeriodsSync();
    List<TaskExecutionEntity> todayExecutions = app.getTaskExecutionRepository().getTodayExecutionsSync();

    List<TaskEntity> displayable = com.nearby.justnow.ui.base.TaskFilterHelper.filterDisplayableTasks(
        app, allTasks, allPeriods, sortedPeriods, todayExecutions);
    if (displayable == null || displayable.isEmpty()) return;

    // 时机1：结束前30分钟
    long phase1Ms = triggerMsFromMinute(lastPeriod.endMinute - 30);
    if (phase1Ms > System.currentTimeMillis()) {
        setUnfinishedCheckAlarm(1, phase1Ms);
    }

    // 时机2：时段结束时
    long phase2Ms = triggerMsFromMinute(lastPeriod.endMinute);
    if (phase2Ms > System.currentTimeMillis()) {
        setUnfinishedCheckAlarm(2, phase2Ms);
    }
}

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

新增 import：

```java
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TimePeriodRepository;
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java
git commit -m "feat: 新增未处理任务检查闹钟调度方法

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: AlarmReceiver — 处理未处理检查 action

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java`

**Interfaces:**
- Consumes: `ACTION_DAILY_UNFINISHED_CHECK`, `EXTRA_CHECK_PHASE`, `sendUnprocessedCheck`

- [ ] **Step 1: onReceive 添加新分支**

在 `ACTION_OVERTIME_CHECK` 分支之后、`else` 之前插入：

```java
        } else if (ReminderScheduler.ACTION_DAILY_UNFINISHED_CHECK.equals(action)) {
            int phase = intent.getIntExtra(ReminderScheduler.EXTRA_CHECK_PHASE, 0);
            if (phase > 0) {
                PendingResult pendingResult = goAsync();
                AppDatabase.execute(() -> {
                    try {
                        handleUnfinishedCheck(context, phase);
                    } finally {
                        pendingResult.finish();
                    }
                });
            }
```

- [ ] **Step 2: 添加处理方法**

类末尾 `}` 之前：

```java
private void handleUnfinishedCheck(Context context, int phase) {
    JustNowApplication app = (JustNowApplication) context.getApplicationContext();

    // 当天已开始过任务 → 跳过
    List<TaskExecutionEntity> todayExecutions = app.getTaskExecutionRepository().getTodayExecutionsSync();
    if (todayExecutions != null) {
        for (TaskExecutionEntity e : todayExecutions) {
            if (e.startMs > 0) return;
        }
    }

    // 获取时段
    TimePeriodRepository periodRepo = app.getTimePeriodRepository();
    ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
    if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty()) return;
    List<TimePeriodEntity> sortedPeriods = com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(
        activeGroup.periods);
    List<TimePeriodEntity> allPeriods = periodRepo.getAllPeriodsSync();

    // 过滤
    List<TaskEntity> tasks = app.getTaskRepository().getAllActiveTasksSync();
    List<TaskEntity> displayable = com.nearby.justnow.ui.base.TaskFilterHelper.filterDisplayableTasks(
        app, tasks, allPeriods, sortedPeriods, todayExecutions);
    if (displayable == null || displayable.isEmpty()) return;

    if (phase == 1) {
        ReminderNotifier.createChannel(context);
        ReminderNotifier.sendUnprocessedCheck(context, 1);
    } else if (phase == 2) {
        boolean hasChore = false;
        for (TaskEntity t : displayable) {
            if (t.focusMinutes == 0) { hasChore = true; break; }
        }
        if (hasChore) {
            ReminderNotifier.createChannel(context);
            ReminderNotifier.sendUnprocessedCheck(context, 2);
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java
git commit -m "feat: 新增未处理任务检查闹钟接收处理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: TaskRepository — 保存任务后触发首次注册

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java`

- [ ] **Step 1: insertSync 末尾追加**

`return id;` 之前：

```java
        new ReminderScheduler(mApp).scheduleUnprocessedCheckIfNeeded();
```

- [ ] **Step 2: updateSync 末尾追加**

`notifyTaskDataChanged();` 之后：

```java
        new ReminderScheduler(mApp).scheduleUnprocessedCheckIfNeeded();
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew assembleRelease 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java
git commit -m "feat: 保存任务后触发未处理检查闹钟注册

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: 安装验证

- [ ] **Step 1: 编译 Release 并安装**

```bash
./gradlew assembleRelease && adb -t 12 install -r app/build/outputs/apk/release/app-release.apk
```
Expected: BUILD SUCCESSFUL + Install Success

- [ ] **Step 2: 验证不崩溃**

打开 App → 确认主界面正常 → 新建任务不崩溃。
