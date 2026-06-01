package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskChecklistRepositoryTest {

    private AppDatabase mDb;
    private TaskChecklistRepository mRepo;
    private long mTaskId;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepo = new TaskChecklistRepository(mDb);

        TaskEntity task = new TaskEntity();
        task.content = "测试任务";
        task.quadrant = 0;
        task.focusMinutes = 0;
        task.createdAt = System.currentTimeMillis();
        mTaskId = mDb.taskDao().insert(task);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- getByTaskIdSync ----

    @Test
    public void getByTaskIdSync_empty() {
        List<TaskChecklistItem> items = mRepo.getByTaskIdSync(mTaskId);
        assertTrue(items.isEmpty());
    }

    @Test
    public void getByTaskIdSync_withItems_orderedByIndex() {
        insertItems(
            item(mTaskId, 2, "第三项"),
            item(mTaskId, 0, "第一项"),
            item(mTaskId, 1, "第二项")
        );

        List<TaskChecklistItem> items = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(3, items.size());
        assertEquals("第一项", items.get(0).content);
        assertEquals("第二项", items.get(1).content);
        assertEquals("第三项", items.get(2).content);
    }

    // ---- hasAnyStateSync ----

    @Test
    public void hasAnyStateSync_empty() {
        assertFalse(mRepo.hasAnyStateSync(mTaskId));
    }

    @Test
    public void hasAnyStateSync_noState() {
        insertItems(item(mTaskId, 0, "未勾选未划掉"));
        assertFalse(mRepo.hasAnyStateSync(mTaskId));
    }

    @Test
    public void hasAnyStateSync_checked() {
        TaskChecklistItem item = item(mTaskId, 0, "已勾选");
        item.checked = true;
        insertItems(item);
        assertTrue(mRepo.hasAnyStateSync(mTaskId));
    }

    @Test
    public void hasAnyStateSync_crossedOut() {
        TaskChecklistItem item = item(mTaskId, 0, "已划掉");
        item.crossedOut = true;
        insertItems(item);
        assertTrue(mRepo.hasAnyStateSync(mTaskId));
    }

    @Test
    public void hasAnyStateSync_bothCheckedAndCrossedOut() {
        TaskChecklistItem a = item(mTaskId, 0, "已勾选");
        a.checked = true;
        TaskChecklistItem b = item(mTaskId, 1, "已划掉");
        b.crossedOut = true;
        insertItems(a, b);
        assertTrue(mRepo.hasAnyStateSync(mTaskId));
    }

    // ---- resetAllByTaskId ----

    @Test
    public void resetAllByTaskId_clearsBothCheckedAndCrossedOut() {
        TaskChecklistItem a = item(mTaskId, 0, "已勾选");
        a.checked = true;
        TaskChecklistItem b = item(mTaskId, 1, "已划掉");
        b.crossedOut = true;
        insertItems(a, b);

        mRepo.resetAllByTaskIdSync(mTaskId);

        List<TaskChecklistItem> items = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(2, items.size());
        for (TaskChecklistItem item : items) {
            assertFalse(item.checked);
            assertFalse(item.crossedOut);
        }
    }

    // ---- replaceAllByTaskId ----

    @Test
    public void replaceAllByTaskId_insertsAndDeletes() {
        insertItems(item(mTaskId, 0, "旧项"));

        List<TaskChecklistItem> newItems = Arrays.asList(
            item(mTaskId, 0, "新项一"),
            item(mTaskId, 1, "新项二")
        );
        mRepo.replaceAllByTaskIdSync(mTaskId, newItems);

        List<TaskChecklistItem> items = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(2, items.size());
        assertEquals("新项一", items.get(0).content);
        assertEquals("新项二", items.get(1).content);
    }

    @Test
    public void replaceAllByTaskId_emptyList_clearsAll() {
        insertItems(item(mTaskId, 0, "旧项"));

        mRepo.replaceAllByTaskIdSync(mTaskId, new ArrayList<>());

        assertTrue(mRepo.getByTaskIdSync(mTaskId).isEmpty());
    }

    // ---- updateState ----

    @Test
    public void updateState_persistsChecked() {
        TaskChecklistItem item = item(mTaskId, 0, "测试项");
        insertItems(item);

        // 从 DB 回读获取自增 ID 后再更新
        List<TaskChecklistItem> inserted = mDb.taskChecklistItemDao().getByTaskIdSync(mTaskId);
        inserted.get(0).checked = true;
        mDb.taskChecklistItemDao().update(inserted.get(0));

        List<TaskChecklistItem> items = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(1, items.size());
        assertTrue(items.get(0).checked);
    }

    // ---- 级联删除 ----

    @Test
    public void cascadeDelete_taskRemovesItems() {
        insertItems(item(mTaskId, 0, "项一"), item(mTaskId, 1, "项二"));

        mDb.taskDao().delete(mTaskId);

        assertTrue(mRepo.getByTaskIdSync(mTaskId).isEmpty());
    }

    // ---- 辅助方法 ----

    private void insertItems(TaskChecklistItem... items) {
        mDb.taskChecklistItemDao().insertAll(Arrays.asList(items));
    }

    private static TaskChecklistItem item(long taskId, int orderIndex, String content) {
        TaskChecklistItem item = new TaskChecklistItem();
        item.taskId = taskId;
        item.orderIndex = orderIndex;
        item.content = content;
        return item;
    }
}
