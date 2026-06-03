package com.nearby.justnow.util;

import java.util.Calendar;

/**
 * 日��工具类。
 */
public final class DateUtils {

    private DateUtils() {}

    /**
     * 获取当天 00:00:00.000 的毫秒时间戳（基于系统默认时区）。
     */
    public static long todayStartMs() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 将"一天中的分钟数"格式化为 HH:mm（如 570 → "09:30"）。
     */
    public static String formatMinute(int minuteOfDay) {
        return String.format(java.util.Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }
}
