package com.nearby.justnow.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.ui.engine.DisplayEngine;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.ui.main.MainActivity;
import com.nearby.justnow.ui.period.PeriodTextResolver;
import com.nearby.justnow.util.PermissionHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Widget 更新工具类 —— 抽取公共更新逻辑，供 JustNowWidgetProvider 调用。
 * 任务列表通过 TableLayout + addView 逐条拼接 TableRow，列宽自动对齐。
 */
public final class WidgetUpdateHelper {

    public static final String ACTION_WIDGET_TAG_CLICK =
        "com.nearby.justnow.ACTION_WIDGET_TAG_CLICK";
    public static final String EXTRA_APP_WIDGET_ID = "app_widget_id";
    public static final String EXTRA_TAG_ID = "tag_id";

    /** Widget 任务区列数 */
    private static final int WIDGET_COLUMN_COUNT = 2;

    private static volatile boolean sDimensionsCached;
    private static int sContentPaddingDp;
    private static int sTopBarDp;

    private static void ensureDimensionsCached(Resources res) {
        if (sDimensionsCached) return;
        synchronized (WidgetUpdateHelper.class) {
            if (sDimensionsCached) return;
            float density = res.getDisplayMetrics().density;
            sContentPaddingDp = (int) (res.getDimensionPixelSize(R.dimen.widget_content_padding) / density);
            sTopBarDp = (int) ((res.getDimensionPixelSize(R.dimen.widget_action_bar_height)
                    + res.getDimensionPixelSize(R.dimen.widget_action_bar_margin_bottom)) / density);
            sDimensionsCached = true;
        }
    }

    private static final int[] sQuadrantColors = {
        0xFFC62828, // 紧急重要
        0xFFE65100, // 紧急不重要
        0xFF2E7D32, // 不紧急重要
        0xFF546E7A, // 不紧急不重要
    };

    /** DisplayEngine 无状态，复用静态单例避免重复创建。 */
    private static final DisplayEngine sDisplayEngine = new DisplayEngine();

    private WidgetUpdateHelper() {}

    // ==================== 核心更新方法 ====================

    /**
     * 更新单个 Widget 实例：设置顶部栏状态文本、TableLayout 任务行，其余在后台计算时段。
     *
     * @param options 当前 Widget 尺寸信息，用于动态计算最大显示条数
     */
    public static void updateWidget(Context context, AppWidgetManager manager, int widgetId, Bundle options) {
        SizeF sizes = options.getParcelable(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF.class);
        int heightDp;
        if (sizes != null) {
            heightDp = (int) sizes.getHeight();
        } else {
            // 根据 Launcher 横竖屏选择对应维度的高度
            int orientation = context.getResources().getConfiguration().orientation;
            String heightKey;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                heightKey = AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT;
            } else {
                heightKey = AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT;
            }
            heightDp = getWidgetOption(options, heightKey, 200);
        }
        updateWidget(context, manager, widgetId, heightDp);
    }

    /**
     * 按当前高度更新 Widget。
     */
    public static void updateWidget(Context context, AppWidgetManager manager, int widgetId, int widgetHeightDp) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_justnow);

        // 添加按钮 PendingIntent
        Intent addIntent = new Intent(context,
            com.nearby.justnow.ui.taskinput.TaskInputActivity.class);
        addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent addPi = PendingIntent.getActivity(
            context, 1, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_widget_add, addPi);

        // 顶部栏状态文本点击跳转主界面
        Intent statusIntent = new Intent(context, MainActivity.class);
        statusIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent statusPi = PendingIntent.getActivity(
            context, 0, statusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tv_widget_status, statusPi);

        // 后台计算时段并渲染任务列表
        AppDatabase.execute(() -> {
            try {
                JustNowApplication app = (JustNowApplication) context.getApplicationContext();
                Resources res = context.getResources();

                // 条件读取
                TimePeriodRepository periodRepo = app.getTimePeriodRepository();
                ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
                List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);

                TaskRepository taskRepo = app.getTaskRepository();
                List<TaskEntity> allActive = taskRepo.getAllActiveTasksSync();
                Set<Long> autoCompletedIds = TaskExecutionAutoCompleter.completeExpiredRunningTasksSync(
                    taskRepo, app.getTaskExecutionRepository(),
                    allActive, periods, periodRepo.getAllPeriodsSync());

                TimeRemainingCalculator.PeriodStatus status = TimeRemainingCalculator.compute(periods);
                Map<Long, TagEntity> tagMap = app.getTagRepository().getAllTagsMapSync();
                Map<Long, TaskQuadrantDegradeEntity> degradeMap = taskRepo.getNonExpiredDegradeMapSync();
                List<TaskScheduleEntity> todaySchedules = app.getTaskScheduleRepository().getAllEnabledSchedulesSync();
                Set<Long> schedulePriorityIds = com.nearby.justnow.ui.main.MainViewModel.computeSchedulePriorityIds(todaySchedules);

                List<TaskEntity> tasks = filterTasksByTag(allActive, autoCompletedIds, context, widgetId, tagMap);

                // 统一档位判定：Widget 高度不足或系统字体放大时启用紧凑模式
                float fontScale = context.getResources().getConfiguration().fontScale;
                boolean compact = widgetHeightDp < 180 || fontScale > 1.0f;

                int maxItems = calculateMaxItems(widgetHeightDp, res, compact);
                List<DisplayItem> items = computeItems(tasks, tagMap, status, maxItems, degradeMap, schedulePriorityIds);

                renderWidgetTasks(views, items, res, context, widgetId, compact);
                renderWidgetStatus(views, status, periods, res, compact);

                manager.updateAppWidget(widgetId, views);

            } catch (Exception e) {
                views.setTextViewText(R.id.tv_widget_status,
                    context.getString(R.string.s_widget_dash));
                views.removeAllViews(R.id.ll_widget_tasks);
                views.setViewVisibility(R.id.ll_widget_tasks, View.GONE);
                views.setViewVisibility(R.id.tv_widget_empty, View.VISIBLE);
                manager.updateAppWidget(widgetId, views);
            }
        });
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, JustNowWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(provider);
        updateAllWidgets(context, manager, widgetIds);
    }

    /**
     * 更新所有 Widget 实例，逐个从 AppWidgetManager 获取实际高度用于动态计算 maxItems。
     */
    public static void updateAllWidgets(Context context, AppWidgetManager manager, int[] widgetIds) {
        if (widgetIds == null) return;
        for (int widgetId : widgetIds) {
            Bundle options = manager.getAppWidgetOptions(widgetId);
            updateWidget(context, manager, widgetId, options);
        }
        if (widgetIds.length > 0) {
            scheduleNextMinuteBoundary(context);
        }
    }

    // ==================== 整分钟闹钟（阶段 4 接入） ====================

    /**
     * 注册下一个整分钟边界闹钟，通过显式 Intent 触发 MinuteBoundaryReceiver。
     */
    public static void scheduleNextMinuteBoundary(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, MinuteBoundaryReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long now = System.currentTimeMillis();
        long nextMinuteMs = ((now / 60000) + 1) * 60000;
        if (PermissionHelper.hasExactAlarmPermission(context)) {
            am.setExact(AlarmManager.RTC_WAKEUP, nextMinuteMs, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, nextMinuteMs, pi);
        }
    }

    /**
     * 取消整分钟边界闹钟。
     */
    public static void cancelMinuteBoundary(Context context) {
        Intent intent = new Intent(context, MinuteBoundaryReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pi);
        }
        if (pi != null) {
            pi.cancel();
        }
    }

    // ==================== 任务行渲染 ====================

    /** 两列配对渲染：每行创建 widget_task_row_container，左/右各放一个 item */
    private static void renderTaskItems(Context context, RemoteViews views,
                                        List<DisplayItem> items, Resources res,
                                        int widgetId, long filterTagId, boolean compact) {
        int i = 0;
        while (i < items.size()) {
            RemoteViews rowContainer = new RemoteViews(context.getPackageName(),
                R.layout.widget_task_row_container);
            RemoteViews item1 = buildTaskRow(context, items.get(i), res, widgetId, filterTagId, compact);
            rowContainer.addView(R.id.ll_row_left, item1);
            i++;
            if (i < items.size()) {
                RemoteViews item2 = buildTaskRow(context, items.get(i), res, widgetId, filterTagId, compact);
                rowContainer.addView(R.id.ll_row_right, item2);
                i++;
            }
            views.addView(R.id.ll_widget_tasks, rowContainer);
        }
    }

    /** 从 item_task_content 模板构建单行 RemoteViews（与主界面共用、统一 ID 对齐 TaskAdapter） */
    private static RemoteViews buildTaskRow(Context context, DisplayItem item, Resources res,
                                            int widgetId, long filterTagId, boolean compact) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.item_task_content);

        TaskEntity task = item.task;
        TagEntity tag = item.tag;

        // 紧凑模式：从 dimens 读取字号、行高、内边距，覆盖 XML 默认值
        // 标准模式零干预，完全走 XML textAppearance / wrap_content
        if (compact) {
            float scaledDensity = res.getDisplayMetrics().scaledDensity;
            float taskContentSp = res.getDimension(R.dimen.widget_compact_content_size) / scaledDensity;
            float tagSp = res.getDimension(R.dimen.widget_compact_tag_size) / scaledDensity;
            float focusBadgeSp = res.getDimension(R.dimen.widget_compact_focus_size) / scaledDensity;
            int rowHeightPx = res.getDimensionPixelSize(R.dimen.widget_compact_row_height);
            int paddingPx = res.getDimensionPixelSize(R.dimen.widget_compact_padding_vertical);

            row.setTextViewTextSize(R.id.tv_task_content, TypedValue.COMPLEX_UNIT_SP, taskContentSp);
            row.setTextViewTextSize(R.id.tv_tag, TypedValue.COMPLEX_UNIT_SP, tagSp);
            row.setTextViewTextSize(R.id.tv_focus_badge, TypedValue.COMPLEX_UNIT_SP, focusBadgeSp);
            row.setInt(R.id.ll_task_item, "setMinimumHeight", rowHeightPx);
            row.setViewPadding(R.id.ll_task_item, 0, paddingPx, 0, paddingPx);
        }

        // 四象限色标
        int colorIdx = Math.max(0, Math.min(item.effectiveQuadrant, sQuadrantColors.length - 1));
        row.setInt(R.id.v_quadrant_color, "setBackgroundColor", sQuadrantColors[colorIdx]);

        // 标签文本
        if (tag != null && tag.name != null && !tag.name.isEmpty()) {
            boolean isActiveFilter = tag.id == filterTagId;
            row.setViewVisibility(R.id.tv_tag, View.VISIBLE);
            row.setTextViewText(R.id.tv_tag, buildTagText(tag.name, isActiveFilter));
            row.setTextColor(R.id.tv_tag,
                res.getColor(isActiveFilter ? R.color.tag_active : R.color.tag_normal, null));
            Intent tagIntent = new Intent(context, JustNowWidgetProvider.class);
            tagIntent.setAction(ACTION_WIDGET_TAG_CLICK);
            tagIntent.putExtra(EXTRA_APP_WIDGET_ID, widgetId);
            tagIntent.putExtra(EXTRA_TAG_ID, tag.id);
            PendingIntent tagPi = PendingIntent.getBroadcast(
                context, buildTagRequestCode(widgetId, tag.id), tagIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            row.setOnClickPendingIntent(R.id.tv_tag, tagPi);
        } else {
            row.setViewVisibility(R.id.tv_tag, View.VISIBLE);
            row.setTextViewText(R.id.tv_tag, "");
        }

        // 专注时长
        String durationText;
        if (task.focusMinutes <= 0) {
            durationText = res.getString(R.string.s_chore_label);
        } else {
            durationText = task.focusMinutes + res.getString(R.string.s_minute_unit);
        }
        row.setTextViewText(R.id.tv_focus_badge, durationText);
        row.setTextColor(R.id.tv_focus_badge,
            res.getColor(R.color.text_tertiary, null));

        // 标题
        row.setTextViewText(R.id.tv_task_content,
            task.content != null ? task.content : "");
        row.setTextColor(R.id.tv_task_content,
            res.getColor(R.color.text_primary, null));

        boolean isExecuting = task.executingStartMs > 0 && task.executingEndMs == 0;
        row.setInt(R.id.ll_task_item, "setBackgroundColor",
            res.getColor(isExecuting ? R.color.widget_task_executing_background
                : android.R.color.transparent, null));

        // 整行点击交给主界面统一任务语义分流。
        Intent detailIntent = new Intent(context, MainActivity.class);
        detailIntent.setAction(MainActivity.ACTION_WIDGET_TASK_CLICK);
        detailIntent.putExtra("task_id", task.id);
        detailIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
            context, (int) task.id, detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        row.setOnClickPendingIntent(R.id.ll_task_item, pi);

        return row;
    }

    // ==================== 内部辅助 ====================

    /** 查找下一个未开始的时段提示（非时段时使用） */
    private static String findNextPeriodHint(List<TimePeriodEntity> sortedPeriods, Resources res) {
        if (sortedPeriods == null) return null;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60
            + cal.get(java.util.Calendar.MINUTE);

        // 当天后续时段
        for (TimePeriodEntity p : sortedPeriods) {
            if (p.startMinute > nowMin) {
                return res.getString(R.string.s_widget_next_period,
                    PeriodTextResolver.getPeriodName(res, p.nameKey));
            }
        }
        // 次日第一个时段
        if (!sortedPeriods.isEmpty()) {
            TimePeriodEntity first = sortedPeriods.get(0);
            return res.getString(R.string.s_widget_next_period,
                PeriodTextResolver.getPeriodName(res, first.nameKey));
        }
        return null;
    }

    private static String buildRestingStatusText(List<TimePeriodEntity> periods, Resources res, boolean isTomorrow) {
        if (isTomorrow) {
            return res.getString(R.string.s_tomorrow);
        }
        String restingText = res.getString(R.string.s_widget_resting);
        String nextHint = findNextPeriodHint(periods, res);
        if (nextHint == null) return restingText;
        return restingText + " - " + nextHint;
    }

    private static String formatRemainingTime(Resources res, int remainingMinutes) {
        if (remainingMinutes > 60) {
            String hourUnit = res.getString(R.string.s_hour_unit);
            if (remainingMinutes % 60 == 0) {
                return (remainingMinutes / 60) + hourUnit;
            }
            return String.format(Locale.US, "%.1f%s", remainingMinutes / 60.0, hourUnit);
        }
        return remainingMinutes + res.getString(R.string.s_minute_unit);
    }

    private static int getWidgetOption(Bundle options, String key, int fallback) {
        if (options == null) return fallback;
        int value = options.getInt(key, 0);
        return value > 0 ? value : fallback;
    }

    private static int calculateMaxItems(int widgetHeightDp, Resources res, boolean compact) {
        ensureDimensionsCached(res);
        float density = res.getDisplayMetrics().density;
        int rowHeightDimen = compact
            ? R.dimen.widget_compact_row_height
            : R.dimen.widget_task_row_height;
        int topBarDp = compact
            ? (int) ((res.getDimensionPixelSize(R.dimen.widget_compact_action_bar_height)
                + res.getDimensionPixelSize(R.dimen.widget_compact_action_bar_margin_bottom)) / density)
            : sTopBarDp;
        int rowHeightDp = (int) (res.getDimensionPixelSize(rowHeightDimen) / density);
        int rows = Math.max(1,
            (widgetHeightDp - topBarDp - sContentPaddingDp * 2 - 4) / rowHeightDp);
        return Math.max(WIDGET_COLUMN_COUNT, rows * WIDGET_COLUMN_COUNT);
    }

    static List<DisplayItem> computeItems(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
            TimeRemainingCalculator.PeriodStatus status, int maxItems,
            Map<Long, TaskQuadrantDegradeEntity> degradeMap,
            Set<Long> schedulePriorityIds) {
        int remainingMin = status.isInPeriod() ? status.remainingMinutes : 0;
        boolean reverseQuadrant = status.isReverseQuadrant();
        try {
            List<DisplayItem> result = sDisplayEngine.compute(tasks, tagMap, remainingMin, reverseQuadrant,
                maxItems, java.util.Collections.emptySet(), degradeMap, schedulePriorityIds);
            return result;
        } catch (Exception e) {
            return buildFallbackList(tasks, tagMap);
        }
    }

    private static void renderWidgetTasks(RemoteViews views, List<DisplayItem> items,
            Resources res, Context context, int widgetId, boolean compact) {
        views.removeAllViews(R.id.ll_widget_tasks);
        if (items == null || items.isEmpty()) {
            views.setViewVisibility(R.id.ll_widget_tasks, View.GONE);
            views.setViewVisibility(R.id.tv_widget_empty, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.ll_widget_tasks, View.VISIBLE);
            views.setViewVisibility(R.id.tv_widget_empty, View.GONE);
            long filterTagId = new WidgetFilterStore(context).getFilterTagId(widgetId);
            renderTaskItems(context, views, items, res, widgetId, filterTagId, compact);
        }
    }

    private static void renderWidgetStatus(RemoteViews views, TimeRemainingCalculator.PeriodStatus status,
            List<TimePeriodEntity> periods, Resources res, boolean compact) {
        if (compact) {
            float scaledDensity = res.getDisplayMetrics().scaledDensity;
            float statusSp = res.getDimension(R.dimen.widget_compact_status_size) / scaledDensity;
            views.setTextViewTextSize(R.id.tv_widget_status, TypedValue.COMPLEX_UNIT_SP, statusSp);
        }
        if (status.isInPeriod()) {
            String periodName = PeriodTextResolver.getPeriodName(res, status.period.nameKey);
            String timeText = formatRemainingTime(res, status.remainingMinutes);
            views.setTextViewText(R.id.tv_widget_status,
                periodName + " " + String.format(res.getString(R.string.s_remaining_format), timeText));
        } else {
            TimeRemainingCalculator.StatusText statusText = TimeRemainingCalculator.buildStatusText(periods, status);
            views.setTextViewText(R.id.tv_widget_status,
                buildRestingStatusText(periods, res, statusText.isTomorrow));
        }
    }

    static List<TaskEntity> filterTasksByTag(List<TaskEntity> tasks, Set<Long> autoCompletedIds,
            Context context, int widgetId, Map<Long, TagEntity> tagMap) {
        if (tasks == null) return new ArrayList<>();
        if (autoCompletedIds != null && !autoCompletedIds.isEmpty()) {
            tasks.removeIf(t -> autoCompletedIds.contains(t.id));
        }
        WidgetFilterStore filterStore = new WidgetFilterStore(context);
        long filterTagId = filterStore.getFilterTagId(widgetId);
        if (filterTagId <= 0) return tasks;
        TagEntity tag = tagMap.get(filterTagId);
        if (tag == null || tag.name == null || tag.name.isEmpty()) {
            filterStore.clearFilter(widgetId);
            return tasks;
        }
        List<TaskEntity> filteredTasks = new ArrayList<>();
        for (TaskEntity task : tasks) {
            if (task == null || task.tagId == null) continue;
            if (task.tagId == filterTagId) {
                filteredTasks.add(task);
            }
        }
        return filteredTasks;
    }

    private static CharSequence buildTagText(String tagName, boolean isActiveFilter) {
        String text = "#" + tagName;
        if (!isActiveFilter) return text;
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new UnderlineSpan(), 0, text.length(), 0);
        return spannable;
    }

    private static int buildTagRequestCode(int widgetId, long tagId) {
        long code = ((long) widgetId << 32) ^ tagId;
        code = code ^ (code >>> 32);
        return (int) code;
    }

    /** 引擎异常降级：按创建时间倒序生成简单列表 */
    private static List<DisplayItem> buildFallbackList(List<TaskEntity> tasks,
                                                        Map<Long, TagEntity> tagMap) {
        if (tasks == null) return new ArrayList<>();
        if (tagMap == null) tagMap = new HashMap<>();
        List<DisplayItem> items = new ArrayList<>();
        for (TaskEntity t : tasks) {
            items.add(new DisplayItem(t, tagMap.get(t.tagId)));
        }
        items.sort((a, b) -> Long.compare(b.task.createdAt, a.task.createdAt));
        return items;
    }
}
