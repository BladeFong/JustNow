package com.nearby.justnow.data.model;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleProfileTest {

    @Test
    public void getOrderedProfiles_mainlandChina_containsSecurities() {
        List<String> profiles = ScheduleProfile.getOrderedProfiles(true);

        assertTrue(profiles.contains(ScheduleProfile.SECURITIES));
    }

    @Test
    public void getOrderedProfiles_nonMainlandChina_excludesSecurities() {
        List<String> profiles = ScheduleProfile.getOrderedProfiles(false);

        assertFalse(profiles.contains(ScheduleProfile.SECURITIES));
    }

    @Test
    public void normalizeProfile_nonMainlandChinaSecurities_fallsBackToGeneral() {
        String profile = ScheduleProfile.normalizeProfile(ScheduleProfile.SECURITIES, false);

        assertEquals(ScheduleProfile.GENERAL, profile);
    }

    @Test
    public void getVisiblePeriodGroupTypes_mainlandChina_usesSpringFestival() {
        List<String> groupTypes = ScheduleProfile.getVisiblePeriodGroupTypes(
            ScheduleProfile.GENERAL, true);

        assertTrue(groupTypes.contains(PeriodGroupType.SPRING_FESTIVAL));
        assertFalse(groupTypes.contains(PeriodGroupType.LONG_VACATION));
    }

    @Test
    public void getVisiblePeriodGroupTypes_nonMainlandChina_usesLongVacation() {
        List<String> groupTypes = ScheduleProfile.getVisiblePeriodGroupTypes(
            ScheduleProfile.GENERAL, false);

        assertTrue(groupTypes.contains(PeriodGroupType.LONG_VACATION));
        assertFalse(groupTypes.contains(PeriodGroupType.SPRING_FESTIVAL));
    }

    @Test
    public void getVisiblePeriodGroupTypes_school_excludesHolidayGroups() {
        List<String> groupTypes = ScheduleProfile.getVisiblePeriodGroupTypes(
            ScheduleProfile.SCHOOL, true);

        assertFalse(groupTypes.contains(PeriodGroupType.SPRING_FESTIVAL));
        assertFalse(groupTypes.contains(PeriodGroupType.LONG_VACATION));
        assertTrue(groupTypes.contains(PeriodGroupType.SUMMER_VACATION));
        assertTrue(groupTypes.contains(PeriodGroupType.WINTER_VACATION));
    }
}
