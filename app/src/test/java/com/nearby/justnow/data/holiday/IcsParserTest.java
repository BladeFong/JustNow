package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IcsParserTest {

    @Test
    public void fill_emptyInput_keepsDataJsonNull() {
        HolidayCacheEntity e1 = HolidayCacheManager.emptyEntity(2026);
        IcsParser.fill(e1, "", 2026, "test");
        assertNull(e1.dataJson);

        HolidayCacheEntity e2 = HolidayCacheManager.emptyEntity(2026);
        IcsParser.fill(e2, null, 2026, "test");
        assertNull(e2.dataJson);
    }

    @Test
    public void fill_singleDayHoliday_fillsHolidays() {
        String ics = "BEGIN:VCALENDAR\n"
            + "BEGIN:VEVENT\n"
            + "DTSTART;VALUE=DATE:20260101\n"
            + "DTEND;VALUE=DATE:20260102\n"
            + "SUMMARY:元旦\n"
            + "END:VEVENT\n"
            + "END:VCALENDAR";

        HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(2026);
        IcsParser.fill(entity, ics, 2026, "test");
        String json = entity.dataJson;

        assertNotNull(json);
        assertTrue(json.contains("\"holidays\""));
        assertTrue(json.contains("2026-01-01"));
        assertTrue(json.contains("\"source\":\"test\""));
        assertEquals(1, entity.holidayCount);
    }

    @Test
    public void fill_springFestival_addsFestival() {
        String ics = "BEGIN:VCALENDAR\n"
            + "BEGIN:VEVENT\n"
            + "DTSTART;VALUE=DATE:20260217\n"
            + "DTEND;VALUE=DATE:20260224\n"
            + "SUMMARY:春节\n"
            + "END:VEVENT\n"
            + "END:VCALENDAR";

        HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(2026);
        IcsParser.fill(entity, ics, 2026, "holiday-cn");
        String json = entity.dataJson;

        assertNotNull(json);
        assertTrue(json.contains("\"festivals\""));
        assertTrue(json.contains("\"type\":\"spring_festival\""));
        assertTrue(json.contains("\"start\":\"2026-02-17\""));
        assertTrue(json.contains("\"end\":\"2026-02-23\""));
    }

    @Test
    public void fill_springFestivalEnglish_addsFestival() {
        String ics = "BEGIN:VCALENDAR\n"
            + "BEGIN:VEVENT\n"
            + "DTSTART;VALUE=DATE:20260217\n"
            + "DTEND;VALUE=DATE:20260224\n"
            + "SUMMARY:Chinese New Year\n"
            + "END:VEVENT\n"
            + "END:VCALENDAR";

        HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(2026);
        IcsParser.fill(entity, ics, 2026, "holiday-cn");
        String json = entity.dataJson;

        assertNotNull(json);
        assertTrue(json.contains("\"festivals\""));
        assertTrue(json.contains("\"type\":\"spring_festival\""));
    }

    @Test
    public void fill_differentYear_skipped() {
        String ics = "BEGIN:VCALENDAR\n"
            + "BEGIN:VEVENT\n"
            + "DTSTART;VALUE=DATE:20250101\n"
            + "DTEND;VALUE=DATE:20250102\n"
            + "SUMMARY:元旦\n"
            + "END:VEVENT\n"
            + "END:VCALENDAR";

        HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(2026);
        IcsParser.fill(entity, ics, 2026, "test");
        String json = entity.dataJson;

        assertNotNull(json);
        assertTrue(!json.contains("2025-01-01"));
    }

    @Test
    public void getFestivalRange_validJson_returnsRange() {
        String json = "{"
            + "\"year\":2026,"
            + "\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-02-17\",\"2026-02-18\"],"
            + "\"workdays\":[],"
            + "\"festivals\":[{\"type\":\"spring_festival\",\"start\":\"2026-02-17\",\"end\":\"2026-02-23\"}]"
            + "}";

        LocalDate[] range = IcsParser.getFestivalRange(json, "spring_festival");

        assertNotNull(range);
        assertEquals(LocalDate.of(2026, 2, 17), range[0]);
        assertEquals(LocalDate.of(2026, 2, 23), range[1]);
    }

    @Test
    public void getFestivalRange_nullJson_returnsNull() {
        assertNull(IcsParser.getFestivalRange(null, "spring_festival"));
    }

    @Test
    public void getFestivalRange_noFestivalField_returnsNull() {
        String json = "{\"year\":2026,\"holidays\":[],\"workdays\":[]}";
        assertNull(IcsParser.getFestivalRange(json, "spring_festival"));
    }

    @Test
    public void getFestivalRange_wrongType_returnsNull() {
        String json = "{"
            + "\"festivals\":[{\"type\":\"other\",\"start\":\"2026-01-01\",\"end\":\"2026-01-03\"}]"
            + "}";

        assertNull(IcsParser.getFestivalRange(json, "spring_festival"));
    }

    // ---- isOffDay 测试 ----

    @Test
    public void isOffDay_nullJson_returnsNull() {
        assertNull(IcsParser.isOffDay(null, LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void isOffDay_emptyJson_returnsNull() {
        assertNull(IcsParser.isOffDay("", LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void isOffDay_holiday_returnsTrue() {
        String json = "{"
            + "\"year\":2026,"
            + "\"holidays\":[\"2026-01-01\",\"2026-02-17\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]"
            + "}";

        assertEquals(Boolean.TRUE, IcsParser.isOffDay(json, LocalDate.of(2026, 1, 1)));
        assertEquals(Boolean.TRUE, IcsParser.isOffDay(json, LocalDate.of(2026, 2, 17)));
    }

    @Test
    public void isOffDay_makeupWorkday_returnsFalse() {
        String json = "{"
            + "\"year\":2026,"
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\",\"2026-04-25\"]"
            + "}";

        assertEquals(Boolean.FALSE, IcsParser.isOffDay(json, LocalDate.of(2026, 1, 31)));
        assertEquals(Boolean.FALSE, IcsParser.isOffDay(json, LocalDate.of(2026, 4, 25)));
    }

    @Test
    public void isOffDay_oldWorkdaysKey_returnsFalse() {
        String json = "{"
            + "\"year\":2026,"
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"workdays\":[\"2026-01-31\"]"
            + "}";

        // 兼容旧 key "workdays"，仍能识别为补班日
        assertEquals(Boolean.FALSE, IcsParser.isOffDay(json, LocalDate.of(2026, 1, 31)));
    }

    @Test
    public void isOffDay_notInCache_returnsNull() {
        String json = "{"
            + "\"year\":2026,"
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]"
            + "}";

        assertNull(IcsParser.isOffDay(json, LocalDate.of(2026, 6, 15)));
    }

    @Test
    public void isOffDay_dateInBothSections_holidaysTakesPriority() {
        // 日期同时出现在两个数组中的边界情况 — holidays 段先检查
        String json = "{"
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-01\"]"
            + "}";

        assertEquals(Boolean.TRUE, IcsParser.isOffDay(json, LocalDate.of(2026, 1, 1)));
    }
}
