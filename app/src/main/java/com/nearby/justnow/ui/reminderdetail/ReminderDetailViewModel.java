package com.nearby.justnow.ui.reminderdetail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.ui.base.BaseTaskViewModel;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskAppActionRepository;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.repository.TaskNoteShareRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 任务详情页 ViewModel
 */
public class ReminderDetailViewModel extends BaseTaskViewModel {

    private final TaskRepository mTaskRepo;
    private final TaskScheduleRepository mScheduleRepo;
    private final TaskChecklistRepository mChecklistRepo;
    private final TaskAppActionRepository mAppActionRepo;
    private final TaskNoteShareRepository mNoteShareRepo;
    private final TagRepository mTagRepo;

    /** 当前任务 */
    private TaskEntity mTask;
    /** 当前安排 */
    private TaskScheduleEntity mSchedule;

    /** APP 跳转当次会话已处理项（仅内存，不写库） */
    private final Set<Long> mCompletedAppActions = new HashSet<>();

    /** 清单状态变化通知 */
    private final MutableLiveData<List<TaskChecklistItem>> mChecklistItems = new MutableLiveData<>();

    /** 一般性完成前确认回调 */
    private PreCompleteConfirmCallback mPreCompleteCallback;

    public ReminderDetailViewModel(JustNowApplication app) {
        super(app);
        mTaskRepo = app.getTaskRepository();
        mScheduleRepo = app.getTaskScheduleRepository();
        mChecklistRepo = app.getTaskChecklistRepository();
        mAppActionRepo = app.getTaskAppActionRepository();
        mNoteShareRepo = app.getTaskNoteShareRepository();
        mTagRepo = app.getTagRepository();
    }

    public void setPreCompleteConfirmCallback(PreCompleteConfirmCallback callback) {
        mPreCompleteCallback = callback;
    }

    /** 加载任务完整数据 */
    public void loadTask(long taskId, Consumer<TaskEntity> onTask, Consumer<TaskScheduleEntity> onSchedule) {
        runInBackground(() -> {
            mTask = mTaskRepo.getTaskByIdSync(taskId);
            mSchedule = mScheduleRepo.getActiveScheduleSync(taskId);
            mCompletedAppActions.clear();
            runOnUiThread(() -> {
                onTask.accept(mTask);
                onSchedule.accept(mSchedule);
            });
        });
    }

    /** 获取 tag */
    public TagEntity getTagSync() {
        if (mTask == null || mTask.tagId == null) return null;
        return mTagRepo.getTagByIdSync(mTask.tagId);
    }

    // ---- todo 清单 ----

    public void loadChecklistItems(long taskId) {
        runInBackground(() -> {
            List<TaskChecklistItem> items = mChecklistRepo.getByTaskIdSync(taskId);
            runOnUiThread(() -> mChecklistItems.postValue(items));
        });
    }

    public LiveData<List<TaskChecklistItem>> getChecklistItems() {
        return mChecklistItems;
    }

    /** 切换勾选状态（不上锁互斥：划掉时不可勾选，已在 UI 层处理） */
    public void toggleChecked(TaskChecklistItem item) {
        if (item.crossedOut) return; // 划掉后不可勾选
        item.checked = !item.checked;
        mChecklistRepo.updateState(item);
    }

    /** 切换划掉状态 */
    public void toggleCrossedOut(TaskChecklistItem item) {
        item.crossedOut = !item.crossedOut;
        if (item.crossedOut) {
            item.checked = false;
        }
        mChecklistRepo.updateState(item);
    }

    // ---- APP 跳转 ----

    public List<TaskAppAction> getAppActionsSync(long taskId) {
        return mAppActionRepo.getByTaskIdSync(taskId);
    }

    /** 标记某个 APP 跳转项在当前会话已处理 */
    public void markAppActionCompleted(long actionId) {
        mCompletedAppActions.add(actionId);
    }

    public boolean isAppActionCompleted(long actionId) {
        return mCompletedAppActions.contains(actionId);
    }

    // ---- 完成流程 ----

    public boolean isExecuting() {
        return mTask != null && mTask.executingStartMs > 0 && mTask.executingEndMs == 0;
    }

    public boolean isFocusTask() {
        return mTask != null && mTask.focusMinutes > 0;
    }

    public boolean hasSchedule() {
        return mSchedule != null;
    }

    /**
     * 完成任务（内部处理清单状态确认后重入）
     */
    public void completeRunningTask(boolean stopSchedule, Runnable onComplete) {
        runInBackground(() -> {
            if (checkListStateNeedsConfirm(mTask, mTask.id) && mPreCompleteCallback != null) {
                runOnUiThread(() -> {
                    mPreCompleteCallback.onConfirmNeeded(mTask.id,
                        CONFIRM_TYPE_CHECKLIST_STATE,
                        () -> {
                            // 确认后重入（跳过检查）
                            runInBackground(() -> {
                                doCompleteRunningTask(stopSchedule, onComplete);
                            });
                        });
                });
            } else {
                doCompleteRunningTask(stopSchedule, onComplete);
            }
        });
    }

    private void doCompleteRunningTask(boolean stopSchedule, Runnable onComplete) {
        TaskEntity task = mTaskRepo.getTaskByIdSync(mTask.id);
        completeTaskFlow(task, mSchedule, stopSchedule, onComplete);
    }

    /** 归档任务（琐碎"不再需要"，不检查清单） */
    public void archiveTask(Runnable onComplete) {
        runInBackground(() -> archiveTaskFlow(mTask.id, mSchedule, onComplete));
    }

    /** 重置清单状态（不保存确认时使用） */
    public void resetChecklistState(long taskId) {
        mChecklistRepo.resetAllByTaskId(taskId);
    }

    /** 删除任务 */
    public void deleteTask(long taskId) {
        runInBackground(() -> {
            // 先取消闹钟，再删除
            if (mSchedule != null) {
                // 删除任务：属退出语义，连带清掉当天剩余所有 schedule 的闹钟（bug 修复）
                ReminderNotifier.cancel(mApp, mSchedule.id);
            }
            mTaskRepo.deleteSync(taskId);
        });
    }

    // ---- < 15min 完成路径 ----

    /**
     * &lt; 15min 完成"取消"选项：仅退出对话框，任务保持执行中。
     * 当前不动数据，方法保留是为了语义清晰，并可用于未来加埋点。
     */
    public void cancelShortCompletion() {
        // no-op
    }

    /**
     * &lt; 15min 完成「直接完成」：不写 task_executions，仅清执行中状态，加入"今日隐藏"集合；
     * 按入口决定是否停安排。
     */
    public void shortCompleteDirect(long taskId, boolean stopSchedule, Runnable onComplete) {
        runInBackground(() -> shortCompleteFlow(taskId, stopSchedule, false, mSchedule, onComplete));
    }

    /**
     * &lt; 15min 完成「(完成 / 不再安排) 并调整」：不写 task_executions，将任务改琐碎，
     * 按入口决定是否停安排，加入"今日隐藏"集合。
     */
    public void shortCompleteAndConvertToChore(long taskId, boolean stopSchedule, Runnable onComplete) {
        runInBackground(() -> shortCompleteFlow(taskId, stopSchedule, true, mSchedule, onComplete));
    }

    /**
     * 后台加载 APP 跳转列表，回调到主线程（供 Activity 使用）。
     */
    public void loadAppActionsAsync(long taskId, java.util.function.Consumer<List<TaskAppAction>> callback) {
        runInBackground(() -> {
            List<TaskAppAction> actions = mAppActionRepo.getByTaskIdSync(taskId);
            runOnUiThread(() -> callback.accept(actions));
        });
    }

    /**
     * 后台加载笔记分享列表，回调到主线程（供 Activity 使用）。
     */
    public void loadNoteSharesAsync(long taskId, Consumer<List<TaskNoteShare>> callback) {
        runInBackground(() -> {
            List<TaskNoteShare> shares = mNoteShareRepo.getByTaskIdSync(taskId);
            runOnUiThread(() -> callback.accept(shares));
        });
    }

    /**
     * 后台重置清单状态并回调到主线程（供 Activity 清单状态确认弹窗"不保存"选项使用）。
     */
    public void resetChecklistStateWithCallback(long taskId, Runnable onComplete) {
        runInBackground(() -> {
            mChecklistRepo.resetAllByTaskIdSync(taskId);
            runOnUiThread(onComplete);
        });
    }

    // ---- 通用确认回调（透传 BaseTaskViewModel 接口） ----
    // 由 Fragment 在 onViewCreated 时注入
}
