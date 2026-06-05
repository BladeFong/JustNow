package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;

import com.nearby.justnow.data.dao.TaskScheduleDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.observer.DataChangeDispatcher;

import java.util.List;

/**
 * 任务安排仓库。
 */
public class TaskScheduleRepository extends BaseRepository {

    private final TaskScheduleDao mDao;
    private final Object mSaveLock = new Object();

    public TaskScheduleRepository(AppDatabase db) {
        super(db);
        mDao = db.taskScheduleDao();
    }

    public LiveData<TaskScheduleEntity> getActiveScheduleLive(long taskId) {
        return mDao.getActiveScheduleLive(taskId);
    }

    public LiveData<List<TaskScheduleEntity>> getAllEnabledSchedulesLive() {
        return mDao.getAllEnabledSchedulesLive();
    }

    public TaskScheduleEntity getActiveScheduleSync(long taskId) {
        return mDao.getActiveScheduleSync(taskId);
    }

    public TaskScheduleEntity getScheduleById(long scheduleId) {
        return mDao.getScheduleById(scheduleId);
    }

    public List<TaskScheduleEntity> getAllEnabledSchedulesSync() {
        return mDao.getAllEnabledSchedulesSync();
    }

    public List<TaskScheduleEntity> getAllSchedulesSync() {
        return mDao.getAllSchedulesSync();
    }

    /** 保存安排。若同任务已有 enabled 安排，则复用原记录更新。 */
    public void insert(TaskScheduleEntity schedule, Runnable onComplete) {
        mDb.runInBackground(() -> {
            synchronized (mSaveLock) {
                long now = System.currentTimeMillis();
                schedule.enabled = true;
                schedule.updatedAt = now;
                schedule.disableReason = null;

                TaskScheduleEntity existing = mDao.getScheduleByTaskIdSync(schedule.taskId);
                if (existing != null) {
                    schedule.id = existing.id;
                    schedule.createdAt = existing.createdAt;
                    mDao.update(schedule);
                } else {
                    if (schedule.createdAt <= 0) schedule.createdAt = now;
                    schedule.id = mDao.insert(schedule);
                }
            }
            try {
                if (onComplete != null) onComplete.run();
            } finally {
                notifyTaskDataChanged();
            }
        });
    }

    /** 更新已有安排。 */
    public void update(TaskScheduleEntity schedule, Runnable onComplete) {
        mDb.runInBackground(() -> {
            synchronized (mSaveLock) {
                long now = System.currentTimeMillis();
                TaskScheduleEntity existing = mDao.getScheduleById(schedule.id);
                if (existing != null && schedule.createdAt <= 0) {
                    schedule.createdAt = existing.createdAt;
                }
                schedule.enabled = true;
                schedule.disableReason = null;
                schedule.updatedAt = now;
                mDao.update(schedule);
            }
            try {
                if (onComplete != null) onComplete.run();
            } finally {
                notifyTaskDataChanged();
            }
        });
    }

    /** 单条 disable（带原因）。 */
    public void disableSchedule(long scheduleId, String reason, Runnable onComplete) {
        mDb.runInBackground(() -> {
            disableScheduleSync(scheduleId, reason);
            if (onComplete != null) onComplete.run();
        });
    }

    public void disableScheduleSync(long scheduleId, String reason) {
        mDao.disableSchedule(scheduleId, reason, System.currentTimeMillis());
        notifyTaskDataChanged();
    }

    /** disable 某任务的全部有效安排（不带原因，兼容旧调用）。 */
    public void disableForTask(long taskId, Runnable onComplete) {
        mDb.runInBackground(() -> {
            disableForTaskSync(taskId);
            if (onComplete != null) onComplete.run();
        });
    }

    public void disableForTaskSync(long taskId) {
        mDao.disableForTask(taskId, System.currentTimeMillis());
        notifyTaskDataChanged();
    }

    /** 批量 disable 今天已过期的 TYPE_ONCE。 */
    public void disableExpiredOnceToday(long todayStartMs) {
        assertNotMainThread();
        mDao.disableExpiredOnceToday(todayStartMs, System.currentTimeMillis());
        notifyTaskDataChanged();
    }

    private void notifyTaskDataChanged() {
        DataChangeDispatcher.notifyTaskDataChanged();
    }
}
