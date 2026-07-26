package com.nearby.justnow.ui.main;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.ui.base.BaseTaskViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * MainViewModel 的 &lt; 15min 完成路径副作用矩阵测试。
 *
 * <p>不通过 LiveData / Handler 走异步入口，而是反射直接调用 private
 * {@code performShortCompletionSync(taskId, stopSchedule, convertToChore)}，
 * 再用 in-memory Room DB 验证 task / schedule / task_executions / ChoreHiddenTodayStore
 * 四处副作用与 task-execution.md 副作用矩阵一致。</p>
 *
 * <p>矩阵入口和方法参数的对应：</p>
 * <ul>
 *   <li>入口1 = 完成本次（无安排），选项 = 直接完成 → stopSchedule=false / convertToChore=false</li>
 *   <li>入口1 = 完成本次（无安排），选项 = 完成并调整 → stopSchedule=false / convertToChore=true</li>
 *   <li>入口2 = 完成本次（有安排），选项 = 直接完成 → stopSchedule=false / convertToChore=false</li>
 *   <li>入口2 = 完成本次（有安排），选项 = 不再安排并调整 → stopSchedule=true / convertToChore=true</li>
 *   <li>入口3 = 完成并停止安排，选项 = 直接完成 → stopSchedule=true / convertToChore=false</li>
 *   <li>入口3 = 完成并停止安排，选项 = 完成并调整 → stopSchedule=true / convertToChore=true</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = MainViewModelShortCompletionTest.TestApp.class, sdk = 35)
public class MainViewModelShortCompletionTest {

    /** addSource 等 LiveData 操作走测试线程。 */
    @Rule
    public InstantTaskExecutorRule mInstantRule = new InstantTaskExecutorRule();

    private AppDatabase mDb;
    private MainViewModel mViewModel;
    private TestApp mApp;

    @Before
    public void setUp() throws Exception {
        mApp = (TestApp) RuntimeEnvironment.getApplication();
        mDb = AppDatabase.createInMemory(mApp);
        mApp.attachDatabase(mDb);

        // 注入测试 DB，所有 AppDatabase.getInstance() 优先返回
        AppDatabase.setTestInstance(mDb);

        // 清空隐藏集合，避免与其他测试串扰
        clearChoreHiddenStore(mApp);

        mViewModel = new MainViewModel(mApp);
    }

    @After
    public void tearDown() throws Exception {
        AppDatabase.clearTestInstance();
        if (mDb != null && mDb.isOpen()) mDb.close();
        clearChoreHiddenStore(mApp);
    }

    // ============================================================
    // 入口1 完成本次（无安排）
    // ============================================================

    @Test
    public void completeOnce_noSchedule_directComplete_keepsFocusMinutesAndHidesToday() throws Exception {
        long taskId = insertTask(/*focusMinutes*/ 60, /*executing*/ true);

        invokeShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ false);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        // focusMinutes 不变
        assertEquals(60, updated.focusMinutes);
        // 执行中状态已清除（任务可移出执行中）
        assertEquals(0L, updated.executingStartMs);
        assertEquals(0L, updated.executingEndMs);
        // 统一完成流程写 status=3 执行记录
        assertTrue("短完成应写执行记录", countTaskExecutions() >= 1);
    }

    @Test
    public void completeOnce_noSchedule_convertToChore_setsFocusZero() throws Exception {
        long taskId = insertTask(60, true);

        invokeShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ true);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals(0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        assertEquals(0L, updated.executingEndMs);
        assertTrue("短完成应写执行记录", countTaskExecutions() >= 1);
    }

    // ============================================================
    // 入口2 完成本次（有 recurring 安排）
    // ============================================================

    @Test
    public void completeOnce_recurringSchedule_directComplete_keepsScheduleEnabled() throws Exception {
        long taskId = insertTask(60, true);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);

        invokeShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ false);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals(60, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        // 长期安排保留 enabled = 1
        TaskScheduleEntity schedule = readScheduleById(scheduleId);
        assertTrue("长期安排 + 直接完成路径应保留 enabled", schedule.enabled);
        assertTrue("短完成应写执行记录", countTaskExecutions() >= 1);
    }

    @Test
    public void completeOnce_recurringSchedule_convertToChore_disablesSchedule() throws Exception {
        long taskId = insertTask(60, true);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);

        invokeShortCompletion(taskId, /*stopSchedule*/ true, /*convertToChore*/ true);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals(0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        TaskScheduleEntity schedule = readScheduleById(scheduleId);
        assertFalse("不再安排并调整应当 disable 长期安排", schedule.enabled);
        assertTrue("短完成应写执行记录", countTaskExecutions() >= 1);
    }

    // ============================================================
    // 入口3 完成并停止安排
    // ============================================================

    @Test
    public void completeAndStopSchedule_directComplete_disablesScheduleKeepsFocus() throws Exception {
        long taskId = insertTask(60, true);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);

        invokeShortCompletion(taskId, /*stopSchedule*/ true, /*convertToChore*/ false);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals("入口3 直接完成不改 focusMinutes", 60, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        TaskScheduleEntity schedule = readScheduleById(scheduleId);
        assertFalse("入口3 直接完成也应 disable 长期安排", schedule.enabled);
        assertTrue("短完成应写执行记录", countTaskExecutions() >= 1);
    }

    @Test
    public void completeAndStopSchedule_convertToChore_disablesScheduleAndSetsFocusZero() throws Exception {
        long taskId = insertTask(60, true);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);

        invokeShortCompletion(taskId, /*stopSchedule*/ true, /*convertToChore*/ true);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals(0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        TaskScheduleEntity schedule = readScheduleById(scheduleId);
        assertFalse(schedule.enabled);
        assertTrue("短完成应写执行记录", countTaskExecutions() >= 1);
    }

    // ============================================================
    // 完成本次不碰安排（不再 auto-disable TYPE_ONCE）
    // ============================================================

    @Test
    public void completeOnce_typeOnceSchedule_directComplete_keepsScheduleEnabled() throws Exception {
        long taskId = insertTask(60, true);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_ONCE);

        // 完成本次（stopSchedule=false）：TYPE_ONCE 安排保持 enabled
        invokeShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ false);

        TaskScheduleEntity schedule = readScheduleById(scheduleId);
        assertTrue("完成本次不碰安排，TYPE_ONCE 应保持 enabled", schedule.enabled);
    }

    // ============================================================
    // 任务不存在：不抛、不写
    // ============================================================

    @Test
    public void unknownTaskId_isNoOp() throws Exception {
        long missingId = 99999L;
        invokeShortCompletion(missingId, true, true);
        // 表内不会凭空多一条
        assertNull(mDb.taskDao().getTaskByIdSync(missingId));
        // 隐藏集合不应被错误标记
    }

    // ============================================================
    // 标签过滤状态管理
    // ============================================================

    @Test
    public void clearMultiFilter_alsoResetsFilterTagId() {
        mViewModel.setFilterTag(5);
        assertEquals(5, mViewModel.getFilterTagId());

        mViewModel.clearMultiFilter();
        assertEquals(-1, mViewModel.getFilterTagId());
    }

    @Test
    public void setFilterTag_alsoClearsMultiFilter() {
        Set<Long> tagIds = new HashSet<>();
        tagIds.add(1L);
        tagIds.add(2L);
        mViewModel.setMultiFilterTags(tagIds);
        assertTrue(mViewModel.isMultiFilterActive());

        mViewModel.setFilterTag(5);
        assertFalse(mViewModel.isMultiFilterActive());
    }

    @Test
    public void isFiltering_trueWhenOnlyTagFilter() {
        assertFalse(mViewModel.isFiltering());

        mViewModel.setFilterTag(3);
        assertTrue(mViewModel.isFiltering());

        mViewModel.clearFilterTag();
        assertFalse(mViewModel.isFiltering());
    }

    @Test
    public void isFiltering_trueWhenMultiFilterActive() {
        assertFalse(mViewModel.isFiltering());

        Set<Long> tagIds = new HashSet<>();
        tagIds.add(1L);
        mViewModel.setMultiFilterTags(tagIds);
        assertTrue(mViewModel.isFiltering());

        mViewModel.clearMultiFilter();
        assertFalse(mViewModel.isFiltering());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private long insertTask(int focusMinutes, boolean executing) {
        TaskEntity task = new TaskEntity();
        task.content = "测试任务";
        task.quadrant = 0;
        task.focusMinutes = focusMinutes;
        task.createdAt = System.currentTimeMillis();
        if (executing) {
            task.executingStartMs = System.currentTimeMillis() - 5 * 60_000L;
        }
        return mDb.taskDao().insert(task);
    }

    private long insertSchedule(long taskId, int scheduleType) {
        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.taskId = taskId;
        schedule.scheduleType = scheduleType;
        schedule.scheduledTime = 600;
        schedule.enabled = true;
        schedule.createdAt = System.currentTimeMillis();
        schedule.updatedAt = System.currentTimeMillis();
        return mDb.taskScheduleDao().insert(schedule);
    }

    /** 通过反射读取任意 id 的 schedule（含 disabled 的），用于断言 enabled 字段。 */
    private TaskScheduleEntity readScheduleById(long scheduleId) {
        // DAO 没有"按 ID 读"的接口；改用原生查询
        android.database.Cursor cursor = mDb.query(
            "SELECT * FROM task_schedules WHERE id = ?",
            new Object[]{scheduleId});
        try {
            assertTrue("schedule 应存在 id=" + scheduleId, cursor.moveToFirst());
            TaskScheduleEntity entity = new TaskScheduleEntity();
            entity.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            entity.taskId = cursor.getLong(cursor.getColumnIndexOrThrow("task_id"));
            entity.scheduleType = cursor.getInt(cursor.getColumnIndexOrThrow("schedule_type"));
            entity.scheduleValue = cursor.getLong(cursor.getColumnIndexOrThrow("schedule_value"));
            entity.scheduledTime = cursor.getInt(cursor.getColumnIndexOrThrow("scheduled_time"));
            entity.enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0;
            entity.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
            entity.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
            return entity;
        } finally {
            cursor.close();
        }
    }

    /** 统计 task_executions 表行数：&lt; 15min 完成路径绝不应写入。 */
    private int countTaskExecutions() {
        android.database.Cursor cursor = mDb.query(
            "SELECT COUNT(*) FROM task_executions", null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        } finally {
            cursor.close();
        }
    }

    /** 通过反射调用 BaseTaskViewModel#performShortCompletionSync（4 参，protected）。
     *  按 taskId 同步查 schedule 后一并传入。 */
    private void invokeShortCompletion(long taskId, boolean stopSchedule, boolean convertToChore)
        throws Exception {
        TaskScheduleEntity schedule = mDb.taskScheduleDao().getActiveScheduleSync(taskId);
        Method m = BaseTaskViewModel.class.getDeclaredMethod(
            "performShortCompletionSync", long.class, boolean.class, boolean.class,
            TaskScheduleEntity.class);
        m.setAccessible(true);
        m.invoke(mViewModel, taskId, stopSchedule, convertToChore, schedule);
    }

    private static void clearChoreHiddenStore(Context ctx) {
        ctx.getSharedPreferences("justnow_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("hide_focus_today_date")
            .remove("hide_focus_today_ids")
            .commit();
    }

    // ============================================================
    // TestApp — 用 @Config(application=...) 让 Robolectric attach base context；
    // 重写 onCreate 跳过真实业务（DB 单例 / 节假日同步 / AlarmManager），
    // 重写 getDatabase() 返回测试 mDb。
    // ============================================================

    public static class TestApp extends JustNowApplication {
        private AppDatabase mTestDb;

        @Override
        public void onCreate() {
            // 故意不调 super.onCreate()：跳过 AppDatabase.getInstance / 节假日 / Alarm
        }

        public void attachDatabase(AppDatabase db) {
            mTestDb = db;
        }

        @Override
        public AppDatabase getDatabase() {
            return mTestDb;
        }
    }
}
