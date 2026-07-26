package com.nearby.justnow.ui.base;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
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

    /** 完成后任务拍照提醒事件 */
    protected final SingleLiveEvent<TaskEntity> mShowPhotoPromptEvent = new SingleLiveEvent<>();
    public androidx.lifecycle.LiveData<TaskEntity> getShowPhotoPromptEvent() { return mShowPhotoPromptEvent; }

    protected BaseTaskViewModel(JustNowApplication app) {
        super(app);
    }

    /**
     * 统一任务完成流程（Sync 方法不应在主线程调用）。
     *
     * @param task               当前任务
     * @param schedule           当前活跃安排（可为 null）
     * @param stopSchedule       是否停止当前安排
     * @param keepTimelineRecord 是否在时间线留记录（正常完成=true，短完成=false）
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
            new ReminderScheduler(mApp).cancel(schedule.id, schedule.scheduledTime);
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

    /** 子类可覆盖：完成后追加行为（如 recompute）。默认空。 */
    protected void onPostComplete() {}

    /**
     * 统一任务完成流程模板（正常完成，时间线留记录）。
     * @deprecated 改用 {@link #completeTaskUnified}
     */
    protected final void completeTaskFlow(TaskEntity task, TaskScheduleEntity schedule,
                                           boolean stopSchedule, Runnable onComplete) {
        completeTaskUnified(task, schedule, stopSchedule, true, onComplete);
    }

    /** 供 TaskExecutionAutoCompleter 等旧调用方使用，内部委托给 completeTaskUnified。 */
    protected void completeRunningTaskSync(TaskEntity task, long endMs) {
        TaskExecutionAutoCompleter.completeRunningTaskSync(
            mApp.getTaskRepository(), mApp.getTaskExecutionRepository(), task, endMs);
    }

    /**
     * 短完成（提前结束）：转琐碎后走统一完成流程，不在时间线留记录。
     */
    protected void performShortCompletionSync(long taskId, boolean stopSchedule,
        boolean convertToChore, TaskScheduleEntity schedule) {
        TaskRepository taskRepo = mApp.getTaskRepository();
        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null) return;

        long startMs = task.executingStartMs; // 转换前保存，convertToChore 会清零
        if (convertToChore) {
            taskRepo.convertToChoreSync(taskId);
            task = taskRepo.getTaskByIdSync(taskId);
            if (task == null) return;
            task.executingStartMs = startMs; // 恢复，供 completeTaskUnified 写执行记录
        }

        completeTaskUnified(task, schedule, stopSchedule, false, null);
    }

    /**
     * 短完成流程模板。
     */
    protected final void shortCompleteFlow(long taskId, boolean stopSchedule,
        boolean convertToChore, TaskScheduleEntity schedule, Runnable onComplete) {
        performShortCompletionSync(taskId, stopSchedule, convertToChore, schedule);
        if (onComplete != null) runOnUiThread(onComplete);
    }

    /**
     * 统一归档任务流程（Sync 方法不应在主线程调用）
     */
    protected void archiveTaskSync(long taskId, TaskScheduleEntity schedule) {
        TaskRepository taskRepo = mApp.getTaskRepository();
        TaskScheduleRepository scheduleRepo = mApp.getTaskScheduleRepository();

        if (schedule != null) {
            ReminderNotifier.cancel(mApp, schedule.id);
        }

        ReminderScheduler.cancelOvertimeCheck(mApp, taskId);
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
