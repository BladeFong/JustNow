package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskPhotoEntity;
import com.nearby.justnow.data.entity.TaskPhotoWithTask;

import java.util.List;

/**
 * 成果照片数据库访问接口
 */
@Dao
public interface TaskPhotoDao {
    @Insert
    long insert(TaskPhotoEntity entity);

    @Delete
    void delete(TaskPhotoEntity entity);

    @Query("SELECT * FROM task_photos WHERE task_id = :taskId LIMIT 1")
    TaskPhotoEntity getPhotoForTask(long taskId);

    @Query("SELECT * FROM task_photos WHERE created_at >= :startTimeMS AND created_at <= :endTimeMS")
    List<TaskPhotoEntity> getPhotosInRange(long startTimeMS, long endTimeMS);

    @Query("SELECT * FROM task_photos")
    List<TaskPhotoEntity> getAllPhotos();

    @Query("SELECT p.*, t.content as taskContent, t.quadrant as taskQuadrant, t.icon_name as taskIconName " +
           "FROM task_photos p INNER JOIN tasks t ON p.task_id = t.id " +
           "WHERE p.created_at >= :startTimeMS AND p.created_at <= :endTimeMS")
    List<TaskPhotoWithTask> getPhotosWithTaskInRange(long startTimeMS, long endTimeMS);

    @Query("SELECT DISTINCT t.* FROM tasks t " +
           "INNER JOIN task_executions e ON t.id = e.task_id " +
           "WHERE e.status = 0 " +
           "  AND e.end_ms >= :startTimeMs AND e.end_ms <= :endTimeMs " +
           "  AND t.id NOT IN (SELECT task_id FROM task_photos)")
    List<TaskEntity> getCompletedTasksWithoutPhotosInRange(long startTimeMs, long endTimeMs);
}
