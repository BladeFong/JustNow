package com.nearby.justnow.ui.engine;

import android.content.res.Resources;

import com.nearby.justnow.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 专注时长档位生成和展示。
 */
public final class FocusDurationOptions {

    public static final int FOCUS_SLOT_MINUTES = 30;
    public static final int CHORE_MINUTES = 0;

    private FocusDurationOptions() {}

    public static boolean isAllowedFocusMaxMinutes(int focusMaxMinutes) {
        return focusMaxMinutes == DisplayPolicy.FOCUS_MAX_120
                || focusMaxMinutes == DisplayPolicy.FOCUS_MAX_150;
    }

    public static List<Integer> buildOptions(DisplayPolicy policy) {
        int focusMaxMinutes = policy != null
                ? policy.getFocusMaxMinutes()
                : DisplayPolicy.defaultPolicy().getFocusMaxMinutes();
        return buildOptions(focusMaxMinutes);
    }

    public static List<Integer> buildOptions(int focusMaxMinutes) {
        List<Integer> options = new ArrayList<>();
        options.add(CHORE_MINUTES);
        for (int minutes = FOCUS_SLOT_MINUTES;
             minutes <= focusMaxMinutes;
             minutes += FOCUS_SLOT_MINUTES) {
            options.add(minutes);
        }
        return options;
    }

    public static String format(Resources res, int focusMinutes) {
        if (focusMinutes <= 0) {
            return res.getString(R.string.s_chore_label);
        }
        return res.getString(R.string.s_focus_minutes_format, focusMinutes);
    }
}
