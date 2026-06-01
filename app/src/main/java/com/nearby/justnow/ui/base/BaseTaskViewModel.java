package com.nearby.justnow.ui.base;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.store.ChoreHiddenTodayStore;
import com.nearby.justnow.scheduler.ReminderScheduler;

/**
 * Task 相关 ViewModel 基类 — 封装跨 ViewModel 的同步业务方法。
 * 仅供 MainViewModel / ReminderDetailViewModel 等任务态 ViewModel 继承。
 */
public abstract class BaseTaskViewModel extends BaseViewModel {

    protected BaseTaskViewModel(JustNowApplication app) {
        super(app);
    }

    /**
     * 统一任务完成流程（Sync 方法不应在主线程调用）
     */
    protected void completeRunningTaskSync(TaskEntity task, long endMs) {
        if (task == null || task.executingStartMs <= 0) return;
        long safeEndMs = Math.max(endMs, task.executingStartMs + 1);
        int actualMinutes = Math.max(1, (int) ((safeEndMs - task.executingStartMs) / 60000));
        new TaskExecutionRepository(mDb).recordCompleteSync(
            task.id, task.executingStartMs, safeEndMs, actualMinutes);
        new TaskRepository(mDb).clearExecutionSync(task.id);
    }

    /**
     * 统一短完成流程（Sync 方法不应在主线程调用）
     */
    protected void performShortCompletionSync(long taskId, boolean stopSchedule,
        boolean convertToChore, TaskScheduleEntity schedule) {
        TaskRepository taskRepo = new TaskRepository(mDb);
        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null) return;

        if (convertToChore) {
            taskRepo.convertToChoreSync(taskId);
        } else {
            taskRepo.clearExecutionSync(taskId);
        }

        if (schedule != null) {
            new ReminderScheduler(mApp).cancel(schedule.id, schedule.scheduledTime);
            // 短完成：单 schedule 收尾；task 仍在，不动其他 schedule
            ReminderNotifier.cancel(mApp, schedule.id);
        }

        if (stopSchedule && schedule != null) {
            new TaskScheduleRepository(mDb).disableScheduleSync(
                schedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
        }

        new ChoreHiddenTodayStore(mApp).hideForToday(taskId);
    }

    /**
     * 统一归档任务流程（Sync 方法不应在主线程调用）
     */
    protected void archiveTaskSync(long taskId, TaskScheduleEntity schedule) {
        TaskRepository taskRepo = new TaskRepository(mDb);
        TaskScheduleRepository scheduleRepo = new TaskScheduleRepository(mDb);

        if (schedule != null) {
            // 归档任务：disableForTaskSync 会清所有 schedule 数据，闹钟须同步清掉（bug 修复）
            ReminderNotifier.cancel(mApp, schedule.id);
        }

        taskRepo.clearExecutionSync(taskId);
        taskRepo.archiveTaskSync(taskId);
        scheduleRepo.disableForTaskSync(taskId);
    }

    /**
     * 检查清单状态是否需要确认（Sync 方法不应在主线程调用）
     */
    protected boolean checkListStateNeedsConfirm(TaskEntity task, long taskId) {
        if (task == null || !"checklist".equals(task.detailModuleType)) {
            return false;
        }
        return new TaskChecklistRepository(mDb).hasAnyStateSync(taskId);
    }

    /**
     * 统一任务完成流程模板（Sync 方法，不应在主线程调用）。
     *
     * @param task 当前任务
     * @param schedule 当前活跃安排（可为 null）
     * @param stopSchedule 是否停止当前安排
     * @param onComplete 完成回调，统一通过 runOnUiThread 投递
     */
    protected final void completeTaskFlow(TaskEntity task, TaskScheduleEntity schedule,
                                           boolean stopSchedule, Runnable onComplete) {
        if (task == null || task.executingStartMs <= 0) {
            if (onComplete != null) {
                runOnUiThread(onComplete);
            }
            return;
        }

        completeRunningTaskSync(task, System.currentTimeMillis());

        if (schedule != null) {
            ReminderNotifier.cancel(mApp, schedule.id);
        }
        if (stopSchedule && schedule != null) {
            new TaskScheduleRepository(mDb).disableScheduleSync(
                schedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
        }

        onPostComplete();
        if (onComplete != null) {
            runOnUiThread(onComplete);
        }
    }

    /** 子类可覆盖：完成后追加行为（如 recompute）。默认空。 */
    protected void onPostComplete() {}

    /**
     * 短完成流程模板。
     */
    protected final void shortCompleteFlow(long taskId, boolean stopSchedule,
        boolean convertToChore, TaskScheduleEntity schedule, Runnable onComplete) {
        performShortCompletionSync(taskId, stopSchedule, convertToChore, schedule);
        onPostComplete();
        if (onComplete != null) {
            runOnUiThread(onComplete);
        }
    }

    /**
     * 归档流程模板。
     */
    protected final void archiveTaskFlow(long taskId, TaskScheduleEntity schedule, Runnable onComplete) {
        archiveTaskSync(taskId, schedule);
        onPostComplete();
        if (onComplete != null) {
            runOnUiThread(onComplete);
        }
    }
}
