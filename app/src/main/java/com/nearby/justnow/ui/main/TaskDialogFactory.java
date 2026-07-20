package com.nearby.justnow.ui.main;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.scheduler.TaskScheduleMatcher;
import com.nearby.justnow.ui.base.BaseTaskViewModel;

import java.util.function.Consumer;

/**
 * 任务对话框工厂 — 接收 Context 与 Callback，承载 MainFragment 中所有与对话框相关的逻辑。
 * <p>
 * 方法体源自 MainFragment，仅将 requireContext() → mContext、getString() → mContext.getString()，
 * requireView() → dialog.getWindow().getDecorView()，ViewModel 调用 → mCallback.xxx()。
 */
public class TaskDialogFactory {

    private final Context mContext;
    private final Callback mCallback;

    public TaskDialogFactory(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    /** 回调接口：工厂所需的 Fragment / ViewModel 操作由 MainFragment 实现并提供。 */
    public interface Callback {
        int getThemeColor();
        boolean isInActivePeriod();
        void navigateToSchedule(long taskId);
        void startTaskNow(long taskId, Consumer<TaskStartResult> onResult);
        void checkTaskStart(long taskId, Consumer<TaskStartResult> onResult);
        String getScheduleText(TaskScheduleEntity schedule);
        void ignoreSchedule(long scheduleId, long taskId, Runnable onSuccess);
        void completeTask(long taskId, boolean stopSchedule, Runnable onResult);
        /** 扩展点：时间线完成对话框展示时回调，默认空实现 */
        default void onTimelineCompletionDialogShown(MainViewModel.TimelineTaskState state) {}
        /** 扩展点：琐碎完成对话框展示时回调，默认空实现 */
        default void onChoreCompletionDialogShown(MainViewModel.TimelineTaskState state) {}
        /** 扩展点：专注任务完成流程结束时回调，默认空实现 */
        default void onFocusTaskCompleted(TaskEntity task, boolean stopSchedule) {}
        void loadTimelineTaskState(long taskId,
                                   Consumer<MainViewModel.TimelineTaskState> callback);
        void loadActiveSchedule(long taskId,
                                Consumer<TaskScheduleEntity> callback);
        void loadActiveScheduleForShortCompletion(long taskId,
                                                  Consumer<TaskScheduleEntity> callback);
        void archiveTask(long taskId);
        void resetChecklistState(long taskId, Runnable onCompleted);
        void cancelShortCompletion();
        void shortCompleteDirect(long taskId, boolean stopSchedule, Runnable onComplete);
        void shortCompleteAndConvertToChore(long taskId, boolean stopSchedule,
                                            Runnable onComplete);
    }

    // ---- 内部工具（不依赖 Callback）----

    private String getFocusText(int focusMinutes) {
        return com.nearby.justnow.ui.engine.FocusDurationOptions.format(
            mContext.getResources(), focusMinutes);
    }

    private String getStartBlockReason(TaskStartResult result) {
        int messageRes = R.string.s_start_blocked_missing;
        if (result.code == TaskStartResult.BLOCKED_RUNNING) {
            messageRes = R.string.s_start_blocked_running;
        } else if (result.code == TaskStartResult.BLOCKED_OUT_OF_PERIOD) {
            messageRes = R.string.s_start_blocked_out_of_period;
        } else if (result.code == TaskStartResult.BLOCKED_TIME_NOT_ENOUGH) {
            messageRes = R.string.s_start_blocked_time_not_enough;
        }
        return mContext.getString(messageRes);
    }

    // ================================================================
    //  公开入口
    // ================================================================

    public void showTaskDetailDialog(TaskEntity task, TagEntity tag,
                                     TaskScheduleEntity schedule) {
        StringBuilder messageBuilder = new StringBuilder();
        if (task.detail != null && !task.detail.trim().isEmpty()) {
            messageBuilder.append(task.detail.trim()).append("\n\n");
        }
        if (tag != null) {
            messageBuilder.append(mContext.getString(R.string.s_task_detail_tag, tag.name))
                .append("\n");
        }
        messageBuilder.append(mContext.getString(R.string.s_task_detail_focus,
            getFocusText(task.focusMinutes)));
        String scheduleText = task.focusMinutes > 0
            && isScheduleActionable(schedule)
            ? mCallback.getScheduleText(schedule) : "";
        if (!scheduleText.isEmpty()) {
            messageBuilder.append("\n")
                .append(mContext.getString(R.string.s_task_detail_schedule, scheduleText));
        }
        String baseMessage = messageBuilder.toString();
        boolean isFocusTask = task.focusMinutes > 0;
        boolean showScheduleAsPrimary = isFocusTask && !mCallback.isInActivePeriod();

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
            .setTitle(task.content)
            .setMessage(baseMessage)
            .setNeutralButton(R.string.s_cancel, null);
        if (isFocusTask) {
            builder.setPositiveButton(showScheduleAsPrimary
                    ? R.string.s_schedule_task : R.string.s_start_now, null)
                .setNegativeButton(showScheduleAsPrimary
                    ? R.string.s_start_now : R.string.s_schedule_task, null);
        } else {
            builder.setPositiveButton(R.string.s_start_now, null);
        }
        AlertDialog dialog = builder.show();

        TaskStartResult initialResult = showScheduleAsPrimary
            ? new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD) : null;
        applyTaskDetailActions(dialog, task, baseMessage, isFocusTask,
            showScheduleAsPrimary, initialResult, schedule);
        mCallback.checkTaskStart(task.id, result -> {
            boolean scheduleAsPrimary = isFocusTask
                && result.code != TaskStartResult.OK
                && (!mCallback.isInActivePeriod()
                    || result.code == TaskStartResult.BLOCKED_OUT_OF_PERIOD);
            applyTaskDetailActions(dialog, task, baseMessage, isFocusTask,
                scheduleAsPrimary, result, schedule);
        });
    }

    /** 左侧时间线已安排任务点击对话框 */
    public void handleTimelineScheduledTaskClick(long taskId) {
        mCallback.loadTimelineTaskState(taskId, state -> {
            TaskEntity task = state.task;
            if (task == null) return;
            mCallback.loadActiveSchedule(taskId, schedule -> {
                if (schedule == null || !schedule.enabled) return;

                StringBuilder messageBuilder = new StringBuilder();
                if (task.detail != null && !task.detail.trim().isEmpty()) {
                    messageBuilder.append(task.detail.trim()).append("\n\n");
                }
                messageBuilder.append(mContext.getString(R.string.s_task_detail_focus,
                    getFocusText(task.focusMinutes)));
                String scheduleText = task.focusMinutes > 0
                    ? mCallback.getScheduleText(schedule) : "";
                if (!scheduleText.isEmpty()) {
                    messageBuilder.append("\n")
                        .append(mContext.getString(R.string.s_task_detail_schedule,
                            scheduleText));
                }

                AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setTitle(task.content)
                    .setMessage(messageBuilder.toString())
                    .setPositiveButton(R.string.s_start_now, null)
                    .setNegativeButton(R.string.s_ignore, null)
                    .setNeutralButton(R.string.s_cancel, null)
                    .show();

                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                applyDialogActionStyle(positiveButton);
                applyDialogActionStyle(negativeButton);
                positiveButton.setOnClickListener(v ->
                    mCallback.startTaskNow(task.id, result ->
                        handleStartTaskResult(dialog, result, task)));
                negativeButton.setOnClickListener(v ->
                    mCallback.ignoreSchedule(schedule.id, task.id, dialog::dismiss));
            });
        });
    }

    /** 仅标题任务完成事件分发 */
    public void handleOnlyTitleTaskComplete(long taskId) {
        mCallback.loadTimelineTaskState(taskId, state -> {
            TaskEntity task = state.task;
            if (task == null) return;
            if (task.focusMinutes == 0) {
                showChoreCompletionDialog(state);
            } else {
                showTimelineCompletionDialog(state);
            }
        });
    }

    /** 清单状态变化确认弹窗 */
    public void showChecklistStateConfirmDialog(long taskId, Runnable onConfirmed) {
        new android.app.AlertDialog.Builder(mContext)
            .setTitle(R.string.s_complete_task)
            .setMessage(R.string.s_checklist_state_changed)
            .setPositiveButton(R.string.s_save, (d, w) -> onConfirmed.run())
            .setNegativeButton(R.string.s_not_save, (d, w) -> {
                mCallback.resetChecklistState(taskId, onConfirmed);
            })
            .setNeutralButton(R.string.s_cancel, null)
            .show();
    }

    /** 未执行任务点击入口（开始/安排对话框） */
    public void handleTaskStart(long taskId) {
        mCallback.loadTimelineTaskState(taskId, state -> {
            TaskEntity task = state.task;
            if (task == null) return;
            mCallback.loadActiveSchedule(taskId,
                schedule -> showTaskDetailDialog(task, null, schedule));
        });
    }

    /** 左侧时间线完成对话框 */
    public void showTimelineCompletionDialog(MainViewModel.TimelineTaskState state) {
        TaskEntity task = state.task;
        if (task == null) return;

        int completeLabel = state.hasRecurringSchedule
            ? R.string.s_complete_once : R.string.s_complete;
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
            .setTitle(task.content)
            .setPositiveButton(completeLabel, (d, w) ->
                handleFocusTaskCompletion(task, false))
            .setNegativeButton(R.string.s_cancel, null);
        if (state.hasRecurringSchedule) {
            builder.setNeutralButton(R.string.s_complete_and_stop_schedule, (d, w) ->
                handleFocusTaskCompletion(task, true));
        }
        builder.show();
        mCallback.onTimelineCompletionDialogShown(state);
    }

    /** 琐碎任务完成对话框 */
    public void showChoreCompletionDialog(MainViewModel.TimelineTaskState state) {
        TaskEntity task = state.task;
        if (task == null) return;
        if (state.hasAnyExecution) {
            new AlertDialog.Builder(mContext)
                .setTitle(task.content)
                .setMessage(R.string.s_complete_this_execution)
                .setPositiveButton(R.string.s_complete, (d, w) ->
                    mCallback.completeTask(task.id, false, null))
                .setNegativeButton(R.string.s_cancel, null)
                .show();
            mCallback.onChoreCompletionDialogShown(state);
            return;
        }

        new AlertDialog.Builder(mContext)
            .setTitle(task.content)
            .setMessage(R.string.s_task_still_needed)
            .setPositiveButton(R.string.s_complete, (d, w) ->
                mCallback.completeTask(task.id, false, null))
            .setNegativeButton(R.string.s_no_longer_needed, (d, w) ->
                mCallback.archiveTask(task.id))
            .setCancelable(true)
            .show();
        mCallback.onChoreCompletionDialogShown(state);
    }

    /** 判断安排是否生效（安排已启用且匹配今日） */
    public boolean isScheduleActionable(@Nullable TaskScheduleEntity schedule) {
        return schedule != null && schedule.enabled
            && TaskScheduleMatcher.matchesToday(schedule);
    }

    // ================================================================
    //  内部辅助
    // ================================================================

    private void applyTaskDetailActions(AlertDialog dialog, TaskEntity task,
                                        String baseMessage, boolean isFocusTask,
                                        boolean scheduleAsPrimary,
                                        @Nullable TaskStartResult startResult,
                                        @Nullable TaskScheduleEntity schedule) {
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        boolean canStart = startResult != null && startResult.code == TaskStartResult.OK;

        if (isFocusTask && scheduleAsPrimary) {
            configureScheduleButton(positiveButton, dialog, task, schedule, true);
            configureStartButton(negativeButton, dialog, task.id, false, false, task);
        } else {
            configureStartButton(positiveButton, dialog, task.id, canStart, true, task);
            if (isFocusTask) {
                configureScheduleButton(negativeButton, dialog, task, schedule, false);
            }
        }

        if (startResult == null || startResult.code == TaskStartResult.OK) {
            dialog.setMessage(baseMessage);
            return;
        }
        dialog.setMessage(baseMessage + "\n\n"
            + mContext.getString(R.string.s_start_unavailable_reason,
                getStartBlockReason(startResult)));
    }

    private void configureStartButton(Button startButton, AlertDialog dialog, long taskId,
                                      boolean enabled, boolean primary, TaskEntity task) {
        if (startButton == null) return;
        startButton.setText(R.string.s_start_now);
        applyDialogActionStyle(startButton);
        startButton.setEnabled(enabled);
        startButton.setOnClickListener(v ->
            mCallback.startTaskNow(taskId, result ->
                handleStartTaskResult(dialog, result, task)));
    }

    private void configureScheduleButton(Button scheduleButton, AlertDialog dialog,
                                         TaskEntity task,
                                         @Nullable TaskScheduleEntity schedule,
                                         boolean primary) {
        if (scheduleButton == null) return;
        boolean hasActiveSchedule = isScheduleActionable(schedule);
        scheduleButton.setText(hasActiveSchedule
            ? R.string.s_adjust_schedule : R.string.s_schedule_task);
        applyDialogActionStyle(scheduleButton);
        boolean canSchedule = task != null && task.focusMinutes > 0;
        scheduleButton.setEnabled(canSchedule);
        if (canSchedule) {
            scheduleButton.setOnClickListener(v -> {
                dialog.dismiss();
                mCallback.navigateToSchedule(task.id);
            });
        } else {
            scheduleButton.setOnClickListener(null);
        }
    }

    private void applyDialogActionStyle(Button button) {
        if (button == null) return;
        int themeColor = mCallback.getThemeColor();
        int disabledColor = mContext.getResources().getColor(R.color.text_hint);
        button.setTextColor(new android.content.res.ColorStateList(
            new int[][] {
                new int[] {-android.R.attr.state_enabled},
                new int[] {}
            },
            new int[] { disabledColor, themeColor }));
    }

    private void handleStartTaskResult(AlertDialog dialog, TaskStartResult result,
                                       TaskEntity task) {
        if (result.code == TaskStartResult.OK) {
            dialog.dismiss();
            boolean hasContent = (task.detailMarkdown != null
                && !task.detailMarkdown.isEmpty())
                || task.detailModuleType != null;
            if (hasContent) {
                Intent intent = new Intent(mContext,
                    com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
                intent.putExtra("task_id", task.id);
                mContext.startActivity(intent);
            }
            return;
        }
        int messageRes = R.string.s_start_blocked_missing;
        if (result.code == TaskStartResult.BLOCKED_RUNNING) {
            messageRes = R.string.s_start_blocked_running;
        } else if (result.code == TaskStartResult.BLOCKED_OUT_OF_PERIOD) {
            messageRes = R.string.s_start_blocked_out_of_period;
        } else if (result.code == TaskStartResult.BLOCKED_TIME_NOT_ENOUGH) {
            messageRes = R.string.s_start_blocked_time_not_enough;
        }
        View decorView = dialog.getWindow().getDecorView();
        com.google.android.material.snackbar.Snackbar.make(
            decorView, messageRes,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
    }

    private void handleFocusTaskCompletion(TaskEntity task, boolean stopSchedule) {
        if (task.focusMinutes <= 0) {
            mCallback.completeTask(task.id, stopSchedule, null);
            mCallback.onFocusTaskCompleted(task, stopSchedule);
            return;
        }
        int elapsedMinutes = (int) ((System.currentTimeMillis()
            - task.executingStartMs) / 60000);
        boolean isShort = task.executingStartMs > 0
            && elapsedMinutes < BaseTaskViewModel.SHORT_DURATION_THRESHOLD_MINUTES;
        if (!isShort) {
            mCallback.completeTask(task.id, stopSchedule, null);
            mCallback.onFocusTaskCompleted(task, stopSchedule);
            return;
        }
        int entry = stopSchedule
            ? ShortCompletionDialog.ENTRY_COMPLETE_AND_STOP_SCHEDULE
            : ShortCompletionDialog.ENTRY_COMPLETE_ONCE;
        mCallback.loadActiveScheduleForShortCompletion(task.id, schedule -> {
            boolean hasSchedule = schedule != null;
            ShortCompletionDialog.show(mContext, task.content, entry, hasSchedule,
                new ShortCompletionDialog.Callback() {
                    @Override
                    public void onCancel() {
                        mCallback.cancelShortCompletion();
                    }

                    @Override
                    public void onDirectComplete() {
                        mCallback.shortCompleteDirect(task.id, stopSchedule, null);
                    }

                    @Override
                    public void onConvertToChore() {
                        boolean stop = stopSchedule || hasSchedule;
                        mCallback.shortCompleteAndConvertToChore(task.id, stop, null);
                    }
                });
        });
        mCallback.onFocusTaskCompleted(task, stopSchedule);
    }
}
