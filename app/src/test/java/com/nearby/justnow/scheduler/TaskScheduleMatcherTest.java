package com.nearby.justnow.scheduler;

import com.nearby.justnow.data.entity.TaskScheduleEntity;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TaskScheduleMatcher 单测：matchesDate / matchesToday / computeNextMatch。
 * 周重复 bitmask 新约定 bit0=周日, bit1=周一, ..., bit6=周六。
 */
public class TaskScheduleMatcherTest {

    // ---------- 辅助 ----------

    private static long dayStart(int year, int month, int dayOfMonth) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, dayOfMonth, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long timestamp(int year, int month, int dayOfMonth, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, dayOfMonth, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static TaskScheduleEntity newSchedule(int type, long value, int scheduledTime) {
        TaskScheduleEntity s = new TaskScheduleEntity();
        s.scheduleType = type;
        s.scheduleValue = value;
        s.scheduledTime = scheduledTime;
        return s;
    }

    // ============================================================
    // matchesDate / matchesToday：TYPE_ONCE
    // ============================================================

    @Test
    public void matchesDate_typeOnceSameDay_returnsTrue() {
        long target = dayStart(2026, Calendar.JUNE, 15);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_ONCE, target, 600);

        assertTrue(TaskScheduleMatcher.matchesDate(s, target));
        // 不同小时的同一天也应该匹配（matchesDate 内部 normalize 到 dayStart）
        assertTrue(TaskScheduleMatcher.matchesDate(s, target + 12 * 3600_000L));
    }

    @Test
    public void matchesDate_typeOnceYesterday_returnsFalse() {
        long target = dayStart(2026, Calendar.JUNE, 15);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_ONCE, target, 600);
        assertFalse(TaskScheduleMatcher.matchesDate(s, target - 24 * 3600_000L));
    }

    @Test
    public void matchesDate_typeOnceTomorrow_returnsFalse() {
        long target = dayStart(2026, Calendar.JUNE, 15);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_ONCE, target, 600);
        assertFalse(TaskScheduleMatcher.matchesDate(s, target + 24 * 3600_000L));
    }

    @Test
    public void matchesToday_typeOnceWithTodayDate_returnsTrue() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_ONCE, todayStart, 600);

        assertTrue(TaskScheduleMatcher.matchesToday(s));
    }

    @Test
    public void matchesToday_typeOnceWithYesterdayDate_returnsFalse() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        TaskScheduleEntity s = newSchedule(
            TaskScheduleEntity.TYPE_ONCE, todayStart - 24 * 3600_000L, 600);

        assertFalse(TaskScheduleMatcher.matchesToday(s));
    }

    // ============================================================
    // matchesDate / matchesToday：TYPE_DAILY
    // ============================================================

    @Test
    public void matchesDate_typeDaily_anyDateReturnsTrue() {
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_DAILY, 0, 600);
        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JANUARY, 1)));
        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 15)));
        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2030, Calendar.DECEMBER, 31)));
    }

    @Test
    public void matchesToday_typeDaily_returnsTrue() {
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_DAILY, 0, 600);
        assertTrue(TaskScheduleMatcher.matchesToday(s));
    }

    // ============================================================
    // matchesDate：TYPE_WEEKLY
    // ============================================================

    @Test
    public void matchesDate_typeWeeklyBit0Sunday_matchesOnlySunday() {
        // 2026-06-14 = Sunday，2026-06-15 = Monday
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 0, 600);

        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 14))); // Sun
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 15))); // Mon
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 16))); // Tue
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 20))); // Sat
    }

    @Test
    public void matchesDate_typeWeeklyBit6Saturday_matchesOnlySaturday() {
        // 2026-06-20 = Saturday
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 6, 600);

        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 20))); // Sat
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 14))); // Sun
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 19))); // Fri
    }

    @Test
    public void matchesDate_typeWeeklyBit1Monday_matchesOnlyMonday() {
        // 2026-06-15 = Monday
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 1, 600);

        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 15))); // Mon
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 14))); // Sun
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 16))); // Tue
    }

    @Test
    public void matchesDate_typeWeeklySunTueThu_matchesThoseDays() {
        // bit0=Sun, bit2=Tue, bit4=Thu
        int bitmask = (1 << 0) | (1 << 2) | (1 << 4);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, bitmask, 600);

        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 14)));  // Sun
        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 16)));  // Tue
        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 18)));  // Thu

        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 15))); // Mon
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 17))); // Wed
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 19))); // Fri
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 20))); // Sat
    }

    @Test
    public void matchesDate_typeWeeklyAllZero_returnsFalse() {
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 0, 600);
        // 任意日期都应返回 false
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 14)));
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 15)));
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 20)));
    }

    // ============================================================
    // matchesDate：未知类型（default 分支返回 false，原 TYPE_MONTHLY 已删除）
    // ============================================================

    @Test
    public void matchesDate_unknownScheduleType_returnsFalse() {
        TaskScheduleEntity s = newSchedule(99, 15, 600);
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 15)));
        assertFalse(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JANUARY, 1)));
    }

    // ============================================================
    // computeNextMatch：TYPE_ONCE
    // ============================================================

    @Test
    public void computeNextMatch_typeOnce_returnsScheduleValuePlusTime() {
        long target = dayStart(2026, Calendar.JUNE, 15);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_ONCE, target, 720); // 12:00
        long afterMs = target - 24 * 3600_000L; // 前一天

        long result = TaskScheduleMatcher.computeNextMatch(s, afterMs);
        assertEquals(target + 720 * 60_000L, result);
    }

    // ============================================================
    // computeNextMatch：TYPE_DAILY
    // ============================================================

    @Test
    public void computeNextMatch_typeDaily_afterMsBeforeTime_returnsToday() {
        long afterMs = timestamp(2026, Calendar.JUNE, 15, 8, 0); // 08:00
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_DAILY, 0, 540); // 09:00

        long expected = timestamp(2026, Calendar.JUNE, 15, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    @Test
    public void computeNextMatch_typeDaily_afterMsPastTime_returnsTomorrow() {
        long afterMs = timestamp(2026, Calendar.JUNE, 15, 10, 0); // 10:00
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_DAILY, 0, 540); // 09:00

        long expected = timestamp(2026, Calendar.JUNE, 16, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    // ============================================================
    // computeNextMatch：TYPE_WEEKLY
    // ============================================================

    @Test
    public void computeNextMatch_typeWeeklyBit0Sunday_afterMondayMorning_returnsNextSunday() {
        // 2026-06-15 = Monday；bit0=Sunday
        long afterMs = timestamp(2026, Calendar.JUNE, 15, 8, 0);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 0, 540);

        // 下个周日 = 2026-06-21 09:00
        long expected = timestamp(2026, Calendar.JUNE, 21, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    @Test
    public void computeNextMatch_typeWeeklyBit0Sunday_onSundayBeforeTime_returnsToday() {
        // 2026-06-14 = Sunday；afterMs=Sunday 08:00；scheduledTime=09:00
        long afterMs = timestamp(2026, Calendar.JUNE, 14, 8, 0);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 0, 540);

        long expected = timestamp(2026, Calendar.JUNE, 14, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    @Test
    public void computeNextMatch_typeWeeklyBit0Sunday_onSundayAfterTime_returnsNextSunday() {
        // 2026-06-14 = Sunday；afterMs=Sunday 10:00；scheduledTime=09:00
        long afterMs = timestamp(2026, Calendar.JUNE, 14, 10, 0);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 0, 540);

        long expected = timestamp(2026, Calendar.JUNE, 21, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    @Test
    public void computeNextMatch_typeWeeklySatPlusMon_onWednesday_returnsSaturday() {
        // bit6=Sat, bit1=Mon；afterMs=Wednesday 2026-06-17
        int bitmask = (1 << 6) | (1 << 1);
        long afterMs = timestamp(2026, Calendar.JUNE, 17, 12, 0);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, bitmask, 540);

        // Wed 之后下一个匹配 = Saturday 2026-06-20 09:00
        long expected = timestamp(2026, Calendar.JUNE, 20, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    @Test
    public void computeNextMatch_typeWeeklySatPlusMon_onSunday_returnsMonday() {
        // bit6=Sat, bit1=Mon；afterMs=Sunday 2026-06-14
        int bitmask = (1 << 6) | (1 << 1);
        long afterMs = timestamp(2026, Calendar.JUNE, 14, 12, 0);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, bitmask, 540);

        // Sunday 之后下一个匹配 = Monday 2026-06-15 09:00
        long expected = timestamp(2026, Calendar.JUNE, 15, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    @Test
    public void computeNextMatch_typeWeeklyBitmaskZero_returnsZero() {
        long afterMs = timestamp(2026, Calendar.JUNE, 15, 8, 0);
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 0, 540);
        assertEquals(0, TaskScheduleMatcher.computeNextMatch(s, afterMs));
    }

    // ============================================================
    // matchesDate 与 computeNextMatch 一致性（合理 bitmask 下）
    // ============================================================

    @Test
    public void matchesDate_andComputeNextMatch_consistent_weeklySingleDay() {
        // 选 bit3 = Wednesday；2026-06-17 = Wed
        TaskScheduleEntity s = newSchedule(TaskScheduleEntity.TYPE_WEEKLY, 1 << 3, 540);

        assertTrue(TaskScheduleMatcher.matchesDate(s, dayStart(2026, Calendar.JUNE, 17)));

        // computeNextMatch 在 Tuesday 应返回 Wednesday
        long tueAfter = timestamp(2026, Calendar.JUNE, 16, 12, 0);
        long expected = timestamp(2026, Calendar.JUNE, 17, 9, 0);
        assertEquals(expected, TaskScheduleMatcher.computeNextMatch(s, tueAfter));
    }
}
