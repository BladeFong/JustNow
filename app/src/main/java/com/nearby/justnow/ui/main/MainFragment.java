package com.nearby.justnow.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.ble.notification.sdk.BleNotificationSDK;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.observer.DataChangeDispatcher;
import com.nearby.justnow.data.store.CutoffTimeStore;
import com.nearby.justnow.databinding.FragmentMainBinding;
import com.nearby.justnow.databinding.FragmentMainPage0Binding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.BaseTaskViewModel;
import com.nearby.justnow.ui.base.TagChipHelper;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.ui.period.PeriodTextResolver;

import com.nearby.justnow.util.PermissionHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 主界面 — 黄金比例双栏 + 智能展示引擎 + 标签展开 + ViewPager2 多页。
 * <p>
 * Page 0 = 现有主界面内容（MainPage0Fragment），
 * Page 1 = 四象限全任务概览（QuadrantOverviewFragment）。
 */
public class MainFragment extends BaseFragment<FragmentMainBinding>
        implements TaskDialogFactory.Callback {

    private MainViewModel mViewModel;
    private TaskAdapter mAdapter;
    private boolean mTimeTickReceiverRegistered = false;
    private boolean mIsInActivePeriod = false;
    private boolean mWidgetConfigureExactAlarmSettingsOpened = false;
    private boolean mWidgetConfigureExactAlarmDialogShowing = false;
    private final Set<Long> mPendingFilterTagIds = new HashSet<>();
    /** 安排入口前置双权限：等待通知权限授予后续跳的任务 id。 */
    private long mPendingScheduleTaskId = -1;
    private ActivityResultLauncher<String> mNotificationPermissionLauncher;
    private boolean mHasPromptedExactAlarmOnResume = false;
    private final BroadcastReceiver mTimeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mViewModel.refreshTimeState();
        }
    };

    /** Page 0 的 ViewBinding，由 childFragment MainPage0Fragment 提供。 */
    private FragmentMainPage0Binding mPage0Binding;
    private TaskDialogFactory mTaskDialogFactory;

    @Override
    protected FragmentMainBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentMainBinding.inflate(inflater, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 必须在 STARTED 之前注册 ActivityResultLauncher，故放在 onCreate。
        mNotificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                long taskId = mPendingScheduleTaskId;
                mPendingScheduleTaskId = -1;
                if (taskId < 0) return;
                if (granted && PermissionHelper.hasExactAlarmPermission(requireContext())) {
                    doNavigateToSchedule(taskId);
                } else {
                    showSchedulePermissionDialog();
                }
            });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
            .get(MainViewModel.class);

        setHasOptionsMenu(true);

        // 平板端多用户切换（独立模块 UserSwitcherManager）
        UserSwitcherManager userSwitcher = new UserSwitcherManager(
            (androidx.appcompat.app.AppCompatActivity) requireActivity());
        userSwitcher.setOnUserChangedListener(this::reloadActivity);
        userSwitcher.setup();

        // 装配 ViewPager2
        ViewPager2 viewPager = getBinding().viewPager;
        viewPager.setOffscreenPageLimit(1);
        viewPager.setAdapter(new MainPagerAdapter(this));

        // 返回键拦截：Page 1 时回到 Page 0
        OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                viewPager.setCurrentItem(0);
            }
        };
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                backCallback.setEnabled(position != 0);
            }
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);

        // Page 0 由 FragmentStateAdapter 懒加载创建，post 等待创建完成后再初始化内容
        viewPager.post(() -> {
            MainPage0Fragment page0 = (MainPage0Fragment) getChildFragmentManager()
                .findFragmentByTag("f0");
            if (page0 == null) {
                viewPager.setCurrentItem(0, false);
                page0 = (MainPage0Fragment) getChildFragmentManager().findFragmentByTag("f0");
            }
            if (page0 != null) {
                setupPage0Content(app, page0);
            }
        });
    }

    private void setupPage0Content(JustNowApplication app, MainPage0Fragment page0) {
        if (page0 == null) return;
        mPage0Binding = page0.getBinding();
        if (mPage0Binding == null) return;

        // 平板横竖屏自适应：先调整 rightPanel 方向再计算最大任务数
        adjustRightPanelForOrientation();

        mTaskDialogFactory = new TaskDialogFactory(requireContext(), this);

        setupAdapter();
        observeDisplay();
        observePriorityConfig();
        setupFilterOverlay();
        setupFab();
        setupPriorityStatus();
        calcMaxDisplayItems();
        consumePendingWidgetConfigureExactAlarmPrompt();
        consumePendingWidgetTaskClick();

        // 通知 RewardBarFragment 主题色应用 + 注入拍照按钮
        RewardBarFragment rewardBar = getRewardBarFragment(page0);
        if (rewardBar != null) {
            rewardBar.setTakePhotoButton(mPage0Binding.btnTakePhoto);
            rewardBar.applyThemeColor();
        }
        applyThemeColor();
    }

    /** 从 Page0 中获取 RewardBarFragment */
    @Nullable
    private RewardBarFragment getRewardBarFragment(MainPage0Fragment page0) {
        if (page0 == null || !isAdded()) return null;
        return (RewardBarFragment) page0.getChildFragmentManager()
            .findFragmentByTag("reward_bar");
    }

    // ---- ViewPager2 Adapter ----

    /** ViewPager2 的 FragmentStateAdapter：Page 0 = 主界面，Page 1 = 四象限概览。 */
    private static class MainPagerAdapter extends FragmentStateAdapter {
        MainPagerAdapter(Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public androidx.fragment.app.Fragment createFragment(int position) {
            if (position == 0) {
                return new MainPage0Fragment();
            } else {
                return new QuadrantOverviewFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    // ---- 选项菜单 ----

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main, menu);
        // 内置图标仅平板可见
        if (!getResources().getBoolean(R.bool.is_tablet)) {
            MenuItem previewIcons = menu.findItem(R.id.action_preview_icons);
            if (previewIcons != null) previewIcons.setVisible(false);
        }
        // 桌面设备同步仅手机可见
        if (getResources().getBoolean(R.bool.is_tablet)) {
            MenuItem bleItem = menu.findItem(R.id.action_ble_device_manager);
            if (bleItem != null) bleItem.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_period_config) {
            startActivity(new Intent(requireContext(),
                com.nearby.justnow.ui.period.PeriodConfigActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_tag_manage) {
            startActivity(new Intent(requireContext(),
                com.nearby.justnow.ui.tagmanage.TagManageActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_display_policy) {
            startActivity(new Intent(requireContext(),
                com.nearby.justnow.ui.displaypolicy.DisplayPolicyActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_stats) {
            startActivity(new Intent(requireContext(),
                com.nearby.justnow.ui.stats.StatsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_preview_icons) {
            showIconPreviewDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_theme_color) {
            showThemeColorDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_ble_device_manager) {
            BleNotificationSDK.Companion.getInstance().openDeviceManager();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAdapter() {
        if (mPage0Binding == null) return;
        mAdapter = new TaskAdapter();
        int spanCount = getResources().getInteger(R.integer.task_grid_span_count);
        mPage0Binding.rvTaskList.setLayoutManager(
            new androidx.recyclerview.widget.GridLayoutManager(requireContext(), spanCount) {
                @Override
                public boolean canScrollVertically() {
                    return false;
                }
            });
        mPage0Binding.rvTaskList.setAdapter(mAdapter);

        // 条目动画
        androidx.recyclerview.widget.DefaultItemAnimator animator =
            new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setAddDuration(300);
        animator.setRemoveDuration(300);
        mPage0Binding.rvTaskList.setItemAnimator(animator);

        // 点击任务
        mAdapter.setOnTaskClickListener(this::onTaskClicked);

        // 点击标签 → 过滤同标签任务
        mAdapter.setOnTagClickListener(this::onTagClicked);

        // 长按标签 → 触发多选标签筛选覆盖层
        mAdapter.setOnTagLongClickListener(this::onRequestMultiTagFilter);
        mPage0Binding.timelineView.setOnTimelineItemClickListener(this::onTimelineItemClicked);

        // 统一任务点击入口观察者：按未执行 / 执行中两种状态分发
        mViewModel.getTaskStartEvent().observe(getViewLifecycleOwner(),
            taskId -> mTaskDialogFactory.handleTaskStart(taskId));

        mViewModel.getTaskCompleteToDetailEvent().observe(getViewLifecycleOwner(), taskId -> {
            Intent intent = new Intent(requireContext(),
                com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
            intent.putExtra("task_id", taskId);
            startActivity(intent);
        });

        mViewModel.getOnlyTitleTaskCompleteEvent().observe(getViewLifecycleOwner(),
            taskId -> mTaskDialogFactory.handleOnlyTitleTaskComplete(taskId));

        // 任务完成时的实时拍照提醒 — 委托给 RewardBarFragment
        mViewModel.getShowPhotoPromptEvent().observe(getViewLifecycleOwner(), task -> {
            // 通过当前可见的 Page0 获取 RewardBarFragment
            MainPage0Fragment page0 = (MainPage0Fragment) getChildFragmentManager()
                .findFragmentByTag("f0");
            if (page0 == null) return;
            RewardBarFragment rbf = (RewardBarFragment) page0.getChildFragmentManager()
                .findFragmentByTag("reward_bar");
            if (rbf != null) {
                rbf.showPhotoReminderDialog(task);
            }
        });

        // 左侧时间线已安排任务点击事件（独立对话框）
        mViewModel.getTimelineScheduledTaskClickEvent().observe(getViewLifecycleOwner(),
            taskId -> mTaskDialogFactory.handleTimelineScheduledTaskClick(taskId));

        // 完成前确认回调
        mViewModel.setPreCompleteConfirmCallback((taskId, confirmType, onConfirmed) -> {
            if (BaseTaskViewModel.CONFIRM_TYPE_CHECKLIST_STATE.equals(confirmType)) {
                mTaskDialogFactory.showChecklistStateConfirmDialog(taskId, onConfirmed);
            } else {
                onConfirmed.run();
            }
        });
    }

    private void setupFab() {
        mPage0Binding.btnAddTask.setOnClickListener(v ->
            startActivity(new Intent(requireContext(),
                com.nearby.justnow.ui.taskinput.TaskInputActivity.class))
        );
    }

    /** 平板横竖屏自适应：横屏时 rightPanel 水平排列（任务列表左侧 + 花朵栏右侧），竖屏垂直排列。 */
    private void adjustRightPanelForOrientation() {
        if (mPage0Binding == null) return;
        boolean isLandscape = getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        LinearLayout rightPanel = mPage0Binding.rightPanel;
        FrameLayout taskListContainer = mPage0Binding.flTaskListContainer;
        View rewardBarContainer = mPage0Binding.getRoot().findViewById(R.id.fragment_reward_bar_container);

        if (isLandscape) {
            rightPanel.setOrientation(LinearLayout.HORIZONTAL);
            // 任务列表占据左侧，weight=1 撑满
            LinearLayout.LayoutParams listLp = (LinearLayout.LayoutParams) taskListContainer.getLayoutParams();
            listLp.width = 0;
            listLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            listLp.weight = 1.0f;
            taskListContainer.setLayoutParams(listLp);
            // 花朵栏容器占据右侧，紧凑宽度
            if (rewardBarContainer != null) {
                LinearLayout.LayoutParams rewardLp = (LinearLayout.LayoutParams) rewardBarContainer.getLayoutParams();
                rewardLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                rewardLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                rewardBarContainer.setLayoutParams(rewardLp);
            }
        } else {
            rightPanel.setOrientation(LinearLayout.VERTICAL);
            // 任务列表占据上方，weight=1 撑满
            LinearLayout.LayoutParams listLp = (LinearLayout.LayoutParams) taskListContainer.getLayoutParams();
            listLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            listLp.height = 0;
            listLp.weight = 1.0f;
            taskListContainer.setLayoutParams(listLp);
            // 花朵栏容器位于底部，紧凑高度
            if (rewardBarContainer != null) {
                LinearLayout.LayoutParams rewardLp = (LinearLayout.LayoutParams) rewardBarContainer.getLayoutParams();
                rewardLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                rewardLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                rewardBarContainer.setLayoutParams(rewardLp);
            }
        }
    }

    /** 优先标签状态行点击：切换临时关闭/恢复 */
    private void setupPriorityStatus() {
        mPage0Binding.llPriorityStatus.setOnClickListener(v -> mViewModel.togglePrioritySuppress());
    }

    /** 根据 RecyclerView 实际高度和 dimens 资源计算可显示的任务数 */
    private int mLastMaxDisplayCount = 0;

    private void calcMaxDisplayItems() {
        mPage0Binding.rvTaskList.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int rvHeight = mPage0Binding.rvTaskList.getHeight();
            if (rvHeight <= 0) return;

            int spanCount = getResources().getInteger(R.integer.task_grid_span_count);
            int itemTotalPx = getResources().getDimensionPixelSize(R.dimen.task_item_total_height);
            int rowsPerCol = rvHeight / itemTotalPx;
            int count = rowsPerCol * spanCount;
            if (count > 0 && count != mLastMaxDisplayCount) {
                mLastMaxDisplayCount = count;
                mViewModel.setMaxDisplayItems(count);
            }
        });
    }

    /** 观察引擎结果，驱动 UI */
    private void observeDisplay() {
        mViewModel.getDisplayResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null || mPage0Binding == null) return;

            boolean empty = result.items == null || result.items.isEmpty();
            mPage0Binding.llEmptyHint.setVisibility(empty ? View.VISIBLE : View.GONE);
            mPage0Binding.rvTaskList.setVisibility(empty ? View.GONE : View.VISIBLE);

            mAdapter.setItems(result.items);
            mAdapter.setFilterTagId(mViewModel.getFilterTagId());
            mAdapter.setPriorityTagIds(result.priorityTagIds);

            // 多标签筛选态
            boolean isMultiFilter = mViewModel.isMultiFilterActive();
            mAdapter.setMultiFilterActive(isMultiFilter);
            mPage0Binding.btnClearMultiFilter.setVisibility(isMultiFilter ? View.VISIBLE : View.GONE);
            mIsInActivePeriod = result.periodStatus != null && result.periodStatus.isInPeriod();

            // 引擎异常提示（固定在界面上）
            mPage0Binding.tvEngineWarning.setVisibility(result.engineFailed ? View.VISIBLE : View.GONE);

            // 优先标签生效状态行
            Set<Long> priorityTagIds = result.priorityTagIds;
            boolean hasPriorityTags = priorityTagIds != null && !priorityTagIds.isEmpty();
            boolean suppressed = mViewModel.isPrioritySuppressed();

            if (!hasPriorityTags) {
                mPage0Binding.llPriorityStatus.setVisibility(View.GONE);
            } else if (suppressed) {
                mPage0Binding.llPriorityStatus.setVisibility(View.VISIBLE);
                mPage0Binding.llPriorityStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.priority_status_paused_bg));
                mPage0Binding.tvPriorityStatusText.setText(R.string.s_priority_tag_paused);
                mPage0Binding.tvPriorityStatusText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary));
            } else {
                mPage0Binding.llPriorityStatus.setVisibility(View.VISIBLE);
                mPage0Binding.llPriorityStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.period_edit_chip_background));
                String groupName = PeriodTextResolver.getGroupName(getResources(), result.activeGroupType);
                String text = groupName + " · " + getString(R.string.s_priority_tag_active);
                mPage0Binding.tvPriorityStatusText.setText(text);
                mPage0Binding.tvPriorityStatusText.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_primary));
            }

            // 时间线
            mPage0Binding.hourColumn.setPeriods(result.timelinePeriods);
            mPage0Binding.hourColumn.setCutoffMinute(
                result.periodStatus != null && result.periodStatus.isCutoff
                    ? result.periodStatus.endMinute : -1);
            mPage0Binding.timelineView.setPeriods(result.timelinePeriods);
            mPage0Binding.timelineView.setActivePeriods(result.periods);
            mPage0Binding.timelineView.setCutoffMinute(
                result.periodStatus != null && result.periodStatus.isCutoff
                    ? result.periodStatus.endMinute : -1);
            mPage0Binding.timelineView.setHasRunningTask(result.executingTasks != null
                && !result.executingTasks.isEmpty());
            mPage0Binding.timelineView.setTimelineItems(result.timelineItems);

            updateBottomPeriodBar(result);

            // 对齐小时数列与时刻刻度
            mPage0Binding.timelineContent.post(() -> {
                mPage0Binding.hourColumn.setTopOffset(mPage0Binding.timelineContent.getPaddingTop());
                mPage0Binding.timelineView.invalidate();
            });

            // 标签过滤提示（仅单标签筛选时提示）
            if (mViewModel.isFiltering() && !isMultiFilter) {
                Snackbar.make(requireView(), R.string.s_filtered_by_tag, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBottomPeriodBar(MainViewModel.EngineResult result) {
        if (result.isUpcoming) {
            mPage0Binding.tvBottomPeriodTitle.setText(result.showRestHint
                ? R.string.s_resting_status : R.string.s_tomorrow);
            mPage0Binding.tvBottomPeriodSubtitle.setText(result.isTomorrow
                ? R.string.s_tomorrow_period_hint : R.string.s_not_started_hint);
            mPage0Binding.tvCutoffArrow.setVisibility(View.GONE);
            setupBottomPeriodBarClick(null);
            return;
        }

        mPage0Binding.tvBottomPeriodTitle.setText(result.periodName);
        mPage0Binding.tvBottomPeriodSubtitle.setText(result.remainingText);

        boolean isInPeriod = result.periodStatus != null && result.periodStatus.isInPeriod();
        mPage0Binding.tvCutoffArrow.setVisibility(isInPeriod ? View.VISIBLE : View.GONE);
        if (isInPeriod && result.periodStatus.isCutoff) {
            mPage0Binding.tvBottomPeriodSubtitle.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary));
        } else {
            mPage0Binding.tvBottomPeriodSubtitle.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_primary));
        }

        setupBottomPeriodBarClick(isInPeriod ? result.periodStatus : null);
    }

    /** 设置底部栏点击事件：时段内可点击弹出截止时间选择器 */
    private void setupBottomPeriodBarClick(@Nullable TimeRemainingCalculator.PeriodStatus status) {
        if (status == null || !status.isInPeriod()) {
            mPage0Binding.bottomPeriodClickable.setOnClickListener(null);
            mPage0Binding.bottomPeriodClickable.setClickable(false);
            mPage0Binding.bottomPeriodClickable.setForeground(null);
            return;
        }
        mPage0Binding.bottomPeriodClickable.setClickable(true);
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        mPage0Binding.bottomPeriodClickable.setForeground(
            ContextCompat.getDrawable(requireContext(), tv.resourceId));
        mPage0Binding.bottomPeriodClickable.setOnClickListener(v -> {
            int periodEndMinute = status.period.endMinute;
            CutoffTimePickerDialog.show(requireContext(), v, periodEndMinute, selectedMinute -> {
                CutoffTimeStore.setCutoffEndMinute(requireContext(), selectedMinute);
                DataChangeDispatcher.notifyTaskDataChanged();
                mViewModel.refreshTimeState();
            }, () -> {
                CutoffTimeStore.clearCutoffEndMinute(requireContext());
                DataChangeDispatcher.notifyTaskDataChanged();
                mViewModel.refreshTimeState();
            });
        });
    }

    /** 观察优先标签配置变化 */
    private void observePriorityConfig() {
        mViewModel.getWorkTimePriorityEnabledLiveData().observe(getViewLifecycleOwner(), enabled -> {
            requireActivity().invalidateOptionsMenu();
        });
    }

    // ---- 多选标签筛选覆盖层 ----

    private void setupFilterOverlay() {
        // 取消按钮
        mPage0Binding.btnFilterCancel.setOnClickListener(v -> {
            hideFilterOverlay();
        });

        // 确定按钮
        mPage0Binding.btnFilterConfirm.setOnClickListener(v -> {
            com.google.android.material.chip.ChipGroup chipGroup = mPage0Binding.cgFilterTags;
            Set<Long> selectedIds = new HashSet<>();
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                Chip chip = (Chip) chipGroup.getChildAt(i);
                if (chip.isChecked()) {
                    TagEntity tag = (TagEntity) chip.getTag();
                    if (tag != null) selectedIds.add(tag.id);
                }
            }
            hideFilterOverlay();
            mViewModel.setMultiFilterTags(selectedIds);
        });

        // 清除筛选按钮
        mPage0Binding.btnClearMultiFilter.setOnClickListener(v -> {
            mViewModel.clearMultiFilter();
        });

        // 点击半透背景关闭
        mPage0Binding.overlayTagFilter.setOnClickListener(v -> {
            hideFilterOverlay();
        });

        // 加载全量标签（按使用频次排序）
        mViewModel.getTopTags(Integer.MAX_VALUE).observe(getViewLifecycleOwner(), tags -> {
            populateFilterChips(tags);
        });
    }

    private void populateFilterChips(List<TagEntity> tags) {
        com.google.android.material.chip.ChipGroup chipGroup = mPage0Binding.cgFilterTags;
        chipGroup.removeAllViews();
        if (tags == null) {
            updateFilterConfirmState();
            return;
        }

        for (TagEntity tag : tags) {
            Chip chip = TagChipHelper.createSelectableChip(chipGroup.getContext(), tag);
            chip.setText(getString(R.string.s_tag_name_format, com.nearby.justnow.util.TagLocalizer.getLocalizedName(requireContext(), tag.name)));
            boolean checked = mPendingFilterTagIds.contains(tag.id);
            chip.setChecked(checked);
            TagChipHelper.updateChipState(chip, checked);
            chip.setOnClickListener(v -> {
                if (chip.isChecked()) {
                    mPendingFilterTagIds.add(tag.id);
                } else {
                    mPendingFilterTagIds.remove(tag.id);
                }
                TagChipHelper.updateChipState(chip, chip.isChecked());
                updateFilterConfirmState();
            });
            chipGroup.addView(chip);
        }
        updateFilterConfirmState();
    }

    /** 长按标签 → 弹出多选标签筛选覆盖层 */
    private void onRequestMultiTagFilter(TagEntity tag) {
        mPendingFilterTagIds.clear();
        if (tag != null) {
            mPendingFilterTagIds.add(tag.id);
        }
        preselectFilterChips();
        updateFilterConfirmState();
        mPage0Binding.overlayTagFilter.setVisibility(View.VISIBLE);
    }

    private void hideFilterOverlay() {
        mPage0Binding.overlayTagFilter.setVisibility(View.GONE);
        mPendingFilterTagIds.clear();
    }

    private void preselectFilterChips() {
        com.google.android.material.chip.ChipGroup chipGroup = mPage0Binding.cgFilterTags;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            TagEntity tag = (TagEntity) chip.getTag();
            boolean checked = tag != null && mPendingFilterTagIds.contains(tag.id);
            chip.setChecked(checked);
            TagChipHelper.updateChipState(chip, checked);
        }
    }

    private void updateFilterConfirmState() {
        com.google.android.material.chip.ChipGroup chipGroup = mPage0Binding.cgFilterTags;
        boolean hasChecked = false;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.isChecked()) {
                hasChecked = true;
                break;
            }
        }
        mPage0Binding.btnFilterConfirm.setEnabled(hasChecked);
    }

    // ---- 交互 ----

    private void onTaskClicked(DisplayItem item) {
        // 执行中专注任务只在左侧时间线可点击
        if (item.task.focusMinutes > 0
            && item.task.executingStartMs > 0
            && item.task.executingEndMs == 0) {
            return;
        }
        mViewModel.resolveAndHandleTaskClick(item.task.id);
    }

    /** 标签点击：已筛选则取消，未筛选则筛选 */
    private void onTagClicked(TagEntity tag) {
        if (mViewModel.isFiltering() && mViewModel.getFilterTagId() == tag.id) {
            mViewModel.clearFilterTag();
        } else {
            mViewModel.setFilterTag(tag.id);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        registerTimeTickReceiver();
        mViewModel.refreshTimeState();
        // 仅 App 退后台再回来时重置筛选和优先标签临时关闭状态，App 内 Fragment 导航不重置
        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        if (app.consumeBackgroundFlag()) {
            mViewModel.resetFiltersAndPriority();
        }
        handleWidgetConfigureExactAlarmResume();
        consumePendingWidgetConfigureExactAlarmPrompt();
        consumePendingWidgetTaskClick();
        checkExactAlarmPermissionOnResume();
    }

    @Override
    public void onPause() {
        unregisterTimeTickReceiver();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        unregisterTimeTickReceiver();
        super.onDestroyView();
    }

    private void registerTimeTickReceiver() {
        if (mTimeTickReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        requireContext().registerReceiver(mTimeTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        mTimeTickReceiverRegistered = true;
    }

    private void unregisterTimeTickReceiver() {
        if (!mTimeTickReceiverRegistered) return;
        requireContext().unregisterReceiver(mTimeTickReceiver);
        mTimeTickReceiverRegistered = false;
    }

    public void consumePendingWidgetTaskClick() {
        if (mViewModel == null || !(requireActivity() instanceof MainActivity)) return;
        long taskId = ((MainActivity) requireActivity()).consumePendingWidgetTaskId();
        if (taskId > 0) {
            mViewModel.resolveAndHandleTaskClick(taskId);
        }
    }

    public void consumePendingWidgetConfigureExactAlarmPrompt() {
        if (!(requireActivity() instanceof MainActivity)) return;
        if (!((MainActivity) requireActivity()).consumePendingWidgetConfigureExactAlarmPrompt()) return;
        handleWidgetConfigureExactAlarmResume();
    }

    private void handleWidgetConfigureExactAlarmResume() {
        if (!(requireActivity() instanceof MainActivity)) return;
        if (!((MainActivity) requireActivity()).isWidgetConfigureExactAlarmFlowActive()) return;
        if (PermissionHelper.hasExactAlarmPermission(requireContext())) {
            ((MainActivity) requireActivity()).finishWidgetConfigureExactAlarmFlow(true);
            return;
        }
        if (mWidgetConfigureExactAlarmSettingsOpened) {
            ((MainActivity) requireActivity()).finishWidgetConfigureExactAlarmFlow(false);
            return;
        }
        showWidgetConfigureExactAlarmDialog();
    }

    private void checkExactAlarmPermissionOnResume() {
        if (requireActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) requireActivity();
            if (mainActivity.isWidgetConfigureExactAlarmFlowActive()) {
                return; // 优先让 widget flow 弹出专属对话框
            }
        }
        
        if (mHasPromptedExactAlarmOnResume) return;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!PermissionHelper.hasExactAlarmPermission(requireContext())) {
                mHasPromptedExactAlarmOnResume = true;
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.s_permission_required)
                    .setMessage(R.string.s_exact_alarm_permission_message)
                    .setPositiveButton(R.string.s_go_to_settings, (d, w) -> {
                        startActivity(PermissionHelper.buildExactAlarmSettingsIntent(requireContext()));
                    })
                    .setNegativeButton(R.string.s_cancel, null)
                    .show();
            }
        }
    }

    private void onTimelineItemClicked(TimelineItem item) {
        if (item.running) {
            mViewModel.resolveAndHandleTaskClick(item.taskId);
            return;
        }
        mViewModel.loadActiveSchedule(item.taskId, schedule -> {
            if (mTaskDialogFactory.isScheduleActionable(schedule)) {
                mViewModel.onTimelineScheduledTaskClick(item.taskId);
            } else {
                mViewModel.resolveAndHandleTaskClick(item.taskId);
            }
        });
    }

    @Override
    public void navigateToSchedule(long taskId) {
        boolean hasAlarm = PermissionHelper.hasExactAlarmPermission(requireContext());
        boolean hasNotify = PermissionHelper.hasNotificationPermission(requireContext());
        if (hasAlarm && hasNotify) {
            doNavigateToSchedule(taskId);
            return;
        }
        mPendingScheduleTaskId = taskId;
        // 通知权限可走系统弹窗；闹钟权限 Android 12+ 只能跳设置页
        if (!hasNotify && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            mNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            mPendingScheduleTaskId = -1;
            showSchedulePermissionDialog();
        }
    }

    private void doNavigateToSchedule(long taskId) {
        Intent intent = new Intent(requireContext(),
            com.nearby.justnow.ui.taskschedule.TaskScheduleActivity.class);
        intent.putExtra("task_id", taskId);
        startActivity(intent);
    }

    private void showSchedulePermissionDialog() {
        boolean hasAlarm = PermissionHelper.hasExactAlarmPermission(requireContext());
        boolean hasNotify = PermissionHelper.hasNotificationPermission(requireContext());
        StringBuilder msg = new StringBuilder();
        if (!hasAlarm) msg.append(getString(R.string.s_exact_alarm_permission_message));
        if (!hasNotify) {
            if (msg.length() > 0) msg.append("\n\n");
            msg.append(getString(R.string.s_notification_permission_message));
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.s_permission_required)
            .setMessage(msg.toString())
            .setPositiveButton(R.string.s_go_to_settings, (d, w) -> {
                if (!hasAlarm && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    startActivity(PermissionHelper.buildExactAlarmSettingsIntent(requireContext()));
                } else {
                    startActivity(PermissionHelper.buildAppDetailsIntent(requireContext()));
                }
            })
            .setNegativeButton(R.string.s_cancel, null)
            .show();
    }

    private void showWidgetConfigureExactAlarmDialog() {
        if (mWidgetConfigureExactAlarmDialogShowing) return;
        mWidgetConfigureExactAlarmDialogShowing = true;
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.s_permission_required)
            .setMessage(R.string.s_exact_alarm_permission_message)
            .setPositiveButton(R.string.s_go_to_settings, (d, w) -> {
                mWidgetConfigureExactAlarmDialogShowing = false;
                mWidgetConfigureExactAlarmSettingsOpened = true;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    startActivity(PermissionHelper.buildExactAlarmSettingsIntent(requireContext()));
                } else {
                    startActivity(PermissionHelper.buildAppDetailsIntent(requireContext()));
                }
            })
            .setNegativeButton(R.string.s_cancel, (d, w) -> {
                mWidgetConfigureExactAlarmDialogShowing = false;
                ((MainActivity) requireActivity()).finishWidgetConfigureExactAlarmFlow(false);
            })
            .setOnCancelListener(d -> {
                mWidgetConfigureExactAlarmDialogShowing = false;
                ((MainActivity) requireActivity()).finishWidgetConfigureExactAlarmFlow(false);
            })
            .show();
    }

    private void showIconPreviewDialog() {
        android.view.View dialogView = android.view.LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_icon_preview, null);
        
        class PreviewItem {
            final int resId;
            final String label;
            PreviewItem(int resId, String label) { this.resId = resId; this.label = label; }
        }
        java.util.List<PreviewItem> items = java.util.Arrays.asList(
            new PreviewItem(R.drawable.ic_activity_blocks, "玩具"),
            new PreviewItem(R.drawable.ic_activity_book, "阅读"),
            new PreviewItem(R.drawable.ic_activity_palette, "美术"),
            new PreviewItem(R.drawable.ic_activity_music, "音乐"),
            new PreviewItem(R.drawable.ic_activity_ball, "运动"),
            new PreviewItem(R.drawable.ic_activity_game_puzzle, "桌游"),
            new PreviewItem(R.drawable.ic_activity_craft, "手工"),
            new PreviewItem(R.drawable.ic_activity_animation, "屏幕"),
            new PreviewItem(R.drawable.ic_activity_study, "学习"),
            new PreviewItem(R.drawable.ic_activity_chores, "家务")
        );

        androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rv_preview);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 5));
        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @androidx.annotation.NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
                android.view.View cell = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_task_icon_selector, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(cell) {};
            }

            @Override
            public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                PreviewItem item = items.get(position);
                android.widget.ImageView iv = holder.itemView.findViewById(R.id.iv_icon);
                android.widget.TextView tv = holder.itemView.findViewById(R.id.tv_icon_label);
                iv.setImageResource(item.resId);
                tv.setText(item.label);
            }

            @Override
            public int getItemCount() { return items.size(); }
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .show();
    }

    /** 用户切换后重建 Activity，确保所有组件主题色和数据正确刷新。 */
    private void reloadActivity() {
        requireActivity().recreate();
    }

    public static int getDefaultThemeColor(Context context) {
        boolean isTablet = context.getResources().getBoolean(R.bool.is_tablet);
        if (isTablet) {
            return Color.parseColor("#FF4081"); // 平板模式默认粉色
        } else {
            return Color.parseColor("#1A73E8"); // 非平板模式默认添加任务按钮的蓝色
        }
    }

    /** 从 SP 获取当前主题色 int 值，供需要 int 色值的程序化着色使用（XML 应优先用 ?attr/colorPrimary）。 */
    public static int getGlobalThemeColor(Context context) {
        long userId = ((JustNowApplication) context.getApplicationContext()).getCurrentUserId();
        android.content.SharedPreferences sp = com.nearby.justnow.data.store.UserPrefs.getPrefs(
            context.getApplicationContext(), userId, "capsule_settings");
        if (sp.contains("theme_color")) {
            int type = sp.getInt("theme_color", 0);
            return type == 1 ? Color.parseColor("#FF4081") : Color.parseColor("#1A73E8");
        }
        // 未曾设置过：按设备类型取默认值
        boolean isTablet = context.getResources().getBoolean(R.bool.is_tablet);
        return isTablet ? Color.parseColor("#FF4081") : Color.parseColor("#1A73E8");
    }

    /** 存储主题类型 ID（0=蓝，1=粉），供 Activity#onCreate 前 setTheme() 读取。 */
    public static void setGlobalThemeColor(Context context, int color) {
        long userId = ((JustNowApplication) context.getApplicationContext()).getCurrentUserId();
        android.content.SharedPreferences sp = com.nearby.justnow.data.store.UserPrefs.getPrefs(
            context.getApplicationContext(), userId, "capsule_settings");
        int type = (color == Color.parseColor("#FF4081")) ? 1 : 0;
        sp.edit().putInt("theme_color", type).apply();
    }

    /** 根据 SP 中的主题类型返回应设置的主题 style 资源 ID。平板首次使用时默认粉色。 */
    public static int resolveThemeStyle(Context context) {
        long userId = ((JustNowApplication) context.getApplicationContext()).getCurrentUserId();
        android.content.SharedPreferences sp = com.nearby.justnow.data.store.UserPrefs.getPrefs(
            context.getApplicationContext(), userId, "capsule_settings");
        if (sp.contains("theme_color")) {
            int type = sp.getInt("theme_color", 0);
            return type == 1 ? R.style.Theme_JustNow_Pink : R.style.Theme_JustNow;
        }
        // 未曾设置过：按设备类型取默认值
        boolean isTablet = context.getResources().getBoolean(R.bool.is_tablet);
        return isTablet ? R.style.Theme_JustNow_Pink : R.style.Theme_JustNow;
    }

    private void showThemeColorDialog() {
        int currentColor = getGlobalThemeColor(requireContext());
        int defaultBlue = Color.parseColor("#1A73E8");
        int defaultPink = Color.parseColor("#FF4081");

        int checkedItem = (currentColor == defaultPink) ? 1 : 0;
        String[] items = new String[]{"🔵 活力蓝", "🌸 童趣粉"};

        final int[] tempSelectedColor = new int[]{currentColor};

        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择主题色")
            .setSingleChoiceItems(items, checkedItem, (d, which) -> {
                tempSelectedColor[0] = (which == 0) ? defaultBlue : defaultPink;
            })
            .setPositiveButton("确定", (d, which) -> {
                setGlobalThemeColor(requireContext(), tempSelectedColor[0]);
                // 不重建 Activity，只刷新受影响的组件颜色
                ((MainActivity) requireActivity()).refreshChromeColors();
                applyThemeColor();
                MainPage0Fragment page0 = (MainPage0Fragment) getChildFragmentManager()
                    .findFragmentByTag("f0");
                if (page0 != null) {
                    RewardBarFragment rewardBar = (RewardBarFragment) page0
                        .getChildFragmentManager().findFragmentByTag("reward_bar");
                    if (rewardBar != null) {
                        rewardBar.applyThemeColor();
                    }
                }
            })
            .setNegativeButton("取消", null)
            .create();

        dialog.show();
        // 对话框按钮文字色由主题的 colorPrimary 自动着色，无需手动设置
    }

    private void applyThemeColor() {
        if (!isAdded()) return;
        int themeColor = getGlobalThemeColor(requireContext());

        // 添加任务按钮
        if (mPage0Binding != null && mPage0Binding.btnAddTask != null) {
            mPage0Binding.btnAddTask.setBackgroundTintList(ColorStateList.valueOf(themeColor));
        }

        // 刷新状态栏和标题栏 Chrome 颜色
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).refreshChromeColors();
        }
    }

    // ================================================================
    //  TaskDialogFactory.Callback 实现
    // ================================================================

    @Override
    public int getThemeColor() {
        return getGlobalThemeColor(requireContext());
    }

    @Override
    public boolean isInActivePeriod() {
        return mIsInActivePeriod;
    }

    @Override
    public void startTaskNow(long taskId, Consumer<TaskStartResult> onResult) {
        mViewModel.startTaskNow(taskId, result -> {
            refreshRewardBarButton();
            if (onResult != null) onResult.accept(result);
        });
    }

    @Override
    public void checkTaskStart(long taskId, Consumer<TaskStartResult> onResult) {
        mViewModel.checkTaskStart(taskId, onResult);
    }

    @Override
    public String getScheduleText(TaskScheduleEntity schedule) {
        return mViewModel.getScheduleText(schedule);
    }

    @Override
    public void ignoreSchedule(long scheduleId, long taskId, Runnable onSuccess) {
        mViewModel.ignoreSchedule(scheduleId, taskId, onSuccess);
    }

    @Override
    public void completeTask(long taskId, boolean stopSchedule, Runnable onResult) {
        mViewModel.completeRunningTask(taskId, stopSchedule, () -> {
            refreshRewardBarButton();
            if (onResult != null) onResult.run();
        });
    }

    private void refreshRewardBarButton() {
        MainPage0Fragment page0 = (MainPage0Fragment) getChildFragmentManager()
            .findFragmentByTag("f0");
        RewardBarFragment rbf = getRewardBarFragment(page0);
        if (rbf != null) {
            rbf.refreshTakePhotoButton();
        }
    }

    @Override
    public void loadTimelineTaskState(long taskId,
                                      Consumer<MainViewModel.TimelineTaskState> callback) {
        mViewModel.loadTimelineTaskState(taskId, callback);
    }

    @Override
    public void loadActiveSchedule(long taskId,
                                   Consumer<TaskScheduleEntity> callback) {
        mViewModel.loadActiveSchedule(taskId, callback);
    }

    @Override
    public void loadActiveScheduleForShortCompletion(long taskId,
                                                     Consumer<TaskScheduleEntity> callback) {
        mViewModel.loadActiveScheduleForShortCompletion(taskId, callback);
    }

    @Override
    public void archiveTask(long taskId) {
        mViewModel.archiveTask(taskId);
    }

    @Override
    public void resetChecklistState(long taskId, Runnable onCompleted) {
        mViewModel.resetChecklistStateForCompletion(taskId, onCompleted);
    }

    @Override
    public void cancelShortCompletion() {
        mViewModel.cancelShortCompletion();
    }

    @Override
    public void shortCompleteDirect(long taskId, boolean stopSchedule, Runnable onComplete) {
        mViewModel.shortCompleteDirect(taskId, stopSchedule, onComplete);
    }

    @Override
    public void shortCompleteAndConvertToChore(long taskId, boolean stopSchedule,
                                               Runnable onComplete) {
        mViewModel.shortCompleteAndConvertToChore(taskId, stopSchedule, onComplete);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }
}
