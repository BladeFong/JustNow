package com.nearby.justnow.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 时间段组配置表。
 */
@Entity(tableName = "time_period_groups")
public class TimePeriodGroupEntity {

    /** 固定预设组类型，见 PeriodGroupType。 */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "group_type")
    public String groupType = "";

    /** 设置页展示顺序。 */
    @ColumnInfo(name = "display_order")
    public int displayOrder;

    /** 是否启用。常规组始终视为启用。 */
    @ColumnInfo(name = "enabled", defaultValue = "0")
    public boolean enabled;

    /** 是否可跟随节假日数据自动命中。 */
    @ColumnInfo(name = "use_holiday_data", defaultValue = "0")
    public boolean useHolidayData;

    /** 自定义命中范围开始，格式 MM-dd。 */
    @ColumnInfo(name = "start_month_day")
    public String startMonthDay;

    /** 自定义命中范围结束，格式 MM-dd。 */
    @ColumnInfo(name = "end_month_day")
    public String endMonthDay;

    /** 上次实际修改时间。 */
    @ColumnInfo(name = "last_edited_at", defaultValue = "0")
    public long lastEditedAt;

    /** 最近复核过的周期 key，例如 summer-2026、winter-2025-2026。 */
    @ColumnInfo(name = "last_reviewed_key")
    public String lastReviewedKey;
}
