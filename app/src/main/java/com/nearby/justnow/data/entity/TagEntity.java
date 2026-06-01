package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 标签表 — 名称唯一
 */
@Entity(
    tableName = "tags",
    indices = @Index(value = "name", unique = true)
)
public class TagEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 标签名称，唯一 */
    public String name;

    /** 标签颜色（ARGB 格式 int） */
    public int color;

    /** 是否为优先标签（持久化到数据库） */
    @ColumnInfo(name = "is_priority", defaultValue = "0")
    public boolean isPriority;
}
