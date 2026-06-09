package com.nearby.justnow;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.holiday.HolidayCacheManager;
import com.nearby.justnow.data.holiday.HolidaySourceFactory;
import com.nearby.justnow.data.holiday.HolidaySyncWorker;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.observer.DataChangeDispatcher;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskAppActionRepository;
import com.nearby.justnow.data.repository.TaskNoteShareRepository;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskSchedulePostponeRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.util.PermissionHelper;
import com.nearby.justnow.ui.taskinput.AppLaunchCatalogCache;
import com.nearby.justnow.widget.WidgetDataChangeNotifier;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.work.WorkManager;

/**
 * Application — 持有数据库单例
 */
public class JustNowApplication extends Application {

    private AppDatabase mDatabase;

    /** App 退后台标记（AtomicBoolean 保证线程安全），供 MainFragment 在 onResume 时判断是否需重置筛选/暂停状态 */
    private final AtomicBoolean mBackgroundFlag = new AtomicBoolean(false);

    // ---- Repository 单例缓存 ----
    private TaskRepository mTaskRepo;
    private TagRepository mTagRepo;
    private TaskChecklistRepository mTaskChecklistRepo;
    private TaskAppActionRepository mTaskAppActionRepo;
    private TaskNoteShareRepository mTaskNoteShareRepo;
    private TaskExecutionRepository mTaskExecutionRepo;
    private TaskScheduleRepository mTaskScheduleRepo;
    private TaskSchedulePostponeRepository mTaskSchedulePostponeRepo;
    private TimePeriodRepository mTimePeriodRepo;
    private PeriodGroupRuleResolver mPeriodGroupRuleResolver;
    private AppLaunchCatalogCache mAppLaunchCatalogCache;

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = AppDatabase.getInstance(this);
        DataChangeDispatcher.setNotifier(new WidgetDataChangeNotifier(this));
        // 预热 jieba 分词词典，避免首次输入时的延迟
        mDatabase.runInBackground(() ->
            com.nearby.justnow.util.TextTokenizer.tokenize("预热"));
        // 启动节假日数据后台同步
        triggerHolidaySync();
        // 注册每日凌晨 3 点提醒闹钟刷新（需要精确闹钟权限）
        if (PermissionHelper.hasExactAlarmPermission(this)) {
            new ReminderScheduler(this).scheduleDailyRefresh();
        }
        // 注册前后台监听：App 退后台时置标记，供返回前台时恢复默认筛选/暂停状态
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                mBackgroundFlag.set(true);
            }
        });
        // 全局屏幕方向锁定：手机竖屏，平板横屏
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                boolean isTablet = (activity.getResources().getConfiguration().screenLayout
                        & Configuration.SCREENLAYOUT_SIZE_MASK)
                        >= Configuration.SCREENLAYOUT_SIZE_LARGE;
                activity.setRequestedOrientation(isTablet
                        ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    private void triggerHolidaySync() {
        mDatabase.runInBackground(() -> {
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            HolidayCacheManager cacheManager = new HolidayCacheManager(
                mDatabase.holidayCacheDao());
            if (!cacheManager.shouldSyncThisMonth(currentYear)) return;

            java.util.List<com.nearby.justnow.data.holiday.HolidayDataSource> sources =
                HolidaySourceFactory.createSourcesForRegion(this);
            if (sources.isEmpty()) return;

            for (com.nearby.justnow.data.holiday.HolidayDataSource source : sources) {
                try {
                    com.nearby.justnow.data.entity.HolidayCacheEntity entity =
                        source.fetch(currentYear);
                    cacheManager.save(entity);
                    return;
                } catch (java.io.IOException ignored) {
                }
            }
            // 所有源均失败，降级为 WorkManager 延迟重试
            WorkManager.getInstance(this)
                .enqueue(HolidaySyncWorker.createRequest(currentYear));
        });
    }

    /**
     * 消费后台标记：读取并重置。供 MainFragment onResume 判断是否需恢复默认筛选/暂停状态。
     * @return true 表示自上次消费后 App 曾退到后台
     */
    public boolean consumeBackgroundFlag() {
        return mBackgroundFlag.getAndSet(false);
    }

    // ---- Repository getters ----

    public TaskRepository getTaskRepository() {
        if (mTaskRepo == null) mTaskRepo = new TaskRepository(mDatabase);
        return mTaskRepo;
    }

    public TagRepository getTagRepository() {
        if (mTagRepo == null) mTagRepo = new TagRepository(mDatabase);
        return mTagRepo;
    }

    public TaskChecklistRepository getTaskChecklistRepository() {
        if (mTaskChecklistRepo == null) mTaskChecklistRepo = new TaskChecklistRepository(mDatabase);
        return mTaskChecklistRepo;
    }

    public TaskAppActionRepository getTaskAppActionRepository() {
        if (mTaskAppActionRepo == null) mTaskAppActionRepo = new TaskAppActionRepository(mDatabase);
        return mTaskAppActionRepo;
    }

    public TaskNoteShareRepository getTaskNoteShareRepository() {
        if (mTaskNoteShareRepo == null) mTaskNoteShareRepo = new TaskNoteShareRepository(mDatabase);
        return mTaskNoteShareRepo;
    }

    public TaskExecutionRepository getTaskExecutionRepository() {
        if (mTaskExecutionRepo == null) mTaskExecutionRepo = new TaskExecutionRepository(mDatabase);
        return mTaskExecutionRepo;
    }

    public TaskScheduleRepository getTaskScheduleRepository() {
        if (mTaskScheduleRepo == null) {
            if (mPeriodGroupRuleResolver == null) {
                mPeriodGroupRuleResolver = new PeriodGroupRuleResolver(this);
            }
            mTaskScheduleRepo = new TaskScheduleRepository(mDatabase, mPeriodGroupRuleResolver);
        }
        return mTaskScheduleRepo;
    }

    public TaskSchedulePostponeRepository getTaskSchedulePostponeRepository() {
        if (mTaskSchedulePostponeRepo == null) mTaskSchedulePostponeRepo = new TaskSchedulePostponeRepository(mDatabase);
        return mTaskSchedulePostponeRepo;
    }

    public TimePeriodRepository getTimePeriodRepository() {
        if (mTimePeriodRepo == null) {
            if (mPeriodGroupRuleResolver == null) {
                mPeriodGroupRuleResolver = new PeriodGroupRuleResolver(this);
            }
            mTimePeriodRepo = new TimePeriodRepository(mDatabase, mPeriodGroupRuleResolver);
        }
        return mTimePeriodRepo;
    }

    /** 获取已有的 PeriodGroupRuleResolver，不创建新实例 */
    public PeriodGroupRuleResolver getPeriodGroupRuleResolver() {
        if (mPeriodGroupRuleResolver == null) {
            mPeriodGroupRuleResolver = new PeriodGroupRuleResolver(this);
        }
        return mPeriodGroupRuleResolver;
    }

    public AppDatabase getDatabase() {
        return mDatabase;
    }

    public AppLaunchCatalogCache getAppLaunchCatalogCache() {
        if (mAppLaunchCatalogCache == null) {
            mAppLaunchCatalogCache = new AppLaunchCatalogCache(this);
        }
        return mAppLaunchCatalogCache;
    }

    /** 获取安排跳过 DAO（供 ReminderScheduler / AlarmReceiver 等使用）。 */
    public com.nearby.justnow.data.dao.TaskScheduleSkipDao getTaskScheduleSkipDao() {
        return mDatabase.taskScheduleSkipDao();
    }
}
