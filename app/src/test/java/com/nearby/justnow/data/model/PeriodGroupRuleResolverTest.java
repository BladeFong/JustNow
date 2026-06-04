package com.nearby.justnow.data.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.data.dao.HolidayCacheDao;
import com.nearby.justnow.data.dao.TimePeriodDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.holiday.HolidayCacheManager;
import com.nearby.justnow.data.holiday.HolidayJsonParser;
import com.nearby.justnow.data.holiday.IcsParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "zh-rCN")
public class PeriodGroupRuleResolverTest {

    private static final String PREFS_NAME = "justnow_prefs";
    private static final String KEY_SCHEDULE_PROFILE = "schedule_profile";
    private static final String KEY_WORKDAY_POLICY = "workday_policy";

    private Context mContext;
    private AppDatabase mDb;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(mContext);
        insertDefaultGroups();
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- isWorkday（旧 Mon-Fri）----

    @Test
    public void resolveActiveGroupType_monday_returnsWorkday() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        assertEquals(PeriodGroupType.WORKDAY,
            resolver.resolveActiveGroupType(groups, makeCalendar(2026, 3, 5))); // 周四
    }

    @Test
    public void resolveActiveGroupType_saturday_returnsRegular() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        assertEquals(PeriodGroupType.REGULAR,
            resolver.resolveActiveGroupType(groups, makeCalendar(2026, 3, 7))); // 周六
    }

    // ---- isWorkdaySync（LEGAL_HOLIDAY + 节假日缓存）----

    @Test
    public void isWorkdaySync_holidayDate_returnsFalse() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);

        // 法定假日（周四） → 不是工作日
        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 1, 1)));
    }

    @Test
    public void isWorkdaySync_generalHonor补班_returnsTrue() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);

        // 补班日（周六） → 常规认补班 → 是工作日
        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 1, 31)));
    }

    @Test
    public void isWorkdaySync_securitiesIgnore补班_returnsFalse() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.SECURITIES, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);

        // 补班日（周六） → 证券不跟补班 → Mon-Fri 兜底 → 周六不是工作日
        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 1, 31)));
    }

    @Test
    public void isWorkdaySync_normalWeekday_returnsTrue() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);

        // 普通周四，不在缓存中 → Mon-Fri 兜底 → 是工作日
        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 3, 5)));
    }

    @Test
    public void isWorkdaySync_cacheNotExists_monFriFallback() {
        // 无缓存数据 → Mon-Fri 兜底
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);

        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 3, 5)));  // 周四
        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 3, 7))); // 周六
    }

    @Test
    public void isWorkdaySync_standardWeek_ignoresCache() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);

        // STANDARD_WEEK 不读缓存 → 周四（法定假日也仍算工作日）
        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 1, 1)));
    }

    // ---- WorkdayMode SIX_DAY / STANDARD_5 ----

    @Test
    public void isWorkday_standard5_saturdayNotWorkday() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK,
            PeriodGroupRuleResolver.WorkdayMode.STANDARD_5);

        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 3, 7)));  // 周六
        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 3, 8)));  // 周日
        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 3, 6)));   // 周五
    }

    @Test
    public void isWorkday_sixDay_saturdayIsWorkday_sundayNot() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK,
            PeriodGroupRuleResolver.WorkdayMode.SIX_DAY);

        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 3, 7)));   // 周六=工作日
        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 3, 8)));  // 周日仍非工作日
        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 3, 6)));   // 周五
    }

    @Test
    public void isWorkdaySync_sixDay_legalHoliday_saturdayHolidayNotWorkday() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-03\"],"
            + "\"makeupWorkdays\":[]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY,
            PeriodGroupRuleResolver.WorkdayMode.SIX_DAY);

        // 2026-01-03 是周六，在法定假日列表中 → 不是工作日
        assertEquals(false, resolver.isWorkdaySync(makeCalendar(2026, 1, 3)));
    }

    @Test
    public void isWorkdaySync_sixDay_legalHoliday_normalSaturdayIsWorkday() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY,
            PeriodGroupRuleResolver.WorkdayMode.SIX_DAY);

        // 2026-01-03 是周六，不在假日缓存中 → SIX_DAY 兜底 → 是工作日
        assertEquals(true, resolver.isWorkdaySync(makeCalendar(2026, 1, 3)));
    }

    @Test
    public void resolveActiveGroupType_sixDay_saturdayHitsWorkday() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK,
            PeriodGroupRuleResolver.WorkdayMode.SIX_DAY);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        assertEquals(PeriodGroupType.WORKDAY,
            resolver.resolveActiveGroupType(groups, makeCalendar(2026, 3, 7))); // 周六
    }

    @Test
    public void resolveActiveGroupType_standard5_saturdayHitsRegular() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK,
            PeriodGroupRuleResolver.WorkdayMode.STANDARD_5);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        assertEquals(PeriodGroupType.REGULAR,
            resolver.resolveActiveGroupType(groups, makeCalendar(2026, 3, 7))); // 周六
    }

    @Test
    public void resolveAsync_sixDay_saturdayHitsWorkday() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK,
            PeriodGroupRuleResolver.WorkdayMode.SIX_DAY);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        String result = resolveAsyncWithPump(resolver, groups, makeCalendar(2026, 3, 7));
        assertEquals(PeriodGroupType.WORKDAY, result);
    }

    @Test
    public void getWorkdayMode_defaultReturnsStandard5() {
        // 不设 workday_mode → 默认 STANDARD_5
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        assertEquals(PeriodGroupRuleResolver.WorkdayMode.STANDARD_5, resolver.getWorkdayMode());
    }

    @Test
    public void setWorkdayMode_sixDay_persistsAndReads() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        resolver.setWorkdayMode(PeriodGroupRuleResolver.WorkdayMode.SIX_DAY);
        assertEquals(PeriodGroupRuleResolver.WorkdayMode.SIX_DAY, resolver.getWorkdayMode());

        // 验证持久化：新建 resolver 读回
        HolidayCacheManager cacheMgr = new HolidayCacheManager(mDb.holidayCacheDao());
        PeriodGroupRuleResolver resolver2 = new PeriodGroupRuleResolver(mContext, cacheMgr);
        assertEquals(PeriodGroupRuleResolver.WorkdayMode.SIX_DAY, resolver2.getWorkdayMode());
    }

    // ---- resolveActiveGroupTypeAsync（异步路径回归）----

    @Test
    public void resolveAsync_legalHolidayWithCache_holidayDateFallsToRegular() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"],"
            + "\"festivals\":[{\"type\":\"spring_festival\",\"start\":\"2026-02-17\",\"end\":\"2026-02-23\"}]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        String result = resolveAsyncWithPump(resolver, groups, makeCalendar(2026, 1, 1));
        assertEquals(PeriodGroupType.REGULAR, result);
    }

    @Test
    public void resolveAsync_securitiesMakeupWorkday_fallsToRegular() {
        insertHolidayCache(2026,
            "{\"year\":2026,\"source\":\"holiday-cn\","
            + "\"holidays\":[\"2026-01-01\"],"
            + "\"makeupWorkdays\":[\"2026-01-31\"]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.SECURITIES, PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();

        String result = resolveAsyncWithPump(resolver, groups, makeCalendar(2026, 1, 31));
        assertEquals(PeriodGroupType.REGULAR, result);
    }

    // ---- participatesInTimelineSync 便捷重载（F 本轮新增）----

    @Test
    public void participatesInTimelineSync_noProfileParam_usesDefaultProfile() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);

        TimePeriodGroupEntity workdayGroup = createGroup(PeriodGroupType.WORKDAY, true, false);

        // 周四 → 工作日 → 参与时间线
        assertTrue(resolver.participatesInTimelineSync(workdayGroup,
            makeCalendar(2026, 3, 5)));

        // 周六 → 非工作日 → 不参与时间线
        assertFalse(resolver.participatesInTimelineSync(workdayGroup,
            makeCalendar(2026, 3, 7)));

        // null 组 → false
        assertFalse(resolver.participatesInTimelineSync(null,
            makeCalendar(2026, 3, 5)));
    }

    @Test
    public void participatesInTimelineSync_regularGroup_alwaysTrue() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity regularGroup = createGroup(PeriodGroupType.REGULAR, true, false);

        assertTrue(resolver.participatesInTimelineSync(regularGroup,
            makeCalendar(2026, 3, 7))); // 周六也应返回 true
    }

    @Test
    public void participatesInTimelineSync_disabledGroup_false() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity disabledWorkday = createGroup(PeriodGroupType.WORKDAY, false, false);

        assertFalse(resolver.participatesInTimelineSync(disabledWorkday,
            makeCalendar(2026, 3, 5))); // 周四但组 disabled
    }

    // ---- canMatchInNextThreeMonths（跨年窗口测试）----

    @Test
    public void canMatchInNextThreeMonths_crossYear_winterVacationMatches() {
        // 今天=11月15日，3个月后=2月15日 → 窗口跨越年底
        // 寒假组 01-01 ~ 01-31 两端点都落入跨年窗口内 → 应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity winterGroup = new TimePeriodGroupEntity();
        winterGroup.groupType = PeriodGroupType.WINTER_VACATION;
        winterGroup.enabled = true;
        winterGroup.displayOrder = 3;
        winterGroup.startMonthDay = "01-01";
        winterGroup.endMonthDay = "01-31";

        Calendar today = makeCalendar(2026, 11, 15);
        assertTrue("寒假组应在跨年窗口内命中",
            resolver.canMatchInNextThreeMonths(winterGroup, today));
    }

    @Test
    public void canMatchInNextThreeMonths_crossYear_groupCrossYearAlsoMatches() {
        // 今天=11月15日，假期组自身跨年（12-20 ~ 01-10）→ 应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity winterGroup = new TimePeriodGroupEntity();
        winterGroup.groupType = PeriodGroupType.WINTER_VACATION;
        winterGroup.enabled = true;
        winterGroup.displayOrder = 3;
        winterGroup.startMonthDay = "12-20";
        winterGroup.endMonthDay = "01-10";

        Calendar today = makeCalendar(2026, 11, 15);
        assertTrue("自身跨年的假期组应在跨年窗口内命中",
            resolver.canMatchInNextThreeMonths(winterGroup, today));
    }

    @Test
    public void canMatchInNextThreeMonths_normalWindow_summerVacationMatches() {
        // 今天=6月，窗口 6月~9月，暑假 07-01 ~ 08-31 → 应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity summerGroup = new TimePeriodGroupEntity();
        summerGroup.groupType = PeriodGroupType.SUMMER_VACATION;
        summerGroup.enabled = true;
        summerGroup.displayOrder = 3;
        summerGroup.startMonthDay = "07-01";
        summerGroup.endMonthDay = "08-31";

        Calendar today = makeCalendar(2026, 6, 5);
        assertTrue("暑假组应在非跨年窗口内命中",
            resolver.canMatchInNextThreeMonths(summerGroup, today));
    }

    @Test
    public void canMatchInNextThreeMonths_normalWindow_vacationNotYetStarted_noMatch() {
        // 今天=3月，窗口 3月~6月，暑假 07-01 ~ 08-31 尚未开始 → 不应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity summerGroup = new TimePeriodGroupEntity();
        summerGroup.groupType = PeriodGroupType.SUMMER_VACATION;
        summerGroup.enabled = true;
        summerGroup.displayOrder = 3;
        summerGroup.startMonthDay = "07-01";
        summerGroup.endMonthDay = "08-31";

        Calendar today = makeCalendar(2026, 3, 5);
        assertFalse("暑假组在3月不应命中",
            resolver.canMatchInNextThreeMonths(summerGroup, today));
    }

    @Test
    public void canMatchInNextThreeMonths_nullGroup_returnsFalse() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        assertFalse(resolver.canMatchInNextThreeMonths(null));
    }

    @Test
    public void canMatchInNextThreeMonths_regularGroup_returnsTrue() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity regularGroup = createGroup(PeriodGroupType.REGULAR, true, false);
        assertTrue("REGULAR 组应始终可命中",
            resolver.canMatchInNextThreeMonths(regularGroup,
                makeCalendar(2026, 3, 5)));
    }

    @Test
    public void canMatchInNextThreeMonths_workdayGroup_returnsTrue() {
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity workdayGroup = createGroup(PeriodGroupType.WORKDAY, true, false);
        assertTrue("WORKDAY 组应始终可命中",
            resolver.canMatchInNextThreeMonths(workdayGroup,
                makeCalendar(2026, 3, 5)));
    }

    @Test
    public void canMatchInNextThreeMonths_springFestival_crossYearWindow_matches() {
        // 今天=11月15日，窗口跨年 1115-0215
        // 春季节假日数据: 2027-01-28 ~ 2027-02-04，startMMDD=128，在窗口内 → 命中
        insertHolidayCache(2027,
            "{\"year\":2027,\"source\":\"holiday-cn\","
            + "\"holidays\":[],\"makeupWorkdays\":[],"
            + "\"festivals\":[{\"type\":\"spring_festival\",\"start\":\"2027-01-28\",\"end\":\"2027-02-04\"}]}");

        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);

        TimePeriodGroupEntity springGroup = new TimePeriodGroupEntity();
        springGroup.groupType = PeriodGroupType.SPRING_FESTIVAL;
        springGroup.enabled = true;
        springGroup.useHolidayData = true;
        springGroup.displayOrder = 2;

        Calendar today = makeCalendar(2026, 11, 15);
        assertTrue("春节假期组应在跨年窗口内命中",
            resolver.canMatchInNextThreeMonths(springGroup, today));
    }

    @Test
    public void canMatchInNextThreeMonths_crossYear_vacationEndPointOutsideWindow_noMatch() {
        // 今天=11月15日，窗口=1115-0215
        // 假期组 03-01 ~ 04-01 两端点都在窗口外 → 不应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.LONG_VACATION;
        group.enabled = true;
        group.displayOrder = 3;
        group.startMonthDay = "03-01";
        group.endMonthDay = "04-01";

        Calendar today = makeCalendar(2026, 11, 15);
        assertFalse("3月的假期组不应在 11月~2月 的跨年窗口内命中",
            resolver.canMatchInNextThreeMonths(group, today));
    }

    @Test
    public void canMatchInNextThreeMonths_crossYear_onlyStartInWindow_matches() {
        // 今天=11月15日，窗口=1115-0215
        // 假期组 01-15 ~ 03-20：start=115 在窗口内，end=320 不在
        // → groupStart 落入跨年窗口应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.WINTER_VACATION;
        group.enabled = true;
        group.displayOrder = 3;
        group.startMonthDay = "01-15";
        group.endMonthDay = "03-20";

        Calendar today = makeCalendar(2026, 11, 15);
        assertTrue("仅 start 端点落入跨年窗口应命中",
            resolver.canMatchInNextThreeMonths(group, today));
    }

    @Test
    public void canMatchInNextThreeMonths_crossYear_onlyEndInWindow_matches() {
        // 今天=11月15日，窗口=1115-0215
        // 假期组 10-01 ~ 12-01：start=1001 不在窗口内，end=1201 在窗口内
        // → groupEnd 落入跨年窗口应命中
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.WINTER_VACATION;
        group.enabled = true;
        group.displayOrder = 3;
        group.startMonthDay = "10-01";
        group.endMonthDay = "12-01";

        Calendar today = makeCalendar(2026, 11, 15);
        assertTrue("仅 end 端点落入跨年窗口应命中",
            resolver.canMatchInNextThreeMonths(group, today));
    }

    @Test
    public void canMatchInNextThreeMonths_crossYear_holidayCustomRange_matches() {
        // 今天=11月15日，窗口=1115-0215
        // SPRING_FESTIVAL 设自定义范围 01-01 ~ 01-31，不走节假日数据
        // → hasCustomRange=true → 走 vacationCanMatch 路径
        PeriodGroupRuleResolver resolver = newResolver(
            ScheduleProfile.GENERAL, PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.SPRING_FESTIVAL;
        group.enabled = true;
        group.useHolidayData = false; // 不走节假日数据，走自定义范围
        group.displayOrder = 2;
        group.startMonthDay = "01-01";
        group.endMonthDay = "01-31";

        Calendar today = makeCalendar(2026, 11, 15);
        assertTrue("假日组自定义范围在跨年窗口内应命中",
            resolver.canMatchInNextThreeMonths(group, today));
    }

    // ---- 辅助方法 ----

    private void insertDefaultGroups() {
        TimePeriodDao dao = mDb.timePeriodDao();
        dao.insertGroups(Arrays.asList(
            createGroup(PeriodGroupType.REGULAR, true, false),
            createGroup(PeriodGroupType.WORKDAY, true, false),
            createGroup(PeriodGroupType.SPRING_FESTIVAL, false, true),
            createGroup(PeriodGroupType.LONG_VACATION, false, false),
            createGroup(PeriodGroupType.SUMMER_VACATION, false, false),
            createGroup(PeriodGroupType.WINTER_VACATION, false, false)
        ));
    }

    private PeriodGroupRuleResolver newResolver(String profile,
                                                 PeriodGroupRuleResolver.WorkdayPolicy policy) {
        return newResolver(profile, policy, PeriodGroupRuleResolver.WorkdayMode.STANDARD_5);
    }

    private PeriodGroupRuleResolver newResolver(String profile,
                                                 PeriodGroupRuleResolver.WorkdayPolicy policy,
                                                 PeriodGroupRuleResolver.WorkdayMode mode) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_SCHEDULE_PROFILE, profile)
            .putString(KEY_WORKDAY_POLICY, policy == PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY
                ? "legal_holiday" : "standard_week")
            .putString("workday_mode", mode == PeriodGroupRuleResolver.WorkdayMode.SIX_DAY
                ? "six_day" : "standard_5")
            .commit();

        HolidayCacheManager cacheMgr = new HolidayCacheManager(mDb.holidayCacheDao());
        return new PeriodGroupRuleResolver(mContext, cacheMgr);
    }

    private void insertHolidayCache(int year, String json) {
        HolidayCacheDao dao = mDb.holidayCacheDao();
        HolidayCacheEntity entity = new HolidayCacheEntity();
        entity.year = year;
        entity.dataJson = json;
        entity.lastUpdated = System.currentTimeMillis();
        dao.insert(entity);
    }

    /**
     * 异步解析 + ShadowLooper 泵取回调结果。
     * 后台 executor 完成后通过 Handler(Looper.getMainLooper()) 回到主线程，
     * 需要手动 idle 主 Looper 让回调执行。
     */
    private String resolveAsyncWithPump(PeriodGroupRuleResolver resolver,
                                         List<TimePeriodGroupEntity> groups,
                                         Calendar cal) {
        AtomicReference<String> result = new AtomicReference<>();
        resolver.resolveActiveGroupTypeAsync(groups, cal, result::set);

        // 给后台线程时间执行完并 post 到主 Looper
        long deadline = System.currentTimeMillis() + 5000;
        while (result.get() == null && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        assertNotNull("Async resolution timed out", result.get());
        return result.get();
    }

    private static TimePeriodGroupEntity createGroup(String groupType, boolean enabled,
                                                     boolean useHolidayData) {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = groupType;
        group.displayOrder = PeriodGroupType.getDisplayOrder(groupType);
        group.enabled = enabled;
        group.useHolidayData = useHolidayData;
        group.lastEditedAt = 0;
        if (PeriodGroupType.SUMMER_VACATION.equals(groupType)) {
            group.startMonthDay = "07-01";
            group.endMonthDay = "08-31";
        } else if (PeriodGroupType.WINTER_VACATION.equals(groupType)) {
            group.startMonthDay = "01-15";
            group.endMonthDay = "02-20";
        }
        return group;
    }

    private static Calendar makeCalendar(int year, int month, int day) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }
}
