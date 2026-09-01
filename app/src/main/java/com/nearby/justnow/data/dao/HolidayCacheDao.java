package com.nearby.justnow.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import java.util.List;

/**
 * 节假日缓存 DAO
 */
@Dao
public interface HolidayCacheDao {

    /** 获取某年的缓存 */
    @Query("SELECT * FROM holiday_cache WHERE year = :year LIMIT 1")
    LiveData<HolidayCacheEntity> getByYear(int year);

    /** 同步获取某年缓存（非 LiveData，供后台任务使用） */
    @Query("SELECT * FROM holiday_cache WHERE year = :year LIMIT 1")
    HolidayCacheEntity getByYearSync(int year);

    /** 同步获取所有年份的节假日缓存（供多用户数据回迁合并使用） */
    @Query("SELECT * FROM holiday_cache")
    List<HolidayCacheEntity> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(HolidayCacheEntity cache);

    /** 仅获取缓存假日天数（不加载 JSON）。无记录时返回 null。 */
    @Query("SELECT holiday_count FROM holiday_cache WHERE year = :year LIMIT 1")
    Integer getHolidayCountByYear(int year);

    /** 仅获取上次下载成功月份（不加载 JSON）。无记录时返回 null。 */
    @Query("SELECT last_sync_month FROM holiday_cache WHERE year = :year LIMIT 1")
    Integer getLastSyncMonthByYear(int year);

    /** 仅更新月份标记，不触动 data_json 和 holiday_count。 */
    @Query("UPDATE holiday_cache SET last_sync_month = :lastSyncMonth, last_updated = :lastUpdated WHERE year = :year")
    void updateSyncMonth(int year, int lastSyncMonth, long lastUpdated);

    /**
     * 原子化的条件写入：仅在新数据假日天数更多时才覆盖 JSON 数据；
     * 月份标记总是更新（如有旧记录）。
     */
    @Transaction
    default void upsertWithCountCheck(HolidayCacheEntity entity, int month) {
        if (entity.dataJson != null && !entity.dataJson.isEmpty()) {
            Integer oldCount = getHolidayCountByYear(entity.year);
            if (oldCount != null && entity.holidayCount <= oldCount) {
                updateSyncMonth(entity.year, month, System.currentTimeMillis());
            } else {
                entity.lastUpdated = System.currentTimeMillis();
                entity.lastSyncMonth = month;
                insert(entity);
            }
        } else {
            Integer oldCount = getHolidayCountByYear(entity.year);
            if (oldCount != null) {
                updateSyncMonth(entity.year, month, System.currentTimeMillis());
            }
        }
    }
}
