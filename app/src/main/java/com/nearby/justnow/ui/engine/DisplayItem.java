package com.nearby.justnow.ui.engine;

import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;

/**
 * 展示项 — 任务 + 标签 + 排序权重
 */
public class DisplayItem {

    public TaskEntity task;
    public TagEntity tag;
    /** 排序权重（越小越靠前） */
    public int sortWeight;

    public DisplayItem(TaskEntity task, TagEntity tag) {
        this.task = task;
        this.tag = tag;
    }
}
