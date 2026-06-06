package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.util.DateUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * CutoffTimeStore 测试 — 截止时间按日期失效。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CutoffTimeStoreTest {

    private static final String KEY_CUTOFF_END_MINUTE = "cutoff_end_minute";
    private static final String KEY_CUTOFF_DATE_MS = "cutoff_date_ms";

    private Context mContext;
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PrefsConfig.PREFS_NAME, Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
    }

    @After
    public void tearDown() {
        if (mPrefs != null) {
            mPrefs.edit().clear().commit();
        }
    }

    @Test
    public void getCutoffEndMinute_sameDay_returnsValue() {
        CutoffTimeStore.setCutoffEndMinute(mContext, 18 * 60);

        assertEquals(18 * 60, CutoffTimeStore.getCutoffEndMinute(mContext));
        assertEquals(DateUtils.todayStartMs(), mPrefs.getLong(KEY_CUTOFF_DATE_MS, 0));
    }

    @Test
    public void getCutoffEndMinute_staleDate_clearsAndReturnsZero() {
        mPrefs.edit()
                .putInt(KEY_CUTOFF_END_MINUTE, 18 * 60)
                .putLong(KEY_CUTOFF_DATE_MS, DateUtils.todayStartMs() - 24 * 60 * 60 * 1000L)
                .commit();

        assertEquals(0, CutoffTimeStore.getCutoffEndMinute(mContext));
        assertFalse(mPrefs.contains(KEY_CUTOFF_END_MINUTE));
        assertFalse(mPrefs.contains(KEY_CUTOFF_DATE_MS));
    }
}
