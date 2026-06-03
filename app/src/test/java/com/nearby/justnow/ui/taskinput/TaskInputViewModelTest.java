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

import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
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
