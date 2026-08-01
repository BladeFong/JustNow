package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 按用户 ID 获取隔离的 SharedPreferences 实例。
 * <p>
 * 规则：userId == 0 → 保持原文件名（向后兼容手机端单用户）
 *      userId != 0 → 文件名加 _&lt;userId&gt; 后缀
 */
public final class UserPrefs {

    private UserPrefs() {}

    /**
     * 获取按用户隔离的 SharedPreferences。
     *
     * @param ctx      Context
     * @param userId   用户 ID（0 = 默认用户，文件名不变）
     * @param baseName 基础文件名（如 "justnow_prefs", "capsule_settings"）
     */
    public static SharedPreferences getPrefs(Context ctx, long userId, String baseName) {
        String name = userId == 0 ? baseName : baseName + "_" + userId;
        SharedPreferences userSp = ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
        if (userId != 0) {
            migrateIfNeeded(ctx, userSp, baseName);
        }
        return userSp;
    }

    private static void migrateIfNeeded(Context ctx, SharedPreferences userSp, String baseName) {
        if (!userSp.contains("migrated_from_global") && !userSp.contains("schedule_profile")) {
            SharedPreferences globalSp = ctx.getSharedPreferences(baseName, Context.MODE_PRIVATE);
            if (globalSp != null && !globalSp.getAll().isEmpty()) {
                SharedPreferences.Editor editor = userSp.edit();
                for (java.util.Map.Entry<String, ?> entry : globalSp.getAll().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        editor.putString(key, (String) value);
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    }
                }
                editor.putBoolean("migrated_from_global", true);
                editor.apply();
            }
        }
    }

    /**
     * 获取全局 SharedPreferences（不按用户隔离）。
     */
    public static SharedPreferences getGlobalPrefs(Context ctx, String baseName) {
        return ctx.getSharedPreferences(baseName, Context.MODE_PRIVATE);
    }
}
