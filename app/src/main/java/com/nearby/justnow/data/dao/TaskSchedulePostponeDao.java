package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskSchedulePostponeEntity;

/**
 * 安排提醒延迟记录 DAO。
 */
@Dao
public interface TaskSchedulePostponeDao {

    @Insert
    long insert(TaskSchedulePostponeEntity entity);

    /** 检查某安排当天是否已延迟过（门控用）。 */
    @Query("SELECT COUNT(*) FROM task_schedule_postpones WHERE schedule_id = :scheduleId AND date_ms = :dateMs")
    int countByScheduleAndDate(long scheduleId, long dateMs);
}
