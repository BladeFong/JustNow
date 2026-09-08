package com.nearby.justnow.data.migration;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.store.UserPrefs;
import com.nearby.justnow.data.store.UserStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DataMigrationManager 单元测试 — 验证节假日缓存合并回公共基础库及防重标记。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DataMigrationManagerTest {

    private Context mContext;
    private UserStore mUserStore;
    private SharedPreferences mMigrationPrefs;

    @Before
    public void setUp() throws Exception {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        mMigrationPrefs = UserPrefs.getGlobalPrefs(mContext, "migration_prefs");
        mMigrationPrefs.edit().clear().commit();

        mUserStore = new UserStore(mContext);
        mUserStore.clear();

        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            AppDatabase.getInstance(mContext, 0L).clearAllTables();
        }).get();
    }

    @After
    public void tearDown() throws Exception {
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            AppDatabase.getInstance(mContext, 0L).clearAllTables();
        }).get();
        AppDatabase.clearAllInstances();
        mMigrationPrefs.edit().clear().commit();
        mUserStore.clear();
    }

    @Test
    public void consolidateHolidayCache_emptyUsers_setsFlag() {
        DataMigrationManager.consolidateHolidayCacheToDefaultDb(mContext, mUserStore);
        assertTrue(mMigrationPrefs.getBoolean("holiday_cache_consolidated_to_default", false));
    }

    @Test
    public void consolidateHolidayCache_withUserData_consolidatesToDefaultDb() throws Exception {
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            // 创建用户 1
            UserStore.UserInfo user1 = mUserStore.addUser("User1");

            // 在用户 1 的数据库中写入 2026 年节假日缓存
            AppDatabase user1Db = AppDatabase.getInstance(mContext, user1.userId);
            HolidayCacheEntity entity = new HolidayCacheEntity();
            entity.year = 2026;
            entity.dataJson = "{\"test\":true}";
            entity.holidayCount = 11;
            entity.lastSyncMonth = 202609;
            entity.lastUpdated = System.currentTimeMillis();
            user1Db.holidayCacheDao().insert(entity);

            // 执行回迁合并
            DataMigrationManager.consolidateHolidayCacheToDefaultDb(mContext, mUserStore);

            // 验证默认数据库 (userId=0) 中已成功合并该节假日记录
            AppDatabase defaultDb = AppDatabase.getInstance(mContext, 0L);
            HolidayCacheEntity defaultEntity = defaultDb.holidayCacheDao().getByYearSync(2026);
            assertNotNull(defaultEntity);
            assertEquals(11, defaultEntity.holidayCount);
            assertEquals("{\"test\":true}", defaultEntity.dataJson);
        }).get();

        // 验证标记已记录
        assertTrue(mMigrationPrefs.getBoolean("holiday_cache_consolidated_to_default", false));
    }

    @Test
    public void dispatchHolidayUpdate_updatesDefaultAndUserDbs() throws Exception {
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            UserStore.UserInfo user1 = mUserStore.addUser("User1");

            HolidayCacheEntity entity = new HolidayCacheEntity();
            entity.year = 2026;
            entity.dataJson = "{\"dispatched\":true}";
            entity.holidayCount = 15;
            entity.lastSyncMonth = 202609;
            entity.lastUpdated = System.currentTimeMillis();

            DataMigrationManager.dispatchHolidayUpdate(mContext, entity, mUserStore);

            // 验证公共底座库已更新
            AppDatabase defaultDb = AppDatabase.getInstance(mContext, 0L);
            HolidayCacheEntity defEntity = defaultDb.holidayCacheDao().getByYearSync(2026);
            assertNotNull(defEntity);
            assertEquals(15, defEntity.holidayCount);
        }).get();
    }

    @Test
    public void copyHolidayCacheToNewUser_copiesAllData() throws Exception {
        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            // 写入公共库
            AppDatabase defaultDb = AppDatabase.getInstance(mContext, 0L);
            HolidayCacheEntity entity = new HolidayCacheEntity();
            entity.year = 2026;
            entity.dataJson = "{\"copied\":true}";
            entity.holidayCount = 20;
            entity.lastSyncMonth = 202609;
            entity.lastUpdated = System.currentTimeMillis();
            defaultDb.holidayCacheDao().insert(entity);

            // 创建新用户并拷贝
            UserStore.UserInfo user1 = mUserStore.addUser("User1");
            DataMigrationManager.copyHolidayCacheToNewUser(mContext, user1.userId);

            // 验证新用户数据库中已存在该节假日数据
            AppDatabase user1Db = AppDatabase.getInstance(mContext, user1.userId);
            HolidayCacheEntity userEntity = user1Db.holidayCacheDao().getByYearSync(2026);
            assertNotNull(userEntity);
            assertEquals(20, userEntity.holidayCount);
            assertEquals("{\"copied\":true}", userEntity.dataJson);
        }).get();
    }
}
