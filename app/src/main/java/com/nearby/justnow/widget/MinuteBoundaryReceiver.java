package com.nearby.justnow.widget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 整分钟边界闹钟接收器 —— 由 AlarmManager 通过显式 Intent 触发，
 * 执行所有 Widget 实例的更新并注册下一次闹钟。
 */
public class MinuteBoundaryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] widgetIds = manager.getAppWidgetIds(
                new ComponentName(context, JustNowWidgetProvider.class));
        WidgetUpdateHelper.updateAllWidgets(context, manager, widgetIds);
    }
}
