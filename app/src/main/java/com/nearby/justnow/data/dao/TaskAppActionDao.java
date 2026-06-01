package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskAppAction;

import java.util.List;

/**
 * APP 跳转列表 DAO
 */
@Dao
public interface TaskAppActionDao {

    @Query("SELECT * FROM task_app_actions WHERE task_id = :taskId ORDER BY order_index")
    List<TaskAppAction> getByTaskIdSync(long taskId);

    @Insert
    void insertAll(List<TaskAppAction> actions);

    @Query("DELETE FROM task_app_actions WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);
}
