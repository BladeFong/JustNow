package com.nearby.justnow.broadcast;

import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TimePeriodRepository;

import java.util.List;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
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
                AppDatabase.execute(() ->
                    handlePostpone(context, scheduleId, taskId, postponeMinutes));
            }
        } else if (ReminderScheduler.ACTION_DAILY_REFRESH.equals(action)) {
            AppDatabase.execute(() -> {
                ReminderScheduler scheduler = new ReminderScheduler(context);
                scheduler.refreshToday();
                scheduler.scheduleDailyRefresh();
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
        AppDatabase db = AppDatabase.getInstance(context);
        TaskRepository taskRepo = new TaskRepository(db);
        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null || task.isArchived) {
            // 任务已不存在或已归档：属任务退出语义，连带清掉当天剩余所有 schedule 的闹钟
            ReminderNotifier.cancel(context, scheduleId);
            return;
        }
        if (task.executingStartMs > 0 && task.executingEndMs == 0) {
            // 任务执行中：仅当前提醒已无意义，单 schedule 收尾；task 还在，不动其他 schedule
            ReminderNotifier.cancel(context, scheduleId);
            return;
        }
        TaskScheduleRepository scheduleRepo = new TaskScheduleRepository(db);
        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule == null || !schedule.enabled) {
            // 单 schedule 已禁用：仅清当前通知；task 仍可能有其他 schedule，不能波及
            ReminderNotifier.cancel(context, scheduleId);
            return;
        }

        // 主动停掉当前执行中任务
        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        if (runningTask != null) {
            TaskExecutionAutoCompleter.completeRunningTaskSync(taskRepo,
                new TaskExecutionRepository(db),
                runningTask, System.currentTimeMillis());
        }

        TaskStartResult result = TaskStartGuard.evaluate(context, taskId);
        if (result.code != TaskStartResult.OK) {
            return;
        }

        taskRepo.startExecutionSync(taskId, System.currentTimeMillis());
        // 任务已开始执行：本次通知收尾即可；task 仍在，不该清当天其他 schedule（bug 修复）
        ReminderNotifier.cancel(context, scheduleId);
    }

    private void handleAlarm(Context context, long scheduleId, long taskId, int scheduledTime) {
        AppDatabase db = AppDatabase.getInstance(context);
        TaskScheduleRepository scheduleRepo = new TaskScheduleRepository(db);
        TaskRepository taskRepo = new TaskRepository(db);
        ReminderScheduler scheduler = new ReminderScheduler(context);
        TimePeriodRepository periodRepo = new TimePeriodRepository(db);

        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule == null || !schedule.enabled) return;

        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null || task.isArchived || task.executingEndMs != 0) return;

        boolean alreadyPostponed = scheduler.hasPostponedToday(schedule.id);

        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        boolean hasRunning = runningTask != null;
        boolean isRunningChore = hasRunning && runningTask.focusMinutes == 0;

        boolean canPostpone15 = false;
        boolean canPostpone30 = false;
        if (!alreadyPostponed && hasRunning && !isRunningChore) {
            canPostpone15 = canPostpone(scheduledTime, 15, periodRepo);
            canPostpone30 = canPostpone(scheduledTime, 30, periodRepo);
        }

        ReminderNotifier.send(context, schedule, task, hasRunning, isRunningChore,
            canPostpone15, canPostpone30);
    }

    private void handlePostpone(Context context, long scheduleId, long taskId,
                                 int postponeMinutes) {
        AppDatabase db = AppDatabase.getInstance(context);
        ReminderScheduler scheduler = new ReminderScheduler(context);
        TaskScheduleRepository scheduleRepo = new TaskScheduleRepository(db);
        TaskRepository taskRepo = new TaskRepository(db);

        TaskScheduleEntity schedule = scheduleRepo.getScheduleById(scheduleId);
        if (schedule == null) return;

        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null) return;

        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        long blockedByTaskId = runningTask != null ? runningTask.id : 0;

        scheduler.postpone(schedule, task, blockedByTaskId, postponeMinutes);
        // postpone 已 setAlarm 新时间：单 schedule 收尾，不动当天其他 schedule
        ReminderNotifier.cancel(context, schedule.id);
    }

    /** 检查延迟 postponeMinutes 后是否仍在当前时段 + 15min 容差内。 */
    private static boolean canPostpone(int scheduledMinute, int postponeMinutes,
                                       TimePeriodRepository periodRepo) {
        ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
        List<TimePeriodEntity> periods = activeGroup.periods;
        if (periods == null || periods.isEmpty()) return false;

        int postponed = scheduledMinute + postponeMinutes;
        for (TimePeriodEntity period : periods) {
            if (scheduledMinute >= period.startMinute && scheduledMinute < period.endMinute) {
                return postponed <= period.endMinute + 15;
            }
        }
        return false;
    }
}
