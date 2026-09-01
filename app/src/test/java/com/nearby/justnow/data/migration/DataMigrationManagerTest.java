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
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        mMigrationPrefs = UserPrefs.getGlobalPrefs(mContext, "migration_prefs");
        mMigrationPrefs.edit().clear().commit();

        mUserStore = new UserStore(mContext);
        mUserStore.clear();
    }

    @After
    public void tearDown() {
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
}
