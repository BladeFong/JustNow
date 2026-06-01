package com.nearby.justnow.data.entity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * TaskSchedulePostponeEntity 基础测试 — 字段赋值、默认值。
 */
public class TaskSchedulePostponeEntityTest {

    @Test
    public void defaultValues_allFieldsZero() {
        TaskSchedulePostponeEntity entity = new TaskSchedulePostponeEntity();

        assertEquals("id 默认值应为 0", 0, entity.id);
        assertEquals("scheduleId 默认值应为 0", 0, entity.scheduleId);
        assertEquals("taskId 默认值应为 0", 0, entity.taskId);
        assertEquals("blockedByTaskId 默认值应为 0", 0, entity.blockedByTaskId);
        assertEquals("postponeMinutes 默认值应为 0", 0, entity.postponeMinutes);
        assertEquals("dateMs 默认值应为 0", 0, entity.dateMs);
        assertEquals("createdAt 默认值应为 0", 0, entity.createdAt);
    }

    @Test
    public void setFields_allValuesStoredCorrectly() {
        TaskSchedulePostponeEntity entity = new TaskSchedulePostponeEntity();
        long now = System.currentTimeMillis();

        entity.scheduleId = 10;
        entity.taskId = 20;
        entity.blockedByTaskId = 30;
        entity.postponeMinutes = 15;
        entity.dateMs = 1700000000000L;
        entity.createdAt = now;

        assertEquals("scheduleId 应被存储", 10, entity.scheduleId);
        assertEquals("taskId 应被存储", 20, entity.taskId);
        assertEquals("blockedByTaskId 应被存储", 30, entity.blockedByTaskId);
        assertEquals("postponeMinutes 应被存储", 15, entity.postponeMinutes);
        assertEquals("dateMs 应被存储", 1700000000000L, entity.dateMs);
        assertEquals("createdAt 应被存储", now, entity.createdAt);
        // 未 insert 前 id 保持默认值
        assertEquals("id 在 insert 前应为 0", 0, entity.id);
    }

    @Test
    public void setFields_negativeValues_preserved() {
        TaskSchedulePostponeEntity entity = new TaskSchedulePostponeEntity();

        entity.scheduleId = -1;
        entity.taskId = -2;
        entity.blockedByTaskId = -3;
        entity.postponeMinutes = -30;

        assertEquals(-1, entity.scheduleId);
        assertEquals(-2, entity.taskId);
        assertEquals(-3, entity.blockedByTaskId);
        assertEquals(-30, entity.postponeMinutes);
    }

    @Test
    public void setFields_largeValues_preserved() {
        TaskSchedulePostponeEntity entity = new TaskSchedulePostponeEntity();

        entity.scheduleId = Long.MAX_VALUE;
        entity.taskId = Long.MAX_VALUE - 1;
        entity.dateMs = Long.MAX_VALUE - 2;
        entity.createdAt = Long.MIN_VALUE;
        entity.postponeMinutes = Integer.MAX_VALUE;

        assertEquals(Long.MAX_VALUE, entity.scheduleId);
        assertEquals(Long.MAX_VALUE - 1, entity.taskId);
        assertEquals(Long.MAX_VALUE - 2, entity.dateMs);
        assertEquals(Long.MIN_VALUE, entity.createdAt);
        assertEquals(Integer.MAX_VALUE, entity.postponeMinutes);
    }
}
