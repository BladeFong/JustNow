package com.nearby.justnow.scheduler;

import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.util.DateUtils;

import java.util.Calendar;

/**
 * 任务安排匹配与下次触发计算。
 * 周重复 bitmask 约定：bit0=周日, bit1=周一, ..., bit6=周六。
 */
public final class TaskScheduleMatcher {

    private TaskScheduleMatcher() {}

    /** 判断安排在指定日期是否会触发。 */
    public static boolean matchesDate(TaskScheduleEntity schedule, long dateMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStartMs = cal.getTimeInMillis();

        switch (schedule.scheduleType) {
            case TaskScheduleEntity.TYPE_ONCE:
                return schedule.scheduleValue == dayStartMs;
            case TaskScheduleEntity.TYPE_DAILY:
                return true;
            case TaskScheduleEntity.TYPE_WEEKLY: {
                int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
                int bit = 1 << (dow - 1);                // bit0=Sun..bit6=Sat
                return ((int) schedule.scheduleValue & bit) != 0;
            }
            case TaskScheduleEntity.TYPE_MONTHLY: {
                int target = (int) schedule.scheduleValue;
                if (target < 1 || target > 31) return false;
                int dom = cal.get(Calendar.DAY_OF_MONTH);
                int maxDom = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                int effective = Math.min(target, maxDom);
                return dom == effective;
            }
            default:
                return false;
        }
    }

    /**
     * 计算下一次触发时间戳（毫秒）；0 表示无匹配。
     * weekly 用 bit0=周日 映射。
     */
    public static long computeNextMatch(TaskScheduleEntity schedule, long afterMs) {
        int time = schedule.scheduledTime;
        int hour = time / 60;
        int minute = time % 60;

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(afterMs);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        switch (schedule.scheduleType) {
            case TaskScheduleEntity.TYPE_ONCE: {
                return schedule.scheduleValue + time * 60000L;
            }
            case TaskScheduleEntity.TYPE_DAILY: {
                long t = cal.getTimeInMillis();
                if (t <= afterMs) t += 86400000L;
                return t;
            }
            case TaskScheduleEntity.TYPE_WEEKLY: {
                int bitmask = (int) schedule.scheduleValue;
                if (bitmask == 0) return 0;
                int todayDow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun..7=Sat
                int todayBitIndex = todayDow - 1;             // 0=Sun..6=Sat
                for (int i = 0; i < 7; i++) {
                    int checkBitIndex = (todayBitIndex + i) % 7;
                    if ((bitmask & (1 << checkBitIndex)) != 0) {
                        if (i == 0) {
                            long t = cal.getTimeInMillis();
                            if (t > afterMs) return t;
                            cal.add(Calendar.DAY_OF_YEAR, 7);
                            return cal.getTimeInMillis();
                        }
                        cal.add(Calendar.DAY_OF_YEAR, i);
                        return cal.getTimeInMillis();
                    }
                }
                return 0;
            }
            case TaskScheduleEntity.TYPE_MONTHLY: {
                int dayOfMonth = (int) schedule.scheduleValue;
                if (dayOfMonth < 1 || dayOfMonth > 31) return 0;
                int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                cal.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, maxDay));
                long t = cal.getTimeInMillis();
                if (t <= afterMs) {
                    cal.add(Calendar.MONTH, 1);
                    maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    cal.set(Calendar.DAY_OF_MONTH, Math.min(dayOfMonth, maxDay));
                    t = cal.getTimeInMillis();
                }
                return t;
            }
            default:
                return 0;
        }
    }

    /** 判断安排今天是否会触发（基于系统时区当日 00:00）。 */
    public static boolean matchesToday(TaskScheduleEntity schedule) {
        return matchesDate(schedule, DateUtils.todayStartMs());
    }

}
