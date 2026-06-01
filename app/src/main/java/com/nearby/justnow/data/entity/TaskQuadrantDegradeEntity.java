package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_quadrant_degrade")
public class TaskQuadrantDegradeEntity {

    @PrimaryKey
    @ColumnInfo(name = "task_id")
    public long taskId;

    @ColumnInfo(name = "original_quadrant")
    public int originalQuadrant;

    @ColumnInfo(name = "recover_ms")
    public long recoverMs;
}
