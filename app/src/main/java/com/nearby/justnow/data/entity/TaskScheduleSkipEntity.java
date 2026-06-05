package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 安排提醒跳过记录。每天每次忽略写入一条。
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
        @Index("schedule_id"),
        @Index("date_ms")
    }
)
public class TaskScheduleSkipEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "schedule_id")
    public long scheduleId;

    /** 跳过日期（当天 00:00 毫秒值）。 */
    @ColumnInfo(name = "date_ms")
    public long dateMs;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
