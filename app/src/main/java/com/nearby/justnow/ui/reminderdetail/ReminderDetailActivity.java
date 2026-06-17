package com.nearby.justnow.ui.reminderdetail;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.databinding.ActivityReminderDetailBinding;
import com.nearby.justnow.ui.base.BaseTaskViewModel;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.ui.main.ShortCompletionDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务详情页 — 独立 Activity，供 Widget / 通知 / APP 内部统一跳转。
 * 支持两种模式：
 * - MODE_EXECUTE（默认）：执行中任务的完成/停止/安排操作
 * - MODE_VIEW：查看模式，提供编辑/删除按钮
 */
public class ReminderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_EXECUTE = "execute";
    public static final String MODE_VIEW = "view";

    private ActivityReminderDetailBinding mBinding;
    private ReminderDetailViewModel mViewModel;
    private ChecklistAdapter mChecklistAdapter;
    private AppActionAdapter mAppActionAdapter;
    private long mTaskId;
    private String mMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityReminderDetailBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBarLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        JustNowApplication app = (JustNowApplication) getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
            .get(ReminderDetailViewModel.class);

        mTaskId = getIntent().getLongExtra("task_id", -1);
        mMode = getIntent().getStringExtra(EXTRA_MODE);
        if (mMode == null) mMode = MODE_EXECUTE;

        // 设置完成前确认回调
        mViewModel.setPreCompleteConfirmCallback((taskId, confirmType, onConfirmed) -> {
            if (BaseTaskViewModel.CONFIRM_TYPE_CHECKLIST_STATE.equals(confirmType)) {
                showChecklistStateConfirmDialog(taskId, onConfirmed);
            } else {
                onConfirmed.run();
            }
        });

        if (mTaskId > 0) {
            loadAndRender();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadAndRender() {
        mViewModel.loadTask(mTaskId, task -> {
            if (task == null || mBinding == null) return;
            renderTask(task);
        }, schedule -> { /* 后续使用 */ });
    }

    private void renderTask(TaskEntity task) {
        Resources res = getResources();

        // Toolbar 标题
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(task.content);
        }

        boolean hasMarkdown = task.detailMarkdown != null && !task.detailMarkdown.isEmpty();
        boolean hasChecklist = "checklist".equals(task.detailModuleType);
        boolean hasAppActions = "app_actions".equals(task.detailModuleType);
        boolean hasNoteShares = "note_shares".equals(task.detailModuleType);
        boolean hasAnyContent = hasMarkdown || hasChecklist || hasAppActions || hasNoteShares;

        if (!hasAnyContent) {
            mBinding.tvNoContent.setVisibility(View.VISIBLE);
            // MODE_VIEW 模式即使无详情内容也要显示底部编辑栏
            if (!MODE_VIEW.equals(mMode)) {
                return;
            }
        }

        // Markdown 渲染（简单文本渲染，后续接入 Markwon）
        if (hasMarkdown) {
            mBinding.tvMarkdown.setVisibility(View.VISIBLE);
            mBinding.tvMarkdown.setText(task.detailMarkdown);
        }

        // todo 清单
        if (hasChecklist) {
            mBinding.tvChecklistLabel.setVisibility(View.VISIBLE);
            mBinding.rvChecklist.setVisibility(View.VISIBLE);
            mBinding.rvChecklist.setLayoutManager(new LinearLayoutManager(this));
            mChecklistAdapter = new ChecklistAdapter(mViewModel, mTaskId);
            mBinding.rvChecklist.setAdapter(mChecklistAdapter);
            mViewModel.loadChecklistItems(mTaskId);
            mViewModel.getChecklistItems().observe(this, items -> {
                mChecklistAdapter.setItems(items);
            });
        }

        // APP 跳转
        if (hasAppActions) {
            mBinding.tvAppActionsLabel.setVisibility(View.VISIBLE);
            mBinding.rvAppActions.setVisibility(View.VISIBLE);
            mBinding.rvAppActions.setLayoutManager(new LinearLayoutManager(this));
            mViewModel.loadAppActionsAsync(mTaskId, actions -> {
                mAppActionAdapter = new AppActionAdapter(mViewModel, getPackageManager(), ReminderDetailActivity.this, actions);
                mBinding.rvAppActions.setAdapter(mAppActionAdapter);
            });
        }

        // 笔记分享
        if (hasNoteShares) {
            mBinding.tvNoteSharesLabel.setVisibility(View.VISIBLE);
            mBinding.rvNoteShares.setVisibility(View.VISIBLE);
            mBinding.rvNoteShares.setLayoutManager(new LinearLayoutManager(this));
            mViewModel.loadNoteSharesAsync(mTaskId, shares -> {
                NoteShareAdapter adapter = new NoteShareAdapter(shares, this::launchDeepLink);
                mBinding.rvNoteShares.setAdapter(adapter);
            });
        }

        // 底部按钮
        if (MODE_VIEW.equals(mMode)) {
            mBinding.dividerBottom.setVisibility(View.VISIBLE);
            mBinding.llBottomButtons.setVisibility(View.VISIBLE);
            setupViewBottomButtons(task);
        } else if (mViewModel.isExecuting()) {
            mBinding.dividerBottom.setVisibility(View.VISIBLE);
            mBinding.llBottomButtons.setVisibility(View.VISIBLE);
            setupBottomButtons(task);
        }
    }

    private void setupBottomButtons(TaskEntity task) {
        Button btnCancel = mBinding.btnCancel;
        Button btnSecondary = mBinding.btnActionSecondary;
        Button btnPrimary = mBinding.btnActionPrimary;

        boolean isFocus = mViewModel.isFocusTask();
        boolean hasSchedule = mViewModel.hasSchedule();

        btnCancel.setOnClickListener(v -> finish());

        if (!isFocus) {
            // 琐碎任务
            btnSecondary.setVisibility(View.VISIBLE);
            btnSecondary.setText(getString(R.string.s_no_longer_needed));
            btnSecondary.setOnClickListener(v -> {
                mViewModel.archiveTask(() -> runOnUiThread(() -> finish()));
            });

            btnPrimary.setText(getString(R.string.s_complete));
            btnPrimary.setOnClickListener(v -> {
                mViewModel.completeRunningTask(false, () -> runOnUiThread(() -> finish()));
            });
        } else {
            // 专注任务
            if (hasSchedule) {
                btnSecondary.setVisibility(View.VISIBLE);
                btnSecondary.setText(getString(R.string.s_complete_and_stop_schedule));
                btnSecondary.setOnClickListener(v -> handleFocusCompletion(task, true));
            }

            btnPrimary.setText(getString(hasSchedule ? R.string.s_complete_once : R.string.s_complete));
            btnPrimary.setOnClickListener(v -> handleFocusCompletion(task, false));
        }
    }

    /**
     * 入口1（stopSchedule=false）/ 入口2（stopSchedule=true）：详情页专注任务完成路径。
     * 实际耗时 &lt; 15min 时弹 ShortCompletionDialog；否则走原完成流程。
     */
    private void handleFocusCompletion(TaskEntity task, boolean stopSchedule) {
        int elapsedMinutes = task.executingStartMs > 0
            ? (int) ((System.currentTimeMillis() - task.executingStartMs) / 60000)
            : Integer.MAX_VALUE;
        boolean isShort = elapsedMinutes < BaseTaskViewModel.SHORT_DURATION_THRESHOLD_MINUTES;
        if (!isShort) {
            mViewModel.completeRunningTask(stopSchedule, () -> runOnUiThread(() -> finish()));
            return;
        }
        int entry = stopSchedule
            ? ShortCompletionDialog.ENTRY_COMPLETE_AND_STOP_SCHEDULE
            : ShortCompletionDialog.ENTRY_COMPLETE_ONCE;
        boolean hasSchedule = mViewModel.hasSchedule();
        ShortCompletionDialog.show(this, task.content, entry, hasSchedule,
            new ShortCompletionDialog.Callback() {
                @Override
                public void onCancel() {
                    mViewModel.cancelShortCompletion();
                }

                @Override
                public void onDirectComplete() {
                    mViewModel.shortCompleteDirect(task.id, stopSchedule, () ->
                        runOnUiThread(() -> finish()));
                }

                @Override
                public void onConvertToChore() {
                    boolean stop = stopSchedule || hasSchedule;
                    mViewModel.shortCompleteAndConvertToChore(task.id, stop, () ->
                        runOnUiThread(() -> finish()));
                }
            });
    }

    private void setupViewBottomButtons(TaskEntity task) {
        Button btnCancel = mBinding.btnCancel;
        Button btnSecondary = mBinding.btnActionSecondary;
        Button btnPrimary = mBinding.btnActionPrimary;

        // 编辑按钮
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(getString(R.string.s_edit));
        btnSecondary.setOnClickListener(v -> {
            Intent intent = new Intent(ReminderDetailActivity.this,
                    com.nearby.justnow.ui.taskinput.TaskInputActivity.class);
            intent.putExtra(com.nearby.justnow.ui.taskinput.TaskInputActivity.EXTRA_EDIT_TASK_ID, task.id);
            startActivity(intent);
        });

        // 删除按钮
        btnPrimary.setText(getString(R.string.s_delete));
        btnPrimary.setOnClickListener(v -> showDeleteConfirmDialog(task));

        // 关闭按钮
        btnCancel.setOnClickListener(v -> finish());
    }

    private void showDeleteConfirmDialog(TaskEntity task) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.s_delete)
                .setMessage(R.string.s_confirm_delete_task)
                .setPositiveButton(R.string.s_delete, (d, w) -> {
                    mViewModel.deleteTask(task.id);
                    finish();
                })
                .setNegativeButton(R.string.s_cancel, null)
                .show();
    }

    private void showChecklistStateConfirmDialog(long taskId, Runnable onConfirmed) {
        new android.app.AlertDialog.Builder(this)
            .setTitle(R.string.s_complete_task)
            .setMessage(R.string.s_checklist_state_changed)
            .setPositiveButton(R.string.s_save, (d, w) -> {
                onConfirmed.run();
            })
            .setNegativeButton(R.string.s_not_save, (d, w) -> {
                mViewModel.resetChecklistStateWithCallback(taskId, onConfirmed);
            })
            .setNeutralButton(R.string.s_cancel, null)
            .show();
    }

    // ==================== Checklist Adapter ====================

    private static class ChecklistAdapter extends RecyclerView.Adapter<ChecklistAdapter.Holder> {

        private final ReminderDetailViewModel mViewModel;
        private final long mTaskId;
        private List<TaskChecklistItem> mItems = new ArrayList<>();

        ChecklistAdapter(ReminderDetailViewModel viewModel, long taskId) {
            mViewModel = viewModel;
            mTaskId = taskId;
        }

        void setItems(List<TaskChecklistItem> items) {
            mItems = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_checklist, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int pos) {
            TaskChecklistItem item = mItems.get(pos);
            Resources res = holder.itemView.getResources();

            // 勾选框图标
            String checkbox;
            if (item.crossedOut) {
                checkbox = "☐";
            } else if (item.checked) {
                checkbox = "☑";
            } else {
                checkbox = "☐";
            }
            holder.checkbox.setText(checkbox);

            // 文本
            SpannableString spannable = new SpannableString(item.content);
            if (item.checked || item.crossedOut) {
                spannable.setSpan(new StrikethroughSpan(), 0, spannable.length(), 0);
            }
            holder.text.setText(spannable);

            int textColor = (item.checked || item.crossedOut)
                ? res.getColor(R.color.text_tertiary, null)
                : res.getColor(R.color.text_primary, null);
            holder.text.setTextColor(textColor);

            // 勾选/划掉互斥交互
            holder.itemView.setOnClickListener(v -> {
                if (!item.crossedOut) {
                    mViewModel.toggleChecked(item);
                    mViewModel.loadChecklistItems(mTaskId);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                mViewModel.toggleCrossedOut(item);
                mViewModel.loadChecklistItems(mTaskId);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            android.widget.TextView checkbox;
            android.widget.TextView text;

            Holder(View v) {
                super(v);
                checkbox = v.findViewById(R.id.tv_checkbox);
                text = v.findViewById(R.id.tv_item_text);
            }
        }
    }

    // ==================== App Action Adapter ====================

    private static class AppActionAdapter extends RecyclerView.Adapter<AppActionAdapter.Holder> {

        private final ReminderDetailViewModel mViewModel;
        private final PackageManager mPackageManager;
        private final Context mContext;
        private List<TaskAppAction> mItems = new ArrayList<>();

        AppActionAdapter(ReminderDetailViewModel viewModel, PackageManager packageManager,
                         Context context, List<TaskAppAction> items) {
            mViewModel = viewModel;
            mPackageManager = packageManager;
            mContext = context;
            mItems = items != null ? items : new ArrayList<>();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_app_action, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int pos) {
            TaskAppAction action = mItems.get(pos);
            Resources res = holder.itemView.getResources();
            PackageManager pm = mPackageManager;

            // App 图标
            try {
                holder.icon.setImageDrawable(pm.getApplicationIcon(action.packageName));
            } catch (PackageManager.NameNotFoundException e) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }

            // 文本 fallback：hint → app label → packageName
            String text;
            if (action.hint != null && !action.hint.isEmpty()) {
                text = action.hint;
            } else {
                String appLabel = null;
                try {
                    appLabel = pm.getApplicationLabel(
                        pm.getApplicationInfo(action.packageName, 0)).toString();
                } catch (PackageManager.NameNotFoundException ignored) {}
                text = (appLabel != null && !appLabel.isEmpty()) ? appLabel : action.packageName;
            }
            // 解析 deepLink 中的 userId，动态添加分身标识
            if (action.deepLink != null && action.deepLink.contains("launch_user_id=")) {
                text += getString(R.string.s_app_clone_suffix);
            }
            holder.text.setText(text);

            boolean completed = mViewModel.isAppActionCompleted(action.id);
            if (completed) {
                SpannableString spannable = new SpannableString(text);
                spannable.setSpan(new StrikethroughSpan(), 0, spannable.length(), 0);
                holder.text.setText(spannable);
                holder.text.setTextColor(res.getColor(R.color.text_tertiary, null));
                holder.itemView.setOnClickListener(null);
                holder.itemView.setClickable(false);
            } else {
                holder.text.setTextColor(res.getColor(R.color.text_primary, null));
                holder.itemView.setClickable(true);
                holder.itemView.setOnClickListener(v -> {
                    // 1. 先准备 Intent
                    Intent intent = pm.getLaunchIntentForPackage(action.packageName);
                    if (action.deepLink != null && !action.deepLink.isEmpty()) {
                        try {
                            intent = Intent.parseUri(action.deepLink, 0);
                        } catch (Exception ignored) {}
                    }

                    // 2. 立即标记已完成（不依赖 rebind）
                    mViewModel.markAppActionCompleted(action.id);

                    // 3. 同步更新 view 状态：文字置灰 + 取消监听 + 禁用点击
                    holder.text.setTextColor(res.getColor(R.color.text_tertiary, null));
                    holder.itemView.setOnClickListener(null);
                    holder.itemView.setClickable(false);

                    // 4. 通知 RecyclerView 数据已变
                    int adapterPos = holder.getBindingAdapterPosition();
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(adapterPos);
                    }

                    // 5. 最后跳转
                    if (intent != null) {
                        try {
                            mContext.startActivity(intent);
                        } catch (Exception e) {
                            android.widget.Toast.makeText(mContext,
                                R.string.s_capture_launch_failed,
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        android.widget.Toast.makeText(mContext,
                            R.string.s_capture_launch_failed,
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            android.widget.ImageView icon;
            android.widget.TextView text;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.iv_app_icon);
                text = v.findViewById(R.id.tv_app_action_text);
            }
        }
    }

    // ==================== Note Share Adapter ====================

    private void launchDeepLink(String deepLink) {
        if (deepLink == null || deepLink.isEmpty()) {
            android.widget.Toast.makeText(this, R.string.s_capture_launch_failed,
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = com.nearby.justnow.ui.base.UriParser.parse(deepLink);
            if (intent != null) {
                startActivity(intent);
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(this, R.string.s_capture_launch_failed,
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private static class NoteShareAdapter extends RecyclerView.Adapter<NoteShareAdapter.Holder> {

        private final List<com.nearby.justnow.data.entity.TaskNoteShare> mItems;
        private final java.util.function.Consumer<String> mOnOpen;

        NoteShareAdapter(List<com.nearby.justnow.data.entity.TaskNoteShare> items,
                         java.util.function.Consumer<String> onOpen) {
            mItems = items != null ? items : new ArrayList<>();
            mOnOpen = onOpen;
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_detail_note_share, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int pos) {
            com.nearby.justnow.data.entity.TaskNoteShare share = mItems.get(pos);

            String displayTitle = share.hint != null && !share.hint.isEmpty()
                ? share.hint : (share.deepLink != null ? share.deepLink : "");
            holder.tvHint.setText(displayTitle);

            String linkDisplay = share.deepLink != null ? share.deepLink : "";
            holder.tvLink.setText(linkDisplay);
            holder.tvLink.setVisibility(linkDisplay.isEmpty() ? View.GONE : View.VISIBLE);

            holder.itemView.setOnClickListener(v -> {
                if (mOnOpen != null) mOnOpen.accept(share.deepLink);
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            android.widget.TextView tvHint, tvLink;

            Holder(View v) {
                super(v);
                tvHint = v.findViewById(R.id.tv_hint);
                tvLink = v.findViewById(R.id.tv_link);
            }
        }
    }
}
