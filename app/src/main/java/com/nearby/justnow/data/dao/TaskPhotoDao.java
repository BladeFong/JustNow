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

    /** 查某任务已拍张数 */
    @Query("SELECT COUNT(*) FROM task_photos WHERE task_id = :taskId")
    int getPhotoCountForTask(long taskId);

    /** 查某任务所有照片（按时间正序，全屏划动用） */
    @Query("SELECT * FROM task_photos WHERE task_id = :taskId ORDER BY created_at ASC")
    List<TaskPhotoEntity> getPhotosForTask(long taskId);

    /** 查某任务在时间范围内的照片（成果墙全屏划动用） */
    @Query("SELECT * FROM task_photos WHERE task_id = :taskId "
         + "AND created_at >= :startMs AND created_at <= :endMs "
         + "ORDER BY created_at ASC")
    List<TaskPhotoEntity> getPhotosForTaskInRange(long taskId, long startMs, long endMs);

    /** 指定时间范围内每任务每天首张照片（带任务信息，花瓣计花 + 成果墙用） */
    @Query("SELECT p.*, t.content as taskContent, t.quadrant as taskQuadrant, t.icon_name as taskIconName " +
           "FROM task_photos p INNER JOIN tasks t ON p.task_id = t.id " +
           "WHERE p.id IN (" +
           "  SELECT MIN(p2.id) FROM task_photos p2 " +
           "  WHERE p2.created_at >= :startTimeMs AND p2.created_at <= :endTimeMs " +
           "  GROUP BY p2.task_id, date(p2.created_at / 1000, 'unixepoch')" +
           ") ORDER BY p.created_at ASC")
    List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long startTimeMs, long endTimeMs);

    /** 当天可拍照任务：已完成（今天有 execution 且 status=0）+ 进行中，按时间倒序 */
    @Query("SELECT DISTINCT t.* FROM tasks t " +
           "LEFT JOIN task_executions e ON t.id = e.task_id AND e.status = 0 " +
           "WHERE t.is_archived = 0 AND (" +
           "  (e.end_ms >= :todayStartMs AND e.end_ms <= :todayEndMs) " +
           "  OR (t.executing_start_ms > 0 AND t.executing_end_ms = 0)" +
           ") ORDER BY COALESCE(e.end_ms, t.executing_start_ms) DESC")
    List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs);
}
