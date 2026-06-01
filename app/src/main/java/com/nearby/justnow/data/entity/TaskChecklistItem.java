package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * todo 勾选清单条目
 */
@Entity(
    tableName = "task_checklist_items",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("task_id")
)
public class TaskChecklistItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "task_id")
    public long taskId;

    @ColumnInfo(name = "order_index")
    public int orderIndex;

    public String content;

    @ColumnInfo(defaultValue = "0")
    public boolean checked;

    @ColumnInfo(name = "crossed_out", defaultValue = "0")
    public boolean crossedOut;
}
