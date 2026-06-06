package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * "本日已 &lt; 15min 完成的专注任务" 的隐藏集合。
 *
 * 由 &lt; 15min 完成对话框路径调用：因为该路径不写 task_executions，
 * {@code TimelineBuilder#hideCompletedChoresForToday} 不适用；
 * 改由本 Store 跨进程持久化（SharedPreferences），跨日自动重置。
 *
 * 数据形态：
 * <ul>
 *   <li>{@code key_date} = "yyyy-MM-dd"</li>
 *   <li>{@code key_ids}  = 逗号分隔的 taskId 字符串</li>
 * </ul>
 *
 * 读取时若 {@code key_date} 与今天不匹配，视为空集（不写回，下次写入时自然覆盖）。
 */
public class ChoreHiddenTodayStore {

    private static final String KEY_DATE = "hide_focus_today_date";
    private static final String KEY_IDS = "hide_focus_today_ids";

    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final SharedPreferences mPrefs;

    public ChoreHiddenTodayStore(Context context) {
        mPrefs = context.getApplicationContext()
            .getSharedPreferences(PrefsConfig.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 标记本日"已隐藏"，并按今天日期写入。 */
    public void hideForToday(long taskId) {
        String today = LocalDate.now().format(sDateFormat);
        Set<Long> ids = readIdsForDate(today);
        ids.add(taskId);
        mPrefs.edit()
            .putString(KEY_DATE, today)
            .putString(KEY_IDS, joinIds(ids))
            .apply();
    }

    /** 返回今日已隐藏的 taskId 集合（跨日自动为空）。 */
    public Set<Long> getHiddenTodayIds() {
        String today = LocalDate.now().format(sDateFormat);
        return readIdsForDate(today);
    }

    private Set<Long> readIdsForDate(String today) {
        String storedDate = mPrefs.getString(KEY_DATE, null);
        if (storedDate == null || !storedDate.equals(today)) {
            return new HashSet<>();
        }
        String ids = mPrefs.getString(KEY_IDS, "");
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> result = new HashSet<>();
        for (String part : ids.split(",")) {
            if (part.isEmpty()) continue;
            try {
                result.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String joinIds(Set<Long> ids) {
        if (ids.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Long id : ids) {
            if (id == null) continue;
            if (!first) sb.append(',');
            sb.append(id);
            first = false;
        }
        return sb.toString();
    }
}
