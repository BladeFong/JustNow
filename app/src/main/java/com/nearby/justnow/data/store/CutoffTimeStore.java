package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 截止时间覆盖的 SharedPreferences 读写封装。
 * 存储当天分钟数（如 10:30 = 630），0 = 未设置。
 */
public class CutoffTimeStore {

    private static final String PREFS_NAME = "justnow_prefs";
    private static final String KEY_CUTOFF_END_MINUTE = "cutoff_end_minute";

    private CutoffTimeStore() {}

    /** 获取截止时间（当天分钟数），0 = 未设置。 */
    public static int getCutoffEndMinute(Context context) {
        return getPrefs(context).getInt(KEY_CUTOFF_END_MINUTE, 0);
    }

    /** 设置截止时间（当天分钟数）。 */
    public static void setCutoffEndMinute(Context context, int endMinute) {
        getPrefs(context).edit()
            .putInt(KEY_CUTOFF_END_MINUTE, endMinute)
            .apply();
    }

    /** 清除截止时间。 */
    public static void clearCutoffEndMinute(Context context) {
        getPrefs(context).edit()
            .remove(KEY_CUTOFF_END_MINUTE)
            .apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
