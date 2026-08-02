package com.nearby.justnow.data.repository;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskPhotoEntity;
import com.nearby.justnow.data.entity.TaskPhotoWithTask;

import java.util.List;

/**
 * 任务成果照片数据仓库
 */
public class TaskPhotoRepository extends BaseRepository {

    public TaskPhotoRepository(AppDatabase db) {
        super(db);
    }

    /**
     * 绑定照片Uri到指定任务，保存拍摄关联
     */
    public long bindPhotoToTask(long taskId, String photoUri) {
        TaskPhotoEntity entity = new TaskPhotoEntity();
        entity.taskId = taskId;
        entity.photoUri = photoUri;
        entity.createdAt = System.currentTimeMillis();
        return mDb.taskPhotoDao().insert(entity);
    }
    /**
     * 从数据库中删除照片记录
     */
    public void deletePhoto(TaskPhotoEntity entity) {
        mDb.taskPhotoDao().delete(entity);
    }
    /**
     * 获取指定本周内的所有成果照片关联数据
     */
    public List<TaskPhotoEntity> getPhotosInWeek(long mondayStartMs) {
        long sundayEndMs = mondayStartMs + (7 * 24 * 60 * 60 * 1000L) - 1;
        return mDb.taskPhotoDao().getPhotosInRange(mondayStartMs, sundayEndMs);
    }

    /**
     * 获取指定本周内的所有成果照片关联数据（带任务信息）
     */
    public List<TaskPhotoWithTask> getPhotosWithTaskInWeek(long mondayStartMs) {
        long sundayEndMs = mondayStartMs + (7 * 24 * 60 * 60 * 1000L) - 1;
        return mDb.taskPhotoDao().getPhotosWithTaskInRange(mondayStartMs, sundayEndMs);
    }

    /**
     * 获取指定时间范围内的所有成果照片关联数据（带任务信息）
     */
    public List<TaskPhotoWithTask> getPhotosWithTaskInRange(long startMs, long endMs) {
        return mDb.taskPhotoDao().getPhotosWithTaskInRange(startMs, endMs);
    }

    /**
     * 查任务已拍张数
     */
    public int getPhotoCountForTask(long taskId) {
        return mDb.taskPhotoDao().getPhotoCountForTask(taskId);
    }

    /**
     * 查任务在时间范围内已拍张数
     */
    public int getPhotoCountForTaskInRange(long taskId, long startMs, long endMs) {
        return mDb.taskPhotoDao().getPhotoCountForTaskInRange(taskId, startMs, endMs);
    }

    /**
     * 是否已拍满 5 张
     */
    public boolean isPhotoLimitReached(long taskId) {
        return getPhotoCountForTask(taskId) >= 5;
    }

    /**
     * 查任务所有照片（按时间正序，全屏划动用）
     */
    public List<TaskPhotoEntity> getPhotosForTask(long taskId) {
        return mDb.taskPhotoDao().getPhotosForTask(taskId);
    }

    /**
     * 查任务在时间范围内的照片（成果墙全屏划动用）
     */
    public List<TaskPhotoEntity> getPhotosForTaskInRange(long taskId, long startMs, long endMs) {
        return mDb.taskPhotoDao().getPhotosForTaskInRange(taskId, startMs, endMs);
    }

    /**
     * 指定时间范围内每任务首张照片（带任务信息）
     */
    public List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long startMs, long endMs) {
        return mDb.taskPhotoDao().getFirstPhotoPerTaskInRange(startMs, endMs);
    }

    /**
     * 当天可拍照任务：已完成（今天有 execution）+ 进行中，按时间倒序
     */
    public List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs) {
        List<TaskEntity> completed = mDb.taskPhotoDao().getTodayCompletedTasks(todayStartMs, todayEndMs);
        TaskEntity running = mDb.taskDao().getRunningTaskSync();
        java.util.LinkedHashMap<Long, TaskEntity> map = new java.util.LinkedHashMap<>();
        for (TaskEntity t : completed) map.put(t.id, t);
        if (running != null) map.putIfAbsent(running.id, running);
        return new java.util.ArrayList<>(map.values());
    }

}
