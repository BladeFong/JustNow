package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;

import java.util.List;

@Dao
public interface TaskQuadrantDegradeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TaskQuadrantDegradeEntity entity);

    @Query("DELETE FROM task_quadrant_degrade WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);

    @Query("DELETE FROM task_quadrant_degrade WHERE task_id IN (:taskIds)")
    void deleteByTaskIds(List<Long> taskIds);

    @Query("SELECT * FROM task_quadrant_degrade")
    List<TaskQuadrantDegradeEntity> queryAll();
}
