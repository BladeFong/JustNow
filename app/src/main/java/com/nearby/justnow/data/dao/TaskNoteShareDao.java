package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskNoteShare;

import java.util.List;

/**
 * 笔记分享列表 DAO
 */
@Dao
public interface TaskNoteShareDao {

    @Query("SELECT * FROM task_note_shares WHERE task_id = :taskId ORDER BY order_index")
    List<TaskNoteShare> getByTaskIdSync(long taskId);

    @Insert
    void insertAll(List<TaskNoteShare> shares);

    @Query("DELETE FROM task_note_shares WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);
}
