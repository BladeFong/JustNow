package com.nearby.justnow.widget;

import android.content.Context;
import android.content.res.Resources;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.nearby.justnow.R;
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
import java.lang.reflect.Method;
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

    // ==================== calculateMaxItems（反射调用 private 方法） ====================

    private static int invokeCalculateMaxItems(int widgetHeightDp, Resources res) throws Exception {
        Method method = WidgetUpdateHelper.class.getDeclaredMethod(
                "calculateMaxItems", int.class, Resources.class);
        method.setAccessible(true);
        return (int) method.invoke(null, widgetHeightDp, res);
    }

    @Test
    public void calculateMaxItems_defaultWidgetHeight_returnsAtLeastTwoColumns() throws Exception {
        Resources res = mContext.getResources();
        int result = invokeCalculateMaxItems(200, res);
        assertTrue("至少返回 2 列", result >= 2);
    }

    @Test
    public void calculateMaxItems_smallHeight_returnsMinimumTwo() throws Exception {
        Resources res = mContext.getResources();
        int result = invokeCalculateMaxItems(10, res);
        assertEquals("最小 Widget 高度应返回 2 项", 2, result);
    }

    @Test
    public void calculateMaxItems_largeHeight_returnsProportionallyMore() throws Exception {
        Resources res = mContext.getResources();
        int result = invokeCalculateMaxItems(600, res);
        assertTrue("大高度应返回更多项", result > 4);
    }

    @Test
    public void calculateMaxItems_tallerHeight_givesMoreOrEqualItems() throws Exception {
        Resources res = mContext.getResources();
        int smallResult = invokeCalculateMaxItems(200, res);
        int largeResult = invokeCalculateMaxItems(400, res);
        assertTrue("高度越大 item 数应 >= 高度小时", largeResult >= smallResult);
    }

    @Test
    public void calculateMaxItems_usesDimensRowHeight_returnsEvenColumns() throws Exception {
        Resources res = mContext.getResources();
        int result = invokeCalculateMaxItems(300, res);
        assertEquals("结果应为 2 的倍数", 0, result % 2);
    }

    @Test
    public void calculateMaxItems_rowHeightFromDimens_consistentWithResource() throws Exception {
        Resources res = mContext.getResources();
        int rowHeightPx = res.getDimensionPixelSize(R.dimen.task_content_row_height);
        float density = res.getDisplayMetrics().density;
        int rowHeightDp = (int) (rowHeightPx / density);

        // 给定 widgetHeightDp，验证结果与 dimens 行高一致
        // 高度 = topBar + padding*2 + rowHeight*N → N 行，2N 列
        int topBarHeightDp = 32 + 12; // widget_action_bar_height + marginBottom: 32 + 12 = 44dp
        int contentPaddingDp = 16;    // widget_content_padding: 16dp
        int usableHeight = 300 - topBarHeightDp - contentPaddingDp * 2;
        int expectedRows = Math.max(1, usableHeight / rowHeightDp);
        int expectedItems = expectedRows * 2; // WIDGET_COLUMN_COUNT = 2

        int result = invokeCalculateMaxItems(300, res);
        assertEquals(expectedItems, result);
    }

    // ==================== buildTaskRow 字体档位与行高（反射 + RemoteViews.apply） ====================

    private RemoteViews invokeBuildTaskRow(Context context, TaskEntity task, TagEntity tag,
                                           int widgetId, long filterTagId) throws Exception {
        DisplayItem item = new DisplayItem(task, tag);
        Resources res = context.getResources();
        Method method = WidgetUpdateHelper.class.getDeclaredMethod(
                "buildTaskRow", Context.class, DisplayItem.class, Resources.class, int.class, long.class);
        method.setAccessible(true);
        return (RemoteViews) method.invoke(null, context, item, res, widgetId, filterTagId);
    }

    @Test
    @Config(fontScale = 1.0f)
    public void buildTaskRow_fontScaleNormal_appliesToView() throws Exception {
        TaskEntity task = createTask(100, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0);
        assertNotNull("RemoteViews 不应为 null", row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.widget.TextView tvContent = parent.findViewById(R.id.tv_task_content);
        android.widget.TextView tvTag = parent.findViewById(R.id.tv_tag);
        android.widget.TextView tvBadge = parent.findViewById(R.id.tv_focus_badge);

        assertNotNull("tv_task_content 应存在", tvContent);
        assertNotNull("tv_tag 应存在", tvTag);
        assertNotNull("tv_focus_badge 应存在", tvBadge);

        // fontScale=1.0 → taskContentSp=18, tagSp=18, focusBadgeSp=16
        assertEquals("fontScale=1.0 时 tv_task_content 字号应为 18sp", 18f,
                tvContent.getTextSize() / ctx.getResources().getDisplayMetrics().scaledDensity, 0.5f);
        assertEquals("fontScale=1.0 时 tv_tag 字号应为 18sp", 18f,
                tvTag.getTextSize() / ctx.getResources().getDisplayMetrics().scaledDensity, 0.5f);
        assertEquals("fontScale=1.0 时 tv_focus_badge 字号应为 16sp", 16f,
                tvBadge.getTextSize() / ctx.getResources().getDisplayMetrics().scaledDensity, 0.5f);
    }

    @Test
    @Config(fontScale = 1.1f)
    public void buildTaskRow_fontScaleMedium_appliesToView() throws Exception {
        TaskEntity task = createTask(101, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0);
        assertNotNull(row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.widget.TextView tvContent = parent.findViewById(R.id.tv_task_content);

        assertNotNull(tvContent);
        // fontScale=1.1 (<= 1.15) → taskContentSp=16
        float actualSp = tvContent.getTextSize() / ctx.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("fontScale=1.1 时 tv_task_content 字号应为 16sp", 16f, actualSp, 0.5f);
    }

    @Test
    @Config(fontScale = 1.5f)
    public void buildTaskRow_fontScaleLarge_appliesToView() throws Exception {
        TaskEntity task = createTask(102, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0);
        assertNotNull(row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.widget.TextView tvContent = parent.findViewById(R.id.tv_task_content);

        assertNotNull(tvContent);
        // fontScale=1.5 (> 1.15) → taskContentSp=14
        float actualSp = tvContent.getTextSize() / ctx.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("fontScale=1.5 时 tv_task_content 字号应为 14sp", 14f, actualSp, 0.5f);
    }

    @Test
    @Config(fontScale = 1.15f)
    public void buildTaskRow_fontScaleBoundary1_15_usesMediumSizes() throws Exception {
        TaskEntity task = createTask(103, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0);
        assertNotNull(row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.widget.TextView tvContent = parent.findViewById(R.id.tv_task_content);

        assertNotNull(tvContent);
        float actualSp = tvContent.getTextSize() / ctx.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("fontScale=1.15 边界值应走二档 (16sp)", 16f, actualSp, 0.5f);
    }

    @Test
    public void buildTaskRow_nullTag_showsEmptyTagText() throws Exception {
        TaskEntity task = createTask(200, null);
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, null, 1, 0);
        assertNotNull(row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.widget.TextView tvTag = parent.findViewById(R.id.tv_tag);
        assertNotNull("tv_tag 应存在（空文本但 View 可见）", tvTag);
    }

    @Test
    public void buildTaskRow_noFocusMinutes_showsChoreLabel() throws Exception {
        TaskEntity task = createTask(201, null);
        task.focusMinutes = 0;
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, null, 1, 0);
        assertNotNull(row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.widget.TextView tvBadge = parent.findViewById(R.id.tv_focus_badge);
        assertNotNull(tvBadge);
        assertEquals("零专注分钟应显示杂务标签",
                ctx.getString(R.string.s_chore_label), tvBadge.getText().toString());
    }

    @Test
    public void buildTaskRow_rowHeightSetFromTaskContentRowHeightDimens() throws Exception {
        TaskEntity task = createTask(300, null);
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        int expectedRowHeightPx = ctx.getResources().getDimensionPixelSize(R.dimen.task_content_row_height);

        RemoteViews row = invokeBuildTaskRow(ctx, task, null, 1, 0);
        assertNotNull(row);

        android.widget.FrameLayout parent = new android.widget.FrameLayout(ctx);
        row.apply(ctx, parent);
        android.view.View llTaskItem = parent.findViewById(R.id.ll_task_item);
        assertNotNull("ll_task_item 应存在", llTaskItem);
        assertEquals("行高应为 task_content_row_height dimens",
                expectedRowHeightPx, llTaskItem.getMinimumHeight());
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
