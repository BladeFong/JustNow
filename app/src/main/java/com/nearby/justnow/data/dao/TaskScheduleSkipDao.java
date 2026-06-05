package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nearby.justnow.data.entity.TaskScheduleSkipEntity;

import java.util.List;

@Dao
public interface TaskScheduleSkipDao {

    @Insert
    long insert(TaskScheduleSkipEntity skip);

    @Query("SELECT date_ms FROM task_schedule_skips WHERE schedule_id = :scheduleId")
    List<Long> getSkippedDates(long scheduleId);

    @Query("DELETE FROM task_schedule_skips WHERE schedule_id = :scheduleId AND date_ms = :dateMs")
    void deleteSkip(long scheduleId, long dateMs);
}
