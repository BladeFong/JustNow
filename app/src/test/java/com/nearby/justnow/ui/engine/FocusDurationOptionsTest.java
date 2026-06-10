package com.nearby.justnow.ui.engine;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class FocusDurationOptionsTest {

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
}
