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

import java.util.List;

import static org.junit.Assert.*;

/**
 * UserStore 单元测试 — 用户列表 CRUD。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UserStoreTest {

    private static final String PREFS_NAME = "justnow_users";

    private Context mContext;
    private UserStore mStore;
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
        mStore = new UserStore(mContext);
    }

    @After
    public void tearDown() {
        if (mPrefs != null) {
            mPrefs.edit().clear().commit();
        }
    }

    // ---- hasUsers / 空状态 ----

    @Test
    public void hasUsers_emptyStore_returnsFalse() {
        assertFalse(mStore.hasUsers());
    }

    @Test
    public void getCurrentUserId_emptyStore_returnsNegative() {
        assertEquals(-1L, mStore.getCurrentUserId());
    }

    @Test
    public void getAllUsers_emptyStore_returnsEmptyList() {
        List<UserStore.UserInfo> users = mStore.getAllUsers();
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    // ---- addUser ----

    @Test
    public void addUser_createsUserWithName() {
        UserStore.UserInfo user = mStore.addUser("TestUser");
        assertNotNull(user);
        assertEquals("TestUser", user.name);
        assertTrue(user.userId > 0);
        assertTrue(user.createdAt > 0);
        assertTrue(mStore.hasUsers());
    }

    @Test
    public void addUser_setsCurrentUser() {
        UserStore.UserInfo user = mStore.addUser("TestUser");
        assertEquals(user.userId, mStore.getCurrentUserId());
    }

    @Test
    public void addUser_multipleUsers_incrementsCount() {
        mStore.addUser("User1");
        mStore.addUser("User2");
        assertTrue(mStore.hasUsers());
        List<UserStore.UserInfo> users = mStore.getAllUsers();
        assertEquals(2, users.size());
    }

    @Test
    public void addUser_multipleUsers_lastAddedIsCurrent() {
        UserStore.UserInfo u1 = mStore.addUser("User1");
        UserStore.UserInfo u2 = mStore.addUser("User2");
        assertEquals(u2.userId, mStore.getCurrentUserId());
    }

    // ---- getUserInfo ----

    @Test
    public void getUserInfo_existingUser_returnsCorrectData() {
        UserStore.UserInfo created = mStore.addUser("TestUser");
        UserStore.UserInfo fetched = mStore.getUserInfo(created.userId);
        assertNotNull(fetched);
        assertEquals(created.userId, fetched.userId);
        assertEquals("TestUser", fetched.name);
        assertEquals(created.createdAt, fetched.createdAt);
    }

    @Test
    public void getUserInfo_nonExistingUser_returnsNull() {
        assertNull(mStore.getUserInfo(99999L));
    }

    // ---- setCurrentUserId / 切换 ----

    @Test
    public void setCurrentUserId_switchesUser() {
        UserStore.UserInfo u1 = mStore.addUser("User1");
        mStore.addUser("User2");
        mStore.setCurrentUserId(u1.userId);
        assertEquals(u1.userId, mStore.getCurrentUserId());
    }

    @Test
    public void setCurrentUserId_persistsAcrossInstances() {
        UserStore.UserInfo user = mStore.addUser("TestUser");
        // 新建实例验证持久化
        UserStore another = new UserStore(mContext);
        assertEquals(user.userId, another.getCurrentUserId());
    }

    // ---- getAllUsers 顺序 ----

    @Test
    public void getAllUsers_preservesInsertionOrder() {
        UserStore.UserInfo u1 = mStore.addUser("User1");
        UserStore.UserInfo u2 = mStore.addUser("User2");
        UserStore.UserInfo u3 = mStore.addUser("User3");

        List<UserStore.UserInfo> users = mStore.getAllUsers();
        assertEquals(3, users.size());
        assertEquals(u1.userId, users.get(0).userId);
        assertEquals(u2.userId, users.get(1).userId);
        assertEquals(u3.userId, users.get(2).userId);
    }

    // ---- 边界：userId=0（默认用户） ----

    @Test
    public void getCurrentUserId_returnsZero_whenDefaultUserExists() {
        // 模拟默认用户（userId=0）场景：手动写入
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putInt("user_count", 1);
        editor.putString("user_0_id", "0");
        editor.putString("user_0_name", "Default");
        editor.putLong("user_0_created", 1000L);
        editor.putLong("current_user_id", 0L);
        editor.commit();

        assertEquals(0L, mStore.getCurrentUserId());
        UserStore.UserInfo user = mStore.getUserInfo(0L);
        assertNotNull(user);
        assertEquals("Default", user.name);
        assertEquals(0L, user.userId);
    }
}
