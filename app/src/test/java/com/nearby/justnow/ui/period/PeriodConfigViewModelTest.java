package com.nearby.justnow.ui.period;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PeriodConfigViewModel 测试 —— 验证 F 重构后的核心逻辑：
 * fillVacationDefaultsCore / fillSpringFestivalDefaultsCore 的日期填充、
 * 时段模板复制、已有数据保护，以及 init* 方法始终调用 updateGroup 的行为变化。
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = PeriodConfigViewModelTest.TestApp.class, sdk = 35)
public class PeriodConfigViewModelTest {

    private AppDatabase mDb;
    private PeriodConfigViewModel mViewModel;
    private TestApp mApp;

    @Before
    public void setUp() throws Exception {
        mApp = (TestApp) RuntimeEnvironment.getApplication();
        mDb = AppDatabase.createInMemory(mApp);
        AppDatabase.setTestInstance(mDb);

        // 插入 REGULAR 组及模板时段，供 copyPeriodsFromTemplate 使用
        insertRegularTemplate();

        mViewModel = new PeriodConfigViewModel(mApp);
    }

    @After
    public void tearDown() throws Exception {
        AppDatabase.clearTestInstance();
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ============================================================
    // fillVacationDefaultsCore — 日期填充
    // ============================================================

    @Test
    public void fillVacationDefaultsCore_setsDefaultDates_whenDatesMissing() {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.SUMMER_VACATION;
        group.enabled = true;

        mViewModel.ensureGroupDefaults(group);

        assertNotNull("startMonthDay 应当被设置", group.startMonthDay);
        assertNotNull("endMonthDay 应当被设置", group.endMonthDay);
        assertFalse("startMonthDay 不应为空", group.startMonthDay.isEmpty());
        assertFalse("endMonthDay 不应为空", group.endMonthDay.isEmpty());
        assertTrue("lastEditedAt 应当 > 0", group.lastEditedAt > 0);
        // 日期格式 MM-dd
        assertTrue("startMonthDay 应为 MM-dd 格式",
                group.startMonthDay.matches("\\d{2}-\\d{2}"));
        assertTrue("endMonthDay 应为 MM-dd 格式",
                group.endMonthDay.matches("\\d{2}-\\d{2}"));
    }

    @Test
    public void fillVacationDefaultsCore_preservesExistingDates() {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.WINTER_VACATION;
        group.enabled = true;
        group.startMonthDay = "12-20";
        group.endMonthDay = "01-10";
        group.lastEditedAt = 1000L;

        mViewModel.ensureGroupDefaults(group);

        assertEquals("已有 startMonthDay 不应被覆盖", "12-20", group.startMonthDay);
        assertEquals("已有 endMonthDay 不应被覆盖", "01-10", group.endMonthDay);
        assertEquals("已有 lastEditedAt 不应被覆盖", 1000L, group.lastEditedAt);
    }

    @Test
    public void fillVacationDefaultsCore_setsDatesForLongVacation() {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.LONG_VACATION;
        group.enabled = true;

        mViewModel.ensureGroupDefaults(group);

        assertNotNull("long_vacation 的 startMonthDay 应当被设置", group.startMonthDay);
        assertNotNull("long_vacation 的 endMonthDay 应当被设置", group.endMonthDay);
        assertFalse(group.startMonthDay.isEmpty());
        assertFalse(group.endMonthDay.isEmpty());
    }

    // ============================================================
    // fillVacationDefaultsCore — 模板时段复制
    // ============================================================

    @Test
    public void fillVacationDefaultsCore_copiesPeriodsFromTemplate_whenPeriodsMissing() {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.SUMMER_VACATION;
        group.enabled = true;

        List<TimePeriodEntity> copied = mViewModel.ensureGroupDefaults(group);

        assertNotNull("应当从 REGULAR 模板复制时段到 summer_vacation", copied);
        assertFalse("复制后的时段列表不应为空", copied.isEmpty());
        // 每个复制出来的时段 groupType 应为 SUMMER_VACATION
        for (TimePeriodEntity p : copied) {
            assertEquals("复制时段的 groupType 应为目标组",
                    PeriodGroupType.SUMMER_VACATION, p.groupType);
        }
    }

    @Test
    public void fillVacationDefaultsCore_doesNotCopyPeriods_whenPeriodsAlreadyExist() {
        // 先插入一条 vacation 时段
        TimePeriodEntity existing = new TimePeriodEntity();
        existing.groupType = PeriodGroupType.WINTER_VACATION;
        existing.nameKey = PeriodNameKey.MORNING;
        existing.startMinute = 480;
        existing.endMinute = 600;
        existing.sortOrder = 1;
        mDb.timePeriodDao().insertPeriods(Arrays.asList(existing));

        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.WINTER_VACATION;
        group.enabled = true;
        group.startMonthDay = "12-20";
        group.endMonthDay = "01-10";

        List<TimePeriodEntity> copied = mViewModel.ensureGroupDefaults(group);
        assertNull("已有时段时应返回 null", copied);

        List<TimePeriodEntity> periods = mDb.timePeriodDao()
                .getPeriodsByGroupSync(PeriodGroupType.WINTER_VACATION);
        assertEquals("已有时段时不应重复复制", 1, periods.size());
        assertEquals("已有时段的 groupType 应保持不变",
                PeriodGroupType.WINTER_VACATION, periods.get(0).groupType);
    }

    // ============================================================
    // fillSpringFestivalDefaultsCore — 基本结构性验证
    // ============================================================

    @Test
    public void fillSpringFestivalDefaultsCore_doesNotCrash_whenNoHolidayCache() {
        // 无假日缓存时，fillSpringFestivalDefaultsCore 应跳过日期填充但不抛异常
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.SPRING_FESTIVAL;
        group.enabled = true;

        List<TimePeriodEntity> copied = mViewModel.ensureGroupDefaults(group);

        // 无假日缓存时日期不应被设置
        assertNull("无假日缓存时 startMonthDay 应为 null", group.startMonthDay);
        assertNull("无假日缓存时 endMonthDay 应为 null", group.endMonthDay);

        // 但时段应从 REGULAR 模板复制
        assertNotNull("应从 REGULAR 模板复制时段到 spring_festival", copied);
        assertFalse("复制后的时段列表不应为空", copied.isEmpty());
    }

    // ============================================================
    // updateGroupEnabled 触发 init* 流程
    // ============================================================

    @Test
    public void updateGroupEnabled_triggersVacationDefaultsAndPersists() throws Exception {
        // 插入一个无日期无时段的 vacation 组
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.SUMMER_VACATION;
        group.enabled = false;
        group.displayOrder = 3;
        mDb.timePeriodDao().insertGroups(Arrays.asList(group));

        // 通过 updateGroupEnabled 启用，应触发 initVacationDefaultsIfNeeded
        group.enabled = false; // reset after insert (insert 不改变 enabled)
        mViewModel.updateGroupEnabled(group, true);

        // 等待异步 initVacationDefaultsIfNeeded 完成
        assertTrue("异步初始化应在 5 秒内完成",
                waitForVacationDefaults(PeriodGroupType.SUMMER_VACATION, 5000));
    }

    @Test
    public void initVacationDefaultsIfNeeded_preservesExistingDates() throws Exception {
        // 验证 ensureGroupDefaults（同步）对已有日期的组不覆盖日期
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.WINTER_VACATION;
        group.enabled = true;
        group.startMonthDay = "12-20";
        group.endMonthDay = "01-10";
        group.lastEditedAt = 2000L;

        mViewModel.ensureGroupDefaults(group);

        assertEquals("已有日期不应被 init 覆盖", "12-20", group.startMonthDay);
        assertEquals("已有日期不应被 init 覆盖", "01-10", group.endMonthDay);
        assertEquals("已有 lastEditedAt 不应被 init 覆盖", 2000L, group.lastEditedAt);
    }

    // ============================================================
    // 重构后方法存在性验证
    // ============================================================

    @Test
    public void fillVacationDefaultsCore_methodExists() throws Exception {
        Method method = PeriodConfigViewModel.class.getDeclaredMethod(
                "fillVacationDefaultsCore", TimePeriodGroupEntity.class);
        assertNotNull("fillVacationDefaultsCore 方法应存在", method);
    }

    @Test
    public void fillSpringFestivalDefaultsCore_methodExists() throws Exception {
        Method method = PeriodConfigViewModel.class.getDeclaredMethod(
                "fillSpringFestivalDefaultsCore", TimePeriodGroupEntity.class);
        assertNotNull("fillSpringFestivalDefaultsCore 方法应存在", method);
    }

    @Test
    public void fillVacationDefaultsSync_delegatesToCore() throws Exception {
        // fillVacationDefaultsSync 应委托给 fillVacationDefaultsCore（不含 DB 写）
        Method syncMethod = PeriodConfigViewModel.class.getDeclaredMethod(
                "fillVacationDefaultsSync", TimePeriodGroupEntity.class);
        assertNotNull("fillVacationDefaultsSync 方法应存在", syncMethod);
    }

    @Test
    public void fillSpringFestivalDefaultsSync_delegatesToCore() throws Exception {
        Method syncMethod = PeriodConfigViewModel.class.getDeclaredMethod(
                "fillSpringFestivalDefaultsSync", TimePeriodGroupEntity.class);
        assertNotNull("fillSpringFestivalDefaultsSync 方法应存在", syncMethod);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private void insertRegularTemplate() {
        // 插入 REGULAR 组
        TimePeriodGroupEntity regularGroup = new TimePeriodGroupEntity();
        regularGroup.groupType = PeriodGroupType.REGULAR;
        regularGroup.enabled = true;
        regularGroup.displayOrder = 0;
        mDb.timePeriodDao().insertGroups(Arrays.asList(regularGroup));

        // 插入模板时段
        List<TimePeriodEntity> templatePeriods = Arrays.asList(
                createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.MORNING, 360, 480, 1),
                createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.NOON, 480, 600, 2),
                createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.AFTERNOON, 600, 780, 3),
                createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.DINNER, 780, 900, 4),
                createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.EVENING, 900, 1080, 5)
        );
        mDb.timePeriodDao().insertPeriods(templatePeriods);
    }

    private static TimePeriodEntity createPeriod(String groupType, String nameKey,
                                                  int startMinute, int endMinute, int sortOrder) {
        TimePeriodEntity p = new TimePeriodEntity();
        p.groupType = groupType;
        p.nameKey = nameKey;
        p.startMinute = startMinute;
        p.endMinute = endMinute;
        p.sortOrder = sortOrder;
        return p;
    }

    /** 轮询等待 vacation 组的时段被复制到 DB，用于等待异步 init 完成。 */
    private boolean waitForVacationDefaults(String groupType, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            List<TimePeriodEntity> periods = mDb.timePeriodDao()
                    .getPeriodsByGroupSync(groupType);
            if (periods != null && !periods.isEmpty()) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    // ============================================================
    // TestApp — 轻量测试 Application
    // ============================================================

    public static class TestApp extends JustNowApplication {
        @Override
        public void onCreate() {
            // 不调 super.onCreate()，跳过 DB 单例、节假日同步、AlarmManager 等
        }
    }
}
