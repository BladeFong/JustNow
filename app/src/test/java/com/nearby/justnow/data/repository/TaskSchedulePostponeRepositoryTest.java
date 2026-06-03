package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TaskSchedulePostponeRepository 测试 — 延迟记录写入与当天门控逻辑。
 * 使用 Room 内存数据库 + Robolectric。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskSchedulePostponeRepositoryTest {

    private AppDatabase mDb;
    private TaskSchedulePostponeRepository mRepository;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepository = new TaskSchedulePostponeRepository(mDb);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    /** 插入一个任务并返回其 ID。 */
    private long insertTask() {
        TaskEntity task = new TaskEntity();
        task.content = "测试任务";
        task.quadrant = 0;
        task.focusMinutes = 30;
        task.createdAt = System.currentTimeMillis();
        return mDb.taskDao().insert(task);
    }

    /** 插入一个安排并返回其 ID。 */
    private long insertSchedule(long taskId) {
        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.taskId = taskId;
        schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        schedule.scheduledTime = 600; // 10:00
        schedule.enabled = true;
        schedule.createdAt = System.currentTimeMillis();
        schedule.updatedAt = System.currentTimeMillis();
        return mDb.taskScheduleDao().insert(schedule);
    }

    // =====================================================================
    // recordPostpone
    // =====================================================================

    @Test
    public void recordPostpone_returnsValidId() {
        long taskId = insertTask();
        long scheduleId = insertSchedule(taskId);
        long dateMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        long id = mRepository.recordPostpone(scheduleId, taskId, 0, 30, dateMs);
        assertTrue("插入后应返回有效 ID（> 0）", id > 0);
    }

    @Test
    public void recordPostpone_recordedData_isQueryableByGate() {
        long taskId = insertTask();
        long scheduleId = insertSchedule(taskId);
        long todayMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        mRepository.recordPostpone(scheduleId, taskId, 0, 30, todayMs);
        assertTrue("写入后同一天门控应为 true", mRepository.hasPostponedToday(scheduleId, todayMs));
    }

    // =====================================================================
    // hasPostponedToday — 门控
    // =====================================================================

    @Test
    public void hasPostponedToday_noRecord_returnsFalse() {
        long taskId = insertTask();
        long scheduleId = insertSchedule(taskId);
        long todayMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        assertFalse("无任何记录时应返回 false", mRepository.hasPostponedToday(scheduleId, todayMs));
    }

    @Test
    public void hasPostponedToday_differentDay_returnsFalse() {
        long taskId = insertTask();
        long scheduleId = insertSchedule(taskId);
        long todayMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        mRepository.recordPostpone(scheduleId, taskId, 0, 30, todayMs);

        // 昨天查
        long yesterdayMs = todayMs - 24 * 60 * 60 * 1000L;
        assertFalse("昨天日期应不受今天门控限制", mRepository.hasPostponedToday(scheduleId, yesterdayMs));

        // 明天查
        long tomorrowMs = todayMs + 24 * 60 * 60 * 1000L;
        assertFalse("明天日期应不受今天门控限制", mRepository.hasPostponedToday(scheduleId, tomorrowMs));
    }

    @Test
    public void hasPostponedToday_differentSchedule_returnsFalse() {
        long taskId = insertTask();
        long scheduleIdA = insertSchedule(taskId);

        // 第二个安排，不同 task
        long taskId2 = insertTask();
        long scheduleIdB = insertSchedule(taskId2);

        long todayMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        mRepository.recordPostpone(scheduleIdA, taskId, 0, 30, todayMs);

        assertTrue("安排 A 门控应为 true", mRepository.hasPostponedToday(scheduleIdA, todayMs));
        assertFalse("安排 B 无记录，门控应为 false", mRepository.hasPostponedToday(scheduleIdB, todayMs));
    }

    @Test
    public void hasPostponedToday_afterMultipleRecords_stillTrue() {
        long taskId = insertTask();
        long scheduleId = insertSchedule(taskId);
        long todayMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        mRepository.recordPostpone(scheduleId, taskId, 0, 15, todayMs);
        mRepository.recordPostpone(scheduleId, taskId, 0, 30, todayMs);

        assertTrue("多条记录后门控仍应为 true", mRepository.hasPostponedToday(scheduleId, todayMs));
    }

    // =====================================================================
    // todayStartMs
    // =====================================================================

    @Test
    public void todayStartMs_returnsMidnight() {
        long midnightMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(midnightMs);
        assertEquals("小时应为 0", 0, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals("分钟应为 0", 0, cal.get(Calendar.MINUTE));
        assertEquals("秒应为 0", 0, cal.get(Calendar.SECOND));
        assertEquals("毫秒应为 0", 0, cal.get(Calendar.MILLISECOND));
    }

    @Test
    public void todayStartMs_positiveValue() {
        long midnightMs = com.nearby.justnow.util.DateUtils.todayStartMs();

        assertTrue("当天 00:00 毫秒值应 >= 0", midnightMs >= 0);
    }

    @Test
    public void todayStartMs_idempotent() {
        long first = com.nearby.justnow.util.DateUtils.todayStartMs();
        long second = com.nearby.justnow.util.DateUtils.todayStartMs();

        assertEquals("连续调用应返回相同值", first, second);
    }

    @Test
    public void todayStartMs_beforeEndOfDay() {
        long midnightMs = com.nearby.justnow.util.DateUtils.todayStartMs();
        long nowMs = System.currentTimeMillis();

        long endOfDayMs = midnightMs + 24 * 60 * 60 * 1000L;
        assertTrue("当前时间应在当天范围内", nowMs >= midnightMs);
        assertTrue("当前时间应早于当天结束", nowMs < endOfDayMs);
    }
}
