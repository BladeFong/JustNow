package com.nearby.justnow.data.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskCompletionCounterDaoTest {

    private AppDatabase mDb;
    private TaskCompletionCounterDao mDao;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        mDao = mDb.taskCompletionCounterDao();
    }

    @After
    public void tearDown() {
        mDb.close();
    }

    @Test
    public void insertOrIncrement_createsNewRecord() {
        mDao.insertOrIncrement(1, "2026-W28");
        TaskCompletionCounterEntity result = mDao.getByTaskIdAndPeriodKey(1, "2026-W28");
        assertNotNull(result);
        assertEquals(1, result.completed);
    }

    @Test
    public void insertOrIncrement_incrementsExisting() {
        mDao.insertOrIncrement(1, "2026-W28");
        mDao.insertOrIncrement(1, "2026-W28");
        TaskCompletionCounterEntity result = mDao.getByTaskIdAndPeriodKey(1, "2026-W28");
        assertEquals(2, result.completed);
    }

    @Test
    public void insertOrIncrement_differentPeriods_areIndependent() {
        mDao.insertOrIncrement(1, "2026-W28");
        mDao.insertOrIncrement(1, "2026-W29");
        assertEquals(1, mDao.getByTaskIdAndPeriodKey(1, "2026-W28").completed);
        assertEquals(1, mDao.getByTaskIdAndPeriodKey(1, "2026-W29").completed);
    }

    @Test
    public void queryByTaskIdDesc_returnsOrderedByPeriodKeyDesc() {
        mDao.insertOrIncrement(1, "2026-W25");
        mDao.insertOrIncrement(2, "2026-W26");
        mDao.insertOrIncrement(1, "2026-W27");
        List<TaskCompletionCounterEntity> results = mDao.queryByTaskIdDesc(1);
        assertEquals(2, results.size());
        // 第 0 个应该 >= 第 1 个（按 period_key 降序）
        assertTrue(results.get(0).periodKey.compareTo(results.get(1).periodKey) >= 0);
    }

    @Test
    public void queryByTaskIdDesc_returnsMax10() {
        for (int i = 1; i <= 12; i++) {
            mDao.insertOrIncrement(1, "2026-W" + String.format("%02d", i));
        }
        List<TaskCompletionCounterEntity> results = mDao.queryByTaskIdDesc(1);
        assertEquals(10, results.size());
        assertEquals("2026-W12", results.get(0).periodKey); // 最新的
    }

    @Test
    public void insertOrIncrement_twiceThenDifferentTask_independent() {
        mDao.insertOrIncrement(1, "2026-07");
        mDao.insertOrIncrement(1, "2026-07");
        mDao.insertOrIncrement(2, "2026-07");
        assertEquals(2, mDao.getByTaskIdAndPeriodKey(1, "2026-07").completed);
        assertEquals(1, mDao.getByTaskIdAndPeriodKey(2, "2026-07").completed);
    }

    @Test
    public void insertOrReplace_overwritesExisting() {
        TaskCompletionCounterEntity entity = new TaskCompletionCounterEntity(1, "2026-07", 5);
        mDao.insertOrReplace(entity);
        TaskCompletionCounterEntity result = mDao.getByTaskIdAndPeriodKey(1, "2026-07");
        assertEquals(5, result.completed);
    }

    @Test
    public void deleteByTaskId_removesAllPeriods() {
        mDao.insertOrIncrement(1, "2026-W28");
        mDao.insertOrIncrement(1, "2026-W29");
        mDao.deleteByTaskId(1);
        assertNull(mDao.getByTaskIdAndPeriodKey(1, "2026-W28"));
        assertNull(mDao.getByTaskIdAndPeriodKey(1, "2026-W29"));
    }

    @Test
    public void deleteByTaskIds_removesMultipleTasks() {
        mDao.insertOrIncrement(1, "2026-07");
        mDao.insertOrIncrement(2, "2026-07");
        mDao.deleteByTaskIds(java.util.Arrays.asList(1L, 2L));
        assertNull(mDao.getByTaskIdAndPeriodKey(1, "2026-07"));
        assertNull(mDao.getByTaskIdAndPeriodKey(2, "2026-07"));
    }

    @Test
    public void monthPeriodKey_format() {
        mDao.insertOrIncrement(1, "2026-07");
        assertNotNull(mDao.getByTaskIdAndPeriodKey(1, "2026-07"));
    }

    @Test
    public void yearPeriodKey_format() {
        mDao.insertOrIncrement(1, "2026");
        assertNotNull(mDao.getByTaskIdAndPeriodKey(1, "2026"));
    }
}
