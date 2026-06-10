package com.nearby.justnow.ui.engine;

import android.content.res.Resources;

import com.nearby.justnow.R;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FocusDurationOptionsTest {

    private Resources mResources;

    @Before
    public void setUp() {
        mResources = mock(Resources.class);
        when(mResources.getString(R.string.s_chore_label)).thenReturn("Chore");
        when(mResources.getString(R.string.s_focus_minutes_format, 30)).thenReturn("30 min");
        when(mResources.getString(R.string.s_hour_unit)).thenReturn("h");
    }

    @Test
    public void buildOptions_120_returnsFiveValues() {
        assertEquals(Arrays.asList(0, 30, 60, 90, 120),
            FocusDurationOptions.buildOptions(120));
    }

    @Test
    public void buildOptions_150_returnsSixValues() {
        assertEquals(Arrays.asList(0, 30, 60, 90, 120, 150),
            FocusDurationOptions.buildOptions(150));
    }

    @Test
    public void isAllowedFocusMaxMinutes_onlyAllows120And150() {
        assertTrue(FocusDurationOptions.isAllowedFocusMaxMinutes(120));
        assertTrue(FocusDurationOptions.isAllowedFocusMaxMinutes(150));
        assertFalse(FocusDurationOptions.isAllowedFocusMaxMinutes(180));
    }

    @Test
    public void format_choreAndThirtyMinutes_useOriginalLabels() {
        assertEquals(mResources.getString(R.string.s_chore_label),
            FocusDurationOptions.format(mResources, 0));
        assertEquals("30 min", FocusDurationOptions.format(mResources, 30));
    }

    @Test
    public void format_hourSlots_useHourLabels() {
        assertEquals("1h", FocusDurationOptions.format(mResources, 60));
        assertEquals("1.5h", FocusDurationOptions.format(mResources, 90));
        assertEquals("2h", FocusDurationOptions.format(mResources, 120));
        assertEquals("2.5h", FocusDurationOptions.format(mResources, 150));
    }
}
