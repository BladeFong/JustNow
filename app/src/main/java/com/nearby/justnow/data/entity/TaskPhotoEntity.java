package com.nearby.justnow.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

/**
 * 任务关联成果照片实体类
 */
@Entity(
    tableName = "task_photos",
    foreignKeys = @ForeignKey(
        entity = TaskEntity.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    )
)
public class TaskPhotoEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "task_id", index = true)
    public long taskId;

    @NonNull
    @ColumnInfo(name = "photo_uri")
    public String photoUri;

    @ColumnInfo(name = "created_at")
    public long createdAt;
}
