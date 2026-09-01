package com.nearby.justnow.data.migration;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.store.UserPrefs;
import com.nearby.justnow.data.store.UserStore;

import java.io.File;
import java.util.List;

/**
 * 数据迁移管理器 — 负责多用户底座下的静默数据合并与按需平滑迁移：
 * 1. 已分用户的平板端节假日数据合并回默认公共库（userId=0 / justnow.db）
 * 2. 平板端引入多用户前的旧版历史业务数据（tasks/tags等）静默平滑迁移至目标用户库
 */
public final class DataMigrationManager {

    private static final String TAG = "DataMigrationManager";
    private static final String PREFS_MIGRATION = "migration_prefs";
    private static final String KEY_HOLIDAY_CACHE_CONSOLIDATED = "holiday_cache_consolidated_to_default";
    private static final String KEY_TABLET_LEGACY_MIGRATED = "tablet_legacy_data_migrated";

    private DataMigrationManager() {}

    /**
     * 将平板端各用户数据库中的 holiday_cache 合并回默认数据库（userId=0）。
     * 仅执行一次，后台线程运行。
     */
    public static void consolidateHolidayCacheToDefaultDb(Context context, UserStore userStore) {
        SharedPreferences prefs = UserPrefs.getGlobalPrefs(context, PREFS_MIGRATION);
        if (prefs.getBoolean(KEY_HOLIDAY_CACHE_CONSOLIDATED, false)) {
            return;
        }

        List<UserStore.UserInfo> users = userStore.getAllUsers();
        if (users == null || users.isEmpty()) {
            prefs.edit().putBoolean(KEY_HOLIDAY_CACHE_CONSOLIDATED, true).apply();
            return;
        }

        AppDatabase defaultDb = AppDatabase.getInstance(context, 0L);
        for (UserStore.UserInfo user : users) {
            if (user.userId == 0L) continue;
            File userDbFile = context.getDatabasePath("justnow_u" + user.userId + ".db");
            if (!userDbFile.exists()) continue;

            try {
                AppDatabase userDb = AppDatabase.getInstance(context, user.userId);
                List<HolidayCacheEntity> caches = userDb.holidayCacheDao().getAllSync();
                if (caches != null && !caches.isEmpty()) {
                    for (HolidayCacheEntity entity : caches) {
                        defaultDb.holidayCacheDao().upsertWithCountCheck(entity, entity.lastSyncMonth);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to consolidate holiday cache from user " + user.userId, e);
            }
        }
        prefs.edit().putBoolean(KEY_HOLIDAY_CACHE_CONSOLIDATED, true).apply();
    }

    /**
     * 检测平板端是否存在已创建的用户且需要迁移旧业务数据。
     */
    public static void checkAndMigrateExistingTabletUser(Context context, UserStore userStore) {
        if (!context.getResources().getBoolean(R.bool.is_tablet)) {
            return;
        }
        SharedPreferences prefs = UserPrefs.getGlobalPrefs(context, PREFS_MIGRATION);
        if (prefs.getBoolean(KEY_TABLET_LEGACY_MIGRATED, false)) {
            return;
        }
        List<UserStore.UserInfo> users = userStore.getAllUsers();
        if (users != null && !users.isEmpty() && users.get(0).userId != 0L) {
            migrateLegacyTabletDataIfNeeded(context, users.get(0).userId);
        }
    }

    /**
     * 按需迁移平板分用户前 justnow.db 中的旧版业务数据至目标用户数据库。
     * 若 justnow.db 中无业务数据（tasks 为空），则直接标记已完成。
     *
     * @param context      Context
     * @param targetUserId 迁移目标用户 ID（必须 != 0）
     */
    public static void migrateLegacyTabletDataIfNeeded(Context context, long targetUserId) {
        if (targetUserId == 0L || !context.getResources().getBoolean(R.bool.is_tablet)) {
            return;
        }
        SharedPreferences prefs = UserPrefs.getGlobalPrefs(context, PREFS_MIGRATION);
        if (prefs.getBoolean(KEY_TABLET_LEGACY_MIGRATED, false)) {
            return;
        }

        File defaultDbFile = context.getDatabasePath("justnow.db");
        if (!defaultDbFile.exists()) {
            prefs.edit().putBoolean(KEY_TABLET_LEGACY_MIGRATED, true).apply();
            return;
        }

        AppDatabase defaultDb = AppDatabase.getInstance(context, 0L);
        boolean hasLegacyTasks = false;
        try (Cursor cursor = defaultDb.query("SELECT COUNT(*) FROM tasks", null)) {
            if (cursor != null && cursor.moveToFirst()) {
                hasLegacyTasks = cursor.getInt(0) > 0;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query tasks count in default db", e);
        }

        if (!hasLegacyTasks) {
            prefs.edit().putBoolean(KEY_TABLET_LEGACY_MIGRATED, true).apply();
            return;
        }

        try {
            AppDatabase targetDb = AppDatabase.getInstance(context, targetUserId);
            SupportSQLiteDatabase writableTarget = targetDb.getOpenHelper().getWritableDatabase();
            String defaultDbPath = defaultDbFile.getAbsolutePath().replace("'", "''");

            writableTarget.beginTransaction();
            try {
                writableTarget.execSQL("ATTACH DATABASE '" + defaultDbPath + "' AS default_db;");
                writableTarget.execSQL("PRAGMA foreign_keys = OFF;");

                String[] userTables = new String[] {
                    "tags", "tasks", "time_period_groups", "time_periods",
                    "priority_tag_rules", "task_executions", "task_schedules",
                    "task_schedule_postpones", "task_checklist_items", "task_app_actions",
                    "task_note_shares", "task_completion_counter", "task_schedule_skips",
                    "task_photos"
                };

                for (String table : userTables) {
                    writableTarget.execSQL("DELETE FROM " + table);
                    writableTarget.execSQL("INSERT OR REPLACE INTO " + table + " SELECT * FROM default_db." + table);
                }

                // 清理 default_db 中的用户业务数据，保留公共节假日底座
                for (String table : userTables) {
                    writableTarget.execSQL("DELETE FROM default_db." + table);
                }

                writableTarget.execSQL("PRAGMA foreign_keys = ON;");
                writableTarget.execSQL("DETACH DATABASE default_db;");
                writableTarget.setTransactionSuccessful();
            } finally {
                writableTarget.endTransaction();
            }
            prefs.edit().putBoolean(KEY_TABLET_LEGACY_MIGRATED, true).apply();
            Log.i(TAG, "Successfully migrated legacy tablet data to user " + targetUserId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate legacy tablet data to user " + targetUserId, e);
        }
    }
}
