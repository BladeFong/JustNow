package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TaskRepository 测试 —— 缓存行为与并发安全集合验证。
 * 验证 mCachedActiveTasks / mCachedDegrades (CopyOnWriteArrayList) 的缓存正确性。
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

    // ---- getAllDegradesSync 缓存 ----

    @Test
    public void getAllDegradesSync_firstCallFromDb_empty() {
        List<TaskQuadrantDegradeEntity> result = mRepo.getAllDegradesSync();
        assertTrue("首次调用 DB 无记录应为空", result.isEmpty());
    }

    @Test
    public void getAllDegradesSync_cacheHit_doesNotRequery() {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.insertDegradeSync(taskId, 0, System.currentTimeMillis() + 99999999);
        mRepo.getAllDegradesSync(); // 预热缓存

        // 直接往 DB 插入另一条降级（绕过缓存）
        long taskId2 = mRepo.insertSync(task("任务二", 1, 60));
        insertDegradeDirect(taskId2, 1, System.currentTimeMillis() + 99999999);

        // 缓存命中 → 应仅含预热时的 1 条
        List<TaskQuadrantDegradeEntity> result = mRepo.getAllDegradesSync();
        assertEquals("缓存命中应仅含预热时的 1 条", 1, result.size());
    }

    @Test
    public void getAllDegradesSync_returnsDefensiveCopy() {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.insertDegradeSync(taskId, 0, System.currentTimeMillis() + 99999999);

        List<TaskQuadrantDegradeEntity> result1 = mRepo.getAllDegradesSync();
        assertEquals(1, result1.size());
        result1.clear();

        List<TaskQuadrantDegradeEntity> result2 = mRepo.getAllDegradesSync();
        assertEquals("防御性拷贝：修改返回列表不应影响缓存", 1, result2.size());
    }

    // ---- getNonExpiredDegradeMapSync ----

    @Test
    public void getNonExpiredDegradeMapSync_onlyReturnsUnexpired() {
        long taskId1 = mRepo.insertSync(task("任务一", 0, 30));
        long taskId2 = mRepo.insertSync(task("任务二", 1, 60));

        // 未过期：未来时间
        mRepo.insertDegradeSync(taskId1, 0, System.currentTimeMillis() + 3600000L);
        // 已过期：过去时间
        mRepo.insertDegradeSync(taskId2, 1, System.currentTimeMillis() - 3600000L);

        Map<Long, TaskQuadrantDegradeEntity> map = mRepo.getNonExpiredDegradeMapSync();
        assertEquals("应仅含未过期的降级记录", 1, map.size());
        assertTrue("应包含未过期的任务", map.containsKey(taskId1));
        assertFalse("不应包含已过期的任务", map.containsKey(taskId2));
    }

    @Test
    public void getNonExpiredDegradeMapSync_emptyWhenNoDegrades() {
        Map<Long, TaskQuadrantDegradeEntity> map = mRepo.getNonExpiredDegradeMapSync();
        assertTrue("无降级记录时应返回空 map", map.isEmpty());
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

    @Test
    public void archiveTaskSync_removesFromDegradeCache() {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.insertDegradeSync(taskId, 0, System.currentTimeMillis() + 99999999L);
        assertEquals(1, mRepo.getAllDegradesSync().size());

        mRepo.archiveTaskSync(taskId);

        assertEquals("归档后应将降级记录从缓存中移除", 0, mRepo.getAllDegradesSync().size());
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

    // ---- insertDegradeSync 缓存更新 ----

    @Test
    public void insertDegradeSync_addsToCache() {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.getAllDegradesSync(); // 预热缓存

        mRepo.insertDegradeSync(taskId, 0, System.currentTimeMillis() + 99999999L);

        List<TaskQuadrantDegradeEntity> result = mRepo.getAllDegradesSync();
        assertEquals("降级记录应添加到缓存", 1, result.size());
    }

    // ---- deleteDegradeSync 缓存更新 ----

    @Test
    public void deleteDegradeSync_removesFromCache() {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.insertDegradeSync(taskId, 0, System.currentTimeMillis() + 99999999L);
        assertEquals(1, mRepo.getAllDegradesSync().size());

        mRepo.deleteDegradeSync(taskId);

        assertEquals("降级记录应从缓存中移除", 0, mRepo.getAllDegradesSync().size());
    }

    // ---- delete 异步 ----

    @Test
    public void delete_removesFromBothCaches() throws InterruptedException {
        long taskId = mRepo.insertSync(task("任务一", 0, 30));
        mRepo.insertDegradeSync(taskId, 0, System.currentTimeMillis() + 99999999L);
        mRepo.getAllActiveTasksSync();
        mRepo.getAllDegradesSync();
        assertEquals(1, mRepo.getAllActiveTasksSync().size());
        assertEquals(1, mRepo.getAllDegradesSync().size());

        mRepo.delete(taskId);

        // delete 走 runInBackground，等待 executor 完成
        Thread.sleep(300);

        assertEquals("删除后活跃缓存应清空", 0, mRepo.getAllActiveTasksSync().size());
        assertEquals("删除后降级缓存应清空", 0, mRepo.getAllDegradesSync().size());
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

    /** 直接操作 DAO 插入降级记录（绕过缓存更新，用于缓存命中测试）。 */
    private void insertDegradeDirect(long taskId, int originalQuadrant, long recoverMs) {
        TaskQuadrantDegradeEntity entity = new TaskQuadrantDegradeEntity();
        entity.taskId = taskId;
        entity.originalQuadrant = originalQuadrant;
        entity.recoverMs = recoverMs;
        mDb.taskQuadrantDegradeDao().insert(entity);
    }
}
