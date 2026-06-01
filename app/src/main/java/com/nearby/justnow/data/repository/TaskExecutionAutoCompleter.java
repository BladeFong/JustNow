package com.nearby.justnow.data.repository;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描执行中任务，补偿完成已经超过所属时段的执行记录。
 */
public final class TaskExecutionAutoCompleter {

    private TaskExecutionAutoCompleter() {
    }

    /**
     * 扫描并自动完成已超出时段的执行中任务。
     * @return 被自动完成的任务 ID 集合
     */
    public static Set<Long> completeExpiredRunningTasksSync(TaskRepository taskRepo,
                                                       TaskExecutionRepository executionRepo,
                                                       List<TaskEntity> tasks,
                                                       List<TimePeriodEntity> activePeriods,
                                                       List<TimePeriodEntity> fallbackPeriods) {
        Set<Long> completedIds = new HashSet<>();
        if (taskRepo == null || executionRepo == null
            || tasks == null || tasks.isEmpty()
            || ((activePeriods == null || activePeriods.isEmpty())
            && (fallbackPeriods == null || fallbackPeriods.isEmpty()))) {
            return completedIds;
        }

        long nowMs = System.currentTimeMillis();
        for (TaskEntity task : tasks) {
            if (task.executingStartMs <= 0 || task.executingEndMs != 0) continue;

            int executionStartMinute = minuteOfDay(task.executingStartMs);
            TimePeriodEntity executionPeriod = findPeriodByMinute(activePeriods, executionStartMinute);
            if (executionPeriod == null) {
                executionPeriod = findPeriodByMinute(fallbackPeriods, executionStartMinute);
            }
            if (executionPeriod == null) continue;

            long periodEndMs = getPeriodBoundaryMs(task.executingStartMs, executionPeriod.endMinute);
            if (nowMs >= periodEndMs) {
                completeRunningTaskSync(taskRepo, executionRepo, task, periodEndMs);
                completedIds.add(task.id);
            }
        }
        return completedIds;
    }

    /**
     * 完成执行中任务：写执行记录 + 清除执行状态。
     * 供 BroadcastReceiver / ViewModel 等非 UI 层直接调用。
     */
    public static void completeRunningTaskSync(TaskRepository taskRepo,
                                               TaskExecutionRepository executionRepo,
                                               TaskEntity task,
                                               long endMs) {
        if (task == null || task.executingStartMs <= 0) return;
        long safeEndMs = Math.max(endMs, task.executingStartMs + 1);
        int actualMinutes = Math.max(1, (int) ((safeEndMs - task.executingStartMs) / 60000));
        executionRepo.recordCompleteSync(task.id, task.executingStartMs, safeEndMs, actualMinutes);
        taskRepo.clearExecutionSync(task.id);

        // 降级恢复：有降级周期的任务完成后写降级记录
        if (task.degradePeriod > 0) {
            taskRepo.insertDegradeSync(task.id, task.quadrant,
                computeDegradeRecoverMs(task.degradePeriod));
        }
    }

    private static TimePeriodEntity findPeriodByMinute(List<TimePeriodEntity> periods, int minuteOfDay) {
        if (periods == null || periods.isEmpty()) return null;
        for (TimePeriodEntity period : periods) {
            if (minuteOfDay >= period.startMinute && minuteOfDay < period.endMinute) {
                return period;
            }
        }
        return null;
    }

    private static int minuteOfDay(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private static long getPeriodBoundaryMs(long baseMs, int minuteOfDay) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(baseMs);
        cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        cal.set(Calendar.MINUTE, minuteOfDay % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 计算降级恢复时间戳：次日/下周一/下月1日 00:00:00.000 */
    private static long computeDegradeRecoverMs(int degradePeriod) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        switch (degradePeriod) {
            case 1: // 次日
                cal.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case 2: // 下周一
                cal.add(Calendar.WEEK_OF_YEAR, 1);
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                break;
            case 3: // 下月1日
                cal.add(Calendar.MONTH, 1);
                cal.set(Calendar.DAY_OF_MONTH, 1);
                break;
            default:
                cal.add(Calendar.DAY_OF_MONTH, 1);
                break;
        }
        return cal.getTimeInMillis();
    }
}
