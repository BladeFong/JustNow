package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.util.DateUtils;

/**
 * 截止时间覆盖的 SharedPreferences 读写封装。
 * 存储日期 + 当天分钟数（如 10:30 = 630），0 = 未设置。
 */
public class CutoffTimeStore {

    private static final String KEY_CUTOFF_END_MINUTE = "cutoff_end_minute";
    private static final String KEY_CUTOFF_DATE_MS = "cutoff_date_ms";

    private CutoffTimeStore() {}

    /** 获取截止时间（当天分钟数），0 = 未设置。 */
    public static int getCutoffEndMinute(Context context) {
        SharedPreferences prefs = getPrefs(context);
        int cutoff = prefs.getInt(KEY_CUTOFF_END_MINUTE, 0);
        if (cutoff == 0) return 0;
        if (prefs.getLong(KEY_CUTOFF_DATE_MS, 0) != DateUtils.todayStartMs()) {
            clearCutoffEndMinute(context);
            return 0;
        }
        return cutoff;
    }

    /** 设置截止时间（当天分钟数）。 */
    public static void setCutoffEndMinute(Context context, int endMinute) {
        getPrefs(context).edit()
            .putInt(KEY_CUTOFF_END_MINUTE, endMinute)
            .putLong(KEY_CUTOFF_DATE_MS, DateUtils.todayStartMs())
            .apply();
    }

    /** 清除截止时间。 */
    public static void clearCutoffEndMinute(Context context) {
        getPrefs(context).edit()
            .remove(KEY_CUTOFF_END_MINUTE)
            .remove(KEY_CUTOFF_DATE_MS)
            .apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        long userId = ((JustNowApplication) context.getApplicationContext()).getCurrentUserId();
        return UserPrefs.getPrefs(context.getApplicationContext(), userId, PrefsConfig.PREFS_NAME);
    }
}
