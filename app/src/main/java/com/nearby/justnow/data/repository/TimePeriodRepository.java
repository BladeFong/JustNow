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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间段配置仓库
 */
public class TimePeriodRepository extends BaseRepository {

    private final TimePeriodDao mDao;
    private final PeriodGroupRuleResolver mRuleResolver;

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
        return mDao.getAllPeriodsSync();
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
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        String groupType = mRuleResolver != null
            ? mRuleResolver.resolveActiveGroupType(groups, Calendar.getInstance(), scheduleProfile)
            : PeriodGroupType.REGULAR;
        return buildActiveGroup(groupType);
    }

    /** 获取当前日期参与显示的同名时段最大集合，供左侧时间线坐标使用。 */
    public List<TimePeriodEntity> getTimelinePeriodsSync(String scheduleProfile) {
        List<TimePeriodGroupEntity> groups = mDao.getAllGroupsSync();
        Map<String, TimePeriodEntity> mergedPeriods = new LinkedHashMap<>();
        Calendar cal = Calendar.getInstance();

        if (groups == null) return new ArrayList<>();
        for (TimePeriodGroupEntity group : groups) {
            if (!shouldUseGroupInTimeline(group, cal, scheduleProfile)) continue;
            mergeTimelinePeriods(mergedPeriods, mDao.getPeriodsByGroupSync(group.groupType));
        }

        return new ArrayList<>(mergedPeriods.values());
    }

    private boolean shouldUseGroupInTimeline(TimePeriodGroupEntity group, Calendar cal,
                                             String scheduleProfile) {
        if (mRuleResolver == null) return group != null && PeriodGroupType.isRegular(group.groupType);
        return mRuleResolver.participatesInTimelineSync(group, cal, scheduleProfile);
    }

    private void mergeTimelinePeriods(Map<String, TimePeriodEntity> mergedPeriods,
                                      List<TimePeriodEntity> periods) {
        if (periods == null) return;
        for (TimePeriodEntity period : periods) {
            if (period == null || period.nameKey == null || period.nameKey.isEmpty()) continue;
            TimePeriodEntity merged = mergedPeriods.get(period.nameKey);
            if (merged == null) {
                mergedPeriods.put(period.nameKey, copyTimelinePeriod(period));
            } else {
                merged.startMinute = Math.min(merged.startMinute, period.startMinute);
                merged.endMinute = Math.max(merged.endMinute, period.endMinute);
            }
        }
    }

    private TimePeriodEntity copyTimelinePeriod(TimePeriodEntity source) {
        TimePeriodEntity copy = new TimePeriodEntity();
        copy.id = source.id;
        copy.groupType = source.groupType;
        copy.nameKey = source.nameKey;
        copy.sortOrder = source.sortOrder;
        copy.startMinute = source.startMinute;
        copy.endMinute = source.endMinute;
        copy.reverseQuadrant = source.reverseQuadrant;
        copy.preferChore = source.preferChore;
        copy.priorityEligible = source.priorityEligible;
        return copy;
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
        mDb.runInBackground(() -> mDao.update(period));
    }

    public void updateGroup(TimePeriodGroupEntity group) {
        mDb.runInBackground(() -> mDao.updateGroup(group));
    }

}
