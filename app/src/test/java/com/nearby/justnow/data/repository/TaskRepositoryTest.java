package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;


import static org.junit.Assert.*;

/**
 * TaskRepository 测试 —— 缓存行为与并发安全集合验证。
 * 验证 mCachedActiveTasks 的缓存正确性。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskRepositoryTest {

    private AppDatabase mDb;
    private TaskRepository mRepo;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepo = new TaskRepository(mDb);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- getAllActiveTasksSync 缓存 ----

    @Test
    public void getAllActiveTasksSync_firstCallFromDb_empty() {
        List<TaskEntity> result = mRepo.getAllActiveTasksSync();
        assertTrue("首次调用 DB 无记录应为空", result.isEmpty());
    }

    @Test
    public void getAllActiveTasksSync_firstCallReturnsDbData() {
        long id = mRepo.insertSync(task("任务一", 0, 30));

        // 新 repo 实例（无缓存）
        TaskRepository freshRepo = new TaskRepository(mDb);
        List<TaskEntity> result = freshRepo.getAllActiveTasksSync();
        assertEquals(1, result.size());
        assertEquals("任务一", result.get(0).content);
    }

    @Test
    public void getAllActiveTasksSync_cacheHit_doesNotRequery() {
        mRepo.insertSync(task("任务一", 0, 30));
        mRepo.getAllActiveTasksSync(); // 预热缓存

        // 直接往 DB 插入（绕过缓存更新）
        TaskEntity newTask = task("任务二", 1, 60);
        mDb.taskDao().insert(newTask);

        // 缓存命中 → 应仅含预热时的数据
        List<TaskEntity> result = mRepo.getAllActiveTasksSync();
        assertEquals("缓存命中应仅含预热时的 1 条", 1, result.size());
    }

    @Test
    public void getAllActiveTasksSync_returnsDefensiveCopy() {
        mRepo.insertSync(task("任务一", 0, 30));

        List<TaskEntity> result1 = mRepo.getAllActiveTasksSync();
        assertEquals(1, result1.size());
        result1.clear();

        List<TaskEntity> result2 = mRepo.getAllActiveTasksSync();
        assertEquals("防御性拷贝：修改返回列表不应影响缓存", 1, result2.size());
    }

    // ---- insertSync 缓存更新 ----

    @Test
    public void insertSync_addsToCache() {
        mRepo.getAllActiveTasksSync(); // 预热缓存

        mRepo.insertSync(task("新任务", 0, 30));

        // 不从 DB 重新查询，直接走缓存
        List<TaskEntity> result = mRepo.getAllActiveTasksSync();
        assertEquals("缓存应包含新插入的任务", 1, result.size());
        assertEquals("新任务", result.get(0).content);
    }

    // ---- archiveTaskSync 缓存更新 ----

    @Test
    public void archiveTaskSync_removesFromActiveCache() {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.getAllActiveTasksSync(); // 预热缓存
        assertEquals(1, mRepo.getAllActiveTasksSync().size());

        mRepo.archiveTaskSync(taskId);

        assertEquals("归档后应将任务从活跃缓存中移除", 0, mRepo.getAllActiveTasksSync().size());
    }

    // ---- update() 缓存失效 ----

    @Test
    public void update_invalidatesActiveCache() throws InterruptedException {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.getAllActiveTasksSync(); // 预热缓存
        assertEquals(1, mRepo.getAllActiveTasksSync().size());

        // 直接改 DB（绕过缓存更新）
        TaskEntity dbTask = mDb.taskDao().getTaskByIdSync(taskId);
        dbTask.content = "修改后的任务";
        mRepo.update(dbTask); // 缓存设为 null，DB 写异步

        // update 中 mCachedActiveTasks 设为 null → 缓存立即失效
        // 但 DB 写走 runInBackground，需等 executor 完成后再验证 DB 内容
        Thread.sleep(300);

        List<TaskEntity> result = mRepo.getAllActiveTasksSync();
        assertEquals("缓存失效后应从 DB 重新加载", 1, result.size());
        assertEquals("修改后的任务", result.get(0).content);
    }

    @Test
    public void testTaskIconNamePersistence() {
        TaskEntity task = new TaskEntity();
        task.content = "测试内置图标任务";
        task.iconName = "palette"; // 选择美术图标
        task.createdAt = System.currentTimeMillis();

        long id = mRepo.insertSync(task);
        TaskEntity retrieved = mRepo.getTaskByIdSync(id);

        org.junit.Assert.assertNotNull(retrieved);
        org.junit.Assert.assertEquals("palette", retrieved.iconName);
    }

    @Test
    public void isPeriodQuotaReachedSync_weeklyQuota_reachedAndNotReached() {
        TaskEntity task = new TaskEntity();
        task.content = "每周3次任务";
        task.completionMode = 1; // 周
        task.quota = 3;
        task.createdAt = System.currentTimeMillis();
        long id = mRepo.insertSync(task);
        task.id = id;

        // 初始未达到
        assertFalse(mRepo.isPeriodQuotaReachedSync(task));

        String periodKey = TaskRepository.computePeriodKey(task);
        mRepo.incrementCompletionCounterSync(id, periodKey);
        mRepo.incrementCompletionCounterSync(id, periodKey);
        assertFalse("完成2次未达配额3", mRepo.isPeriodQuotaReachedSync(task));

        mRepo.incrementCompletionCounterSync(id, periodKey);
        assertTrue("完成3次达到配额3", mRepo.isPeriodQuotaReachedSync(task));
    }

    // ---- 辅助方法 ----

    private static TaskEntity task(String content, int quadrant, int focusMinutes) {
        TaskEntity task = new TaskEntity();
        task.content = content;
        task.quadrant = quadrant;
        task.focusMinutes = focusMinutes;
        task.createdAt = System.currentTimeMillis();
        return task;
    }
}
