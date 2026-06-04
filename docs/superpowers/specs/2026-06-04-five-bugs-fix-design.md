# 5 项 Bug 修复设计

日期：2026-06-04

## Bug 1: 执行中专注任务不插入左侧时间线 + 右侧栏执行中高亮丢失

### 现象

专注任务开始执行后，左侧时间线不出现任务条，右侧任务列表也没有执行中高亮。

### 根因

`TimelineBuilder.build()` 第 67-74 行正确地将执行中任务加入 `items`。`TaskAdapter` 第 188-197 行也正确判断执行中状态。

数据链路：`TaskRepository.startExecutionSync()` → `notifyTaskDataChanged()` → `DataChangeDispatcher` → 主界面 `recompute()`。

**问题在于时序**：`startExecutionSync` 是同步写 DB + `notifyTaskDataChanged()`，但 `recompute()` 的触发链（MediatorLiveData → 后台线程 → postValue）存在延迟。如果 `startExecutionSync` 在 `recomputeSync` 的 `getAllActiveTasksSync()` 之前完成且缓存被更新，数据应该是正确的。

进一步排查：`TaskRepository` 有内存缓存 `mCachedActiveTasks`，`startExecutionSync` 修改 DB 后调用 `notifyTaskDataChanged()` 触发 LiveData 更新，但 `mCachedActiveTasks` 的更新路径取决于 `notifyTaskDataChanged` → `DataChangeDispatcher` → `mTaskRepo.getAllActiveTasks()` LiveData 是否会刷新缓存。

**实际根因（两层缓存）**：

1. **`TaskRepository` 缓存**：`startExecutionSync` 未置空 `mCachedActiveTasks`，导致 `getAllActiveTasksSync()` 返回旧缓存（`executingStartMs = 0`）。`update()` 有置空但 `startExecutionSync` 漏了。

2. **`TimelineBuilder` 缓存**：`build()` 第 47 行用 task ID + execution ID 做缓存键。任务开始执行后 `executingStartMs` 变化但 ID 不变，缓存命中返回旧结果——里面没有该执行中任务块。这才是"返回任意界面都没用、只有重启 APP 才恢复"的根因。

### 修复

1. `TaskRepository` 中 `startExecution`/`startExecutionSync`/`endExecution`/`clearExecution`/`clearExecutionSync`/`convertToChoreSync` 均补 `mCachedActiveTasks = null`。

2. `TimelineBuilder` 缓存键增加 `hasRunning`（当前是否有执行中任务），执行状态变化时自动穿透缓存。

---

## Bug 2: 已完成专注任务时间线块应显示真实耗时

### 现象

完成的专注任务在时间线上显示的是 `focusMinutes`（计划时长），而非真实耗时。

### 根因

`TimelineView.getTaskMetaText()` 第 556 行：始终返回 `item.focusMinutes + "min"`。对于已完成任务，`TaskExecutionEntity.actualMinutes` 已记录真实耗时，但 `TimelineItem` 没有 `actualMinutes` 字段。

`TimelineBuilder.build()` 第 115-123 行，从 `TaskExecutionEntity` 构建 `TimelineItem` 时只传了 `focusMinutes`，没有传 `actualMinutes`。

同时 `TimelineView.onDraw()` 第 355-360 行，已完成任务条形图的**长度**已经用 `(endMs - startMs) / 60000` 绘制真实耗时，但**元文字**仍是 focusMinutes。

### 修复

1. `TimelineItem` 增加 `actualMinutes` 字段（0 表示未完成 / 执行中）
2. `TimelineBuilder` 构建已完成项时传入 `execution.actualMinutes`
3. `TimelineView.getTaskMetaText()` 对已完成任务显示 `actualMinutes`，执行中仍显示 `focusMinutes`

---

## Bug 3: 执行中专注任务详情页按钮文案和功能问题

### 现象

- 有附加模块的专注任务，详情页右下角按钮显示"完成本次"
- 非模块任务弹窗右下角按钮也显示"完成本次"
- 无论有无安排，主操作按钮都是"完成本次"

### 根因

`ReminderDetailActivity.setupBottomButtons()` 第 204-214 行：

```java
if (!isFocus) {
    // 琐碎任务
    ...
} else {
    // 专注任务
    if (hasSchedule) {
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(getString(R.string.s_complete_and_stop_schedule));
    }
    btnPrimary.setText(getString(R.string.s_complete_once));  // ← 始终"完成本次"
}
```

**文案问题**：`s_complete_once`（完成本次）只在有循环安排时才有语义区分（"完成本次" vs "完成并停止安排"）。无安排时应显示"完成"。

**功能问题**：`handleFocusCompletion()` 中 `stopSchedule=false` 路径的完成逻辑是正确的——会走 `completeRunningTask` 写执行记录。文案虽让人困惑但功能无误。

### 修复

`setupBottomButtons()` 中，专注任务的 `btnPrimary` 文案按有无安排区分：
- 有循环安排 → "完成本次"（与 secondary "完成并停止安排" 对应）
- 无安排 → "完成"

同理，`MainFragment.showTimelineCompletionDialog()` 中 `s_complete_once` 也需按同样逻辑区分。

---

## Bug 4: 编辑任务页标签输入框被输入法遮挡

### 现象

在任务编辑页面，聚焦标签输入框后，软键盘弹出遮挡输入框。

### 根因

`TaskInputActivity` 使用 `EdgeToEdge.enable()` + `windowSoftInputMode="adjustResize"`。EdgeToEdge 开启后，系统不再自动为 IME 调整窗口大小，`adjustResize` 失效。

当前 `setOnApplyWindowInsetsListener` 只处理了 `statusBars` 的 top padding，**未处理 IME 的 bottom inset**。

### 修复（Android 官方规范做法）

在 `TaskInputActivity.onCreate()` 的 `setOnApplyWindowInsetsListener` 中，同时处理 `WindowInsetsCompat.Type.ime()`：

```java
ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
    int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
    int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
    mBinding.appBarLayout.setPadding(
        mBinding.appBarLayout.getPaddingLeft(), top,
        mBinding.appBarLayout.getPaddingRight(),
        mBinding.appBarLayout.getPaddingBottom());
    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
        v.getPaddingRight(), imeBottom);
    return insets;
});
```

根布局底部 padding 随 IME 高度变化，输入框自然被顶到键盘上方。

---

## Bug 5: 单象限任务列表删除后不刷新 + 多选状态未退出

### 现象

1. 从任务详情页删除任务后返回，列表不刷新
2. 长按多选删除后，列表不刷新，顶部栏未退出多选状态

### 根因

**问题 1（单条删除后不刷新）**：`QuadrantTaskListFragment` 从 `ReminderDetailActivity`（MODE_VIEW）删除任务后返回，Fragment 的 `onResume` 未触发 `loadData()`。`QuadrantTaskListViewModel` 使用的是 `mAllItems` 内存缓存 + `loadDataSync()` 从 DB 加载，但**没有观察 `TaskRepository` 的 LiveData**。数据变更不会自动反映。

**问题 2（多选删除后不刷新 + 顶部栏不退出）**：`deleteSelectedTasks()` 第 184-200 行，在后台线程中：
```java
mSelectedTaskIds.clear();
mSelectionMode.postValue(false);  // postValue，非 setValue
loadData();                        // 后台线程中重新加载
if (onComplete != null) runOnUiThread(onComplete);
```

- `mSelectionMode.postValue(false)` 在后台线程调用，值会延迟到主线程。但 `loadData()` 也在后台线程执行并 `postValue` 到 `mFilteredItems`。
- `onComplete` 回调是 `this::updateSelectionTitle`，此时标题更新可能发生在 `onSelectionModeChanged` 之前（时序不确定）。
- **核心问题**：`mSelectionMode.postValue(false)` 确实会触发 `onSelectionModeChanged(false)`，恢复顶部栏。但 `deleteSelectedTasks` 中 `mTaskRepo.delete(taskId)` 是异步的（`mDb.runInBackground`），**删除可能还没完成，`loadData()` 就已经读到了旧数据**。

`TaskRepository.delete()` 方法：
```java
public void delete(long taskId) {
    mDb.runInBackground(() -> {
        mDb.runInTransaction(() -> { ... });
        notifyTaskDataChanged();
    });
}
```

而 `QuadrantTaskListViewModel.deleteSelectedTasks()` 的 for 循环中直接调用 `mTaskRepo.delete(taskId)`，这是**异步投递后台任务**，for 循环立即结束，`loadData()` 紧接着执行时删除可能还没落盘。

### 修复

1. **观察数据变更**：`QuadrantTaskListViewModel` 增加 `mTaskRepo.getAllActiveTasks()` LiveData 观察，数据变更时自动 `loadData()`。或者，Fragment `onResume` 时主动调用 `mViewModel.loadData()`。

2. **删除同步化**：`deleteSelectedTasks()` 中改用同步删除（`mTaskRepo` 需要提供同步 `deleteSync` 方法），确保 `loadData()` 在删除完成后执行。

3. **多选状态退出**：`mSelectionMode.postValue(false)` 改为 `runOnUiThread(() -> mSelectionMode.setValue(false))`，确保 UI 立即更新。同时 `onComplete` 回调应在 `loadData` 和模式切换之后执行。
