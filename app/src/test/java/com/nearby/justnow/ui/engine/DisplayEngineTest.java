package com.nearby.justnow.ui.engine;

import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * DisplayEngine 单元测试
 */
public class DisplayEngineTest {

    private final DisplayEngine mEngine = new DisplayEngine();

    /** 创建测试任务 */
    private TaskEntity createTask(long id, String content, int quadrant, int focusMinutes) {
        TaskEntity t = new TaskEntity();
        t.id = id;
        t.content = content;
        t.quadrant = quadrant;
        t.focusMinutes = focusMinutes;
        t.createdAt = System.currentTimeMillis() - id * 1000;
        return t;
    }

    private DisplayPolicy policy(List<DisplayPolicy.PriorityRule> priorityOrder,
                                 int fitToleranceMinutes,
                                 int focusMaxMinutes,
                                 DisplayPolicy.FocusDurationOrder focusDurationOrder,
                                 int[] quadrantRatio) {
        return new DisplayPolicy(DisplayPolicy.VERSION, priorityOrder, fitToleranceMinutes,
                focusMaxMinutes, focusDurationOrder, quadrantRatio);
    }

    private DisplayPolicy focusFirstPolicy(int fitToleranceMinutes,
                                           int focusMaxMinutes,
                                           DisplayPolicy.FocusDurationOrder focusDurationOrder) {
        return policy(Arrays.asList(
                DisplayPolicy.PriorityRule.FOCUS_DURATION,
                DisplayPolicy.PriorityRule.QUADRANT,
                DisplayPolicy.PriorityRule.SCHEDULE_PRIORITY,
                DisplayPolicy.PriorityRule.TAG_PRIORITY
        ), fitToleranceMinutes, focusMaxMinutes, focusDurationOrder, new int[]{4, 2, 2, 1});
    }

    @Test
    public void compute_nullTasks_returnsEmptyList() {
        List<DisplayItem> result = mEngine.compute(null, new HashMap<>(), 120, false, 8);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void compute_nullTagMap_handlesGracefully() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "测试任务", 0, 30));
        List<DisplayItem> result = mEngine.compute(tasks, null, 120, false, 8);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertNull(result.get(0).tag);
    }

    @Test
    public void compute_emptyTasks_returnsEmptyList() {
        List<DisplayItem> result = mEngine.compute(new ArrayList<>(), new HashMap<>(), 120, false, 8);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void compute_daytime_quadrantSorting() {
        // 日间：紧急重要(0)应排在紧急不重要(1)前面
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q1任务", 1, 30));  // 紧急不重要
        tasks.add(createTask(2, "Q0任务", 0, 30));  // 紧急重要

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 120, false, 8);

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).task.quadrant); // Q0 在前
        assertEquals(1, result.get(1).task.quadrant); // Q1 在后
    }

    @Test
    public void compute_evening_quadrantReversal() {
        // 晚上：不紧急不重要(3)应排在紧急重要(0)前面
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q0任务", 0, 30));
        tasks.add(createTask(2, "Q3任务", 3, 30));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 120, true, 8);

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).task.quadrant); // Q3 在前（反转）
        assertEquals(0, result.get(1).task.quadrant); // Q0 在后（反转）
    }

    @Test
    public void compute_remainingTimeWeight_fitsBetterFirst() {
        // 90分钟剩余，30分钟任务能容纳；120分钟任务超过默认容差，排到时间不足组。
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "120分钟任务", 0, 120));
        tasks.add(createTask(2, "30分钟任务", 0, 30));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 90, false, 8);

        assertEquals(2, result.size());
        assertEquals(30, result.get(0).task.focusMinutes);
        assertEquals(120, result.get(1).task.focusMinutes);
    }

    @Test
    public void compute_policyPriorityOrder_focusBeforeQuadrant() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "短任务 Q0", 0, 30));
        tasks.add(createTask(2, "长任务 Q3", 3, 120));

        DisplayPolicy policy = focusFirstPolicy(15, 120, DisplayPolicy.FocusDurationOrder.DESC);

        List<DisplayItem> result = mEngine.compute(tasks, new HashMap<>(), 120, false, 8,
                Collections.emptySet(), null, null, policy);

        assertEquals(2L, result.get(0).task.id);
        assertEquals(1L, result.get(1).task.id);
    }

    @Test
    public void compute_policyFitTolerance_changesFittingGroup() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "150分钟任务", 0, 150));
        tasks.add(createTask(2, "30分钟任务", 0, 30));

        DisplayPolicy policy = focusFirstPolicy(30, 150, DisplayPolicy.FocusDurationOrder.DESC);

        List<DisplayItem> result = mEngine.compute(tasks, new HashMap<>(), 120, false, 8,
                Collections.emptySet(), null, null, policy);

        assertEquals(150, result.get(0).task.focusMinutes);
        assertEquals(30, result.get(1).task.focusMinutes);
    }

    @Test
    public void compute_policyFocusDurationAsc_shorterFirst() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "长任务", 0, 120));
        tasks.add(createTask(2, "短任务", 0, 30));

        DisplayPolicy policy = focusFirstPolicy(15, 120, DisplayPolicy.FocusDurationOrder.ASC);

        List<DisplayItem> result = mEngine.compute(tasks, new HashMap<>(), 120, false, 8,
                Collections.emptySet(), null, null, policy);

        assertEquals(30, result.get(0).task.focusMinutes);
        assertEquals(120, result.get(1).task.focusMinutes);
    }

    @Test
    public void compute_policyFocusMax150_keeps150AsDistinctSlot() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "120分钟任务", 0, 120));
        tasks.add(createTask(2, "150分钟任务", 0, 150));

        DisplayPolicy policy = focusFirstPolicy(15, 150, DisplayPolicy.FocusDurationOrder.DESC);

        List<DisplayItem> result = mEngine.compute(tasks, new HashMap<>(), 200, false, 8,
                Collections.emptySet(), null, null, policy);

        assertEquals(150, result.get(0).task.focusMinutes);
        assertEquals(120, result.get(1).task.focusMinutes);
    }

    @Test
    public void compute_withValidInput_returnsCorrectItemCount() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "正常任务", 0, 0));
        List<DisplayItem> result = mEngine.compute(tasks, new HashMap<>(), 120, false, 8);
        assertEquals(1, result.size());
    }

    // ---- 降级恢复 ----

    private Map<Long, TaskQuadrantDegradeEntity> degradeMap(long taskId, int originalQuadrant, long recoverMs) {
        TaskQuadrantDegradeEntity d = new TaskQuadrantDegradeEntity();
        d.taskId = taskId;
        d.originalQuadrant = originalQuadrant;
        d.recoverMs = recoverMs;
        Map<Long, TaskQuadrantDegradeEntity> map = new HashMap<>();
        map.put(taskId, d);
        return map;
    }

    @Test
    public void compute_degradeNotExpired_quadrantShifted() {
        // Q0 任务降级中 → effectiveQuadrant 按 Q1（降一级）
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q0降级任务", 0, 60));
        tasks.add(createTask(2, "Q1正常任务", 1, 60));

        Map<Long, TaskQuadrantDegradeEntity> map = degradeMap(1, 0,
            System.currentTimeMillis() + 3600000L);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 120, false, 8,
            Collections.emptySet(), map);

        // 降级后 Q0→Q1，两个同为 Q1 权重 50 - longTaskBonus(6) = 44
        // 同权重保留插入顺序：Q0(id=1) 在前
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).task.quadrant); // 降级 Q0（先插入）
        assertEquals(1, result.get(0).effectiveQuadrant);
        assertEquals(1, result.get(1).task.quadrant); // 真实 Q1
        assertEquals(1, result.get(1).effectiveQuadrant);
    }

    @Test
    public void compute_degradeNotExpired_sortsByEffectiveQuadrant() {
        // 原 Q0 降级成有效 Q1 后，应排在未降级 Q0 后面
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q0降级任务", 0, 60));
        tasks.add(createTask(2, "Q0正常任务", 0, 60));

        Map<Long, TaskQuadrantDegradeEntity> map = degradeMap(1, 0,
            System.currentTimeMillis() + 3600000L);

        List<DisplayItem> result = mEngine.compute(tasks, new HashMap<>(), 120, false, 8,
            Collections.emptySet(), map);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).task.id);
        assertEquals(0, result.get(0).effectiveQuadrant);
        assertEquals(1L, result.get(1).task.id);
        assertEquals(1, result.get(1).effectiveQuadrant);
    }

    @Test
    public void compute_degradeExpired_usesOriginalQuadrant() {
        // 降级已过期 → 权重按原象限
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q0已过期", 0, 60));
        tasks.add(createTask(2, "Q1任务", 1, 60));

        Map<Long, TaskQuadrantDegradeEntity> map = degradeMap(1, 0,
            System.currentTimeMillis() - 1000L); // 已过期

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 120, false, 8,
            Collections.emptySet(), map);

        // 过期后恢复原象限 Q0
        assertEquals(2, result.size());
        assertEquals(0, result.get(0).task.quadrant); // Q0 在前
        assertEquals(0, result.get(0).effectiveQuadrant);
        assertEquals(1, result.get(1).task.quadrant); // Q1 在后
        assertEquals(1, result.get(1).effectiveQuadrant);
    }

    @Test
    public void compute_degradeQuadrant3_staysAt3() {
        // Q3 任务降级 → 仍然是 Q3（已到底）
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q3降级", 3, 0));

        Map<Long, TaskQuadrantDegradeEntity> map = degradeMap(1, 3,
            System.currentTimeMillis() + 3600000L);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 120, false, 8,
            Collections.emptySet(), map);

        assertEquals(1, result.size());
        // Q3 降级后 effectiveQuadrant = Math.min(3, 3+1) = 3
        assertEquals(3, result.get(0).task.quadrant);
        assertEquals(3, result.get(0).effectiveQuadrant);
    }

    @Test
    public void compute_noDegradeMap_normalBehavior() {
        // 无降级记录 → 正常排序
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, "Q0", 0, 60));
        tasks.add(createTask(2, "Q1", 1, 60));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<DisplayItem> result = mEngine.compute(tasks, tagMap, 120, false, 8,
            Collections.emptySet(), null);

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).task.quadrant);
        assertEquals(0, result.get(0).effectiveQuadrant);
        assertEquals(1, result.get(1).task.quadrant);
        assertEquals(1, result.get(1).effectiveQuadrant);
    }
}
