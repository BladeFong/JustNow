package com.nearby.justnow.data.entity;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskScheduleEntity 字段与 isRecurring() 逻辑测试。
 * 覆盖 scheduleType 枚举值、linkedPeriodGroupType/scheduleSubType 新字段、
 * 以及 isRecurring 判定（含 linkedPeriodGroupType + scheduleSubType 组合）。
 *
 * <p>TYPE_MONTHLY 已删除；新增 linkedPeriodGroupType/scheduleSubType 时段组关联字段。</p>
 */
public class TaskScheduleEntityTest {

    // ==================== isRecurring：scheduleType 旧路径 ====================

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

    // ==================== isRecurring：linkedPeriodGroupType + scheduleSubType 新路径 ====================

    @Test
    public void isRecurring_linkedGroupSubType0_everyday_true() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.linkedPeriodGroupType = "WORKDAY";
        entity.scheduleSubType = 0; // 每天
        assertTrue(entity.isRecurring());
    }

    @Test
    public void isRecurring_linkedGroupSubType1_weekly_true() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.linkedPeriodGroupType = "WORKDAY";
        entity.scheduleSubType = 1; // 每周
        assertTrue(entity.isRecurring());
    }

    @Test
    public void isRecurring_linkedGroupSubType2_once_false() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.linkedPeriodGroupType = "LONG_VACATION";
        entity.scheduleSubType = 2; // 单次
        assertFalse(entity.isRecurring());
    }

    @Test
    public void isRecurring_emptyLinkedGroup_scheduleTypeOnce_false() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.linkedPeriodGroupType = "";
        entity.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        assertFalse(entity.isRecurring());
    }

    @Test
    public void isRecurring_emptyLinkedGroup_scheduleSubType0_cannotOverride() {
        // linkedPeriodGroupType 为空时 scheduleSubType 无效，仅看 scheduleType
        TaskScheduleEntity entity = new TaskScheduleEntity();
        entity.linkedPeriodGroupType = "";
        entity.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        entity.scheduleSubType = 0; // 每天，但 linkedPeriodGroupType 为空，不起作用
        assertFalse(entity.isRecurring());
    }

    // ==================== 常量验证 ====================

    @Test
    public void scheduleTypeConstants_matchSpec() {
        assertEquals(0, TaskScheduleEntity.TYPE_ONCE);
        assertEquals(1, TaskScheduleEntity.TYPE_DAILY);
        assertEquals(2, TaskScheduleEntity.TYPE_WEEKLY);
    }

    @Test
    public void disableReasonConstants_areCorrect() {
        assertEquals("USER_STOPPED", TaskScheduleEntity.REASON_USER_STOPPED);
        assertEquals("TASK_ARCHIVED", TaskScheduleEntity.REASON_TASK_ARCHIVED);
    }

    // ==================== 字段默认值 ====================

    @Test
    public void newField_defaults() {
        TaskScheduleEntity entity = new TaskScheduleEntity();

        assertEquals(0L, entity.scheduleValue);
        assertEquals(0, entity.scheduledTime);
        assertFalse(entity.enabled);
        assertNull(entity.disableReason);
    }

    @Test
    public void newFields_linkedPeriodGroupType_defaults() {
        TaskScheduleEntity entity = new TaskScheduleEntity();
        assertEquals("", entity.linkedPeriodGroupType);
        assertEquals(0, entity.scheduleSubType);
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
