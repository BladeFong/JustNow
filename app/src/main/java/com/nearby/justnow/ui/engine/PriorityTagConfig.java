package com.nearby.justnow.ui.engine;

import android.content.Context;
import android.content.SharedPreferences;

import com.nearby.justnow.data.store.PrefsConfig;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.repository.TagRepository;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 优先标签配置 — 标签集合按时间段组持久化，开关保留在 SharedPreferences。
 */
public class PriorityTagConfig {

    private static final String KEY_GROUP_PRIORITY_ENABLED_PREFIX = "group_priority_enabled_";

    private final SharedPreferences mPrefs;
    private final TagRepository mTagRepo;

    public PriorityTagConfig(Context context, TagRepository tagRepo) {
        mPrefs = context.getSharedPreferences(PrefsConfig.PREFS_NAME, Context.MODE_PRIVATE);
        mTagRepo = tagRepo;
    }

    /** 获取指定时间段组的优先标签 ID（同步查库，可在后台线程调用）。 */
    public Set<Long> getPriorityTagIds(String groupType) {
        List<Long> ids = mTagRepo.getPriorityTagIdsForGroupSync(groupType);
        if (ids == null || ids.isEmpty()) return Collections.emptySet();
        return new LinkedHashSet<>(ids);
    }

    /** 兼容旧调用：工作日时间段组优先标签。 */
    public Set<Long> getPriorityTagIds() {
        return getPriorityTagIds(PeriodGroupType.WORKDAY);
    }

    public void setPriorityTags(String groupType, Set<Long> tagIds) {
        mTagRepo.setPriorityTagsForGroup(groupType, tagIds);
    }

    /** 兼容旧调用：添加到工作日时间段组。 */
    public void addPriorityTag(long tagId) {
        Set<Long> ids = new LinkedHashSet<>(getPriorityTagIds(PeriodGroupType.WORKDAY));
        ids.add(tagId);
        setPriorityTags(PeriodGroupType.WORKDAY, ids);
    }

    /** 兼容旧调用：从工作日时间段组移除。 */
    public void removePriorityTag(long tagId) {
        Set<Long> ids = new LinkedHashSet<>(getPriorityTagIds(PeriodGroupType.WORKDAY));
        ids.remove(tagId);
        setPriorityTags(PeriodGroupType.WORKDAY, ids);
    }

    /** 是否为工作日时间段组的优先标签。 */
    public boolean isPriorityTag(long tagId) {
        return getPriorityTagIds(PeriodGroupType.WORKDAY).contains(tagId);
    }

    public boolean isPriorityEnabled(String groupType) {
        return mPrefs.getBoolean(KEY_GROUP_PRIORITY_ENABLED_PREFIX + groupType, false);
    }

    public void setPriorityEnabled(String groupType, boolean enabled) {
        mPrefs.edit().putBoolean(KEY_GROUP_PRIORITY_ENABLED_PREFIX + groupType, enabled).apply();
    }

    /** 兼容旧调用：工作日时间段组开关。 */
    public boolean isWorkTimePriorityEnabled() {
        return isPriorityEnabled(PeriodGroupType.WORKDAY);
    }

    /** 兼容旧调用：工作日时间段组开关。 */
    public void setWorkTimePriorityEnabled(boolean enabled) {
        setPriorityEnabled(PeriodGroupType.WORKDAY, enabled);
    }

    /** 根据当前命中的时间段组和时段规则返回生效的优先标签 ID。 */
    public Set<Long> getEffectivePriorityTagIds(String activeGroupType, TimePeriodEntity period) {
        if (PeriodGroupType.isRegular(activeGroupType)) return Collections.emptySet();
        if (period == null || !period.priorityEligible) return Collections.emptySet();
        if (!isPriorityEnabled(activeGroupType)) return Collections.emptySet();
        return getPriorityTagIds(activeGroupType);
    }

}
