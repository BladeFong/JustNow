package com.nearby.justnow.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * 按时间段组保存优先标签规则。
 */
@Entity(
    tableName = "priority_tag_rules",
    primaryKeys = {"group_type", "tag_id"}
)
public class PriorityTagRuleEntity {

    @NonNull
    @ColumnInfo(name = "group_type")
    public String groupType = "";

    @ColumnInfo(name = "tag_id")
    public long tagId;
}
