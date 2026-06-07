package com.nearby.justnow.ui.taskinput;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDestination;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.databinding.ActivityTaskInputBinding;
import com.nearby.justnow.ui.base.ViewModelFactory;

/**
 * 任务录入 Activity — 宿主输入→编辑→象限三步向导
 */
public class TaskInputActivity extends AppCompatActivity {

    /** 加载已有任务直接编辑（从 ReminderDetail 或 CapturePicker 入口 1） */
    public static final String EXTRA_LOAD_TASK_ID = "extra_load_task_id";

    /** 进入编辑页后自动打开 APP 跳转 sheet */
    public static final String EXTRA_OPEN_APP_ACTION_SHEET = "extra_open_app_action_sheet";

    /** 新建任务时进入编辑页后自动打开 APP 跳转 sheet（仅入口 2） */
    public static final String EXTRA_DRAFT_OPEN_APP_ACTION_SHEET = "extra_draft_open_app_action_sheet";

    /** 预填 APP 跳转项：Intent URI（Intent.toUri / parseUri）。配合 OPEN sheet 用 */
    public static final String EXTRA_PREFILL_APP_ACTION_URI = "extra_prefill_app_action_uri";

    /** 预填 APP 跳转项：hint 描述（短标题） */
    public static final String EXTRA_PREFILL_APP_ACTION_HINT = "extra_prefill_app_action_hint";

    /** 新建任务预填：标题 */
    public static final String EXTRA_DRAFT_TASK_TITLE = "extra_draft_task_title";

    /** 新建任务预填：标签名（按名查找或新建） */
    public static final String EXTRA_DRAFT_TASK_TAG_NAME = "extra_draft_task_tag_name";

    /** 新建任务预填：Markdown 正文（笔记流用） */
    public static final String EXTRA_DRAFT_TASK_MARKDOWN = "extra_draft_task_markdown";

    private ActivityTaskInputBinding mBinding;
    private NavController mNavController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityTaskInputBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

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

        setSupportActionBar(mBinding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.s_add_task);
        }

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            mNavController = navHost.getNavController();
            mNavController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> updateTitle(destination));
        }

        mBinding.toolbar.setNavigationOnClickListener(v -> navigateBackOrFinish());

        applyCaptureExtras();
    }

    /** 来自 CapturePickerActivity 的预填 extras：草稿/Sheet 预填，并跳过录入首屏 */
    private void applyCaptureExtras() {
        if (getIntent() == null) return;
        String prefillUri = getIntent().getStringExtra(EXTRA_PREFILL_APP_ACTION_URI);
        String prefillHint = getIntent().getStringExtra(EXTRA_PREFILL_APP_ACTION_HINT);
        boolean openSheet = getIntent().getBooleanExtra(EXTRA_OPEN_APP_ACTION_SHEET, false)
                || getIntent().getBooleanExtra(EXTRA_DRAFT_OPEN_APP_ACTION_SHEET, false);
        long loadTaskId = getIntent().getLongExtra(EXTRA_LOAD_TASK_ID, -1L);
        String draftTitle = getIntent().getStringExtra(EXTRA_DRAFT_TASK_TITLE);
        String draftTag = getIntent().getStringExtra(EXTRA_DRAFT_TASK_TAG_NAME);
        String draftMarkdown = getIntent().getStringExtra(EXTRA_DRAFT_TASK_MARKDOWN);

        boolean hasCaptureExtras = loadTaskId > 0
                || draftTitle != null
                || draftTag != null
                || draftMarkdown != null
                || prefillUri != null;
        if (!hasCaptureExtras) return;

        JustNowApplication app = (JustNowApplication) getApplication();
        TaskInputViewModel viewModel = new ViewModelProvider(this, new ViewModelFactory(app))
                .get(TaskInputViewModel.class);

        if (loadTaskId > 0) {
            // 入口 1：先加载已有任务，加载完再注入 prefill 并跳转
            viewModel.loadTaskForEdit(loadTaskId);
            viewModel.stagePendingAppActionPrefill(prefillUri, prefillHint, openSheet);
            // postDelayed 等异步加载落到 ViewModel（沿用 TaskInputFragment 既有模式）
            mBinding.getRoot().postDelayed(() -> {
                if (mNavController != null) {
                    mNavController.navigate(R.id.action_taskInputFragment_to_taskEditFragment);
                }
            }, 150);
        } else {
            // 入口 2 / 3：直接灌入草稿态
            viewModel.applyDraftPrefill(draftTitle, draftTag, draftMarkdown);
            viewModel.stagePendingAppActionPrefill(prefillUri, prefillHint, openSheet);
            mBinding.getRoot().post(() -> {
                if (mNavController != null) {
                    mNavController.navigate(R.id.action_taskInputFragment_to_taskEditFragment);
                }
            });
        }
    }

    private void updateTitle(NavDestination destination) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;
        CharSequence label = destination.getLabel();
        actionBar.setTitle(label != null ? label : getString(R.string.s_add_task));
    }

    private boolean navigateBackOrFinish() {
        if (mNavController != null && mNavController.getCurrentDestination() != null
            && mNavController.getCurrentDestination().getId()
            != mNavController.getGraph().getStartDestinationId()
            && mNavController.popBackStack()) {
            return true;
        }
        finish();
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navigateBackOrFinish();
    }
}
