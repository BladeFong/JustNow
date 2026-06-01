package com.nearby.justnow.data.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 固定预设时间段组类型。
 */
public final class PeriodGroupType {

    public static final String REGULAR = "regular";
    public static final String WORKDAY = "workday";
    public static final String SPRING_FESTIVAL = "spring_festival";
    public static final String LONG_VACATION = "long_vacation";
    public static final String SUMMER_VACATION = "summer_vacation";
    public static final String WINTER_VACATION = "winter_vacation";

    private static final List<String> sOrderedTypes = Arrays.asList(
        REGULAR,
        WORKDAY,
        SPRING_FESTIVAL,
        LONG_VACATION,
        SUMMER_VACATION,
        WINTER_VACATION
    );

    private static final List<String> sPriorityTypes = Arrays.asList(
        SUMMER_VACATION,
        WINTER_VACATION,
        SPRING_FESTIVAL,
        LONG_VACATION,
        WORKDAY,
        REGULAR
    );

    private static final Set<String> sVacationTypes = new HashSet<>(Arrays.asList(
        SUMMER_VACATION,
        WINTER_VACATION,
        LONG_VACATION
    ));

    private static final Set<String> sHolidayTypes = new HashSet<>(Arrays.asList(
        SPRING_FESTIVAL
    ));

    private PeriodGroupType() {
    }

    public static List<String> getOrderedTypes() {
        return sOrderedTypes;
    }

    public static List<String> getPriorityTypes() {
        return sPriorityTypes;
    }

    public static boolean isRegular(String groupType) {
        return REGULAR.equals(groupType);
    }

    public static boolean isVacation(String groupType) {
        return sVacationTypes.contains(groupType);
    }

    public static boolean isHoliday(String groupType) {
        return sHolidayTypes.contains(groupType);
    }

    public static int getDisplayOrder(String groupType) {
        int index = sOrderedTypes.indexOf(groupType);
        return index >= 0 ? index : sOrderedTypes.size();
    }
}
