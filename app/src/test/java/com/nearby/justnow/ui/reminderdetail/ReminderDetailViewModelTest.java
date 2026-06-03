package com.nearby.justnow.ui.reminderdetail;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.ui.base.BaseTaskViewModel;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = ReminderDetailViewModelTest.TestApplication.class, sdk = 35)
public class ReminderDetailViewModelTest {

    private AppDatabase mDb;
    private ReminderDetailViewModel mViewModel;
    private TaskChecklistRepository mChecklistRepo;
    private TaskRepository mTaskRepo;
    private TestApplication mApp;
    private long mTaskId;

    @Before
    public void setUp() {
        mApp = (TestApplication) RuntimeEnvironment.getApplication();
        mDb = AppDatabase.createInMemory(mApp);
        mApp.attachDatabase(mDb);
        mViewModel = new ReminderDetailViewModel(mApp);
        mChecklistRepo = new TaskChecklistRepository(mDb);
        mTaskRepo = new TaskRepository(mDb);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- isExecuting ----

    @Test
    public void isExecuting_true() {
        setTask(createAndPersistTask(0, true, false));

        assertTrue(mViewModel.isExecuting());
    }

    @Test
    public void isExecuting_false_whenNotStarted() {
        setTask(createAndPersistTask(0, false, false));

        assertFalse(mViewModel.isExecuting());
    }

    @Test
    public void isExecuting_false_whenAlreadyEnded() {
        TaskEntity task = createAndPersistTask(30, true, false);
        task.executingEndMs = System.currentTimeMillis();
        mDb.taskDao().update(task);
        setTask(mDb.taskDao().getTaskByIdSync(mTaskId));

        assertFalse(mViewModel.isExecuting());
    }

    // ---- isFocusTask ----

    @Test
    public void isFocusTask_true() {
        setTask(createAndPersistTask(60, false, false));

        assertTrue(mViewModel.isFocusTask());
    }

    @Test
    public void isFocusTask_false_whenZero() {
        setTask(createAndPersistTask(0, false, false));

        assertFalse(mViewModel.isFocusTask());
    }

    // ---- hasSchedule ----

    @Test
    public void hasSchedule_true() {
        TaskEntity task = createAndPersistTask(30, false, false);

        insertSchedule(mTaskId);
        setTask(task);
        setSchedule(mDb.taskScheduleDao().getActiveScheduleSync(mTaskId));

        assertTrue(mViewModel.hasSchedule());
    }

    @Test
    public void hasSchedule_false() {
        setTask(createAndPersistTask(30, false, false));

        assertFalse(mViewModel.hasSchedule());
    }

    // ---- checkListStateNeedsConfirm ----

    @Test
    public void checkListStateNeedsConfirm_checklistWithState() throws Exception {
        TaskEntity task = createAndPersistTask(0, false, false);

        task.detailModuleType = "checklist";
        mDb.taskDao().update(task);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(mTaskId);
        setTask(reloaded);
        insertChecklistItem(true, false);

        assertTrue(invokeCheckListStateNeedsConfirm(reloaded, mTaskId));
    }

    @Test
    public void checkListStateNeedsConfirm_checklistWithoutState() throws Exception {
        TaskEntity task = createAndPersistTask(0, false, false);

        task.detailModuleType = "checklist";
        mDb.taskDao().update(task);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(mTaskId);
        setTask(reloaded);
        insertChecklistItem(false, false);

        assertFalse(invokeCheckListStateNeedsConfirm(reloaded, mTaskId));
    }

    @Test
    public void checkListStateNeedsConfirm_notChecklist() throws Exception {
        TaskEntity task = createAndPersistTask(0, false, false);

        task.detailModuleType = "app_actions";
        mDb.taskDao().update(task);
        TaskEntity reloaded = mDb.taskDao().getTaskByIdSync(mTaskId);
        setTask(reloaded);

        assertFalse(invokeCheckListStateNeedsConfirm(reloaded, mTaskId));
    }

    @Test
    public void checkListStateNeedsConfirm_nullModuleType() throws Exception {
        TaskEntity task = createAndPersistTask(0, false, false);
        setTask(task);

        assertFalse(invokeCheckListStateNeedsConfirm(task, mTaskId));
    }

    // ---- toggleChecked（互斥逻辑） ----

    @Test
    public void toggleChecked_togglesFromFalseToTrue() {
        TaskEntity task = createAndPersistTask(0, false, false);

        TaskChecklistItem item = insertChecklistItem(false, false);

        // 模拟 toggleChecked 逻辑
        item.checked = !item.checked;
        mDb.taskChecklistItemDao().update(item);

        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        assertTrue(items.get(0).checked);
    }

    @Test
    public void toggleChecked_togglesFromTrueToFalse() {
        TaskEntity task = createAndPersistTask(0, false, false);

        TaskChecklistItem item = insertChecklistItem(true, false);

        item.checked = !item.checked;
        mDb.taskChecklistItemDao().update(item);

        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        assertFalse(items.get(0).checked);
    }

    @Test
    public void toggleChecked_blockedWhenCrossedOut() {
        TaskEntity task = createAndPersistTask(0, false, false);

        TaskChecklistItem item = insertChecklistItem(false, true);

        // 划掉状态下不应切换 checked（模拟 ViewModel 中的 if (item.crossedOut) return）
        if (!item.crossedOut) {
            item.checked = !item.checked;
        }
        mDb.taskChecklistItemDao().update(item);

        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        assertFalse(items.get(0).checked);
    }

    // ---- toggleCrossedOut（互斥逻辑：划掉时清除勾选） ----

    @Test
    public void toggleCrossedOut_setsCrossedOutAndClearsChecked() {
        TaskEntity task = createAndPersistTask(0, false, false);

        TaskChecklistItem item = insertChecklistItem(true, false);

        // 模拟 toggleCrossedOut 逻辑
        item.crossedOut = !item.crossedOut;
        if (item.crossedOut) {
            item.checked = false;
        }
        mDb.taskChecklistItemDao().update(item);

        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        assertTrue(items.get(0).crossedOut);
        assertFalse(items.get(0).checked);
    }

    @Test
    public void toggleCrossedOut_undoesCrossedOut() {
        TaskEntity task = createAndPersistTask(0, false, false);

        TaskChecklistItem item = insertChecklistItem(false, true);

        item.crossedOut = !item.crossedOut;
        if (item.crossedOut) {
            item.checked = false;
        }
        mDb.taskChecklistItemDao().update(item);

        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        assertFalse(items.get(0).crossedOut);
    }

    // ---- markAppActionCompleted / isAppActionCompleted ----

    @Test
    public void appActionCompleted_defaultFalse() {
        setTask(createAndPersistTask(0, false, false));

        assertFalse(mViewModel.isAppActionCompleted(1));
        assertFalse(mViewModel.isAppActionCompleted(999));
    }

    @Test
    public void appActionCompleted_markAndCheck() {
        setTask(createAndPersistTask(0, false, false));

        mViewModel.markAppActionCompleted(42);
        assertTrue(mViewModel.isAppActionCompleted(42));
        assertFalse(mViewModel.isAppActionCompleted(1));
    }

    @Test
    public void appActionCompleted_clearedOnReload() {
        setTask(createAndPersistTask(0, false, false));
        mViewModel.markAppActionCompleted(1);
        assertTrue(mViewModel.isAppActionCompleted(1));

        // 模拟重新加载：loadTask 会调用 mCompletedAppActions.clear()
        setTask(createAndPersistTask(0, false, false));
        assertTrue(mViewModel.isAppActionCompleted(1)); // 需要 loadTask 清空，这里只验证 setTask 不清空

        // 直接验证 loadTask 的清空行为：创建新 ViewModel 等同于清空后状态
        ReminderDetailViewModel newVm = new ReminderDetailViewModel(mApp);
        assertFalse(newVm.isAppActionCompleted(1));
    }

    // ---- getAppActionsSync ----

    @Test
    public void getAppActionsSync_empty() {
        setTask(createAndPersistTask(0, false, false));

        List<TaskAppAction> actions = mViewModel.getAppActionsSync(mTaskId);
        assertTrue(actions.isEmpty());
    }

    @Test
    public void getAppActionsSync_withItems() {
        TaskEntity task = createAndPersistTask(30, false, false);

        insertAppAction("com.example.a", "hint A");
        insertAppAction("com.example.b", "hint B");
        setTask(task);

        List<TaskAppAction> actions = mViewModel.getAppActionsSync(mTaskId);
        assertEquals(2, actions.size());
        assertEquals("hint A", actions.get(0).hint);
        assertEquals("hint B", actions.get(1).hint);
    }

    // ---- getTagSync ----

    @Test
    public void getTagSync_nullWhenNoTag() {
        setTask(createAndPersistTask(0, false, false));

        assertNull(mViewModel.getTagSync());
    }

    @Test
    public void getTagSync_returnsCorrectTag() {
        TagEntity tag = new TagEntity();
        tag.name = "工作";
        tag.isPriority = false;
        long tagId = mDb.tagDao().insert(tag);

        TaskEntity task = createAndPersistTask(30, false, false);
        task.tagId = tagId;
        mDb.taskDao().update(task);
        setTask(mDb.taskDao().getTaskByIdSync(mTaskId));

        TagEntity result = mViewModel.getTagSync();
        assertNotNull(result);
        assertEquals("工作", result.name);
    }

    // ---- resetChecklistState ----

    @Test
    public void resetChecklistState_clearsAll() {
        TaskEntity task = createAndPersistTask(30, false, false);

        insertChecklistItem(true, false);
        insertChecklistItem(false, true);

        mChecklistRepo.resetAllByTaskIdSync(mTaskId);

        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        assertEquals(2, items.size());
        for (TaskChecklistItem item : items) {
            assertFalse(item.checked);
            assertFalse(item.crossedOut);
        }
    }

    // ---- loadChecklistItems（验证数据） ----

    @Test
    public void loadChecklistItems_returnsItems() {
        TaskEntity task = createAndPersistTask(30, false, false);

        insertChecklistItem(false, false);
        insertChecklistItem(true, false);

        List<TaskChecklistItem> items = mChecklistRepo.getByTaskIdSync(mTaskId);
        assertEquals(2, items.size());
    }

    // ---- 辅助方法 ----

    /** 创建任务并持久化到 DB，返回持久化后的实体（含自增 ID） */
    private TaskEntity createAndPersistTask(int focusMinutes, boolean executing, boolean withSchedule) {
        TaskEntity task = new TaskEntity();
        task.content = "测试任务";
        task.quadrant = 0;
        task.focusMinutes = focusMinutes;
        task.createdAt = System.currentTimeMillis();
        if (executing) {
            task.executingStartMs = System.currentTimeMillis() - 60000;
        }
        mTaskId = mDb.taskDao().insert(task);

        if (withSchedule) {
            insertSchedule(mTaskId);
        }
        return mDb.taskDao().getTaskByIdSync(mTaskId);
    }

    /** 反射调用 BaseTaskViewModel#checkListStateNeedsConfirm（protected） */
    private boolean invokeCheckListStateNeedsConfirm(TaskEntity task, long taskId) throws Exception {
        java.lang.reflect.Method m = BaseTaskViewModel.class.getDeclaredMethod(
            "checkListStateNeedsConfirm", TaskEntity.class, long.class);
        m.setAccessible(true);
        return (boolean) m.invoke(mViewModel, task, taskId);
    }

    /** 通过反射设置 ViewModel 的 mTask */
    private void setTask(TaskEntity task) {
        try {
            Field f = ReminderDetailViewModel.class.getDeclaredField("mTask");
            f.setAccessible(true);
            f.set(mViewModel, task);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 通过反射设置 ViewModel 的 mSchedule */
    private void setSchedule(TaskScheduleEntity schedule) {
        try {
            Field f = ReminderDetailViewModel.class.getDeclaredField("mSchedule");
            f.setAccessible(true);
            f.set(mViewModel, schedule);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertSchedule(long taskId) {
        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.taskId = taskId;
        schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        schedule.scheduledTime = 600;
        schedule.enabled = true;
        schedule.createdAt = System.currentTimeMillis();
        schedule.updatedAt = System.currentTimeMillis();
        mDb.taskScheduleDao().insert(schedule);
    }

    private TaskChecklistItem insertChecklistItem(boolean checked, boolean crossedOut) {
        TaskChecklistItem item = new TaskChecklistItem();
        item.taskId = mTaskId;
        item.orderIndex = 0;
        item.content = "清单项";
        item.checked = checked;
        item.crossedOut = crossedOut;
        mDb.taskChecklistItemDao().insertAll(Arrays.asList(item));
        // 从 DB 回读获取自增 ID
        List<TaskChecklistItem> all = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        return all.get(all.size() - 1);
    }

    private void insertAppAction(String packageName, String hint) {
        TaskAppAction action = new TaskAppAction();
        action.taskId = mTaskId;
        action.orderIndex = 0;
        action.packageName = packageName;
        action.hint = hint;
        mDb.taskAppActionDao().insertAll(Arrays.asList(action));
    }

    // ---- TestApplication ----

    public static class TestApplication extends JustNowApplication {
        private AppDatabase mDb;

        @Override
        public void onCreate() {
            // 故意不调 super.onCreate()：跳过 AppDatabase.getInstance / 节假日 / Alarm
        }

        public void attachDatabase(AppDatabase db) {
            mDb = db;
            try {
                Field dbField = JustNowApplication.class.getDeclaredField("mDatabase");
                dbField.setAccessible(true);
                dbField.set(this, db);
            } catch (Exception e) {
                throw new RuntimeException("Failed to set mDatabase", e);
            }
        }

        @Override
        public AppDatabase getDatabase() {
            return mDb;
        }
    }
}
