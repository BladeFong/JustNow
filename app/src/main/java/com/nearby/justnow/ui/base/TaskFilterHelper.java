package com.nearby.justnow.ui.base;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.store.ChoreHiddenTodayStore;
import com.nearby.justnow.data.store.CutoffTimeStore;
import com.nearby.justnow.ui.engine.DisplayEngine;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.DisplayPolicy;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.ui.main.MainViewModel;
import com.nearby.justnow.ui.main.TimelineBuilder;
import com.nearby.justnow.data.repository.TaskExecutionAutoCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务数据获取和过滤工具 — 主界面右侧栏和 Widget 共用。
 * 单实例 + 防抖 + 缓存，避免重复计算。
 *
 * 防抖机制：
 * 1. 调用 compute 时，如果不存在 postDelayed，实时 post 一次
 * 2. 再 postDelayed 个 1 秒
 * 3. 1 秒内的重复调用被忽略
 * 4. 1 秒后再执行一次，保证最终一致性
 */
public class TaskFilterHelper {

    private static final long DEBOUNCE_DELAY = 1000; // 1 秒防抖
    private static TaskFilterHelper sInstance;

    private final JustNowApplication mApp;
    private final Handler mHandler;
    private Runnable mPendingCompute;
    private boolean mHasPendingDelayed;

    // 缓存数据
    private List<TaskEntity> mFilteredTasks;
    private List<TaskEntity> mExecutingTasks;
    private List<TaskExecutionEntity> mTodayExecutions;
    private Map<Long, TagEntity> mTagMap;
    private List<TimePeriodEntity> mPeriods;
    private TimeRemainingCalculator.PeriodStatus mStatus;

    public static synchronized TaskFilterHelper getInstance(@NonNull JustNowApplication app) {
        if (sInstance == null) {
            sInstance = new TaskFilterHelper(app);
        }
        return sInstance;
    }

    private TaskFilterHelper(@NonNull JustNowApplication app) {
        mApp = app;
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 触发计算（数据变更或 TIME_TICK 时调用）。
     * 防抖：1 秒内重复调用被忽略，但保证 1 秒后再执行一次。
     * 计算在后台线程执行，结果 post 到主线程更新缓存。
     */
    public void compute(@Nullable Set<Long> filterTagIds) {
        // 移除待执行的计算
        if (mPendingCompute != null) {
            mHandler.removeCallbacks(mPendingCompute);
            mPendingCompute = null;
        }

        // 创建新的计算任务（后台线程执行）
        mPendingCompute = () -> {
            computeFilteredTasks(filterTagIds);
            mPendingCompute = null;
            mHasPendingDelayed = false;
        };

        // 实时 post 一次（后台线程）
        AppDatabase.execute(mPendingCompute);

        // 再 postDelayed 个 1 秒（如果还没有 delayed 任务）
        if (!mHasPendingDelayed) {
            mHasPendingDelayed = true;
            mHandler.postDelayed(() -> {
                if (mPendingCompute != null) {
                    // 还有待执行的计算，执行它
                    AppDatabase.execute(mPendingCompute);
                } else {
                    // 没有待执行的计算，重新计算一次（保证最终一致性）
                    AppDatabase.execute(() -> computeFilteredTasks(filterTagIds));
                }
                mHasPendingDelayed = false;
            }, DEBOUNCE_DELAY);
        }
    }

    /**
     * 获取 DisplayItem 列表（从缓存获取，不触发计算）。
     */
    @NonNull
    public List<DisplayItem> getDisplayItems(int maxDisplayItems) {
        if (mFilteredTasks == null) {
            // 缓存为空，立即计算
            computeFilteredTasks(null);
        }
        return computeDisplayItems(mFilteredTasks, maxDisplayItems);
    }

    /** 获取过滤后的任务列表 */
    @Nullable
    public List<TaskEntity> getFilteredTasks() {
        return mFilteredTasks;
    }

    /** 获取执行中任务列表 */
    @Nullable
    public List<TaskEntity> getExecutingTasks() {
        return mExecutingTasks;
    }

    /** 获取今日执行记录 */
    @Nullable
    public List<TaskExecutionEntity> getTodayExecutions() {
        return mTodayExecutions;
    }

    /** 获取标签映射 */
    @Nullable
    public Map<Long, TagEntity> getTagMap() {
        return mTagMap;
    }

    /** 获取时段列表 */
    @Nullable
    public List<TimePeriodEntity> getPeriods() {
        return mPeriods;
    }

    /** 获取时段状态 */
    @Nullable
    public TimeRemainingCalculator.PeriodStatus getStatus() {
        return mStatus;
    }

    // ==================== 内部方法 ====================

    private void computeFilteredTasks(@Nullable Set<Long> filterTagIds) {
        // 1. 获取所有活跃任务
        List<TaskEntity> tasks = mApp.getTaskRepository().getAllActiveTasksSync();

        // 2. 自动完成过期任务
        List<TimePeriodEntity> allPeriods = mApp.getTimePeriodRepository().getAllPeriodsSync();
        ActivePeriodGroup activeGroup = mApp.getTimePeriodRepository().getActivePeriodGroupSync();
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);
        Set<Long> autoCompletedIds = TaskExecutionAutoCompleter.completeExpiredRunningTasksSync(
                mApp.getTaskRepository(), mApp.getTaskExecutionRepository(),
                tasks, periods, allPeriods);
        if (!autoCompletedIds.isEmpty()) {
            tasks.removeIf(t -> autoCompletedIds.contains(t.id));
        }

        // 3. 隐藏今日已隐藏的任务（短时间完成的专注任务）
        ChoreHiddenTodayStore hiddenStore = new ChoreHiddenTodayStore(mApp);
        Set<Long> hiddenToday = hiddenStore.getHiddenTodayIds();
        if (!hiddenToday.isEmpty()) {
            tasks.removeIf(t -> hiddenToday.contains(t.id) && t.executingStartMs <= 0);
        }

        // 4. 标签过滤
        if (filterTagIds != null && !filterTagIds.isEmpty()) {
            tasks.removeIf(t -> t.tagId == null || !filterTagIds.contains(t.tagId));
        }

        // 5. 隐藏今日已完成的琐碎任务
        List<TaskExecutionEntity> todayExecutions = mApp.getTaskExecutionRepository().getTodayExecutionsSync();
        TimelineBuilder.hideCompletedChoresForToday(tasks, todayExecutions);

        // 保存缓存
        mFilteredTasks = tasks;
        mTodayExecutions = todayExecutions;
        mTagMap = mApp.getTagRepository().getAllTagsMapSync();
        mPeriods = periods;
        mExecutingTasks = new ArrayList<>();
        for (TaskEntity t : tasks) {
            if (t.executingStartMs > 0) {
                mExecutingTasks.add(t);
            }
        }

        // 时段状态
        int cutoffEndMinute = CutoffTimeStore.getCutoffEndMinute(mApp);
        mStatus = TimeRemainingCalculator.compute(periods, cutoffEndMinute);
    }

    private List<DisplayItem> computeDisplayItems(List<TaskEntity> tasks, int maxDisplayItems) {
        Map<Long, TaskQuadrantDegradeEntity> degradeMap = mApp.getTaskRepository().getNonExpiredDegradeMapSync();
        List<TaskScheduleEntity> todaySchedules = mApp.getTaskScheduleRepository().getAllEnabledSchedulesSync();
        Set<Long> schedulePriorityIds = MainViewModel.computeSchedulePriorityIds(todaySchedules);
        DisplayPolicy displayPolicy = mApp.getDisplayPolicyRepository().getEffectivePolicySync();

        DisplayEngine displayEngine = new DisplayEngine();
        return displayEngine.compute(tasks, mTagMap, mStatus.remainingMinutes,
                mStatus.isReverseQuadrant(), maxDisplayItems,
                Collections.emptySet(), degradeMap, schedulePriorityIds, displayPolicy);
    }
}
