package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户列表持久化存储。独立 SharedPreferences 文件 "justnow_users"。
 * 不按用户隔离——所有用户共享同一份用户列表。
 */
public class UserStore {

    /** 用户 ID 计数器（确保同进程内不重复） */
    private static final AtomicLong sNextUserId = new AtomicLong(System.currentTimeMillis());

    private static final String PREFS_NAME = "justnow_users";
    private static final String KEY_CURRENT_USER_ID = "current_user_id";
    private static final String KEY_USER_COUNT = "user_count";
    private static final String KEY_USER_INDEX_PREFIX = "user_";
    private static final String KEY_USER_INDEX_ID_SUFFIX = "_id";
    private static final String KEY_USER_NAME_SUFFIX = "_name";
    private static final String KEY_USER_CREATED_SUFFIX = "_created";

    private final SharedPreferences mPrefs;

    public UserStore(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public long getCurrentUserId() {
        return mPrefs.getLong(KEY_CURRENT_USER_ID, -1);
    }

    public void setCurrentUserId(long userId) {
        mPrefs.edit().putLong(KEY_CURRENT_USER_ID, userId).apply();
    }

    public boolean hasUsers() {
        return mPrefs.getInt(KEY_USER_COUNT, 0) > 0;
    }

    public List<UserInfo> getAllUsers() {
        int count = mPrefs.getInt(KEY_USER_COUNT, 0);
        List<UserInfo> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String userIdStr = mPrefs.getString(
                KEY_USER_INDEX_PREFIX + i + KEY_USER_INDEX_ID_SUFFIX, null);
            if (userIdStr == null) continue;
            long userId = Long.parseLong(userIdStr);
            String name = mPrefs.getString(userNameKey(userId), "");
            long createdAt = mPrefs.getLong(userCreatedKey(userId), 0);
            users.add(new UserInfo(userId, name, createdAt));
        }
        return users;
    }

    public UserInfo getUserInfo(long userId) {
        String name = mPrefs.getString(userNameKey(userId), null);
        if (name == null) return null;
        long createdAt = mPrefs.getLong(userCreatedKey(userId), 0);
        return new UserInfo(userId, name, createdAt);
    }

    public UserInfo addUser(String name) {
        long userId = sNextUserId.incrementAndGet();
        long now = System.currentTimeMillis();
        int count = mPrefs.getInt(KEY_USER_COUNT, 0);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(KEY_USER_INDEX_PREFIX + count + KEY_USER_INDEX_ID_SUFFIX,
            String.valueOf(userId));
        editor.putString(userNameKey(userId), name);
        editor.putLong(userCreatedKey(userId), now);
        editor.putInt(KEY_USER_COUNT, count + 1);
        editor.putLong(KEY_CURRENT_USER_ID, userId);
        editor.apply();
        return new UserInfo(userId, name, now);
    }

    private static String userNameKey(long userId) {
        return KEY_USER_INDEX_PREFIX + userId + KEY_USER_NAME_SUFFIX;
    }

    private static String userCreatedKey(long userId) {
        return KEY_USER_INDEX_PREFIX + userId + KEY_USER_CREATED_SUFFIX;
    }

    /** 用户信息数据类 */
    public static class UserInfo {
        public final long userId;
        public final String name;
        public final long createdAt;

        public UserInfo(long userId, String name, long createdAt) {
            this.userId = userId;
            this.name = name;
            this.createdAt = createdAt;
        }
    }
}
