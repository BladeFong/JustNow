package com.nearby.justnow.widget;

import android.content.Context;

import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.ui.engine.DisplayEngine;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WidgetUpdateHelper 单元测试 — filterTasksByTag 和 computeItems 纯数据逻辑。
 * filterTasksByTag 需要 Robolectric（WidgetFilterStore 读写 SharedPreferences）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WidgetUpdateHelperTest {

    private Context mContext;
    /** 反射替换 sDisplayEngine 前的原始实例，tearDown 中恢复 */
    private DisplayEngine mOriginalEngine;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        // 每个测试用例独立清空 WidgetFilterStore 的 SharedPreferences
        mContext.getSharedPreferences("widget_filter_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @After
    public void tearDown() {
        if (mOriginalEngine != null) {
            restoreStaticFinalField("sDisplayEngine", mOriginalEngine);
            mOriginalEngine = null;
        }
    }

    // ==================== filterTasksByTag ====================

    private static TaskEntity createTask(long id, Long tagId) {
        TaskEntity t = new TaskEntity();
        t.id = id;
        t.content = "task_" + id;
        t.tagId = tagId;
        t.quadrant = 0;
        t.focusMinutes = 30;
        t.createdAt = System.currentTimeMillis();
        return t;
    }

    private static TagEntity createTag(long id, String name) {
        TagEntity tag = new TagEntity();
        tag.id = id;
        tag.name = name;
        tag.color = 0xFF0000;
        return tag;
    }

    @Test
    public void filterTasksByTag_nullTasks_returnsEmptyList() {
        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                null, Collections.emptySet(), mContext, 1, new HashMap<>());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void filterTasksByTag_emptyTasks_returnsEmptyList() {
        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                new ArrayList<>(), Collections.emptySet(), mContext, 1, new HashMap<>());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void filterTasksByTag_autoComplete_removesAutoCompletedTasks() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, null));
        tasks.add(createTask(2, null));
        tasks.add(createTask(3, null));

        Set<Long> autoCompletedIds = new HashSet<>(Arrays.asList(1L, 3L));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, autoCompletedIds, mContext, 1, new HashMap<>());

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id);
    }

    @Test
    public void filterTasksByTag_autoCompleteNullSet_noRemoval() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, null));
        tasks.add(createTask(2, null));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, null, mContext, 1, new HashMap<>());

        assertEquals(2, result.size());
    }

    @Test
    public void filterTasksByTag_autoCompleteEmptySet_noRemoval() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, null));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, new HashSet<>(), mContext, 1, new HashMap<>());

        assertEquals(1, result.size());
    }

    @Test
    public void filterTasksByTag_noTagFilter_returnsAllTasks() {
        // 未设置 filterTagId（默认 0）→ 返回全部
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, null));
        tasks.add(createTask(2, 100L));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, Collections.emptySet(), mContext, 1, new HashMap<>());

        assertEquals(2, result.size());
    }

    @Test
    public void filterTasksByTag_withExistingTag_filtersCorrectly() {
        long tagId = 100L;
        int widgetId = 1;

        new WidgetFilterStore(mContext).setFilterTagId(widgetId, tagId);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(tagId, createTag(tagId, "Work"));
        tagMap.put(200L, createTag(200L, "Personal"));

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, tagId));   // 匹配 → 保留
        tasks.add(createTask(2, 200L));    // 不匹配 → 过滤
        tasks.add(createTask(3, null));    // null tagId → 过滤
        tasks.add(createTask(4, tagId));   // 匹配 → 保留

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, Collections.emptySet(), mContext, widgetId, tagMap);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id);
        assertEquals(4L, result.get(1).id);
    }

    @Test
    public void filterTasksByTag_tagNotFoundInMap_clearsFilterAndReturnsAll() {
        long deletedTagId = 999L;
        int widgetId = 1;

        new WidgetFilterStore(mContext).setFilterTagId(widgetId, deletedTagId);

        // tagMap 不含 filterTagId
        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(100L, createTag(100L, "Work"));

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, 100L));
        tasks.add(createTask(2, null));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, Collections.emptySet(), mContext, widgetId, tagMap);

        assertEquals(2, result.size());
        // 验证 filter 被清除
        assertEquals(0, new WidgetFilterStore(mContext).getFilterTagId(widgetId));
    }

    @Test
    public void filterTasksByTag_tagNameNull_clearsFilterAndReturnsAll() {
        long tagId = 100L;
        int widgetId = 1;

        new WidgetFilterStore(mContext).setFilterTagId(widgetId, tagId);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        TagEntity tagWithNullName = createTag(tagId, "Work");
        tagWithNullName.name = null;
        tagMap.put(tagId, tagWithNullName);

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, tagId));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, Collections.emptySet(), mContext, widgetId, tagMap);

        assertEquals(1, result.size());
        assertEquals(0, new WidgetFilterStore(mContext).getFilterTagId(widgetId));
    }

    @Test
    public void filterTasksByTag_tagNameEmpty_clearsFilterAndReturnsAll() {
        long tagId = 100L;
        int widgetId = 1;

        new WidgetFilterStore(mContext).setFilterTagId(widgetId, tagId);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(tagId, createTag(tagId, ""));

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, tagId));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, Collections.emptySet(), mContext, widgetId, tagMap);

        assertEquals(1, result.size());
        assertEquals(0, new WidgetFilterStore(mContext).getFilterTagId(widgetId));
    }

    @Test
    public void filterTasksByTag_autoCompleteCombinedWithTagFilter() {
        long tagId = 100L;
        int widgetId = 1;

        new WidgetFilterStore(mContext).setFilterTagId(widgetId, tagId);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(tagId, createTag(tagId, "Work"));

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, tagId));   // has tag, but auto-completed
        tasks.add(createTask(2, tagId));   // has tag, not auto-completed → kept
        tasks.add(createTask(3, 200L));    // wrong tag → filtered

        Set<Long> autoCompletedIds = new HashSet<>(Collections.singletonList(1L));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, autoCompletedIds, mContext, widgetId, tagMap);

        // Only task 2 should remain (has correct tag + not auto-completed)
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id);
    }

    // ==================== computeItems ====================

    private static TimeRemainingCalculator.PeriodStatus createPeriodStatus(
            int remainingMinutes, boolean reverseQuadrant) {
        TimeRemainingCalculator.PeriodStatus status = new TimeRemainingCalculator.PeriodStatus();
        TimePeriodEntity period = new TimePeriodEntity();
        period.nameKey = "morning";
        period.startMinute = 480;
        period.endMinute = 720;
        period.reverseQuadrant = reverseQuadrant;
        status.period = period;
        status.endMinute = 720;
        status.remainingMinutes = remainingMinutes;
        return status;
    }

    private static TimeRemainingCalculator.PeriodStatus createOutOfPeriodStatus() {
        return new TimeRemainingCalculator.PeriodStatus();
    }

    @Test
    public void computeItems_inPeriod_usesRemainingMinutes() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, 100L));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(100L, createTag(100L, "Work"));

        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);

        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                tasks, tagMap, status, 8, Collections.emptyMap());

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).task.id);
    }

    @Test
    public void computeItems_inPeriod_emptyTasks_returnsEmptyList() {
        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);
        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                new ArrayList<>(), new HashMap<>(), status, 8, Collections.emptyMap());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void computeItems_outOfPeriod_usesZeroRemainingMinutes() {
        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, 100L));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(100L, createTag(100L, "Work"));

        TimeRemainingCalculator.PeriodStatus status = createOutOfPeriodStatus();
        assertFalse("precondition: not in period", status.isInPeriod());

        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                tasks, tagMap, status, 8, Collections.emptyMap());

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void computeItems_reverseQuadrant_passedToEngine() {
        List<TaskEntity> tasks = new ArrayList<>();
        TaskEntity q0 = createTask(1, null);
        q0.quadrant = 0;
        TaskEntity q3 = createTask(2, null);
        q3.quadrant = 3;
        tasks.add(q0);
        tasks.add(q3);

        Map<Long, TagEntity> tagMap = new HashMap<>();
        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(120, true);

        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                tasks, tagMap, status, 8, Collections.emptyMap());

        assertEquals(2, result.size());
        // reverseQuadrant = true → Q3 排在 Q0 前面
        assertEquals(3, result.get(0).task.quadrant);
        assertEquals(0, result.get(1).task.quadrant);
    }

    @Test
    public void computeItems_engineException_returnsFallback() {
        DisplayEngine throwingMock = mock(DisplayEngine.class);
        when(throwingMock.compute(any(), any(), anyInt(), anyBoolean(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("forced exception"));

        mOriginalEngine = replaceStaticFinalField("sDisplayEngine", throwingMock);

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, 100L));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(100L, createTag(100L, "Work"));

        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);

        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                tasks, tagMap, status, 8, Collections.emptyMap());

        // Fallback: buildFallbackList 按创建时间倒序返回
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).task.id);
    }

    @Test
    public void computeItems_nullTasks_passedToEngine() {
        // Engine handles null tasks internally
        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);
        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                null, new HashMap<>(), status, 8, Collections.emptyMap());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void computeItems_degradeMap_passedToEngine() {
        List<TaskEntity> tasks = new ArrayList<>();
        TaskEntity degraded = createTask(1, null);
        degraded.quadrant = 0;
        TaskEntity normal = createTask(2, null);
        normal.quadrant = 0;
        tasks.add(degraded);
        tasks.add(normal);

        TaskQuadrantDegradeEntity degrade = new TaskQuadrantDegradeEntity();
        degrade.taskId = 1L;
        degrade.originalQuadrant = 0;
        degrade.recoverMs = System.currentTimeMillis() + 3600000L;
        Map<Long, TaskQuadrantDegradeEntity> degradeMap = new HashMap<>();
        degradeMap.put(1L, degrade);

        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(120, false);

        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                tasks, new HashMap<>(), status, 8, degradeMap);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).task.id);
        assertEquals(0, result.get(0).effectiveQuadrant);
        assertEquals(1L, result.get(1).task.id);
        assertEquals(1, result.get(1).effectiveQuadrant);
    }

    // ==================== 反射辅助方法 ====================

    /** 通过 Unsafe 绕过 final 限制替换 static 字段（纯运行时反射，避免编译期模块限制）。 */
    private static DisplayEngine replaceStaticFinalField(String fieldName, DisplayEngine newValue) {
        try {
            Object unsafe = getUnsafeInstance();
            Field field = WidgetUpdateHelper.class.getDeclaredField(fieldName);
            long offset = (Long) invokeUnsafe(unsafe, "staticFieldOffset", field);
            Object base = invokeUnsafe(unsafe, "staticFieldBase", field);
            DisplayEngine original = (DisplayEngine) invokeUnsafe(unsafe, "getObject", base, offset);
            invokeUnsafe(unsafe, "putObject", base, offset, newValue);
            return original;
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace static final field: " + fieldName, e);
        }
    }

    private static void restoreStaticFinalField(String fieldName, DisplayEngine value) {
        try {
            Object unsafe = getUnsafeInstance();
            Field field = WidgetUpdateHelper.class.getDeclaredField(fieldName);
            long offset = (Long) invokeUnsafe(unsafe, "staticFieldOffset", field);
            Object base = invokeUnsafe(unsafe, "staticFieldBase", field);
            invokeUnsafe(unsafe, "putObject", base, offset, value);
        } catch (Exception e) {
            System.err.println("Warning: Failed to restore field " + fieldName + ": " + e.getMessage());
        }
    }

    private static Object getUnsafeInstance() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return theUnsafe.get(null);
    }

    private static Object invokeUnsafe(Object unsafe, String methodName, Object... args) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        for (java.lang.reflect.Method m : unsafeClass.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                return m.invoke(unsafe, args);
            }
        }
        throw new NoSuchMethodException("Unsafe." + methodName + " with " + args.length + " params");
    }
}
