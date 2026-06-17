package com.nearby.justnow.ui.taskinput;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 本次编辑流程内的可启动 APP 列表缓存。
 */
public class AppLaunchCatalogCache {

    public enum Status {
        NOT_LOADED,
        LOADING,
        LOADED,
        FAILED
    }

    private final Context mAppContext;
    private final MutableLiveData<Status> mStatus = new MutableLiveData<>(Status.NOT_LOADED);
    private final Object mLock = new Object();
    private List<AppInfo> mApps = new ArrayList<>();
    private Status mCurrentStatus = Status.NOT_LOADED;
    private int mLoadGeneration = 0;

    public AppLaunchCatalogCache(Context context) {
        mAppContext = context.getApplicationContext();
    }

    public LiveData<Status> getStatus() {
        return mStatus;
    }

    public Status getCurrentStatus() {
        synchronized (mLock) {
            return mCurrentStatus;
        }
    }

    public List<AppInfo> getApps() {
        synchronized (mLock) {
            return new ArrayList<>(mApps);
        }
    }

    public void loadIfNeeded() {
        reload();
    }

    public void reload() {
        int generation;
        synchronized (mLock) {
            if (mCurrentStatus == Status.LOADING || mCurrentStatus == Status.LOADED) return;
            generation = ++mLoadGeneration;
            mCurrentStatus = Status.LOADING;
        }
        mStatus.postValue(Status.LOADING);
        AppDatabase.execute(() -> loadInBackground(generation));
    }

    @MainThread
    public void clear() {
        synchronized (mLock) {
            mLoadGeneration++;
            mApps = new ArrayList<>();
            mCurrentStatus = Status.NOT_LOADED;
        }
        mStatus.setValue(Status.NOT_LOADED);
    }

    public List<AppInfo> filter(String keyword) {
        String normalized = keyword != null ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<AppInfo> allApps = getApps();
        if (normalized.isEmpty()) {
            return allApps;
        }
        List<AppInfo> result = new ArrayList<>();
        for (AppInfo app : allApps) {
            if (app.label.toLowerCase(Locale.ROOT).contains(normalized)
                || app.packageName.toLowerCase(Locale.ROOT).contains(normalized)) {
                result.add(app);
            }
        }
        return result;
    }

    private void loadInBackground(int generation) {
        try {
            PackageManager pm = mAppContext.getPackageManager();
            UserManager um = mAppContext.getSystemService(UserManager.class);
            String selfPackage = mAppContext.getPackageName();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<UserHandle> users = um.getUserProfiles();
            List<AppInfo> apps = new ArrayList<>();
            Method queryMethod = PackageManager.class.getMethod(
                "queryIntentActivitiesAsUser", Intent.class, int.class, int.class);
            Method getIdentifierMethod = UserHandle.class.getMethod("getIdentifier");
            for (UserHandle user : users) {
                int userId = (int) getIdentifierMethod.invoke(user);
                @SuppressWarnings("unchecked")
                List<ResolveInfo> resolvedApps =
                    (List<ResolveInfo>) queryMethod.invoke(pm, intent, 0, userId);
                for (ResolveInfo info : resolvedApps) {
                    String packageName = info.activityInfo.packageName;
                    if (selfPackage.equals(packageName)) continue;
                    CharSequence label = info.loadLabel(pm);
                    Drawable icon = info.loadIcon(pm);
                    String displayLabel = label != null ? label.toString() : packageName;
                    if (userId != 0) {
                        displayLabel += mAppContext.getString(R.string.s_app_clone_suffix);
                    }
                    apps.add(new AppInfo(packageName, displayLabel, icon, userId));
                }
            }
            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
            synchronized (mLock) {
                if (generation != mLoadGeneration) return;
                mApps = apps;
                mCurrentStatus = Status.LOADED;
            }
            mStatus.postValue(Status.LOADED);
        } catch (Exception e) {
            android.util.Log.e("AppLaunchCatalogCache", "loadInBackground failed", e);
            synchronized (mLock) {
                if (generation != mLoadGeneration) return;
                mApps = new ArrayList<>();
                mCurrentStatus = Status.FAILED;
            }
            mStatus.postValue(Status.FAILED);
        }
    }

    public static class AppInfo {
        public final String packageName;
        public final String label;
        public final Drawable icon;
        public final int userId;

        public AppInfo(String packageName, String label, Drawable icon, int userId) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.userId = userId;
        }
    }
}
