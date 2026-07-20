# 任务多照片支持与完成前拍照 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 每任务支持最多 5 张照片，拍照入口前移到任务进行中，成果墙按任务去重展示首张并支持左右划动浏览。

**Architecture:** 扩展现有 `TaskPhotoEntity` 表（天然支持多行），在 DAO/Repository 层新增查询方法；改造 `RetroactivePhotoDialog` 为通用 `TaskPhotoListDialog`；花瓣计花改用每任务每周首张去重；成果墙全屏切换为 ViewPager2。

**Tech Stack:** Java, Android Room, ViewPager2, MediaStore, FileProvider

**相关 Spec:** `docs/superpowers/specs/2026-07-21-task-photo-multi-and-precompletion-design.md`
**相关模块:** `modules/tablet-flower-rewards.md`

## Global Constraints

- Java 命名：成员变量 `m` 前缀，静态变量 `s` 前缀，局部变量 `camelCase`
- 字符串必须资源化写入 `res/values/strings.xml`（及 zh-rCN/zh-rTW/zh-rHK）
- 禁止硬编码 `android:textSize`，使用 `TextAppearance.JustNow.*` 或 `R.dimen.text_size_*`
- 每任务最多 5 张照片，插入前检查
- 花瓣只计每任务每周首张（`created_at` 最小者）
- 任务列表按时间倒序排列

---

### Task 1: TaskPhotoDao 新增查询方法

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/dao/TaskPhotoDao.java`

**Interfaces:**
- Produces: `int getPhotoCountForTask(long taskId)`, `List<TaskPhotoEntity> getPhotosForTask(long taskId)`, `List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long startTimeMs, long endTimeMs)`, `List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs)`

- [ ] **Step 1: 在 TaskPhotoDao 接口末尾添加 4 个查询方法**

```java
@Query("SELECT COUNT(*) FROM task_photos WHERE task_id = :taskId")
int getPhotoCountForTask(long taskId);

@Query("SELECT * FROM task_photos WHERE task_id = :taskId ORDER BY created_at ASC")
List<TaskPhotoEntity> getPhotosForTask(long taskId);

@Query("SELECT p.*, t.content as taskContent, t.quadrant as taskQuadrant, t.icon_name as taskIconName " +
       "FROM task_photos p INNER JOIN tasks t ON p.task_id = t.id " +
       "WHERE p.id IN (" +
       "  SELECT MIN(p2.id) FROM task_photos p2 " +
       "  WHERE p2.created_at >= :startTimeMs AND p2.created_at <= :endTimeMs " +
       "  GROUP BY p2.task_id" +
       ") ORDER BY p.created_at ASC")
List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long startTimeMs, long endTimeMs);

@Query("SELECT DISTINCT t.* FROM tasks t " +
       "LEFT JOIN task_executions e ON t.id = e.task_id AND e.status = 0 " +
       "WHERE t.is_archived = 0 AND (" +
       "  (e.end_ms >= :todayStartMs AND e.end_ms <= :todayEndMs) " +
       "  OR (t.executing_start_ms > 0 AND t.executing_end_ms = 0)" +
       ") ORDER BY COALESCE(e.end_ms, t.executing_start_ms) DESC")
List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs);
```

- [ ] **Step 2: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/dao/TaskPhotoDao.java
git commit -m "feat: TaskPhotoDao 新增多照片查询方法

- getPhotoCountForTask: 查任务已拍张数
- getPhotosForTask: 查任务所有照片
- getFirstPhotoPerTaskInRange: 范围内每任务首张
- getTodayTasksAvailableForPhoto: 当天可拍照任务

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: TaskPhotoRepository 新增仓储方法

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`

**Interfaces:**
- Consumes: `TaskPhotoDao.getPhotoCountForTask(long)`, `TaskPhotoDao.getPhotosForTask(long)`, `TaskPhotoDao.getFirstPhotoPerTaskInRange(long, long)`, `TaskPhotoDao.getTodayTasksAvailableForPhoto(long, long)`
- Produces: `int getPhotoCountForTask(long taskId)`, `List<TaskPhotoEntity> getPhotosForTask(long taskId)`, `List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long, long)`, `List<TaskEntity> getTodayTasksAvailableForPhoto(long, long)`, `boolean isPhotoLimitReached(long taskId)`

- [ ] **Step 1: 在 TaskPhotoRepository 末尾添加方法**

```java
/**
 * 查任务已拍张数
 */
public int getPhotoCountForTask(long taskId) {
    return mDb.taskPhotoDao().getPhotoCountForTask(taskId);
}

/**
 * 是否已拍满 5 张
 */
public boolean isPhotoLimitReached(long taskId) {
    return getPhotoCountForTask(taskId) >= 5;
}

/**
 * 查任务所有照片（按时间正序，全屏划动用）
 */
public List<TaskPhotoEntity> getPhotosForTask(long taskId) {
    return mDb.taskPhotoDao().getPhotosForTask(taskId);
}

/**
 * 指定时间范围内每任务首张照片（带任务信息）
 */
public List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long startMs, long endMs) {
    return mDb.taskPhotoDao().getFirstPhotoPerTaskInRange(startMs, endMs);
}

/**
 * 当天可拍照任务：已完成（今天有 execution）或进行中
 */
public List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs) {
    return mDb.taskPhotoDao().getTodayTasksAvailableForPhoto(todayStartMs, todayEndMs);
}
```

- [ ] **Step 2: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java
git commit -m "feat: TaskPhotoRepository 新增多照片仓储方法

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: RetroactivePhotoDialog → TaskPhotoListDialog 重命名并扩展

**Files:**
- Rename: `app/src/main/java/com/nearby/justnow/ui/main/RetroactivePhotoDialog.java` → `app/src/main/java/com/nearby/justnow/ui/main/TaskPhotoListDialog.java`
- Rename: `app/src/main/res/layout/dialog_retroactive_list.xml` → `app/src/main/res/layout/dialog_task_photo_list.xml`
- Modify: `app/src/main/res/layout/item_retroactive_task.xml` → (保持不变，只在其绑定时新增张数显示逻辑)
- Create: `app/src/main/res/layout/item_task_photo_list.xml`

**Interfaces:**
- Consumes: `TaskPhotoRepository.getPhotoCountForTask(long)`, `TaskPhotoRepository.getTodayTasksAvailableForPhoto(long, long)`
- Produces: `TaskPhotoListDialog(Context, long todayStartMs, long todayEndMs, TaskPhotoRepository, OnTaskPhotoClickListener)`

- [ ] **Step 1: 新建列表项布局 `item_task_photo_list.xml`（在 item_retroactive_task.xml 基础上增加张数显示）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="8dp"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:baselineAligned="false">

        <!-- 左侧象限主题色竖色条 -->
        <View
            android:id="@+id/v_task_photo_quadrant_bar"
            android:layout_width="6dp"
            android:layout_height="match_parent"
            android:background="@color/quadrant_urgent_important" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:padding="12dp">

            <ImageView
                android:id="@+id/iv_task_photo_icon"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:layout_marginEnd="12dp"
                android:background="@android:color/transparent"
                android:scaleType="fitCenter" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tv_task_photo_title"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="任务标题"
                    android:textAppearance="@style/TextAppearance.JustNow.Caption"
                    android:textStyle="bold"
                    android:textColor="@color/text_primary"
                    android:maxLines="1"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/tv_task_photo_hint"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="点击拍照"
                    android:textAppearance="@style/TextAppearance.JustNow.Caption"
                    android:textColor="@color/text_secondary"
                    android:layout_marginTop="2dp" />

            </LinearLayout>

            <!-- 张数指示 -->
            <TextView
                android:id="@+id/tv_photo_count"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="0/5"
                android:textAppearance="@style/TextAppearance.JustNow.Caption"
                android:textStyle="bold"
                android:textColor="@color/text_secondary"
                android:layout_marginStart="8dp" />

            <ImageView
                android:id="@+id/iv_camera_indicator"
                android:layout_width="28dp"
                android:layout_height="28dp"
                android:src="@android:drawable/ic_menu_camera"
                android:layout_marginStart="4dp"
                android:alpha="0.8"
                android:clickable="false"
                android:focusable="false" />

        </LinearLayout>

    </LinearLayout>

</androidx.cardview.widget.CardView>
```

- [ ] **Step 2: 复制并改造对话框布局 `dialog_task_photo_list.xml`**

```bash
cp app/src/main/res/layout/dialog_retroactive_list.xml app/src/main/res/layout/dialog_task_photo_list.xml
```

编辑 `dialog_task_photo_list.xml`，将标题 `@string/s_retroactive_list_title` 改为引用新 key `@string/s_task_photo_list_title`，RecyclerView id 改为 `rv_task_photo_list`。

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="20dp"
    android:background="@drawable/badge_bg">

    <RelativeLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginBottom="16dp">

        <TextView
            android:id="@+id/tv_task_photo_list_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/s_task_photo_list_title"
            android:textAppearance="@style/TextAppearance.JustNow.Title"
            android:textStyle="bold"
            android:textColor="@color/text_primary"
            android:layout_alignParentStart="true"
            android:layout_centerVertical="true" />

        <TextView
            android:id="@+id/btn_close_task_photo_list"
            android:layout_width="36dp"
            android:layout_height="36dp"
            android:text="✖"
            android:textAppearance="@style/TextAppearance.JustNow.Body"
            android:gravity="center"
            android:textColor="@color/text_secondary"
            android:layout_alignParentEnd="true"
            android:layout_centerVertical="true"
            android:clickable="true"
            android:focusable="true"
            android:background="?attr/selectableItemBackgroundBorderless" />

    </RelativeLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_task_photo_list"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

- [ ] **Step 3: 编写 TaskPhotoListDialog.java（改造自 RetroactivePhotoDialog）**

```java
package com.nearby.justnow.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;

import java.util.List;

/**
 * 通用任务拍照列表弹窗（当天已完成 + 进行中任务，每任务最多 5 张）
 */
public class TaskPhotoListDialog extends Dialog {

    public interface OnTaskPhotoClickListener {
        void onCapturePhoto(TaskEntity task);
    }

    private final TaskPhotoRepository mPhotoRepository;
    private final long mTodayStartMs;
    private final long mTodayEndMs;
    private final OnTaskPhotoClickListener mListener;
    private RecyclerView mRecyclerView;
    private TaskPhotoListAdapter mAdapter;

    public TaskPhotoListDialog(@NonNull Context context,
                               @NonNull TaskPhotoRepository photoRepository,
                               long todayStartMs, long todayEndMs,
                               @NonNull OnTaskPhotoClickListener listener) {
        super(context);
        mPhotoRepository = photoRepository;
        mTodayStartMs = todayStartMs;
        mTodayEndMs = todayEndMs;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_task_photo_list);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(lp);
        }

        setCanceledOnTouchOutside(true);

        mRecyclerView = findViewById(R.id.rv_task_photo_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new TaskPhotoListAdapter();
        mRecyclerView.setAdapter(mAdapter);

        findViewById(R.id.btn_close_task_photo_list).setOnClickListener(v -> dismiss());

        loadTasks();
    }

    private void loadTasks() {
        com.nearby.justnow.data.db.AppDatabase.execute(() -> {
            List<TaskEntity> tasks = mPhotoRepository.getTodayTasksAvailableForPhoto(
                mTodayStartMs, mTodayEndMs);
            mRecyclerView.post(() -> {
                mAdapter.setTasks(tasks);
                if (tasks.isEmpty()) {
                    android.widget.Toast.makeText(getContext(),
                        R.string.s_no_tasks_available_for_photo,
                        android.widget.Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            });
        });
    }

    private class TaskPhotoListAdapter extends RecyclerView.Adapter<TaskPhotoListViewHolder> {

        private List<TaskEntity> mTasks = new java.util.ArrayList<>();

        void setTasks(List<TaskEntity> tasks) {
            mTasks = tasks;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public TaskPhotoListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext())
                .inflate(R.layout.item_task_photo_list, parent, false);
            return new TaskPhotoListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskPhotoListViewHolder holder, int position) {
            TaskEntity item = mTasks.get(position);

            int accentColor;
            switch (item.quadrant) {
                case 0:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_important);
                    break;
                case 1:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_not_important);
                    break;
                case 2:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_important);
                    break;
                case 3:
                default:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_not_important);
                    break;
            }
            holder.vQuadrantBar.setBackgroundColor(accentColor);

            holder.ivIcon.setImageDrawable(null);
            if (item.iconName != null && !item.iconName.isEmpty()) {
                String resName = "ic_activity_" + item.iconName;
                int resId = getContext().getResources().getIdentifier(
                    resName, "drawable", getContext().getPackageName());
                if (resId != 0) {
                    holder.ivIcon.setImageResource(resId);
                }
            }

            holder.tvTitle.setText(item.content);

            // 查询张数
            com.nearby.justnow.data.db.AppDatabase.execute(() -> {
                int count = mPhotoRepository.getPhotoCountForTask(item.id);
                holder.tvPhotoCount.post(() -> {
                    holder.tvPhotoCount.setText(count + "/5");
                    if (count >= 5) {
                        holder.tvPhotoCount.setTextColor(ContextCompat.getColor(
                            getContext(), R.color.quadrant_urgent_important));
                    } else {
                        holder.tvPhotoCount.setTextColor(ContextCompat.getColor(
                            getContext(), R.color.text_secondary));
                    }
                });
            });

            holder.itemView.setOnClickListener(v -> {
                // 后台线程检查张数，避免主线程 Room 查询异常
                com.nearby.justnow.data.db.AppDatabase.execute(() -> {
                    boolean reached = mPhotoRepository.isPhotoLimitReached(item.id);
                    holder.itemView.post(() -> {
                        if (reached) {
                            android.widget.Toast.makeText(getContext(),
                                R.string.s_photo_limit_reached,
                                android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (mListener != null) {
                            mListener.onCapturePhoto(item);
                        }
                        dismiss();
                    });
                });
            });
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }
    }

    private static class TaskPhotoListViewHolder extends RecyclerView.ViewHolder {
        View vQuadrantBar;
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvPhotoCount;

        TaskPhotoListViewHolder(@NonNull View itemView) {
            super(itemView);
            vQuadrantBar = itemView.findViewById(R.id.v_task_photo_quadrant_bar);
            ivIcon = itemView.findViewById(R.id.iv_task_photo_icon);
            tvTitle = itemView.findViewById(R.id.tv_task_photo_title);
            tvPhotoCount = itemView.findViewById(R.id.tv_photo_count);
        }
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/layout/dialog_task_photo_list.xml \
        app/src/main/res/layout/item_task_photo_list.xml \
        app/src/main/java/com/nearby/justnow/ui/main/TaskPhotoListDialog.java
git commit -m "feat: 通用任务拍照列表弹窗 TaskPhotoListDialog

替换原 RetroactivePhotoDialog，数据源扩展为当天已完成+进行中任务，
每项显示已拍张数，满5张拒绝拍照。

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: RewardBarFragment 按钮改造 + 花瓣去重

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java`
- Modify: `app/src/main/res/layout/fragment_reward_bar.xml`

**Interfaces:**
- Consumes: `TaskPhotoListDialog`, `TaskPhotoRepository.getFirstPhotoPerTaskInRange(long, long)`, `TaskPhotoRepository.isPhotoLimitReached(long)`
- Modifies: `setupRetroactivePhotoButton()`, `refreshWeeklyFlowers()`, `showPhotoReminderDialog(TaskEntity)`, `startCameraForTask(long)`

- [ ] **Step 1: 修改 fragment_reward_bar.xml 按钮文案和 id**

```xml
<!-- 将 btn_retroactive_photo 改为 btn_take_photo，文案改为 s_take_photo -->
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_take_photo"
    style="@style/Widget.Material3.Button.OutlinedButton"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="end"
    android:layout_marginEnd="12dp"
    android:layout_marginBottom="8dp"
    android:minWidth="0dp"
    android:minHeight="0dp"
    android:paddingHorizontal="@dimen/main_bottom_bar_button_padding_horizontal"
    android:text="@string/s_take_photo"
    android:textAppearance="@style/TextAppearance.JustNow.Caption"
    android:visibility="visible"
    app:strokeColor="?attr/colorPrimary"
    app:strokeWidth="1.5dp"
    android:insetTop="0dp"
    android:insetBottom="0dp" />
```

- [ ] **Step 2: 改造 RewardBarFragment.java**

改动点：
1. `mBtnRetroactivePhoto` → `mBtnTakePhoto`，绑定 id `btn_take_photo`
2. `setupRetroactivePhotoButton()` → `setupTakePhotoButton()`，改为弹出 `TaskPhotoListDialog`
3. `showPhotoReminderDialog()` 中拍照完后弹出 `TaskPhotoListDialog`
4. `refreshWeeklyFlowers()` 中花瓣去重 + 数据源切到 `getFirstPhotoPerTaskInRange`
5. `startCameraForTask()` 中增加张数检查

关键代码段：

**setupTakePhotoButton():**
```java
private void setupTakePhotoButton() {
    if (mBtnTakePhoto == null) return;
    mBtnTakePhoto.setOnClickListener(v -> {
        long[] todayRange = getTodayRangeMs();
        TaskPhotoListDialog dialog = new TaskPhotoListDialog(
            requireContext(), mPhotoRepository,
            todayRange[0], todayRange[1],
            task -> startCameraForTask(task.id));
        dialog.show();
    });
}
```

**refreshWeeklyFlowers() 花瓣去重:**
```java
// 改用每任务首张查询
List<TaskPhotoWithTask> photos = mPhotoRepository.getFirstPhotoPerTaskInRange(monday, sundayEnd);
// 按 created_at 升序
Collections.sort(photos, (a, b) -> Long.compare(a.photo.createdAt, b.photo.createdAt));
// 后续花瓣累加逻辑不变（已经是每任务一条，无需额外去重）
```

**showPhotoReminderDialog() 调整:**
```java
public void showPhotoReminderDialog(TaskEntity task) {
    if (task == null) return;
    mCongratulationDialog = new com.nearby.justnow.ui.dialog.CongratulationDialog(
        requireContext(), task,
        new com.nearby.justnow.ui.dialog.CongratulationDialog.OnActionListener() {
            @Override
            public void onTakePhoto() {
                // 后台检查张数
                AppDatabase.execute(() -> {
                    if (mPhotoRepository.isPhotoLimitReached(task.id)) {
                        mBtnTakePhoto.post(() -> Toast.makeText(requireContext(),
                            R.string.s_photo_limit_reached, Toast.LENGTH_SHORT).show());
                        return;
                    }
                    mBtnTakePhoto.post(() -> startCameraForTask(task.id));
                });
            }

            @Override
            public void onSkip() {
                refreshWeeklyFlowers();
            }
        });
    mCongratulationDialog.setOnDismissListener(d -> {
        mCongratulationDialog = null;
        refreshWeeklyFlowers();
    });
    mCongratulationDialog.show();
}
```

**onActivityResult 拍照成功后弹出列表:**
在 `onActivityResult` 中照片保存成功后，`refreshWeeklyFlowers()` 调用前，弹出 `TaskPhotoListDialog`：

```java
// onActivityResult RESULT_OK 分支，照片保存后:
mBtnTakePhoto.post(() -> {
    Toast.makeText(requireContext(),
        R.string.s_photo_saved_album, Toast.LENGTH_SHORT).show();
    // 弹出任务列表供继续拍照
    long[] todayRange = getTodayRangeMs();
    new TaskPhotoListDialog(requireContext(), mPhotoRepository,
        todayRange[0], todayRange[1],
        t -> startCameraForTask(t.id)).show();
    refreshWeeklyFlowers();
});
```

**新增辅助方法:**
```java
private long[] getTodayRangeMs() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long start = cal.getTimeInMillis();
    cal.set(Calendar.HOUR_OF_DAY, 23);
    cal.set(Calendar.MINUTE, 59);
    cal.set(Calendar.SECOND, 59);
    cal.set(Calendar.MILLISECOND, 999);
    long end = cal.getTimeInMillis();
    return new long[]{start, end};
}
```

- [ ] **Step 3: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/fragment_reward_bar.xml \
        app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java
git commit -m "feat: 拍照按钮通用化 + 花瓣每任务每周只计首张

- 补拍按钮改为通用拍照按钮，始终可见
- 花瓣刷新改用 getFirstPhotoPerTaskInRange 去重
- 拍照成功后弹出 TaskPhotoListDialog 供继续拍照

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: CongratulationDialog 满 5 张 Toast

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/dialog/CongratulationDialog.java`

**Interfaces:**
- Consumes: `TaskPhotoRepository.isPhotoLimitReached(long)`
- Modifies: 构造器增加 `TaskPhotoRepository` 参数，onTakePhoto 回调触发前先检查张数

- [ ] **Step 1: 修改构造器，增加满 5 张时的 Toast**

```java
// 构造器新增 photoRepository 参数
private final TaskPhotoRepository mPhotoRepository;

public CongratulationDialog(@NonNull Context context, @NonNull TaskEntity task,
                            @NonNull TaskPhotoRepository photoRepository,
                            @NonNull OnActionListener listener) {
    super(context);
    this.mTask = task;
    this.mPhotoRepository = photoRepository;
    this.mListener = listener;
}

// btnAction onClick 改为后台检查:
btnAction.setOnClickListener(v -> {
    com.nearby.justnow.data.db.AppDatabase.execute(() -> {
        boolean reached = mPhotoRepository.isPhotoLimitReached(mTask.id);
        v.post(() -> {
            if (reached) {
                android.widget.Toast.makeText(getContext(),
                    R.string.s_photo_limit_reached, android.widget.Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
            mListener.onTakePhoto();
            dismiss();
        });
    });
});
```

- [ ] **Step 2: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/ui/dialog/CongratulationDialog.java
git commit -m "feat: 完成弹窗拍照前检查照片张数限制

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: MainFragment 回调调整

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**Interfaces:**
- Consumes: `CongratulationDialog` 新构造器 `(Context, TaskEntity, TaskPhotoRepository, OnActionListener)`
- Modifies: `showPhotoReminderDialog` 调用构造器时传入 `mPhotoRepository`

- [ ] **Step 1: 在 MainFragment 中找到 `showPhotoReminderDialog` 调用点，更新 `CongratulationDialog` 构造**

需要找到类似的代码：
```java
// 当前代码（RewardBarFragment 内部）:
mCongratulationDialog = new com.nearby.justnow.ui.dialog.CongratulationDialog(
    requireContext(), task,
    new com.nearby.justnow.ui.dialog.CongratulationDialog.OnActionListener() { ... });

// 改为:
mCongratulationDialog = new com.nearby.justnow.ui.dialog.CongratulationDialog(
    requireContext(), task, mPhotoRepository,
    new com.nearby.justnow.ui.dialog.CongratulationDialog.OnActionListener() { ... });
```

> 注：`CongratulationDialog` 实际在 `RewardBarFragment.showPhotoReminderDialog()` 中构造，该处已有 `mPhotoRepository`，改动在本 Task 4 中已覆盖。MainFragment 本身如果也直接调用 `CongratulationDialog`，同样加参即可，否则无需额外改动。

- [ ] **Step 2: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
git commit -m "fix: CongratulationDialog 构造器适配新增 photoRepository 参数

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: TimeCapsuleWallActivity 数据源切换 + ViewPager2 全屏

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`
- Modify: `app/src/main/res/layout/dialog_photo_detail.xml`

**Interfaces:**
- Consumes: `TaskPhotoRepository.getFirstPhotoPerTaskInRange(long, long)`, `TaskPhotoRepository.getPhotosForTask(long)`
- Modifies: `loadPhotos()` 数据源, `showFullScreenPhoto(String)` → `showFullScreenPhotos(long taskId, int startIndex)`

- [ ] **Step 1: 修改 `loadPhotos()` 数据源**

```java
// 将 getPhotosWithTaskInRange → getFirstPhotoPerTaskInRange
List<TaskPhotoWithTask> list = mPhotoRepository.getFirstPhotoPerTaskInRange(
    mRangeStartMs, mRangeEndMs);
```

- [ ] **Step 2: 修改 `countPetalsInRange()` 数据源**

```java
private int countPetalsInRange(long startMs, long endMs) {
    List<TaskPhotoWithTask> photos = mPhotoRepository.getFirstPhotoPerTaskInRange(startMs, endMs);
    int total = 0;
    for (TaskPhotoWithTask p : photos) {
        total += QUADRANT_PETALS[Math.min(p.taskQuadrant, 3)];
    }
    return total;
}
```

- [ ] **Step 3: 替换 `dialog_photo_detail.xml` 为 ViewPager2 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#E0000000"
    android:clickable="true"
    android:focusable="true">

    <!-- ViewPager2 用于左右划动浏览多张照片 -->
    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/vp_fullscreen_photos"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 右上角关闭按钮 -->
    <TextView
        android:id="@+id/tv_detail_close"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_gravity="top|end"
        android:layout_marginTop="20dp"
        android:layout_marginEnd="20dp"
        android:text="✖"
        android:textSize="22sp"
        android:textColor="@android:color/white"
        android:gravity="center"
        android:clickable="true"
        android:focusable="true"
        android:background="?attr/selectableItemBackgroundBorderless" />

</FrameLayout>
```

- [ ] **Step 4: 替换 `showFullScreenPhoto` 为 `showFullScreenPhotos`**

```java
private void showFullScreenPhotos(TaskEntity task, int startIndex) {
    Dialog detailDialog = new Dialog(this, R.style.ThemeOverlay_JustNow_FullscreenDialog);
    detailDialog.setContentView(R.layout.dialog_photo_detail);

    ViewPager2 viewPager = detailDialog.findViewById(R.id.vp_fullscreen_photos);
    TextView tvClose = detailDialog.findViewById(R.id.tv_detail_close);

    AppDatabase.execute(() -> {
        List<TaskPhotoEntity> photos = mPhotoRepository.getPhotosForTask(task.id);
        if (photos.isEmpty()) {
            detailDialog.dismiss();
            return;
        }
        viewPager.post(() -> {
            PhotoPagerAdapter adapter = new PhotoPagerAdapter(photos);
            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(Math.min(startIndex, photos.size() - 1), false);
        });
    });

    tvClose.setOnClickListener(v -> detailDialog.dismiss());
    detailDialog.show();
}

private class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoPageViewHolder> {

    private final List<TaskPhotoEntity> mPhotos;

    PhotoPagerAdapter(List<TaskPhotoEntity> photos) {
        mPhotos = photos;
    }

    @NonNull
    @Override
    public PhotoPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView iv = new ImageView(TimeCapsuleWallActivity.this);
        iv.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return new PhotoPageViewHolder(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoPageViewHolder holder, int position) {
        String uri = mPhotos.get(position).photoUri;
        AppDatabase.execute(() -> {
            try {
                Bitmap bitmap = decodeUriToBitmap(TimeCapsuleWallActivity.this,
                    Uri.parse(uri), 1200, 1200);
                if (bitmap != null) {
                    holder.imageView.post(() -> holder.imageView.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
        });
        setupZoomableImageView(holder.imageView);
    }

    @Override
    public int getItemCount() {
        return mPhotos.size();
    }

    class PhotoPageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        PhotoPageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = (ImageView) itemView;
        }
    }
}
```

- [ ] **Step 5: 修改 PhotoWallAdapter 中卡片点击事件**

```java
// 将原来的 showFullScreenPhoto(item.photo.photoUri) 改为:
int photoIndex = 0; // 首张
holder.ivThumbnail.setOnClickListener(v -> showFullScreenPhotos(taskForPosition(position), photoIndex));
```

由于 `PhotoWallAdapter` 内部需要 `TaskEntity` 找到任务所有照片，需从 `TaskPhotoWithTask` 重建 `TaskEntity` 或额外查询。简化方案：将 `TaskPhotoWithTask` 的 `taskId` 传入 `showFullScreenPhotos`，内部查询任务对象。

改为传递 `taskId`:
```java
// 在绑定中取出 taskId 并在点击时传入:
// 需要修改 ViewHolder 存储 taskId，或在 onBindViewHolder 中处理
final long taskId = item.photo.taskId;
holder.ivThumbnail.setOnClickListener(v -> {
    // 查询该任务首张索引
    AppDatabase.execute(() -> {
        List<TaskPhotoEntity> photos = mPhotoRepository.getPhotosForTask(taskId);
        if (!photos.isEmpty()) {
            long firstPhotoId = photos.get(0).id;
            int startIndex = 0;
            for (int i = 0; i < photos.size(); i++) {
                if (photos.get(i).id == item.photo.id) {
                    startIndex = i;
                    break;
                }
            }
            final int idx = startIndex;
            holder.ivThumbnail.post(() -> {
                // 用 taskId 调新方法
                showFullScreenPhotosByTaskId(taskId, idx);
            });
        }
    });
});
```

新增方法：
```java
private void showFullScreenPhotosByTaskId(long taskId, int startIndex) {
    Dialog detailDialog = new Dialog(this, R.style.ThemeOverlay_JustNow_FullscreenDialog);
    detailDialog.setContentView(R.layout.dialog_photo_detail);

    ViewPager2 viewPager = detailDialog.findViewById(R.id.vp_fullscreen_photos);
    TextView tvClose = detailDialog.findViewById(R.id.tv_detail_close);

    AppDatabase.execute(() -> {
        List<TaskPhotoEntity> photos = mPhotoRepository.getPhotosForTask(taskId);
        if (photos.isEmpty()) { detailDialog.dismiss(); return; }
        viewPager.post(() -> {
            PhotoPagerAdapter adapter = new PhotoPagerAdapter(photos);
            viewPager.setAdapter(adapter);
            viewPager.setCurrentItem(Math.min(startIndex, photos.size() - 1), false);
        });
    });

    tvClose.setOnClickListener(v -> detailDialog.dismiss());
    detailDialog.show();
}
```

- [ ] **Step 6: 编译验证**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 提交**

```bash
git add app/src/main/res/layout/dialog_photo_detail.xml \
        app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java
git commit -m "feat: 成果墙按任务去重 + ViewPager2 全屏左右划动

- 数据源切换为 getFirstPhotoPerTaskInRange
- 点击卡片进入 ViewPager2 全屏浏览该任务所有照片
- 保留缩放手势

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: 字符串资源四语翻译

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`

- [ ] **Step 1: 在四个 strings.xml 末尾添加新字符串**

`values/strings.xml` (英文，默认):
```xml
<string name="s_take_photo">📷 Photo</string>
<string name="s_task_photo_list_title">📷 Take a Photo</string>
<string name="s_no_tasks_available_for_photo">No tasks available for photos</string>
<string name="s_photo_limit_reached">This task already has 5 photos</string>
```

`values-zh-rCN/strings.xml` (简体中文):
```xml
<string name="s_take_photo">📷 拍照</string>
<string name="s_task_photo_list_title">📷 拍照记录成果</string>
<string name="s_no_tasks_available_for_photo">暂无可拍照的任务</string>
<string name="s_photo_limit_reached">该任务已拍满 5 张照片</string>
```

`values-zh-rTW/strings.xml` (繁体中文-台湾):
```xml
<string name="s_take_photo">📷 拍照</string>
<string name="s_task_photo_list_title">📷 拍照記錄成果</string>
<string name="s_no_tasks_available_for_photo">暫無可拍照的任務</string>
<string name="s_photo_limit_reached">該任務已拍滿 5 張照片</string>
```

`values-zh-rHK/strings.xml` (繁体中文-香港):
```xml
<string name="s_take_photo">📷 拍照</string>
<string name="s_task_photo_list_title">📷 拍照記錄成果</string>
<string name="s_no_tasks_available_for_photo">暫無可拍照嘅任務</string>
<string name="s_photo_limit_reached">該任務已拍滿 5 張相片</string>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        app/src/main/res/values-zh-rHK/strings.xml
git commit -m "feat: 任务拍照流程四语字符串资源

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: 全量编译 + 收尾清理

- [ ] **Step 1: 全量编译**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 删除旧文件（RetroactivePhotoDialog + 旧布局）**

```bash
rm app/src/main/java/com/nearby/justnow/ui/main/RetroactivePhotoDialog.java
rm app/src/main/res/layout/dialog_retroactive_list.xml
rm app/src/main/res/layout/item_retroactive_task.xml
```

- [ ] **Step 3: 再次编译确认无引用残留**

```bash
cd /mnt/androiddev/StudioProjects/JustNow && ./gradlew :app:assembleDebug 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 更新模块进度文档**

在 `modules/tablet-flower-rewards_progress.md` 头部插入：
```
### 2026-07-21 — 任务多照片支持与完成前拍照实现完成
- 每任务最多 5 张照片，右下角补拍按钮改为通用拍照按钮
- 花瓣只计每任务每周首张
- 成果墙按任务去重 + ViewPager2 左右划动
- RetroactivePhotoDialog → TaskPhotoListDialog
```

同步更新 `progress.md`。

- [ ] **Step 5: 最终提交**

```bash
git add -A
git commit -m "chore: 移除 RetroactivePhotoDialog 旧文件 + 更新进度文档

Co-Authored-By: Claude <noreply@anthropic.com>"
```
