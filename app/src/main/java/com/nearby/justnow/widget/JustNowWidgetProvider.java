package com.nearby.justnow.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * 桌面 Widget Provider —— 委托 WidgetUpdateHelper 执行核心逻辑。
 */
public class JustNowWidgetProvider extends AppWidgetProvider {

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || !WidgetUpdateHelper.ACTION_WIDGET_TAG_CLICK.equals(intent.getAction())) {
            return;
        }
        int widgetId = intent.getIntExtra(WidgetUpdateHelper.EXTRA_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        long tagId = intent.getLongExtra(WidgetUpdateHelper.EXTRA_TAG_ID, 0);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || tagId <= 0) {
            return;
        }

        WidgetFilterStore filterStore = new WidgetFilterStore(context);
        long currentFilterTagId = filterStore.getFilterTagId(widgetId);
        if (currentFilterTagId == tagId) {
            filterStore.clearFilter(widgetId);
        } else {
            filterStore.setFilterTagId(widgetId, tagId);
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        Bundle options = manager.getAppWidgetOptions(widgetId);
        WidgetUpdateHelper.updateWidget(context, manager, widgetId, options);
        WidgetUpdateHelper.scheduleNextMinuteBoundary(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        new WidgetFilterStore(context).clearMissingWidgets(appWidgetManager.getAppWidgetIds(
            new ComponentName(context, JustNowWidgetProvider.class)));
        WidgetUpdateHelper.updateAllWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                           int widgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions);
        // 尺寸变化触发更新，传入实际高度用于动态计算 maxItems。
        WidgetUpdateHelper.updateWidget(context, appWidgetManager, widgetId, newOptions);
        WidgetUpdateHelper.scheduleNextMinuteBoundary(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        WidgetUpdateHelper.scheduleNextMinuteBoundary(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        WidgetUpdateHelper.cancelMinuteBoundary(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        WidgetFilterStore filterStore = new WidgetFilterStore(context);
        if (appWidgetIds == null) return;
        for (int widgetId : appWidgetIds) {
            filterStore.clearFilter(widgetId);
        }
    }

}
