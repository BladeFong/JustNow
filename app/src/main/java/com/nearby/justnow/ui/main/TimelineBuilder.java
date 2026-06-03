package com.nearby.justnow.ui.main;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import com.nearby.justnow.scheduler.TaskScheduleMatcher;
import com.nearby.justnow.util.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 时间线数据构建 — 从活跃任务、安排、执行记录组装 TimelineItem 列表。
 */
public class TimelineBuilder {

    private final TaskRepository mTaskRepo;
    private final TaskScheduleRepository mScheduleRepo;

    /** 输入签名缓存：避免时间线数据未变化时重复计算 */
    private Set<Long> mCachedTaskIds;
    private Set<Long> mCachedExecutionIds;
    private List<TimelineItem> mCachedResult;

    public TimelineBuilder(TaskRepository taskRepo, TaskScheduleRepository scheduleRepo) {
        mTaskRepo = taskRepo;
        mScheduleRepo = scheduleRepo;
    }

    public List<TimelineItem> build(List<TaskEntity> activeTasks,
                                     List<TaskExecutionEntity> executions) {
        // 输入签名：任务 ID + 执行 ID 未变化则直接返回缓存结果
        Set<Long> taskIds = new HashSet<>();
        if (activeTasks != null) {
            for (TaskEntity t : activeTasks) taskIds.add(t.id);
        }
        Set<Long> execIds = new HashSet<>();
        if (executions != null) {
            for (TaskExecutionEntity e : executions) execIds.add(e.id);
        }
        if (taskIds.equals(mCachedTaskIds) && execIds.equals(mCachedExecutionIds) && mCachedResult != null) {
            return mCachedResult;
        }

        Map<Long, TaskEntity> taskMap = new HashMap<>();
        if (activeTasks != null) {
            for (TaskEntity task : activeTasks) taskMap.put(task.id, task);
        }

        Set<Long> recurringTaskIds = new HashSet<>();
        List<TaskScheduleEntity> schedules = mScheduleRepo.getAllEnabledSchedulesSync();
        if (schedules != null) {
            for (TaskScheduleEntity schedule : schedules) {
                if (schedule.isRecurring()) recurringTaskIds.add(schedule.taskId);
            }
        }

        List<TimelineItem> items = new ArrayList<>();

        // 执行中的专注任务
        if (activeTasks != null) {
            for (TaskEntity task : activeTasks) {
                if (task.focusMinutes <= 0) continue;
                if (task.executingStartMs > 0 && task.executingEndMs == 0) {
                    items.add(new TimelineItem(task.id, task.content, task.focusMinutes,
                        task.executingStartMs, 0, true, recurringTaskIds.contains(task.id)));
                }
            }
        }

        // 收集需补查的 taskId
        Set<Long> missingIds = new HashSet<>();
        if (schedules != null) {
            for (TaskScheduleEntity schedule : schedules) {
                TaskEntity task = taskMap.get(schedule.taskId);
                if (task == null) missingIds.add(schedule.taskId);
            }
        }
        if (executions != null) {
            for (TaskExecutionEntity execution : executions) {
                if (execution.startMs <= 0 || execution.endMs <= execution.startMs) continue;
                TaskEntity task = taskMap.get(execution.taskId);
                if (task == null) missingIds.add(execution.taskId);
            }
        }
        if (!missingIds.isEmpty()) {
            List<TaskEntity> missingTasks = mTaskRepo.getTasksByIdsSync(new ArrayList<>(missingIds));
            if (missingTasks != null) {
                for (TaskEntity t : missingTasks) taskMap.put(t.id, t);
            }
        }

        // 已安排的专注任务（未在执行中）
        if (schedules != null) {
            long todayStartMs = DateUtils.todayStartMs();
            for (TaskScheduleEntity schedule : schedules) {
                // 仅今日命中的安排进入时间线，避免 ONCE 非今日 / WEEKLY/MONTHLY 不命中今日的项被错画
                if (!TaskScheduleMatcher.matchesToday(schedule)) continue;
                TaskEntity task = taskMap.get(schedule.taskId);
                if (task == null || task.focusMinutes <= 0 || task.executingStartMs > 0) continue;
                long startMs = todayStartMs + schedule.scheduledTime * 60000L;
                long endMs = startMs + task.focusMinutes * 60000L;
                items.add(new TimelineItem(task.id, task.content, task.focusMinutes,
                    startMs, endMs, false, recurringTaskIds.contains(task.id)));
            }
        }

        // 当天已完成记录
        if (executions != null) {
            for (TaskExecutionEntity execution : executions) {
                if (execution.startMs <= 0 || execution.endMs <= execution.startMs) continue;
                TaskEntity task = taskMap.get(execution.taskId);
                if (task == null || task.focusMinutes <= 0) continue;
                items.add(new TimelineItem(task.id, task.content, task.focusMinutes,
                    execution.startMs, execution.endMs, false, recurringTaskIds.contains(task.id)));
            }
        }

        items.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        mCachedTaskIds = taskIds;
        mCachedExecutionIds = execIds;
        mCachedResult = items;
        return items;
    }

    /** 当天已完成且非执行中的琐碎任务，从列表中移除。 */
    public static void hideCompletedChoresForToday(List<TaskEntity> tasks,
                                                    List<TaskExecutionEntity> executions) {
        if (tasks == null || tasks.isEmpty() || executions == null || executions.isEmpty()) {
            return;
        }
        Set<Long> completedTaskIds = new HashSet<>();
        for (TaskExecutionEntity execution : executions) {
            if (execution.status == 0 && execution.endMs > execution.startMs) {
                completedTaskIds.add(execution.taskId);
            }
        }
        if (completedTaskIds.isEmpty()) return;
        tasks.removeIf(task -> task.focusMinutes == 0
            && task.executingStartMs <= 0
            && completedTaskIds.contains(task.id));
    }

}
