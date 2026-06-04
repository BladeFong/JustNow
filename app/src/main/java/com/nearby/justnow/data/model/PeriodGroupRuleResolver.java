package com.nearby.justnow.data.model;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.holiday.HolidayCacheManager;
import com.nearby.justnow.data.holiday.IcsParser;
import com.nearby.justnow.util.RegionSettings;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间段组命中规则。节假日数据驱动，无数据时回退本地规则。
 */
public class PeriodGroupRuleResolver {

    public enum WorkdayPolicy {
        STANDARD_WEEK,
        LEGAL_HOLIDAY
    }

    /** 工作日时段组的周模式。 */
    public enum WorkdayMode {
        STANDARD_5,  // 周一~五
        SIX_DAY       // 周一~六
    }

    private static final String PREFS_NAME = "justnow_prefs";
    private static final String KEY_SCHEDULE_PROFILE = "schedule_profile";
    private static final String KEY_WORKDAY_POLICY = "workday_policy";
    private static final String POLICY_STANDARD_WEEK = "standard_week";
    private static final String POLICY_LEGAL_HOLIDAY = "legal_holiday";
    private static final String KEY_WORKDAY_MODE = "workday_mode";
    private static final String MODE_STANDARD_5 = "standard_5";
    private static final String MODE_SIX_DAY = "six_day";

    private final SharedPreferences mPrefs;
    private final boolean mIsMainlandChina;
    private final HolidayCacheManager mHolidayCacheManager;

    public PeriodGroupRuleResolver(Context context) {
        this(context, new HolidayCacheManager(
            AppDatabase.getInstance(context).holidayCacheDao()));
    }

    /** 测试专用构造函数，允许注入 HolidayCacheManager 以使用内存数据库。 */
    public PeriodGroupRuleResolver(Context context, HolidayCacheManager cacheManager) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mIsMainlandChina = RegionSettings.isMainlandChina(context);
        mHolidayCacheManager = cacheManager;
    }

    public WorkdayPolicy getWorkdayPolicy() {
        String value = mPrefs.getString(KEY_WORKDAY_POLICY, POLICY_STANDARD_WEEK);
        if (POLICY_LEGAL_HOLIDAY.equals(value)) return WorkdayPolicy.LEGAL_HOLIDAY;
        return WorkdayPolicy.STANDARD_WEEK;
    }

    public void setWorkdayPolicy(WorkdayPolicy policy) {
        String value = policy == WorkdayPolicy.LEGAL_HOLIDAY
            ? POLICY_LEGAL_HOLIDAY : POLICY_STANDARD_WEEK;
        mPrefs.edit().putString(KEY_WORKDAY_POLICY, value).apply();
    }

    public WorkdayMode getWorkdayMode() {
        String value = mPrefs.getString(KEY_WORKDAY_MODE, MODE_STANDARD_5);
        if (MODE_SIX_DAY.equals(value)) return WorkdayMode.SIX_DAY;
        return WorkdayMode.STANDARD_5;
    }

    public void setWorkdayMode(WorkdayMode mode) {
        String value = mode == WorkdayMode.SIX_DAY ? MODE_SIX_DAY : MODE_STANDARD_5;
        mPrefs.edit().putString(KEY_WORKDAY_MODE, value).apply();
    }

    public String resolveActiveGroupType(List<TimePeriodGroupEntity> groups, Calendar cal) {
        return resolveActiveGroupType(groups, cal, getScheduleProfile());
    }

    /** 同步解析当前生效的时间段组（调用方传入已读取的 scheduleProfile 避免重复读 SP）。 */
    public String resolveActiveGroupType(List<TimePeriodGroupEntity> groups, Calendar cal,
                                          String scheduleProfile) {
        if (groups == null || groups.isEmpty()) return PeriodGroupType.REGULAR;

        Map<String, TimePeriodGroupEntity> groupMap = new HashMap<>();
        for (TimePeriodGroupEntity group : groups) {
            groupMap.put(group.groupType, group);
        }

        for (String groupType : PeriodGroupType.getPriorityTypes()) {
            TimePeriodGroupEntity group = groupMap.get(groupType);
            if (matchesSync(group, cal, scheduleProfile)) return groupType;
        }

        return PeriodGroupType.REGULAR;
    }

    /** 判断某时间段组是否参与当前日期的时间线显示范围（自动读取 scheduleProfile）。 */
    public boolean participatesInTimelineSync(TimePeriodGroupEntity group, Calendar cal) {
        return participatesInTimelineSync(group, cal, getScheduleProfile());
    }

    /** 判断某时间段组是否参与当前日期的时间线显示范围。 */
    public boolean participatesInTimelineSync(TimePeriodGroupEntity group, Calendar cal,
                                               String scheduleProfile) {
        if (group == null) return false;
        if (PeriodGroupType.isRegular(group.groupType)) return true;
        if (!ScheduleProfile.getVisiblePeriodGroupTypes(scheduleProfile, mIsMainlandChina)
            .contains(group.groupType)) {
            return false;
        }
        if (!group.enabled) return false;

        if (PeriodGroupType.isVacation(group.groupType)) {
            return matchesMonthDayRange(group.startMonthDay, group.endMonthDay, cal);
        }

        if (PeriodGroupType.isHoliday(group.groupType)) {
            if (hasCustomRange(group)) {
                return matchesMonthDayRange(group.startMonthDay, group.endMonthDay, cal);
            }
            return group.useHolidayData && matchesHolidayDataSync(group.groupType, cal);
        }

        if (PeriodGroupType.WORKDAY.equals(group.groupType)) {
            return isWorkdaySync(cal, scheduleProfile);
        }

        return false;
    }

    /**
     * 异步解析当前生效的时间段组，回调返回 groupType。
     * 用于 UI 层，避免主线程 Room 查询。
     */
    public void resolveActiveGroupTypeAsync(List<TimePeriodGroupEntity> groups, Calendar cal,
                                             java.util.function.Consumer<String> callback) {
        if (groups == null || groups.isEmpty()) {
            callback.accept(PeriodGroupType.REGULAR);
            return;
        }

        Map<String, TimePeriodGroupEntity> groupMap = new HashMap<>();
        for (TimePeriodGroupEntity group : groups) {
            groupMap.put(group.groupType, group);
        }

        // 按优先级遍历
        resolveAsyncRecursive(groupMap, PeriodGroupType.getPriorityTypes(), 0, cal, callback);
    }

    private void resolveAsyncRecursive(Map<String, TimePeriodGroupEntity> groupMap,
                                        List<String> priorityTypes, int index,
                                        Calendar cal, java.util.function.Consumer<String> callback) {
        if (index >= priorityTypes.size()) {
            callback.accept(PeriodGroupType.REGULAR);
            return;
        }

        String groupType = priorityTypes.get(index);
        TimePeriodGroupEntity group = groupMap.get(groupType);

        // 同步可判断的快速路径
        if (group == null) {
            resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
            return;
        }
        if (PeriodGroupType.isRegular(groupType)) {
            callback.accept(groupType);
            return;
        }
        if (!ScheduleProfile.getVisiblePeriodGroupTypes(getScheduleProfile(), mIsMainlandChina)
            .contains(groupType)) {
            resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
            return;
        }
        if (!group.enabled) {
            resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
            return;
        }

        if (PeriodGroupType.isVacation(groupType)) {
            boolean matched = matchesMonthDayRange(group.startMonthDay, group.endMonthDay, cal);
            if (matched) callback.accept(groupType);
            else resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
            return;
        }

        if (PeriodGroupType.isHoliday(groupType)) {
            if (hasCustomRange(group)) {
                boolean matched = matchesMonthDayRange(group.startMonthDay, group.endMonthDay, cal);
                if (matched) callback.accept(groupType);
                else resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
                return;
            }
            if (group.useHolidayData) {
                matchesHolidayData(groupType, cal, matched -> {
                    if (matched) callback.accept(groupType);
                    else resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
                });
                return;
            }
            resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
            return;
        }

        if (PeriodGroupType.WORKDAY.equals(groupType)) {
            isWorkdayAsync(cal, isWorkday -> {
                if (isWorkday) callback.accept(groupType);
                else resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
            });
            return;
        }

        resolveAsyncRecursive(groupMap, priorityTypes, index + 1, cal, callback);
    }

    private boolean matchesSync(TimePeriodGroupEntity group, Calendar cal) {
        return matchesSync(group, cal, getScheduleProfile());
    }

    /**
     * 同步判断时间段组是否命中。
     */
    private boolean matchesSync(TimePeriodGroupEntity group, Calendar cal, String scheduleProfile) {
        if (group == null) return false;
        if (PeriodGroupType.isRegular(group.groupType)) return true;
        if (!ScheduleProfile.getVisiblePeriodGroupTypes(scheduleProfile, mIsMainlandChina)
            .contains(group.groupType)) {
            return false;
        }
        if (!group.enabled) return false;

        if (PeriodGroupType.isVacation(group.groupType)) {
            return matchesMonthDayRange(group.startMonthDay, group.endMonthDay, cal);
        }

        if (PeriodGroupType.isHoliday(group.groupType)) {
            if (hasCustomRange(group)) {
                return matchesMonthDayRange(group.startMonthDay, group.endMonthDay, cal);
            }
            return group.useHolidayData && matchesHolidayDataSync(group.groupType, cal);
        }

        if (PeriodGroupType.WORKDAY.equals(group.groupType)) {
            return isWorkdaySync(cal, scheduleProfile);
        }

        return false;
    }

    private String getScheduleProfile() {
        String profile = mPrefs.getString(KEY_SCHEDULE_PROFILE, ScheduleProfile.GENERAL);
        return ScheduleProfile.normalizeProfile(profile, mIsMainlandChina);
    }

    private boolean isWorkday(Calendar cal) {
        int day = cal.get(Calendar.DAY_OF_WEEK);
        if (WorkdayMode.SIX_DAY == getWorkdayMode()) {
            return day != Calendar.SUNDAY;  // 周六算工作日
        }
        return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
    }

    public boolean isWorkdaySync(Calendar cal) {
        return isWorkdaySync(cal, getScheduleProfile());
    }

    public boolean isWorkdaySync(Calendar cal, String scheduleProfile) {
        if (getWorkdayPolicy() == WorkdayPolicy.STANDARD_WEEK) {
            return isWorkday(cal);
        }
        int year = cal.get(Calendar.YEAR);
        String cachedJson = mHolidayCacheManager.getSync(year);
        if (cachedJson == null) {
            return isWorkday(cal);
        }
        LocalDate date = LocalDate.of(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH));
        Boolean offDay = IcsParser.isOffDay(cachedJson, date);
        if (offDay != null) {
            if (offDay) return false;
            if (!ScheduleProfile.SECURITIES.equals(scheduleProfile)) {
                return true;
            }
        }
        return isWorkday(cal);
    }

    private void isWorkdayAsync(Calendar cal, java.util.function.Consumer<Boolean> callback) {
        if (getWorkdayPolicy() == WorkdayPolicy.STANDARD_WEEK) {
            callback.accept(isWorkday(cal));
            return;
        }

        int year = cal.get(Calendar.YEAR);
        mHolidayCacheManager.get(year, cachedJson -> {
            if (cachedJson == null) {
                callback.accept(isWorkday(cal));
                return;
            }
            LocalDate date = LocalDate.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
            Boolean offDay = IcsParser.isOffDay(cachedJson, date);
            if (offDay != null) {
                if (offDay) {
                    callback.accept(false);  // 法定假日 → 不是工作日
                    return;
                }
                // isOffDay=false → 补班日
                if (!ScheduleProfile.SECURITIES.equals(getScheduleProfile())) {
                    callback.accept(true);  // 常规认补班 → 是工作日
                    return;
                }
                // 证券从业不跟补班 → fall through to Mon-Fri 兜底
            }
            callback.accept(isWorkday(cal));  // 未命中或证券从业忽略补班 → Mon-Fri 兜底
        });
    }

    /**
     * 从 holiday_cache 表中读取节假日数据判断是否命中（异步）。
     */
    private void matchesHolidayData(String groupType, Calendar cal,
                                     java.util.function.Consumer<Boolean> callback) {
        int year = cal.get(Calendar.YEAR);
        mHolidayCacheManager.get(year, cachedJson -> {
            if (cachedJson == null) {
                callback.accept(false);
                return;
            }

            if (PeriodGroupType.SPRING_FESTIVAL.equals(groupType)) {
                LocalDate[] range = IcsParser.getFestivalRange(cachedJson, "spring_festival");
                if (range == null) {
                    callback.accept(false);
                    return;
                }
                LocalDate today = LocalDate.of(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
                callback.accept(!today.isBefore(range[0]) && !today.isAfter(range[1]));
                return;
            }

            callback.accept(false);
        });
    }

    private boolean matchesHolidayDataSync(String groupType, Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        String cachedJson = mHolidayCacheManager.getSync(year);
        if (cachedJson == null) return false;

        if (PeriodGroupType.SPRING_FESTIVAL.equals(groupType)) {
            LocalDate[] range = IcsParser.getFestivalRange(cachedJson, "spring_festival");
            if (range == null) return false;
            LocalDate today = LocalDate.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
            return !today.isBefore(range[0]) && !today.isAfter(range[1]);
        }

        return false;
    }

    private static boolean hasCustomRange(TimePeriodGroupEntity group) {
        return group.startMonthDay != null && group.endMonthDay != null
            && !group.startMonthDay.isEmpty() && !group.endMonthDay.isEmpty();
    }

    private static boolean matchesMonthDayRange(String startMonthDay, String endMonthDay, Calendar cal) {
        if (startMonthDay == null || endMonthDay == null) return false;
        int start = parseMonthDay(startMonthDay);
        int end = parseMonthDay(endMonthDay);
        if (start <= 0 || end <= 0) return false;

        int current = (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
        if (start <= end) {
            return current >= start && current <= end;
        }
        return current >= start || current <= end;
    }

    /**
     * 判断时段组在未来3个月内能否命中，用于安排页过滤已过期时段组。
     */
    public boolean canMatchInNextThreeMonths(TimePeriodGroupEntity group) {
        return canMatchInNextThreeMonths(group, Calendar.getInstance());
    }

    /**
     * 判断时段组在未来3个月内能否命中（测试可注入 today Calendar 以模拟跨年）。
     */
    boolean canMatchInNextThreeMonths(TimePeriodGroupEntity group, Calendar today) {
        if (group == null) return false;
        if (PeriodGroupType.isRegular(group.groupType)) return true;
        if (PeriodGroupType.WORKDAY.equals(group.groupType)) return true;

        Calendar threeMonthsLater = (Calendar) today.clone();
        threeMonthsLater.add(Calendar.MONTH, 3);

        int windowStart = (today.get(Calendar.MONTH) + 1) * 100 + today.get(Calendar.DAY_OF_MONTH);
        int windowEnd = (threeMonthsLater.get(Calendar.MONTH) + 1) * 100
            + threeMonthsLater.get(Calendar.DAY_OF_MONTH);

        if (PeriodGroupType.isVacation(group.groupType)) {
            return vacationCanMatch(windowStart, windowEnd, group);
        }

        if (PeriodGroupType.isHoliday(group.groupType)) {
            if (hasCustomRange(group)) {
                return vacationCanMatch(windowStart, windowEnd, group);
            }
            if (PeriodGroupType.SPRING_FESTIVAL.equals(group.groupType) && group.useHolidayData) {
                return springFestivalCanMatch(windowStart, windowEnd, today);
            }
        }

        return false;
    }

    private boolean vacationCanMatch(int windowStart, int windowEnd, TimePeriodGroupEntity group) {
        int groupStart = parseMonthDay(group.startMonthDay);
        int groupEnd = parseMonthDay(group.endMonthDay);
        if (groupStart <= 0 || groupEnd <= 0) return false;
        if (windowStart > windowEnd) {
            // 窗口跨年：假期组任一端点落入窗口即命中；假期组自身跨年也命中
            return isInMonthDayRange(windowStart, windowEnd, groupStart)
                || isInMonthDayRange(windowStart, windowEnd, groupEnd)
                || groupStart > groupEnd;
        }
        return windowStart <= groupEnd && groupStart <= windowEnd;
    }

    /** 判断单个 MM-dd 值是否在区间内，正确处理跨年区间。 */
    private static boolean isInMonthDayRange(int rangeStart, int rangeEnd, int value) {
        if (rangeStart <= rangeEnd) {
            return value >= rangeStart && value <= rangeEnd;
        }
        return value >= rangeStart || value <= rangeEnd;
    }

    private boolean springFestivalCanMatch(int windowStart, int windowEnd, Calendar today) {
        int thisYear = today.get(Calendar.YEAR);
        if (checkSpringFestivalYear(thisYear, windowStart, windowEnd)) return true;
        return checkSpringFestivalYear(thisYear + 1, windowStart, windowEnd);
    }

    private boolean checkSpringFestivalYear(int year, int windowStart, int windowEnd) {
        String json = mHolidayCacheManager.getSync(year);
        if (json == null) return false;
        LocalDate[] range = IcsParser.getFestivalRange(json, "spring_festival");
        if (range == null) return false;
        int startMMDD = range[0].getMonthValue() * 100 + range[0].getDayOfMonth();
        return isInMonthDayRange(windowStart, windowEnd, startMMDD);
    }

    private static int parseMonthDay(String value) {
        String[] parts = value.split("-");
        if (parts.length != 2) return -1;
        try {
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            if (month < 1 || month > 12 || day < 1 || day > 31) return -1;
            return month * 100 + day;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
