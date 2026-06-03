package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * HolidayJsonParser 单元测试 — 验证 holiday-cn JSON 解析及与 IcsParser 输出格式兼容性。
 * F5 将 fill()/buildJson() 从手动字符串解析重写为 org.json.JSONObject/JSONArray。
 * 需要 Robolectric 提供 org.json 和 android.util.Log 的真实实现。
 */
@RunWith(RobolectricTestRunner.class)
public class HolidayJsonParserTest {

    // ========================================================================
    // null / 空输入 — 不抛异常，dataJson 保持 null
    // ========================================================================

    @Test
    public void fill_nullEntity_keepsDataJsonNull() {
        HolidayJsonParser.fill(null, "{}", 2026, "test");
        // 不应抛异常
    }

    @Test
    public void fill_nullJson_keepsDataJsonNull() {
        HolidayCacheEntity entity = emptyEntity(2026);
        HolidayJsonParser.fill(entity, null, 2026, "test");
        assertNull(entity.dataJson);
    }

    @Test
    public void fill_emptyJson_keepsDataJsonNull() {
        HolidayCacheEntity entity = emptyEntity(2026);
        HolidayJsonParser.fill(entity, "", 2026, "test");
        assertNull(entity.dataJson);
    }

    @Test
    public void fill_emptyDaysArray_noError() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":[]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        // buildJson 在无春节区间时不应包含 festivals
        assertTrue(!entity.dataJson.contains("\"festivals\""));
        assertEquals(0, entity.holidayCount);
    }

    // ========================================================================
    // 正常 JSON 解析 — 假日 / 补班 / 春节区间
    // ========================================================================

    @Test
    public void fill_singleHoliday_parsesCorrectly() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-01\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(entity.dataJson.contains("\"holidays\""));
        assertTrue(entity.dataJson.contains("\"2026-01-01\""));
        assertTrue(entity.dataJson.contains("\"makeupWorkdays\""));
        assertTrue(entity.dataJson.contains("\"source\":\"holiday-cn\""));
        assertTrue(entity.dataJson.contains("\"year\":2026"));
        assertEquals(1, entity.holidayCount);
    }

    @Test
    public void fill_singleMakeupWorkday_parsesCorrectly() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-04\",\"isOffDay\":false}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(entity.dataJson.contains("\"makeupWorkdays\""));
        assertTrue(entity.dataJson.contains("\"2026-01-04\""));
        assertEquals(0, entity.holidayCount);
        // holidays 数组存在但为空
        assertTrue(entity.dataJson.contains("\"holidays\":[]"));
    }

    @Test
    public void fill_mixedHolidayAndWorkday_parsesCorrectly() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-01\",\"isOffDay\":true},"
            + "{\"name\":\"元旦\",\"date\":\"2026-01-02\",\"isOffDay\":true},"
            + "{\"name\":\"元旦\",\"date\":\"2026-01-04\",\"isOffDay\":false}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(entity.dataJson.contains("\"2026-01-01\""));
        assertTrue(entity.dataJson.contains("\"2026-01-02\""));
        assertTrue(entity.dataJson.contains("\"2026-01-04\""));
        assertEquals(2, entity.holidayCount);
    }

    // ========================================================================
    // 春节区间计算
    // ========================================================================

    @Test
    public void fill_springFestival_computesRange() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"春节\",\"date\":\"2026-02-14\",\"isOffDay\":false},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-15\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-16\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-23\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(entity.dataJson.contains("\"festivals\""));
        assertTrue(entity.dataJson.contains("\"type\":\"spring_festival\""));
        assertTrue(entity.dataJson.contains("\"start\":\"2026-02-15\""));
        assertTrue(entity.dataJson.contains("\"end\":\"2026-02-23\""));
        // 补班日 02-14 不算入假日/区间
        assertEquals(3, entity.holidayCount);
    }

    @Test
    public void fill_springFestival_makeupOnly_noFestivalRange() {
        // 春节只有补班日、无假日 → 不应生成 festivals
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"春节\",\"date\":\"2026-02-14\",\"isOffDay\":false},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-28\",\"isOffDay\":false}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(!entity.dataJson.contains("\"festivals\""));
        assertEquals(0, entity.holidayCount);
    }

    @Test
    public void fill_nonSpringHoliday_noFestivalRange() {
        // 非春节假日 → 不计入 festivals
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-01\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-02\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(!entity.dataJson.contains("\"festivals\""));
        assertEquals(2, entity.holidayCount);
    }

    // ========================================================================
    // 缺失字段处理 — 跳过而不抛异常
    // ========================================================================

    @Test
    public void fill_missingNameField_entrySkipped() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"date\":\"2026-01-01\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertEquals(0, entity.holidayCount);
    }

    @Test
    public void fill_nullNameField_entrySkipped() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":null,\"date\":\"2026-01-01\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertEquals(0, entity.holidayCount);
    }

    @Test
    public void fill_missingDateField_entrySkipped() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertEquals(0, entity.holidayCount);
    }

    @Test
    public void fill_missingIsOffDay_defaultsToWorkday() {
        // isOffDay 缺失 → optBoolean 默认 false → 归入 makeupWorkdays
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-01\"}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        assertTrue(entity.dataJson.contains("\"2026-01-01\""));
        assertTrue(entity.dataJson.contains("\"makeupWorkdays\""));
        assertEquals(0, entity.holidayCount);
    }

    @Test
    public void fill_mixedValidAndInvalidEntries_partialParsing() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-01\",\"isOffDay\":true},"
            + "{\"name\":null,\"date\":\"2026-01-02\",\"isOffDay\":true},"
            + "{\"date\":\"2026-01-03\",\"isOffDay\":true},"
            + "{\"name\":\"补班\",\"isOffDay\":false},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-01\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        // 只有 01-01 和 05-01 两个有效假日条目
        assertEquals(2, entity.holidayCount);
        assertTrue(entity.dataJson.contains("\"2026-01-01\""));
        assertTrue(entity.dataJson.contains("\"2026-05-01\""));
    }

    // ========================================================================
    // 真实 2026.json 数据集成测试
    // ========================================================================

    @Test
    public void fill_real2026Json_parsesAllHolidays() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = real2026Json();

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        assertNotNull(entity.dataJson);
        // 2026 年法定假日共计 33 天（元旦3+春节9+清明3+劳动5+端午3+中秋3+国庆7）
        assertEquals(33, entity.holidayCount);

        // 关键假日日期存在
        assertTrue(entity.dataJson.contains("\"2026-01-01\""));
        assertTrue(entity.dataJson.contains("\"2026-05-01\""));
        assertTrue(entity.dataJson.contains("\"2026-10-01\""));

        // 补班日存在
        assertTrue(entity.dataJson.contains("\"2026-01-04\""));
        assertTrue(entity.dataJson.contains("\"2026-02-14\""));
        assertTrue(entity.dataJson.contains("\"2026-09-20\""));

        // 春节区间正确
        assertTrue(entity.dataJson.contains("\"type\":\"spring_festival\""));
        assertTrue(entity.dataJson.contains("\"start\":\"2026-02-15\""));
        assertTrue(entity.dataJson.contains("\"end\":\"2026-02-23\""));
    }

    @Test
    public void fill_real2026Json_outputHasExpectedTopLevelKeys() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = real2026Json();

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");

        // 输出 JSON 应包含所有预期顶层键
        assertTrue(entity.dataJson.contains("\"year\":2026"));
        assertTrue(entity.dataJson.contains("\"source\":\"holiday-cn\""));
        assertTrue(entity.dataJson.contains("\"holidays\""));
        assertTrue(entity.dataJson.contains("\"makeupWorkdays\""));
        assertTrue(entity.dataJson.contains("\"festivals\""));
    }

    // ========================================================================
    // 与 IcsParser 输出格式兼容性验证
    // ========================================================================

    @Test
    public void fill_outputCompatible_isOffDay_holidayReturnsTrue() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = real2026Json();

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");
        String cachedJson = entity.dataJson;

        // 法定假日 → TRUE
        assertEquals(Boolean.TRUE,
            IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 1, 1)));
        assertEquals(Boolean.TRUE,
            IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 5, 1)));
        assertEquals(Boolean.TRUE,
            IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 10, 1)));
    }

    @Test
    public void fill_outputCompatible_isOffDay_makeupReturnsFalse() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = real2026Json();

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");
        String cachedJson = entity.dataJson;

        // 补班日 → FALSE
        assertEquals(Boolean.FALSE,
            IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 1, 4)));
        assertEquals(Boolean.FALSE,
            IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 2, 14)));
        assertEquals(Boolean.FALSE,
            IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 2, 28)));
    }

    @Test
    public void fill_outputCompatible_isOffDay_notInCacheReturnsNull() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = real2026Json();

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");
        String cachedJson = entity.dataJson;

        // 不在缓存中的日期 → null
        assertNull(IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 3, 15)));
        assertNull(IcsParser.isOffDay(cachedJson, LocalDate.of(2026, 8, 6)));
    }

    @Test
    public void fill_outputCompatible_getFestivalRange_returnsCorrectRange() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = real2026Json();

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");
        String cachedJson = entity.dataJson;

        LocalDate[] range = IcsParser.getFestivalRange(cachedJson, "spring_festival");
        assertNotNull(range);
        assertEquals(2, range.length);
        assertEquals(LocalDate.of(2026, 2, 15), range[0]);
        assertEquals(LocalDate.of(2026, 2, 23), range[1]);
    }

    @Test
    public void fill_outputCompatible_getFestivalRange_noFestival_returnsNull() {
        HolidayCacheEntity entity = emptyEntity(2026);
        String json = "{\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-01\",\"isOffDay\":true}"
            + "]}";

        HolidayJsonParser.fill(entity, json, 2026, "holiday-cn");
        String cachedJson = entity.dataJson;

        assertNull(IcsParser.getFestivalRange(cachedJson, "spring_festival"));
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private static HolidayCacheEntity emptyEntity(int year) {
        return HolidayCacheManager.emptyEntity(year);
    }

    /** 构造与 NateScarlet/holiday-cn 格式一致的 2026 年真实测试数据。 */
    private static String real2026Json() {
        return "{"
            + "\"$schema\":\"https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/schema.json\","
            + "\"$id\":\"https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/2026.json\","
            + "\"year\":2026,"
            + "\"papers\":[\"https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm\"],"
            + "\"days\":["
            + "{\"name\":\"元旦\",\"date\":\"2026-01-01\",\"isOffDay\":true},"
            + "{\"name\":\"元旦\",\"date\":\"2026-01-02\",\"isOffDay\":true},"
            + "{\"name\":\"元旦\",\"date\":\"2026-01-03\",\"isOffDay\":true},"
            + "{\"name\":\"元旦\",\"date\":\"2026-01-04\",\"isOffDay\":false},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-14\",\"isOffDay\":false},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-15\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-16\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-17\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-18\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-19\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-20\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-21\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-22\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-23\",\"isOffDay\":true},"
            + "{\"name\":\"春节\",\"date\":\"2026-02-28\",\"isOffDay\":false},"
            + "{\"name\":\"清明节\",\"date\":\"2026-04-04\",\"isOffDay\":true},"
            + "{\"name\":\"清明节\",\"date\":\"2026-04-05\",\"isOffDay\":true},"
            + "{\"name\":\"清明节\",\"date\":\"2026-04-06\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-01\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-02\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-03\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-04\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-05\",\"isOffDay\":true},"
            + "{\"name\":\"劳动节\",\"date\":\"2026-05-09\",\"isOffDay\":false},"
            + "{\"name\":\"端午节\",\"date\":\"2026-06-19\",\"isOffDay\":true},"
            + "{\"name\":\"端午节\",\"date\":\"2026-06-20\",\"isOffDay\":true},"
            + "{\"name\":\"端午节\",\"date\":\"2026-06-21\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-09-20\",\"isOffDay\":false},"
            + "{\"name\":\"中秋节\",\"date\":\"2026-09-25\",\"isOffDay\":true},"
            + "{\"name\":\"中秋节\",\"date\":\"2026-09-26\",\"isOffDay\":true},"
            + "{\"name\":\"中秋节\",\"date\":\"2026-09-27\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-01\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-02\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-03\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-04\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-05\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-06\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-07\",\"isOffDay\":true},"
            + "{\"name\":\"国庆节\",\"date\":\"2026-10-10\",\"isOffDay\":false}"
            + "]}";
    }
}
