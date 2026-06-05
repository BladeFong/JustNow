package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 安排提醒跳过记录。每个安排只存一行（最后跳过日期 + 累计次数）。
 */
@Entity(
    tableName = "task_schedule_skips",
    foreignKeys = {
        @ForeignKey(
            entity = TaskScheduleEntity.class,
            parentColumns = "id",
            childColumns = "schedule_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("schedule_id")
    }
)
public class TaskScheduleSkipEntity {

    @PrimaryKey
    @ColumnInfo(name = "schedule_id")
    public long scheduleId;

    /** 最后一次跳过日期（当天 00:00 毫秒值）。 */
    @ColumnInfo(name = "last_skipped_date_ms")
    public long lastSkippedDateMs;

    /** 累计跳过次数。 */
    @ColumnInfo(name = "skip_count")
    public int skipCount;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
