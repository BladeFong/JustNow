package com.nearby.justnow.ui.engine;

import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能展示引擎 — 多维度排序 + 比例截取。
 */
public class DisplayEngine {

    /**
     * 对任务列表排序并截取（无优先标签）。
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems) {
        return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
                Collections.emptySet(), null);
    }

    /**
     * 对任务列表排序并截取（含优先标签）。
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems, Set<Long> priorityTagIds) {
        return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
                priorityTagIds, null);
    }

    /**
     * 对任务列表排序并截取（含优先标签、安排任务优先）。
     */
    public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems, Set<Long> priorityTagIds,
                                      Set<Long> schedulePriorityTaskIds) {
        return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
                priorityTagIds, schedulePriorityTaskIds, DisplayPolicy.defaultPolicy());
    }

    public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                      int remainingMin, boolean reverseQuadrant,
                                      int maxDisplayItems, Set<Long> priorityTagIds,
                                      Set<Long> schedulePriorityTaskIds,
                                      DisplayPolicy policy) {
        if (tasks == null) tasks = new ArrayList<>();
        DisplayPolicy effectivePolicy = effectivePolicy(policy);
        try {
            List<DisplayItem>[] groups = buildSortedGroups(tasks, tagMap, remainingMin,
                    reverseQuadrant, priorityTagIds, schedulePriorityTaskIds,
                    effectivePolicy);
            return QuadrantRatioFilter.apply(groups[0], groups[1], maxDisplayItems,
                    effectivePolicy.getQuadrantRatio());
        } catch (Exception e) {
            return fallbackList(tasks, tagMap);
        }
    }

    /**
     * 构建并按策略排序的两组列表。
     * @return [0]=时间容纳组, [1]=时间不足组
     */
    @SuppressWarnings("unchecked")
    private List<DisplayItem>[] buildSortedGroups(List<TaskEntity> tasks,
                                                   Map<Long, TagEntity> tagMap,
                                                   int remainingMin, boolean reverseQuadrant,
                                                   Set<Long> priorityTagIds,
                                                   Set<Long> schedulePriorityTaskIds,
                                                   DisplayPolicy policy) {
        List<DisplayItem> groupA = new ArrayList<>();
        List<DisplayItem> groupB = new ArrayList<>();

        for (TaskEntity task : tasks) {
            DisplayItem item = buildDisplayItem(task, tagMap);
            item.sortWeight = computeCompatibilityWeight(item, reverseQuadrant, priorityTagIds,
                    schedulePriorityTaskIds, policy);

            if (fitsTime(task, remainingMin, policy)) {
                groupA.add(item);
            } else {
                groupB.add(item);
            }
        }

        Comparator<DisplayItem> comparator = buildComparator(reverseQuadrant, priorityTagIds,
                schedulePriorityTaskIds, policy, true);
        Collections.sort(groupA, comparator);
        Collections.sort(groupB, comparator);

        return new List[]{groupA, groupB};
    }

    /**
     * 四象限任务管理专用排序（不分组，不计象限权重）。
     */
    private List<DisplayItem> buildSortedItemsForQuadrant(List<TaskEntity> tasks,
                                                           Map<Long, TagEntity> tagMap,
                                                           int remainingMin,
                                                           Set<Long> priorityTagIds,
                                                           Set<Long> schedulePriorityTaskIds,
                                                           DisplayPolicy policy) {
        List<DisplayItem> items = new ArrayList<>();
        for (TaskEntity task : tasks) {
            DisplayItem item = buildDisplayItem(task, tagMap);
            item.sortWeight = fitsTime(task, remainingMin, policy) ? 0 : 10000;
            items.add(item);
        }
        Comparator<DisplayItem> comparator = Comparator
                .comparingInt((DisplayItem item) -> item.sortWeight)
                .thenComparing(buildComparator(false, priorityTagIds, schedulePriorityTaskIds,
                        policy, false));
        Collections.sort(items, comparator);
        return items;
    }

    /**
     * 按象限分组计算，不经过 QuadrantRatioFilter 截取，返回各象限全量排序结果。
     */
    public List<DisplayItem>[] computeByQuadrant(int[] quadrantMask,
                                                  List<TaskEntity> tasks,
                                                  Map<Long, TagEntity> tagMap,
                                                  int remainingMin,
                                                  Set<Long> priorityTagIds) {
        return computeByQuadrant(quadrantMask, tasks, tagMap, remainingMin, priorityTagIds, null);
    }

    public List<DisplayItem>[] computeByQuadrant(int[] quadrantMask,
                                                  List<TaskEntity> tasks,
                                                  Map<Long, TagEntity> tagMap,
                                                  int remainingMin,
                                                  Set<Long> priorityTagIds,
                                                  Set<Long> schedulePriorityTaskIds) {
        return computeByQuadrant(quadrantMask, tasks, tagMap, remainingMin, priorityTagIds,
                schedulePriorityTaskIds, DisplayPolicy.defaultPolicy());
    }

    public List<DisplayItem>[] computeByQuadrant(int[] quadrantMask,
                                                  List<TaskEntity> tasks,
                                                  Map<Long, TagEntity> tagMap,
                                                  int remainingMin,
                                                  Set<Long> priorityTagIds,
                                                  Set<Long> schedulePriorityTaskIds,
                                                  DisplayPolicy policy) {
        if (tasks == null) tasks = new ArrayList<>();
        DisplayPolicy effectivePolicy = effectivePolicy(policy);
        try {
            @SuppressWarnings("unchecked")
            List<DisplayItem>[] result = new List[4];

            if (quadrantMask == null || quadrantMask.length != 4) {
                throw new IllegalArgumentException("quadrantMask 长度必须为 4");
            }

            for (int quadrant = 0; quadrant < 4; quadrant++) {
                if (quadrantMask[quadrant] == 1) {
                    List<TaskEntity> quadrantTasks = new ArrayList<>();
                    for (TaskEntity task : tasks) {
                        if (task.quadrant == quadrant) {
                            quadrantTasks.add(task);
                        }
                    }
                    result[quadrant] = buildSortedItemsForQuadrant(quadrantTasks, tagMap,
                            remainingMin, priorityTagIds, schedulePriorityTaskIds,
                            effectivePolicy);
                } else {
                    result[quadrant] = null;
                }
            }

            return result;
        } catch (Exception e) {
            return fallbackQuadrantList(tasks, tagMap);
        }
    }

    private DisplayItem buildDisplayItem(TaskEntity task, Map<Long, TagEntity> tagMap) {
        TagEntity tag = (tagMap != null && task.tagId != null) ? tagMap.get(task.tagId) : null;
        DisplayItem item = new DisplayItem(task, tag);
        item.effectiveQuadrant = task.quadrant;
        return item;
    }

    private Comparator<DisplayItem> buildComparator(boolean reverseQuadrant,
                                                    Set<Long> priorityTagIds,
                                                    Set<Long> schedulePriorityTaskIds,
                                                    DisplayPolicy policy,
                                                    boolean includeQuadrant) {
        return (a, b) -> {
            for (DisplayPolicy.PriorityRule rule : policy.getPriorityOrder()) {
                int result = compareRule(rule, a, b, reverseQuadrant, priorityTagIds,
                        schedulePriorityTaskIds, policy, includeQuadrant);
                if (result != 0) return result;
            }
            return 0;
        };
    }

    private int compareRule(DisplayPolicy.PriorityRule rule, DisplayItem a, DisplayItem b,
                            boolean reverseQuadrant, Set<Long> priorityTagIds,
                            Set<Long> schedulePriorityTaskIds, DisplayPolicy policy,
                            boolean includeQuadrant) {
        switch (rule) {
            case SCHEDULE_PRIORITY:
                return Boolean.compare(isSchedulePriority(b, schedulePriorityTaskIds),
                        isSchedulePriority(a, schedulePriorityTaskIds));
            case TAG_PRIORITY:
                return Boolean.compare(isPriorityTag(b, priorityTagIds),
                        isPriorityTag(a, priorityTagIds));
            case QUADRANT:
                if (!includeQuadrant) return 0;
                return compareQuadrant(a.effectiveQuadrant, b.effectiveQuadrant, reverseQuadrant);
            case FOCUS_DURATION:
                return compareFocusDuration(a.task.focusMinutes, b.task.focusMinutes, policy);
            default:
                return 0;
        }
    }

    private int compareQuadrant(int a, int b, boolean reverseQuadrant) {
        int rankA = reverseQuadrant ? 3 - a : a;
        int rankB = reverseQuadrant ? 3 - b : b;
        return Integer.compare(rankA, rankB);
    }

    private int compareFocusDuration(int a, int b, DisplayPolicy policy) {
        int cappedA = Math.min(a, policy.getFocusMaxMinutes());
        int cappedB = Math.min(b, policy.getFocusMaxMinutes());
        if (policy.getFocusDurationOrder() == DisplayPolicy.FocusDurationOrder.ASC) {
            return Integer.compare(cappedA, cappedB);
        }
        return Integer.compare(cappedB, cappedA);
    }

    private boolean fitsTime(TaskEntity task, int remainingMin, DisplayPolicy policy) {
        return task.focusMinutes == 0
                || remainingMin >= task.focusMinutes
                || task.focusMinutes - remainingMin <= policy.getFitToleranceMinutes();
    }

    private boolean isSchedulePriority(DisplayItem item, Set<Long> schedulePriorityTaskIds) {
        return schedulePriorityTaskIds != null && schedulePriorityTaskIds.contains(item.task.id);
    }

    private boolean isPriorityTag(DisplayItem item, Set<Long> priorityTagIds) {
        TaskEntity task = item.task;
        return priorityTagIds != null && task.tagId != null && priorityTagIds.contains(task.tagId);
    }

    private DisplayPolicy effectivePolicy(DisplayPolicy policy) {
        return policy != null ? policy : DisplayPolicy.defaultPolicy();
    }

    private int computeCompatibilityWeight(DisplayItem item, boolean reverseQuadrant,
                                           Set<Long> priorityTagIds,
                                           Set<Long> schedulePriorityTaskIds,
                                           DisplayPolicy policy) {
        int weight = 0;
        if (isSchedulePriority(item, schedulePriorityTaskIds)) {
            weight -= 300;
        }
        if (isPriorityTag(item, priorityTagIds)) {
            weight -= 200;
        }
        if (reverseQuadrant) {
            weight += (3 - item.effectiveQuadrant) * 50;
        } else {
            weight += item.effectiveQuadrant * 50;
        }
        int cappedFocus = Math.min(item.task.focusMinutes, policy.getFocusMaxMinutes());
        if (policy.getFocusDurationOrder() == DisplayPolicy.FocusDurationOrder.ASC) {
            weight += cappedFocus / 10;
        } else {
            weight -= cappedFocus / 10;
        }
        return weight;
    }

    /** 降级：按象限分组，不计算权重。 */
    private List<DisplayItem>[] fallbackQuadrantList(List<TaskEntity> tasks,
                                                      Map<Long, TagEntity> tagMap) {
        @SuppressWarnings("unchecked")
        List<DisplayItem>[] result = new List[4];
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            result[quadrant] = new ArrayList<>();
        }
        if (tasks != null) {
            if (tagMap == null) tagMap = Collections.emptyMap();
            for (TaskEntity task : tasks) {
                int quadrant = task.quadrant;
                if (quadrant >= 0 && quadrant < 4) {
                    result[quadrant].add(new DisplayItem(task,
                            task.tagId != null ? tagMap.get(task.tagId) : null));
                }
            }
        }
        return result;
    }

    /** 降级：简单列表（创建时间倒序）。 */
    private List<DisplayItem> fallbackList(List<TaskEntity> tasks,
                                            Map<Long, TagEntity> tagMap) {
        if (tasks == null) return new ArrayList<>();
        if (tagMap == null) tagMap = Collections.emptyMap();
        List<DisplayItem> items = new ArrayList<>();
        for (TaskEntity task : tasks) {
            items.add(new DisplayItem(task, task.tagId != null ? tagMap.get(task.tagId) : null));
        }
        items.sort((a, b) -> Long.compare(b.task.createdAt, a.task.createdAt));
        return items;
    }
}
