package com.nearby.justnow.ui.base;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.ViewModel;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;

/**
 * ViewModel 基类 — 持有 Application 和 Database 引用，封装后台/主线程调度。
 */
public abstract class BaseViewModel extends ViewModel {
    protected final JustNowApplication mApp;
    protected final AppDatabase mDb;
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    protected BaseViewModel(JustNowApplication app) {
        mApp = app;
        mDb = app.getDatabase();
    }

    /** 提交 Runnable 到数据库后台线程池。 */
    protected void runInBackground(Runnable r) {
        mDb.runInBackground(r);
    }

    /** 提交 Runnable 到主线程。 */
    protected void runOnUiThread(Runnable r) {
        sMainHandler.post(r);
    }
}
