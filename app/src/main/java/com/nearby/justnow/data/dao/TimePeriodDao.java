package com.nearby.justnow.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;

import java.util.List;

/**
 * 时间段配置 DAO
 */
@Dao
public interface TimePeriodDao {

    @Query("SELECT * FROM time_period_groups ORDER BY display_order ASC")
    LiveData<List<TimePeriodGroupEntity>> getAllGroups();

    @Query("SELECT * FROM time_period_groups ORDER BY display_order ASC")
    List<TimePeriodGroupEntity> getAllGroupsSync();

    @Query("SELECT * FROM time_period_groups WHERE group_type = :groupType LIMIT 1")
    TimePeriodGroupEntity getGroupSync(String groupType);

    @Query("SELECT * FROM time_periods WHERE group_type = :groupType " +
           "ORDER BY start_minute ASC, end_minute ASC, sort_order ASC")
    LiveData<List<TimePeriodEntity>> getPeriodsByGroup(String groupType);

    @Query("SELECT * FROM time_periods ORDER BY group_type ASC, start_minute ASC, end_minute ASC, sort_order ASC")
    LiveData<List<TimePeriodEntity>> getAllPeriods();

    @Query("SELECT * FROM time_periods WHERE group_type = :groupType " +
           "ORDER BY start_minute ASC, end_minute ASC, sort_order ASC")
    List<TimePeriodEntity> getPeriodsByGroupSync(String groupType);

    @Query("SELECT * FROM time_periods ORDER BY group_type ASC, start_minute ASC, end_minute ASC, sort_order ASC")
    List<TimePeriodEntity> getAllPeriodsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertGroups(List<TimePeriodGroupEntity> groups);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPeriods(List<TimePeriodEntity> periods);

    @Query("SELECT COUNT(*) FROM time_periods")
    int count();

    @Update
    void updateGroup(TimePeriodGroupEntity group);

    @Update
    void update(TimePeriodEntity period);
}
