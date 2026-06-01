package com.nearby.justnow.data.dao;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskQuadrantDegradeDaoTest {

    private AppDatabase mDb;
    private TaskQuadrantDegradeDao mDao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mDao = mDb.taskQuadrantDegradeDao();
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    @Test
    public void insertAndQueryAll() {
        TaskQuadrantDegradeEntity entity = newEntity(1, 0, System.currentTimeMillis() + 86400000L);
        mDao.insert(entity);

        List<TaskQuadrantDegradeEntity> all = mDao.queryAll();
        assertEquals(1, all.size());
        assertEquals(1, all.get(0).taskId);
        assertEquals(0, all.get(0).originalQuadrant);
    }

    @Test
    public void insert_replaceOnConflict() {
        TaskQuadrantDegradeEntity e1 = newEntity(1, 0, System.currentTimeMillis() + 86400000L);
        mDao.insert(e1);

        TaskQuadrantDegradeEntity e2 = newEntity(1, 2, System.currentTimeMillis() + 172800000L);
        mDao.insert(e2);

        List<TaskQuadrantDegradeEntity> all = mDao.queryAll();
        assertEquals(1, all.size());
        assertEquals(2, all.get(0).originalQuadrant);
    }

    @Test
    public void deleteByTaskId() {
        mDao.insert(newEntity(1, 0, System.currentTimeMillis() + 86400000L));
        mDao.insert(newEntity(2, 1, System.currentTimeMillis() + 86400000L));

        mDao.deleteByTaskId(1);

        List<TaskQuadrantDegradeEntity> all = mDao.queryAll();
        assertEquals(1, all.size());
        assertEquals(2, all.get(0).taskId);
    }

    @Test
    public void deleteByTaskIds() {
        mDao.insert(newEntity(1, 0, System.currentTimeMillis() + 86400000L));
        mDao.insert(newEntity(2, 1, System.currentTimeMillis() + 86400000L));
        mDao.insert(newEntity(3, 2, System.currentTimeMillis() + 86400000L));

        mDao.deleteByTaskIds(java.util.Arrays.asList(1L, 3L));

        List<TaskQuadrantDegradeEntity> all = mDao.queryAll();
        assertEquals(1, all.size());
        assertEquals(2, all.get(0).taskId);
    }

    @Test
    public void queryAll_empty() {
        List<TaskQuadrantDegradeEntity> all = mDao.queryAll();
        assertTrue(all.isEmpty());
    }

    private static TaskQuadrantDegradeEntity newEntity(long taskId, int originalQuadrant, long recoverMs) {
        TaskQuadrantDegradeEntity entity = new TaskQuadrantDegradeEntity();
        entity.taskId = taskId;
        entity.originalQuadrant = originalQuadrant;
        entity.recoverMs = recoverMs;
        return entity;
    }
}
