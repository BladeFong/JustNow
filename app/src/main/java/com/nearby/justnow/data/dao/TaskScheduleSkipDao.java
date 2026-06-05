package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskScheduleSkipEntity;

@Dao
public interface TaskScheduleSkipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TaskScheduleSkipEntity skip);

    @Query("SELECT last_skipped_date_ms FROM task_schedule_skips WHERE schedule_id = :scheduleId")
    Long getLastSkippedDateMs(long scheduleId);

    @Query("SELECT * FROM task_schedule_skips WHERE schedule_id = :scheduleId")
    TaskScheduleSkipEntity getSkip(long scheduleId);
}
