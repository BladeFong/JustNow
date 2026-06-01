package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 时间段配置表
 * 组内时段按时间范围排序，不依赖固定 5 段编号。
 */
@Entity(
    tableName = "time_periods",
    indices = @Index("group_type")
)
public class TimePeriodEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 所属时间段组类型，见 PeriodGroupType。 */
    @ColumnInfo(name = "group_type")
    public String groupType;

    /** 时段名称 key，见 PeriodNameKey。 */
    @ColumnInfo(name = "name_key")
    public String nameKey;

    /** 同起点时的展示兜底顺序，主要顺序仍按 startMinute/endMinute。 */
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    public int sortOrder;

    /** 开始时间（一天中的分钟数，如 08:30 = 8*60+30 = 510） */
    @ColumnInfo(name = "start_minute")
    public int startMinute;

    /** 结束时间（一天中的分钟数） */
    @ColumnInfo(name = "end_minute")
    public int endMinute;

    /** 是否反转四象限排序。 */
    @ColumnInfo(name = "reverse_quadrant", defaultValue = "0")
    public boolean reverseQuadrant;

    /** 是否优先展示琐碎任务。 */
    @ColumnInfo(name = "prefer_chore", defaultValue = "0")
    public boolean preferChore;

    /** 当前时段是否允许套用该组的优先标签规则。 */
    @ColumnInfo(name = "priority_eligible", defaultValue = "0")
    public boolean priorityEligible;
}
