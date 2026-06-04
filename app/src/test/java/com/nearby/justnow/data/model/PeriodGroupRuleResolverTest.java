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
