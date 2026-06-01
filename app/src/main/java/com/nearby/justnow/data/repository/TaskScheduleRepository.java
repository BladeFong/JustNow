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

    public TaskScheduleRepository(AppDatabase db) {
        super(db);
        mDao = db.taskScheduleDao();
    }

    public LiveData<TaskScheduleEntity> getActiveScheduleLive(long taskId) {
        return mDao.getActiveScheduleLive(taskId);
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

    /** 插入新安排。 */
    public void insert(TaskScheduleEntity schedule, Runnable onComplete) {
        mDb.runInBackground(() -> {
            mDb.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                schedule.enabled = true;
                if (schedule.createdAt <= 0) schedule.createdAt = now;
                schedule.updatedAt = now;
                // 清理同 taskId 的 disabled 残留，避免撞 task_id UNIQUE
                mDao.deleteDisabledByTaskId(schedule.taskId);
                mDao.insert(schedule);
            });
            notifyTaskDataChanged();
            if (onComplete != null) onComplete.run();
        });
    }

    /** 更新已有安排。 */
    public void update(TaskScheduleEntity schedule, Runnable onComplete) {
        mDb.runInBackground(() -> {
            schedule.updatedAt = System.currentTimeMillis();
            mDao.update(schedule);
            notifyTaskDataChanged();
            if (onComplete != null) onComplete.run();
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
