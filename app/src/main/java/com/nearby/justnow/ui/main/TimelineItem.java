package com.nearby.justnow.ui.main;

/**
 * 左侧时间线任务条。
 */
public class TimelineItem {

    public final long taskId;
    public final String title;
    public final int focusMinutes;
    public final long startMs;
    public final long endMs;
    public final boolean running;
    public final boolean hasRecurringSchedule;

    public TimelineItem(long taskId, String title, int focusMinutes, long startMs, long endMs,
                        boolean running, boolean hasRecurringSchedule) {
        this.taskId = taskId;
        this.title = title;
        this.focusMinutes = focusMinutes;
        this.startMs = startMs;
        this.endMs = endMs;
        this.running = running;
        this.hasRecurringSchedule = hasRecurringSchedule;
    }
}
