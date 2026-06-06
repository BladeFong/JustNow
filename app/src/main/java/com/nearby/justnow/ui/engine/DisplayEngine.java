package com.nearby.justnow.ui.engine;

import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能展示引擎 — 多维度排序 + 比例截取
 */
public class DisplayEngine {

    /**
     * 对任务列表排序并截取（无优先标签）
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, java.util.Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems) {
        return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
                       Collections.emptySet(), null);
    }

    /**
     * 对任务列表排序并截取（含优先标签）
     * @param tasks          未归档任务
     * @param tagMap         taskId → TagEntity 映射
     * @param remainingMin   剩余分钟数
     * @param reverseQuadrant 是否反转四象限排序
     * @param maxDisplayItems 最大显示项数
     * @param priorityTagIds 当前生效的优先标签 ID 集合
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, java.util.Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems, Set<Long> priorityTagIds) {
        return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
                       priorityTagIds, null);
    }

    /**
     * 对任务列表排序并截取（含优先标签和降级记录）
     * @param degradeMap     taskId → 降级记录，可为 null
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, java.util.Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems, Set<Long> priorityTagIds,
                                      Map<Long, TaskQuadrantDegradeEntity> degradeMap) {
        return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
                       priorityTagIds, degradeMap, null);
    }

    /**
     * 对任务列表排序并截取（含优先标签、降级记录、安排任务优先）
     * @param schedulePriorityTaskIds 当前在 30 分钟优先窗口内的任务 ID 集合，可为 null
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, java.util.Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems, Set<Long> priorityTagIds,
                                      Map<Long, TaskQuadrantDegradeEntity> degradeMap,
                                      Set<Long> schedulePriorityTaskIds) {
        if (tasks == null) tasks = new ArrayList<>();
        try {
            return doCompute(tasks, tagMap, remainingMin, reverseQuadrant,
                             maxDisplayItems, priorityTagIds, degradeMap, schedulePriorityTaskIds);
        } catch (Exception e) {
            return fallbackList(tasks, tagMap);
        }
    }

    private List<DisplayItem> doCompute(List<TaskEntity> tasks, java.util.Map<Long, TagEntity> tagMap,
                                         int remainingMin, boolean reverseQuadrant,
                                         int maxDisplayItems, Set<Long> priorityTagIds,
                                         Map<Long, TaskQuadrantDegradeEntity> degradeMap,
                                         Set<Long> schedulePriorityTaskIds) {
        List<DisplayItem>[] groups = buildSortedGroups(tasks, tagMap, remainingMin, reverseQuadrant,
                                                       priorityTagIds, degradeMap, schedulePriorityTaskIds);
        return QuadrantRatioFilter.apply(groups[0], groups[1], maxDisplayItems);
    }

    /**
     * 构建并按权重排序的两组列表（供主展示引擎 + QuadrantRatioFilter 使用）。
     * 先按剩余时间能否容纳分两组，组内按优先标签→象限→专注时长微调排序。
     * @return [0]=时间容纳组, [1]=时间不足组
     */
    @SuppressWarnings("unchecked")
    private List<DisplayItem>[] buildSortedGroups(List<TaskEntity> tasks,
                                                   java.util.Map<Long, TagEntity> tagMap,
                                                   int remainingMin, boolean reverseQuadrant,
                                                   Set<Long> priorityTagIds,
                                                   Map<Long, TaskQuadrantDegradeEntity> degradeMap,
                                                   Set<Long> schedulePriorityTaskIds) {
        List<DisplayItem> groupA = new ArrayList<>(); // 时间容纳
        List<DisplayItem> groupB = new ArrayList<>(); // 时间不足

        for (TaskEntity t : tasks) {
            TagEntity tag = (tagMap != null && t.tagId != null) ? tagMap.get(t.tagId) : null;
            DisplayItem item = new DisplayItem(t, tag);

            int effectiveQuadrant = t.quadrant;
            if (degradeMap != null) {
                TaskQuadrantDegradeEntity degrade = degradeMap.get(t.id);
                if (degrade != null) {
                    long now = System.currentTimeMillis();
                    if (now < degrade.recoverMs) {
                        effectiveQuadrant = Math.min(3, degrade.originalQuadrant + 1);
                    }
                }
            }
            item.effectiveQuadrant = effectiveQuadrant;

            int weight = 0;
            // 安排任务优先（到点后 30 分钟内）
            if (schedulePriorityTaskIds != null && schedulePriorityTaskIds.contains(t.id)) {
                weight -= 300;
            }
            // 优先标签
            if (priorityTagIds != null && t.tagId != null && priorityTagIds.contains(t.tagId)) {
                weight -= 200;
            }
            // 四象限（组内第二优先级）
            if (reverseQuadrant) {
                weight += (3 - effectiveQuadrant) * 50;
            } else {
                weight += effectiveQuadrant * 50;
            }
            // 专注时长从长到短（组内微调）
            weight -= Math.min(t.focusMinutes, 120) / 10;
            item.sortWeight = weight;

            boolean fitsTime = t.focusMinutes == 0
                    || remainingMin >= t.focusMinutes
                    || (t.focusMinutes - remainingMin <= 15);
            if (fitsTime) {
                groupA.add(item);
            } else {
                groupB.add(item);
            }
        }

        Collections.sort(groupA, Comparator.comparingInt(a -> a.sortWeight));
        Collections.sort(groupB, Comparator.comparingInt(a -> a.sortWeight));

        return new List[]{groupA, groupB};
    }

    /**
     * 四象限任务管理专用排序（不分组，不计象限权重）。
     * 排序：时间容纳 → 优先标签 → 专注时长微调。
     */
    private List<DisplayItem> buildSortedItemsForQuadrant(List<TaskEntity> tasks,
                                                           java.util.Map<Long, TagEntity> tagMap,
                                                           int remainingMin,
                                                           Set<Long> priorityTagIds,
                                                           Set<Long> schedulePriorityTaskIds) {
        List<DisplayItem> items = new ArrayList<>();
        for (TaskEntity t : tasks) {
            TagEntity tag = (tagMap != null && t.tagId != null) ? tagMap.get(t.tagId) : null;
            DisplayItem item = new DisplayItem(t, tag);

            int weight = 0;
            // 时间容纳（最高优先级）
            boolean fitsTime = t.focusMinutes == 0
                    || remainingMin >= t.focusMinutes
                    || (t.focusMinutes - remainingMin <= 15);
            if (!fitsTime) {
                weight += 10000;
            }
            // 安排任务优先（到点后 30 分钟内）
            if (schedulePriorityTaskIds != null && schedulePriorityTaskIds.contains(t.id)) {
                weight -= 300;
            }
            // 优先标签
            if (priorityTagIds != null && t.tagId != null && priorityTagIds.contains(t.tagId)) {
                weight -= 200;
            }
            // 专注时长微调
            weight -= Math.min(t.focusMinutes, 120) / 10;

            item.sortWeight = weight;
            items.add(item);
        }
        Collections.sort(items, Comparator.comparingInt(a -> a.sortWeight));
        return items;
    }

    /**
     * 按象限分组计算，不经过 QuadrantRatioFilter 截取，返回各象限全量排序结果。
     *
     * @param quadrantMask   长度 4 的 int 数组，1=需要该象限，0=不需要，下标即象限号
     * @param tasks          未归档任务
     * @param tagMap         taskId → TagEntity 映射
     * @param remainingMin   剩余分钟数
     *（已移除 reverseQuadrant 参数：组内任务同一象限，象限排序无意义）
     * @param priorityTagIds 当前生效的优先标签 ID 集合
     * @return 长度 4 的 List 数组，仅 mask=1 的位置有数据，mask=0 的位置为 null
     */
    public List<DisplayItem>[] computeByQuadrant(int[] quadrantMask,
                                                  List<TaskEntity> tasks,
                                                  java.util.Map<Long, TagEntity> tagMap,
                                                  int remainingMin,
                                                  Set<Long> priorityTagIds) {
        return computeByQuadrant(quadrantMask, tasks, tagMap, remainingMin, priorityTagIds, null);
    }

    public List<DisplayItem>[] computeByQuadrant(int[] quadrantMask,
                                                  List<TaskEntity> tasks,
                                                  java.util.Map<Long, TagEntity> tagMap,
                                                  int remainingMin,
                                                  Set<Long> priorityTagIds,
                                                  Set<Long> schedulePriorityTaskIds) {
        if (tasks == null) tasks = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            List<DisplayItem>[] result = new List[4];

            if (quadrantMask == null || quadrantMask.length != 4) {
                throw new IllegalArgumentException("quadrantMask 长度必须为 4");
            }

            for (int q = 0; q < 4; q++) {
                if (quadrantMask[q] == 1) {
                    final int quadrant = q;
                    List<TaskEntity> quadrantTasks = new ArrayList<>();
                    for (TaskEntity t : tasks) {
                        if (t.quadrant == quadrant) {
                            quadrantTasks.add(t);
                        }
                    }
                    result[q] = buildSortedItemsForQuadrant(quadrantTasks, tagMap, remainingMin,
                                                             priorityTagIds, schedulePriorityTaskIds);
                } else {
                    result[q] = null;
                }
            }

            return result;
        } catch (Exception e) {
            return fallbackQuadrantList(tasks, tagMap);
        }
    }

    /** 降级：按象限分组，不计算权重 */
    private List<DisplayItem>[] fallbackQuadrantList(List<TaskEntity> tasks,
                                                      java.util.Map<Long, TagEntity> tagMap) {
        @SuppressWarnings("unchecked")
        List<DisplayItem>[] result = new List[4];
        for (int q = 0; q < 4; q++) {
            result[q] = new ArrayList<>();
        }
        if (tasks != null) {
            if (tagMap == null) tagMap = java.util.Collections.emptyMap();
            for (TaskEntity t : tasks) {
                int q = t.quadrant;
                if (q >= 0 && q < 4) {
                    result[q].add(new DisplayItem(t, t.tagId != null ? tagMap.get(t.tagId) : null));
                }
            }
        }
        return result;
    }

    /** 降级：简单列表（创建时间倒序） */
    private List<DisplayItem> fallbackList(List<TaskEntity> tasks,
                                            java.util.Map<Long, TagEntity> tagMap) {
        if (tasks == null) return new ArrayList<>();
        if (tagMap == null) tagMap = java.util.Collections.emptyMap();
        List<DisplayItem> items = new ArrayList<>();
        for (TaskEntity t : tasks) {
            items.add(new DisplayItem(t, t.tagId != null ? tagMap.get(t.tagId) : null));
        }
        items.sort((a, b) -> Long.compare(b.task.createdAt, a.task.createdAt));
        return items;
    }
}
