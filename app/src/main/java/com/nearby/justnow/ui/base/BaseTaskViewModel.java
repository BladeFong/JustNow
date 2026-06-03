package com.nearby.justnow.ui.base;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.store.ChoreHiddenTodayStore;
import com.nearby.justnow.scheduler.ReminderScheduler;

/**
 * Task 相关 ViewModel 基类 — 封装跨 ViewModel 的同步业务方法。
 * 仅供 MainViewModel / ReminderDetailViewModel 等任务态 ViewModel 继承。
 */
public abstract class BaseTaskViewModel extends BaseViewModel {

    /** 完成前确认回调接口 */
    public interface PreCompleteConfirmCallback {
        void onConfirmNeeded(long taskId, String confirmType, Runnable onConfirmed);
    }

    /** 确认类型：清单状态变化 */
    public static final String CONFIRM_TYPE_CHECKLIST_STATE = "checklist_state";

    /** &lt; 15min 阈值：本次完成耗时低于该值时触发"耗时较短"对话框。 */
    public static final int SHORT_DURATION_THRESHOLD_MINUTES = 15;

    protected BaseTaskViewModel(JustNowApplication app) {
        super(app);
    }

    /**
     * 统一任务完成流程（Sync 方法不应在主线程调用）
     */
    protected void completeRunningTaskSync(TaskEntity task, long endMs) {
        TaskExecutionAutoCompleter.completeRunningTaskSync(
            mApp.getTaskRepository(), mApp.getTaskExecutionRepository(), task, endMs);
    }

    /**
     * 统一短完成流程（Sync 方法不应在主线程调用）
     */
    protected void performShortCompletionSync(long taskId, boolean stopSchedule,
        boolean convertToChore, TaskScheduleEntity schedule) {
        TaskRepository taskRepo = mApp.getTaskRepository();
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
            mApp.getTaskScheduleRepository().disableScheduleSync(
                schedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
        }

        new ChoreHiddenTodayStore(mApp).hideForToday(taskId);
    }

    /**
     * 统一归档任务流程（Sync 方法不应在主线程调用）
     */
    protected void archiveTaskSync(long taskId, TaskScheduleEntity schedule) {
        TaskRepository taskRepo = mApp.getTaskRepository();
        TaskScheduleRepository scheduleRepo = mApp.getTaskScheduleRepository();

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
        return mApp.getTaskChecklistRepository().hasAnyStateSync(taskId);
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
            mApp.getTaskScheduleRepository().disableScheduleSync(
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
