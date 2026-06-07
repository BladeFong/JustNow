package com.nearby.justnow.ui.taskinput;

import android.content.Context;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskAppAction;

import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskInputViewModelTest {

    private AppDatabase mDb;
    private TaskInputViewModel mViewModel;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mViewModel = new TaskInputViewModel(new TestApplication(mDb));
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    @Test
    public void saveTask_withoutTag_savesNullTagId() throws Exception {
        mViewModel.setTitle("无标签任务");
        mViewModel.setDetail("");
        mViewModel.setTagName("");
        mViewModel.setQuadrant(0);
        mViewModel.setFocusMinutes(30);

        CountDownLatch latch = new CountDownLatch(1);
        mViewModel.saveTask(latch::countDown);
        // saveTask 的回调通过 runOnUiThread 投递到主线程 Handler；
        // 不能直接 await() 阻塞主线程，改用轮询 + idleMainLooper 推进消息队列。
        long deadline = System.currentTimeMillis() + 3000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(50);
        }
        assertTrue("save 应在 3 秒内完成", latch.getCount() == 0);

        List<TaskEntity> tasks = mDb.taskDao().getAllActiveTasksSync();
        assertEquals(1, tasks.size());
        assertNull(tasks.get(0).tagId);
    }

    // ---- checklistContentChanged null 安全 ----

    @Test
    public void checklistContentChanged_nullContent_vs_oldContent_detectsChange() throws Exception {
        Method method = TaskInputViewModel.class.getDeclaredMethod(
            "checklistContentChanged", List.class, List.class);
        method.setAccessible(true);

        TaskChecklistItem oldItem = new TaskChecklistItem();
        oldItem.content = "旧内容";
        TaskChecklistItem newItem = new TaskChecklistItem();
        newItem.content = null;

        List<TaskChecklistItem> oldItems = Collections.singletonList(oldItem);
        List<TaskChecklistItem> newItems = Collections.singletonList(newItem);

        boolean result = (boolean) method.invoke(mViewModel, oldItems, newItems);
        assertTrue("新旧内容不同应返回 true（不抛 NPE）", result);
    }

    @Test
    public void checklistContentChanged_oldNullContent_vs_newContent_detectsChange() throws Exception {
        Method method = TaskInputViewModel.class.getDeclaredMethod(
            "checklistContentChanged", List.class, List.class);
        method.setAccessible(true);

        TaskChecklistItem oldItem = new TaskChecklistItem();
        oldItem.content = null;
        TaskChecklistItem newItem = new TaskChecklistItem();
        newItem.content = "新内容";

        List<TaskChecklistItem> oldItems = Collections.singletonList(oldItem);
        List<TaskChecklistItem> newItems = Collections.singletonList(newItem);

        boolean result = (boolean) method.invoke(mViewModel, oldItems, newItems);
        assertTrue("旧 null vs 新内容应返回 true", result);
    }

    @Test
    public void checklistContentChanged_bothNullContent_notChanged() throws Exception {
        Method method = TaskInputViewModel.class.getDeclaredMethod(
            "checklistContentChanged", List.class, List.class);
        method.setAccessible(true);

        TaskChecklistItem oldItem = new TaskChecklistItem();
        oldItem.content = null;
        TaskChecklistItem newItem = new TaskChecklistItem();
        newItem.content = null;

        List<TaskChecklistItem> oldItems = Collections.singletonList(oldItem);
        List<TaskChecklistItem> newItems = Collections.singletonList(newItem);

        boolean result = (boolean) method.invoke(mViewModel, oldItems, newItems);
        assertFalse("两者都为 null 应视为内容未变", result);
    }

    @Test
    public void saveTask_emptyAppActions_savesNoModule() throws Exception {
        mViewModel.setTitle("空 APP 模块");
        mViewModel.setQuadrant(0);
        mViewModel.setSelectedModuleType("app_actions");
        mViewModel.setPendingAppActions(Collections.emptyList());

        saveAndDrain();

        TaskEntity task = mDb.taskDao().getAllActiveTasksSync().get(0);
        assertNull(task.detailModuleType);
        assertTrue(mDb.taskAppActionDao().getByTaskIdSync(task.id).isEmpty());
    }

    @Test
    public void saveTask_emptyChecklist_savesNoModule() throws Exception {
        mViewModel.setTitle("空清单模块");
        mViewModel.setQuadrant(0);
        mViewModel.setSelectedModuleType("checklist");
        mViewModel.setPendingChecklistItems(Collections.emptyList());

        saveAndDrain();

        TaskEntity task = mDb.taskDao().getAllActiveTasksSync().get(0);
        assertNull(task.detailModuleType);
        assertTrue(mDb.taskChecklistItemDao().getByTaskIdSync(task.id).isEmpty());
    }

    @Test
    public void saveTask_nonEmptyAppActions_savesModuleAndItems() throws Exception {
        mViewModel.setTitle("APP 模块");
        mViewModel.setQuadrant(0);
        mViewModel.setSelectedModuleType("app_actions");
        TaskAppAction action = appAction("com.example.app", "打开示例");
        mViewModel.setPendingAppActions(Collections.singletonList(action));

        saveAndDrain();

        TaskEntity task = mDb.taskDao().getAllActiveTasksSync().get(0);
        assertEquals("app_actions", task.detailModuleType);
        List<TaskAppAction> actions = mDb.taskAppActionDao().getByTaskIdSync(task.id);
        assertEquals(1, actions.size());
        assertEquals("com.example.app", actions.get(0).packageName);
        assertEquals("打开示例", actions.get(0).hint);
    }

    @Test
    public void saveTask_nonEmptyChecklist_savesModuleAndItems() throws Exception {
        mViewModel.setTitle("清单模块");
        mViewModel.setQuadrant(0);
        mViewModel.setSelectedModuleType("checklist");
        TaskChecklistItem item = checklistItem("检查结果");
        mViewModel.setPendingChecklistItems(Collections.singletonList(item));

        saveAndDrain();

        TaskEntity task = mDb.taskDao().getAllActiveTasksSync().get(0);
        assertEquals("checklist", task.detailModuleType);
        List<TaskChecklistItem> items = mDb.taskChecklistItemDao().getByTaskIdSync(task.id);
        assertEquals(1, items.size());
        assertEquals("检查结果", items.get(0).content);
    }

    @Test
    public void saveTask_editExistingAppActionsToEmpty_clearsModuleAndItems() throws Exception {
        long taskId = insertTaskWithModule("app_actions");
        mDb.taskAppActionDao().insertAll(Collections.singletonList(
            appAction(taskId, 0, "com.old", "旧 APP")));

        mViewModel.loadTaskForEdit(taskId);
        waitForBackground();
        mViewModel.setSelectedModuleType("app_actions");
        mViewModel.setPendingAppActions(Collections.emptyList());

        saveAndDrain();

        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        assertNull(task.detailModuleType);
        assertTrue(mDb.taskAppActionDao().getByTaskIdSync(taskId).isEmpty());
    }

    @Test
    public void saveTask_editExistingChecklistToEmpty_clearsModuleAndItems() throws Exception {
        long taskId = insertTaskWithModule("checklist");
        mDb.taskChecklistItemDao().insertAll(Collections.singletonList(
            checklistItem(taskId, 0, "旧清单")));

        mViewModel.loadTaskForEdit(taskId);
        waitForBackground();
        mViewModel.setSelectedModuleType("checklist");
        mViewModel.setPendingChecklistItems(Collections.emptyList());

        saveAndDrain();

        TaskEntity task = mDb.taskDao().getTaskByIdSync(taskId);
        assertNull(task.detailModuleType);
        assertTrue(mDb.taskChecklistItemDao().getByTaskIdSync(taskId).isEmpty());
    }

    private void saveAndDrain() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mViewModel.saveTask(latch::countDown);
        long deadline = System.currentTimeMillis() + 3000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(50);
        }
        assertTrue("save 应在 3 秒内完成", latch.getCount() == 0);
    }

    private void waitForBackground() throws Exception {
        Thread.sleep(200);
        ShadowLooper.idleMainLooper();
    }

    private long insertTaskWithModule(String moduleType) {
        TaskEntity task = new TaskEntity();
        task.content = "旧模块任务";
        task.quadrant = 0;
        task.focusMinutes = 30;
        task.createdAt = System.currentTimeMillis();
        task.detailModuleType = moduleType;
        return mDb.taskDao().insert(task);
    }

    private static TaskAppAction appAction(String packageName, String hint) {
        TaskAppAction action = new TaskAppAction();
        action.packageName = packageName;
        action.hint = hint;
        return action;
    }

    private static TaskAppAction appAction(long taskId, int orderIndex,
                                           String packageName, String hint) {
        TaskAppAction action = appAction(packageName, hint);
        action.taskId = taskId;
        action.orderIndex = orderIndex;
        return action;
    }

    private static TaskChecklistItem checklistItem(String content) {
        TaskChecklistItem item = new TaskChecklistItem();
        item.content = content;
        return item;
    }

    private static TaskChecklistItem checklistItem(long taskId, int orderIndex, String content) {
        TaskChecklistItem item = checklistItem(content);
        item.taskId = taskId;
        item.orderIndex = orderIndex;
        return item;
    }

    private static class TestApplication extends JustNowApplication {
        private final AppDatabase mDb;

        TestApplication(AppDatabase db) {
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
