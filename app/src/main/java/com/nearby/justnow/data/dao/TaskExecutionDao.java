package com.nearby.justnow.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.nearby.justnow.data.entity.TaskExecutionEntity;

import java.util.List;

/**
 * 任务执行记录 DAO
 */
@Dao
public interface TaskExecutionDao {

    /** 某日某任务的执行记录 */
    @Query("SELECT * FROM task_executions WHERE task_id = :taskId AND date = :date")
    LiveData<List<TaskExecutionEntity>> getExecutionsByTaskAndDate(long taskId, String date);

    /** 某日全部执行记录 */
    @Query("SELECT * FROM task_executions WHERE date = :date")
    LiveData<List<TaskExecutionEntity>> getExecutionsByDate(String date);

    /** 某日全部执行记录（同步，供主界面时间线合成使用）。 */
    @Query("SELECT * FROM task_executions WHERE date = :date ORDER BY start_ms ASC")
    List<TaskExecutionEntity> getExecutionsByDateSync(String date);

    /** 检查某日是否已有执行记录（用于"琐碎"任务判断是否首次执行） */
    @Query("SELECT COUNT(*) FROM task_executions WHERE task_id = :taskId AND date = :date")
    LiveData<Integer> hasExecutionToday(long taskId, String date);

    /** 某任务累计执行记录数。 */
    @Query("SELECT COUNT(*) FROM task_executions WHERE task_id = :taskId")
    int countExecutionsSync(long taskId);

    @Insert
    long insert(TaskExecutionEntity execution);

    @Update
    void update(TaskExecutionEntity execution);
}
