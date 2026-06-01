package com.nearby.justnow.ui.base;

import android.content.Context;
import android.database.Cursor;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.store.ChoreHiddenTodayStore;

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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * BaseTaskViewModel 4 个 Sync 方法的单元测试（F1 改动）：
 * - {@link BaseTaskViewModel#completeRunningTaskSync(TaskEntity, long)}
 * - {@link BaseTaskViewModel#performShortCompletionSync(long, boolean, boolean, TaskScheduleEntity)}
 * - {@link BaseTaskViewModel#archiveTaskSync(long, TaskScheduleEntity)}
 * - {@link BaseTaskViewModel#checkListStateNeedsConfirm(TaskEntity, long)}
 *
 * <p>用 in-memory Room 验证副作用，通过反射调用 protected 方法。
 * 内部触发的 ReminderScheduler / ReminderNotifier 在 Robolectric 下走 Shadow，无副作用。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = BaseTaskViewModelSyncTest.TestApp.class, sdk = 35)
public class BaseTaskViewModelSyncTest {

    private AppDatabase mDb;
    private TestApp mApp;
    private TestViewModel mViewModel;
    private ChoreHiddenTodayStore mChoreHiddenStore;

    @Before
    public void setUp() throws Exception {
        mApp = (TestApp) RuntimeEnvironment.getApplication();
        mDb = AppDatabase.createInMemory(mApp);
        mApp.attachDatabase(mDb);
        setStaticInstance(mDb);

        mChoreHiddenStore = new ChoreHiddenTodayStore(mApp);
        clearChoreHiddenStore(mApp);

        mViewModel = new TestViewModel(mApp);
    }

    @After
    public void tearDown() throws Exception {
        setStaticInstance(null);
        if (mDb != null && mDb.isOpen()) mDb.close();
        clearChoreHiddenStore(mApp);
    }

    // ============================================================
    // completeRunningTaskSync
    // ============================================================

    @Test
    public void completeRunningTaskSync_writesExecutionAndClearsRunningState() throws Exception {
        long start = System.currentTimeMillis() - 30 * 60_000L;
        long end = start + 25 * 60_000L;
        long taskId = insertTask(60, start);

        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        invokeCompleteRunningTaskSync(task, end);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals("executingStartMs 清零", 0L, updated.executingStartMs);
        assertEquals("executingEndMs 清零", 0L, updated.executingEndMs);

        // task_executions 应写入 1 条
        assertEquals(1, countTaskExecutions());
        Cursor c = mDb.query(
            "SELECT start_ms, end_ms, actual_minutes, status FROM task_executions WHERE task_id = ?",
            new Object[]{taskId});
        try {
            assertTrue(c.moveToFirst());
            assertEquals(start, c.getLong(0));
            assertEquals(end, c.getLong(1));
            assertEquals(25, c.getInt(2));
            assertEquals(0, c.getInt(3)); // 已完成
        } finally {
            c.close();
        }
    }

    @Test
    public void completeRunningTaskSync_endBeforeStart_clampsToStartPlus1ms() throws Exception {
        long start = System.currentTimeMillis();
        long end = start - 5_000L; // 反常：end < start
        long taskId = insertTask(60, start);

        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        invokeCompleteRunningTaskSync(task, end);

        Cursor c = mDb.query(
            "SELECT start_ms, end_ms, actual_minutes FROM task_executions WHERE task_id = ?",
            new Object[]{taskId});
        try {
            assertTrue(c.moveToFirst());
            assertEquals(start, c.getLong(0));
            assertEquals("end_ms 应被钳到 start+1", start + 1L, c.getLong(1));
            assertEquals("不足 1 分钟应记 1 分钟", 1, c.getInt(2));
        } finally {
            c.close();
        }
    }

    @Test
    public void completeRunningTaskSync_nullTask_isNoOp() throws Exception {
        invokeCompleteRunningTaskSync(null, System.currentTimeMillis());
        assertEquals(0, countTaskExecutions());
    }

    @Test
    public void completeRunningTaskSync_notRunning_isNoOp() throws Exception {
        long taskId = insertTask(60, 0L);

        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        invokeCompleteRunningTaskSync(task, System.currentTimeMillis());

        assertEquals(0, countTaskExecutions());
    }

    // ============================================================
    // performShortCompletionSync
    // ============================================================

    @Test
    public void performShortCompletion_directComplete_clearsExecutionHidesAndKeepsFocus() throws Exception {
        long taskId = insertTask(60, System.currentTimeMillis() - 60_000L);

        invokePerformShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ false, null);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals("focusMinutes 不变", 60, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        assertEquals(0L, updated.executingEndMs);
        assertEquals("短完成不写 task_executions", 0, countTaskExecutions());

        Set<Long> hidden = mChoreHiddenStore.getHiddenTodayIds();
        assertTrue("应当被加入今日隐藏集合", hidden.contains(taskId));
    }

    @Test
    public void performShortCompletion_convertToChore_setsFocusZero() throws Exception {
        long taskId = insertTask(60, System.currentTimeMillis() - 60_000L);

        invokePerformShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ true, null);

        TaskEntity updated = mDb.taskDao().getTaskByIdSync(taskId);
        assertEquals("convertToChore 应清零 focusMinutes", 0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        assertTrue(mChoreHiddenStore.getHiddenTodayIds().contains(taskId));
    }

    @Test
    public void performShortCompletion_stopScheduleWithSchedule_disablesSchedule() throws Exception {
        long taskId = insertTask(60, System.currentTimeMillis() - 60_000L);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);
        TaskScheduleEntity schedule = readScheduleById(scheduleId);

        invokePerformShortCompletion(taskId, /*stopSchedule*/ true, /*convertToChore*/ false, schedule);

        TaskScheduleEntity after = readScheduleById(scheduleId);
        assertFalse("stopSchedule=true 应 disable 安排", after.enabled);
        assertEquals(TaskScheduleEntity.REASON_USER_STOPPED, after.disableReason);
    }

    @Test
    public void performShortCompletion_keepSchedule_keepsScheduleEnabled() throws Exception {
        long taskId = insertTask(60, System.currentTimeMillis() - 60_000L);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);
        TaskScheduleEntity schedule = readScheduleById(scheduleId);

        invokePerformShortCompletion(taskId, /*stopSchedule*/ false, /*convertToChore*/ false, schedule);

        TaskScheduleEntity after = readScheduleById(scheduleId);
        assertTrue("stopSchedule=false 应保留 enabled", after.enabled);
    }

    @Test
    public void performShortCompletion_unknownTaskId_isNoOp() throws Exception {
        long missing = 99999L;
        invokePerformShortCompletion(missing, true, true, null);
        assertNull(mDb.taskDao().getTaskByIdSync(missing));
        assertFalse(mChoreHiddenStore.getHiddenTodayIds().contains(missing));
    }

    // ============================================================
    // archiveTaskSync
    // ============================================================

    @Test
    public void archiveTaskSync_marksTaskArchivedAndDisablesSchedule() throws Exception {
        long taskId = insertTask(60, System.currentTimeMillis() - 60_000L);
        long scheduleId = insertSchedule(taskId, TaskScheduleEntity.TYPE_DAILY);
        TaskScheduleEntity schedule = readScheduleById(scheduleId);

        invokeArchiveTaskSync(taskId, schedule);

        // 注意：getTaskByIdSync 默认只查未归档（看 DAO 实现）；用原生查询直接读 is_archived
        Cursor c = mDb.query(
            "SELECT is_archived, executing_start_ms, executing_end_ms FROM tasks WHERE id = ?",
            new Object[]{taskId});
        try {
            assertTrue(c.moveToFirst());
            assertEquals("应标记为已归档", 1, c.getInt(0));
            assertEquals(0L, c.getLong(1));
            assertEquals(0L, c.getLong(2));
        } finally {
            c.close();
        }

        TaskScheduleEntity after = readScheduleById(scheduleId);
        assertFalse("归档应 disable 安排", after.enabled);
    }

    @Test
    public void archiveTaskSync_nullSchedule_stillArchivesTask() throws Exception {
        long taskId = insertTask(0, 0L);

        invokeArchiveTaskSync(taskId, null);

        Cursor c = mDb.query("SELECT is_archived FROM tasks WHERE id = ?",
            new Object[]{taskId});
        try {
            assertTrue(c.moveToFirst());
            assertEquals(1, c.getInt(0));
        } finally {
            c.close();
        }
    }

    // ============================================================
    // checkListStateNeedsConfirm
    // ============================================================

    @Test
    public void checkListStateNeedsConfirm_nullTask_false() throws Exception {
        assertFalse(invokeCheckListStateNeedsConfirm(null, 0L));
    }

    @Test
    public void checkListStateNeedsConfirm_nonChecklistType_false() throws Exception {
        long taskId = insertTask(0, 0L);
        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        task.detailModuleType = "app_actions";
        mDb.taskDao().update(task);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(taskId);

        assertFalse(invokeCheckListStateNeedsConfirm(reloaded, taskId));
    }

    @Test
    public void checkListStateNeedsConfirm_checklistWithChecked_true() throws Exception {
        long taskId = insertTask(0, 0L);
        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        task.detailModuleType = "checklist";
        mDb.taskDao().update(task);
        insertChecklistItem(taskId, /*checked*/ true, /*crossedOut*/ false);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(taskId);

        assertTrue(invokeCheckListStateNeedsConfirm(reloaded, taskId));
    }

    @Test
    public void checkListStateNeedsConfirm_checklistWithCrossedOut_true() throws Exception {
        long taskId = insertTask(0, 0L);
        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        task.detailModuleType = "checklist";
        mDb.taskDao().update(task);
        insertChecklistItem(taskId, false, true);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(taskId);

        assertTrue(invokeCheckListStateNeedsConfirm(reloaded, taskId));
    }

    @Test
    public void checkListStateNeedsConfirm_checklistAllClean_false() throws Exception {
        long taskId = insertTask(0, 0L);
        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        task.detailModuleType = "checklist";
        mDb.taskDao().update(task);
        insertChecklistItem(taskId, false, false);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(taskId);

        assertFalse(invokeCheckListStateNeedsConfirm(reloaded, taskId));
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private long insertTask(int focusMinutes, long executingStartMs) {
        TaskEntity task = new TaskEntity();
        task.content = "测试任务";
        task.quadrant = 0;
        task.focusMinutes = focusMinutes;
        task.createdAt = System.currentTimeMillis();
        task.executingStartMs = executingStartMs;
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

    private void insertChecklistItem(long taskId, boolean checked, boolean crossedOut) {
        TaskChecklistItem item = new TaskChecklistItem();
        item.taskId = taskId;
        item.orderIndex = 0;
        item.content = "项";
        item.checked = checked;
        item.crossedOut = crossedOut;
        mDb.taskChecklistItemDao().insertAll(Arrays.asList(item));
    }

    private TaskScheduleEntity readScheduleById(long scheduleId) {
        Cursor cursor = mDb.query(
            "SELECT * FROM task_schedules WHERE id = ?",
            new Object[]{scheduleId});
        try {
            assertTrue(cursor.moveToFirst());
            TaskScheduleEntity entity = new TaskScheduleEntity();
            entity.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
            entity.taskId = cursor.getLong(cursor.getColumnIndexOrThrow("task_id"));
            entity.scheduleType = cursor.getInt(cursor.getColumnIndexOrThrow("schedule_type"));
            entity.scheduleValue = cursor.getLong(cursor.getColumnIndexOrThrow("schedule_value"));
            entity.scheduledTime = cursor.getInt(cursor.getColumnIndexOrThrow("scheduled_time"));
            entity.enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0;
            int reasonIdx = cursor.getColumnIndex("disable_reason");
            if (reasonIdx >= 0 && !cursor.isNull(reasonIdx)) {
                entity.disableReason = cursor.getString(reasonIdx);
            }
            entity.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
            entity.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
            return entity;
        } finally {
            cursor.close();
        }
    }

    private int countTaskExecutions() {
        Cursor c = mDb.query("SELECT COUNT(*) FROM task_executions", null);
        try {
            assertTrue(c.moveToFirst());
            return c.getInt(0);
        } finally {
            c.close();
        }
    }

    // ---- 反射调用 BaseTaskViewModel 的 protected 方法 ----

    private void invokeCompleteRunningTaskSync(TaskEntity task, long endMs) throws Exception {
        Method m = BaseTaskViewModel.class.getDeclaredMethod(
            "completeRunningTaskSync", TaskEntity.class, long.class);
        m.setAccessible(true);
        m.invoke(mViewModel, task, endMs);
    }

    private void invokePerformShortCompletion(long taskId, boolean stopSchedule,
                                              boolean convertToChore, TaskScheduleEntity schedule) throws Exception {
        Method m = BaseTaskViewModel.class.getDeclaredMethod(
            "performShortCompletionSync", long.class, boolean.class, boolean.class,
            TaskScheduleEntity.class);
        m.setAccessible(true);
        m.invoke(mViewModel, taskId, stopSchedule, convertToChore, schedule);
    }

    private void invokeArchiveTaskSync(long taskId, TaskScheduleEntity schedule) throws Exception {
        Method m = BaseTaskViewModel.class.getDeclaredMethod(
            "archiveTaskSync", long.class, TaskScheduleEntity.class);
        m.setAccessible(true);
        m.invoke(mViewModel, taskId, schedule);
    }

    private boolean invokeCheckListStateNeedsConfirm(TaskEntity task, long taskId) throws Exception {
        Method m = BaseTaskViewModel.class.getDeclaredMethod(
            "checkListStateNeedsConfirm", TaskEntity.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(mViewModel, task, taskId);
    }

    private static void setStaticInstance(AppDatabase db) throws Exception {
        Field f = AppDatabase.class.getDeclaredField("sInstance");
        f.setAccessible(true);
        f.set(null, db);
    }

    private static void clearChoreHiddenStore(Context ctx) {
        ctx.getSharedPreferences("justnow_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("hide_focus_today_date")
            .remove("hide_focus_today_ids")
            .commit();
    }

    // ============================================================
    // 测试桩
    // ============================================================

    /** 用具体子类把 BaseTaskViewModel 实例化（BaseTaskViewModel 是 abstract）。 */
    public static class TestViewModel extends BaseTaskViewModel {
        public TestViewModel(JustNowApplication app) {
            super(app);
        }
    }

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
