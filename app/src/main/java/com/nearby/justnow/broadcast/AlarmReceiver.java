package com.nearby.justnow.broadcast;

import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;

import java.util.List;

import com.nearby.justnow.data.store.CutoffTimeStore;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.scheduler.TaskStartGuard;
import com.nearby.justnow.ui.main.TaskStartResult;

/**
 * 闹钟广播接收器。处理提醒到点、开始任务、延迟操作和每日刷新。
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        long scheduleId = intent.getLongExtra(ReminderScheduler.EXTRA_SCHEDULE_ID, -1);
        long taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1);
        String action = intent.getAction();

        if (ReminderScheduler.ACTION_START_TASK.equals(action)) {
            // 通知"开始"按钮 → 直接 startExecution
            if (taskId > 0) {
                PendingResult pendingResult = goAsync();
                AppDatabase.execute(() -> {
                    try {
                        handleStartTask(context, scheduleId, taskId);
                    } finally {
                        pendingResult.finish();
                    }
                });
            }
        } else if (ReminderNotifier.ACTION_POSTPONE.equals(action)) {
            int postponeMinutes = intent.getIntExtra("postpone_minutes", 0);
            if (scheduleId >= 0 && postponeMinutes > 0) {
                PendingResult pendingResult = goAsync();
                AppDatabase.execute(() -> {
                    try {
                        handlePostpone(context, scheduleId, taskId, postponeMinutes);
                    } finally {
                        pendingResult.finish();
                    }
                });
            }
        } else if (ReminderNotifier.ACTION_IGNORE.equals(action)) {
            // 通知"忽略"按钮 → 关闭通知 + 跳过本次
            if (scheduleId >= 0) {
                PendingResult pendingResult = goAsync();
                AppDatabase.execute(() -> {
                    try {
                        handleIgnore(context, scheduleId, taskId);
                    } finally {
                        pendingResult.finish();
                    }
                });
            }
        } else if (ReminderScheduler.ACTION_DAILY_REFRESH.equals(action)) {
            PendingResult pendingResult = goAsync();
            AppDatabase.execute(() -> {
                try {
                    ReminderScheduler scheduler = new ReminderScheduler(context);
                    scheduler.refreshToday();
                    scheduler.scheduleDailyRefresh();
                } finally {
                    pendingResult.finish();
                }
            });
        } else {
            // ACTION_CHECK_ALARM：闹钟到点 → 发通知
            ReminderNotifier.createChannel(context);
            int scheduledTime = intent.getIntExtra(ReminderScheduler.EXTRA_SCHEDULED_TIME, 0);
            PendingResult pendingResult = goAsync();
            AppDatabase.execute(() -> {
                try {
                    handleAlarm(context, scheduleId, taskId, scheduledTime);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }

    /** 通知"开始"按钮：先停掉执行中任务，再经统一校验后开始执行到点任务。 */
    private void handleStartTask(Context context, long scheduleId, long taskId) {
        JustNowApplication app = (JustNowApplication) context.getApplicationContext();
        TaskRepository taskRepo = app.getTaskRepository();
        TaskScheduleRepository scheduleRepo = app.getTaskScheduleRepository();
        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null || task.isArchived) {
            ReminderNotifier.cancel(context, scheduleId);
            return;
        }
        if (task.executingStartMs > 0 && task.executingEndMs == 0) {
            ReminderNotifier.cancel(context, scheduleId);
            return;
        }
        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule == null || !schedule.enabled) {
            ReminderNotifier.cancel(context, scheduleId);
            return;
        }

        // 主动停掉当前执行中任务
        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        if (runningTask != null) {
            TaskExecutionAutoCompleter.completeRunningTaskSync(taskRepo,
                app.getTaskExecutionRepository(),
                runningTask, System.currentTimeMillis());
        }

        TaskStartResult result = TaskStartGuard.evaluate(context, taskId);
        if (result.code != TaskStartResult.OK) {
            return;
        }

        taskRepo.startExecutionSync(taskId, System.currentTimeMillis());
        // 清除延迟标记（任务已开始，优先窗口取消）
        if (schedule.postponedUntilMs > 0) {
            scheduleRepo.updatePostponedUntil(schedule.id, 0, System.currentTimeMillis());
        }
        ReminderNotifier.cancel(context, scheduleId);
    }

    private void handleAlarm(Context context, long scheduleId, long taskId, int scheduledTime) {
        JustNowApplication app = (JustNowApplication) context.getApplicationContext();
        TaskScheduleRepository scheduleRepo = app.getTaskScheduleRepository();
        TaskRepository taskRepo = app.getTaskRepository();
        TimePeriodRepository periodRepo = app.getTimePeriodRepository();
        ReminderScheduler scheduler = new ReminderScheduler(context);

        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule == null || !schedule.enabled) return;

        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null || task.isArchived || task.executingEndMs != 0) return;

        // 安排任务到点时直接清除截止时间覆盖
        CutoffTimeStore.clearCutoffEndMinute(context);

        boolean alreadyPostponed = scheduler.hasPostponedToday(schedule.id);

        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        boolean hasRunning = runningTask != null;
        boolean isRunningChore = hasRunning && runningTask.focusMinutes == 0;

        // 可延迟条件：未延迟过 且 不是琐碎任务阻塞
        boolean canDelay30 = !alreadyPostponed && !isRunningChore
            && canDelay(scheduledTime, periodRepo);

        ReminderNotifier.send(context, schedule, task, hasRunning, isRunningChore, canDelay30);
    }

    private void handlePostpone(Context context, long scheduleId, long taskId,
                                 int postponeMinutes) {
        JustNowApplication app = (JustNowApplication) context.getApplicationContext();
        ReminderScheduler scheduler = new ReminderScheduler(context);
        TaskScheduleRepository scheduleRepo = app.getTaskScheduleRepository();
        TaskRepository taskRepo = app.getTaskRepository();

        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule == null) return;

        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null) return;

        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        long blockedByTaskId = runningTask != null ? runningTask.id : 0;

        scheduler.postpone(schedule, task, blockedByTaskId, postponeMinutes);

        ReminderNotifier.cancel(context, schedule.id);
    }

    /** 忽略本次提醒：单次安排→禁用，重复安排→记录当天跳过。清除延迟标记。 */
    private void handleIgnore(Context context, long scheduleId, long taskId) {
        JustNowApplication app = (JustNowApplication) context.getApplicationContext();
        TaskScheduleRepository scheduleRepo = app.getTaskScheduleRepository();

        ReminderNotifier.cancel(context, scheduleId);

        // 清除延迟标记（忽略 = 取消优先级）
        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule != null && schedule.postponedUntilMs > 0) {
            scheduleRepo.updatePostponedUntil(scheduleId, 0, System.currentTimeMillis());
        }

        if (scheduleRepo.skipOrDisable(scheduleId)) {
            schedule = scheduleRepo.getScheduleById(scheduleId);
            TaskRepository taskRepo = app.getTaskRepository();
            TaskEntity task = taskRepo.getTaskByIdSync(taskId);
            if (schedule != null && task != null && ReminderScheduler.shouldRegisterAlarm(task)) {
                ReminderScheduler scheduler = new ReminderScheduler(context);
                scheduler.scheduleNextAfterSkip(schedule, task);
            }
        }
    }

    /** 检查延迟30分钟后是否仍在当前时段内（不含容差）。 */
    private static boolean canDelay(int scheduledMinute, TimePeriodRepository periodRepo) {
        ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
        List<TimePeriodEntity> periods = activeGroup.periods;
        if (periods == null || periods.isEmpty()) return false;

        int delayed = scheduledMinute + 30;
        for (TimePeriodEntity period : periods) {
            if (scheduledMinute >= period.startMinute && scheduledMinute < period.endMinute) {
                return delayed < period.endMinute;
            }
        }
        return false;
    }
}
