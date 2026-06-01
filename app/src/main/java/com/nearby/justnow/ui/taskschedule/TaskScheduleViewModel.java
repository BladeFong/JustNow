package com.nearby.justnow.ui.taskschedule;

import androidx.lifecycle.ViewModel;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.scheduler.TaskScheduleMatcher;
import com.nearby.justnow.util.PermissionHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务安排 ViewModel。
 */
public class TaskScheduleViewModel extends BaseViewModel {

    private final TaskRepository mTaskRepo;
    private final TaskScheduleRepository mScheduleRepo;
    private final TimePeriodRepository mPeriodRepo;
    private final PeriodGroupRuleResolver mResolver;

    /** 编辑模式下的已有安排（null = 新建）。 */
    private TaskScheduleEntity mExistingSchedule;

    /** 缓存：所有 enabled 安排。loadInitialState 时填充，每次 save 后失效。 */
    private volatile List<TaskScheduleEntity> mEnabledSchedulesCache;
    /** 缓存：dateMs -> 活跃非琐碎时段。命中减少 PeriodGroupRuleResolver 重复计算。 */
    private final Map<Long, List<TimePeriodEntity>> mPeriodsCache = new HashMap<>();
    /** 缓存：schedule 关联的 task，供 getOccupiedSlots 展开范围用。与 mEnabledSchedulesCache 同生命周期刷新。 */
    private Map<Long, TaskEntity> mTaskCache;

    public static class InitialState {
        public final TaskEntity task;
        public final TaskScheduleEntity schedule;

        public InitialState(TaskEntity task, TaskScheduleEntity schedule) {
            this.task = task;
            this.schedule = schedule;
        }
    }

    public TaskScheduleViewModel(JustNowApplication app) {
        super(app);
        mTaskRepo = new TaskRepository(mDb);
        mScheduleRepo = new TaskScheduleRepository(mDb);
        mResolver = new PeriodGroupRuleResolver(mApp);
        mPeriodRepo = new TimePeriodRepository(mDb, mResolver);
    }

    public TaskEntity getTaskSync(long taskId) {
        return mTaskRepo.getTaskByIdSync(taskId);
    }

    public TaskScheduleEntity getActiveScheduleSync(long taskId) {
        return mScheduleRepo.getActiveScheduleSync(taskId);
    }

    public void loadInitialState(long taskId, java.util.function.Consumer<InitialState> callback) {
        runInBackground(() -> {
            InitialState state = new InitialState(
                mTaskRepo.getTaskByIdSync(taskId),
                mScheduleRepo.getActiveScheduleSync(taskId));
            refreshCaches();
            // 预热时段缓存（避免主线程回调中 refreshSlotView 触发同步 Room 查询）
            long todayMs = todayStartMs();
            getActivePeriodsForDate(todayMs);
            if (state.schedule != null && state.schedule.scheduleType == TaskScheduleEntity.TYPE_ONCE) {
                long onceDateMs = state.schedule.scheduleValue;
                if (onceDateMs != todayMs) {
                    getActivePeriodsForDate(onceDateMs);
                }
            }
            runOnUiThread(() -> {
                mExistingSchedule = state.schedule;
                callback.accept(state);
            });
        });
    }

    /** 确保 dateMs 对应时段已缓存，完成后在主线程回调 onReady。 */
    public void ensurePeriodsCached(long dateMs, Runnable onReady) {
        if (mPeriodsCache.containsKey(dateMs)) {
            onReady.run();
            return;
        }
        runInBackground(() -> {
            getActivePeriodsForDate(dateMs); // 填充缓存
            runOnUiThread(onReady);
        });
    }

    /** 获取指定日期生效的时段列表（非 break 时段）。 */
    public List<TimePeriodEntity> getActivePeriodsForDate(long dateMs) {
        List<TimePeriodEntity> cached = mPeriodsCache.get(dateMs);
        if (cached != null) return cached;
        ActivePeriodGroup group = mPeriodRepo.getActivePeriodGroupSync(dateMs);
        List<TimePeriodEntity> result = new ArrayList<>();
        if (group != null && group.periods != null) {
            for (TimePeriodEntity p : group.periods) {
                if (!p.preferChore) {
                    result.add(p);
                }
            }
        }
        mPeriodsCache.put(dateMs, result);
        return result;
    }

    /** 刷新 enabled schedules 缓存 + 关联 task 缓存。loadInitialState / insert / update 后调用。 */
    private void refreshCaches() {
        mEnabledSchedulesCache = mScheduleRepo.getAllEnabledSchedulesSync();
        mTaskCache = new java.util.HashMap<>();
        if (mEnabledSchedulesCache != null) {
            for (TaskScheduleEntity s : mEnabledSchedulesCache) {
                TaskEntity t = mTaskRepo.getTaskByIdSync(s.taskId);
                if (t != null) mTaskCache.put(t.id, t);
            }
        }
    }

    /** 获取已被占用的槽位分钟集合（展开为 10 分钟粒度范围）。调用方需确保 mTaskCache 已填充。 */
    public Set<Integer> getOccupiedSlots(long excludeScheduleId, long dateMs) {
        Set<Integer> occupied = new HashSet<>();
        List<TaskScheduleEntity> all = mEnabledSchedulesCache;
        if (all == null) return occupied;
        Map<Long, TaskEntity> taskCache = mTaskCache;
        for (TaskScheduleEntity s : all) {
            if (s.id == excludeScheduleId) continue;
            if (TaskScheduleMatcher.matchesDate(s, dateMs)) {
                TaskEntity task = taskCache != null ? taskCache.get(s.taskId) : null;
                int duration = (task != null && task.focusMinutes > 0) ? task.focusMinutes : 0;
                for (int m = s.scheduledTime; m < s.scheduledTime + duration; m += 10) {
                    occupied.add(m);
                }
            }
        }
        return occupied;
    }

    /** 检查双权限。 */
    public boolean hasRequiredPermissions() {
        return PermissionHelper.hasExactAlarmPermission(mApp)
            && PermissionHelper.hasNotificationPermission(mApp);
    }

    /** 新建安排。 */
    public void insertSchedule(TaskScheduleEntity schedule, Runnable onComplete) {
        mScheduleRepo.insert(schedule, () -> {
            scheduleTaskAlarm(schedule);
            refreshCaches();
            if (onComplete != null) onComplete.run();
        });
    }

    /** 更新已有安排。 */
    public void updateSchedule(TaskScheduleEntity schedule, Runnable onComplete) {
        // 先取消旧闹钟
        if (mExistingSchedule != null) {
            new ReminderScheduler(mApp).cancel(mExistingSchedule.id, mExistingSchedule.scheduledTime);
        }
        mScheduleRepo.update(schedule, () -> {
            scheduleTaskAlarm(schedule);
            mExistingSchedule = schedule;
            refreshCaches();
            if (onComplete != null) onComplete.run();
        });
    }

    public boolean isEditMode() {
        return mExistingSchedule != null;
    }

    private void scheduleTaskAlarm(TaskScheduleEntity schedule) {
        TaskEntity task = mTaskRepo.getTaskByIdSync(schedule.taskId);
        if (!ReminderScheduler.shouldRegisterAlarm(task)) return;
        long triggerMs = ReminderScheduler.computeNextMatch(schedule, System.currentTimeMillis());
        new ReminderScheduler(mApp).schedule(schedule, task, triggerMs);
    }

    /** 工具方法：将日期时间戳转换为当天某分钟的毫秒时间戳。 */
    public static long dateAndTimeToMs(long dateMs, int minuteOfDay) {
        return dateMs + minuteOfDay * 60000L;
    }

    private static long todayStartMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
