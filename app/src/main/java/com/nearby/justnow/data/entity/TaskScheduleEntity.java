package com.nearby.justnow.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 任务安排表。每个任务只保留一条有效安排（task_id UNIQUE）。
 */
@Entity(
    tableName = "task_schedules",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "task_id", unique = true)
)
public class TaskScheduleEntity {

    public static final int TYPE_ONCE = 0;
    public static final int TYPE_DAILY = 1;
    public static final int TYPE_WEEKLY = 2;

    /** disableReason 常量 */
    public static final String REASON_USER_STOPPED = "USER_STOPPED";
    public static final String REASON_TASK_ARCHIVED = "TASK_ARCHIVED";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "task_id")
    public long taskId;

    /** 0=TYPE_ONCE, 1=DAILY, 2=WEEKLY */
    @ColumnInfo(name = "schedule_type")
    public int scheduleType;

    /**
     * TYPE_ONCE → 目标日期 00:00:00 毫秒时间戳；
     * daily → 0；
     * weekly → 星期 bitmask（bit0=周日）；
     * monthly → 1-31
     */
    @ColumnInfo(name = "schedule_value")
    public long scheduleValue;

    /** 一天中的分钟数（0-1439） */
    @ColumnInfo(name = "scheduled_time")
    public int scheduledTime;

    /** 关联的时段组类型（对应 PeriodGroupType 常量值），空字符串=顶层单次（不关联固定时段组） */
    @NonNull
    @ColumnInfo(name = "linked_period_group_type", defaultValue = "")
    public String linkedPeriodGroupType = "";

    /** 时间组下子类型：0=每天, 1=每周, 2=单次。仅 linkedPeriodGroupType 非空时有效 */
    @ColumnInfo(name = "schedule_sub_type", defaultValue = "0")
    public int scheduleSubType;

    @ColumnInfo(name = "enabled", defaultValue = "1")
    public boolean enabled;

    /** null / USER_STOPPED / TASK_ARCHIVED */
    @ColumnInfo(name = "disable_reason")
    public String disableReason;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    /** 关联任务的专注时长（非 DB 字段，JOIN 查询时填充） */
    @Ignore
    @ColumnInfo(name = "focus_minutes")
    public int focusMinutes;

    public boolean isRecurring() {
        return scheduleType == TYPE_DAILY
            || scheduleType == TYPE_WEEKLY
            || (!linkedPeriodGroupType.isEmpty() && scheduleSubType != 2);
    }
}
