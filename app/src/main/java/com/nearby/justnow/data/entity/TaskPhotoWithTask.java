package com.nearby.justnow.data.entity;

import androidx.room.Embedded;
import androidx.room.Relation;

/**
 * 关联照片与任务详情的POJO类，用于时光胶囊卡片渲染
 */
public class TaskPhotoWithTask {
    @Embedded
    public TaskPhotoEntity photo;

    // 关联获取任务标题、象限及图标信息
    public String taskContent;
    public int taskQuadrant;
    public String taskIconName;
}
