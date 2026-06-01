package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 安排提醒延迟记录。每次延迟写入一条，用于当天门控与后续统计。
 */
@Entity(
    tableName = "task_schedule_postpones",
    foreignKeys = {
        @ForeignKey(
            entity = TaskScheduleEntity.class,
            parentColumns = "id",
            childColumns = "schedule_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = TaskEntity.class,
            parentColumns = "id",
            childColumns = "task_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index("schedule_id"),
        @Index("task_id"),
        @Index("date_ms")
    }
)
public class TaskSchedulePostponeEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 关联安排 ID。 */
    @ColumnInfo(name = "schedule_id")
    public long scheduleId;

    /** 被延迟的任务 ID。 */
    @ColumnInfo(name = "task_id")
    public long taskId;

    /** 阻塞它的执行中任务 ID（统计预留）。 */
    @ColumnInfo(name = "blocked_by_task_id")
    public long blockedByTaskId;

    /** 延迟时长（分钟）。 */
    @ColumnInfo(name = "postpone_minutes")
    public int postponeMinutes;

    /** 延迟发生日期（当天 00:00 毫秒值），用于当天延迟一次门控。 */
    @ColumnInfo(name = "date_ms")
    public long dateMs;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
