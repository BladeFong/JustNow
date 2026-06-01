package com.nearby.justnow.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Update;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.nearby.justnow.data.entity.TaskEntity;

import java.util.List;

/**
 * 任务 DAO
 */
@Dao
public interface TaskDao {

    /** 多 token 全文检索（AND 逻辑），每个 token 匹配 content 或 detail */
    @RawQuery
    List<TaskEntity> searchTasksSync(SupportSQLiteQuery query);

    /** 按 ID 查询任务（同步） */
    @Query("SELECT * FROM tasks WHERE id = :id")
    TaskEntity getTaskByIdSync(long id);

    /** 批量按 ID 查询任务（同步） */
    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    List<TaskEntity> getTasksByIdsSync(List<Long> ids);

    /** 按四象限和标签过滤 */
    @Query("SELECT * FROM tasks WHERE is_archived = 0 AND (:quadrant = -1 OR quadrant = :quadrant) AND (:tagId = -1 OR tag_id = :tagId)")
    LiveData<List<TaskEntity>> getTasksByQuadrantAndTag(int quadrant, long tagId);

    /** 获取所有未归档任务 */
    @Query("SELECT * FROM tasks WHERE is_archived = 0 ORDER BY quadrant ASC, focus_minutes DESC")
    LiveData<List<TaskEntity>> getAllActiveTasks();

    /** 按标签获取未归档任务 */
    @Query("SELECT * FROM tasks WHERE is_archived = 0 AND tag_id = :tagId")
    LiveData<List<TaskEntity>> getTasksByTag(long tagId);

    /** 标记任务为归档 */
    @Query("UPDATE tasks SET is_archived = 1 WHERE id = :taskId")
    void archiveTask(long taskId);

    /** 同步获取全部未归档任务 */
    @Query("SELECT * FROM tasks WHERE is_archived = 0 ORDER BY quadrant ASC, focus_minutes DESC")
    List<TaskEntity> getAllActiveTasksSync();

    /** 获取当前执行中的任务。 */
    @Query("SELECT * FROM tasks WHERE is_archived = 0 AND executing_start_ms > 0 AND executing_end_ms = 0 LIMIT 1")
    TaskEntity getRunningTaskSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TaskEntity task);

    @Update
    void update(TaskEntity task);

    @Query("DELETE FROM tasks WHERE id = :taskId")
    void delete(long taskId);

    /** 设置任务开始执行 */
    @Query("UPDATE tasks SET executing_start_ms = :startMs, executing_end_ms = 0 WHERE id = :taskId")
    void setExecutingStartMs(long taskId, long startMs);

    /** 设置任务执行结束 */
    @Query("UPDATE tasks SET executing_end_ms = :endMs WHERE id = :taskId")
    void setExecutingEndMs(long taskId, long endMs);

    /** 清除执行状态（归档时使用） */
    @Query("UPDATE tasks SET executing_start_ms = 0, executing_end_ms = 0 WHERE id = :taskId")
    void clearExecutingState(long taskId);

    /** 改为琐碎任务：focus_minutes = 0，同时清空执行中状态，避免"执行中"残留。 */
    @Query("UPDATE tasks SET focus_minutes = 0, executing_start_ms = 0, executing_end_ms = 0 WHERE id = :taskId")
    void convertToChore(long taskId);
}
