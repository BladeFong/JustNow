package com.nearby.justnow.scheduler;

import android.content.Context;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.ui.main.TaskStartResult;

import java.util.List;

/**
 * 任务开始校验的无 UI 依赖入口，可在 BroadcastReceiver 等无 LiveData 上下文复用。
 *
 * <p>校验逻辑与 {@code MainViewModel.evaluateTaskStartSync} 保持等价：
 * 任务存在/未归档、无其他执行中任务、当前在有效时段、剩余时段+15min 容差能放下 focusMinutes。
 * 必须在后台线程调用（内部走同步 DAO）。
 */
public final class TaskStartGuard {

    private TaskStartGuard() {}

    /** 评估任务此刻是否可以开始。 */
    public static TaskStartResult evaluate(Context context, long taskId) {
        JustNowApplication app = (JustNowApplication) context.getApplicationContext();
        TaskRepository taskRepo = app.getTaskRepository();
        TaskEntity task = taskRepo.getTaskByIdSync(taskId);
        if (task == null || task.isArchived) {
            return new TaskStartResult(TaskStartResult.BLOCKED_TASK_MISSING);
        }

        TaskEntity runningTask = taskRepo.getRunningTaskSync();
        if (runningTask != null) {
            return new TaskStartResult(TaskStartResult.BLOCKED_RUNNING);
        }

        TimePeriodRepository periodRepo = app.getTimePeriodRepository();
        ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
        if (activeGroup == null || activeGroup.periods == null) {
            return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
        }
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);
        TimeRemainingCalculator.PeriodStatus status = TimeRemainingCalculator.compute(periods);
        if (!status.isInPeriod() && task.focusMinutes > 0) {
            return new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD);
        }

        if (task.focusMinutes > 0 && status.remainingMinutes + 15 < task.focusMinutes) {
            return new TaskStartResult(TaskStartResult.BLOCKED_TIME_NOT_ENOUGH);
        }

        return new TaskStartResult(TaskStartResult.OK);
    }
}
