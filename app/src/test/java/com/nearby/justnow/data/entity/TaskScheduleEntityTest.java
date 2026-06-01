package com.nearby.justnow.data.entity;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskScheduleEntity 字段与 isRecurring() 逻辑测试。
 * 覆盖 scheduleType 新枚举值与 isRecurring 判定。
 */
public class TaskScheduleEntityTest {

    @Test
    public void isRecurring_typeOnce_false() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        assertFalse(entity.isRecurring());
    }

    @Test
    public void isRecurring_typeDaily_true() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        assertTrue(entity.isRecurring());
    }

    @Test
    public void isRecurring_typeWeekly_true() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        assertTrue(entity.isRecurring());
    }

    @Test
    public void isRecurring_typeMonthly_true() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.scheduleType = TaskScheduleEntity.TYPE_MONTHLY;
        assertTrue(entity.isRecurring());
    }

    @Test
    public void scheduleTypeConstants_matchSpec() {
        assertEquals(0, TaskScheduleEntity.TYPE_ONCE);
        assertEquals(1, TaskScheduleEntity.TYPE_DAILY);
        assertEquals(2, TaskScheduleEntity.TYPE_WEEKLY);
        assertEquals(3, TaskScheduleEntity.TYPE_MONTHLY);
    }

    @Test
    public void disableReasonConstants_areCorrect() {
        assertEquals("EXPIRED", TaskScheduleEntity.REASON_EXPIRED);
        assertEquals("USER_STOPPED", TaskScheduleEntity.REASON_USER_STOPPED);
        assertEquals("TASK_ARCHIVED", TaskScheduleEntity.REASON_TASK_ARCHIVED);
    }

    @Test
    public void newField_defaults() {
        TaskScheduleEntity entity = new TaskScheduleEntity();

        assertEquals(0L, entity.scheduleValue);
        assertEquals(0, entity.scheduledTime);
        assertFalse(entity.enabled);
        assertNull(entity.disableReason);
    }

    @Test
    public void scheduleValue_forWeeklyBitmask() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
        // Mon + Wed + Fri = bit1=2, bit3=8, bit5=32
        int bitmask = (1 << Calendar.MONDAY)
            | (1 << Calendar.WEDNESDAY)
            | (1 << Calendar.FRIDAY);
        entity.scheduleValue = bitmask;

        assertTrue(entity.isRecurring());
        assertEquals((long) bitmask, entity.scheduleValue);
    }
}
