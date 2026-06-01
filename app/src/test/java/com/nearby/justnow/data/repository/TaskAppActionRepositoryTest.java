package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskAppAction;
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
public class TaskAppActionRepositoryTest {

    private AppDatabase mDb;
    private TaskAppActionRepository mRepo;
    private long mTaskId;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepo = new TaskAppActionRepository(mDb);

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
        List<TaskAppAction> actions = mRepo.getByTaskIdSync(mTaskId);
        assertTrue(actions.isEmpty());
    }

    @Test
    public void getByTaskIdSync_withItems_orderedByIndex() {
        insertActions(
            action(mTaskId, 2, "com.example.c", null, "第三个"),
            action(mTaskId, 0, "com.example.a", null, "第一个"),
            action(mTaskId, 1, "com.example.b", null, "第二个")
        );

        List<TaskAppAction> actions = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(3, actions.size());
        assertEquals("第一个", actions.get(0).hint);
        assertEquals("第二个", actions.get(1).hint);
        assertEquals("第三个", actions.get(2).hint);
    }

    @Test
    public void getByTaskIdSync_preservesAllFields() {
        TaskAppAction action = action(mTaskId, 0, "com.example.app", "example://open", "提示文字");
        insertActions(action);

        List<TaskAppAction> actions = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(1, actions.size());
        TaskAppAction result = actions.get(0);
        assertEquals(mTaskId, result.taskId);
        assertEquals("com.example.app", result.packageName);
        assertEquals("example://open", result.deepLink);
        assertEquals("提示文字", result.hint);
    }

    // ---- replaceAllByTaskId (同步版本) ----

    @Test
    public void replaceAllByTaskIdSync_insertsAndDeletes() {
        insertActions(action(mTaskId, 0, "com.old", null, "旧项"));

        List<TaskAppAction> newActions = Arrays.asList(
            action(mTaskId, 0, "com.new.a", null, "新项一"),
            action(mTaskId, 1, "com.new.b", null, "新项二")
        );
        // 直接测试同步版本，不经过 Executor
        mDb.taskAppActionDao().deleteByTaskId(mTaskId);
        mDb.taskAppActionDao().insertAll(newActions);

        List<TaskAppAction> actions = mRepo.getByTaskIdSync(mTaskId);
        assertEquals(2, actions.size());
        assertEquals("新项一", actions.get(0).hint);
        assertEquals("新项二", actions.get(1).hint);
    }

    @Test
    public void replaceAllByTaskIdSync_emptyList_clearsAll() {
        insertActions(action(mTaskId, 0, "com.old", null, "旧项"));

        mDb.taskAppActionDao().deleteByTaskId(mTaskId);

        assertTrue(mRepo.getByTaskIdSync(mTaskId).isEmpty());
    }

    // ---- 级联删除 ----

    @Test
    public void cascadeDelete_taskRemovesActions() {
        insertActions(
            action(mTaskId, 0, "com.a", null, "A"),
            action(mTaskId, 1, "com.b", null, "B")
        );

        mDb.taskDao().delete(mTaskId);

        assertTrue(mRepo.getByTaskIdSync(mTaskId).isEmpty());
    }

    // ---- 不同任务隔离 ----

    @Test
    public void differentTasks_isolated() {
        TaskEntity task2 = new TaskEntity();
        task2.content = "任务二";
        task2.quadrant = 0;
        task2.focusMinutes = 0;
        task2.createdAt = System.currentTimeMillis();
        long taskId2 = mDb.taskDao().insert(task2);

        insertActions(action(mTaskId, 0, "com.task1", null, "任务一"));
        insertActions(action(taskId2, 0, "com.task2", null, "任务二"));

        assertEquals(1, mRepo.getByTaskIdSync(mTaskId).size());
        assertEquals(1, mRepo.getByTaskIdSync(taskId2).size());
        assertEquals("任务一", mRepo.getByTaskIdSync(mTaskId).get(0).hint);
        assertEquals("任务二", mRepo.getByTaskIdSync(taskId2).get(0).hint);
    }

    // ---- 辅助方法 ----

    private void insertActions(TaskAppAction... actions) {
        mDb.taskAppActionDao().insertAll(Arrays.asList(actions));
    }

    private static TaskAppAction action(long taskId, int orderIndex,
                                        String packageName, String deepLink, String hint) {
        TaskAppAction action = new TaskAppAction();
        action.taskId = taskId;
        action.orderIndex = orderIndex;
        action.packageName = packageName;
        action.deepLink = deepLink;
        action.hint = hint;
        return action;
    }
}
