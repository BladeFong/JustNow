package com.nearby.justnow.ui.engine;

import com.nearby.justnow.data.entity.TaskEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * QuadrantRatioFilter 单元测试。
 * 4:2:2:1 比例仅在 total > maxDisplayItems 时生效，否则全量展示。
 */
public class QuadrantRatioFilterTest {

    private DisplayItem createItem(long id, int quadrant) {
        TaskEntity t = new TaskEntity();
        t.id = id;
        t.content = "任务" + id;
        t.quadrant = quadrant;
        return new DisplayItem(t, null);
    }

    @Test
    public void emptyList_returnsEmpty() {
        assertTrue(QuadrantRatioFilter.apply(new ArrayList<>(), new ArrayList<>(), 9).isEmpty());
    }

    @Test
    public void totalNotExceedMax_returnsAll() {
        // 5条 ≤ 可显示10条 → 全量
        List<DisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(createItem(i, 0));
        assertEquals(5, QuadrantRatioFilter.apply(items, new ArrayList<>(), 10).size());
    }

    @Test
    public void totalExceedMax_Q0FillsAllSlots() {
        // 15条全是Q0，可显示10条 → 迭代多轮逐步填满
        List<DisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 15; i++) items.add(createItem(i, 0));
        List<DisplayItem> result = QuadrantRatioFilter.apply(items, new ArrayList<>(), 10);
        assertEquals(10, result.size());
        for (DisplayItem item : result) assertEquals(0, item.task.quadrant);
    }

    @Test
    public void totalExceedMax_Q3FillsAllSlots() {
        // 8条全是Q3，可显示6条 → 迭代多轮逐步填满
        List<DisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) items.add(createItem(i, 3));
        List<DisplayItem> result = QuadrantRatioFilter.apply(items, new ArrayList<>(), 6);
        assertEquals(6, result.size());
        for (DisplayItem item : result) assertEquals(3, item.task.quadrant);
    }

    @Test
    public void totalExceedMax_mixedQuadrantsHonorEachLimit() {
        // 各象限10条，共40条，可显示9条
        // Q0=ceil(9×4/9)=4, Q1=ceil(9×2/9)=2, Q2=ceil(9×2/9)=2, Q3=ceil(9×1/9)=1
        List<DisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(createItem(i, 0));
        for (int i = 10; i < 20; i++) items.add(createItem(i, 1));
        for (int i = 20; i < 30; i++) items.add(createItem(i, 2));
        for (int i = 30; i < 40; i++) items.add(createItem(i, 3));

        List<DisplayItem> result = QuadrantRatioFilter.apply(items, new ArrayList<>(), 9);
        assertEquals(9, result.size()); // 4+2+2+1

        long[] qCounts = new long[4];
        for (DisplayItem item : result) qCounts[item.task.quadrant]++;
        assertEquals(4, qCounts[0]);
        assertEquals(2, qCounts[1]);
        assertEquals(2, qCounts[2]);
        assertEquals(1, qCounts[3]);
    }

    @Test
    public void totalExceedMax_customRatioOverridesDefault() {
        List<DisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(createItem(i, 0));
        for (int i = 10; i < 20; i++) items.add(createItem(i, 1));
        for (int i = 20; i < 30; i++) items.add(createItem(i, 2));
        for (int i = 30; i < 40; i++) items.add(createItem(i, 3));

        List<DisplayItem> result = QuadrantRatioFilter.apply(items, new ArrayList<>(), 8,
                new int[]{1, 1, 0, 0});

        long[] qCounts = new long[4];
        for (DisplayItem item : result) qCounts[item.task.quadrant]++;
        assertEquals(4, qCounts[0]);
        assertEquals(4, qCounts[1]);
        assertEquals(0, qCounts[2]);
        assertEquals(0, qCounts[3]);
    }

    @Test
    public void preservesInputOrder() {
        // 3条Q0，可显示10条 → 全量，顺序不变
        List<DisplayItem> items = new ArrayList<>();
        items.add(createItem(100, 0));
        items.add(createItem(200, 0));
        items.add(createItem(300, 0));
        List<DisplayItem> result = QuadrantRatioFilter.apply(items, new ArrayList<>(), 10);
        assertEquals(3, result.size());
        assertEquals(100, result.get(0).task.id);
        assertEquals(200, result.get(1).task.id);
        assertEquals(300, result.get(2).task.id);
    }

    @Test
    public void quadrantWithFewerItems_othersFillRemaining() {
        // Q0=6条, Q1=1条，共7条 > 可显示6条 → 触发比例
        // 第1轮：Q0=ceil(6×4/9)=3, Q1=ceil(6×2/9)=2 → 收Q0×3 Q1×1=4条
        // 第2轮：剩余2空位, Q0=ceil(2×4/9)=1 → 收Q0×1=1条
        // 第3轮：剩余1空位, Q0=ceil(1×4/9)=1 → 收Q0×1=1条 → 填满6条
        List<DisplayItem> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) items.add(createItem(i, 0));
        items.add(createItem(100, 1));

        List<DisplayItem> result = QuadrantRatioFilter.apply(items, new ArrayList<>(), 6);
        assertEquals(6, result.size());
    }

    @Test
    public void groupAExhaustedBeforeGroupB() {
        // groupA=Q1×2条, groupB=Q0×10条, maxDisplayItems=4
        // groupA 有2条Q1, groupB 有10条Q0
        // 应先取完groupA的2条，剩余2空位取groupB的Q0
        List<DisplayItem> groupA = new ArrayList<>();
        groupA.add(createItem(1, 1));
        groupA.add(createItem(2, 1));

        List<DisplayItem> groupB = new ArrayList<>();
        for (int i = 0; i < 10; i++) groupB.add(createItem(100 + i, 0));

        List<DisplayItem> result = QuadrantRatioFilter.apply(groupA, groupB, 4);
        assertEquals(4, result.size());
        // 前2条来自groupA(Q1)
        assertEquals(1, result.get(0).task.quadrant);
        assertEquals(1, result.get(1).task.quadrant);
        // 后2条来自groupB(Q0)
        assertEquals(0, result.get(2).task.quadrant);
        assertEquals(0, result.get(3).task.quadrant);
    }
}
