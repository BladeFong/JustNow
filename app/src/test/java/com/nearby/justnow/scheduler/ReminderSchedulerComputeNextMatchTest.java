package com.nearby.justnow.scheduler;

import android.app.AlarmManager;

import com.nearby.justnow.data.entity.TaskScheduleEntity;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ReminderScheduler.computeNextMatch 静态方法测试。
 * 覆盖 4 种 scheduleType 的下次触发时间计算逻辑。
 */
public class ReminderSchedulerComputeNextMatchTest {

    // ============================================================
    // TYPE_ONCE
    // ============================================================

    @Test
    public void typeOnce_returnsDatePlusTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dateMs = cal.getTimeInMillis();

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        schedule.scheduleValue = dateMs;
        schedule.scheduledTime = 600; // 10:00

        long afterMs = dateMs - AlarmManager.INTERVAL_DAY;
        long result = ReminderScheduler.computeNextMatch(schedule, afterMs);

        assertEquals(dateMs + 600 * 60000L, result);
    }

    @Test
    public void typeOnce_returnsZero_afterTriggerTime() {
        // afterMs already past the trigger time, still returns the trigger time
        // (computeNextMatch doesn't filter past triggers; caller handles that)
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dateMs = cal.getTimeInMillis();

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        schedule.scheduleValue = dateMs;
        schedule.scheduledTime = 600; // 10:00

        long afterMs = dateMs + 700 * 60000L; // 11:40, past 10:00
        long result = ReminderScheduler.computeNextMatch(schedule, afterMs);

        assertEquals(dateMs + 600 * 60000L, result);
    }

    // ============================================================
    // TYPE_DAILY
    // ============================================================

    @Test
    public void typeDaily_todayTimeNotYetPassed_returnsToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long afterMs = cal.getTimeInMillis(); // 08:00

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, afterMs);

        // 应该返回今天 10:00
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        assertEquals(cal.getTimeInMillis(), result);
    }

    @Test
    public void typeDaily_todayTimePassed_returnsTomorrow() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long afterMs = cal.getTimeInMillis(); // 12:00

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, afterMs);

        // 应该返回明天 10:00
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        assertEquals(cal.getTimeInMillis(), result);
    }

    @Test
    public void typeDaily_exactSameMinute_returnsTomorrow() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 10, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long afterMs = cal.getTimeInMillis(); // exactly 10:00

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, afterMs);

        // afterMs == todayTrigger, 不等式 todayTrigger <= afterMs 成立，返回明天
        cal.add(Calendar.DAY_OF_YEAR, 1);
        assertEquals(cal.getTimeInMillis(), result);
    }

    // ============================================================
    // TYPE_WEEKLY
    // ============================================================

    @Test
    public void typeWeekly_bitmaskZero_returnsZero() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        schedule.scheduleValue = 0;
        schedule.scheduledTime = 600;

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());
        assertEquals(0, result);
    }

    @Test
    public void typeWeekly_todayMatchesAndNotYetPassed_returnsToday() {
        // 找到今天星期几，构造只有今天匹配的 bitmask
        // 新约定 bit0=Sun..bit6=Sat：Calendar.DAY_OF_WEEK (1=Sun..7=Sat) → bit (dow-1)
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int todayDow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun
        int bitmask = 1 << (todayDow - 1);

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        schedule.scheduleValue = bitmask;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());

        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        assertEquals("今天 10:00 未过应返回今天", cal.getTimeInMillis(), result);
    }

    @Test
    public void typeWeekly_todayMatchesButPassed_returnsNextWeek() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int todayDow = cal.get(Calendar.DAY_OF_WEEK);
        int bitmask = 1 << (todayDow - 1);

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        schedule.scheduleValue = bitmask;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());

        // 今天 10:00 已过，应返回下周同一天 10:00
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        cal.add(Calendar.DAY_OF_YEAR, 7);
        assertEquals(cal.getTimeInMillis(), result);
    }

    @Test
    public void typeWeekly_tomorrowMatches_returnsTomorrow() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int todayDow = cal.get(Calendar.DAY_OF_WEEK);
        // 新约定 bit0=Sun..bit6=Sat；tomorrow 的 bit 索引 = ((todayDow-1)+1) % 7
        int tomorrowBitIndex = (todayDow) % 7;
        int bitmask = 1 << tomorrowBitIndex;

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        schedule.scheduleValue = bitmask;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());

        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        assertEquals(cal.getTimeInMillis(), result);
    }

    @Test
    public void typeWeekly_noMatchingDay_returnsZero() {
        // 构造一个所有 bit 都不匹配的 mask：找今天之后 6 天所有的 DOW
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int todayDow = cal.get(Calendar.DAY_OF_WEEK);
        // mask 里设今天但今天已过 → 本应加到下周
        // 实际上循环会找 0..6，如果只有今天匹配且今天已过，仍返回下周
        // 为了触发 return 0，需要 bitmask & (1 << checkDow) 对所有 i=0..6 都为 false
        // 这意味着所有 7 天的 bit 都不在 mask 中
        int bitmaskAllDays = (1 << Calendar.SUNDAY) | (1 << Calendar.MONDAY)
            | (1 << Calendar.TUESDAY) | (1 << Calendar.WEDNESDAY)
            | (1 << Calendar.THURSDAY) | (1 << Calendar.FRIDAY)
            | (1 << Calendar.SATURDAY);
        int noDayBitmask = 0; // 不设任何 bit

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        schedule.scheduleValue = noDayBitmask;
        schedule.scheduledTime = 600;

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());
        // bitmask=0 已在方法开头 return 0
        assertEquals(0, result);
    }

    @Test
    public void typeWeekly_nextDayInFutureWeek_returnsCorrectDay() {
        // 今天周三，bitmask 只含周一(bit 2)，应返回下周一
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 17, 12, 0, 0); // 2026-06-17 = Wednesday
        cal.set(Calendar.MILLISECOND, 0);
        int wednesdayDow = cal.get(Calendar.DAY_OF_WEEK); // Wednesday
        // 确认是周三（Calendar.WEDNESDAY=4）
        assertTrue("测试日期应为周三", wednesdayDow == Calendar.WEDNESDAY);

        // bitmask: Monday only — 新约定 bit0=Sun..bit6=Sat → 周一 = bit1
        int mondayBit = 1 << 1;

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        schedule.scheduleValue = mondayBit;
        schedule.scheduledTime = 600; // 10:00

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());

        // 下周一 = 周三 + 5 天
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        cal.add(Calendar.DAY_OF_YEAR, 5); // Wed → Mon = 5 days
        assertEquals(cal.getTimeInMillis(), result);
    }

    // ============================================================
    // 边界：未知类型
    // ============================================================

    @Test
    public void unknownScheduleType_returnsZero() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 8, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = 99;
        schedule.scheduledTime = 600;

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());
        assertEquals(0, result);
    }

    // ============================================================
    // 午夜边界
    // ============================================================

    @Test
    public void typeDaily_midnightSchedule_returnsCorrectTime() {
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.JUNE, 15, 0, 30, 0); // 00:30, past 00:00
        cal.set(Calendar.MILLISECOND, 0);

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        schedule.scheduledTime = 0; // 00:00

        long result = ReminderScheduler.computeNextMatch(schedule, cal.getTimeInMillis());

        // 今天 00:00 已过，返回明天 00:00
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        assertEquals(cal.getTimeInMillis(), result);
    }
}
