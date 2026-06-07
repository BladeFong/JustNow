package com.nearby.justnow.ui.taskinput;

import android.content.res.Resources;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskAppActionRepository;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.repository.TaskNoteShareRepository;
import com.nearby.justnow.data.repository.TaskRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务录入 ViewModel — 管理检索、标签、专注时长、草稿任务状态
 */
public class TaskInputViewModel extends BaseViewModel {

    /** 新建标签默认颜色 */
    private static final int DEFAULT_TAG_COLOR = 0xFF1A73E8;

    private final TaskRepository mTaskRepo;
    private final TagRepository mTagRepo;
    private final TaskChecklistRepository mChecklistRepo;
    private final TaskAppActionRepository mAppActionRepo;
    private final TaskNoteShareRepository mNoteShareRepo;

    /** 搜索结果 */
    private final MutableLiveData<List<TaskEntity>> mSearchResults = new MutableLiveData<>();

    /** 当前搜索的分词结果（供 UI 高亮） */
    private final MutableLiveData<java.util.List<String>> mSearchTokens = new MutableLiveData<>();

    /** 当前编辑中的任务草稿 */
    private TaskEntity mDraftTask;

    /** 编辑模式：正在编辑的任务 ID（0 = 新建模式） */
    private long mEditingTaskId = 0;

    /** 已选标签（可为空） */
    private TagEntity mSelectedTag;

    /** 用户输入的标签名（保存时若标签不存在则创建） */
    private String mTagName;

    /** 选中的附加模块类型：null / 'checklist' / 'app_actions' / 'note_shares' */
    private String mSelectedModuleType;

    /** 编辑模式下原任务附加模块类型，用于清理旧子表 */
    private String mOriginalModuleType;

    /** 暂存的 todo 清单条目（最终保存时才写入） */
    private List<TaskChecklistItem> mPendingChecklistItems;

    /** 暂存的 APP 跳转条目（最终保存时才写入） */
    private List<TaskAppAction> mPendingAppActions;

    /** 暂存的笔记分享条目（最终保存时才写入） */
    private List<TaskNoteShare> mPendingNoteShares;

    /** 来自外部捕获的 APP 跳转预填项（Intent URI + hint），sheet 打开时消费 */
    private String mPendingAppActionPrefillUri;
    private String mPendingAppActionPrefillHint;

    /** 外部捕获要求进入编辑页后自动打开 APP 跳转 sheet */
    private boolean mPendingOpenAppActionSheet;

    /** 来自外部捕获的笔记分享预填项（URI/URL + hint），sheet 打开时消费 */
    private String mPendingNoteSharePrefillUri;
    private String mPendingNoteSharePrefillHint;

    /** 外部捕获要求进入编辑页后自动打开笔记分享 sheet */
    private boolean mPendingOpenNoteShareSheet;

    /** 标记本次编辑来自外部捕获流（CapturePicker），保存成功后需引导用户留在 JustNow */
    private boolean mFromCapture;

    /** 任务列表渲染用：tagId -> tagName 映射 */
    private final MutableLiveData<Map<Long, String>> mTagNamesMap = new MutableLiveData<>();

    public TaskInputViewModel(JustNowApplication app) {
        super(app);
        mTaskRepo = app.getTaskRepository();
        mTagRepo = app.getTagRepository();
        mChecklistRepo = app.getTaskChecklistRepository();
        mAppActionRepo = app.getTaskAppActionRepository();
        mNoteShareRepo = app.getTaskNoteShareRepository();
        resetDraft();
    }

    private void resetDraft() {
        mDraftTask = new TaskEntity();
        mDraftTask.focusMinutes = 30; // 默认30分钟
        mDraftTask.quadrant = 0;       // 默认紧急重要
        mDraftTask.degradePeriod = 1;  // 默认次日
        mEditingTaskId = 0;
        mOriginalModuleType = null;
    }

    public boolean isEditMode() {
        return mEditingTaskId > 0;
    }

    // ---- 检索 ----

    /** 全文检索：TextTokenizer 分词 → 多 token AND 匹配 content + detail */
    public void clearSearchResults() {
        mSearchResults.postValue(new java.util.ArrayList<>());
        mSearchTokens.postValue(new java.util.ArrayList<>());
    }

    public void searchTasks(String input) {
        runInBackground(() -> {
            java.util.List<String> tokens =
                com.nearby.justnow.util.TextTokenizer.tokenize(input);
            mSearchTokens.postValue(tokens);
            java.util.List<TaskEntity> results;
            if (tokens.isEmpty()) {
                results = new java.util.ArrayList<>();
            } else {
                results = mTaskRepo.searchTasksByTokens(tokens);
            }
            mSearchResults.postValue(results);
        });
    }

    public LiveData<List<TaskEntity>> getSearchResults() {
        return mSearchResults;
    }

    public LiveData<java.util.List<String>> getSearchTokens() {
        return mSearchTokens;
    }

    public LiveData<List<TagEntity>> searchTags(String keyword) {
        return mTagRepo.searchTags(keyword);
    }

    public LiveData<List<TagEntity>> getAllTags() {
        return mTagRepo.getAllTags();
    }

    public LiveData<List<TagEntity>> getTopTags(int limit) {
        return mTagRepo.getTopTags(limit);
    }

    // ---- 草稿操作 ----

    /** 设置标题 */
    public void setTitle(String title) {
        mDraftTask.content = title;
    }

    public String getTitle() {
        return mDraftTask.content;
    }

    /** 设置正文 */
    public void setDetail(String detail) {
        mDraftTask.detail = detail;
    }

    public String getDetail() {
        return mDraftTask.detail;
    }

    /** 加载已有任务进入编辑模式 */
    public void loadTaskForEdit(long taskId) {
        mEditingTaskId = taskId;
        runInBackground(() -> {
            TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
            if (task != null) {
                mDraftTask = task;
                mSelectedTag = null;
                mTagName = null;
                if (task.tagId != null && task.tagId > 0) {
                    TagEntity tag = mTagRepo.getTagByIdSync(task.tagId);
                    if (tag != null) {
                        mSelectedTag = tag;
                        mTagName = tag.name;
                    }
                }
                // 加载附加模块数据（不读勾选/划掉/完成状态）
                mSelectedModuleType = task.detailModuleType;
                mOriginalModuleType = task.detailModuleType;
                mPendingChecklistItems = null;
                mPendingAppActions = null;
                mPendingNoteShares = null;
                if ("checklist".equals(task.detailModuleType)) {
                    mPendingChecklistItems = mChecklistRepo.getByTaskIdSync(taskId);
                } else if ("app_actions".equals(task.detailModuleType)) {
                    mPendingAppActions = mAppActionRepo.getByTaskIdSync(taskId);
                } else if ("note_shares".equals(task.detailModuleType)) {
                    mPendingNoteShares = mNoteShareRepo.getByTaskIdSync(taskId);
                }
            }
        });
    }

    public void setFocusMinutes(int minutes) {
        mDraftTask.focusMinutes = minutes;
    }

    public int getFocusMinutes() {
        return mDraftTask.focusMinutes;
    }

    public void setQuadrant(int quadrant) {
        mDraftTask.quadrant = quadrant;
    }

    public int getQuadrant() {
        return mDraftTask.quadrant;
    }

    public void setDegradePeriod(int period) {
        mDraftTask.degradePeriod = period;
    }

    public int getDegradePeriod() {
        return mDraftTask.degradePeriod;
    }

    public String getFocusMinutesLabel(Resources res) {
        if (mDraftTask.focusMinutes == 0) return res.getString(R.string.s_chore_label);
        if (mDraftTask.focusMinutes == 30) return res.getString(R.string.s_30min_label);
        if (mDraftTask.focusMinutes == 60) return res.getString(R.string.s_60min_label);
        if (mDraftTask.focusMinutes == 90) return res.getString(R.string.s_90min_label);
        if (mDraftTask.focusMinutes == 120) return res.getString(R.string.s_120min_label);
        return mDraftTask.focusMinutes + res.getString(R.string.s_minute_unit);
    }

    // ---- 标签 ----

    public void setSelectedTag(TagEntity tag) {
        this.mSelectedTag = tag;
    }

    public TagEntity getSelectedTag() {
        return mSelectedTag;
    }

    public void setTagName(String name) {
        this.mTagName = (name != null && !name.isEmpty()) ? name : null;
    }

    public String getTagName() {
        return mTagName;
    }

    public void setMarkdown(String markdown) {
        mDraftTask.detailMarkdown = markdown;
    }

    public String getMarkdown() {
        return mDraftTask.detailMarkdown;
    }

    // ---- 附加模块 ----

    public String getSelectedModuleType() {
        return mSelectedModuleType;
    }

    public void setSelectedModuleType(String type) {
        // 切换模块不清除旧编辑内容，仅更新选中状态
        mSelectedModuleType = type;
    }

    public List<TaskChecklistItem> getPendingChecklistItems() {
        return mPendingChecklistItems;
    }

    public void setPendingChecklistItems(List<TaskChecklistItem> items) {
        mPendingChecklistItems = items;
    }

    public List<TaskAppAction> getPendingAppActions() {
        return mPendingAppActions;
    }

    public void setPendingAppActions(List<TaskAppAction> actions) {
        mPendingAppActions = actions;
    }

    public List<TaskNoteShare> getPendingNoteShares() {
        return mPendingNoteShares;
    }

    public void setPendingNoteShares(List<TaskNoteShare> shares) {
        mPendingNoteShares = shares;
    }

    // ---- 任务列表渲染：tag 名映射 ----

    public LiveData<Map<Long, String>> getTagNamesMap() {
        return mTagNamesMap;
    }

    public void loadTagNamesMap() {
        runInBackground(() -> {
            Map<Long, TagEntity> raw = mTagRepo.getAllTagsMapSync();
            Map<Long, String> names = new HashMap<>();
            if (raw != null) {
                for (Map.Entry<Long, TagEntity> e : raw.entrySet()) {
                    TagEntity tag = e.getValue();
                    if (tag != null) names.put(e.getKey(), tag.name);
                }
            }
            mTagNamesMap.postValue(names);
        });
    }

    // ---- 外部捕获预填 ----

    /** 本次编辑是否来自外部捕获流 */
    public boolean isFromCapture() {
        return mFromCapture;
    }

    /** 标记本次编辑来自外部捕获流 */
    public void setFromCapture(boolean fromCapture) {
        mFromCapture = fromCapture;
    }

    /** 灌入新建任务草稿字段（外部捕获入口 2 / 3） */
    public void applyDraftPrefill(String title, String tagName, String markdown) {
        if (title != null) mDraftTask.content = title;
        if (markdown != null) mDraftTask.detailMarkdown = markdown;
        if (tagName != null && !tagName.isEmpty()) {
            mTagName = tagName;
        }
    }

    /**
     * 暂存外部捕获的 APP 跳转预填项，sheet 打开时调用 {@link #consumePendingAppActionPrefill()}。
     * 同时设置自动打开 sheet 标记。
     */
    public void stagePendingAppActionPrefill(String intentUri, String hint, boolean openSheet) {
        mPendingAppActionPrefillUri = intentUri;
        mPendingAppActionPrefillHint = hint;
        mPendingOpenAppActionSheet = openSheet;
        mFromCapture = true;
        if (openSheet) {
            // 入口 1 / 入口 2 都需选中 APP 跳转模块
            mSelectedModuleType = "app_actions";
        }
    }

    /** Fragment 端读取并清空（一次性）：是否要自动打开 APP 跳转 sheet */
    public boolean consumePendingOpenAppActionSheet() {
        boolean v = mPendingOpenAppActionSheet;
        mPendingOpenAppActionSheet = false;
        return v;
    }

    /** Sheet 端读取并清空（一次性）：外部捕获预填项；无则返回 null */
    public TaskAppAction consumePendingAppActionPrefill() {
        if (mPendingAppActionPrefillUri == null || mPendingAppActionPrefillUri.isEmpty()) {
            return null;
        }
        TaskAppAction action = new TaskAppAction();
        action.deepLink = mPendingAppActionPrefillUri;
        action.hint = mPendingAppActionPrefillHint;
        action.packageName = resolvePackageFromIntentUri(mPendingAppActionPrefillUri);
        mPendingAppActionPrefillUri = null;
        mPendingAppActionPrefillHint = null;
        return action;
    }

    /**
     * 暂存外部捕获的笔记分享预填项，sheet 打开时调用 {@link #consumePendingNoteSharePrefill()}。
     * 同时设置自动打开 sheet 标记。
     */
    public void stagePendingNoteSharePrefill(String uri, String hint, boolean openSheet) {
        mPendingNoteSharePrefillUri = uri;
        mPendingNoteSharePrefillHint = hint;
        mPendingOpenNoteShareSheet = openSheet;
        mFromCapture = true;
        if (openSheet) {
            mSelectedModuleType = "note_shares";
        }
    }

    /** Fragment 端读取并清空（一次性）：是否要自动打开笔记分享 sheet */
    public boolean consumePendingOpenNoteShareSheet() {
        boolean v = mPendingOpenNoteShareSheet;
        mPendingOpenNoteShareSheet = false;
        return v;
    }

    /** Sheet 端读取并清空（一次性）：外部捕获笔记分享预填项；无则返回 null */
    public TaskNoteShare consumePendingNoteSharePrefill() {
        if (mPendingNoteSharePrefillUri == null || mPendingNoteSharePrefillUri.isEmpty()) {
            return null;
        }
        TaskNoteShare share = new TaskNoteShare();
        share.deepLink = mPendingNoteSharePrefillUri;
        share.hint = mPendingNoteSharePrefillHint;
        mPendingNoteSharePrefillUri = null;
        mPendingNoteSharePrefillHint = null;
        return share;
    }

    private String resolvePackageFromIntentUri(String uri) {
        try {
            android.content.Intent intent =
                android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME);
            if (intent.getPackage() != null) return intent.getPackage();
            if (intent.getComponent() != null) return intent.getComponent().getPackageName();
            android.content.pm.PackageManager pm = mApp.getPackageManager();
            android.content.pm.ResolveInfo ri = pm.resolveActivity(intent, 0);
            return ri != null ? ri.activityInfo.packageName : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 保存 ----

    /** 保存任务：编辑模式 update，新建模式 insert。标签按名称查找或创建 */
    public void saveTask(Runnable onComplete) {
        runInBackground(() -> {
            Long tagId = null;
            if (mTagName != null && !mTagName.isEmpty()) {
                TagEntity found = mTagRepo.getTagByNameSync(mTagName);
                if (found != null) {
                    tagId = found.id;
                } else {
                    TagEntity newTag = new TagEntity();
                    newTag.name = mTagName;
                    newTag.color = DEFAULT_TAG_COLOR;
                    tagId = mTagRepo.insertSync(newTag);
                }
            }
            mDraftTask.tagId = tagId;
            String normalizedModuleType = normalizeSelectedModuleType();
            mDraftTask.detailModuleType = normalizedModuleType;

            // 编辑模式：检查内容变化以决定是否清除清单状态
            if (mEditingTaskId > 0 && "checklist".equals(normalizedModuleType)
                && mPendingChecklistItems != null) {
                List<TaskChecklistItem> oldItems = mChecklistRepo.getByTaskIdSync(mEditingTaskId);
                if (checklistContentChanged(oldItems, mPendingChecklistItems)
                    && mChecklistRepo.hasAnyStateSync(mEditingTaskId)) {
                    for (TaskChecklistItem item : mPendingChecklistItems) {
                        item.checked = false;
                        item.crossedOut = false;
                    }
                }
            }

            if (mEditingTaskId > 0) {
                // 编辑模式：象限变更 → 清理旧降级记录
                TaskEntity oldTask = mTaskRepo.getTaskByIdSync(mEditingTaskId);
                if (oldTask != null && oldTask.quadrant != mDraftTask.quadrant) {
                    mTaskRepo.deleteDegradeSync(mEditingTaskId);
                }
                // 编辑模式：更新已有任务
                mTaskRepo.updateSync(mDraftTask);
            } else {
                // 新建模式：插入新任务
                mDraftTask.createdAt = System.currentTimeMillis();
                long newTaskId = mTaskRepo.insertSync(mDraftTask);
                mDraftTask.id = newTaskId;
            }

            // 清理与新模块类型不同的旧子表（编辑模式或新建场景）
            if (mOriginalModuleType != null
                && !mOriginalModuleType.equals(normalizedModuleType)) {
                if ("checklist".equals(mOriginalModuleType)) {
                    mChecklistRepo.deleteByTaskIdSync(mDraftTask.id);
                } else if ("app_actions".equals(mOriginalModuleType)) {
                    mAppActionRepo.deleteByTaskIdSync(mDraftTask.id);
                } else if ("note_shares".equals(mOriginalModuleType)) {
                    mNoteShareRepo.deleteByTaskIdSync(mDraftTask.id);
                }
            }

            // 写入新模块数据
            if ("checklist".equals(normalizedModuleType)) {
                for (int i = 0; i < mPendingChecklistItems.size(); i++) {
                    mPendingChecklistItems.get(i).taskId = mDraftTask.id;
                    mPendingChecklistItems.get(i).orderIndex = i;
                }
                mChecklistRepo.replaceAllByTaskIdSync(mDraftTask.id, mPendingChecklistItems);
            } else if ("app_actions".equals(normalizedModuleType)) {
                for (int i = 0; i < mPendingAppActions.size(); i++) {
                    mPendingAppActions.get(i).taskId = mDraftTask.id;
                    mPendingAppActions.get(i).orderIndex = i;
                }
                mAppActionRepo.replaceAllByTaskIdSync(mDraftTask.id, mPendingAppActions);
            } else if ("note_shares".equals(normalizedModuleType)) {
                for (int i = 0; i < mPendingNoteShares.size(); i++) {
                    mPendingNoteShares.get(i).taskId = mDraftTask.id;
                    mPendingNoteShares.get(i).orderIndex = i;
                }
                mNoteShareRepo.replaceAllByTaskIdSync(mDraftTask.id, mPendingNoteShares);
            }

            resetDraft();
            mSelectedTag = null;
            mTagName = null;
            mSelectedModuleType = null;
            mOriginalModuleType = null;
            mPendingChecklistItems = null;
            mPendingAppActions = null;
            mPendingNoteShares = null;
            if (onComplete != null) runOnUiThread(onComplete);
        });
    }

    private String normalizeSelectedModuleType() {
        if ("checklist".equals(mSelectedModuleType)
            && hasEffectiveChecklistItems(mPendingChecklistItems)) {
            return "checklist";
        }
        if ("app_actions".equals(mSelectedModuleType)
            && hasEffectiveAppActions(mPendingAppActions)) {
            return "app_actions";
        }
        if ("note_shares".equals(mSelectedModuleType)
            && hasEffectiveNoteShares(mPendingNoteShares)) {
            return "note_shares";
        }
        return null;
    }

    private boolean hasEffectiveChecklistItems(List<TaskChecklistItem> items) {
        if (items == null) return false;
        for (TaskChecklistItem item : items) {
            if (item != null && item.content != null && !item.content.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEffectiveAppActions(List<TaskAppAction> actions) {
        if (actions == null) return false;
        for (TaskAppAction action : actions) {
            if (action == null) continue;
            boolean hasPkg = action.packageName != null && !action.packageName.trim().isEmpty();
            boolean hasLink = action.deepLink != null && !action.deepLink.trim().isEmpty();
            if (hasPkg || hasLink) return true;
        }
        return false;
    }

    private boolean hasEffectiveNoteShares(List<TaskNoteShare> shares) {
        if (shares == null) return false;
        for (TaskNoteShare s : shares) {
            if (s == null) continue;
            if (s.deepLink != null && !s.deepLink.trim().isEmpty()) return true;
        }
        return false;
    }

    /** 比较清单条目内容是否变化（仅按 content 比较） */
    private boolean checklistContentChanged(List<TaskChecklistItem> oldItems, List<TaskChecklistItem> newItems) {
        if (oldItems == null && newItems == null) return false;
        if (oldItems == null || newItems == null) return true;
        if (oldItems.size() != newItems.size()) return true;
        for (int i = 0; i < oldItems.size(); i++) {
            if (!java.util.Objects.equals(oldItems.get(i).content, newItems.get(i).content)) return true;
        }
        return false;
    }

    /** 快速创建标签 */
    public void createTag(String name, int color, Runnable onComplete) {
        TagEntity tag = new TagEntity();
        tag.name = name;
        tag.color = color;
        mTagRepo.insert(tag, onComplete);
    }
}
