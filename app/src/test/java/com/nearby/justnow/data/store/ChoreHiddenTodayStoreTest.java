package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * ChoreHiddenTodayStore 单元测试 — Robolectric + SharedPreferences。
 *
 * <p>覆盖 < 15min 完成路径下用来"今日移出右侧栏"的隐藏集合：</p>
 * <ul>
 *   <li>同日 hideForToday → getHiddenTodayIds 命中</li>
 *   <li>跨日（手动写入旧日期）→ getHiddenTodayIds 视为空集</li>
 *   <li>重复 hide 同一 id 幂等</li>
 *   <li>多个 id 同日累积</li>
 * </ul>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ChoreHiddenTodayStoreTest {

    private static final String PREFS_NAME = "justnow_prefs";
    private static final String KEY_DATE = "hide_focus_today_date";
    private static final String KEY_IDS = "hide_focus_today_ids";
    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Context mContext;
    private ChoreHiddenTodayStore mStore;
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        // 每个测试用例独立清空 prefs（同一进程中 Robolectric 跨用例不会自动重置）
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
        mStore = new ChoreHiddenTodayStore(mContext);
    }

    @After
    public void tearDown() {
        if (mPrefs != null) {
            mPrefs.edit().clear().commit();
        }
    }

    // ---- 同日命中 ----

    @Test
    public void hideForToday_singleId_isHidden() {
        mStore.hideForToday(42L);

        Set<Long> ids = mStore.getHiddenTodayIds();
        assertEquals(1, ids.size());
        assertTrue(ids.contains(42L));
    }

    @Test
    public void hideForToday_multipleIds_allHidden() {
        mStore.hideForToday(1L);
        mStore.hideForToday(2L);
        mStore.hideForToday(3L);

        Set<Long> ids = mStore.getHiddenTodayIds();
        assertEquals(3, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
        assertTrue(ids.contains(3L));
    }

    // ---- 重复 add 幂等 ----

    @Test
    public void hideForToday_duplicateId_idempotent() {
        mStore.hideForToday(7L);
        mStore.hideForToday(7L);
        mStore.hideForToday(7L);

        Set<Long> ids = mStore.getHiddenTodayIds();
        assertEquals(1, ids.size());
        assertTrue(ids.contains(7L));
    }

    // ---- 跨日自然失效 ----

    @Test
    public void getHiddenTodayIds_staleDate_returnsEmpty() {
        // 手动写入"昨天"日期 + 一些 id：模拟跨日后调用
        String yesterday = LocalDate.now().minusDays(1).format(sDateFormat);
        mPrefs.edit()
            .putString(KEY_DATE, yesterday)
            .putString(KEY_IDS, "1,2,3")
            .commit();

        Set<Long> ids = mStore.getHiddenTodayIds();
        assertTrue("跨日应当视为空集", ids.isEmpty());
    }

    @Test
    public void hideForToday_afterStaleDate_overwritesToToday() {
        // 旧日期 + 旧 id
        String yesterday = LocalDate.now().minusDays(1).format(sDateFormat);
        mPrefs.edit()
            .putString(KEY_DATE, yesterday)
            .putString(KEY_IDS, "100,200")
            .commit();

        // 今天 hide 一个新 id：应覆盖日期且只保留今天的集合
        mStore.hideForToday(42L);

        Set<Long> ids = mStore.getHiddenTodayIds();
        assertEquals(1, ids.size());
        assertTrue(ids.contains(42L));
        assertFalse("旧日期的 id 不应残留", ids.contains(100L));
        assertFalse("旧日期的 id 不应残留", ids.contains(200L));
    }

    // ---- 边界 ----

    @Test
    public void getHiddenTodayIds_emptyPrefs_returnsEmpty() {
        Set<Long> ids = mStore.getHiddenTodayIds();
        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }

    @Test
    public void getHiddenTodayIds_emptyIdsField_returnsEmpty() {
        String today = LocalDate.now().format(sDateFormat);
        mPrefs.edit()
            .putString(KEY_DATE, today)
            .putString(KEY_IDS, "")
            .commit();

        Set<Long> ids = mStore.getHiddenTodayIds();
        assertTrue(ids.isEmpty());
    }

    @Test
    public void getHiddenTodayIds_corruptIdsField_skipsInvalidEntries() {
        String today = LocalDate.now().format(sDateFormat);
        mPrefs.edit()
            .putString(KEY_DATE, today)
            .putString(KEY_IDS, "5,abc,7,,9")
            .commit();

        Set<Long> ids = mStore.getHiddenTodayIds();
        // "abc" / "" 应被静默跳过，5/7/9 命中
        assertEquals(3, ids.size());
        assertTrue(ids.contains(5L));
        assertTrue(ids.contains(7L));
        assertTrue(ids.contains(9L));
    }

    @Test
    public void hideForToday_persistsAcrossNewStoreInstance() {
        mStore.hideForToday(11L);
        mStore.hideForToday(22L);

        // 同一 Context 再 new 一个 Store：应能读到之前写入的集合
        ChoreHiddenTodayStore another = new ChoreHiddenTodayStore(mContext);
        Set<Long> ids = another.getHiddenTodayIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains(11L));
        assertTrue(ids.contains(22L));
    }
}
