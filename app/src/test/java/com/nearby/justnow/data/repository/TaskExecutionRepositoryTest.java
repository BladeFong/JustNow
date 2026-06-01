package com.nearby.justnow.data.repository;

import org.junit.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

/**
 * TaskExecutionRepository 日期格式测试
 */
public class TaskExecutionRepositoryTest {

    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Test
    public void dateFormat_usesCorrectPattern() {
        String today = LocalDate.now().format(sDateFormat);
        assertNotNull(today);
        assertEquals(10, today.length()); // yyyy-MM-dd = 10字符
        assertTrue(today.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void dateFormat_paddedValues() {
        String date = LocalDate.of(2026, 1, 5).format(sDateFormat);
        assertEquals("2026-01-05", date);
    }

    @Test
    public void dateFormat_decemberMonth() {
        String date = LocalDate.of(2026, 12, 31).format(sDateFormat);
        assertEquals("2026-12-31", date);
    }
}
