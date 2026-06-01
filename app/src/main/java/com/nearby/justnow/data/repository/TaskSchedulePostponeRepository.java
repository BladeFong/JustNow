package com.nearby.justnow.data.repository;

import com.nearby.justnow.data.dao.TaskSchedulePostponeDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskSchedulePostponeEntity;

import java.util.Calendar;

/**
 * 安排提醒延迟记录仓库。
 */
public class TaskSchedulePostponeRepository extends BaseRepository {

    private final TaskSchedulePostponeDao mDao;

    public TaskSchedulePostponeRepository(AppDatabase db) {
        super(db);
        mDao = db.taskSchedulePostponeDao();
    }

    /** 记录一次延迟。 */
    public long recordPostpone(long scheduleId, long taskId, long blockedByTaskId,
                               int postponeMinutes, long dateMs) {
        assertNotMainThread();
        TaskSchedulePostponeEntity entity = new TaskSchedulePostponeEntity();
        entity.scheduleId = scheduleId;
        entity.taskId = taskId;
        entity.blockedByTaskId = blockedByTaskId;
        entity.postponeMinutes = postponeMinutes;
        entity.dateMs = dateMs;
        entity.createdAt = System.currentTimeMillis();
        return mDao.insert(entity);
    }

    /** 当天是否已延迟过。 */
    public boolean hasPostponedToday(long scheduleId, long dateMs) {
        assertNotMainThread();
        return mDao.countByScheduleAndDate(scheduleId, dateMs) > 0;
    }

    /** 获取当天 00:00 毫秒值。 */
    public static long todayStartMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
