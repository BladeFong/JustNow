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
        return ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /**
     * 获取全局 SharedPreferences（不按用户隔离）。
     */
    public static SharedPreferences getGlobalPrefs(Context ctx, String baseName) {
        return ctx.getSharedPreferences(baseName, Context.MODE_PRIVATE);
    }
}
