package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;

import com.nearby.justnow.data.dao.TaskExecutionDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskExecutionEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 任务执行记录仓库
 */
public class TaskExecutionRepository extends BaseRepository {

    private final TaskExecutionDao mDao;
    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public TaskExecutionRepository(AppDatabase db) {
        super(db);
        this.mDao = db.taskExecutionDao();
    }

    /** 今日是否已有执行记录（用于"琐碎"任务判断首次弹窗） */
    public LiveData<Integer> hasExecutionToday(long taskId) {
        String today = LocalDate.now().format(sDateFormat);
        return mDao.hasExecutionToday(taskId, today);
    }

    /** 记录执行完成 */
    public void recordComplete(long taskId, int actualMinutes, Runnable onComplete) {
        recordComplete(taskId, 0, System.currentTimeMillis(), actualMinutes, onComplete);
    }

    /** 记录执行完成。 */
    public void recordComplete(long taskId, long startMs, long endMs, int actualMinutes,
                               Runnable onComplete) {
        mDb.runInBackground(() -> {
            recordCompleteSync(taskId, startMs, endMs, actualMinutes);
            if (onComplete != null) onComplete.run();
        });
    }

    public void recordCompleteSync(long taskId, long startMs, long endMs, int actualMinutes) {
        TaskExecutionEntity entity = new TaskExecutionEntity();
        entity.taskId = taskId;
        entity.date = formatDate(endMs);
        entity.startMs = startMs;
        entity.endMs = endMs;
        entity.status = 0; // 已完成
        entity.actualMinutes = actualMinutes;
        mDao.insert(entity);
    }

    /** 记录调度执行 */
    public void recordScheduled(long taskId, String scheduledTime, String periodSlot,
                                Runnable onComplete) {
        mDb.runInBackground(() -> {
            TaskExecutionEntity entity = new TaskExecutionEntity();
            entity.taskId = taskId;
            entity.date = LocalDate.now().format(sDateFormat);
            entity.scheduledTime = scheduledTime;
            entity.periodSlot = periodSlot;
            entity.startMs = 0;
            entity.endMs = 0;
            entity.status = 0;
            mDao.insert(entity);
            if (onComplete != null) onComplete.run();
        });
    }

    public List<TaskExecutionEntity> getTodayExecutionsSync() {
        return mDao.getExecutionsByDateSync(LocalDate.now().format(sDateFormat));
    }

    public int countExecutionsSync(long taskId) {
        return mDao.countExecutionsSync(taskId);
    }

    private static String formatDate(long endMs) {
        if (endMs <= 0) return LocalDate.now().format(sDateFormat);
        return Instant.ofEpochMilli(endMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(sDateFormat);
    }
}
