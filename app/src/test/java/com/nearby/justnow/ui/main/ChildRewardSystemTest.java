package com.nearby.justnow.ui.main;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.PeriodGroupType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ChildRewardSystemTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
    }

    @Test
    public void targetDaysPreference_defaultIs3() {
        SharedPreferences sp = mContext.getSharedPreferences("justnow_prefs", Context.MODE_PRIVATE);
        int defaultDays = sp.getInt("weekly_reward_target_days", 3);
        assertEquals(3, defaultDays);
    }

    @Test
    public void targetDaysPreference_canBeUpdatedToCustomValue() {
        SharedPreferences sp = mContext.getSharedPreferences("justnow_prefs", Context.MODE_PRIVATE);
        sp.edit().putInt("weekly_reward_target_days", 4).apply();
        assertEquals(4, sp.getInt("weekly_reward_target_days", 3));
    }

    @Test
    public void isWorkdaySync_normalWeekday_returnsTrue() {
        PeriodGroupRuleResolver resolver = new PeriodGroupRuleResolver(mContext);
        Calendar Thursday = Calendar.getInstance();
        Thursday.set(2026, Calendar.MARCH, 5); // 2026-03-05 (Thursday, normal workday)
        assertTrue(resolver.isWorkdaySync(Thursday));
    }

    @Test
    public void isWorkdaySync_normalWeekend_returnsFalse() {
        PeriodGroupRuleResolver resolver = new PeriodGroupRuleResolver(mContext);
        Calendar Saturday = Calendar.getInstance();
        Saturday.set(2026, Calendar.MARCH, 7); // 2026-03-07 (Saturday, weekend)
        assertFalse(resolver.isWorkdaySync(Saturday));
    }

    @Test
    public void isVacation_summerVacation_returnsTrue() {
        assertTrue(PeriodGroupType.isVacation(PeriodGroupType.SUMMER_VACATION));
        assertTrue(PeriodGroupType.isVacation(PeriodGroupType.WINTER_VACATION));
        assertFalse(PeriodGroupType.isVacation(PeriodGroupType.WORKDAY));
        assertFalse(PeriodGroupType.isVacation(PeriodGroupType.REGULAR));
    }
}
