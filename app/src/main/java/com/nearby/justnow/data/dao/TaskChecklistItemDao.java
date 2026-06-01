package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskChecklistItem;

import java.util.List;

/**
 * todo 勾选清单 DAO
 */
@Dao
public interface TaskChecklistItemDao {

    @Query("SELECT * FROM task_checklist_items WHERE task_id = :taskId ORDER BY order_index")
    List<TaskChecklistItem> getByTaskIdSync(long taskId);

    @Query("SELECT COUNT(*) FROM task_checklist_items WHERE task_id = :taskId AND (checked = 1 OR crossed_out = 1)")
    int countHasStateSync(long taskId);

    @Query("UPDATE task_checklist_items SET checked = 0, crossed_out = 0 WHERE task_id = :taskId")
    void resetAllByTaskId(long taskId);

    @Insert
    void insertAll(List<TaskChecklistItem> items);

    @Query("DELETE FROM task_checklist_items WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);

    @androidx.room.Update
    void update(TaskChecklistItem item);
}
