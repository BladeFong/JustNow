package com.nearby.justnow.ui.main;

import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.ui.base.BaseTaskViewModel;
import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.store.PrefsConfig;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;
import com.nearby.justnow.data.repository.TaskExecutionRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.observer.DataChangeDispatcher;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.data.store.CutoffTimeStore;
import com.nearby.justnow.ui.base.SingleLiveEvent;
import com.nearby.justnow.ui.engine.DisplayEngine;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.DisplayPolicy;
import com.nearby.justnow.ui.engine.DisplayPolicyRepository;
import com.nearby.justnow.ui.engine.PriorityTagConfig;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.ui.period.PeriodTextResolver;
import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.util.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import com.nearby.justnow.ui.base.TaskFilterHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主界面 ViewModel — 任务列表 + 执行 + 时间段 + 智能展示引擎
 */
public class MainViewModel extends BaseTaskViewModel {

    private PreCompleteConfirmCallback mPreCompleteCallback;
    private TaskChecklistRepository mChecklistRepo;

    public void setPreCompleteConfirmCallback(PreCompleteConfirmCallback callback) {
        mPreCompleteCallback = callback;
    }

    public TaskChecklistRepository getChecklistRepo() {
        return mChecklistRepo;
    }

    private TaskRepository mTaskRepo;
    private TaskExecutionRepository mExecutionRepo;
    private TimePeriodRepository mPeriodRepo;
    private TagRepository mTagRepo;
    private TaskScheduleRepository mScheduleRepo;
    private DisplayPolicyRepository mDisplayPolicyRepo;

    /** 保存 LiveData 源引用，用于 reloadForCurrentUser() 时 remove + rebind */
    private LiveData<List<TaskEntity>> mTasksSource;
    private LiveData<List<TimePeriodEntity>> mPeriodsSource;
    private LiveData<List<TaskScheduleEntity>> mSchedulesSource;
    private LiveData<List<TimePeriodGroupEntity>> mGroupsSource;
    private LiveData<List<PriorityTagRuleEntity>> mPriorityRulesSource;

    private static final String KEY_DEFAULT_FILTER_TAG = "default_filter_tag_id";

    private SharedPreferences mPrefs;
    private final DisplayEngine mDisplayEngine = new DisplayEngine();
    private PriorityTagConfig mPriorityTagConfig;
    private TimelineBuilder mTimelineBuilder;

    /** 防抖：避免用户操作与 TIME_TICK 同时触发时积压多个重算任务 */
    private final AtomicBoolean mRecomputePending = new AtomicBoolean(false);
    /** 重算排队标志：当前 recompute 执行期间有新的 recompute 请求被丢弃时置位，finally 块中检查 */
    private final AtomicBoolean mRecomputeQueued = new AtomicBoolean(false);

    /** 引擎计算结果 */
    private final MediatorLiveData<EngineResult> mDisplayResult = new MediatorLiveData<>();

    /** 按象限分组计算结果（不截取），供四象限视图使用 */
    private final MediatorLiveData<EngineResult[]> mQuadrantResults = new MediatorLiveData<>();

    /** 优先标签 ID 集合（从数据库 LiveData 派生） */
    private final MediatorLiveData<Set<Long>> mPriorityTagIdsLiveData = new MediatorLiveData<>();

    /** 工作时段优先开关状态（LiveData，供 UI 观察） */
    private final MutableLiveData<Boolean> mWorkTimePriorityEnabledLiveData = new MutableLiveData<>();

    /** 任务点击事件，按状态分流：
     *  - 未执行任务点击 → mTaskStartEvent（开始/安排弹窗）
     *  - 执行中且有详情/模块 → mTaskCompleteToDetailEvent（跳详情页完成）
     *  - 执行中仅标题（无详情/模块）→ mOnlyTitleTaskCompleteEvent（仅标题任务完成流程） */
    private final SingleLiveEvent<Long> mTaskStartEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> mTaskCompleteToDetailEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> mOnlyTitleTaskCompleteEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<Long> mTimelineScheduledTaskClickEvent = new SingleLiveEvent<>();

    public LiveData<Long> getTaskStartEvent() { return mTaskStartEvent; }
    public LiveData<Long> getTaskCompleteToDetailEvent() { return mTaskCompleteToDetailEvent; }
    public LiveData<Long> getOnlyTitleTaskCompleteEvent() { return mOnlyTitleTaskCompleteEvent; }
    public LiveData<Long> getTimelineScheduledTaskClickEvent() { return mTimelineScheduledTaskClickEvent; }

    public void onTimelineScheduledTaskClick(long taskId) {
        mTimelineScheduledTaskClickEvent.postValue(taskId);
    }

    /** 标签过滤（-1 = 不过滤） */
    private long mFilterTagId = -1;

    /** 多标签筛选集合（非空 = 多标签筛选态，优先于 mFilterTagId） */
    private Set<Long> mMultiFilterTagIds = null;

    /** 默认筛选标签（持久化，-1 = 无默认） */
    private long mDefaultFilterTagId;

    /** 主界面可显示的最大任务数（由布局高度 / 单项高度动态计算） */
    private int mMaxDisplayItems = 6;

    /** 优先标签临时关闭（会话级，不持久化） */
    private boolean mSuppressPriority = false;

    /** 引擎结果包装 */
    public static class EngineResult {
        public List<DisplayItem> items;
        public boolean engineFailed;
        public String remainingText;
        /** 当前真实生效时段组。 */
        public List<TimePeriodEntity> periods;
        /** 供左侧时间线坐标使用，不影响真实生效时段业务判断。 */
        public List<TimePeriodEntity> timelinePeriods;
        public TimeRemainingCalculator.PeriodStatus periodStatus;
        public List<TaskEntity> executingTasks;
        public List<TimelineItem> timelineItems;
        /** 非时段提示 */
        public boolean isUpcoming;
        public boolean isTomorrow;
        public boolean showRestHint;
        /** 当前时段名 */
        public String periodName;
        /** 当前命中的时间段组 */
        public String activeGroupType;
        /** 当前命中的时间段组实际生效的优先标签 */
        public Set<Long> priorityTagIds;
    }


    public class TimelineTaskState {
        public final TaskEntity task;
        public final boolean hasAnyExecution;
        public final boolean hasRecurringSchedule;

        private TimelineTaskState(TaskEntity task, boolean hasAnyExecution,
                                   boolean hasRecurringSchedule) {
            this.task = task;
            this.hasAnyExecution = hasAnyExecution;
            this.hasRecurringSchedule = hasRecurringSchedule;
        }
    }

    public MainViewModel(JustNowApplication app) {
        super(app);
        mTaskRepo = app.getTaskRepository();
        mExecutionRepo = app.getTaskExecutionRepository();
        mPeriodRepo = app.getTimePeriodRepository();
        mTagRepo = app.getTagRepository();
        mScheduleRepo = app.getTaskScheduleRepository();
        mDisplayPolicyRepo = app.getDisplayPolicyRepository();
        mChecklistRepo = app.getTaskChecklistRepository();

        mPrefs = com.nearby.justnow.data.store.UserPrefs.getPrefs(
            app, app.getCurrentUserId(), PrefsConfig.PREFS_NAME);
        mDefaultFilterTagId = mPrefs.getLong(KEY_DEFAULT_FILTER_TAG, -1);

        mPriorityTagConfig = new PriorityTagConfig(app, mTagRepo);
        mTimelineBuilder = new TimelineBuilder(mTaskRepo, mScheduleRepo);
        mWorkTimePriorityEnabledLiveData.setValue(mPriorityTagConfig.isWorkTimePriorityEnabled());

        // 绑定数据源
        bindSources();

    }

    /** 绑定所有 LiveData 数据源。构造函数与 reloadForCurrentUser() 复用。 */
    private void bindSources() {
        // 从按组优先规则派生优先标签 ID 集合，作为重算触发源。
        mPriorityRulesSource = mTagRepo.getAllPriorityRulesLive();
        mPriorityTagIdsLiveData.addSource(mPriorityRulesSource, rules -> {
            java.util.Set<Long> ids = new java.util.HashSet<>();
            if (rules != null) for (PriorityTagRuleEntity rule : rules) ids.add(rule.tagId);
            mPriorityTagIdsLiveData.setValue(ids);
        });

        // 组合 tasks + periods + priorityTags → engine result
        mTasksSource = mTaskRepo.getAllActiveTasks();
        mPeriodsSource = mPeriodRepo.getAllPeriods();
        mSchedulesSource = mScheduleRepo.getAllEnabledSchedulesLive();
        mGroupsSource = mPeriodRepo.getAllGroups();

        mDisplayResult.addSource(mTasksSource, t -> recompute());
        mDisplayResult.addSource(mPeriodsSource, p -> recompute());
        mDisplayResult.addSource(mSchedulesSource, s -> recompute());
        mDisplayResult.addSource(mGroupsSource, p -> recompute());
        mDisplayResult.addSource(mPriorityTagIdsLiveData, ids -> recompute());

        mQuadrantResults.addSource(mTasksSource, t -> refreshQuadrantOverview());
        mQuadrantResults.addSource(mPeriodsSource, p -> refreshQuadrantOverview());
        mQuadrantResults.addSource(mSchedulesSource, s -> refreshQuadrantOverview());
        mQuadrantResults.addSource(mGroupsSource, p -> refreshQuadrantOverview());
        mQuadrantResults.addSource(mPriorityTagIdsLiveData, ids -> refreshQuadrantOverview());
    }

    /** 移除所有 LiveData 数据源。 */
    private void unbindSources() {
        if (mPriorityRulesSource != null) {
            mPriorityTagIdsLiveData.removeSource(mPriorityRulesSource);
        }
        if (mTasksSource != null) {
            mDisplayResult.removeSource(mTasksSource);
            mQuadrantResults.removeSource(mTasksSource);
        }
        if (mPeriodsSource != null) {
            mDisplayResult.removeSource(mPeriodsSource);
            mQuadrantResults.removeSource(mPeriodsSource);
        }
        if (mSchedulesSource != null) {
            mDisplayResult.removeSource(mSchedulesSource);
            mQuadrantResults.removeSource(mSchedulesSource);
        }
        if (mGroupsSource != null) {
            mDisplayResult.removeSource(mGroupsSource);
            mQuadrantResults.removeSource(mGroupsSource);
        }
        mDisplayResult.removeSource(mPriorityTagIdsLiveData);
        mQuadrantResults.removeSource(mPriorityTagIdsLiveData);
    }

    /**
     * 用户切换后重新加载当前用户的数据源。
     * 重新获取 Repository 实例（指向新用户数据库），重新绑定 LiveData，触发重算。
     */
    public void reloadForCurrentUser() {
        TaskFilterHelper.getInstance(mApp).invalidate();
        unbindSources();

        mTaskRepo = mApp.getTaskRepository();
        mExecutionRepo = mApp.getTaskExecutionRepository();
        mPeriodRepo = mApp.getTimePeriodRepository();
        mTagRepo = mApp.getTagRepository();
        mScheduleRepo = mApp.getTaskScheduleRepository();
        mDisplayPolicyRepo = mApp.getDisplayPolicyRepository();
        mChecklistRepo = mApp.getTaskChecklistRepository();

        mPrefs = com.nearby.justnow.data.store.UserPrefs.getPrefs(
            mApp, mApp.getCurrentUserId(), PrefsConfig.PREFS_NAME);
        mDefaultFilterTagId = mPrefs.getLong(KEY_DEFAULT_FILTER_TAG, -1);

        mPriorityTagConfig = new PriorityTagConfig(mApp, mTagRepo);
        mTimelineBuilder = new TimelineBuilder(mTaskRepo, mScheduleRepo);

        bindSources();

        runOnUiThread(() -> {
            recompute();
            refreshQuadrantOverview();
        });
    }

    public LiveData<EngineResult> getDisplayResult() {
        return mDisplayResult;
    }

    /** 获取按象限分组计算结果（不截取），供四象限视图观察 */
    public LiveData<EngineResult[]> getQuadrantResults() {
        return mQuadrantResults;
    }

    /** 刷新依赖当前时间的时段状态与展示结果。 */
    public void refreshTimeState() {
        runInBackground(() -> {
            lazyRefreshState();
            runOnUiThread(() -> {
                recompute();
                refreshQuadrantOverview();
            });
        });
    }

    /** 协调入口：守卫刷新过期的安排和截止时间。 */
    private void lazyRefreshState() {
        mScheduleRepo.refreshExpiredOnceSchedules();
        refreshExpiredCutoff();
    }

    /** 检查截止时间是否过期或时段已结束，过期则清除。 */
    private void refreshExpiredCutoff() {
        int cutoff = CutoffTimeStore.getCutoffEndMinute(mApp);
        if (cutoff == 0) return;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60
            + cal.get(Calendar.MINUTE);
        // 截止时间未过且在时段内 → 不清除
        if (nowMinute < cutoff) return;
        CutoffTimeStore.clearCutoffEndMinute(mApp);
        DataChangeDispatcher.notifyTaskDataChanged();
    }

    /** 设置标签过滤 */
    public void setFilterTag(long tagId) {
        this.mFilterTagId = tagId;
        this.mMultiFilterTagIds = null;
        recompute();
    }

    /** 清除标签过滤 */
    public void clearFilterTag() {
        this.mFilterTagId = -1;
        recompute();
    }

    /** 设置多标签筛选 */
    public void setMultiFilterTags(Set<Long> tagIds) {
        this.mMultiFilterTagIds = (tagIds != null && !tagIds.isEmpty()) ? tagIds : null;
        recompute();
    }

    /** 清除多标签筛选 */
    public void clearMultiFilter() {
        this.mMultiFilterTagIds = null;
        this.mFilterTagId = -1;
        recompute();
    }

    public boolean isMultiFilterActive() {
        return mMultiFilterTagIds != null && !mMultiFilterTagIds.isEmpty();
    }

    public LiveData<List<TagEntity>> getTopTags(int limit) {
        return mTagRepo.getTopTags(limit);
    }

    /** 应用默认筛选（从任何界面返回主界面时调用）：清除所有筛选 */
    public void applyDefaultFilter() {
        this.mFilterTagId = -1;
        this.mMultiFilterTagIds = null;
        if (mDefaultFilterTagId >= 0) {
            this.mFilterTagId = mDefaultFilterTagId;
        }
        recompute();
    }

    /** 重置标签筛选和优先标签临时关闭状态（App 退后台再回来时调用） */
    public void resetFiltersAndPriority() {
        mSuppressPriority = false;
        applyDefaultFilter();
    }

    /** 设置默认筛选标签（持久化），-1 表示无默认 */
    public void setDefaultFilterTag(long tagId) {
        this.mDefaultFilterTagId = tagId;
        mPrefs.edit().putLong(KEY_DEFAULT_FILTER_TAG, tagId).apply();
    }

    public long getDefaultFilterTagId() {
        return mDefaultFilterTagId;
    }

    /** 根据实际视图尺寸更新可显示任务数，触发 recompute */
    public void setMaxDisplayItems(int count) {
        if (count > 0 && count != mMaxDisplayItems) {
            mMaxDisplayItems = count;
            recompute();
        }
    }

    public boolean isFiltering() {
        return mFilterTagId >= 0 || isMultiFilterActive();
    }

    public long getFilterTagId() {
        return mFilterTagId;
    }

    // ---- 优先标签 ----

    public LiveData<Set<Long>> getPriorityTagIdsLiveData() {
        return mPriorityTagIdsLiveData;
    }

    public LiveData<Boolean> getWorkTimePriorityEnabledLiveData() {
        return mWorkTimePriorityEnabledLiveData;
    }

    public void addPriorityTag(long tagId) {
        mPriorityTagConfig.addPriorityTag(tagId);
    }

    public void removePriorityTag(long tagId) {
        mPriorityTagConfig.removePriorityTag(tagId);
    }

    public boolean isPriorityTag(long tagId) {
        return mPriorityTagConfig.isPriorityTag(tagId);
    }

    public boolean isWorkTimePriorityEnabled() {
        return mPriorityTagConfig.isWorkTimePriorityEnabled();
    }

    public void setWorkTimePriorityEnabled(boolean enabled) {
        mPriorityTagConfig.setWorkTimePriorityEnabled(enabled);
        mWorkTimePriorityEnabledLiveData.postValue(enabled);
        recompute();
    }

    /** 切换优先标签临时关闭/恢复（会话级，不持久化） */
    public void togglePrioritySuppress() {
        mSuppressPriority = !mSuppressPriority;
        recompute();
    }

    public boolean isPrioritySuppressed() {
        return mSuppressPriority;
    }

    private void recompute() {
        if (mRecomputePending.compareAndSet(false, true)) {
            runInBackground(this::recomputeSync);
        } else {
            mRecomputeQueued.set(true);
        }
    }


    private void recomputeSync() {
        try {
            // ---- 时段上下文 ----
            String scheduleProfile = mPrefs.getString("schedule_profile",
                    com.nearby.justnow.data.model.ScheduleProfile.GENERAL);
            ActivePeriodGroup activeGroup = mPeriodRepo.getActivePeriodGroupSync(scheduleProfile);
            List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);
            String activeGroupType = activeGroup.getGroupType();

            // ---- 任务 + 过滤（使用 TaskFilterHelper） ----
            Set<Long> filterTagIds;
            if (isMultiFilterActive()) {
                filterTagIds = mMultiFilterTagIds;
            } else if (mFilterTagId >= 0) {
                filterTagIds = Collections.singleton(mFilterTagId);
            } else {
                filterTagIds = null;
            }

            TaskFilterHelper filterHelper = TaskFilterHelper.getInstance(mApp);
            filterHelper.refreshSync(filterTagIds);
            List<DisplayItem> items = filterHelper.getDisplayItems(mMaxDisplayItems);
            List<TaskEntity> tasks = filterHelper.getFilteredTasks();
            List<TaskEntity> executingTasks = filterHelper.getExecutingTasks();
            List<TaskExecutionEntity> todayExecutions = filterHelper.getTodayExecutions();
            Map<Long, TagEntity> tagMap = filterHelper.getTagMap();
            TimeRemainingCalculator.PeriodStatus status = filterHelper.getStatus();

            // 主界面特有逻辑
            List<TimePeriodEntity> timelinePeriods = TimeRemainingCalculator.sortPeriods(
                    mPeriodRepo.getTimelinePeriodsSync(scheduleProfile));
            TimeRemainingCalculator.StatusText statusText = TimeRemainingCalculator.buildStatusText(periods, status);
            Set<Long> priorityTagIds = mPriorityTagConfig.getEffectivePriorityTagIds(activeGroupType, status.period);
            List<TimelineItem> timelineItems = mTimelineBuilder.build(tasks, todayExecutions);

            // ---- 引擎计算（使用缓存的 DisplayItem） ----
            Set<Long> enginePriorityIds = mSuppressPriority ? Collections.emptySet() : priorityTagIds;

            EngineResult result = assembleDisplayItems(items, periods, timelinePeriods,
                    status, executingTasks, timelineItems,
                    statusText.isUpcoming, statusText.isTomorrow,
                    statusText.showRestHint, activeGroupType, priorityTagIds);
            mDisplayResult.postValue(result);
        } finally {
            mRecomputePending.set(false);
            if (mRecomputeQueued.getAndSet(false)) {
                recompute();
            }
        }
    }

    private void computeQuadrantOverviewSync() {
        // ---- 时段上下文 ----
        String scheduleProfile = mPrefs.getString("schedule_profile",
                com.nearby.justnow.data.model.ScheduleProfile.GENERAL);
        ActivePeriodGroup activeGroup = mPeriodRepo.getActivePeriodGroupSync(scheduleProfile);
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);
        String activeGroupType = activeGroup.getGroupType();
        int cutoffEndMinute = CutoffTimeStore.getCutoffEndMinute(mApp);
        TimeRemainingCalculator.PeriodStatus status = TimeRemainingCalculator.compute(periods, cutoffEndMinute);
        List<TimePeriodEntity> timelinePeriods = TimeRemainingCalculator.sortPeriods(
                mPeriodRepo.getTimelinePeriodsSync(scheduleProfile));
        TimeRemainingCalculator.StatusText statusText = TimeRemainingCalculator.buildStatusText(periods, status);
        Set<Long> priorityTagIds = mPriorityTagConfig.getEffectivePriorityTagIds(activeGroupType, status.period);

        // ---- 任务 + 过滤（不过滤今日隐藏，四象限应显示所有任务） ----
        List<TaskEntity> tasks = mTaskRepo.getAllActiveTasksSync();

        // 仅应用自动完成
        Set<Long> autoCompletedIds = TaskExecutionAutoCompleter.completeExpiredRunningTasksSync(
                mTaskRepo, mExecutionRepo, tasks, periods, mPeriodRepo.getAllPeriodsSync());
        if (!autoCompletedIds.isEmpty()) {
            tasks.removeIf(t -> autoCompletedIds.contains(t.id));
            for (long autoId : autoCompletedIds) {
                ReminderScheduler.cancelOvertimeCheck(mApp, autoId);
            }
        }

        // 标签过滤（全局筛选对四象限也应生效）
        if (isMultiFilterActive()) {
            tasks.removeIf(t -> t.tagId == null || !mMultiFilterTagIds.contains(t.tagId));
        } else if (mFilterTagId >= 0) {
            tasks.removeIf(t -> t.tagId == null || t.tagId != mFilterTagId);
        }

        Map<Long, TagEntity> tagMap = mTagRepo.getAllTagsMapSync();
        List<TaskExecutionEntity> todayExecutions = mExecutionRepo.getTodayExecutionsSync();
        List<TimelineItem> timelineItems = mTimelineBuilder.build(tasks, todayExecutions);

        List<TaskEntity> executingTasks = new ArrayList<>();
        if (tasks != null) {
            for (TaskEntity t : tasks) {
                if (t.executingStartMs > 0) executingTasks.add(t);
            }
        }

        // ---- 引擎计算（按象限分组，不截取） ----
        Set<Long> enginePriorityIds = mSuppressPriority ? Collections.emptySet() : priorityTagIds;
        List<TaskScheduleEntity> todaySchedules = mScheduleRepo.getAllEnabledSchedulesSync();
        Set<Long> schedulePriorityIds = computeSchedulePriorityIds(todaySchedules);
        DisplayPolicy displayPolicy = mDisplayPolicyRepo.getEffectivePolicySync();
        List<DisplayItem>[] quadrantItems = mDisplayEngine.computeByQuadrant(
                new int[]{1, 1, 1, 1}, tasks, tagMap, status.remainingMinutes,
                enginePriorityIds, schedulePriorityIds, displayPolicy);

        EngineResult[] results = new EngineResult[4];
        for (int q = 0; q < 4; q++) {
            if (quadrantItems[q] != null) {
                results[q] = assembleDisplayItems(quadrantItems[q], periods, timelinePeriods,
                        status, executingTasks, timelineItems,
                        statusText.isUpcoming, statusText.isTomorrow,
                        statusText.showRestHint, activeGroupType, priorityTagIds);
            }
        }
        mQuadrantResults.postValue(results);
    }

    /** 刷新四象限概览（由数据变更/页面可见时触发） */
    public void refreshQuadrantOverview() {
        runInBackground(this::computeQuadrantOverviewSync);
    }

    // ---- 任务执行 ----

    public void startTaskNow(long taskId, java.util.function.Consumer<TaskStartResult> callback) {
        runInBackground(() -> {
            TaskStartResult result = evaluateTaskStartSync(taskId, true);
            if (callback != null) {
                runOnUiThread(() -> callback.accept(result));
            }
        });
    }

    public void checkTaskStart(long taskId, java.util.function.Consumer<TaskStartResult> callback) {
        runInBackground(() -> {
            TaskStartResult result = evaluateTaskStartSync(taskId, false);
            runOnUiThread(() -> callback.accept(result));
        });
    }

    private TaskStartResult evaluateTaskStartSync(long taskId, boolean startWhenAllowed) {
        TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
        if (task == null || task.isArchived) {
            return new TaskStartResult(TaskStartResult.BLOCKED_TASK_MISSING);
        }

        TaskEntity runningTask = mTaskRepo.getRunningTaskSync();
        if (runningTask != null) {
            return new TaskStartResult(TaskStartResult.BLOCKED_RUNNING);
        }

        ActivePeriodGroup activeGroup = mPeriodRepo.getActivePeriodGroupSync();
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);
        int cutoffEndMinute = CutoffTimeStore.getCutoffEndMinute(mApp);
        TimeRemainingCalculator.PeriodStatus status = TimeRemainingCalculator.compute(periods, cutoffEndMinute);
        if (!status.isInPeriod() && task.focusMinutes > 0) {
            return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
        }

        if (task.focusMinutes > 0 && status.remainingMinutes + 15 < task.focusMinutes) {
            return new TaskStartResult(TaskStartResult.BLOCKED_TIME_NOT_ENOUGH);
        }

        if (startWhenAllowed) {
            mTaskRepo.startExecutionSync(taskId, System.currentTimeMillis());
            // 消除该任务的所有相关通知（安排提醒与超时）
            ReminderNotifier.cancelForTask(mApp, mScheduleRepo, taskId);
            // 专注任务调度超时检查
            TaskEntity startedTask = mTaskRepo.getTaskByIdSync(taskId);
            new ReminderScheduler(mApp).scheduleOvertimeCheck(startedTask);
        }
        return new TaskStartResult(TaskStartResult.OK);
    }

    /**
     * 计算当前在 30 分钟优先窗口内的安排任务 ID 集合。
     * 窗口 = [scheduledTime, scheduledTime+30]（分钟-of-day）。
     * 推迟只重新设闹钟，不延长推荐优先窗口。
     */
    public static Set<Long> computeSchedulePriorityIds(List<TaskScheduleEntity> schedules) {
        if (schedules == null || schedules.isEmpty()) return Collections.emptySet();
        long nowMs = System.currentTimeMillis();
        long todayStartMs = com.nearby.justnow.util.DateUtils.todayStartMs();
        int nowMinute = (int) ((nowMs - todayStartMs) / 60000L);
        Set<Long> ids = new HashSet<>();
        for (TaskScheduleEntity s : schedules) {
            if (!s.enabled) continue;
            if (!com.nearby.justnow.scheduler.TaskScheduleMatcher.matchesToday(s)) continue;
            if (nowMinute >= s.scheduledTime && nowMinute < s.scheduledTime + 30) {
                ids.add(s.taskId);
            }
        }
        return ids;
    }

    /** 完成琐碎任务：计算实际耗时并记录 */
    public void completeChoreTask(long taskId, Runnable onComplete) {
        completeRunningTask(taskId, false, onComplete);
    }

    // ---- < 15min 完成路径 ----

    /**
     * &lt; 15min 完成"取消"选项：仅退出对话框，任务保持执行中。
     * 当前对话框未在数据层留任何痕迹，因此实际上是 no-op；保留方法是为了语义清晰。
     */
    public void cancelShortCompletion() {
        // no-op：执行中状态保留，对话框退出由 UI 层负责
    }

    /**
     * &lt; 15min 完成「直接完成」：不写 task_executions，
     * 仅清除执行中状态，加入"今日隐藏"集合；按需停止安排。
     *
     * @param stopSchedule 是否一并 disable 当前任务的有效安排（入口3 必传 true）。
     */
    public void shortCompleteDirect(long taskId, boolean stopSchedule, Runnable onComplete) {
        runInBackground(() -> {
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            shortCompleteFlow(taskId, stopSchedule, false, schedule, onComplete);
        });
    }

    /**
     * &lt; 15min 完成「(完成 / 不再安排) 并调整」：不写 task_executions，
     * 将任务 focusMinutes 改 0（连带清空执行中状态）、按需停止安排、加入"今日隐藏"集合。
     *
     * @param stopSchedule 是否一并 disable 当前任务的有效安排
     *                     （入口1 无安排 = false；入口1 有安排 = true；入口3 = true）。
     */
    public void shortCompleteAndConvertToChore(long taskId, boolean stopSchedule, Runnable onComplete) {
        runInBackground(() -> {
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            shortCompleteFlow(taskId, stopSchedule, true, schedule, onComplete);
        });
    }

    public void completeRunningTask(long taskId, boolean stopSchedule, Runnable onComplete) {
        completeRunningTask(taskId, stopSchedule, onComplete, false);
    }

    private void completeRunningTask(long taskId, boolean stopSchedule, Runnable onComplete, boolean skipPreCheck) {
        runInBackground(() -> {
            TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
            // 完成前检查（仅首次）
            if (!skipPreCheck && mPreCompleteCallback != null) {
                if (checkListStateNeedsConfirm(task, taskId)) {
                    runOnUiThread(() -> {
                        mPreCompleteCallback.onConfirmNeeded(taskId, CONFIRM_TYPE_CHECKLIST_STATE,
                            () -> completeRunningTask(taskId, stopSchedule, onComplete, true));
                    });
                    return;
                }
            }
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            completeTaskFlow(task, schedule, stopSchedule, onComplete);
        });
    }

    @Override
    protected void onPostComplete() {
        super.onPostComplete();
        recompute();
    }



    private EngineResult assembleDisplayItems(List<DisplayItem> items, List<TimePeriodEntity> periods,
            List<TimePeriodEntity> timelinePeriods, TimeRemainingCalculator.PeriodStatus status,
            List<TaskEntity> executingTasks, List<TimelineItem> timelineItems,
            boolean isUpcoming, boolean isTomorrow, boolean showRestHint,
            String activeGroupType, Set<Long> priorityTagIds) {
        EngineResult result = new EngineResult();
        result.items = items;
        result.engineFailed = false;
        result.remainingText = status.isInPeriod() ? status.getRemainingText(mApp.getResources()) : "";
        result.periods = periods;
        result.timelinePeriods = timelinePeriods;
        result.periodStatus = status;
        result.executingTasks = executingTasks;
        result.timelineItems = timelineItems;
        result.isUpcoming = isUpcoming;
        result.isTomorrow = isTomorrow;
        result.showRestHint = showRestHint;
        if (status.isInPeriod() && status.isCutoff) {
            String time = String.format("%02d:%02d", status.endMinute / 60, status.endMinute % 60);
            result.periodName = mApp.getString(R.string.s_cutoff_label, time);
        } else {
            result.periodName = getPeriodName(status.isInPeriod() ? status.period : findNextPeriod(periods));
        }
        result.activeGroupType = activeGroupType;
        result.priorityTagIds = priorityTagIds;
        return result;
    }

    /** 统一任务点击入口，按执行状态二级分流：
     *  顶层：未执行 → 开始/安排流程；执行中 → 完成相关流程
     *  执行中再分：有详情/模块 → 跳详情页完成；仅标题 → 弹"仅标题任务完成"弹窗 */
    public void resolveAndHandleTaskClick(long taskId) {
        runInBackground(() -> {
            TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
            if (task == null) return;
            boolean isExecuting = task.executingStartMs > 0 && task.executingEndMs == 0;
            boolean hasContent = (task.detailMarkdown != null && !task.detailMarkdown.isEmpty())
                || task.detailModuleType != null;
            runOnUiThread(() -> {
                if (!isExecuting) {
                    mTaskStartEvent.postValue(taskId);
                } else {
                    if (hasContent) {
                        mTaskCompleteToDetailEvent.postValue(taskId);
                    } else {
                        mOnlyTitleTaskCompleteEvent.postValue(taskId);
                    }
                }
            });
        });
    }

    public void recordComplete(long taskId, Runnable onComplete) {
        mExecutionRepo.recordComplete(taskId, 0, onComplete);
    }

    public void archiveTask(long taskId) {
        runInBackground(() -> {
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            archiveTaskFlow(taskId, schedule, null);
        });
    }

    public TaskEntity getTaskByIdSync(long taskId) {
        return mTaskRepo.getTaskByIdSync(taskId);
    }

    public TaskScheduleEntity getActiveScheduleSync(long taskId) {
        return mScheduleRepo.getActiveScheduleSync(taskId);
    }

    public boolean hasAnyExecutionSync(long taskId) {
        return mExecutionRepo.countExecutionsSync(taskId) > 0;
    }

    public void loadActiveSchedule(long taskId, java.util.function.Consumer<TaskScheduleEntity> callback) {
        runInBackground(() -> {
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            runOnUiThread(() -> callback.accept(schedule));
        });
    }

    public void loadTimelineTaskState(long taskId,
                                      java.util.function.Consumer<TimelineTaskState> callback) {
        runInBackground(() -> {
            TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
            if (task == null) {
                if (callback != null) runOnUiThread(() -> callback.accept(null));
                return;
            }
            boolean hasAnyExecution = mExecutionRepo.countExecutionsSync(taskId) > 0;
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            boolean hasRecurringSchedule = schedule != null && schedule.isRecurring();
            TimelineTaskState state = new TimelineTaskState(task, hasAnyExecution,
                    hasRecurringSchedule);
            if (callback != null) runOnUiThread(() -> callback.accept(state));
        });
    }

    public String getScheduleText(TaskScheduleEntity schedule) {
        if (schedule == null) return "";
        String time = DateUtils.formatMinute(schedule.scheduledTime);
        Resources res = mApp.getResources();
        switch (schedule.scheduleType) {
            case TaskScheduleEntity.TYPE_ONCE:
                return mApp.getString(R.string.s_schedule_once_status, time);
            case TaskScheduleEntity.TYPE_DAILY:
                return mApp.getString(R.string.s_schedule_daily_status, time);
            case TaskScheduleEntity.TYPE_WEEKLY:
                return mApp.getString(R.string.s_schedule_weekly_status,
                    weeklyDaysToString(schedule.scheduleValue, res), time);
            default:
                return time;
        }
    }

    private static String weeklyDaysToString(long scheduleValue, Resources res) {
        String[] dayNames = {
            res.getString(R.string.s_day_sun), res.getString(R.string.s_day_mon),
            res.getString(R.string.s_day_tue), res.getString(R.string.s_day_wed),
            res.getString(R.string.s_day_thu), res.getString(R.string.s_day_fri),
            res.getString(R.string.s_day_sat)
        };
        StringBuilder sb = new StringBuilder();
        int bitmask = (int) scheduleValue;
        for (int i = 0; i < 7; i++) {
            if ((bitmask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append("、");
                sb.append(dayNames[i]);
            }
        }
        return sb.toString();
    }

    private String getPeriodName(TimePeriodEntity period) {
        if (period == null) return "";
        return PeriodTextResolver.getPeriodName(mApp.getResources(), period.nameKey);
    }

    private static TimePeriodEntity findNextPeriod(List<TimePeriodEntity> periods) {
        if (periods == null || periods.isEmpty()) return null;
        Calendar cal = Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        for (TimePeriodEntity p : periods) {
            if (p.startMinute > nowMinute) return p;
        }
        return periods.get(0); // 跨天
    }

    /**
     * 后台重置清单状态并回调（供 Fragment 清单状态确认弹窗"不保存"选项使用）。
     */
    public void resetChecklistStateForCompletion(long taskId, Runnable onConfirmed) {
        runInBackground(() -> {
            mChecklistRepo.resetAllByTaskIdSync(taskId);
            runOnUiThread(onConfirmed);
        });
    }

    /**
     * 后台加载活跃安排并回调到主线程（供 Fragment 短完成弹窗使用）。
     */
    public void loadActiveScheduleForShortCompletion(long taskId,
                                                      java.util.function.Consumer<TaskScheduleEntity> callback) {
        runInBackground(() -> {
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            runOnUiThread(() -> callback.accept(schedule));
        });
    }

    /**
     * 忽略本次提醒：取消通知 + 单次安排→禁用，重复安排→记录跳过并重新调度。
     */
    public void ignoreSchedule(long scheduleId, long taskId, Runnable onComplete) {
        runInBackground(() -> {
            ReminderNotifier.cancel(mApp, scheduleId);

            if (mScheduleRepo.skipOrDisable(scheduleId)) {
                TaskScheduleEntity schedule = mScheduleRepo.getScheduleById(scheduleId);
                TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
                if (schedule != null && task != null && ReminderScheduler.shouldRegisterAlarm(task)) {
                    ReminderScheduler scheduler = new ReminderScheduler(mApp);
                    scheduler.scheduleNextAfterSkip(schedule, task);
                }
            }

            if (onComplete != null) runOnUiThread(onComplete);
        });
    }

}
