package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 笔记分享列表条目
 */
@Entity(
    tableName = "task_note_shares",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index("task_id")
)
public class TaskNoteShare {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "task_id")
    public long taskId;

    @ColumnInfo(name = "order_index")
    public int orderIndex;

    @ColumnInfo(name = "deep_link")
    public String deepLink;

    public String hint;
}
