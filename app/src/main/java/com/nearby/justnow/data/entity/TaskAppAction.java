package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * APP 跳转列表条目
 */
@Entity(
    tableName = "task_app_actions",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("task_id")
)
public class TaskAppAction {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "task_id")
    public long taskId;

    @ColumnInfo(name = "order_index")
    public int orderIndex;

    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "deep_link")
    public String deepLink;

    public String hint;
}
