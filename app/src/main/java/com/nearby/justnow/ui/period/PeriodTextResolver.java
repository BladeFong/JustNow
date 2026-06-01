package com.nearby.justnow.ui.period;

import android.content.res.Resources;

import androidx.annotation.StringRes;

import com.nearby.justnow.R;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;
import com.nearby.justnow.data.model.ScheduleProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 时间段相关 key 到字符串资源的统一映射。
 */
public final class PeriodTextResolver {

    private static final Map<String, Integer> sGroupNameResIds = createGroupNameResIds();
    private static final Map<String, Integer> sPeriodNameResIds = createPeriodNameResIds();
    private static final Map<String, Integer> sProfileNameResIds = createProfileNameResIds();
    private static final Map<String, Integer> sProfileDescResIds = createProfileDescResIds();

    private PeriodTextResolver() {
    }

    public static String getGroupName(Resources res, String groupType) {
        return getString(res, sGroupNameResIds.get(groupType));
    }

    public static String getPeriodName(Resources res, String nameKey) {
        return getString(res, sPeriodNameResIds.get(nameKey));
    }

    public static String getProfileName(Resources res, String profile) {
        return getString(res, sProfileNameResIds.get(profile));
    }

    public static String getProfileDescription(Resources res, String profile) {
        return getString(res, sProfileDescResIds.get(profile));
    }

    private static String getString(Resources res, @StringRes Integer resId) {
        return resId != null ? res.getString(resId) : "";
    }

    private static Map<String, Integer> createGroupNameResIds() {
        Map<String, Integer> map = new HashMap<>();
        map.put(PeriodGroupType.REGULAR, R.string.s_period_group_regular);
        map.put(PeriodGroupType.WORKDAY, R.string.s_period_group_workday);
        map.put(PeriodGroupType.SPRING_FESTIVAL, R.string.s_period_group_spring_festival);
        map.put(PeriodGroupType.LONG_VACATION, R.string.s_period_group_long_vacation);
        map.put(PeriodGroupType.SUMMER_VACATION, R.string.s_period_group_summer_vacation);
        map.put(PeriodGroupType.WINTER_VACATION, R.string.s_period_group_winter_vacation);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Integer> createPeriodNameResIds() {
        Map<String, Integer> map = new HashMap<>();
        map.put(PeriodNameKey.MORNING, R.string.s_period_morning);
        map.put(PeriodNameKey.NOON, R.string.s_period_noon);
        map.put(PeriodNameKey.AFTERNOON, R.string.s_period_afternoon);
        map.put(PeriodNameKey.DINNER, R.string.s_period_dinner);
        map.put(PeriodNameKey.EVENING, R.string.s_period_evening);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Integer> createProfileNameResIds() {
        Map<String, Integer> map = new HashMap<>();
        map.put(ScheduleProfile.GENERAL, R.string.s_schedule_profile_general);
        map.put(ScheduleProfile.SCHOOL, R.string.s_schedule_profile_school);
        map.put(ScheduleProfile.SECURITIES, R.string.s_schedule_profile_securities);
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Integer> createProfileDescResIds() {
        Map<String, Integer> map = new HashMap<>();
        map.put(ScheduleProfile.GENERAL, R.string.s_schedule_profile_general_desc);
        map.put(ScheduleProfile.SCHOOL, R.string.s_schedule_profile_school_desc);
        map.put(ScheduleProfile.SECURITIES, R.string.s_schedule_profile_securities_desc);
        return Collections.unmodifiableMap(map);
    }
}
