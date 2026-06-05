package com.nearby.justnow.ui.taskschedule;

import androidx.lifecycle.ViewModel;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.scheduler.ReminderScheduler;
import com.nearby.justnow.scheduler.TaskScheduleMatcher;
import com.nearby.justnow.util.DateUtils;
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
    private final PeriodGroupRuleResolver mRuleResolver;

    /** 编辑模式下的已有安排（null = 新建）。 */
    private TaskScheduleEntity mExistingSchedule;

    /** 缓存：所有 enabled 安排。loadInitialState 时填充，每次 save 后失效。 */
    private volatile List<TaskScheduleEntity> mEnabledSchedulesCache;
    /** 缓存：所有时段组实体，供 getOccupiedSlots 查找 linkedPeriodGroupType 用。refreshCaches 时填充。 */
    private volatile List<TimePeriodGroupEntity> mAllPeriodGroupsCache;
    /** 缓存：dateMs -> 活跃非琐碎时段。命中减少 PeriodGroupRuleResolver 重复计算。 */
    private final Map<Long, List<TimePeriodEntity>> mPeriodsCache = new HashMap<>();
    /** 缓存：schedule 关联的 task，供 getOccupiedSlots 展开范围用。与 mEnabledSchedulesCache 同生命周期刷新。 */
    private Map<Long, TaskEntity> mTaskCache;

    public static class InitialState {
        public final TaskEntity task;
        public final TaskScheduleEntity schedule;
        /** 开启的非 REGULAR 时段组列表，供 Fragment 渲染动态 RadioGroup。 */
        public final List<TimePeriodGroupEntity> enabledPeriodGroups;
        /** 工作日模式，决定周 chip 数量（5 天/6 天）。 */
        public final PeriodGroupRuleResolver.WorkdayMode workdayMode;

        public InitialState(TaskEntity task, TaskScheduleEntity schedule,
                            List<TimePeriodGroupEntity> enabledPeriodGroups,
                            PeriodGroupRuleResolver.WorkdayMode workdayMode) {
            this.task = task;
            this.schedule = schedule;
            this.enabledPeriodGroups = enabledPeriodGroups;
            this.workdayMode = workdayMode;
        }
    }

    public TaskScheduleViewModel(JustNowApplication app) {
        super(app);
        mTaskRepo = app.getTaskRepository();
        mScheduleRepo = app.getTaskScheduleRepository();
        mPeriodRepo = app.getTimePeriodRepository();
        mRuleResolver = app.getPeriodGroupRuleResolver();
    }

    public TaskEntity getTaskSync(long taskId) {
        return mTaskRepo.getTaskByIdSync(taskId);
    }

    public TaskScheduleEntity getActiveScheduleSync(long taskId) {
        return mScheduleRepo.getActiveScheduleSync(taskId);
    }

    public void loadInitialState(long taskId, java.util.function.Consumer<InitialState> callback) {
        runInBackground(() -> {
            TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
            TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
            refreshCaches();
            // 获取开启的时段组列表和工作日模式，供 Fragment 渲染动态 RadioGroup
            List<TimePeriodGroupEntity> enabledGroups = buildEnabledPeriodGroups();
            PeriodGroupRuleResolver.WorkdayMode workdayMode = mRuleResolver.getWorkdayMode();
            InitialState state = new InitialState(task, schedule, enabledGroups, workdayMode);
            // 预热时段缓存（避免主线程回调中 refreshSlotView 触发同步 Room 查询）
            long todayMs = DateUtils.todayStartMs();
            getActivePeriodsForDate(todayMs);
            if (schedule != null && schedule.scheduleType == TaskScheduleEntity.TYPE_ONCE) {
                long onceDateMs = schedule.scheduleValue;
                if (onceDateMs != todayMs) {
                    getActivePeriodsForDate(onceDateMs);
                }
            }
            runOnUiThread(() -> {
                mExistingSchedule = schedule;
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

    /** 将 Runnable 提交到后台线程池（供 Fragment 异步查库用）。 */
    public void runOnBackgroundThread(Runnable action) {
        runInBackground(action);
    }

    /** 获取指定时段组的所有非琐碎时段（关联固定时段组时使用）。 */
    public List<TimePeriodEntity> getPeriodsByGroupSync(String groupType) {
        List<TimePeriodEntity> all = mPeriodRepo.getPeriodsByGroupSync(groupType);
        List<TimePeriodEntity> result = new ArrayList<>();
        if (all != null) {
            for (TimePeriodEntity p : all) {
                if (!p.preferChore) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    /** 获取指定日期命中的活跃时段组（顶层单次时使用）。 */
    public ActivePeriodGroup getActivePeriodGroupForDate(long dateMs) {
        return mPeriodRepo.getActivePeriodGroupSync(dateMs);
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

    /** 刷新 enabled schedules 缓存 + 关联 task 缓存 + 时段组缓存。loadInitialState / insert / update 后调用。 */
    private void refreshCaches() {
        mEnabledSchedulesCache = mScheduleRepo.getAllEnabledSchedulesSync();
        mAllPeriodGroupsCache = mPeriodRepo.getAllPeriodGroupsSync();
        mTaskCache = new java.util.HashMap<>();
        if (mEnabledSchedulesCache != null) {
            for (TaskScheduleEntity s : mEnabledSchedulesCache) {
                TaskEntity t = mTaskRepo.getTaskByIdSync(s.taskId);
                if (t != null) mTaskCache.put(t.id, t);
            }
        }
    }

    /** 从全部时段组缓存中过滤出开启的非 REGULAR 时段组，供 Fragment 渲染 RadioGroup 用。 */
    private List<TimePeriodGroupEntity> buildEnabledPeriodGroups() {
        List<TimePeriodGroupEntity> result = new ArrayList<>();
        List<TimePeriodGroupEntity> all = mAllPeriodGroupsCache;
        if (all != null) {
            for (TimePeriodGroupEntity g : all) {
                if (g.enabled && !PeriodGroupType.isRegular(g.groupType)
                    && mRuleResolver.canMatchInNextThreeMonths(g)) {
                    result.add(g);
                }
            }
        }
        return result;
    }

    /** 获取已被占用的槽位分钟集合（展开为 10 分钟粒度范围）。调用方需确保 mTaskCache 已填充。 */
    public Set<Integer> getOccupiedSlots(long excludeScheduleId, long dateMs) {
        Set<Integer> occupied = new HashSet<>();
        List<TaskScheduleEntity> all = mEnabledSchedulesCache;
        if (all == null) return occupied;
        Map<Long, TaskEntity> taskCache = mTaskCache;

        // 构建当天 Calendar，供 linkedPeriodGroupType 匹配判断用
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMs);

        List<TimePeriodGroupEntity> groups = mAllPeriodGroupsCache;

        for (TaskScheduleEntity s : all) {
            if (s.id == excludeScheduleId) continue;

            boolean matches;
            if (!s.linkedPeriodGroupType.isEmpty()) {
                // 关联了固定时段组：判断时段组当天是否生效
                TimePeriodGroupEntity linkedGroup = findGroupByType(groups, s.linkedPeriodGroupType);
                matches = linkedGroup != null
                    && mRuleResolver.participatesInTimelineSync(linkedGroup, cal);
            } else {
                // 历史数据/顶层单次：使用原有 matchesDate 逻辑
                matches = TaskScheduleMatcher.matchesDate(s, dateMs);
            }

            if (matches) {
                TaskEntity task = taskCache != null ? taskCache.get(s.taskId) : null;
                int duration = (task != null && task.focusMinutes > 0) ? task.focusMinutes : 0;
                for (int m = s.scheduledTime; m < s.scheduledTime + duration; m += 10) {
                    occupied.add(m);
                }
            }
        }
        return occupied;
    }

    /** 从时段组列表中按 groupType 查找实体。 */
    private static TimePeriodGroupEntity findGroupByType(List<TimePeriodGroupEntity> groups,
                                                         String groupType) {
        if (groups == null) return null;
        for (TimePeriodGroupEntity g : groups) {
            if (groupType.equals(g.groupType)) return g;
        }
        return null;
    }

    /** 检查双权限。 */
    public boolean hasRequiredPermissions() {
        return PermissionHelper.hasExactAlarmPermission(mApp)
            && PermissionHelper.hasNotificationPermission(mApp);
    }

    /** 新建安排。 */
    public void insertSchedule(TaskScheduleEntity schedule, Runnable onComplete) {
        mScheduleRepo.insert(schedule, () -> {
            if (onComplete != null) onComplete.run();
            scheduleTaskAlarm(schedule);
            refreshCaches();
        });
    }

    /** 更新已有安排。 */
    public void updateSchedule(TaskScheduleEntity schedule, Runnable onComplete) {
        // 先取消旧闹钟
        if (mExistingSchedule != null) {
            new ReminderScheduler(mApp).cancel(mExistingSchedule.id, mExistingSchedule.scheduledTime);
        }
        mScheduleRepo.update(schedule, () -> {
            if (onComplete != null) onComplete.run();
            scheduleTaskAlarm(schedule);
            mExistingSchedule = schedule;
            refreshCaches();
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

}
