package com.nearby.justnow.data.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 作息类型只决定默认口径和默认开关，不决定可用时间段组类型。
 */
public final class ScheduleProfile {

    public static final String GENERAL = "general";
    public static final String SCHOOL = "school";
    public static final String SECURITIES = "securities";

    private static final List<String> sMainlandProfiles = Collections.unmodifiableList(Arrays.asList(
        GENERAL,
        SCHOOL,
        SECURITIES
    ));

    private static final List<String> sGlobalProfiles = Collections.unmodifiableList(Arrays.asList(
        GENERAL,
        SCHOOL
    ));

    private ScheduleProfile() {
    }

    public static List<String> getOrderedProfiles() {
        return sMainlandProfiles;
    }

    public static List<String> getOrderedProfiles(boolean isMainlandChina) {
        return isMainlandChina ? sMainlandProfiles : sGlobalProfiles;
    }

    public static String normalizeProfile(String profile, boolean isMainlandChina) {
        if (getOrderedProfiles(isMainlandChina).contains(profile)) return profile;
        return GENERAL;
    }

    /**
     * 根据地区返回可见的时间段组类型。
     * 中国大陆：春节；非中国大陆：长假。
     */
    public static List<String> getVisiblePeriodGroupTypes(String profile, boolean isMainlandChina) {
        if (SCHOOL.equals(profile)) {
            return Arrays.asList(
                PeriodGroupType.WORKDAY,
                PeriodGroupType.SUMMER_VACATION,
                PeriodGroupType.WINTER_VACATION
            );
        }
        return Arrays.asList(
            PeriodGroupType.WORKDAY,
            isMainlandChina ? PeriodGroupType.SPRING_FESTIVAL : PeriodGroupType.LONG_VACATION
        );
    }
}
