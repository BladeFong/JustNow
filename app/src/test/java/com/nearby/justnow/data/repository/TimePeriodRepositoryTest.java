package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TimePeriodRepository 测试 —— 缓存行为与异步写后清缓存验证。
 * 验证 mCachedTimelinePeriods / mCachedAllPeriods (CopyOnWriteArrayList) 的缓存正确性，
 * 以及 update()/updateGroup() 在 runInBackground 回调末尾清缓存的顺序修正。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TimePeriodRepositoryTest {

    private AppDatabase mDb;
    private TimePeriodRepository mRepo;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepo = new TimePeriodRepository(mDb);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- getAllPeriodsSync 缓存 ----

    @Test
    public void getAllPeriodsSync_firstCallFromDb_empty() {
        List<TimePeriodEntity> result = mRepo.getAllPeriodsSync();
        assertTrue("无数据时首次调用应返回空", result.isEmpty());
    }

    @Test
    public void getAllPeriodsSync_firstCallReturnsDbData() {
        insertRegularPeriod("morning", 0, 480);

        // 新 Repo 实例（无缓存）
        TimePeriodRepository freshRepo = new TimePeriodRepository(mDb);
        List<TimePeriodEntity> result = freshRepo.getAllPeriodsSync();
        assertEquals("应从 DB 加载数据", 1, result.size());
    }

    @Test
    public void getAllPeriodsSync_cacheHit_doesNotRequery() {
        insertRegularPeriod("morning", 0, 480);
        mRepo.getAllPeriodsSync(); // 预热缓存

        // 直接往 DB 插入（绕过缓存更新）
        insertPeriodDirect("regular", "noon", 480, 600);

        // 缓存命中 → 应仅返回预热时的数据
        List<TimePeriodEntity> result = mRepo.getAllPeriodsSync();
        assertEquals("缓存命中应仅含预热时的 1 条", 1, result.size());
    }

    @Test
    public void getAllPeriodsSync_returnsDefensiveCopy() {
        insertRegularPeriod("morning", 0, 480);

        List<TimePeriodEntity> result1 = mRepo.getAllPeriodsSync();
        assertEquals(1, result1.size());
        result1.clear();

        List<TimePeriodEntity> result2 = mRepo.getAllPeriodsSync();
        assertEquals("防御性拷贝：修改返回列表不应影响缓存", 1, result2.size());
    }

    // ---- getActivePeriodGroupSync 缓存 ----

    @Test
    public void getActivePeriodGroupSync_returnsActiveGroup() {
        insertGroupAndPeriod();

        ActivePeriodGroup group = mRepo.getActivePeriodGroupSync("default");
        assertNotNull("应返回当前生效的时段组", group);
        assertEquals(PeriodGroupType.REGULAR, group.getGroupType());
        assertEquals(1, group.periods.size());
    }

    @Test
    public void getActivePeriodGroupSync_cacheHit() {
        insertGroupAndPeriod();

        ActivePeriodGroup first = mRepo.getActivePeriodGroupSync("default");
        ActivePeriodGroup second = mRepo.getActivePeriodGroupSync("default");
        assertSame("相同 profile 应命中缓存", first, second);
    }

    @Test
    public void getActivePeriodGroupSync_differentProfile_invalidatesCache() {
        insertGroupAndPeriod();

        ActivePeriodGroup first = mRepo.getActivePeriodGroupSync("profile1");
        ActivePeriodGroup second = mRepo.getActivePeriodGroupSync("profile2");
        assertNotSame("不同 profile 应重建缓存", first, second);
    }

    // ---- getTimelinePeriodsSync 缓存 ----

    @Test
    public void getTimelinePeriodsSync_withProfile_cachesResult() {
        insertGroupAndPeriod();

        List<TimePeriodEntity> first = mRepo.getTimelinePeriodsSync("default");
        List<TimePeriodEntity> second = mRepo.getTimelinePeriodsSync("default");
        assertSame("相同 profile 应命中缓存", first, second);
    }

    @Test
    public void getTimelinePeriodsSync_differentProfile_returnsNewResult() {
        insertGroupAndPeriod();

        List<TimePeriodEntity> first = mRepo.getTimelinePeriodsSync("p1");
        List<TimePeriodEntity> second = mRepo.getTimelinePeriodsSync("p2");
        assertNotSame("不同 profile 应返回不同对象", first, second);
    }

    // ---- update() 清缓存 ----

    @Test
    public void update_clearsAllCaches() throws InterruptedException {
        insertGroupAndPeriod();
        mRepo.getAllPeriodsSync();       // 预热 allPeriods 缓存
        mRepo.getActivePeriodGroupSync("default");  // 预热 group 缓存
        mRepo.getTimelinePeriodsSync("default");    // 预热 timeline 缓存

        // 获取已插入的 period 并修改
        List<TimePeriodEntity> periods = mDb.timePeriodDao().getAllPeriodsSync();
        assertEquals(1, periods.size());
        TimePeriodEntity toUpdate = periods.get(0);
        toUpdate.startMinute = 999;

        // update 走 runInBackground，在 DB 写后清缓存
        mRepo.update(toUpdate);

        // 等待 executor 完成
        Thread.sleep(300);

        // 缓存已清空 → 下一次 getAllPeriodsSync 会重新从 DB 加载
        List<TimePeriodEntity> reloaded = mRepo.getAllPeriodsSync();
        assertEquals("重新加载应从 DB 获取更新后的数据", 999, reloaded.get(0).startMinute);
    }

    // ---- updateGroup() 清缓存 ----

    @Test
    public void updateGroup_clearsAllCaches() throws InterruptedException {
        insertGroupAndPeriod();
        mRepo.getAllPeriodsSync();
        mRepo.getActivePeriodGroupSync("default");
        mRepo.getTimelinePeriodsSync("default");

        // 修改 group 并更新
        List<TimePeriodGroupEntity> groups = mDb.timePeriodDao().getAllGroupsSync();
        TimePeriodGroupEntity toUpdate = groups.get(0);
        toUpdate.displayOrder = 99;

        mRepo.updateGroup(toUpdate);

        Thread.sleep(300);

        // 缓存已清空 → getActivePeriodGroupSync 返回重建数据
        ActivePeriodGroup reloaded = mRepo.getActivePeriodGroupSync("default");
        assertEquals("重新加载 group 应获取更新后的 displayOrder", 99, reloaded.group.displayOrder);
    }

    // ---- 辅助方法 ----

    private void insertGroupAndPeriod() {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = PeriodGroupType.REGULAR;
        group.enabled = true;
        group.displayOrder = 0;
        mDb.timePeriodDao().insertGroups(Arrays.asList(group));

        insertPeriodDirect(PeriodGroupType.REGULAR, PeriodNameKey.MORNING, 0, 480);
    }

    private void insertRegularPeriod(String nameKey, int startMinute, int endMinute) {
        insertPeriodDirect(PeriodGroupType.REGULAR, nameKey, startMinute, endMinute);
    }

    private void insertPeriodDirect(String groupType, String nameKey, int startMinute, int endMinute) {
        TimePeriodEntity period = new TimePeriodEntity();
        period.groupType = groupType;
        period.nameKey = nameKey;
        period.startMinute = startMinute;
        period.endMinute = endMinute;
        mDb.timePeriodDao().insertPeriods(Arrays.asList(period));
    }
}
