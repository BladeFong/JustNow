# 任务完成统一流程 + 拍照条件判断封装

> 相关模块：[tablet-flower-rewards](../../../modules/tablet-flower-rewards.md)，[task-completion-mode](../../../modules/task-completion-mode.md)
> 先决规范：[任务多照片支持与完成前拍照设计规范](./2026-07-21-task-photo-multi-and-precompletion-design.md)

## 1. 概述

当前任务完成分两条路径：`completeTaskFlow`（正常完成/琐碎完成）和 `shortCompleteFlow`（专注任务提前结束）。前者写执行记录、触发拍照弹窗、在时间线留下记录；后者不写执行记录、拍照列表查不到、时间线无记录。

本次改造将两条路径统一为一个完成流程，由单一参数控制时间线记录，其余行为（执行记录写入、拍照弹窗触发、当天隐藏）由流程内部自动处理。

同时封装 `isChildTask` 判断：平板 + 任务有内置图标标签时，才进入拍照奖励流程。

## 2. 统一完成流程

### 2.1 入口方法

```java
/**
 * 统一任务完成流程。
 *
 * @param task              当前任务
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

    // 1. 写入执行记录（时间线记录 = 正常完成 status=0，短完成 status=3）
    int status = keepTimelineRecord ? 0 : 3;
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
    mApp.getTaskRepository().incrementCompletionCounterSync(taskId, periodKey);

    // 6. 完成后钩子（子类可覆盖）
    onPostComplete();

    // 7. 拍照弹窗（平板 + 内置图标标签）
    if (isChildTask(task)) {
        runOnUiThread(() -> mShowPhotoPromptEvent.setValue(task));
    }

    if (onComplete != null) runOnUiThread(onComplete);
}
```

### 2.2 旧方法收归

- `completeTaskFlow` → 调用 `completeTaskUnified(task, schedule, stopSchedule, true, onComplete)`
- `shortCompleteFlow` / `performShortCompletionSync` → 调用 `completeTaskUnified(task, schedule, stopSchedule, false, onComplete)`
- `convertToChore` 逻辑保留在短完成入口自行处理（先转琐碎再调统一完成）

### 2.3 移除项

- 移除 `ChoreHiddenTodayStore.hideForToday(taskId)`：短完成不再额外标记隐藏，日模式任务靠 `TaskFilterHelper` 已有 `todayCompletedIds` 自然过滤
- 移除 `performShortCompletionSync`：归入统一方法

## 3. isChildTask 判断封装

```java
// JustNowApplication.java
/**
 * 是否儿童任务（平板 + 内置图标标签）。
 * 用于拍照弹窗、拍照按钮显隐、可拍照任务列表的统一判断。
 */
public boolean isChildTask(TaskEntity task) {
    return task != null
        && task.iconName != null && !task.iconName.isEmpty()
        && getResources().getBoolean(R.bool.is_tablet);
}
```

### 3.2 TaskExecutionRepository 新增 status 参数重载

```java
// TaskExecutionRepository.java
public void recordCompleteSync(long taskId, long startMs, long endMs,
                                int actualMinutes, int status) {
    TaskExecutionEntity entity = new TaskExecutionEntity();
    entity.taskId = taskId;
    entity.date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(endMs));
    entity.startMs = startMs;
    entity.endMs = endMs;
    entity.status = status;
    entity.actualMinutes = actualMinutes;
    mDb.taskExecutionDao().insert(entity);
}
```

### 3.1 调用点

| 场景 | 调用 |
|------|------|
| 拍照弹窗触发 | `completeTaskUnified` 内 `isChildTask(task)` |
| 拍照按钮显隐 | `refreshTakePhotoButtonVisibility` 查询时附加过滤 |
| 可拍照列表 | `getTodayTasksAvailableForPhoto` 仅返回 `isChildTask` 的任务 |

### 3.2 数据查询

保持现有两查询合并方案（`getTodayCompletedTasks` 去掉 `status=0` + `getRunningTaskSync`），Java 层用 `isChildTask` 过滤后返回。

## 4. TaskExecutionEntity status 扩展

| status | 含义 | 时间线显示 | 拍照列表 |
|--------|------|-----------|---------|
| 0 | 正常完成 | ✅ | ✅ |
| 1 | 延迟 | ❌ | ❌ |
| 2 | 暂停 | ❌ | ❌ |
| 3 | 短完成（提前结束） | ❌ | ✅ |

时间线过滤：`TimelineBuilder` 保持 `execution.status == 0` 不变。

拍照列表：不区分 status，所有今天完成的都列出来。

## 5. 文件改动清单

| 文件 | 改动 |
|------|------|
| `BaseTaskViewModel.java` | 新增 `completeTaskUnified`，旧方法收归调用 |
| `MainViewModel.java` | `shortCompleteDirect` / `completeChoreTask` 适配 |
| `TaskPhotoDao.java` | 查询改为 UNION，去掉 status=0 限制 |
| `TaskPhotoRepository.java` | Java 层 `isChildTask` 过滤 |
| `RewardBarFragment.java` | 拍照按钮显隐/列表查询 加 `isChildTask` 过滤 |
| `TaskExecutionEntity.java` | status 字段注释更新 |
| `TimelineBuilder.java` | 无需改动（status==0 不变） |
| `TaskFilterHelper.java` | 移除 `ChoreHiddenTodayStore` 隐藏逻辑 |
| `ChoreHiddenTodayStore.java` | 保留类文件（`getHiddenTodayIds` 读方法保留），`hideForToday` 写入调用已全部移除 |

## 6. 数据迁移

- 历史短完成数据无执行记录，无法回溯，不迁移
- 新完成统一走新流程，后续数据一致
