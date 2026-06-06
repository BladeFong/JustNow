package com.nearby.justnow.ui.engine;

import android.content.res.Resources;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.scheduler.TaskScheduleMatcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

/**
 * 剩余时间计算器 — 独立模块，被多处复用
 */
public class TimeRemainingCalculator {

    /**
     * 结果：当前时段 + 剩余时间
     */
    public static class PeriodStatus {
        /** 当前命中的时段。 */
        public TimePeriodEntity period;
        /** 时段结束分钟数（一天中） */
        public int endMinute;
        /** 剩余分钟数（到时段结束） */
        public int remainingMinutes;
        /** 有效截止分钟（最近安排开始 or 时段结束） */
        public int effectiveEndMinute;
        /** 有效剩余分钟（考虑安排截断后） */
        public int effectiveRemaining;

        public boolean isInPeriod() {
            return period != null;
        }

        public boolean isReverseQuadrant() {
            return period != null && period.reverseQuadrant;
        }

        public boolean isPriorityEligible() {
            return period != null && period.priorityEligible;
        }

        public String getRemainingText(Resources res) {
            // 在安排任务范围内：显示原始时段剩余
            // 在安排任务之前：显示到安排任务的剩余时间
            int minutes = (effectiveRemaining > 0) ? effectiveRemaining : remainingMinutes;
            int h = minutes / 60;
            int m = minutes % 60;
            String hourUnit = res.getString(R.string.s_hour_unit);
            String minUnit = res.getString(R.string.s_minute_unit);
            String timeText;
            if (h > 0 && m > 0) timeText = h + hourUnit + m + minUnit;
            else if (h > 0) timeText = h + hourUnit;
            else timeText = minutes + minUnit;
            // 有安排任务且在安排之前：用安排任务格式
            if (effectiveRemaining > 0 && effectiveRemaining < remainingMinutes) {
                return String.format(res.getString(R.string.s_schedule_remaining_format), timeText);
            }
            return String.format(res.getString(R.string.s_remaining_format), timeText);
        }
    }

    /** 计算当前时段状态 */
    public static PeriodStatus compute(List<TimePeriodEntity> periods) {
        return compute(periods, Calendar.getInstance());
    }

    /** 计算指定时间的时段状态（供测试注入固定时间） */
    static PeriodStatus compute(List<TimePeriodEntity> periods, Calendar cal) {
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        PeriodStatus status = new PeriodStatus();

        for (TimePeriodEntity p : sortPeriods(periods)) {
            if (nowMinute >= p.startMinute && nowMinute < p.endMinute) {
                status.period = p;
                status.endMinute = p.endMinute;
                status.remainingMinutes = p.endMinute - nowMinute;
                status.effectiveEndMinute = p.endMinute;
                status.effectiveRemaining = p.endMinute - nowMinute;
                return status;
            }
        }

        return status; // 不在任何时段
    }

    /** 计算当前时段状态（含安排截断） */
    public static PeriodStatus compute(List<TimePeriodEntity> periods,
                                       List<TaskScheduleEntity> todaySchedules) {
        return compute(periods, todaySchedules, Calendar.getInstance());
    }

    /** 计算指定时间的时段状态（含安排截断，供测试注入固定时间） */
    static PeriodStatus compute(List<TimePeriodEntity> periods,
                                List<TaskScheduleEntity> todaySchedules,
                                Calendar cal) {
        PeriodStatus status = compute(periods, cal);
        if (status.isInPeriod()) {
            int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            applyScheduleTruncation(status, todaySchedules, nowMinute);
        }
        return status;
    }

    /**
     * 截断 effectiveRemaining：
     * 1. 当前在安排任务范围内 → effectiveRemaining = 0（不可开始新任务）
     * 2. 未来有安排任务 → effectiveRemaining = 到最近安排开始的分钟数
     * 3. 都不是 → 保持原值
     */
    private static void applyScheduleTruncation(PeriodStatus status,
                                                List<TaskScheduleEntity> todaySchedules,
                                                int nowMinute) {
        if (todaySchedules == null || todaySchedules.isEmpty()) return;

        int nearestStart = Integer.MAX_VALUE;
        for (TaskScheduleEntity s : todaySchedules) {
            if (!TaskScheduleMatcher.matchesToday(s)) continue;
            int startMinute = s.scheduledTime;
            // 判断当前是否在该安排的时间范围内
            if (s.focusMinutes > 0 && nowMinute >= startMinute
                    && nowMinute < startMinute + s.focusMinutes) {
                status.effectiveRemaining = 0;
                status.effectiveEndMinute = nowMinute;
                return;
            }
            if (startMinute > nowMinute && startMinute < nearestStart) {
                nearestStart = startMinute;
            }
        }

        if (nearestStart != Integer.MAX_VALUE) {
            status.effectiveRemaining = nearestStart - nowMinute;
            status.effectiveEndMinute = nearestStart;
        }
    }

    public static List<TimePeriodEntity> sortPeriods(List<TimePeriodEntity> periods) {
        List<TimePeriodEntity> sorted = new ArrayList<>();
        if (periods != null) sorted.addAll(periods);
        sorted.sort(Comparator
            .comparingInt((TimePeriodEntity p) -> p.startMinute)
            .thenComparingInt(p -> p.endMinute)
            .thenComparingInt(p -> p.sortOrder));
        return sorted;
    }

    /** 时段状态文本（buildStatusText 返回值） */
    public static class StatusText {
        public boolean isUpcoming;
        public boolean isTomorrow;
        public boolean showRestHint;
    }

    /** 构建非时段状态的提示文本标志 */
    public static StatusText buildStatusText(List<TimePeriodEntity> periods, PeriodStatus status) {
        StatusText result = new StatusText();
        result.isUpcoming = !status.isInPeriod();

        if (result.isUpcoming && periods != null && !periods.isEmpty()) {
            Calendar cal = Calendar.getInstance();
            int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60
                + cal.get(Calendar.MINUTE);
            int firstStart = periods.get(0).startMinute;
            int lastEnd = periods.get(periods.size() - 1).endMinute;

            result.showRestHint = (nowMinute < 6 * 60 || nowMinute < firstStart
                || nowMinute >= lastEnd);

            boolean hasNextToday = false;
            for (TimePeriodEntity p : periods) {
                if (p.startMinute > nowMinute) {
                    hasNextToday = true;
                    break;
                }
            }
            result.isTomorrow = !hasNextToday;
        }

        return result;
    }
}
