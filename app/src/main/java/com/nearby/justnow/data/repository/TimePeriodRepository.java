package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;

import com.nearby.justnow.data.dao.TimePeriodDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodGroupWithPeriods;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 时间段配置仓库
 */
public class TimePeriodRepository extends BaseRepository {

    private final TimePeriodDao mDao;
    private final PeriodGroupRuleResolver mRuleResolver;

    // 内存缓存 —— 所有消费者共享，减少 Room 同步查询次数
    // 使用 CopyOnWriteArrayList 保证并发读写安全（volatile 只保证引用可见性，不保护集合内部状态）
    private volatile ActivePeriodGroup mCachedGroup;
    private volatile String mCachedProfile;
    private volatile CopyOnWriteArrayList<TimePeriodEntity> mCachedTimelinePeriods;
    private volatile String mCachedTimelineProfile;
    private volatile CopyOnWriteArrayList<TimePeriodEntity> mCachedAllPeriods;

    public TimePeriodRepository(AppDatabase db) {
        this(db, null);
    }

    public TimePeriodRepository(AppDatabase db, PeriodGroupRuleResolver ruleResolver) {
        super(db);
        this.mDao = db.timePeriodDao();
        this.mRuleResolver = ruleResolver;
    }

    public LiveData<List<TimePeriodGroupEntity>> getAllGroups() {
        return mDao.getAllGroups();
    }

    public LiveData<List<TimePeriodEntity>> getPeriodsByGroup(String groupType) {
        return mDao.getPeriodsByGroup(groupType);
    }

    /** 同步获取某时间段组的时段列表。 */
    public List<TimePeriodEntity> getPeriodsByGroupSync(String groupType) {
        return mDao.getPeriodsByGroupSync(groupType);
    }

    public LiveData<List<TimePeriodEntity>> getAllPeriods() {
        return mDao.getAllPeriods();
    }

    public List<TimePeriodEntity> getAllPeriodsSync() {
        if (mCachedAllPeriods != null) {
            return new ArrayList<>(mCachedAllPeriods);
        }
        List<TimePeriodEntity> result = mDao.getAllPeriodsSync();
        mCachedAllPeriods = new CopyOnWriteArrayList<>(result);
        return new ArrayList<>(result);
    }

    public List<PeriodGroupWithPeriods> getAllGroupDetailsSync() {
        List<PeriodGroupWithPeriods> result = new ArrayList<>();
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        if (groups == null) return result;
        for (TimePeriodGroupEntity group : groups) {
            result.add(new PeriodGroupWithPeriods(
                group,
                mDao.getPeriodsByGroupSync(group.groupType)
            ));
        }
        return result;
    }

    /** 获取所有时段组（含 disabled，供 getOccupiedSlots 查找 linkedPeriodGroupType）。 */
    public List<TimePeriodGroupEntity> getAllPeriodGroupsSync() {
        List<TimePeriodGroupEntity> result = mDao.getAllGroupsSync();
        return result != null ? result : new ArrayList<>();
    }

    /** 获取当前开启的时段组列表（排除 REGULAR，安排页用）。 */
    public List<TimePeriodGroupEntity> getEnabledPeriodGroupsSync() {
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        if (groups == null) return new ArrayList<>();
        List<TimePeriodGroupEntity> result = new ArrayList<>();
        for (TimePeriodGroupEntity group : groups) {
            if (!group.enabled) continue;
            if (PeriodGroupType.isRegular(group.groupType)) continue;
            result.add(group);
        }
        return result;
    }

    public ActivePeriodGroup getActivePeriodGroupSync() {
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        String groupType = mRuleResolver != null
            ? mRuleResolver.resolveActiveGroupType(groups, Calendar.getInstance())
            : PeriodGroupType.REGULAR;
        return buildActiveGroup(groupType);
    }

    /** 获取指定日期的生效时段组。 */
    public ActivePeriodGroup getActivePeriodGroupSync(long dateMs) {
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateMs);
        String groupType = mRuleResolver != null
            ? mRuleResolver.resolveActiveGroupType(groups, cal)
            : PeriodGroupType.REGULAR;
        return buildActiveGroup(groupType);
    }

    /** 调用方传入已读取的 scheduleProfile，避免 Resolver 重复读 SharedPreferences。 */
    public ActivePeriodGroup getActivePeriodGroupSync(String scheduleProfile) {
        if (mCachedGroup != null && Objects.equals(scheduleProfile, mCachedProfile)) {
            return mCachedGroup;
        }
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        String groupType = mRuleResolver != null
            ? mRuleResolver.resolveActiveGroupType(groups, Calendar.getInstance(), scheduleProfile)
            : PeriodGroupType.REGULAR;
        ActivePeriodGroup result = buildActiveGroup(groupType);
        mCachedGroup = result;
        mCachedProfile = scheduleProfile;
        return result;
    }

    /** 获取当前生效时段组的时段列表，供左侧时间线坐标使用。 */
    public List<TimePeriodEntity> getTimelinePeriodsSync(String scheduleProfile) {
        if (mCachedTimelinePeriods != null && Objects.equals(scheduleProfile, mCachedTimelineProfile)) {
            return mCachedTimelinePeriods;
        }
        ActivePeriodGroup activeGroup = getActivePeriodGroupSync(scheduleProfile);
        List<TimePeriodEntity> periods = activeGroup.periods;
        CopyOnWriteArrayList<TimePeriodEntity> result = periods != null
            ? new CopyOnWriteArrayList<>(periods)
            : new CopyOnWriteArrayList<>();
        mCachedTimelinePeriods = result;
        mCachedTimelineProfile = scheduleProfile;
        return result;
    }

    private ActivePeriodGroup buildActiveGroup(String groupType) {
        TimePeriodGroupEntity group = mDao.getGroupSync(groupType);
        if (group == null) {
            group = mDao.getGroupSync(PeriodGroupType.REGULAR);
            groupType = PeriodGroupType.REGULAR;
        }
        return new ActivePeriodGroup(group, mDao.getPeriodsByGroupSync(groupType));
    }

    public void update(TimePeriodEntity period) {
        // 先写 DB，完成后再清缓存，避免清缓存后、写 DB 前的窗口期内其他线程
        // 从 DB 读旧数据回填缓存，导致缓存长期持有过期数据
        mDb.runInBackground(() -> {
            mDao.update(period);
            clearCache();
        });
    }

    public void updateGroup(TimePeriodGroupEntity group) {
        mDb.runInBackground(() -> {
            mDao.updateGroup(group);
            clearCache();
        });
    }

    private void clearCache() {
        mCachedGroup = null;
        mCachedProfile = null;
        mCachedTimelinePeriods = null;
        mCachedTimelineProfile = null;
        mCachedAllPeriods = null;
    }

}
