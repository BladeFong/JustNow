package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * TaskExecutionRepository 测试 —— 缓存行为与并发安全集合验证。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskExecutionRepositoryTest {

    private AppDatabase mDb;
    private TaskExecutionRepository mRepo;
    private long mTaskId;
    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepo = new TaskExecutionRepository(mDb);

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

    // ---- 缓存基本行为 ----

    @Test
    public void getTodayExecutionsSync_firstCallFromDb_empty() {
        List<TaskExecutionEntity> result = mRepo.getTodayExecutionsSync();
        assertTrue("首次调用 DB 无记录应为空", result.isEmpty());
    }

    @Test
    public void getTodayExecutionsSync_cacheHit_doesNotRequery() {
        // 预热缓存（DB 此时为空）
        mRepo.getTodayExecutionsSync();

        // 直接往 DB 插入（绕过缓存更新）
        TaskExecutionEntity entity = execution(mTaskId, 100, 200);
        mDb.taskExecutionDao().insert(entity);

        // 缓存命中 → 应返回缓存中的旧数据（不含新插入的记录）
        List<TaskExecutionEntity> result = mRepo.getTodayExecutionsSync();
        assertTrue("缓存命中应返回旧数据，不含绕过缓存插入的记录", result.isEmpty());
    }

    @Test
    public void getTodayExecutionsSync_returnsDefensiveCopy() {
        long now = System.currentTimeMillis();
        mRepo.recordCompleteSync(mTaskId, now - 60000, now, 30);

        List<TaskExecutionEntity> result1 = mRepo.getTodayExecutionsSync();
        assertEquals(1, result1.size());
        result1.clear(); // 修改返回列表

        // 缓存不应受影响
        List<TaskExecutionEntity> result2 = mRepo.getTodayExecutionsSync();
        assertEquals("防御性拷贝：修改返回列表不应影响缓存", 1, result2.size());
    }

    // ---- recordCompleteSync 缓存更新 ----

    @Test
    public void recordCompleteSync_addsToCache_ifCacheWarm() {
        mRepo.getTodayExecutionsSync(); // 预热缓存
        long now = System.currentTimeMillis();

        mRepo.recordCompleteSync(mTaskId, now - 60000, now, 30);

        List<TaskExecutionEntity> result = mRepo.getTodayExecutionsSync();
        assertEquals("预热缓存后 recordCompleteSync 应添加到缓存", 1, result.size());
        assertEquals(mTaskId, result.get(0).taskId);
    }

    @Test
    public void recordCompleteSync_withoutWarmCache_noCrash() {
        long now = System.currentTimeMillis();

        // 未预热缓存直接调用 recordCompleteSync
        mRepo.recordCompleteSync(mTaskId, now - 60000, now, 30);

        // 不会崩溃，缓存会从 DB 加载
        List<TaskExecutionEntity> result = mRepo.getTodayExecutionsSync();
        assertEquals(1, result.size());
    }

    // ---- recordComplete 异步回调 ----

    @Test
    public void recordComplete_callbackInvoked() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] called = {false};
        long now = System.currentTimeMillis();

        mRepo.recordComplete(mTaskId, now - 60000, now, 60, () -> {
            called[0] = true;
            latch.countDown();
        });

        assertTrue("onComplete 应在超时前被调用", latch.await(5, TimeUnit.SECONDS));
        assertTrue("onComplete 回调应被触发", called[0]);
    }

    // ---- recordScheduled 异步 ----

    @Test
    public void recordScheduled_callbackInvoked() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] called = {false};

        mRepo.recordScheduled(mTaskId, "10:30", "10:00-11:00", () -> {
            called[0] = true;
            latch.countDown();
        });

        assertTrue("onComplete 应在超时前被调用", latch.await(5, TimeUnit.SECONDS));
        assertTrue("onComplete 回调应被触发", called[0]);
    }

    // ---- countExecutionsSync ----

    @Test
    public void countExecutionsSync_returnsCorrectCount() {
        long now = System.currentTimeMillis();
        assertEquals("无记录时计数应为 0", 0, mRepo.countExecutionsSync(mTaskId));

        mRepo.recordCompleteSync(mTaskId, now - 120000, now - 60000, 30);
        mRepo.recordCompleteSync(mTaskId, now - 60000, now, 60);

        assertEquals("两条记录计数应为 2", 2, mRepo.countExecutionsSync(mTaskId));
    }

    @Test
    public void countExecutionsSync_differentTasks() {
        // 创建另一个任务
        TaskEntity task2 = new TaskEntity();
        task2.content = "任务二";
        task2.quadrant = 0;
        task2.focusMinutes = 0;
        task2.createdAt = System.currentTimeMillis();
        long taskId2 = mDb.taskDao().insert(task2);

        long now = System.currentTimeMillis();
        mRepo.recordCompleteSync(mTaskId, now - 120000, now - 60000, 30);
        mRepo.recordCompleteSync(taskId2, now - 60000, now, 60);

        assertEquals("任务一应有 1 条记录", 1, mRepo.countExecutionsSync(mTaskId));
        assertEquals("任务二应有 1 条记录", 1, mRepo.countExecutionsSync(taskId2));
    }

    // ---- 日期格式（保留原有测试） ----

    @Test
    public void dateFormat_usesCorrectPattern() {
        String today = LocalDate.now().format(sDateFormat);
        assertNotNull(today);
        assertEquals(10, today.length());
        assertTrue(today.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void dateFormat_paddedValues() {
        assertEquals("2026-01-05", LocalDate.of(2026, 1, 5).format(sDateFormat));
    }

    @Test
    public void dateFormat_decemberMonth() {
        assertEquals("2026-12-31", LocalDate.of(2026, 12, 31).format(sDateFormat));
    }

    // ---- 辅助方法 ----

    private static TaskExecutionEntity execution(long taskId, long startMs, long endMs) {
        TaskExecutionEntity entity = new TaskExecutionEntity();
        entity.taskId = taskId;
        entity.date = LocalDate.now().format(sDateFormat);
        entity.startMs = startMs;
        entity.endMs = endMs;
        entity.status = 0;
        return entity;
    }
}
