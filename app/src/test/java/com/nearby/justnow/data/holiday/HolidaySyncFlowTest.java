package com.nearby.justnow.data.holiday;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.data.dao.HolidayCacheDao;
import com.nearby.justnow.data.dao.TimePeriodDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.ScheduleProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;

/**
 * 完整业务流程测试 — 按"国家/地区 × 作息类型 × 时段组"矩阵覆盖。
 * OkHttp 拦截器模拟数据源下载，其余链路全部真实。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "zh-rCN")
public class HolidaySyncFlowTest {

    private static final String PREFS_NAME = "justnow_prefs";
    private static final String KEY_SCHEDULE_PROFILE = "schedule_profile";
    private static final String KEY_WORKDAY_POLICY = "workday_policy";

    private Context mContext;
    private AppDatabase mDb;
    private OkHttpClient mFakeCnClient;
    private OkHttpClient mFakeHkClient;
    private OkHttpClient mFakeMoClient;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(mContext);

        mFakeCnClient = newFileClient("NateScarlet/holiday-cn",
            "holiday/2026.json", "application/json");
        mFakeHkClient = newFileClient("1823.gov.hk",
            "holiday/hk.ics", "text/calendar");
        mFakeMoClient = newFileClient("gov.mo",
            "holiday/mo.ics", "text/calendar");
    }

    @After
    public void tearDown() throws Exception {
        if (mDb != null && mDb.isOpen()) mDb.close();
    }

    // =====================================================================
    // 中国大陆 · 常规作息 (GENERAL)
    // 可见组: WORKDAY, SPRING_FESTIVAL
    // =====================================================================

    /** 春节假期命中春节组 */
    @Test
    public void cnGeneral_springFestival() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-02-17   周二  SPRING_FESTIVAL
        insertCnCacheAndGroups(ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        // useHolidayData 组在 sync 路径需设显式日期（缓存读范围走 async）
        setGroupEnabled(PeriodGroupType.SPRING_FESTIVAL, true, "02-15", "02-23");

        assertGroup(PeriodGroupType.SPRING_FESTIVAL, 2026, 2, 17, "周二，春节假期中");
    }

    /** 普通周四命中工作日 */
    @Test
    public void cnGeneral_regularThursday_workday() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-08-06   周四  WORKDAY
        insertCnCacheAndGroups(ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.WORKDAY, 2026, 8, 6, "周四，普通工作日");
    }

    /** 法定假日命中常规 */
    @Test
    public void cnGeneral_legalHoliday_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-01   周四  REGULAR（Holiday→WORKDAY不命中→REGULAR兜底）
        insertCnCacheAndGroups(ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.REGULAR, 2026, 1, 1, "周四，元旦法定假日");

        // 同步版验证：isWorkdaySync 直接判断
        PeriodGroupRuleResolver r = currentResolver();
        assertEquals("法定假日 isWorkdaySync 返回 false", false,
            r.isWorkdaySync(cal(2026, 1, 1)));
    }

    /** 补班日命中工作日 */
    @Test
    public void cnGeneral_makeupDay_workday() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-04   周日  WORKDAY（常规认补班）
        insertCnCacheAndGroups(ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.WORKDAY, 2026, 1, 4, "周日，元旦补班日，常规认补班");
    }

    // =====================================================================
    // 中国大陆 · 学生作息 (SCHOOL)
    // 可见组: WORKDAY, SUMMER_VACATION, WINTER_VACATION
    // =====================================================================

    /** 暑假命中暑假组 */
    @Test
    public void cnSchool_summerVacation() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-07-15   周三  SUMMER_VACATION
        insertCnCacheAndGroups(ScheduleProfile.SCHOOL, STANDARD_WEEK);
        setGroupEnabled(PeriodGroupType.SUMMER_VACATION, true);
        setGroupEnabled(PeriodGroupType.WINTER_VACATION, true);

        assertGroup(PeriodGroupType.SUMMER_VACATION, 2026, 7, 15, "周三，暑假范围内");
    }

    /** 寒假命中寒假组 */
    @Test
    public void cnSchool_winterVacation() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-20   周二  WINTER_VACATION
        insertCnCacheAndGroups(ScheduleProfile.SCHOOL, STANDARD_WEEK);
        setGroupEnabled(PeriodGroupType.SUMMER_VACATION, true);
        setGroupEnabled(PeriodGroupType.WINTER_VACATION, true);

        assertGroup(PeriodGroupType.WINTER_VACATION, 2026, 1, 20, "周二，寒假范围内");
    }

    /** 普通周三命中工作日 */
    @Test
    public void cnSchool_wednesday_workday() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-09-09   周三  WORKDAY
        insertCnCacheAndGroups(ScheduleProfile.SCHOOL, STANDARD_WEEK);
        setGroupEnabled(PeriodGroupType.SUMMER_VACATION, true);
        setGroupEnabled(PeriodGroupType.WINTER_VACATION, true);

        assertGroup(PeriodGroupType.WORKDAY, 2026, 9, 9, "周三，非寒暑假，普通工作日");
    }

    /** 普通周六命中常规 */
    @Test
    public void cnSchool_saturday_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-09-12   周六  REGULAR
        insertCnCacheAndGroups(ScheduleProfile.SCHOOL, STANDARD_WEEK);
        setGroupEnabled(PeriodGroupType.SUMMER_VACATION, true);
        setGroupEnabled(PeriodGroupType.WINTER_VACATION, true);

        assertGroup(PeriodGroupType.REGULAR, 2026, 9, 12, "周六，非暑寒假，周末→REGULAR兜底");
    }

    // =====================================================================
    // 中国大陆 · 证券从业 (SECURITIES)
    // 可见组: WORKDAY, SPRING_FESTIVAL
    // 策略: LEGAL_HOLIDAY + 不跟补班
    // =====================================================================

    /** 普通周一命中工作日 */
    @Test
    public void cnSecurities_monday_workday() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-06-01   周一  WORKDAY
        insertCnCacheAndGroups(ScheduleProfile.SECURITIES, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.WORKDAY, 2026, 6, 1, "周一，普通工作日");
    }

    /** 补班日命中常规 */
    @Test
    public void cnSecurities_makeupDay_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-04   周日  REGULAR（补班日→证券不跟→周日→WORKDAY不命中）
        insertCnCacheAndGroups(ScheduleProfile.SECURITIES, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.REGULAR, 2026, 1, 4, "周日，补班日，证券不跟补班→算休息");
    }

    /** 春节补班命中常规 */
    @Test
    public void cnSecurities_springFestivalMakeup_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-02-14   周六  REGULAR（春节补班→证券不跟→周六→WORKDAY不命中）
        insertCnCacheAndGroups(ScheduleProfile.SECURITIES, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.REGULAR, 2026, 2, 14, "周六，春节补班日，证券不跟补班→算休息");
    }

    /** 法定假日命中常规 */
    @Test
    public void cnSecurities_legalHoliday_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-02-17   周二  REGULAR（Holiday→WORKDAY不命中）
        insertCnCacheAndGroups(ScheduleProfile.SECURITIES, LEGAL_HOLIDAY);

        assertGroup(PeriodGroupType.REGULAR, 2026, 2, 17, "周二，春节假期，法定假日");
    }

    // =====================================================================
    // 香港 · 常规作息 (GENERAL)
    // 可见组: WORKDAY, LONG_VACATION
    // =====================================================================

        @Config(qualifiers = "zh-rHK")
    /** 长假命中长假组 */
    @Test
    public void hkGeneral_longVacation() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-03-05   周四  LONG_VACATION
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertHkCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        TimePeriodGroupEntity lv = db.timePeriodDao().getGroupSync(PeriodGroupType.LONG_VACATION);
        lv.enabled = true;
        lv.startMonthDay = "03-01";
        lv.endMonthDay = "03-10";
        db.timePeriodDao().updateGroup(lv);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周四，长假范围内", PeriodGroupType.LONG_VACATION,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 3, 5)));
        db.close();
    }

        @Config(qualifiers = "zh-rHK")
    /** 普通周五命中工作日 */
    @Test
    public void hkGeneral_friday_workday() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-09   周五  WORKDAY
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertHkCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        setGroupEnabled(db, PeriodGroupType.LONG_VACATION, false);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周五，非假期，普通工作日", PeriodGroupType.WORKDAY,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 1, 9)));
        db.close();
    }

        @Config(qualifiers = "zh-rHK")
    /** 公众假期命中常规 */
    @Test
    public void hkGeneral_publicHoliday_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-01   周四  REGULAR（Holiday→WORKDAY不命中）
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertHkCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        setGroupEnabled(db, PeriodGroupType.LONG_VACATION, false);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周四，香港公众假期", PeriodGroupType.REGULAR,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 1, 1)));
        db.close();
    }

        @Config(qualifiers = "zh-rHK")
    /** 无补班周六命中常规 */
    @Test
    public void hkGeneral_saturday_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-10   周六  REGULAR（HK无补班→周六→WORKDAY不命中）
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertHkCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周六，香港无补班数据，始终休息", PeriodGroupType.REGULAR,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 1, 10)));
        db.close();
    }

    // =====================================================================
    // 澳门 · 常规作息 (GENERAL)
    // 可见组: WORKDAY, LONG_VACATION
    // =====================================================================

        @Config(qualifiers = "zh-rMO")
    /** 普通周一命中工作日 */
    @Test
    public void moGeneral_monday_workday() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-05-04   周一  WORKDAY
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertMoCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        setGroupEnabled(db, PeriodGroupType.LONG_VACATION, false);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周一，非假期，普通工作日", PeriodGroupType.WORKDAY,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 5, 4)));
        db.close();
    }

        @Config(qualifiers = "zh-rMO")
    /** 长假命中长假组 */
    @Test
    public void moGeneral_longVacation() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-06-15   周一  LONG_VACATION
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertMoCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);

        TimePeriodGroupEntity lv = db.timePeriodDao().getGroupSync(PeriodGroupType.LONG_VACATION);
        lv.enabled = true;
        lv.startMonthDay = "06-10";
        lv.endMonthDay = "06-20";
        db.timePeriodDao().updateGroup(lv);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周一，长假范围内", PeriodGroupType.LONG_VACATION,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 6, 15)));
        db.close();
    }

        @Config(qualifiers = "zh-rMO")
    /** 公众假期命中常规 */
    @Test
    public void moGeneral_publicHoliday_regular() throws IOException {
        //                    日期          周几  期望命中
        //                    2026-01-01   周四  REGULAR（Holiday→WORKDAY不命中）
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        AppDatabase db = AppDatabase.createInMemory(ctx);
        insertDefaultGroups(db);
        insertMoCache(db);
        setProfile(ctx, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        setGroupEnabled(db, PeriodGroupType.LONG_VACATION, false);

        PeriodGroupRuleResolver r = newResolver(ctx, db, ScheduleProfile.GENERAL, LEGAL_HOLIDAY);
        assertEquals("周四，澳门公众假期", PeriodGroupType.REGULAR,
            r.resolveActiveGroupType(db.timePeriodDao().getAllGroupsSync(), cal(2026, 1, 1)));
        db.close();
    }

    // =====================================================================
    // 辅助方法
    // =====================================================================

    private static final int STANDARD_WEEK = 0;
    private static final int LEGAL_HOLIDAY = 1;

    /** 插入大陆节假日缓存 + 默认时段组 + 设置 SharedPreferences */
    private void insertCnCacheAndGroups(String profile, int policy) throws java.io.IOException {
        insertDefaultGroups(mDb);
        setProfile(mContext, profile, policy);
        // 下载→解析→缓存 大陆数据
        ChinaGovSource src = new ChinaGovSource(mFakeCnClient);
        HolidayCacheEntity e = src.fetch(2026);
        assert e.dataJson != null;
        e.lastUpdated = System.currentTimeMillis();
        mDb.holidayCacheDao().insert(e);
    }

    /** 插入香港节假日缓存 */
    private void insertHkCache(AppDatabase db) throws java.io.IOException {
        HongKongGovSource src = new HongKongGovSource(mFakeHkClient);
        HolidayCacheEntity e = src.fetch(2026);
        assert e.dataJson != null;
        e.lastUpdated = System.currentTimeMillis();
        db.holidayCacheDao().insert(e);
    }

    /** 插入澳门节假日缓存 */
    private void insertMoCache(AppDatabase db) throws java.io.IOException {
        MacauGovSource src = new MacauGovSource(mFakeMoClient);
        HolidayCacheEntity e = src.fetch(2026);
        assert e.dataJson != null;
        e.lastUpdated = System.currentTimeMillis();
        db.holidayCacheDao().insert(e);
    }

    private void setProfile(Context ctx, String profile, int policy) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(KEY_SCHEDULE_PROFILE, profile)
            .putString(KEY_WORKDAY_POLICY, policy == LEGAL_HOLIDAY ? "legal_holiday" : "standard_week")
            .commit();
    }

    private void insertDefaultGroups(AppDatabase db) {
        db.timePeriodDao().insertGroups(Arrays.asList(
            group(PeriodGroupType.REGULAR, true),
            group(PeriodGroupType.WORKDAY, true),
            group(PeriodGroupType.SPRING_FESTIVAL, false),
            group(PeriodGroupType.LONG_VACATION, false),
            group(PeriodGroupType.SUMMER_VACATION, false),
            group(PeriodGroupType.WINTER_VACATION, false)
        ));
    }

    private void setGroupEnabled(String groupType, boolean enabled) {
        setGroupEnabled(mDb, groupType, enabled);
    }

    private void setGroupEnabled(String groupType, boolean enabled,
                                  String startMonthDay, String endMonthDay) {
        setGroupEnabled(mDb, groupType, enabled, startMonthDay, endMonthDay);
    }

    private void setGroupEnabled(AppDatabase db, String groupType, boolean enabled) {
        if (PeriodGroupType.isRegular(groupType)) return;
        TimePeriodGroupEntity g = db.timePeriodDao().getGroupSync(groupType);
        if (g != null) { g.enabled = enabled; db.timePeriodDao().updateGroup(g); }
    }

    private void setGroupEnabled(AppDatabase db, String groupType, boolean enabled,
                                  String startMonthDay, String endMonthDay) {
        if (PeriodGroupType.isRegular(groupType)) return;
        TimePeriodGroupEntity g = db.timePeriodDao().getGroupSync(groupType);
        if (g != null) {
            g.enabled = enabled;
            g.startMonthDay = startMonthDay;
            g.endMonthDay = endMonthDay;
            db.timePeriodDao().updateGroup(g);
        }
    }

    private PeriodGroupRuleResolver newResolver(Context ctx, AppDatabase db,
                                                  String profile, int policy) {
        setProfile(ctx, profile, policy);
        return new PeriodGroupRuleResolver(ctx,
            new HolidayCacheManager(db.holidayCacheDao()));
    }

    private void assertGroup(String expected, int year, int month, int day, String desc) {
        PeriodGroupRuleResolver r = currentResolver();
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();
        String actual = r.resolveActiveGroupType(groups, cal(year, month, day));
        assertEquals(desc, expected, actual);
    }

    private PeriodGroupRuleResolver currentResolver() {
        String profile = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULE_PROFILE, ScheduleProfile.GENERAL);
        String pol = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WORKDAY_POLICY, "standard_week");
        int p = "legal_holiday".equals(pol) ? LEGAL_HOLIDAY : STANDARD_WEEK;
        return newResolver(mContext, mDb,
            ScheduleProfile.normalizeProfile(profile, true), p);
    }

    private static TimePeriodGroupEntity group(String type, boolean enabled) {
        TimePeriodGroupEntity g = new TimePeriodGroupEntity();
        g.groupType = type;
        g.displayOrder = PeriodGroupType.getDisplayOrder(type);
        g.enabled = enabled;
        g.lastEditedAt = 0;
        if (PeriodGroupType.SUMMER_VACATION.equals(type)) {
            g.startMonthDay = "07-01"; g.endMonthDay = "08-31";
        } else if (PeriodGroupType.WINTER_VACATION.equals(type)) {
            g.startMonthDay = "01-15"; g.endMonthDay = "02-20";
        }
        return g;
    }

    private static Calendar cal(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        c.set(y, m - 1, d, 12, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    /** 从 test/resources 读文件并以 HTTP 200 返回的 OkHttp 客户端 */
    private static OkHttpClient newFileClient(String urlKeyword, String resourcePath,
                                               String mediaType) {
        return new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                if (chain.request().url().toString().contains(urlKeyword)) {
                    String body = readResource(resourcePath);
                    if (body == null) return chain.proceed(chain.request());
                    return new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200).message("OK")
                        .body(ResponseBody.create(body, MediaType.get(mediaType + "; charset=utf-8")))
                        .build();
                }
                return chain.proceed(chain.request());
            }).build();
    }

    private static String readResource(String path) {
        try (InputStream is = HolidaySyncFlowTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            try (Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                return s.hasNext() ? s.next() : "";
            }
        } catch (IOException e) {
            return null;
        }
    }
}
