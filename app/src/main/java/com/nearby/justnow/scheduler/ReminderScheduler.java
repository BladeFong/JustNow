package com.nearby.justnow.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.nearby.justnow.broadcast.AlarmReceiver;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskSchedulePostponeRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;

import java.util.Calendar;
import java.util.List;

/**
 * 基于 AlarmManager 的提醒闹钟调度器。
 */
public class ReminderScheduler {

    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_SCHEDULE_TYPE = "schedule_type";
    public static final String EXTRA_SCHEDULED_TIME = "scheduled_time";
    public static final String EXTRA_SCHEDULE_VALUE = "schedule_value";
    public static final String ACTION_CHECK_ALARM = "com.nearby.justnow.ACTION_CHECK_ALARM";
    public static final String ACTION_START_TASK = "com.nearby.justnow.ACTION_START_TASK";
    public static final String ACTION_DAILY_REFRESH = "com.nearby.justnow.ACTION_DAILY_REFRESH";
    private static final int DAILY_REFRESH_CODE = 0;

    private final Context mAppContext;
    private final AlarmManager mAlarmManager;
    private final TaskScheduleRepository mScheduleRepo;
    private final TaskSchedulePostponeRepository mPostponeRepo;
    private final TaskRepository mTaskRepo;

    public ReminderScheduler(Context context) {
        mAppContext = context.getApplicationContext();
        mAlarmManager = (AlarmManager) mAppContext.getSystemService(Context.ALARM_SERVICE);
        AppDatabase db = AppDatabase.getInstance(context);
        mScheduleRepo = new TaskScheduleRepository(db);
        mPostponeRepo = new TaskSchedulePostponeRepository(db);
        mTaskRepo = new TaskRepository(db);
    }

    /** 注册安排的闹钟，triggerMs 由调用方传入。 */
    public void schedule(TaskScheduleEntity schedule, TaskEntity task, long triggerMs) {
        if (schedule == null || task == null || triggerMs <= System.currentTimeMillis()) return;
        setAlarm(schedule, task, triggerMs);
    }

    /** 任务是否应该注册闹钟。归档/琐碎/正在执行中的任务不注册。 */
    public static boolean shouldRegisterAlarm(TaskEntity task) {
        if (task == null || task.isArchived || task.focusMinutes <= 0) return false;
        if (task.executingStartMs > 0 && task.executingEndMs == 0) return false;
        return true;
    }

    /** 取消单个安排的闹钟。 */
    public void cancel(long scheduleId, int scheduledTime) {
        PendingIntent pi = buildPendingIntent(scheduleId, 0, 0, scheduledTime);
        if (pi != null) mAlarmManager.cancel(pi);
    }

    /** 延迟本次提醒：写入记录 + 取消当前闹钟 + 注册新闹钟。 */
    public void postpone(TaskScheduleEntity schedule, TaskEntity task,
                         long blockedByTaskId, int postponeMinutes) {
        long todayMs = TaskSchedulePostponeRepository.todayStartMs();
        mPostponeRepo.recordPostpone(schedule.id, task.id, blockedByTaskId,
            postponeMinutes, todayMs);
        cancel(schedule.id, schedule.scheduledTime);

        long newTriggerMs = System.currentTimeMillis() + postponeMinutes * 60000L;
        setAlarm(schedule, task, newTriggerMs);
    }

    /**
     * 每日 3 点全量刷新：
     * 1. cancelAll 安全兜底
     * 2. disableExpiredOnceToday（TYPE_ONCE 今日已过 → EXPIRED）
     * 3. getTodayTriggers → 注册当天闹钟
     */
    public void refreshToday() {
        long now = System.currentTimeMillis();
        long todayStartMs = todayStartMs();

        // Step 1: cancel all（含 disabled 残留，避免 disable 后遗留闹钟）
        List<TaskScheduleEntity> allSchedules = mScheduleRepo.getAllSchedulesSync();
        if (allSchedules != null) {
            for (TaskScheduleEntity s : allSchedules) {
                cancel(s.id, s.scheduledTime);
            }
        }

        // Step 2: disable expired TYPE_ONCE
        mScheduleRepo.disableExpiredOnceToday(todayStartMs);

        // Step 3: register today's triggers
        List<TaskScheduleEntity> enabled = mScheduleRepo.getAllEnabledSchedulesSync();
        if (enabled != null) {
            for (TaskScheduleEntity schedule : enabled) {
                // 只对今日命中的安排注册闹钟，避免把几天/几周后的项塞进 AlarmManager
                if (!TaskScheduleMatcher.matchesToday(schedule)) continue;

                TaskEntity task = mTaskRepo.getTaskByIdSync(schedule.taskId);
                if (!shouldRegisterAlarm(task)) continue;

                long triggerMs = computeNextMatch(schedule, now);
                if (triggerMs > now) {
                    setAlarm(schedule, task, triggerMs);
                }
            }
        }
    }

    /** 检查某安排当天是否已延迟过。 */
    public boolean hasPostponedToday(long scheduleId) {
        return mPostponeRepo.hasPostponedToday(scheduleId,
            TaskSchedulePostponeRepository.todayStartMs());
    }

    /**
     * 计算安排的下一次触发时间戳（毫秒）；委托 {@link TaskScheduleMatcher}，保留 public 静态入口以兼容现有调用方。
     */
    public static long computeNextMatch(TaskScheduleEntity schedule, long afterMs) {
        return TaskScheduleMatcher.computeNextMatch(schedule, afterMs);
    }

    private void setAlarm(TaskScheduleEntity schedule, TaskEntity task, long triggerMs) {
        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_CHECK_ALARM);
        intent.putExtra(EXTRA_SCHEDULE_ID, schedule.id);
        intent.putExtra(EXTRA_TASK_ID, task.id);
        intent.putExtra(EXTRA_SCHEDULE_TYPE, schedule.scheduleType);
        intent.putExtra(EXTRA_SCHEDULED_TIME, schedule.scheduledTime);
        intent.putExtra(EXTRA_SCHEDULE_VALUE, schedule.scheduleValue);

        int requestCode = (int) ((schedule.id * 31 + schedule.scheduledTime) & 0x7FFFFFFF);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
    }

    private PendingIntent buildPendingIntent(long scheduleId, long taskId,
                                              int scheduleType, int scheduledTime) {
        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_CHECK_ALARM);
        intent.putExtra(EXTRA_SCHEDULE_ID, scheduleId);
        intent.putExtra(EXTRA_TASK_ID, taskId);
        intent.putExtra(EXTRA_SCHEDULE_TYPE, scheduleType);
        intent.putExtra(EXTRA_SCHEDULED_TIME, scheduledTime);

        int requestCode = (int) ((scheduleId * 31 + scheduledTime) & 0x7FFFFFFF);
        return PendingIntent.getBroadcast(mAppContext, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 注册每日凌晨 3 点全量刷新闹钟。 */
    public void scheduleDailyRefresh() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 3);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long triggerMs = cal.getTimeInMillis();
        if (triggerMs <= System.currentTimeMillis()) {
            triggerMs += AlarmManager.INTERVAL_DAY;
        }

        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_DAILY_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext, DAILY_REFRESH_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi);
    }

    private static long todayStartMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
