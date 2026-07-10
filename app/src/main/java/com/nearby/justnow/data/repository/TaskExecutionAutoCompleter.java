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

        // 完成计数器：日模式不写，周/月/年模式写入
        String periodKey = TaskRepository.computePeriodKey(task);
        taskRepo.incrementCompletionCounterSync(task.id, periodKey);
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

}
