package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 任务执行记录表
 */
@Entity(
    tableName = "task_executions",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("task_id"), @Index("date")}
)
public class TaskExecutionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 关联任务ID */
    @ColumnInfo(name = "task_id")
    public long taskId;

    /** 执行日期 yyyy-MM-dd */
    public String date;

    /** 实际开始时间戳（毫秒）。 */
    @ColumnInfo(name = "start_ms", defaultValue = "0")
    public long startMs;

    /** 实际结束时间戳（毫秒）。 */
    @ColumnInfo(name = "end_ms", defaultValue = "0")
    public long endMs;

    /** 调度时间 HH:mm（专注时长任务设置的时间），可为空 */
    @ColumnInfo(name = "scheduled_time")
    public String scheduledTime;

    /**
     * 执行状态：0=正常完成 1=延迟 2=暂停 3=短完成（提前结束）
     */
    public int status;

    /** 占用时间段区间，如 "14:00-15:30" */
    @ColumnInfo(name = "period_slot")
    public String periodSlot;

    /** 实际耗时（分钟） */
    @ColumnInfo(name = "actual_minutes", defaultValue = "0")
    public int actualMinutes;
}
