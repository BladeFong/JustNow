package com.nearby.justnow.ui.tagmanage;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.store.PrefsConfig;
import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.ScheduleProfile;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.ui.engine.PriorityTagConfig;
import com.nearby.justnow.util.RegionSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签管理 ViewModel — 优先场景管理 + 优先标签读写
 */
public class TagManageViewModel extends BaseViewModel {

    private static final String KEY_SCHEDULE_PROFILE = "schedule_profile";

    private final TagRepository mTagRepo;
    private final PriorityTagConfig mPriorityTagConfig;
    private final SharedPreferences mPrefs;
    private final boolean mIsMainlandChina;

    /** 优先标签 ID 集合（从数据库 LiveData 派生） */
    private final MediatorLiveData<Set<Long>> mPriorityTagIds = new MediatorLiveData<>();
    private final MutableLiveData<Boolean> mWorkTimePriorityEnabled = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Boolean>> mGroupPriorityEnabled = new MutableLiveData<>();

    /** 优先标签数量缓存（主线程安全读取，避免同步DB查询） */
    private Map<String, Integer> mPriorityTagCountCache = new HashMap<>();
    /** 缓存刷新事件，通知 Fragment 刷新列表 */
    private final MutableLiveData<Boolean> mCacheRefreshEvent = new MutableLiveData<>();

    public TagManageViewModel(JustNowApplication app) {
        super(app);
        mTagRepo = app.getTagRepository();
        mPriorityTagConfig = new PriorityTagConfig(mApp, mTagRepo);
        mPrefs = com.nearby.justnow.data.store.UserPrefs.getPrefs(
            mApp, mApp.getCurrentUserId(), PrefsConfig.PREFS_NAME);
        mIsMainlandChina = RegionSettings.isMainlandChina(mApp);

        mPriorityTagIds.addSource(mTagRepo.getAllPriorityRulesLive(), rules -> {
            Set<Long> ids = new HashSet<>();
            if (rules != null) {
                for (PriorityTagRuleEntity rule : rules) ids.add(rule.tagId);
            }
            mPriorityTagIds.setValue(ids);
        });

        refreshGroupPriorityEnabled();
    }

    /** 同步获取全部标签（供对话框使用，调用方负责在线程池中执行） */
    public List<TagEntity> getAllTagsSync() {
        List<TagEntity> tags = mTagRepo.getAllTagsSync();
        return tags != null ? tags : new ArrayList<>();
    }

    /** 获取指定时间段组的优先标签实体列表（调用方负责在线程池中执行） */
    public List<TagEntity> getPriorityTagsFromRepo(String groupType) {
        Set<Long> priorityIds = mPriorityTagConfig.getPriorityTagIds(groupType);
        return mTagRepo.getTagsByIdsSync(priorityIds);
    }

    public LiveData<Set<Long>> getPriorityTagIdsLiveData() {
        return mPriorityTagIds;
    }

    public LiveData<Boolean> getWorkTimePriorityEnabledLiveData() {
        return mWorkTimePriorityEnabled;
    }

    public LiveData<Map<String, Boolean>> getGroupPriorityEnabledLiveData() {
        return mGroupPriorityEnabled;
    }

    /** 获取优先标签数量（缓存读取，主线程安全） */
    public int getPriorityTagCount(String groupType) {
        Integer count = mPriorityTagCountCache.get(groupType);
        return count != null ? count : 0;
    }

    public boolean isPriorityEnabled(String groupType) {
        return mPriorityTagConfig.isPriorityEnabled(groupType);
    }

    public void setPriorityEnabled(String groupType, boolean enabled) {
        mPriorityTagConfig.setPriorityEnabled(groupType, enabled);
        refreshGroupPriorityEnabled();
        refreshPriorityTagCounts();
    }

    /** 批量设置指定时间段组的优先标签 */
    public void setTagsPriority(String groupType, Set<Long> tagIds) {
        mPriorityTagConfig.setPriorityTags(groupType, tagIds);
        refreshPriorityTagCounts();
    }

    public Set<Long> getPriorityTagIds(String groupType) {
        return mPriorityTagConfig.getPriorityTagIds(groupType);
    }

    public List<String> getVisiblePriorityGroupTypes() {
        String profile = mPrefs.getString(KEY_SCHEDULE_PROFILE, ScheduleProfile.GENERAL);
        String normalized = ScheduleProfile.normalizeProfile(profile, mIsMainlandChina);
        List<String> visibleTypes = ScheduleProfile.getVisiblePeriodGroupTypes(normalized, mIsMainlandChina);

        // 只返回已启用的组
        List<String> enabledTypes = new ArrayList<>();
        com.nearby.justnow.data.entity.TimePeriodGroupEntity group;
        for (String groupType : visibleTypes) {
            if (PeriodGroupType.isRegular(groupType)) continue;
            group = mDb.timePeriodDao().getGroupSync(groupType);
            if (group != null && group.enabled) {
                enabledTypes.add(groupType);
            }
        }
        return enabledTypes;
    }

    /** 缓存刷新事件，Fragment 观察后刷新 Adapter */
    public LiveData<Boolean> getCacheRefreshEvent() {
        return mCacheRefreshEvent;
    }

    /** 刷新指定组类型的优先标签计数缓存（异步） */
    public void refreshPriorityTagCounts(List<String> groupTypes) {
        runInBackground(() -> {
            Map<String, Integer> cache = new HashMap<>();
            for (String groupType : groupTypes) {
                cache.put(groupType, mPriorityTagConfig.getPriorityTagIds(groupType).size());
            }
            mPriorityTagCountCache = cache;
            mCacheRefreshEvent.postValue(true);
        });
    }

    /** 刷新所有可见组的优先标签计数缓存（异步，供 setTagsPriority / setPriorityEnabled 内部调用） */
    private void refreshPriorityTagCounts() {
        runInBackground(() -> {
            List<String> types = getVisiblePriorityGroupTypes();
            Map<String, Integer> cache = new HashMap<>();
            for (String groupType : types) {
                cache.put(groupType, mPriorityTagConfig.getPriorityTagIds(groupType).size());
            }
            mPriorityTagCountCache = cache;
            mCacheRefreshEvent.postValue(true);
        });
    }

    private void refreshGroupPriorityEnabled() {
        Map<String, Boolean> state = new HashMap<>();
        for (String groupType : PeriodGroupType.getOrderedTypes()) {
            if (!PeriodGroupType.isRegular(groupType)) {
                state.put(groupType, mPriorityTagConfig.isPriorityEnabled(groupType));
            }
        }
        mGroupPriorityEnabled.setValue(Collections.unmodifiableMap(state));
        mWorkTimePriorityEnabled.setValue(mPriorityTagConfig.isWorkTimePriorityEnabled());
    }

    /** 后台加载可见且已启用的优先组类型列表，回调到主线程（供 Fragment 使用）。 */
    public void loadVisibleEnabledGroupTypesAsync(java.util.function.Consumer<List<String>> callback) {
        runInBackground(() -> {
            List<String> types = getVisiblePriorityGroupTypes();
            runOnUiThread(() -> callback.accept(types));
        });
    }

    /** 后台加载全部标签和指定组的优先标签 ID，回调到主线程（供 Fragment 标签编辑对话框使用）。 */
    public void loadTagsForGroupEditAsync(String groupType,
                                           java.util.function.BiConsumer<List<TagEntity>, Set<Long>> callback) {
        runInBackground(() -> {
            List<TagEntity> allTags = getAllTagsSync();
            Set<Long> priorityIds = getPriorityTagIds(groupType);
            runOnUiThread(() -> callback.accept(allTags, priorityIds));
        });
    }

    /** 后台加载指定组的优先标签实体列表，回调到主线程（供 Fragment 刷新 Chip 使用）。 */
    public void loadPriorityTagsForGroupAsync(String groupType,
                                               java.util.function.Consumer<List<TagEntity>> callback) {
        runInBackground(() -> {
            List<TagEntity> tags = getPriorityTagsFromRepo(groupType);
            runOnUiThread(() -> callback.accept(tags));
        });
    }
}
