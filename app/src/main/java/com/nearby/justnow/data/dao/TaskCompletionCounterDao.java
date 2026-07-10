package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;

import java.util.List;

@Dao
public interface TaskCompletionCounterDao {

    @Query("SELECT * FROM task_completion_counter WHERE task_id = :taskId AND period_key = :periodKey")
    TaskCompletionCounterEntity getByTaskIdAndPeriodKey(long taskId, String periodKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(TaskCompletionCounterEntity entity);

    @Query("SELECT * FROM task_completion_counter WHERE task_id = :taskId ORDER BY period_key DESC LIMIT 10")
    List<TaskCompletionCounterEntity> queryByTaskIdDesc(long taskId);

    @Query("DELETE FROM task_completion_counter WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);

    @Query("DELETE FROM task_completion_counter WHERE task_id IN (:taskIds)")
    void deleteByTaskIds(List<Long> taskIds);

    /** 插入或累加：已有记录则 completed+1，否则新建 completed=1 */
    @Transaction
    default void insertOrIncrement(long taskId, String periodKey) {
        TaskCompletionCounterEntity existing = getByTaskIdAndPeriodKey(taskId, periodKey);
        if (existing != null) {
            existing.completed++;
            insertOrReplace(existing);
        } else {
            insertOrReplace(new TaskCompletionCounterEntity(taskId, periodKey, 1));
        }
    }
}
