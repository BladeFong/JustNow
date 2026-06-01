package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 节假日缓存表 — 按年缓存 JSON 数据，按月标记下载成功，按假日天数判断更新。
 */
@Entity(tableName = "holiday_cache",
    indices = {@Index(value = "year", unique = true)})
public class HolidayCacheEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 年份 */
    public int year;

    /** 节假日 JSON 数据（网络可达但今年暂无数据时可为 null） */
    @ColumnInfo(name = "data_json")
    public String dataJson;

    /** 假日天数 */
    @ColumnInfo(name = "holiday_count")
    public int holidayCount;

    /** 最后更新时间戳 */
    @ColumnInfo(name = "last_updated")
    public long lastUpdated;

    /** 最近一次网络下载成功月份（yyyyMM 整数），用于控制月度下载频率 */
    @ColumnInfo(name = "last_sync_month")
    public int lastSyncMonth;
}
