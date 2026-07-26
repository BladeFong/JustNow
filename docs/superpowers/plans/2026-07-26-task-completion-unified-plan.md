# 任务完成统一流程 + isChildTask 判断封装 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一任务完成流程，短完成写执行记录（status=3），封装 isChildTask 判断，修复拍照列表查不到短完成任务的问题。

**Architecture:** 在 BaseTaskViewModel 新增 `completeTaskUnified` 统一入口，收归 `completeTaskFlow` 和 `shortCompleteFlow`。`isChildTask` 封装在 JustNowApplication。`TaskExecutionRepository.recordCompleteSync` 新增 status 参数重载。

**Tech Stack:** Java, Android Room, AndroidX Fragment

**相关 Spec:** `docs/superpowers/specs/2026-07-26-task-completion-unified-design.md`

## Global Constraints

- Java 命名：成员变量 `m` 前缀，局部变量 `camelCase`
- 短完成写执行记录 status=3，正常完成 status=0
- 时间线 `TimelineBuilder` 保持 `status==0` 过滤不变
- 拍照条件 = `isTablet() && iconName != null`，封装在 JustNowApplication
- 移除 `ChoreHiddenTodayStore.hideForToday` 调用

---

### Task 1: TaskExecutionRepository 新增 status 参数重载

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskExecutionRepository.java`

**Interfaces:**
- Produces: `void recordCompleteSync(long taskId, long startMs, long endMs, int actualMinutes, int status)`

- [ ] **Step 1: 新增 status 参数重载**

```java
// 在现有 recordCompleteSync(taskId, startMs, endMs, actualMinutes) 之后添加：

/** 记录执行完成（指定状态）。正常完成 status=0，短完成 status=3 */
public void recordCompleteSync(long taskId, long startMs, long endMs,
                                int actualMinutes, int status) {
    TaskExecutionEntity entity = new TaskExecutionEntity();
    entity.taskId = taskId;
    entity.date = formatDate(endMs);
    entity.startMs = startMs;
    entity.endMs = endMs;
    entity.status = status;
    entity.actualMinutes = actualMinutes;
    mDao.insert(entity);
    addToTodayCache(entity);
}
```

- [ ] **Step 2: 现有方法委托到新重载**

```java
// 将现有方法改为委托：
public void recordCompleteSync(long taskId, long startMs, long endMs, int actualMinutes) {
    recordCompleteSync(taskId, startMs, endMs, actualMinutes, 0);
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskExecutionRepository.java
git commit -m "feat: recordCompleteSync 新增 status 参数重载

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: TaskExecutionEntity status 注释更新

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/entity/TaskExecutionEntity.java`

- [ ] **Step 1: 更新 status 字段注释**

```java
// 将：
//  执行状态：0=已完成 1=延迟 2=暂停
// 改为：
  执行状态：0=正常完成 1=延迟 2=暂停 3=短完成（提前结束）
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/entity/TaskExecutionEntity.java
git commit -m "docs: TaskExecutionEntity status 新增短完成状态注释

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: JustNowApplication 新增 isChildTask 方法

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/JustNowApplication.java`

**Interfaces:**
- Produces: `boolean isChildTask(TaskEntity task)`

- [ ] **Step 1: 添加方法**

```java
// 在 JustNowApplication 类中，getCurrentUserId() 附近添加：

/**
 * 是否儿童任务（平板 + 内置图标标签）。
 * 拍照弹窗、拍照按钮显隐、可拍照任务列表的统一判断。
 */
public boolean isChildTask(@NonNull TaskEntity task) {
    return task.iconName != null && !task.iconName.isEmpty()
        && getResources().getBoolean(R.bool.is_tablet);
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/JustNowApplication.java
git commit -m "feat: JustNowApplication 新增 isChildTask 统一拍照条件判断

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: BaseTaskViewModel 新增 completeTaskUnified + 旧方法收归

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/base/BaseTaskViewModel.java`

**Interfaces:**
- Consumes: `TaskExecutionRepository.recordCompleteSync(taskId, startMs, endMs, actualMinutes, status)`, `JustNowApplication.isChildTask(task)`
- Produces: `completeTaskUnified(TaskEntity, TaskScheduleEntity, boolean stopSchedule, boolean keepTimelineRecord, Runnable onComplete)`
- Modifies: `completeTaskFlow` → 委托给 `completeTaskUnified`
- Modifies: `performShortCompletionSync` → 委托给 `completeTaskUnified`
- Removes: `hideForToday` 调用

- [ ] **Step 1: 新增统一完成方法**

在 `completeTaskFlow` 方法上方添加：

```java
/**
 * 统一任务完成流程。
 *
 * @param task               当前任务
 * @param schedule           当前活跃安排（可为 null）
 * @param stopSchedule       是否停止当前安排
 * @param keepTimelineRecord 是否在时间线留下记录（正常完成=true，短完成=false）
 * @param onComplete         完成回调，通过 runOnUiThread 投递
 */
protected final void completeTaskUnified(TaskEntity task, TaskScheduleEntity schedule,
                                          boolean stopSchedule, boolean keepTimelineRecord,
                                          Runnable onComplete) {
    if (task == null || task.executingStartMs <= 0) {
        if (onComplete != null) runOnUiThread(onComplete);
        return;
    }

    long endMs = System.currentTimeMillis();
    int actualMinutes = Math.max(1, (int) ((endMs - task.executingStartMs) / 60000));
    int status = keepTimelineRecord ? 0 : 3;

    // 1. 写入执行记录
    mApp.getTaskExecutionRepository().recordCompleteSync(
        task.id, task.executingStartMs, endMs, actualMinutes, status);

    // 2. 清除任务执行中状态
    mApp.getTaskRepository().clearExecutionSync(task.id);

    // 3. 取消超时检查闹钟
    ReminderScheduler.cancelOvertimeCheck(mApp, task.id);

    // 4. 处理安排收尾
    if (schedule != null) {
        ReminderNotifier.cancel(mApp, schedule.id);
    }
    if (stopSchedule && schedule != null) {
        mApp.getTaskScheduleRepository().disableScheduleSync(
            schedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
    }

    // 5. 消耗完成配额
    String periodKey = TaskRepository.computePeriodKey(task);
    mApp.getTaskRepository().incrementCompletionCounterSync(task.id, periodKey);

    // 6. 完成后钩子
    onPostComplete();

    // 7. 拍照弹窗（平板 + 内置图标标签）
    TaskEntity completedTask = mApp.getTaskRepository().getTaskByIdSync(task.id);
    if (completedTask != null && mApp.isChildTask(completedTask)) {
        runOnUiThread(() -> mShowPhotoPromptEvent.setValue(completedTask));
    }

    if (onComplete != null) runOnUiThread(onComplete);
}
```

- [ ] **Step 2: 改造 `completeTaskFlow` 委托到统一方法**

将现有 `completeTaskFlow` 方法体替换为调用统一方法：

```java
protected final void completeTaskFlow(TaskEntity task, TaskScheduleEntity schedule,
                                       boolean stopSchedule, Runnable onComplete) {
    completeTaskUnified(task, schedule, stopSchedule, true, onComplete);
}
```

- [ ] **Step 3: 改造 `performShortCompletionSync` 委托到统一方法**

```java
protected void performShortCompletionSync(long taskId, boolean stopSchedule,
    boolean convertToChore, TaskScheduleEntity schedule) {
    TaskRepository taskRepo = mApp.getTaskRepository();
    TaskEntity task = taskRepo.getTaskByIdSync(taskId);
    if (task == null) return;

    // 转琐碎先处理
    if (convertToChore) {
        taskRepo.convertToChoreSync(taskId);
        task = taskRepo.getTaskByIdSync(taskId);
        if (task == null) return;
    }

    // 走统一完成流程（不在时间线留记录）
    completeTaskUnified(task, schedule, stopSchedule, false, null);
}
```

注意：移除 `ReminderScheduler.cancelOvertimeCheck`、`ReminderNotifier.cancel`、`disableScheduleSync`、`hideForToday`、`incrementCompletionCounterSync` 调用——这些都由 `completeTaskUnified` 处理。

- [ ] **Step 4: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/ui/base/BaseTaskViewModel.java
git commit -m "feat: 统一任务完成流程 completeTaskUnified

- 新增 completeTaskUnified 统一入口
- completeTaskFlow/performShortCompletionSync 收归委托
- 短完成写 status=3 执行记录
- 移除 ChoreHiddenTodayStore.hideForToday
- isChildTask 判断控制拍照弹窗触发

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: TaskPhotoDao 查询去掉 status=0 限制

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/dao/TaskPhotoDao.java`

**Interfaces:**
- Modifies: `getTodayCompletedTasks` 去掉 `e.status = 0` 条件

- [ ] **Step 1: 去掉 status=0 限制**

```java
// 将 getTodayCompletedTasks 查询中的：
//   AND e.status = 0
// 删除该条件行
```

即：
```java
@Query("SELECT DISTINCT t.* FROM tasks t " +
       "INNER JOIN task_executions e ON t.id = e.task_id " +
       "WHERE t.is_archived = 0 " +
       "  AND e.end_ms >= :todayStartMs AND e.end_ms <= :todayEndMs " +
       "ORDER BY e.end_ms DESC")
List<TaskEntity> getTodayCompletedTasks(long todayStartMs, long todayEndMs);
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/dao/TaskPhotoDao.java
git commit -m "fix: 拍照列表查询去掉 status=0 限制，短完成任务可查

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: 拍照列表/按钮/弹窗加 isChildTask 过滤

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/TaskPhotoListDialog.java`

**Interfaces:**
- Consumes: `JustNowApplication.isChildTask(TaskEntity)`
- Modifies: `getTodayTasksAvailableForPhoto` 返回前过滤, `refreshTakePhotoButtonVisibility`, `TaskPhotoListAdapter.onBindViewHolder`

- [ ] **Step 1: TaskPhotoRepository 加 isChildTask 过滤**

```java
// getTodayTasksAvailableForPhoto 方法末尾，返回前过滤：
public List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs) {
    List<TaskEntity> completed = mDb.taskPhotoDao().getTodayCompletedTasks(todayStartMs, todayEndMs);
    TaskEntity running = mDb.taskDao().getRunningTaskSync();
    JustNowApplication app = (JustNowApplication) mDb.getContext().getApplicationContext();
    // 合并去重 + isChildTask 过滤
    java.util.LinkedHashMap<Long, TaskEntity> map = new java.util.LinkedHashMap<>();
    for (TaskEntity t : completed) {
        if (app.isChildTask(t)) map.put(t.id, t);
    }
    if (running != null && app.isChildTask(running)) {
        map.putIfAbsent(running.id, running);
    }
    return new java.util.ArrayList<>(map.values());
}
```

等等——`mDb` 是 `AppDatabase` 实例，没有 `getContext()`。需要改用 `JustNowApplication` 实例。让 `TaskPhotoRepository` 接收 `JustNowApplication` 参数，或者在 `RewardBarFragment` 层过滤。

**改为在 RewardBarFragment / TaskPhotoListDialog 层过滤，Repository 保持纯数据层：**

- [ ] **Step 1: RewardBarFragment 加 isChildTask 过滤**

在 `refreshTakePhotoButtonVisibility()` 中，查询后过滤：

```java
private void refreshTakePhotoButtonVisibility() {
    if (mBtnTakePhoto == null) return;
    long[] todayRange = getTodayRangeMs();
    List<TaskEntity> availableTasks =
        mPhotoRepository.getTodayTasksAvailableForPhoto(todayRange[0], todayRange[1]);
    // isChildTask 过滤
    com.nearby.justnow.JustNowApplication app =
        (com.nearby.justnow.JustNowApplication) requireActivity().getApplication();
    java.util.Iterator<TaskEntity> it = availableTasks.iterator();
    while (it.hasNext()) {
        if (!app.isChildTask(it.next())) it.remove();
    }
    mBtnTakePhoto.post(() -> {
        if (availableTasks.isEmpty()) {
            mBtnTakePhoto.setVisibility(View.GONE);
        } else {
            mBtnTakePhoto.setVisibility(View.VISIBLE);
        }
    });
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java
git commit -m "feat: 拍照按钮/列表加 isChildTask 过滤

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: TaskFilterHelper 移除 ChoreHiddenTodayStore

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java`

- [ ] **Step 1: 移除 hiddenToday 过滤**

删除以下代码块（约 line 233-238）：
```java
// 2. 隐藏今日已隐藏的任务（短时间完成的专注任务）
ChoreHiddenTodayStore hiddenStore = new ChoreHiddenTodayStore(app);
Set<Long> hiddenToday = hiddenStore.getHiddenTodayIds();
if (!hiddenToday.isEmpty()) {
    tasks.removeIf(t -> hiddenToday.contains(t.id) && t.executingStartMs <= 0);
}
```

如果 `ChoreHiddenTodayStore` 的 import 仅此一处使用，同时删除 import。

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java
git commit -m "refactor: 移除 ChoreHiddenTodayStore 隐藏逻辑，统一完成流程自然过滤

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: 全量编译 + 更新进度文档

- [ ] **Step 1: 全量编译**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:assembleDebug 2>&1 | tail -5
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 更新进度文档**

`modules/tablet-flower-rewards_progress.md` 头部插入：
```
### 2026-07-26 — 任务完成统一流程 + isChildTask 判断封装

- 新增 completeTaskUnified 统一入口，收归 completeTaskFlow / performShortCompletionSync
- 短完成写 status=3 执行记录，拍照列表可查
- isChildTask 统一判断：isTablet && iconName != null
- 移除 ChoreHiddenTodayStore.hideForToday
- TaskExecutionRepository.recordCompleteSync 新增 status 参数重载
```

`progress.md` 同步。

- [ ] **Step 3: 提交**

```bash
git add modules/tablet-flower-rewards_progress.md progress.md
git commit -m "docs: 更新进度文档

Co-Authored-By: Claude <noreply@anthropic.com>"
```
