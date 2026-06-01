package com.nearby.justnow.data.repository;

import android.os.Looper;
import android.util.Log;

import com.nearby.justnow.data.db.AppDatabase;

/**
 * Repository 基类 — 持有 AppDatabase 引用，提供子类复用字段与线程断言。
 */
public abstract class BaseRepository {

    protected final AppDatabase mDb;

    public BaseRepository(AppDatabase db) {
        this.mDb = db;
    }

    /** 警告：Sync 方法不应在主线程调用。 */
    protected static void assertNotMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w("BaseRepository", "sync method called on main thread");
        }
    }
}
