package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.util.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TaskScheduleRepository 测试 — 单次安排生命周期清理。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskScheduleRepositoryTest {

    private AppDatabase mDb;
    private TaskScheduleRepository mRepository;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepository = new TaskScheduleRepository(mDb);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    @Test
    public void disableExpiredOnceSchedules_sameDayBeforePeriodEnd_keepsEnabled() {
        int nowMinute = 600;
        int startMinute = 570;
        int endMinute = 660;
        insertGroupAndPeriod(startMinute, endMinute);

        long taskId = insertTask(20);
        long scheduleId = insertOnceSchedule(taskId, startMinute, DateUtils.todayStartMs());

        mRepository.disableExpiredOnceSchedules(System.currentTimeMillis(),
                DateUtils.todayStartMs(), nowMinute);

        assertTrue("新版安排不应按 scheduledTime + focusMinutes 过期",
                mDb.taskScheduleDao().getScheduleById(scheduleId).enabled);
    }

    @Test
    public void disableExpiredOnceSchedules_sameDayAfterPeriodEnd_disables() {
        int nowMinute = 600;
        int startMinute = 540;
        int endMinute = 600;
        insertGroupAndPeriod(startMinute, endMinute);

        long taskId = insertTask(20);
        long scheduleId = insertOnceSchedule(taskId, startMinute, DateUtils.todayStartMs());

        mRepository.disableExpiredOnceSchedules(System.currentTimeMillis(),
                DateUtils.todayStartMs(), nowMinute);

        assertFalse("所属时段结束后，TYPE_ONCE 应清理",
                mDb.taskScheduleDao().getScheduleById(scheduleId).enabled);
    }

    @Test
    public void disableExpiredOnceSchedules_previousDate_disables() {
        insertGroupAndPeriod(0, 24 * 60);
        long taskId = insertTask(20);
        long scheduleId = insertOnceSchedule(taskId, 60,
                DateUtils.todayStartMs() - 24 * 60 * 60 * 1000L);

        mRepository.disableExpiredOnceSchedules(System.currentTimeMillis(),
                DateUtils.todayStartMs(), 600);

        assertFalse("昨天及更早的 TYPE_ONCE 应清理",
                mDb.taskScheduleDao().getScheduleById(scheduleId).enabled);
    }

    private long insertTask(int focusMinutes) {
        TaskEntity task = new TaskEntity();
        task.content = "测试任务";
        task.quadrant = 0;
        task.focusMinutes = focusMinutes;
        task.createdAt = System.currentTimeMillis();
        return mDb.taskDao().insert(task);
    }

    private long insertOnceSchedule(long taskId, int scheduledTime, long dateMs) {
        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.taskId = taskId;
        schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        schedule.scheduleValue = dateMs;
        schedule.scheduledTime = scheduledTime;
        schedule.linkedPeriodGroupType = PeriodGroupType.REGULAR;
        schedule.enabled = true;
        schedule.createdAt = System.currentTimeMillis();
        schedule.updatedAt = System.currentTimeMillis();
        return mDb.taskScheduleDao().insert(schedule);
    }

    private void insertGroupAndPeriod(int startMinute, int endMinute) {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.REGULAR;
        group.enabled = true;
        group.displayOrder = 0;
        mDb.timePeriodDao().insertGroups(Arrays.asList(group));

        TimePeriodEntity period = new TimePeriodEntity();
        period.groupType = PeriodGroupType.REGULAR;
        period.nameKey = "test";
        period.startMinute = startMinute;
        period.endMinute = endMinute;
        mDb.timePeriodDao().insertPeriods(Arrays.asList(period));
    }
}
