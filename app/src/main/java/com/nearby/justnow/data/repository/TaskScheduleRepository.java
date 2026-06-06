package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;

import com.nearby.justnow.data.dao.TaskScheduleDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.observer.DataChangeDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务安排仓库。
 */
public class TaskScheduleRepository extends BaseRepository {

    private final TaskScheduleDao mDao;
    private final Object mSaveLock = new Object();

    // 内存缓存 —— 减少 Room 同步查询次数
    private volatile CopyOnWriteArrayList<TaskScheduleEntity> mCachedEnabledSchedules;

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
        if (mCachedEnabledSchedules != null) {
            return new ArrayList<>(mCachedEnabledSchedules);
        }
        List<TaskScheduleDao.ScheduleWithFocusMinutesFull> pojoList =
                mDao.getEnabledSchedulesWithFocusSync();
        List<TaskScheduleEntity> result = new ArrayList<>(pojoList.size());
        for (TaskScheduleDao.ScheduleWithFocusMinutesFull p : pojoList) {
            result.add(p.toEntity());
        }
        mCachedEnabledSchedules = new CopyOnWriteArrayList<>(result);
        return new ArrayList<>(result);
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
                mCachedEnabledSchedules = null;
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
                mCachedEnabledSchedules = null;
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
        mCachedEnabledSchedules = null;
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
        mCachedEnabledSchedules = null;
        notifyTaskDataChanged();
    }

    /** 批量 disable 今天已过期的 TYPE_ONCE。 */
    public void disableExpiredOnceToday(long todayStartMs) {
        assertNotMainThread();
        mDao.disableExpiredOnceToday(todayStartMs, System.currentTimeMillis());
        mCachedEnabledSchedules = null;
        notifyTaskDataChanged();
    }

    /** disable 今天已超过"应完成时间"的 TYPE_ONCE 安排。 */
    public void disableExpiredOnceSchedules() {
        assertNotMainThread();
        long now = System.currentTimeMillis();
        long todayStartMs = com.nearby.justnow.util.DateUtils.todayStartMs();
        List<TaskScheduleDao.ScheduleWithFocusMinutes> onceList = mDao.getEnabledOnceSchedulesWithFocusSync();
        if (onceList == null || onceList.isEmpty()) return;
        java.util.ArrayList<Long> expiredIds = new java.util.ArrayList<>();
        for (TaskScheduleDao.ScheduleWithFocusMinutes s : onceList) {
            if (s.scheduleValue < todayStartMs) {
                // 日期已过（昨天或更早）
                expiredIds.add(s.id);
            } else if (s.scheduleValue == todayStartMs) {
                // 今天：判断"应完成时间"是否已过
                long deadlineMs = todayStartMs + s.scheduledTime * 60000L + s.focusMinutes * 60000L;
                if (deadlineMs <= now) {
                    expiredIds.add(s.id);
                }
            }
        }
        if (!expiredIds.isEmpty()) {
            mDao.disableByIds(expiredIds, now);
            mCachedEnabledSchedules = null;
            notifyTaskDataChanged();
        }
    }

    private void notifyTaskDataChanged() {
        DataChangeDispatcher.notifyTaskDataChanged();
    }
}
