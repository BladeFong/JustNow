package com.nearby.justnow.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;

/**
 * 任务完成计数器表。
 * 联合主键 (task_id, period_key)，每个任务每周期一行。
 * 日模式不写此表，由 task_executions 当天记录判定。
 */
@Entity(
    tableName = "task_completion_counter",
    primaryKeys = {"task_id", "period_key"}
)
public class TaskCompletionCounterEntity {

    @ColumnInfo(name = "task_id")
    public long taskId;

    /** 周期标识：周 yyyy-Www、月 yyyy-MM、年 yyyy */
    @NonNull
    @ColumnInfo(name = "period_key")
    public String periodKey;

    @ColumnInfo(name = "completed", defaultValue = "0")
    public int completed;

    public TaskCompletionCounterEntity() {}

    @Ignore
    public TaskCompletionCounterEntity(long taskId, @NonNull String periodKey, int completed) {
        this.taskId = taskId;
        this.periodKey = periodKey;
        this.completed = completed;
    }
}
