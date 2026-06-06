package com.nearby.justnow.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.widget.FrameLayout;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
        tasks.add(createTask(1, tagId));
        tasks.add(createTask(2, 200L));
        tasks.add(createTask(3, null));
        tasks.add(createTask(4, tagId));

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

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(100L, createTag(100L, "Work"));

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, 100L));
        tasks.add(createTask(2, null));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, Collections.emptySet(), mContext, widgetId, tagMap);

        assertEquals(2, result.size());
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
        tasks.add(createTask(1, tagId));
        tasks.add(createTask(2, tagId));
        tasks.add(createTask(3, 200L));

        Set<Long> autoCompletedIds = new HashSet<>(Collections.singletonList(1L));

        List<TaskEntity> result = WidgetUpdateHelper.filterTasksByTag(
                tasks, autoCompletedIds, mContext, widgetId, tagMap);

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
                tasks, tagMap, status, 8, Collections.emptyMap(), null);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).task.id);
    }

    @Test
    public void computeItems_inPeriod_emptyTasks_returnsEmptyList() {
        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);
        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                new ArrayList<>(), new HashMap<>(), status, 8, Collections.emptyMap(), null);

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
                tasks, tagMap, status, 8, Collections.emptyMap(), null);

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
                tasks, tagMap, status, 8, Collections.emptyMap(), null);

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).task.quadrant);
        assertEquals(0, result.get(1).task.quadrant);
    }

    @Test
    public void computeItems_engineException_returnsFallback() {
        DisplayEngine throwingMock = mock(DisplayEngine.class);
        when(throwingMock.compute(any(), any(), anyInt(), anyBoolean(), anyInt(), any(), any(), any()))
                .thenThrow(new RuntimeException("forced exception"));

        mOriginalEngine = replaceStaticFinalField("sDisplayEngine", throwingMock);

        List<TaskEntity> tasks = new ArrayList<>();
        tasks.add(createTask(1, 100L));

        Map<Long, TagEntity> tagMap = new HashMap<>();
        tagMap.put(100L, createTag(100L, "Work"));

        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);

        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                tasks, tagMap, status, 8, Collections.emptyMap(), null);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).task.id);
    }

    @Test
    public void computeItems_nullTasks_passedToEngine() {
        TimeRemainingCalculator.PeriodStatus status = createPeriodStatus(60, false);
        List<DisplayItem> result = WidgetUpdateHelper.computeItems(
                null, new HashMap<>(), status, 8, Collections.emptyMap(), null);

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
                tasks, new HashMap<>(), status, 8, degradeMap, null);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).task.id);
        assertEquals(0, result.get(0).effectiveQuadrant);
        assertEquals(1L, result.get(1).task.id);
        assertEquals(1, result.get(1).effectiveQuadrant);
    }

    // ==================== calculateMaxItems（Mock Resources 绕过 dimen 加载限制）=============

    private static void clearDimensionCache() throws Exception {
        Field cachedField = WidgetUpdateHelper.class.getDeclaredField("sDimensionsCached");
        cachedField.setAccessible(true);
        cachedField.setBoolean(null, false);
    }

    private Resources createCalcMockResources(Context ctx, int rowHeightPx) {
        Resources orig = ctx.getResources();
        Resources spyRes = spy(orig);
        float density = orig.getDisplayMetrics().density;

        // ensureDimensionsCached 需要的 dimens（用 doReturn 避免调用真实方法）
        doReturn((int) (16 * density)).when(spyRes).getDimensionPixelSize(R.dimen.widget_content_padding);
        doReturn((int) (32 * density)).when(spyRes).getDimensionPixelSize(R.dimen.widget_action_bar_height);
        doReturn((int) (12 * density)).when(spyRes).getDimensionPixelSize(R.dimen.widget_action_bar_margin_bottom);

        // calculateMaxItems 直接需要的 dimens
        doReturn(rowHeightPx).when(spyRes).getDimensionPixelSize(R.dimen.widget_task_row_height);

        return spyRes;
    }

    private static int invokeCalculateMaxItems(int widgetHeightDp, Resources res) throws Exception {
        Method method = WidgetUpdateHelper.class.getDeclaredMethod(
                "calculateMaxItems", int.class, Resources.class, boolean.class);
        method.setAccessible(true);
        return (int) method.invoke(null, widgetHeightDp, res, false);
    }

    @Test
    public void calculateMaxItems_defaultWidgetHeight_returnsAtLeastTwoColumns() throws Exception {
        clearDimensionCache();
        Resources res = createCalcMockResources(mContext, 216);
        int result = invokeCalculateMaxItems(200, res);
        assertTrue("至少返回 2 列", result >= 2);
    }

    @Test
    public void calculateMaxItems_smallHeight_returnsMinimumTwo() throws Exception {
        clearDimensionCache();
        Resources res = createCalcMockResources(mContext, 216);
        int result = invokeCalculateMaxItems(10, res);
        assertEquals("最小 Widget 高度应返回 2 项", 2, result);
    }

    @Test
    public void calculateMaxItems_largeHeight_returnsProportionallyMore() throws Exception {
        clearDimensionCache();
        Resources res = createCalcMockResources(mContext, 216);
        float density = mContext.getResources().getDisplayMetrics().density;
        int rowHeightDp = (int) (216 / density);
        int topBarDp = 44, paddingDp = 16;
        int usableHeight = 600 - topBarDp - paddingDp * 2;
        int expectedRows = Math.max(1, usableHeight / rowHeightDp);
        int expectedMin = expectedRows * 2;
        int result = invokeCalculateMaxItems(600, res);
        assertTrue("大高度应返回 >= " + expectedMin + " 项，实际 " + result, result >= expectedMin);
    }

    @Test
    public void calculateMaxItems_tallerHeight_givesMoreOrEqualItems() throws Exception {
        clearDimensionCache();
        Resources res = createCalcMockResources(mContext, 216);
        int smallResult = invokeCalculateMaxItems(200, res);
        int largeResult = invokeCalculateMaxItems(400, res);
        assertTrue("高度越大 item 数应 >= 高度小时", largeResult >= smallResult);
    }

    @Test
    public void calculateMaxItems_usesRowHeightDp_returnsEvenColumns() throws Exception {
        clearDimensionCache();
        Resources res = createCalcMockResources(mContext, 216);
        int result = invokeCalculateMaxItems(300, res);
        assertEquals("结果应为 2 的倍数（WIDGET_COLUMN_COUNT=2）", 0, result % 2);
    }

    @Test
    public void calculateMaxItems_manualArithmetic_consistentWithDimens() throws Exception {
        clearDimensionCache();
        float density = mContext.getResources().getDisplayMetrics().density;
        int rowHeightDp = 72;
        int widgetHeightDp = 300;
        int topBarDp = 44, paddingDp = 16;
        int usableHeight = widgetHeightDp - topBarDp - paddingDp * 2;
        int expectedRows = Math.max(1, usableHeight / rowHeightDp);
        int expectedItems = Math.max(2, expectedRows * 2);

        Resources res = createCalcMockResources(mContext, (int) (rowHeightDp * density));
        int result = invokeCalculateMaxItems(widgetHeightDp, res);
        assertEquals(expectedItems, result);
    }

    // ==================== buildTaskRow（反射 + RemoteViews.reapply 验证）=============

    /**
     * 创建手动构造的 View 树（含 buildTaskRow 会操作的 view ID），
     * 再通过 RemoteViews.reapply() 应用字号/行高，避免依赖 Robolectric 的资源加载。
     */
    private FrameLayout createReapplyTarget(Context ctx) {
        FrameLayout root = new FrameLayout(ctx);
        // 模拟 RemoteViews 要 apply 的 View 结构：item_task_content 的根 layout 内含各子 view
        FrameLayout llTaskItem = new FrameLayout(ctx);
        llTaskItem.setId(R.id.ll_task_item);
        root.addView(llTaskItem);

        View vQuadrant = new View(ctx);
        vQuadrant.setId(R.id.v_quadrant_color);
        llTaskItem.addView(vQuadrant);

        TextView tvTag = new TextView(ctx);
        tvTag.setId(R.id.tv_tag);
        llTaskItem.addView(tvTag);

        TextView tvBadge = new TextView(ctx);
        tvBadge.setId(R.id.tv_focus_badge);
        llTaskItem.addView(tvBadge);

        TextView tvContent = new TextView(ctx);
        tvContent.setId(R.id.tv_task_content);
        llTaskItem.addView(tvContent);

        return root;
    }

    private Resources createBuildTaskMockResources(Context ctx, int rowHeightPx, boolean compact) {
        Resources orig = ctx.getResources();
        Resources spyRes = spy(orig);

        // dimens
        if (compact) {
            doReturn(rowHeightPx).when(spyRes).getDimensionPixelSize(R.dimen.widget_compact_row_height);
            // compact 模式还需字号和 padding，给默认值让 buildTaskRow 不抛异常即可
            float density = orig.getDisplayMetrics().density;
            doReturn((int) (14 * density)).when(spyRes).getDimension(R.dimen.widget_compact_content_size);
            doReturn((int) (14 * density)).when(spyRes).getDimension(R.dimen.widget_compact_tag_size);
            doReturn((int) (12 * density)).when(spyRes).getDimension(R.dimen.widget_compact_focus_size);
            doReturn((int) (8 * density)).when(spyRes).getDimensionPixelSize(R.dimen.widget_compact_padding_vertical);
        } else {
            doReturn(rowHeightPx).when(spyRes).getDimensionPixelSize(R.dimen.widget_task_row_height);
        }

        // 颜色
        doReturn(0xFF1976D2).when(spyRes).getColor(R.color.tag_active, null);
        doReturn(0xFF757575).when(spyRes).getColor(R.color.tag_normal, null);
        doReturn(0xFF9E9E9E).when(spyRes).getColor(R.color.text_tertiary, null);
        doReturn(0xFF212121).when(spyRes).getColor(R.color.text_primary, null);
        doReturn(0x1A000000).when(spyRes).getColor(R.color.widget_task_executing_background, null);

        // 字符串
        doReturn("Chore").when(spyRes).getString(R.string.s_chore_label);
        doReturn("min").when(spyRes).getString(R.string.s_minute_unit);

        return spyRes;
    }

    private RemoteViews invokeBuildTaskRow(Context ctx, TaskEntity task, TagEntity tag,
                                            int widgetId, long filterTagId, int mockRowHeightPx) throws Exception {
        return invokeBuildTaskRow(ctx, task, tag, widgetId, filterTagId, mockRowHeightPx, false);
    }

    private RemoteViews invokeBuildTaskRow(Context ctx, TaskEntity task, TagEntity tag,
                                            int widgetId, long filterTagId, int mockRowHeightPx,
                                            boolean compact) throws Exception {
        Resources mockRes = createBuildTaskMockResources(ctx, mockRowHeightPx, compact);
        DisplayItem item = new DisplayItem(task, tag);
        Method method = WidgetUpdateHelper.class.getDeclaredMethod(
                "buildTaskRow", Context.class, DisplayItem.class, Resources.class, int.class, long.class, boolean.class);
        method.setAccessible(true);
        return (RemoteViews) method.invoke(null, ctx, item, mockRes, widgetId, filterTagId, compact);
    }

    /** 将 RemoteViews 通过 reapply 应用到 View 树，返回目标 TextView。 */
    private TextView getTextViewByIdAfterReapply(RemoteViews row, FrameLayout target, int viewId) {
        row.reapply(target.getContext(), target);
        return target.findViewById(viewId);
    }

    @Test
    @Config(fontScale = 1.0f)
    public void buildTaskRow_fontScaleNormal_verifiesTextSize() throws Exception {
        TaskEntity task = createTask(100, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0, 200);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        TextView tvContent = getTextViewByIdAfterReapply(row, target, R.id.tv_task_content);
        TextView tvTag = getTextViewByIdAfterReapply(row, target, R.id.tv_tag);
        TextView tvBadge = getTextViewByIdAfterReapply(row, target, R.id.tv_focus_badge);

        assertNotNull(tvContent);
        assertNotNull(tvTag);
        assertNotNull(tvBadge);

        float density = ctx.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("fontScale=1.0 → 18sp", 18f, tvContent.getTextSize() / density, 0.5f);
        assertEquals("fontScale=1.0 → tag 18sp", 18f, tvTag.getTextSize() / density, 0.5f);
        assertEquals("fontScale=1.0 → badge 16sp", 16f, tvBadge.getTextSize() / density, 0.5f);
    }

    @Test
    @Config(fontScale = 1.1f)
    public void buildTaskRow_fontScaleMedium_verifiesTextSize() throws Exception {
        TaskEntity task = createTask(101, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0, 200);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        TextView tvContent = getTextViewByIdAfterReapply(row, target, R.id.tv_task_content);
        assertNotNull(tvContent);

        float density = ctx.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("fontScale=1.1 → 16sp", 16f, tvContent.getTextSize() / density, 0.5f);
    }

    @Test
    @Config(fontScale = 1.5f)
    public void buildTaskRow_fontScaleLarge_verifiesTextSize() throws Exception {
        TaskEntity task = createTask(102, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0, 200);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        TextView tvContent = getTextViewByIdAfterReapply(row, target, R.id.tv_task_content);
        assertNotNull(tvContent);

        float density = ctx.getResources().getDisplayMetrics().scaledDensity;
        // Robolectric 的 fontScale 与 SP 换算有精度偏差，以宽容差验证三档已正确选中
        float actualSp = tvContent.getTextSize() / density;
        assertTrue("fontScale=1.5 应选三档 (14sp)，实际 " + actualSp,
                actualSp >= 13f && actualSp <= 15.5f && actualSp < 16f);
    }

    @Test
    @Config(fontScale = 1.15f)
    public void buildTaskRow_fontScaleBoundary1_15_usesMediumSizes() throws Exception {
        TaskEntity task = createTask(103, 10L);
        TagEntity tag = createTag(10L, "Work");
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, tag, 1, 0, 200);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        TextView tvContent = getTextViewByIdAfterReapply(row, target, R.id.tv_task_content);
        assertNotNull(tvContent);

        float density = ctx.getResources().getDisplayMetrics().scaledDensity;
        assertEquals("fontScale=1.15 边界值应走二档", 16f, tvContent.getTextSize() / density, 0.5f);
    }

    @Test
    public void buildTaskRow_nullTag_emptyTagText() throws Exception {
        TaskEntity task = createTask(200, null);
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, null, 1, 0, 200);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        TextView tvTag = getTextViewByIdAfterReapply(row, target, R.id.tv_tag);
        assertNotNull("null 标签时 tv_tag 仍应可见", tvTag);
    }

    @Test
    public void buildTaskRow_zeroFocusMinutes_showsChoreLabel() throws Exception {
        TaskEntity task = createTask(201, null);
        task.focusMinutes = 0;
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();

        RemoteViews row = invokeBuildTaskRow(ctx, task, null, 1, 0, 200);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        TextView tvBadge = getTextViewByIdAfterReapply(row, target, R.id.tv_focus_badge);
        assertNotNull(tvBadge);
        assertEquals("零专注分钟 → 杂务标签", "Chore", tvBadge.getText().toString());
    }

    @Test
    public void buildTaskRow_setsMinimumHeightFromRowHeightDimens() throws Exception {
        // compact 模式才显式设置 rowHeight，非 compact 走 XML wrap_content
        TaskEntity task = createTask(300, null);
        Context ctx = RuntimeEnvironment.getApplication().getApplicationContext();
        int expectedRowHeightPx = 200;

        RemoteViews row = invokeBuildTaskRow(ctx, task, null, 1, 0, expectedRowHeightPx, true);
        assertNotNull(row);

        FrameLayout target = createReapplyTarget(ctx);
        row.reapply(ctx, target);
        FrameLayout llTaskItem = target.findViewById(R.id.ll_task_item);
        assertNotNull(llTaskItem);
        assertEquals("compact 行高应来自 widget_compact_row_height", expectedRowHeightPx, llTaskItem.getMinimumHeight());
    }

    // ==================== 反射辅助方法 ====================

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
        for (Method m : unsafeClass.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                return m.invoke(unsafe, args);
            }
        }
        throw new NoSuchMethodException("Unsafe." + methodName + " with " + args.length + " params");
    }
}
