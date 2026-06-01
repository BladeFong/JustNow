package com.nearby.justnow.data.dao;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * TaskDao.convertToChore 单元测试 — Room in-memory。
 *
 * <p>验证「< 15min 完成 → 完成并调整」路径下，{@code convertToChore} 必须：</p>
 * <ul>
 *   <li>{@code focus_minutes} 改 0</li>
 *   <li>同时清空 {@code executing_start_ms} / {@code executing_end_ms}（防止"执行中"残留）</li>
 *   <li>不影响其他字段（content / detail / tag / quadrant 等）</li>
 *   <li>不存在的 taskId 不抛异常</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskDaoConvertToChoreTest {

    private AppDatabase mDb;
    private TaskDao mTaskDao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mTaskDao = mDb.taskDao();
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- 主路径 ----

    @Test
    public void convertToChore_clearsFocusMinutesAndExecutingFields() {
        long now = System.currentTimeMillis();
        TaskEntity task = newTask("写报告", /*quadrant*/ 0, /*focusMinutes*/ 60);
        task.executingStartMs = now - 300_000L;
        task.executingEndMs = now - 60_000L;
        long taskId = mTaskDao.insert(task);

        mTaskDao.convertToChore(taskId);

        TaskEntity updated = mTaskDao.getTaskByIdSync(taskId);
        assertNotNull(updated);
        assertEquals(0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        assertEquals(0L, updated.executingEndMs);
    }

    @Test
    public void convertToChore_preservesOtherFields() {
        long createdAt = 1_700_000_000_000L;
        TaskEntity task = newTask("不要丢字段", /*quadrant*/ 2, /*focusMinutes*/ 90);
        task.detail = "正文内容";
        task.detailMarkdown = "# Markdown";
        task.detailModuleType = "checklist";
        task.createdAt = createdAt;
        task.executingStartMs = 999L;
        task.executingEndMs = 0L;
        task.isArchived = false;
        long taskId = mTaskDao.insert(task);

        mTaskDao.convertToChore(taskId);

        TaskEntity updated = mTaskDao.getTaskByIdSync(taskId);
        assertNotNull(updated);
        // 改琐碎相关字段
        assertEquals(0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        assertEquals(0L, updated.executingEndMs);
        // 其他字段保留
        assertEquals("不要丢字段", updated.content);
        assertEquals("正文内容", updated.detail);
        assertEquals("# Markdown", updated.detailMarkdown);
        assertEquals("checklist", updated.detailModuleType);
        assertEquals(2, updated.quadrant);
        assertEquals(createdAt, updated.createdAt);
        assertFalse(updated.isArchived);
    }

    @Test
    public void convertToChore_alreadyChoreNoExecuting_isIdempotent() {
        // 已经是琐碎且无执行状态：再调一次应当无副作用
        TaskEntity task = newTask("已是琐碎", /*quadrant*/ 3, /*focusMinutes*/ 0);
        task.executingStartMs = 0L;
        task.executingEndMs = 0L;
        long taskId = mTaskDao.insert(task);

        mTaskDao.convertToChore(taskId);

        TaskEntity updated = mTaskDao.getTaskByIdSync(taskId);
        assertNotNull(updated);
        assertEquals(0, updated.focusMinutes);
        assertEquals(0L, updated.executingStartMs);
        assertEquals(0L, updated.executingEndMs);
        assertEquals("已是琐碎", updated.content);
    }

    @Test
    public void convertToChore_onlyAffectsTargetTask() {
        TaskEntity a = newTask("目标", 0, 60);
        a.executingStartMs = 1234L;
        long idA = mTaskDao.insert(a);

        TaskEntity b = newTask("旁路", 1, 30);
        b.executingStartMs = 5678L;
        b.executingEndMs = 9999L;
        long idB = mTaskDao.insert(b);

        mTaskDao.convertToChore(idA);

        TaskEntity updatedA = mTaskDao.getTaskByIdSync(idA);
        TaskEntity untouchedB = mTaskDao.getTaskByIdSync(idB);

        assertEquals(0, updatedA.focusMinutes);
        assertEquals(0L, updatedA.executingStartMs);

        // B 的执行中字段 / focusMinutes 应完全保留
        assertEquals(30, untouchedB.focusMinutes);
        assertEquals(5678L, untouchedB.executingStartMs);
        assertEquals(9999L, untouchedB.executingEndMs);
    }

    // ---- 边界 ----

    @Test
    public void convertToChore_nonexistentId_doesNotThrow() {
        // 不存在的 taskId：SQL UPDATE 不命中行，不应抛异常
        try {
            mTaskDao.convertToChore(99999L);
        } catch (Throwable t) {
            fail("convertToChore should be a no-op for nonexistent id, but threw: " + t);
        }
        // 表内不应凭空多一条
        assertNull(mTaskDao.getTaskByIdSync(99999L));
    }

    // ---- 辅助 ----

    private static TaskEntity newTask(String content, int quadrant, int focusMinutes) {
        TaskEntity task = new TaskEntity();
        task.content = content;
        task.quadrant = quadrant;
        task.focusMinutes = focusMinutes;
        task.createdAt = System.currentTimeMillis();
        return task;
    }
}
