package com.nearby.justnow.ui.taskschedule;

import android.content.Context;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.holiday.HolidayCacheManager;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;
import com.nearby.justnow.data.model.ScheduleProfile;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TaskScheduleViewModel 测试（F 本轮改动相关方法）。
 *
 * <p>重点覆盖：
 * <ul>
 *   <li>{@link TaskScheduleViewModel#getOccupiedSlots(long, long)} — 两步匹配（linkedPeriodGroupType 驱动）</li>
 *   <li>{@link TaskScheduleViewModel#getPeriodsByGroupSync(String)} — 过滤 preferChore</li>
 *   <li>{@link TaskScheduleViewModel#getActivePeriodGroupForDate(long)} — 委托 repo</li>
 *   <li>{@link TaskScheduleViewModel#dateAndTimeToMs(long, int)} — 工具方法</li>
 *   <li>findGroupByType（private static）— 反射验证</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskScheduleViewModelTest {

    private AppDatabase mDb;
    private TestApplication mApp;
    private TaskScheduleViewModel mViewModel;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mApp = new TestApplication(mDb, context);
        mViewModel = new TaskScheduleViewModel(mApp);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- dateAndTimeToMs ----

    @Test
    public void dateAndTimeToMs_zeroMinute_returnsDateMs() {
        long dateMs = 1717459200000L; // 2024-06-04 00:00 UTC
        assertEquals(dateMs, TaskScheduleViewModel.dateAndTimeToMs(dateMs, 0));
    }

    @Test
    public void dateAndTimeToMs_8AM_returnsDateMsPlus480Minutes() {
        long dateMs = 1717459200000L;
        assertEquals(dateMs + 480 * 60000L, TaskScheduleViewModel.dateAndTimeToMs(dateMs, 480));
    }

    @Test
    public void dateAndTimeToMs_2359_returnsDateMsPlus1439Minutes() {
        long dateMs = 1717459200000L;
        assertEquals(dateMs + 1439 * 60000L, TaskScheduleViewModel.dateAndTimeToMs(dateMs, 1439));
    }

    // ---- findGroupByType（private static，反射）----

    @Test
    public void findGroupByType_nullList_returnsNull() throws Exception {
        Method method = getFindGroupByTypeMethod();
        Object result = method.invoke(null, (List<TimePeriodGroupEntity>) null, PeriodGroupType.WORKDAY);
        assertEquals(null, result);
    }

    @Test
    public void findGroupByType_emptyList_returnsNull() throws Exception {
        Method method = getFindGroupByTypeMethod();
        Object result = method.invoke(null, Collections.emptyList(), PeriodGroupType.WORKDAY);
        assertEquals(null, result);
    }

    @Test
    public void findGroupByType_matchFound_returnsGroup() throws Exception {
        TimePeriodGroupEntity workday = createGroup(PeriodGroupType.WORKDAY, true);
        TimePeriodGroupEntity regular = createGroup(PeriodGroupType.REGULAR, true);
        List<TimePeriodGroupEntity> groups = Arrays.asList(workday, regular);

        Method method = getFindGroupByTypeMethod();
        Object result = method.invoke(null, groups, PeriodGroupType.WORKDAY);
        assertNotNull(result);
        assertEquals(PeriodGroupType.WORKDAY, ((TimePeriodGroupEntity) result).groupType);
    }

    @Test
    public void findGroupByType_noMatch_returnsNull() throws Exception {
        TimePeriodGroupEntity workday = createGroup(PeriodGroupType.WORKDAY, true);
        List<TimePeriodGroupEntity> groups = Collections.singletonList(workday);

        Method method = getFindGroupByTypeMethod();
        Object result = method.invoke(null, groups, "nonexistent");
        assertEquals(null, result);
    }

    // ---- getPeriodsByGroupSync（过滤 preferChore）----

    @Test
    public void getPeriodsByGroupSync_filtersPreferChore() {
        // 插入包含 preferChore 和非 preferChore 的时段
        insertPeriod(PeriodGroupType.REGULAR, PeriodNameKey.MORNING, 0, 480, false);
        insertPeriod(PeriodGroupType.REGULAR, PeriodNameKey.AFTERNOON, 600, 720, true);  // preferChore
        insertPeriod(PeriodGroupType.REGULAR, PeriodNameKey.NOON, 480, 600, false);

        List<TimePeriodEntity> result = mViewModel.getPeriodsByGroupSync(PeriodGroupType.REGULAR);
        assertEquals("应过滤 preferChore 时段", 2, result.size());
        for (TimePeriodEntity p : result) {
            assertFalse("不应包含 preferChore 时段", p.preferChore);
        }
    }

    @Test
    public void getPeriodsByGroupSync_emptyGroup_returnsEmpty() {
        List<TimePeriodEntity> result = mViewModel.getPeriodsByGroupSync("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    public void getPeriodsByGroupSync_noneChoreGroup_allReturned() {
        insertPeriod(PeriodGroupType.REGULAR, PeriodNameKey.MORNING, 0, 480, false);
        insertPeriod(PeriodGroupType.REGULAR, PeriodNameKey.NOON, 480, 600, false);

        List<TimePeriodEntity> result = mViewModel.getPeriodsByGroupSync(PeriodGroupType.REGULAR);
        assertEquals(2, result.size());
    }

    // ---- getActivePeriodGroupForDate ----

    @Test
    public void getActivePeriodGroupForDate_usesRepo() {
        // 插入 REGULAR 组和时段
        insertGroup(PeriodGroupType.REGULAR, true);
        insertPeriod(PeriodGroupType.REGULAR, PeriodNameKey.MORNING, 0, 480, false);

        // 工作日 → WORKDAY 排在前面，但只有 REGULAR 有 enabled=true
        // 使用 STANDARD_5 + 周四 → 应该命中 WORKDAY（但因为 WORKDAY 没创建，回退到 REGULAR）
        // 实际行为：resolveActiveGroupType 会按优先级找，找不到 enabled WORKDAY 就退到 REGULAR
        // 只在 REGULAR 存在时，必然返回 REGULAR
        long dateMs = makeDateMs(2026, 3, 5); // 周四
        ActivePeriodGroup group = mViewModel.getActivePeriodGroupForDate(dateMs);
        assertNotNull(group);
        assertEquals(PeriodGroupType.REGULAR, group.getGroupType());
        assertEquals(1, group.periods.size());
    }

    // ---- getOccupiedSlots（核心改动：两步匹配）----

    @Test
    public void getOccupiedSlots_emptyCache_returnsEmpty() {
        Set<Integer> result = mViewModel.getOccupiedSlots(0, makeDateMs(2026, 3, 5));
        assertTrue("空缓存应返回空集", result.isEmpty());
    }

    @Test
    public void getOccupiedSlots_linkedPeriodGroupType_nonEmpty_groupMatches_occupies() throws Exception {
        // GIVEN: 安排关联 WORKDAY 时段组，且日期是工作日（周四）
        long dateMs = makeDateMs(2026, 3, 5); // 周四 → 工作日 → participatesInTimelineSync=true

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 30;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.taskId = 1L;
        schedule.scheduledTime = 600; // 10:00
        schedule.linkedPeriodGroupType = PeriodGroupType.WORKDAY;

        setEnabledSchedulesCache(Collections.singletonList(schedule));
        setAllPeriodGroupsCache(Collections.singletonList(
            createGroup(PeriodGroupType.WORKDAY, true)));
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);

        // 10:00 开始，focusMinutes=30，10 分钟粒度 → 600, 610, 620
        assertEquals(3, result.size());
        assertTrue(result.contains(600));
        assertTrue(result.contains(610));
        assertTrue(result.contains(620));
    }

    @Test
    public void getOccupiedSlots_linkedPeriodGroupType_groupNotFound_notOccupied() throws Exception {
        // GIVEN: 安排关联不存在的时段组
        long dateMs = makeDateMs(2026, 3, 5);

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 30;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.taskId = 1L;
        schedule.scheduledTime = 600;
        schedule.linkedPeriodGroupType = "nonexistent";

        setEnabledSchedulesCache(Collections.singletonList(schedule));
        // mAllPeriodGroupsCache 只包含 REGULAR，不包含 "nonexistent"
        setAllPeriodGroupsCache(Collections.singletonList(
            createGroup(PeriodGroupType.REGULAR, true)));
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);
        assertTrue("时段组未找到应不占用", result.isEmpty());
    }

    @Test
    public void getOccupiedSlots_linkedPeriodGroupType_weekendNotOccupied() throws Exception {
        // GIVEN: 安排关联 WORKDAY，但日期是周六 → participatesInTimelineSync=false
        long dateMs = makeDateMs(2026, 3, 7); // 周六

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 30;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.taskId = 1L;
        schedule.scheduledTime = 600;
        schedule.linkedPeriodGroupType = PeriodGroupType.WORKDAY;

        setEnabledSchedulesCache(Collections.singletonList(schedule));
        setAllPeriodGroupsCache(Collections.singletonList(
            createGroup(PeriodGroupType.WORKDAY, true)));
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);
        assertTrue("周六 WORKDAY 不生效，应不占用", result.isEmpty());
    }

    @Test
    public void getOccupiedSlots_linkedPeriodGroupType_disabledGroup_notOccupied() throws Exception {
        // GIVEN: 安排关联 WORKDAY，组存在但 disabled
        long dateMs = makeDateMs(2026, 3, 5); // 周四

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 30;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.taskId = 1L;
        schedule.scheduledTime = 600;
        schedule.linkedPeriodGroupType = PeriodGroupType.WORKDAY;

        setEnabledSchedulesCache(Collections.singletonList(schedule));
        // disabled 的 WORKDAY → participatesInTimelineSync 返回 false
        setAllPeriodGroupsCache(Collections.singletonList(
            createGroup(PeriodGroupType.WORKDAY, false)));
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);
        assertTrue("disabled 组应不占用", result.isEmpty());
    }

    @Test
    public void getOccupiedSlots_emptyLinkedPeriodGroupType_usesMatchesDate() throws Exception {
        // GIVEN: 安排不关联时段组（linkedPeriodGroupType=""），daily 类型
        long dateMs = makeDateMs(2026, 3, 7); // 周六（对 daily 无影响）

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 60;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.taskId = 1L;
        schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        schedule.scheduledTime = 60; // 01:00
        schedule.linkedPeriodGroupType = ""; // 空=历史数据/顶层单次

        setEnabledSchedulesCache(Collections.singletonList(schedule));
        // 不需要 allPeriodGroups，因为 linkedPeriodGroupType 为空走旧路径
        setAllPeriodGroupsCache(Collections.emptyList());
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);

        // TYPE_DAILY 每天匹配，60 分钟 → 60, 70, 80, 90, 100, 110
        assertEquals(6, result.size());
        assertTrue(result.contains(60));
        assertTrue(result.contains(110));
    }

    @Test
    public void getOccupiedSlots_excludeScheduleId_skipsMatchedSchedule() throws Exception {
        // GIVEN: 两个 daily 安排，排除 id=100
        long dateMs = makeDateMs(2026, 3, 5);

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 30;
        task.content = "测试";

        TaskScheduleEntity s1 = new TaskScheduleEntity();
        s1.id = 100L;
        s1.taskId = 1L;
        s1.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        s1.scheduledTime = 600;
        s1.linkedPeriodGroupType = "";

        TaskScheduleEntity s2 = new TaskScheduleEntity();
        s2.id = 101L;
        s2.taskId = 1L;
        s2.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        s2.scheduledTime = 800;
        s2.linkedPeriodGroupType = "";

        setEnabledSchedulesCache(Arrays.asList(s1, s2));
        setAllPeriodGroupsCache(Collections.emptyList());
        Map<Long, TaskEntity> tasks = new HashMap<>();
        tasks.put(1L, task);
        setTaskCache(tasks);

        // 排除 s1 → 只应包含 s2 的槽位（800, 810, 820）
        Set<Integer> result = mViewModel.getOccupiedSlots(100L, dateMs);
        assertEquals(3, result.size());
        assertTrue(result.contains(800));
        assertTrue(result.contains(810));
        assertTrue(result.contains(820));
    }

    @Test
    public void getOccupiedSlots_zeroFocusMinutes_notOccupied() throws Exception {
        // GIVEN: task.focusMinutes = 0（无执行时长）
        long dateMs = makeDateMs(2026, 3, 5);

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 0;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.taskId = 1L;
        schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        schedule.scheduledTime = 600;
        schedule.linkedPeriodGroupType = "";

        setEnabledSchedulesCache(Collections.singletonList(schedule));
        setAllPeriodGroupsCache(Collections.emptyList());
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);
        assertTrue("focusMinutes=0 不占用任何槽位", result.isEmpty());
    }

    @Test
    public void getOccupiedSlots_multipleSchedules_mixedLinkedAndNonLinked() throws Exception {
        // GIVEN: 混合场景 —— 一个关联 WORKDAY（工作日生效），一个 daily（总是生效）
        long dateMs = makeDateMs(2026, 3, 5); // 周四

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 20;
        task.content = "测试";

        // 关联 WORKDAY 的安排（工作日生效）
        TaskScheduleEntity linkedSchedule = new TaskScheduleEntity();
        linkedSchedule.id = 100L;
        linkedSchedule.taskId = 1L;
        linkedSchedule.scheduledTime = 480; // 08:00
        linkedSchedule.linkedPeriodGroupType = PeriodGroupType.WORKDAY;

        // 不关联的 daily 安排（总是生效）
        TaskScheduleEntity freeSchedule = new TaskScheduleEntity();
        freeSchedule.id = 101L;
        freeSchedule.taskId = 1L;
        freeSchedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        freeSchedule.scheduledTime = 720; // 12:00
        freeSchedule.linkedPeriodGroupType = "";

        setEnabledSchedulesCache(Arrays.asList(linkedSchedule, freeSchedule));
        setAllPeriodGroupsCache(Collections.singletonList(
            createGroup(PeriodGroupType.WORKDAY, true)));
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);

        // 工作日 → linkedSchedule 生效 (480, 490) + freeSchedule 生效 (720, 730)
        assertEquals(4, result.size());
        assertTrue(result.contains(480));
        assertTrue(result.contains(490));
        assertTrue(result.contains(720));
        assertTrue(result.contains(730));
    }

    @Test
    public void getOccupiedSlots_multipleSchedules_weekendOnlyFreeScheduleOccupies() throws Exception {
        // GIVEN: 同上混合场景，但日期是周六
        long dateMs = makeDateMs(2026, 3, 7); // 周六

        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.focusMinutes = 20;
        task.content = "测试";

        TaskScheduleEntity linkedSchedule = new TaskScheduleEntity();
        linkedSchedule.id = 100L;
        linkedSchedule.taskId = 1L;
        linkedSchedule.scheduledTime = 480;
        linkedSchedule.linkedPeriodGroupType = PeriodGroupType.WORKDAY;

        TaskScheduleEntity freeSchedule = new TaskScheduleEntity();
        freeSchedule.id = 101L;
        freeSchedule.taskId = 1L;
        freeSchedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
        freeSchedule.scheduledTime = 720;
        freeSchedule.linkedPeriodGroupType = "";

        setEnabledSchedulesCache(Arrays.asList(linkedSchedule, freeSchedule));
        setAllPeriodGroupsCache(Collections.singletonList(
            createGroup(PeriodGroupType.WORKDAY, true)));
        setTaskCache(Collections.singletonMap(1L, task));

        Set<Integer> result = mViewModel.getOccupiedSlots(0, dateMs);

        // 周六 → linkedSchedule 不生效，仅 freeSchedule 生效
        assertEquals(2, result.size());
        assertTrue(result.contains(720));
        assertTrue(result.contains(730));
    }

    // ---- InitialState 构造器（新增字段验证）----

    @Test
    public void initialState_constructor_allFieldsSet() {
        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.content = "测试";

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = 100L;
        schedule.scheduledTime = 600;

        List<TimePeriodGroupEntity> groups = Collections.singletonList(
            createGroup(PeriodGroupType.WORKDAY, true));
        PeriodGroupRuleResolver.WorkdayMode mode = PeriodGroupRuleResolver.WorkdayMode.SIX_DAY;

        TaskScheduleViewModel.InitialState state = new TaskScheduleViewModel.InitialState(
            task, schedule, groups, mode);

        assertEquals(task, state.task);
        assertEquals(schedule, state.schedule);
        assertEquals(groups, state.enabledPeriodGroups);
        assertEquals(PeriodGroupRuleResolver.WorkdayMode.SIX_DAY, state.workdayMode);
    }

    @Test
    public void initialState_nullSchedule_isAllowed() {
        TaskEntity task = new TaskEntity();
        task.id = 1L;

        TaskScheduleViewModel.InitialState state = new TaskScheduleViewModel.InitialState(
            task, null,
            Collections.emptyList(),
            PeriodGroupRuleResolver.WorkdayMode.STANDARD_5);

        assertEquals(task, state.task);
        assertEquals(null, state.schedule);
    }

    // ---- 辅助方法 ----

    /**
     * 通过反射设置 mEnabledSchedulesCache。
     */
    @SuppressWarnings("unchecked")
    private void setEnabledSchedulesCache(List<TaskScheduleEntity> schedules) throws Exception {
        Field f = TaskScheduleViewModel.class.getDeclaredField("mEnabledSchedulesCache");
        f.setAccessible(true);
        f.set(mViewModel, schedules);
    }

    /**
     * 通过反射设置 mAllPeriodGroupsCache。
     */
    @SuppressWarnings("unchecked")
    private void setAllPeriodGroupsCache(List<TimePeriodGroupEntity> groups) throws Exception {
        Field f = TaskScheduleViewModel.class.getDeclaredField("mAllPeriodGroupsCache");
        f.setAccessible(true);
        f.set(mViewModel, groups);
    }

    /**
     * 通过反射设置 mTaskCache。
     */
    @SuppressWarnings("unchecked")
    private void setTaskCache(Map<Long, TaskEntity> tasks) throws Exception {
        Field f = TaskScheduleViewModel.class.getDeclaredField("mTaskCache");
        f.setAccessible(true);
        f.set(mViewModel, tasks);
    }

    /**
     * 通过反射获取 private static findGroupByType 方法。
     */
    private Method getFindGroupByTypeMethod() throws Exception {
        Method method = TaskScheduleViewModel.class.getDeclaredMethod(
            "findGroupByType", List.class, String.class);
        method.setAccessible(true);
        return method;
    }

    private void insertGroup(String groupType, boolean enabled) {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = groupType;
        group.enabled = enabled;
        group.displayOrder = PeriodGroupType.getDisplayOrder(groupType);
        mDb.timePeriodDao().insertGroups(Collections.singletonList(group));
    }

    private void insertPeriod(String groupType, String nameKey, int startMinute,
                              int endMinute, boolean preferChore) {
        TimePeriodEntity period = new TimePeriodEntity();
        period.groupType = groupType;
        period.nameKey = nameKey;
        period.startMinute = startMinute;
        period.endMinute = endMinute;
        period.preferChore = preferChore;
        mDb.timePeriodDao().insertPeriods(Collections.singletonList(period));
    }

    private static TimePeriodGroupEntity createGroup(String groupType, boolean enabled) {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = groupType;
        group.enabled = enabled;
        group.displayOrder = PeriodGroupType.getDisplayOrder(groupType);
        return group;
    }

    private static long makeDateMs(int year, int month, int day) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        cal.set(year, month - 1, day, 12, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ---- TestApplication ----

    private static class TestApplication extends JustNowApplication {
        private final AppDatabase mTestDb;
        private final Context mContext;
        private TaskRepository mTaskRepo;
        private TaskScheduleRepository mScheduleRepo;
        private TimePeriodRepository mTimePeriodRepo;
        private PeriodGroupRuleResolver mRuleResolver;

        TestApplication(AppDatabase db, Context context) {
            mTestDb = db;
            mContext = context;
        }

        @Override
        public void onCreate() {
            // 不调 super，跳过真实 App 初始化
        }

        @Override
        public AppDatabase getDatabase() {
            return mTestDb;
        }

        @Override
        public TaskRepository getTaskRepository() {
            if (mTaskRepo == null) {
                mTaskRepo = new TaskRepository(mTestDb);
            }
            return mTaskRepo;
        }

        @Override
        public TaskScheduleRepository getTaskScheduleRepository() {
            if (mScheduleRepo == null) {
                mScheduleRepo = new TaskScheduleRepository(mTestDb);
            }
            return mScheduleRepo;
        }

        @Override
        public TimePeriodRepository getTimePeriodRepository() {
            if (mTimePeriodRepo == null) {
                mTimePeriodRepo = new TimePeriodRepository(mTestDb, getPeriodGroupRuleResolver());
            }
            return mTimePeriodRepo;
        }

        @Override
        public PeriodGroupRuleResolver getPeriodGroupRuleResolver() {
            if (mRuleResolver == null) {
                // STANDARD_WEEK 策略，STANDARD_5 模式 —— 只判断 Mon-Fri
                mRuleResolver = new PeriodGroupRuleResolver(mContext,
                    new HolidayCacheManager(mTestDb.holidayCacheDao()));
            }
            return mRuleResolver;
        }
    }
}
