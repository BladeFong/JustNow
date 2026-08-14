package com.nearby.justnow;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.nearby.justnow.data.entity.TaskEntity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.store.UserStore;
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
import com.nearby.justnow.ui.engine.DisplayPolicyRepository;
import com.nearby.justnow.widget.WidgetDataChangeNotifier;
import com.ble.notification.sdk.BleNotificationSDK;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.work.WorkManager;

/**
 * Application — 持有数据库单例
 */
public class JustNowApplication extends Application {

    /** 用户列表管理器（不按用户隔离——所有用户共享同一份用户列表） */
    private UserStore mUserStore;

    /** App 退后台标记（AtomicBoolean 保证线程安全），供 MainFragment 在 onResume 时判断是否需重置筛选/暂停状态 */
    private final AtomicBoolean mBackgroundFlag = new AtomicBoolean(false);

    // ---- 多用户 Repository 缓存（每用户独立实例） ----
    private final Map<Long, TaskRepository> mTaskRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TagRepository> mTagRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TaskChecklistRepository> mTaskChecklistRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TaskAppActionRepository> mTaskAppActionRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TaskNoteShareRepository> mTaskNoteShareRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TaskExecutionRepository> mTaskExecutionRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TaskScheduleRepository> mTaskScheduleRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TaskSchedulePostponeRepository> mTaskSchedulePostponeRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, TimePeriodRepository> mTimePeriodRepoMap = new ConcurrentHashMap<>();
    private final Map<Long, PeriodGroupRuleResolver> mPeriodGroupRuleResolverMap = new ConcurrentHashMap<>();
    private final Map<Long, DisplayPolicyRepository> mDisplayPolicyRepoMap = new ConcurrentHashMap<>();
    private AppLaunchCatalogCache mAppLaunchCatalogCache;

    @Override
    public void onCreate() {
        super.onCreate();
        mUserStore = new UserStore(this);
        // 手机端：自动创建默认用户，检测旧 justnow.db 复用 userId=0 保留历史数据
        if (!getResources().getBoolean(R.bool.is_tablet)) {
            if (!mUserStore.hasUsers()) {
                if (getDatabasePath("justnow.db").exists()) {
                    mUserStore.addUserWithId(getString(R.string.s_default_user_name), 0L);
                } else {
                    mUserStore.addUser(getString(R.string.s_default_user_name));
                }
            } else {
                // 已有用户但旧 DB 存在：清掉空用户，重绑 userId=0
                List<UserStore.UserInfo> users = mUserStore.getAllUsers();
                if (users.size() == 1 && users.get(0).userId != 0L
                    && getDatabasePath("justnow.db").exists()) {
                    // 删除新用户留下的空库
                    deleteDatabase("justnow_u" + users.get(0).userId + ".db");
                    // 用 userId=0 重新指向旧库
                    mUserStore.clear();
                    mUserStore.addUserWithId(users.get(0).name, 0L);
                }
            }
        }
        ReminderNotifier.createChannel(this);
        BleNotificationSDK.Companion.init(this);
        DataChangeDispatcher.setNotifier(new WidgetDataChangeNotifier(this));
        // 预热 jieba 分词词典，避免首次输入时的延迟
        getDatabase().runInBackground(() ->
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
        // 全局屏幕方向锁定：手机强制竖屏，平板允许旋转（不限制）
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                boolean isTablet = activity.getResources().getBoolean(R.bool.is_tablet);
                if (!isTablet) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }

    /** 节假日同步日内节流：同一天内不重复检查。 */
    private static volatile int sLastHolidaySyncDay;

    /**
     * 触发节假日数据同步（日内节流 + 月度节流）。
     * 供 onCreate() 和 Widget onUpdate() 调用。
     */
    public void triggerHolidaySync() {
        getDatabase().runInBackground(() -> {
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            HolidayCacheManager cacheManager = new HolidayCacheManager(
                getDatabase().holidayCacheDao());

            // 月度节流：本月已同步过则跳过
            if (!cacheManager.shouldSyncThisMonth(currentYear)) return;

            // 日内节流：已有缓存数据时，同一天内不重复检查
            int today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            if (cacheManager.getSync(currentYear) != null && sLastHolidaySyncDay == today) return;
            sLastHolidaySyncDay = today;

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

    // ---- 多用户支持 ----

    /** 获取当前活跃用户 ID。平板端可切换，手机端始终为默认用户(0)。 */
    public long getCurrentUserId() {
        if (mUserStore == null) return 0L;
        long id = mUserStore.getCurrentUserId();
        return id >= 0 ? id : 0L;
    }

    /** 获取用户列表管理器。 */
    public UserStore getUserStore() {
        return mUserStore;
    }

    /**
     * 是否儿童任务（平板 + 内置图标标签）。
     * 拍照弹窗、拍照按钮显隐、可拍照任务列表的统一判断。
     */
    public boolean isChildTask(@NonNull TaskEntity task) {
        return task.iconName != null && !task.iconName.isEmpty()
            && getResources().getBoolean(R.bool.is_tablet);
    }

    /**
     * 切换到指定用户，清除 ViewModel 级数据缓存。
     * 调用方需自行重建当前 Activity/Fragment UI。
     */
    public void switchToUser(long userId) {
        mUserStore.setCurrentUserId(userId);
        com.nearby.justnow.ui.base.TaskFilterHelper.getInstance(this).invalidate();
    }

    /**
     * 创建新用户并切换。回调在创建完成后执行。
     */
    public void addUser(String name, Runnable onCreated) {
        UserStore.UserInfo info = mUserStore.addUser(name);
        switchToUser(info.userId);
        if (onCreated != null) {
            onCreated.run();
        }
    }

    // ---- Repository getters（按当前用户返回对应实例） ----

    /** 获取当前用户的数据库实例。 */
    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this, getCurrentUserId());
    }

    private PeriodGroupRuleResolver getPeriodGroupRuleResolverForUser(long userId) {
        return mPeriodGroupRuleResolverMap.computeIfAbsent(userId,
            uid -> new PeriodGroupRuleResolver(this));
    }

    /** 获取当前用户的 PeriodGroupRuleResolver。 */
    public PeriodGroupRuleResolver getPeriodGroupRuleResolver() {
        return getPeriodGroupRuleResolverForUser(getCurrentUserId());
    }

    public TaskRepository getTaskRepository() {
        long userId = getCurrentUserId();
        return mTaskRepoMap.computeIfAbsent(userId,
            uid -> new TaskRepository(this, AppDatabase.getInstance(this, uid)));
    }

    public TagRepository getTagRepository() {
        long userId = getCurrentUserId();
        return mTagRepoMap.computeIfAbsent(userId,
            uid -> new TagRepository(AppDatabase.getInstance(this, uid)));
    }

    public TaskChecklistRepository getTaskChecklistRepository() {
        long userId = getCurrentUserId();
        return mTaskChecklistRepoMap.computeIfAbsent(userId,
            uid -> new TaskChecklistRepository(AppDatabase.getInstance(this, uid)));
    }

    public TaskAppActionRepository getTaskAppActionRepository() {
        long userId = getCurrentUserId();
        return mTaskAppActionRepoMap.computeIfAbsent(userId,
            uid -> new TaskAppActionRepository(AppDatabase.getInstance(this, uid)));
    }

    public TaskNoteShareRepository getTaskNoteShareRepository() {
        long userId = getCurrentUserId();
        return mTaskNoteShareRepoMap.computeIfAbsent(userId,
            uid -> new TaskNoteShareRepository(AppDatabase.getInstance(this, uid)));
    }

    public TaskExecutionRepository getTaskExecutionRepository() {
        long userId = getCurrentUserId();
        return mTaskExecutionRepoMap.computeIfAbsent(userId,
            uid -> new TaskExecutionRepository(AppDatabase.getInstance(this, uid)));
    }

    public TaskScheduleRepository getTaskScheduleRepository() {
        long userId = getCurrentUserId();
        return mTaskScheduleRepoMap.computeIfAbsent(userId, uid -> {
            PeriodGroupRuleResolver resolver = getPeriodGroupRuleResolverForUser(uid);
            return new TaskScheduleRepository(AppDatabase.getInstance(this, uid), resolver);
        });
    }

    public TaskSchedulePostponeRepository getTaskSchedulePostponeRepository() {
        long userId = getCurrentUserId();
        return mTaskSchedulePostponeRepoMap.computeIfAbsent(userId,
            uid -> new TaskSchedulePostponeRepository(AppDatabase.getInstance(this, uid)));
    }

    public TimePeriodRepository getTimePeriodRepository() {
        long userId = getCurrentUserId();
        return mTimePeriodRepoMap.computeIfAbsent(userId, uid -> {
            PeriodGroupRuleResolver resolver = getPeriodGroupRuleResolverForUser(uid);
            return new TimePeriodRepository(this, AppDatabase.getInstance(this, uid), resolver);
        });
    }

    public DisplayPolicyRepository getDisplayPolicyRepository() {
        long userId = getCurrentUserId();
        return mDisplayPolicyRepoMap.computeIfAbsent(userId, uid ->
            new DisplayPolicyRepository(this, getTaskRepository()));
    }

    public AppLaunchCatalogCache getAppLaunchCatalogCache() {
        if (mAppLaunchCatalogCache == null) {
            mAppLaunchCatalogCache = new AppLaunchCatalogCache(this);
        }
        return mAppLaunchCatalogCache;
    }

    /** 获取安排跳过 DAO（供 ReminderScheduler / AlarmReceiver 等使用）。 */
    public com.nearby.justnow.data.dao.TaskScheduleSkipDao getTaskScheduleSkipDao() {
        return getDatabase().taskScheduleSkipDao();
    }
}
