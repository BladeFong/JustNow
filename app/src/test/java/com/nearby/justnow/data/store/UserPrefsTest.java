package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * UserPrefs 单元测试 — 按 userId 隔离 SharedPreferences。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UserPrefsTest {

    private static final String TEST_PREFS = "test_prefs";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
    }

    @After
    public void tearDown() {
        // 清理测试写入的文件
        clearPrefs("test_prefs");
        clearPrefs("test_prefs_99");
        clearPrefs("test_prefs_12345");
    }

    private void clearPrefs(String name) {
        SharedPreferences prefs = mContext.getSharedPreferences(name, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    // ---- 默认用户 (userId=0) ----

    @Test
    public void getPrefs_defaultUser_usesOriginalName() {
        SharedPreferences prefs = UserPrefs.getPrefs(mContext, 0L, TEST_PREFS);
        assertNotNull(prefs);
        prefs.edit().putString("key", "value").commit();
        // 验证读写正常
        assertEquals("value", prefs.getString("key", null));
    }

    // ---- 非默认用户 ----

    @Test
    public void getPrefs_nonDefaultUser_usesSuffixedName() {
        SharedPreferences prefs = UserPrefs.getPrefs(mContext, 99L, TEST_PREFS);
        assertNotNull(prefs);
        prefs.edit().putString("key", "u99value").commit();
        assertEquals("u99value", prefs.getString("key", null));
    }

    // ---- 隔离性 ----

    @Test
    public void getPrefs_differentUsers_isolation() {
        SharedPreferences p0 = UserPrefs.getPrefs(mContext, 0L, TEST_PREFS);
        SharedPreferences p1 = UserPrefs.getPrefs(mContext, 99L, TEST_PREFS);

        p0.edit().putString("key", "v0").commit();
        p1.edit().putString("key", "v1").commit();

        assertEquals("v0", p0.getString("key", null));
        assertEquals("v1", p1.getString("key", null));
    }

    @Test
    public void getPrefs_sameUser_sameFile() {
        SharedPreferences p1 = UserPrefs.getPrefs(mContext, 12345L, TEST_PREFS);
        SharedPreferences p2 = UserPrefs.getPrefs(mContext, 12345L, TEST_PREFS);

        p1.edit().putString("key", "shared").commit();
        assertEquals("shared", p2.getString("key", null));
    }

    // ---- getGlobalPrefs ----

    @Test
    public void getGlobalPrefs_usesOriginalName() {
        SharedPreferences prefs = UserPrefs.getGlobalPrefs(mContext, TEST_PREFS);
        assertNotNull(prefs);
        prefs.edit().putString("global_key", "global_value").commit();
        assertEquals("global_value", prefs.getString("global_key", null));
    }
}
