package com.nearby.justnow.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Widget 标签筛选状态存储。每个桌面 Widget 实例独立保存一个 tagId。
 */
public class WidgetFilterStore {

    private static final String PREFS_NAME = "widget_filter_prefs";
    private static final String KEY_PREFIX_FILTER_TAG = "filter_tag_";

    private final SharedPreferences mPrefs;

    public WidgetFilterStore(Context context) {
        mPrefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public long getFilterTagId(int widgetId) {
        if (widgetId <= 0) return 0;
        return mPrefs.getLong(buildFilterKey(widgetId), 0);
    }

    public void setFilterTagId(int widgetId, long tagId) {
        if (widgetId <= 0) return;
        if (tagId <= 0) {
            clearFilter(widgetId);
            return;
        }
        mPrefs.edit()
            .putLong(buildFilterKey(widgetId), tagId)
            .apply();
    }

    public void clearFilter(int widgetId) {
        if (widgetId <= 0) return;
        mPrefs.edit()
            .remove(buildFilterKey(widgetId))
            .apply();
    }

    public void clearMissingWidgets(int[] activeWidgetIds) {
        Set<String> activeKeys = new HashSet<>();
        if (activeWidgetIds != null) {
            for (int widgetId : activeWidgetIds) {
                if (widgetId > 0) {
                    activeKeys.add(buildFilterKey(widgetId));
                }
            }
        }

        SharedPreferences.Editor editor = null;
        Map<String, ?> allValues = mPrefs.getAll();
        for (String key : allValues.keySet()) {
            if (!key.startsWith(KEY_PREFIX_FILTER_TAG)) continue;
            if (activeKeys.contains(key)) continue;
            if (editor == null) editor = mPrefs.edit();
            editor.remove(key);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private static String buildFilterKey(int widgetId) {
        return KEY_PREFIX_FILTER_TAG + widgetId;
    }
}
