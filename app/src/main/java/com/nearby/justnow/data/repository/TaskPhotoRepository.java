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
     * 获取指定时间范围内所有已完成但未拍照的任务
     */
    public List<TaskEntity> getCompletedTasksWithoutPhotos(long startTimeMs, long endTimeMs) {
        return mDb.taskPhotoDao().getCompletedTasksWithoutPhotosInRange(startTimeMs, endTimeMs);
    }

}
