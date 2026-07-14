package com.nearby.justnow.ui.base;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.store.ChoreHiddenTodayStore;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.data.store.CutoffTimeStore;
import com.nearby.justnow.ui.engine.DisplayEngine;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.DisplayPolicy;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.ui.main.MainViewModel;
import com.nearby.justnow.ui.main.TimelineBuilder;
import com.nearby.justnow.data.observer.DataChangeDispatcher;
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
 * 1. 首次调用即时执行 + postDelayed 1 秒兜底
 * 2. 防抖窗口内重复调用被忽略，postDelayed 重置为 500ms
 * 3. 无新调用后 500ms 执行一次，保证最终一致性
 */
public class TaskFilterHelper {

    private static final long DEBOUNCE_DELAY = 500;
    private static final long DEBOUNCE_LONG_DELAY = 1000;
    private static TaskFilterHelper sInstance;

    private final JustNowApplication mApp;
    private final Handler mHandler;
    private final DisplayEngine mDisplayEngine = new DisplayEngine();
    private final Runnable mDelayedCompute;
    private boolean mHasPendingDelayed;
    private Set<Long> mPendingFilterTagIds;

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
        mDelayedCompute = () -> {
            AppDatabase.execute(() -> {
                computeFilteredTasks(mPendingFilterTagIds);
                DataChangeDispatcher.notifyTaskDataChanged();
            });
            mHasPendingDelayed = false;
        };
    }

    /**
     * 触发计算（数据变更或 TIME_TICK 时调用）。
     * 防抖：首次即时执行 + 1 秒兜底；防抖窗口内重复调用被忽略，500ms 后刷新。
     * 计算在后台线程执行，结果 post 到主线程更新缓存。
     */
    public void compute(@Nullable Set<Long> filterTagIds) {
        mPendingFilterTagIds = filterTagIds;
        // 始终投递即时异步执行
        AppDatabase.execute(() -> computeFilteredTasks(filterTagIds));
        // 防抖仅控制延迟回调：避免堆积多个 postDelayed
        if (!mHasPendingDelayed) {
            mHasPendingDelayed = true;
            mHandler.postDelayed(mDelayedCompute, DEBOUNCE_LONG_DELAY);
        } else {
            mHandler.removeCallbacks(mDelayedCompute);
            mHandler.postDelayed(mDelayedCompute, DEBOUNCE_DELAY);
        }
    }

    /**
     * 同步刷新缓存（在调用者线程执行，不经过线程池投递）。
     * 供 recomputeSync / Widget update 等需要即时结果的路径使用。
     */
    public void refreshSync(@Nullable Set<Long> filterTagIds) {
        computeFilteredTasks(filterTagIds);
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
            for (long autoId : autoCompletedIds) {
                ReminderScheduler.cancelOvertimeCheck(mApp, autoId);
            }
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

        // 6. 完成模式：日/周/月/年隐藏判定
        java.util.HashSet<Long> todayCompletedIds = new java.util.HashSet<>();
        if (todayExecutions != null) {
            for (TaskExecutionEntity e : todayExecutions) {
                todayCompletedIds.add(e.taskId);
            }
        }
        java.util.Iterator<TaskEntity> iter = tasks.iterator();
        while (iter.hasNext()) {
            TaskEntity task = iter.next();
            if (todayCompletedIds.contains(task.id)) {
                // 今天完成过 → 日模式直接隐藏
                if (task.completionMode == 0) {
                    iter.remove();
                    continue;
                }
                // 周/月/年模式：还需检查周期配额
                String periodKey = com.nearby.justnow.data.repository.TaskRepository.computePeriodKey(task);
                TaskCompletionCounterEntity counter = mApp.getTaskRepository()
                        .getCompletionCounterSync(task.id, periodKey);
                if (counter != null && counter.completed >= task.quota) {
                    iter.remove();
                }
            }
        }

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
        List<TaskScheduleEntity> todaySchedules = mApp.getTaskScheduleRepository().getAllEnabledSchedulesSync();
        Set<Long> schedulePriorityIds = MainViewModel.computeSchedulePriorityIds(todaySchedules);
        DisplayPolicy displayPolicy = mApp.getDisplayPolicyRepository().getEffectivePolicySync();

        return mDisplayEngine.compute(tasks, mTagMap, mStatus.remainingMinutes,
                mStatus.isReverseQuadrant(), maxDisplayItems,
                Collections.emptySet(), schedulePriorityIds, displayPolicy);
    }
}
