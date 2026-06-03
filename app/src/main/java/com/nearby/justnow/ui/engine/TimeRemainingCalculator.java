package com.nearby.justnow.ui.engine;

import android.content.res.Resources;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TimePeriodEntity;

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
        /** 剩余分钟数 */
        public int remainingMinutes;

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
            int h = remainingMinutes / 60;
            int m = remainingMinutes % 60;
            String hourUnit = res.getString(R.string.s_hour_unit);
            String minUnit = res.getString(R.string.s_minute_unit);
            String timeText;
            if (h > 0 && m > 0) timeText = h + hourUnit + m + minUnit;
            else if (h > 0) timeText = h + hourUnit;
            else timeText = remainingMinutes + minUnit;
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
                return status;
            }
        }

        return status; // 不在任何时段
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
