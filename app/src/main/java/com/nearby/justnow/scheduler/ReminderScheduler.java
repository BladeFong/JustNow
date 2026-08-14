package com.nearby.justnow.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.broadcast.AlarmReceiver;
import com.nearby.justnow.data.dao.TaskScheduleSkipDao;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodNameKey;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskSchedulePostponeRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.util.DateUtils;

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
    public static final String ACTION_OVERTIME_CHECK = "com.nearby.justnow.ACTION_OVERTIME_CHECK";
    public static final String EXTRA_OVERTIME_TASK_ID = "overtime_task_id";
    public static final String ACTION_DAILY_UNFINISHED_CHECK = "com.nearby.justnow.ACTION_DAILY_UNFINISHED_CHECK";
    public static final String EXTRA_CHECK_PHASE = "check_phase";
    public static final String ACTION_PERIOD_END = "com.nearby.justnow.ACTION_PERIOD_END";
    public static final String EXTRA_PERIOD_KEY = "period_key";
    private static final int OVERTIME_REQUEST_CODE_BASE = 9000;
    private static final int UNFINISHED_CHECK_REQUEST_CODE = 5000;
    private static final int PERIOD_END_REQUEST_CODE_BASE = 5100;
    private static final int DAILY_REFRESH_CODE = 0;

    private final Context mAppContext;
    private final AlarmManager mAlarmManager;
    private final TaskScheduleRepository mScheduleRepo;
    private final TaskSchedulePostponeRepository mPostponeRepo;
    private final TaskScheduleSkipDao mSkipDao;
    private final TaskRepository mTaskRepo;

    public ReminderScheduler(Context context) {
        mAppContext = context.getApplicationContext();
        mAlarmManager = (AlarmManager) mAppContext.getSystemService(Context.ALARM_SERVICE);
        JustNowApplication app = (JustNowApplication) mAppContext;
        mScheduleRepo = app.getTaskScheduleRepository();
        mPostponeRepo = app.getTaskSchedulePostponeRepository();
        mSkipDao = app.getTaskScheduleSkipDao();
        mTaskRepo = app.getTaskRepository();
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
        long todayMs = DateUtils.todayStartMs();
        mPostponeRepo.recordPostpone(schedule.id, task.id, blockedByTaskId,
            postponeMinutes, todayMs);
        cancel(schedule.id, schedule.scheduledTime);

        long newTriggerMs = System.currentTimeMillis() + postponeMinutes * 60000L;
        setAlarm(schedule, task, newTriggerMs);
    }

    /**
     * 每日 3 点全量刷新：
     * 1. cancelAll 安全兜底
     * 2. disableExpiredOnceToday（TYPE_ONCE 今日已过 → 禁用，与忽略统一）
     * 3. getTodayTriggers → 注册当天闹钟
     */
    public void refreshToday() {
        long now = System.currentTimeMillis();
        long todayStartMs = DateUtils.todayStartMs();

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

        // 每日续期时段结束闹钟
        schedulePeriodEndChecks();
    }

    /** 忽略本次后重新调度下一次（仅重复安排调用）。排除已跳过日期。 */
    public void scheduleNextAfterSkip(TaskScheduleEntity schedule, TaskEntity task) {
        long now = System.currentTimeMillis();
        long lastSkippedDateMs = mSkipDao.getLastSkippedDateMs(schedule.id);
        long afterMs = Math.max(now, lastSkippedDateMs + 86400000L);

        long triggerMs = TaskScheduleMatcher.computeNextMatch(schedule, afterMs);
        if (triggerMs > now) {
            setAlarm(schedule, task, triggerMs);
        }
    }

    /** 检查某安排当天是否已延迟过。 */
    public boolean hasPostponedToday(long scheduleId) {
        return mPostponeRepo.hasPostponedToday(scheduleId,
            DateUtils.todayStartMs());
    }

    /**
     * 计算安排的下一次触发时间戳（毫秒）；委托 {@link TaskScheduleMatcher}，保留 public 静态入口以兼容现有调用方。
     */
    public static long computeNextMatch(TaskScheduleEntity schedule, long afterMs) {
        return TaskScheduleMatcher.computeNextMatch(schedule, afterMs);
    }

    private void setAlarm(TaskScheduleEntity schedule, TaskEntity task, long triggerMs) {
        cancel(schedule.id, schedule.scheduledTime);

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
        setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMs, pi);
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
        cancelDailyRefresh();

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
        setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMs, pi);
    }

    /** 取消每日刷新闹钟。 */
    public void cancelDailyRefresh() {
        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_DAILY_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext, DAILY_REFRESH_CODE, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) mAlarmManager.cancel(pi);
    }

    /** 为指定时段注册/续期下一次结束闹钟。 */
    public void schedulePeriodEndAlarm(String periodKey, int endMinute) {
        if (periodKey == null || endMinute <= 0) return;
        cancelPeriodEndAlarm(periodKey);
        setPeriodEndAlarm(periodKey, endMinute);
        if (PeriodNameKey.EVENING.equalsIgnoreCase(periodKey)) {
            cancelUnfinishedCheckAlarm(1);
            long phase1Ms = triggerMsFromMinute(endMinute - 30);
            if (phase1Ms > System.currentTimeMillis()) {
                setUnfinishedCheckAlarm(1, phase1Ms);
            }
        }
    }

    /** 查找当前活跃时段组并为指定 periodKey 续期下一次结束闹钟。 */
    public void schedulePeriodEndAlarm(String periodKey) {
        if (periodKey == null) return;
        JustNowApplication app = (JustNowApplication) mAppContext;
        TimePeriodRepository periodRepo = app.getTimePeriodRepository();
        ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
        if (activeGroup == null || activeGroup.periods == null) return;
        for (TimePeriodEntity p : activeGroup.periods) {
            if (periodKey.equalsIgnoreCase(p.nameKey)) {
                schedulePeriodEndAlarm(p.nameKey, p.endMinute);
                break;
            }
        }
    }

    /** 取消指定时段结束闹钟。 */
    public void cancelPeriodEndAlarm(String periodKey) {
        if (periodKey == null) return;
        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_PERIOD_END);
        intent.putExtra(EXTRA_PERIOD_KEY, periodKey);
        int requestCode = PERIOD_END_REQUEST_CODE_BASE + Math.abs(periodKey.hashCode() & 0x7FFF);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) mAlarmManager.cancel(pi);
    }

    /** 取消所有已知时段的结束闹钟及时机1未处理检查闹钟。 */
    public void cancelAllPeriodEndAlarms() {
        String[] keys = {PeriodNameKey.MORNING, PeriodNameKey.NOON, PeriodNameKey.AFTERNOON,
                         PeriodNameKey.DINNER, PeriodNameKey.EVENING};
        for (String key : keys) {
            cancelPeriodEndAlarm(key);
        }
        cancelUnfinishedCheckAlarm(1);
        cancelUnfinishedCheckAlarm(2);
    }

    /** 注册每时段结束通知闹钟。跳 NOON/DINNER。 */
    public void schedulePeriodEndChecks() {
        cancelAllPeriodEndAlarms();

        JustNowApplication app = (JustNowApplication) mAppContext;
        TimePeriodRepository periodRepo = app.getTimePeriodRepository();
        ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
        if (activeGroup == null || activeGroup.periods == null || activeGroup.periods.isEmpty()) return;
        List<TimePeriodEntity> sortedPeriods = com.nearby.justnow.ui.engine.TimeRemainingCalculator.sortPeriods(
            activeGroup.periods);

        for (TimePeriodEntity p : sortedPeriods) {
            if (PeriodNameKey.NOON.equals(p.nameKey) || PeriodNameKey.DINNER.equals(p.nameKey)) continue;
            schedulePeriodEndAlarm(p.nameKey, p.endMinute);
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

    /** 取消未处理任务检查闹钟。 */
    public void cancelUnfinishedCheckAlarm(int phase) {
        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_DAILY_UNFINISHED_CHECK);
        intent.putExtra(EXTRA_CHECK_PHASE, phase);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext,
            UNFINISHED_CHECK_REQUEST_CODE + phase, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) mAlarmManager.cancel(pi);
    }

    private void setUnfinishedCheckAlarm(int phase, long triggerMs) {
        cancelUnfinishedCheckAlarm(phase);

        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_DAILY_UNFINISHED_CHECK);
        intent.putExtra(EXTRA_CHECK_PHASE, phase);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext,
            UNFINISHED_CHECK_REQUEST_CODE + phase, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMs, pi);
    }

    private void setPeriodEndAlarm(String periodKey, int endMinute) {
        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_PERIOD_END);
        intent.putExtra(EXTRA_PERIOD_KEY, periodKey);
        int requestCode = PERIOD_END_REQUEST_CODE_BASE + Math.abs(periodKey.hashCode() & 0x7FFF);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMsFromMinute(endMinute), pi);
    }

    /** 注册专注任务超时检查闹钟（executingStartMs + focusMinutes + 30 分钟）。 */
    public void scheduleOvertimeCheck(TaskEntity task) {
        if (task == null || task.focusMinutes <= 0 || task.executingStartMs <= 0) return;
        cancelOvertimeCheck(mAppContext, task.id);

        long triggerMs = task.executingStartMs + (task.focusMinutes + 30) * 60000L;
        if (triggerMs <= System.currentTimeMillis()) return;

        Intent intent = new Intent(mAppContext, AlarmReceiver.class);
        intent.setAction(ACTION_OVERTIME_CHECK);
        intent.putExtra(EXTRA_OVERTIME_TASK_ID, task.id);

        int requestCode = OVERTIME_REQUEST_CODE_BASE + (int) (task.id & 0x7FFF);
        PendingIntent pi = PendingIntent.getBroadcast(mAppContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarmSafe(AlarmManager.RTC_WAKEUP, triggerMs, pi);
    }

    /** 取消超时检查闹钟（静态方法，无需 ReminderScheduler 实例）。 */
    public static void cancelOvertimeCheck(@NonNull Context context, long taskId) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_OVERTIME_CHECK);
        intent.putExtra(EXTRA_OVERTIME_TASK_ID, taskId);

        int requestCode = OVERTIME_REQUEST_CODE_BASE + (int) (taskId & 0x7FFF);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
        }
    }

    private void setAlarmSafe(int type, long triggerAtMillis, PendingIntent operation) {
        try {
            mAlarmManager.setExactAndAllowWhileIdle(type, triggerAtMillis, operation);
        } catch (SecurityException e) {
            // Android 12/13+ 降级使用非精确闹钟以防止 Crash
            mAlarmManager.setAndAllowWhileIdle(type, triggerAtMillis, operation);
        }
    }
}
