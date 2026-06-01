package com.nearby.justnow.ui.quadrant;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.ScheduleProfile;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.ui.engine.DisplayEngine;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.PriorityTagConfig;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单象限全任务列表 ViewModel — 引擎排序 + 时长/标签筛选 + 多选删除。
 */
public class QuadrantTaskListViewModel extends BaseViewModel {

    private final TaskRepository mTaskRepo;
    private final TagRepository mTagRepo;
    private final TimePeriodRepository mPeriodRepo;
    private final DisplayEngine mDisplayEngine = new DisplayEngine();
    private final TimeRemainingCalculator mTimeCalc = new TimeRemainingCalculator();
    private final PriorityTagConfig mPriorityTagConfig;

    private int mQuadrant = -1;
    private List<DisplayItem> mAllItems = new ArrayList<>();
    private boolean mLoaded = false;

    /** 时长筛选 — 选中的 focusMinutes 值集合（空 = 不筛选） */
    private final Set<Integer> mFocusFilterMinutes = new HashSet<>();
    /** 选中标签 ID 集合（空 = 不过滤） */
    private final Set<Long> mSelectedTagIds = new HashSet<>();

    /** 多选模式 */
    private final MutableLiveData<Boolean> mSelectionMode = new MutableLiveData<>(false);
    private final Set<Long> mSelectedTaskIds = new HashSet<>();

    /** 筛选后结果 */
    private final MutableLiveData<List<DisplayItem>> mFilteredItems = new MutableLiveData<>();

    public QuadrantTaskListViewModel(JustNowApplication app) {
        super(app);
        mTaskRepo = new TaskRepository(mDb);
        mTagRepo = new TagRepository(mDb);
        PeriodGroupRuleResolver ruleResolver = new PeriodGroupRuleResolver(mApp);
        mPeriodRepo = new TimePeriodRepository(mDb, ruleResolver);
        mPriorityTagConfig = new PriorityTagConfig(mApp, mTagRepo);
    }

    /**
     * 设置象限并触发数据加载。由 Fragment 在 onViewCreated 中调用。
     */
    public void setQuadrant(int quadrant) {
        if (quadrant < 0 || quadrant > 3 || quadrant == mQuadrant) return;
        mQuadrant = quadrant;
        loadData();
    }

    public int getQuadrant() {
        return mQuadrant;
    }

    public LiveData<List<DisplayItem>> getFilteredItems() {
        return mFilteredItems;
    }

    public LiveData<Boolean> getSelectionMode() {
        return mSelectionMode;
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    // ---- 时长筛选 ----

    public void setFocusFilterMinutes(Set<Integer> minutes) {
        mFocusFilterMinutes.clear();
        if (minutes != null) mFocusFilterMinutes.addAll(minutes);
        applyFilters();
    }

    public Set<Integer> getFocusFilterMinutes() {
        return new HashSet<>(mFocusFilterMinutes);
    }

    public boolean isFocusFilterActive() {
        return !mFocusFilterMinutes.isEmpty();
    }

    // ---- 标签筛选 ----

    public void setSelectedTagIds(Set<Long> tagIds) {
        mSelectedTagIds.clear();
        if (tagIds != null) mSelectedTagIds.addAll(tagIds);
        applyFilters();
    }

    public Set<Long> getSelectedTagIds() {
        return new HashSet<>(mSelectedTagIds);
    }

    public boolean isTagFilterActive() {
        return !mSelectedTagIds.isEmpty();
    }

    /**
     * 获取当前象限任务列表中实际使用的标签（去重、按名称排序）。
     * 供标签筛选下拉 PopupWindow 使用。
     */
    public List<TagEntity> getTagsInCurrentList() {
        Set<Long> seenIds = new HashSet<>();
        List<TagEntity> result = new ArrayList<>();
        for (DisplayItem item : mAllItems) {
            if (item.tag != null && seenIds.add(item.tag.id)) {
                result.add(item.tag);
            }
        }
        Collections.sort(result, (a, b) -> {
            if (a.name == null) return b.name == null ? 0 : 1;
            if (b.name == null) return -1;
            return a.name.compareToIgnoreCase(b.name);
        });
        return result;
    }

    // ---- 多选模式 ----

    /** 长按进入多选，初始选中指定任务 */
    public void enterSelectionMode(long taskId) {
        mSelectedTaskIds.clear();
        mSelectedTaskIds.add(taskId);
        mSelectionMode.setValue(true);
        notifyAdapterRefresh();
    }

    /** 退出多选模式 */
    public void exitSelectionMode() {
        mSelectedTaskIds.clear();
        mSelectionMode.setValue(false);
        notifyAdapterRefresh();
    }

    /** 切换任务选中状态 */
    public void toggleSelection(long taskId) {
        if (mSelectedTaskIds.contains(taskId)) {
            mSelectedTaskIds.remove(taskId);
            if (mSelectedTaskIds.isEmpty()) {
                mSelectionMode.setValue(false);
            }
        } else {
            mSelectedTaskIds.add(taskId);
        }
        notifyAdapterRefresh();
    }

    public boolean isSelected(long taskId) {
        return mSelectedTaskIds.contains(taskId);
    }

    public int getSelectedCount() {
        return mSelectedTaskIds.size();
    }

    /** 删除所有选中任务 */
    public void deleteSelectedTasks(Runnable onComplete) {
        final Set<Long> idsToDelete = new HashSet<>(mSelectedTaskIds);
        if (idsToDelete.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        runInBackground(() -> {
            for (long taskId : idsToDelete) {
                mTaskRepo.delete(taskId);
            }
            mSelectedTaskIds.clear();
            mSelectionMode.postValue(false);
            loadData();
            if (onComplete != null) {
                runOnUiThread(onComplete);
            }
        });
    }

    // ---- 数据加载 ----

    private void loadData() {
        if (mQuadrant < 0) return;
        runInBackground(this::loadDataSync);
    }

    private void loadDataSync() {
        List<TaskEntity> tasks = mTaskRepo.getAllActiveTasksSync();
        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<TagEntity> allTags = mTagRepo.getAllTagsSync();
        if (allTags != null) {
            for (TagEntity tag : allTags) tagMap.put(tag.id, tag);
        }

        SharedPreferences prefs = mApp.getSharedPreferences("justnow_prefs", Context.MODE_PRIVATE);
        String scheduleProfile = prefs.getString("schedule_profile", ScheduleProfile.GENERAL);
        ActivePeriodGroup activeGroup = mPeriodRepo.getActivePeriodGroupSync(scheduleProfile);
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(activeGroup.periods);
        TimeRemainingCalculator.PeriodStatus status = mTimeCalc.compute(periods);
        boolean reverseQuadrant = status.isReverseQuadrant();

        Set<Long> priorityTagIds = mPriorityTagConfig.getEffectivePriorityTagIds(
                activeGroup.getGroupType(), status.period);

        int[] mask = {0, 0, 0, 0};
        mask[mQuadrant] = 1;
        List<DisplayItem>[] results = mDisplayEngine.computeByQuadrant(
                mask, tasks, tagMap, status.remainingMinutes, reverseQuadrant, priorityTagIds);

        mAllItems.clear();
        if (results[mQuadrant] != null) {
            mAllItems.addAll(results[mQuadrant]);
        }
        mLoaded = true;
        applyFiltersSync();
    }

    private void applyFilters() {
        runInBackground(this::applyFiltersSync);
    }

    private void applyFiltersSync() {
        List<DisplayItem> filtered = new ArrayList<>();
        for (DisplayItem item : mAllItems) {
            if (!matchesFocusFilter(item)) continue;
            if (!matchesTagFilter(item)) continue;
            filtered.add(item);
        }
        mFilteredItems.postValue(filtered);
    }

    private void notifyAdapterRefresh() {
        List<DisplayItem> current = mFilteredItems.getValue();
        if (current != null) {
            mFilteredItems.postValue(current);
        }
    }

    /** 时长筛选匹配（OR 逻辑：任意选中档位命中即显示） */
    private boolean matchesFocusFilter(DisplayItem item) {
        if (mFocusFilterMinutes.isEmpty()) return true;
        return mFocusFilterMinutes.contains(item.task.focusMinutes);
    }

    /** 标签筛选匹配（任一选中标签匹配即命中，OR 逻辑） */
    private boolean matchesTagFilter(DisplayItem item) {
        if (mSelectedTagIds.isEmpty()) return true;
        Long tagId = item.task.tagId;
        return tagId != null && mSelectedTagIds.contains(tagId);
    }
}
