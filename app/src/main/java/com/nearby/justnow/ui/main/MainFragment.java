package com.nearby.justnow.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.databinding.FragmentMainBinding;
import com.nearby.justnow.databinding.FragmentMainPage0Binding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.BaseTaskViewModel;
import com.nearby.justnow.ui.base.TagChipHelper;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.scheduler.TaskScheduleMatcher;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.period.PeriodTextResolver;

import com.nearby.justnow.util.PermissionHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 主界面 — 黄金比例双栏 + 智能展示引擎 + 标签展开 + ViewPager2 多页。
 * <p>
 * Page 0 = 现有主界面内容（MainPage0Fragment），
 * Page 1 = 四象限全任务概览（QuadrantOverviewFragment）。
 */
public class MainFragment extends BaseFragment<FragmentMainBinding> {

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
    private final BroadcastReceiver mTimeTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mViewModel.refreshTimeState();
        }
    };

    /** Page 0 的 ViewBinding，由 childFragment MainPage0Fragment 提供。 */
    private FragmentMainPage0Binding mPage0Binding;

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
                // 防御：setCurrentItem 强制触发创建
                viewPager.setCurrentItem(0, false);
                getView().post(() -> setupPage0Content(
                    app, (MainPage0Fragment) getChildFragmentManager().findFragmentByTag("f0")));
            } else {
                setupPage0Content(app, page0);
            }
        });
    }

    private void setupPage0Content(JustNowApplication app, MainPage0Fragment page0) {
        if (page0 == null) return;
        mPage0Binding = page0.getBinding();
        if (mPage0Binding == null) return;

        setupAdapter();
        observeDisplay();
        observePriorityConfig();
        setupFilterOverlay();
        setupFab();
        setupPriorityStatus();
        calcMaxDisplayItems();
        consumePendingWidgetConfigureExactAlarmPrompt();
        consumePendingWidgetTaskClick();
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
        if (item.getItemId() == R.id.action_stats) {
            startActivity(new Intent(requireContext(),
                com.nearby.justnow.ui.stats.StatsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupAdapter() {
        if (mPage0Binding == null) return;
        mAdapter = new TaskAdapter();
        mPage0Binding.rvTaskList.setLayoutManager(
            new androidx.recyclerview.widget.LinearLayoutManager(requireContext()) {
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
        mViewModel.getTaskStartEvent().observe(getViewLifecycleOwner(), this::handleTaskStart);

        mViewModel.getTaskCompleteToDetailEvent().observe(getViewLifecycleOwner(), taskId -> {
            Intent intent = new Intent(requireContext(),
                com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
            intent.putExtra("task_id", taskId);
            startActivity(intent);
        });

        mViewModel.getOnlyTitleTaskCompleteEvent().observe(getViewLifecycleOwner(), this::handleOnlyTitleTaskComplete);

        // 左侧时间线已安排任务点击事件（独立对话框）
        mViewModel.getTimelineScheduledTaskClickEvent().observe(getViewLifecycleOwner(),
            this::handleTimelineScheduledTaskClick);

        // 完成前确认回调
        mViewModel.setPreCompleteConfirmCallback((taskId, confirmType, onConfirmed) -> {
            if (BaseTaskViewModel.CONFIRM_TYPE_CHECKLIST_STATE.equals(confirmType)) {
                showChecklistStateConfirmDialog(taskId, onConfirmed);
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

    /** 优先标签状态行点击：切换临时关闭/恢复 */
    private void setupPriorityStatus() {
        mPage0Binding.llPriorityStatus.setOnClickListener(v -> mViewModel.togglePrioritySuppress());
    }

    /** 根据 RecyclerView 实际高度和 dimens 资源计算可显示的任务数 */
    private void calcMaxDisplayItems() {
        mPage0Binding.rvTaskList.post(() -> {
            int rvHeight = mPage0Binding.rvTaskList.getHeight();
            if (rvHeight <= 0) return;

            int itemTotalPx = getResources().getDimensionPixelSize(R.dimen.task_item_total_height);
            int count = rvHeight / itemTotalPx;
            if (count > 0) {
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
            mPage0Binding.timelineView.setPeriods(result.timelinePeriods);
            mPage0Binding.timelineView.setActivePeriods(result.periods);
            mPage0Binding.timelineView.setPeriodStatus(result.periodStatus);
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
            return;
        }

        mPage0Binding.tvBottomPeriodTitle.setText(result.periodName);
        mPage0Binding.tvBottomPeriodSubtitle.setText(result.remainingText);
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

    private void showTaskDetailDialog(TaskEntity task, TagEntity tag, TaskScheduleEntity schedule) {
        StringBuilder messageBuilder = new StringBuilder();
        if (task.detail != null && !task.detail.trim().isEmpty()) {
            messageBuilder.append(task.detail.trim()).append("\n\n");
        }
        if (tag != null) {
            messageBuilder.append(getString(R.string.s_task_detail_tag, tag.name)).append("\n");
        }
        messageBuilder.append(getString(R.string.s_task_detail_focus, getFocusText(task.focusMinutes)));
        String scheduleText = task.focusMinutes > 0
            && isScheduleActionable(schedule)
            ? mViewModel.getScheduleText(schedule) : "";
        if (!scheduleText.isEmpty()) {
            messageBuilder.append("\n").append(getString(R.string.s_task_detail_schedule, scheduleText));
        }
        String baseMessage = messageBuilder.toString();
        boolean isFocusTask = task.focusMinutes > 0;
        boolean showScheduleAsPrimary = isFocusTask && !mIsInActivePeriod;

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
            .setTitle(task.content)
            .setMessage(baseMessage)
            .setNeutralButton(R.string.s_cancel, null);
        if (isFocusTask) {
            builder.setPositiveButton(showScheduleAsPrimary
                    ? R.string.s_schedule_task : R.string.s_start_now, null)
                .setNegativeButton(showScheduleAsPrimary
                    ? R.string.s_start_now : R.string.s_schedule_task, null);
        } else {
            builder.setPositiveButton(R.string.s_start_now, null);
        }
        AlertDialog dialog = builder.show();

        TaskStartResult initialResult = showScheduleAsPrimary
            ? new TaskStartResult(TaskStartResult.BLOCKED_OUT_OF_PERIOD) : null;
        applyTaskDetailActions(dialog, task, baseMessage, isFocusTask,
            showScheduleAsPrimary, initialResult, schedule);
        mViewModel.checkTaskStart(task.id, result -> {
            boolean scheduleAsPrimary = isFocusTask
                && result.code != TaskStartResult.OK
                && (!mIsInActivePeriod
                    || result.code == TaskStartResult.BLOCKED_OUT_OF_PERIOD);
            applyTaskDetailActions(dialog, task, baseMessage, isFocusTask,
                scheduleAsPrimary, result, schedule);
        });
    }

    private void applyTaskDetailActions(AlertDialog dialog, TaskEntity task, String baseMessage,
                                        boolean isFocusTask, boolean scheduleAsPrimary,
                                        @Nullable TaskStartResult startResult,
                                        @Nullable TaskScheduleEntity schedule) {
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        boolean canStart = startResult != null && startResult.code == TaskStartResult.OK;

        if (isFocusTask && scheduleAsPrimary) {
            configureScheduleButton(positiveButton, dialog, task, schedule, true);
            configureStartButton(negativeButton, dialog, task.id, false, false, task);
        } else {
            configureStartButton(positiveButton, dialog, task.id, canStart, true, task);
            if (isFocusTask) {
                configureScheduleButton(negativeButton, dialog, task, schedule, false);
            }
        }

        if (startResult == null || startResult.code == TaskStartResult.OK) {
            dialog.setMessage(baseMessage);
            return;
        }
        dialog.setMessage(baseMessage + "\n\n"
            + getString(R.string.s_start_unavailable_reason,
                getStartBlockReason(startResult)));
    }

    private void configureStartButton(Button startButton, AlertDialog dialog, long taskId,
                                      boolean enabled, boolean primary, TaskEntity task) {
        if (startButton == null) return;
        startButton.setText(R.string.s_start_now);
        applyDialogActionStyle(startButton, primary
            ? R.color.dialog_primary_action_text : R.color.dialog_action_text);
        startButton.setEnabled(enabled);
        startButton.setOnClickListener(v ->
            mViewModel.startTaskNow(taskId, result -> handleStartTaskResult(dialog, result, task)));
    }

    private boolean isScheduleActionable(@Nullable TaskScheduleEntity schedule) {
        return schedule != null && schedule.enabled
            && TaskScheduleMatcher.matchesToday(schedule);
    }

    private void configureScheduleButton(Button scheduleButton, AlertDialog dialog, TaskEntity task,
                                         @Nullable TaskScheduleEntity schedule, boolean primary) {
        if (scheduleButton == null) return;
        boolean hasActiveSchedule = isScheduleActionable(schedule);
        scheduleButton.setText(hasActiveSchedule ? R.string.s_adjust_schedule : R.string.s_schedule_task);
        applyDialogActionStyle(scheduleButton, primary
            ? R.color.dialog_primary_action_text : R.color.dialog_action_text);
        boolean canSchedule = task != null && task.focusMinutes > 0;
        scheduleButton.setEnabled(canSchedule);
        if (canSchedule) {
            scheduleButton.setOnClickListener(v -> {
                dialog.dismiss();
                navigateToSchedule(task.id);
            });
        } else {
            scheduleButton.setOnClickListener(null);
        }
    }

    private void applyDialogActionStyle(Button button, int textColorRes) {
        if (button == null) return;
        ColorStateList colors = ContextCompat.getColorStateList(requireContext(), textColorRes);
        if (colors != null) {
            button.setTextColor(colors);
        }
    }

    private void handleStartTaskResult(AlertDialog dialog, TaskStartResult result, TaskEntity task) {
        if (result.code == TaskStartResult.OK) {
            dialog.dismiss();
            boolean hasContent = (task.detailMarkdown != null && !task.detailMarkdown.isEmpty())
                || task.detailModuleType != null;
            if (hasContent) {
                Intent intent = new Intent(requireContext(),
                    com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
                intent.putExtra("task_id", task.id);
                startActivity(intent);
            }
            return;
        }
        int messageRes = R.string.s_start_blocked_missing;
        if (result.code == TaskStartResult.BLOCKED_RUNNING) {
            messageRes = R.string.s_start_blocked_running;
        } else if (result.code == TaskStartResult.BLOCKED_OUT_OF_PERIOD) {
            messageRes = R.string.s_start_blocked_out_of_period;
        } else if (result.code == TaskStartResult.BLOCKED_TIME_NOT_ENOUGH) {
            messageRes = R.string.s_start_blocked_time_not_enough;
        }
        Snackbar.make(requireView(), messageRes, Snackbar.LENGTH_SHORT).show();
    }

    private String getStartBlockReason(TaskStartResult result) {
        int messageRes = R.string.s_start_blocked_missing;
        if (result.code == TaskStartResult.BLOCKED_RUNNING) {
            messageRes = R.string.s_start_blocked_running;
        } else if (result.code == TaskStartResult.BLOCKED_OUT_OF_PERIOD) {
            messageRes = R.string.s_start_blocked_out_of_period;
        } else if (result.code == TaskStartResult.BLOCKED_TIME_NOT_ENOUGH) {
            messageRes = R.string.s_start_blocked_time_not_enough;
        }
        return getString(messageRes);
    }

    private void onTimelineItemClicked(TimelineItem item) {
        if (item.running) {
            mViewModel.resolveAndHandleTaskClick(item.taskId);
            return;
        }
        mViewModel.loadActiveSchedule(item.taskId, schedule -> {
            if (isScheduleActionable(schedule)) {
                mViewModel.onTimelineScheduledTaskClick(item.taskId);
            } else {
                mViewModel.resolveAndHandleTaskClick(item.taskId);
            }
        });
    }

    private void showTimelineCompletionDialog(MainViewModel.TimelineTaskState state) {
        TaskEntity task = state.task;
        if (task == null) return;

        int completeLabel = state.hasRecurringSchedule
            ? R.string.s_complete_once : R.string.s_complete;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
            .setTitle(task.content)
            .setPositiveButton(completeLabel, (d, w) ->
                handleFocusTaskCompletion(task, false))
            .setNegativeButton(R.string.s_cancel, null);
        if (state.hasRecurringSchedule) {
            builder.setNeutralButton(R.string.s_complete_and_stop_schedule, (d, w) ->
                handleFocusTaskCompletion(task, true));
        }
        builder.show();
    }

    /**
     * 入口3：左侧时间线点击完成（仅标题专注任务）。
     * 实际耗时 &lt; 15min 时拦截弹 ShortCompletionDialog；否则走正常 completeRunningTask。
     */
    private void handleFocusTaskCompletion(TaskEntity task, boolean stopSchedule) {
        if (task.focusMinutes <= 0) {
            // 防御：非专注任务不进入本路径
            mViewModel.completeRunningTask(task.id, stopSchedule, null);
            return;
        }
        int elapsedMinutes = (int) ((System.currentTimeMillis() - task.executingStartMs) / 60000);
        boolean isShort = task.executingStartMs > 0
            && elapsedMinutes < BaseTaskViewModel.SHORT_DURATION_THRESHOLD_MINUTES;
        if (!isShort) {
            mViewModel.completeRunningTask(task.id, stopSchedule, null);
            return;
        }
        int entry = stopSchedule
            ? ShortCompletionDialog.ENTRY_COMPLETE_AND_STOP_SCHEDULE
            : ShortCompletionDialog.ENTRY_COMPLETE_ONCE;
        // 入口3 / 入口1 共享：stopSchedule 取决于用户上一步按的按钮
        // 入口1 的「完成并调整」副作用受 hasSchedule 影响（有安排时也停安排）
        mViewModel.loadActiveScheduleForShortCompletion(task.id, schedule -> {
            boolean hasSchedule = schedule != null;
            ShortCompletionDialog.show(requireContext(), task.content, entry, hasSchedule,
                new ShortCompletionDialog.Callback() {
                    @Override
                    public void onCancel() {
                        mViewModel.cancelShortCompletion();
                    }

                    @Override
                    public void onDirectComplete() {
                        // 入口3：上一步已选「停安排」，沿用；入口1：不动安排
                        mViewModel.shortCompleteDirect(task.id, stopSchedule, null);
                    }

                    @Override
                    public void onConvertToChore() {
                        // 入口3：停安排已是上一步语义；入口1：有安排时停安排
                        boolean stop = stopSchedule || hasSchedule;
                        mViewModel.shortCompleteAndConvertToChore(task.id, stop, null);
                    }
                });
        });
    }

    /** 未执行任务点击：弹"开始/安排"对话框 */
    private void handleTaskStart(long taskId) {
        mViewModel.loadTimelineTaskState(taskId, state -> {
            TaskEntity task = state.task;
            if (task == null) return;
            mViewModel.loadActiveSchedule(taskId,
                schedule -> showTaskDetailDialog(task, null, schedule));
        });
    }

    /** 左侧时间线已安排任务点击：弹独立对话框（开始/忽略/取消） */
    private void handleTimelineScheduledTaskClick(long taskId) {
        mViewModel.loadTimelineTaskState(taskId, state -> {
            TaskEntity task = state.task;
            if (task == null) return;
            mViewModel.loadActiveSchedule(taskId, schedule -> {
                if (schedule == null || !schedule.enabled) return;

                StringBuilder messageBuilder = new StringBuilder();
                if (task.detail != null && !task.detail.trim().isEmpty()) {
                    messageBuilder.append(task.detail.trim()).append("\n\n");
                }
                messageBuilder.append(getString(R.string.s_task_detail_focus,
                    getFocusText(task.focusMinutes)));
                String scheduleText = task.focusMinutes > 0
                    ? mViewModel.getScheduleText(schedule) : "";
                if (!scheduleText.isEmpty()) {
                    messageBuilder.append("\n")
                        .append(getString(R.string.s_task_detail_schedule, scheduleText));
                }

                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(task.content)
                    .setMessage(messageBuilder.toString())
                    .setPositiveButton(R.string.s_start_now, null)
                    .setNegativeButton(R.string.s_ignore, null)
                    .setNeutralButton(R.string.s_cancel, null)
                    .show();

                Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                applyDialogActionStyle(positiveButton, R.color.dialog_primary_action_text);
                applyDialogActionStyle(negativeButton, R.color.dialog_action_text);
                positiveButton.setOnClickListener(v ->
                    mViewModel.startTaskNow(task.id, result ->
                        handleStartTaskResult(dialog, result, task)));
                negativeButton.setOnClickListener(v ->
                    mViewModel.ignoreSchedule(schedule.id, task.id, dialog::dismiss));
            });
        });
    }

    /** 执行中且仅标题（无详情/模块）任务点击：弹完成对话框。
     *  琐碎任务 → showChoreCompletionDialog；专注任务 → showTimelineCompletionDialog */
    private void handleOnlyTitleTaskComplete(long taskId) {
        mViewModel.loadTimelineTaskState(taskId, state -> {
            TaskEntity task = state.task;
            if (task == null) return;
            if (task.focusMinutes == 0) {
                showChoreCompletionDialog(state);
            } else {
                showTimelineCompletionDialog(state);
            }
        });
    }

    /** 清单状态变化确认弹窗 */
    private void showChecklistStateConfirmDialog(long taskId, Runnable onConfirmed) {
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.s_complete_task)
            .setMessage(R.string.s_checklist_state_changed)
            .setPositiveButton(R.string.s_save, (d, w) -> onConfirmed.run())
            .setNegativeButton(R.string.s_not_save, (d, w) -> {
                mViewModel.resetChecklistStateForCompletion(taskId, onConfirmed);
            })
            .setNeutralButton(R.string.s_cancel, null)
            .show();
    }

    private void showChoreCompletionDialog(MainViewModel.TimelineTaskState state) {
        TaskEntity task = state.task;
        if (task == null) return;
        if (state.hasAnyExecution) {
            new AlertDialog.Builder(requireContext())
                .setTitle(task.content)
                .setMessage(R.string.s_complete_this_execution)
                .setPositiveButton(R.string.s_complete, (d, w) ->
                    mViewModel.completeRunningTask(task.id, false, null))
                .setNegativeButton(R.string.s_cancel, null)
                .show();
            return;
        }

        new AlertDialog.Builder(requireContext())
            .setTitle(task.content)
            .setMessage(R.string.s_task_still_needed)
            .setPositiveButton(R.string.s_complete, (d, w) ->
                mViewModel.completeRunningTask(task.id, false, null))
            .setNegativeButton(R.string.s_no_longer_needed, (d, w) ->
                mViewModel.archiveTask(task.id))
            .setCancelable(true)
            .show();
    }

    private void navigateToSchedule(long taskId) {
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

    private String getFocusText(int focusMinutes) {
        return focusMinutes == 0
            ? getString(R.string.s_chore)
            : focusMinutes + getString(R.string.s_minute_unit);
    }
}
