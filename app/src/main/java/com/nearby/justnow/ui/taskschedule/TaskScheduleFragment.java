package com.nearby.justnow.ui.taskschedule;

import android.app.DatePickerDialog;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.databinding.FragmentTaskScheduleBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.util.DateUtils;
import com.nearby.justnow.util.PermissionHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 任务安排界面（F5 重构：动态时段组选择 + 二级互斥选择）。
 */
public class TaskScheduleFragment extends BaseFragment<FragmentTaskScheduleBinding> {

    private static final int SLOTS_PER_ROW = 6;
    private static final int SLOT_INTERVAL_MINUTES = 10;

    private static final Map<String, Integer> sGroupDisplayNameMap;
    static {
        Map<String, Integer> map = new HashMap<>();
        map.put(PeriodGroupType.WORKDAY, R.string.s_period_group_workday);
        map.put(PeriodGroupType.SPRING_FESTIVAL, R.string.s_period_group_spring_festival);
        map.put(PeriodGroupType.LONG_VACATION, R.string.s_period_group_long_vacation);
        map.put(PeriodGroupType.SUMMER_VACATION, R.string.s_period_group_summer_vacation);
        map.put(PeriodGroupType.WINTER_VACATION, R.string.s_period_group_winter_vacation);
        sGroupDisplayNameMap = Collections.unmodifiableMap(map);
    }

    private TaskScheduleViewModel mViewModel;
    private long mTaskId;
    private TaskScheduleEntity mExistingSchedule;

    /** 当前选中状态 */
    private long mSelectedDateMs;
    private int mSelectedSlotMinute = -1;
    private int mWeeklyBitmask;
    /** 当前任务的专注时长（分钟），用于槽位范围计算。从 loadInitialState 异步加载。 */
    private int mTaskFocusMinutes;

    /** 当前选中的时段组类型（空字符串 = 顶层单次）。 */
    private String mSelectedGroupType = "";
    /** 时段组下子类型：0=每天, 1=每周, 2=单次。 */
    private int mScheduleSubType = 0;
    /** 左栏"每天"是否选中。 */
    private boolean mEverydaySelected = false;
    /** 时段组单次日期（scheduleSubType=2 时有效）。 */
    private long mGroupOnceDateMs;
    /** 开启的时段组列表（loadInitialState 回调解包）。 */
    private List<TimePeriodGroupEntity> mEnabledPeriodGroups;
    /** 工作日模式（决定周 chip 数量）。 */
    private PeriodGroupRuleResolver.WorkdayMode mWorkdayMode;
    /** 右栏长假类单次日期视图（程序化创建）。 */
    private TextView mGroupOnceDateView;

    /** 动态槽位视图引用 */
    private final List<TextView> mSlotViews = new ArrayList<>();
    /** 星期 Chip 数组 */
    private TextView[] mDayChips;

    /** 上次异步加载的槽位数据（refreshSlotView 纯渲染用，避免 Room 主线程查询）。 */
    private List<TimePeriodEntity> mLoadedPeriods;
    private long mLoadedOccupiedDateMs;
    private Set<Integer> mLoadedOccupiedSlots;

    @Override
    protected FragmentTaskScheduleBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentTaskScheduleBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
            .get(TaskScheduleViewModel.class);
        mTaskId = getArguments() != null ? getArguments().getLong("task_id", -1) : -1;

        mSelectedDateMs = DateUtils.todayStartMs();
        mDayChips = new TextView[]{
            getBinding().chipSun, getBinding().chipMon, getBinding().chipTue,
            getBinding().chipWed, getBinding().chipThu, getBinding().chipFri,
            getBinding().chipSat
        };

        setupDatePicker();
        setupWeeklyDays();
        setupEverydayToggle();
        getBinding().btnSaveSchedule.setOnClickListener(v -> saveSchedule());

        loadInitialState();
    }

    private void loadInitialState() {
        mViewModel.loadInitialState(mTaskId, state -> {
            if (state.task == null) {
                Navigation.findNavController(requireView()).popBackStack();
                return;
            }
            getBinding().tvScheduleTaskTitle.setText(state.task.content);
            mTaskFocusMinutes = state.task.focusMinutes;
            mExistingSchedule = state.schedule;
            mEnabledPeriodGroups = state.enabledPeriodGroups;
            mWorkdayMode = state.workdayMode;

            // 程序化生成动态 RadioGroup
            buildTypeRadioGroup(mEnabledPeriodGroups);

            if (state.schedule != null) {
                restoreExistingSchedule(state.schedule);
            } else {
                mSelectedDateMs = DateUtils.todayStartMs();
                mSelectedGroupType = "";
                mScheduleSubType = 0;
                mSelectedSlotMinute = -1;
                selectTypeRadioByTag("");
            }
            updateSaveButton();
        });
    }

    private void restoreExistingSchedule(TaskScheduleEntity s) {
        mSelectedSlotMinute = s.scheduledTime;
        mSelectedGroupType = s.linkedPeriodGroupType != null ? s.linkedPeriodGroupType : "";
        mScheduleSubType = s.scheduleSubType;

        if (mSelectedGroupType.isEmpty()) {
            // 顶层单次
            selectTypeRadioByTag("");
            mSelectedDateMs = s.scheduleValue;
            updateDateDisplay();
        } else {
            // 关联时段组
            selectTypeRadioByTag(mSelectedGroupType);
            onTypeSelected(mSelectedGroupType); // 配置右栏

            switch (s.scheduleSubType) {
                case 0: // 每天
                    mEverydaySelected = true;
                    getBinding().tvEveryday.setSelected(true);
                    break;
                case 1: // 每周
                    mWeeklyBitmask = (int) s.scheduleValue;
                    updateDayChips();
                    break;
                case 2: // 单次
                    mGroupOnceDateMs = s.scheduleValue;
                    if (mGroupOnceDateView != null) {
                        mGroupOnceDateView.setSelected(true);
                    }
                    updateGroupOnceDateDisplay();
                    break;
            }
        }

        updateSaveButton();
    }

    // ==================== 动态 RadioGroup 生成 ====================

    private void buildTypeRadioGroup(List<TimePeriodGroupEntity> groups) {
        RadioGroup rg = getBinding().rgScheduleType;
        rg.removeAllViews();
        rg.setOnCheckedChangeListener(null);

        // "单次" RadioButton（始终存在，固定 ID）
        RadioButton rbOnce = new RadioButton(requireContext());
        rbOnce.setId(R.id.rb_schedule_once);
        rbOnce.setText(R.string.s_schedule_once);
        rbOnce.setTag("");
        rbOnce.setTextAppearance(R.style.TextAppearance_JustNow_Body);
        RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rbOnce.setLayoutParams(params);
        rg.addView(rbOnce);

        // 时段组 RadioButton（按 display_order 排序后按序生成）
        if (groups != null) {
            for (TimePeriodGroupEntity g : groups) {
                RadioButton rb = new RadioButton(requireContext());
                rb.setId(View.generateViewId());
                rb.setText(getGroupDisplayName(g.groupType));
                rb.setTag(g.groupType);
                rb.setTextAppearance(R.style.TextAppearance_JustNow_Body);
                rb.setLayoutParams(new RadioGroup.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                rg.addView(rb);
            }
        }

        // 设置选择监听
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            View checkedView = group.findViewById(checkedId);
            if (checkedView == null) return;
            String groupType = (String) checkedView.getTag();
            if (groupType == null) groupType = "";
            onTypeSelected(groupType);
        });
    }

    private void selectTypeRadioByTag(String tag) {
        RadioGroup rg = getBinding().rgScheduleType;
        for (int i = 0; i < rg.getChildCount(); i++) {
            View child = rg.getChildAt(i);
            String childTag = (String) child.getTag();
            if (childTag == null) childTag = "";
            if (tag.equals(childTag)) {
                rg.check(child.getId());
                return;
            }
        }
    }

    /** 时段组类型 -> 显示名映射（复用现有字符串资源）。 */
    private String getGroupDisplayName(String groupType) {
        Integer resId = sGroupDisplayNameMap.get(groupType);
        if (resId != null) {
            return getString(resId);
        }
        return groupType;
    }

    // ==================== 类型选择 ====================

    private void onTypeSelected(String groupType) {
        mSelectedGroupType = groupType;
        mEverydaySelected = false;
        mScheduleSubType = 0;
        mWeeklyBitmask = 0;
        mGroupOnceDateMs = 0;

        if (groupType.isEmpty()) {
            // 顶层单次
            getBinding().llDatePicker.setVisibility(View.VISIBLE);
            getBinding().llGroupExtra.setVisibility(View.GONE);
            updateDateDisplay();
        } else {
            // 时段组
            getBinding().llDatePicker.setVisibility(View.GONE);
            getBinding().llGroupExtra.setVisibility(View.VISIBLE);
            configureRightColumn(groupType);
            resetSecondarySelection();
        }

        mSelectedSlotMinute = -1;
        refreshSlotViewAsync();
        updateSaveButton();
    }

    /** 根据时段组类型配置右栏内容。 */
    private void configureRightColumn(String groupType) {
        LinearLayout rightColumn = getBinding().llRightColumn;
        // 先隐藏所有
        getBinding().llWeeklyDays.setVisibility(View.GONE);
        if (mGroupOnceDateView != null) {
            mGroupOnceDateView.setVisibility(View.GONE);
        }

        if (PeriodGroupType.WORKDAY.equals(groupType)) {
            // 工作日 → 周 chips
            getBinding().llWeeklyDays.setVisibility(View.VISIBLE);

            boolean sixDay = mWorkdayMode == PeriodGroupRuleResolver.WorkdayMode.SIX_DAY;
            getBinding().chipSun.setVisibility(View.GONE); // 工作日不显示周日
            getBinding().chipMon.setVisibility(View.VISIBLE);
            getBinding().chipTue.setVisibility(View.VISIBLE);
            getBinding().chipWed.setVisibility(View.VISIBLE);
            getBinding().chipThu.setVisibility(View.VISIBLE);
            getBinding().chipFri.setVisibility(View.VISIBLE);
            getBinding().chipSat.setVisibility(sixDay ? View.VISIBLE : View.GONE);
        } else {
            // 长假/春节 → 单次日期
            if (mGroupOnceDateView == null) {
                mGroupOnceDateView = new TextView(requireContext());
                mGroupOnceDateView.setBackgroundResource(R.drawable.bg_day_chip);
                mGroupOnceDateView.setTextColor(
                    getResources().getColorStateList(R.color.chip_day_text, null));
                mGroupOnceDateView.setGravity(Gravity.CENTER);
                float density = getResources().getDisplayMetrics().density;
                mGroupOnceDateView.setPadding(
                    (int) (8 * density), (int) (8 * density),
                    (int) (8 * density), (int) (8 * density));
                mGroupOnceDateView.setTextAppearance(R.style.TextAppearance_JustNow_Body);
                mGroupOnceDateView.setClickable(true);
                mGroupOnceDateView.setFocusable(true);
                mGroupOnceDateView.setOnClickListener(v -> showGroupOnceDatePicker());
                rightColumn.addView(mGroupOnceDateView);
            }
            mGroupOnceDateView.setVisibility(View.VISIBLE);
            updateGroupOnceDateDisplay();
        }
    }

    // ==================== 日期选择 ====================

    private void setupDatePicker() {
        getBinding().tvSelectedDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(mSelectedDateMs);
            new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, day, 0, 0, 0);
                    c.set(Calendar.MILLISECOND, 0);
                    mSelectedDateMs = c.getTimeInMillis();
                    updateDateDisplay();
                    mSelectedSlotMinute = -1;
                    refreshSlotViewAsync();
                    updateSaveButton();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });
        updateDateDisplay();
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        getBinding().tvSelectedDate.setText(sdf.format(new Date(mSelectedDateMs)));
    }

    // ==================== 左栏"每天" toggle ====================

    private void setupEverydayToggle() {
        getBinding().tvEveryday.setOnClickListener(v -> {
            boolean newState = !getBinding().tvEveryday.isSelected();
            getBinding().tvEveryday.setSelected(newState);
            mEverydaySelected = newState;

            if (newState) {
                // 选左栏 → 自动取消右栏选中
                for (TextView chip : mDayChips) {
                    chip.setSelected(false);
                }
                mWeeklyBitmask = 0;
                if (mGroupOnceDateView != null) {
                    mGroupOnceDateView.setSelected(false);
                }
                mGroupOnceDateMs = 0;
                mScheduleSubType = 0;
            }
            updateSaveButton();
        });
    }

    /** 重置二级选择状态（切换时段组时调用）。 */
    private void resetSecondarySelection() {
        mEverydaySelected = false;
        getBinding().tvEveryday.setSelected(false);
        for (TextView chip : mDayChips) {
            chip.setSelected(false);
        }
        mWeeklyBitmask = 0;
        if (mGroupOnceDateView != null) {
            mGroupOnceDateView.setSelected(false);
        }
        mGroupOnceDateMs = 0;
        mScheduleSubType = 0;
    }

    // ==================== 右栏：周 chips ====================

    private void setupWeeklyDays() {
        for (TextView chip : mDayChips) {
            chip.setOnClickListener(view -> {
                // 选右栏 chip → 取消左栏"每天"
                if (mEverydaySelected) {
                    mEverydaySelected = false;
                    getBinding().tvEveryday.setSelected(false);
                }

                view.setSelected(!view.isSelected());
                updateWeeklyBitmask();

                if (mWeeklyBitmask != 0) {
                    mScheduleSubType = 1;
                } else {
                    mScheduleSubType = 0;
                }

                refreshSlotViewAsync();
                updateSaveButton();
            });
        }
    }

    private void updateWeeklyBitmask() {
        int mask = 0;
        for (int i = 0; i < mDayChips.length; i++) {
            if (mDayChips[i].isSelected()) {
                mask |= (1 << i); // i=0=Sun
            }
        }
        mWeeklyBitmask = mask;
    }

    private void updateDayChips() {
        for (int i = 0; i < mDayChips.length; i++) {
            mDayChips[i].setSelected((mWeeklyBitmask & (1 << i)) != 0);
        }
    }

    // ==================== 右栏：单次日期（长假/春节） ====================

    private void showGroupOnceDatePicker() {
        // 选右栏日期 → 取消左栏"每天"
        if (mEverydaySelected) {
            mEverydaySelected = false;
            getBinding().tvEveryday.setSelected(false);
        }

        Calendar cal = Calendar.getInstance();
        if (mGroupOnceDateMs > 0) {
            cal.setTimeInMillis(mGroupOnceDateMs);
        }
        new DatePickerDialog(requireContext(),
            (picker, year, month, day) -> {
                Calendar c = Calendar.getInstance();
                c.set(year, month, day, 0, 0, 0);
                c.set(Calendar.MILLISECOND, 0);
                mGroupOnceDateMs = c.getTimeInMillis();
                mScheduleSubType = 2;
                if (mGroupOnceDateView != null) {
                    mGroupOnceDateView.setSelected(true);
                }
                updateGroupOnceDateDisplay();
                mSelectedSlotMinute = -1;
                refreshSlotViewAsync();
                updateSaveButton();
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void updateGroupOnceDateDisplay() {
        if (mGroupOnceDateView == null) return;
        if (mGroupOnceDateMs > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            mGroupOnceDateView.setText(sdf.format(new Date(mGroupOnceDateMs)));
        } else {
            mGroupOnceDateView.setText(getString(R.string.s_schedule_date));
        }
    }

    // ==================== 槽位视图 ====================

    /** 异步版 refreshSlotView：后台线程查库，UI 线程渲染。 */
    private void refreshSlotViewAsync() {
        // 捕获状态供后台线程使用
        final String groupType = mSelectedGroupType;
        final long selectedDateMs = mSelectedDateMs;
        final int subType = mScheduleSubType;
        final long groupOnceDateMs = mGroupOnceDateMs;
        final long excludeId = mExistingSchedule != null ? mExistingSchedule.id : -1;

        mViewModel.runOnBackgroundThread(() -> {
            // 统一驱动：顶层单次走所选日期，时段组单次走单次日期，时段组每天/每周不查占用不过去时间
            final long effectiveDateMs;
            if (groupType.isEmpty()) {
                effectiveDateMs = selectedDateMs;
            } else if (subType == 2) {
                effectiveDateMs = groupOnceDateMs;
            } else {
                effectiveDateMs = 0;
            }

            List<TimePeriodEntity> periods;
            if (groupType.isEmpty()) {
                // 顶层单次：按所选日期命中时段组
                ActivePeriodGroup group = mViewModel.getActivePeriodGroupForDate(effectiveDateMs);
                periods = new ArrayList<>();
                if (group != null && group.periods != null) {
                    for (TimePeriodEntity p : group.periods) {
                        if (!p.preferChore) {
                            periods.add(p);
                        }
                    }
                }
            } else {
                // 关联了固定时段组：直接取该组的时段
                periods = mViewModel.getPeriodsByGroupSync(groupType);
            }

            Set<Integer> occupied;
            if (effectiveDateMs > 0) {
                occupied = mViewModel.getOccupiedSlots(excludeId, effectiveDateMs);
            } else {
                occupied = new HashSet<>();
            }

            // 缓存数据供 refreshSlotView 纯渲染使用
            mLoadedPeriods = periods;
            mLoadedOccupiedDateMs = effectiveDateMs;
            mLoadedOccupiedSlots = occupied;

            requireActivity().runOnUiThread(TaskScheduleFragment.this::refreshSlotView);
        });
    }

    /** 纯渲染方法：读取 mLoaded* 缓存数据构建槽位视图，不含任何 Room 查询。 */
    private void refreshSlotView() {
        LinearLayout container = getBinding().llSlotContainer;
        container.removeAllViews();
        mSlotViews.clear();

        List<TimePeriodEntity> periods = mLoadedPeriods;
        if (periods == null || periods.isEmpty()) {
            return;
        }

        long occupiedDateMs = mLoadedOccupiedDateMs;
        Set<Integer> occupied = mLoadedOccupiedSlots;

        boolean isToday = occupiedDateMs == DateUtils.todayStartMs();
        int nowMinute = isToday ? currentMinuteOfDay() : -1;

        Resources res = getResources();
        int colorAvailable = res.getColor(R.color.text_primary, null);
        int colorSelected = res.getColor(R.color.white, null);
        int colorDisabled = res.getColor(R.color.text_tertiary, null);
        int bgAvailable = res.getColor(R.color.period_edit_chip_background, null);
        int bgSelected = res.getColor(R.color.tag_normal, null);
        int bgDisabled = Color.rgb(245, 245, 245); // 浅灰

        for (TimePeriodEntity period : periods) {
            // Section header
            TextView header = new TextView(requireContext());
            header.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            header.setText(periodName(period));
            header.setTextColor(res.getColor(R.color.text_secondary, null));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = (int) (8 * res.getDisplayMetrics().density);
            hp.bottomMargin = (int) (4 * res.getDisplayMetrics().density);
            header.setLayoutParams(hp);
            container.addView(header);

            // Slot grid
            GridLayout grid = new GridLayout(requireContext());
            grid.setColumnCount(SLOTS_PER_ROW);
            grid.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int startMin = period.startMinute;
            int endMin = period.endMinute;

            int focusMinutes = mTaskFocusMinutes;
            int selectedStart = mSelectedSlotMinute;

            for (int min = startMin; min < endMin; min += SLOT_INTERVAL_MINUTES) {
                TextView slot = new TextView(requireContext());
                slot.setText(DateUtils.formatMinute(min));
                slot.setGravity(Gravity.CENTER);
                slot.setTextAppearance(com.nearby.justnow.R.style.TextAppearance_JustNow_Caption);
                slot.setPadding(
                    (int) (4 * res.getDisplayMetrics().density),
                    (int) (6 * res.getDisplayMetrics().density),
                    (int) (4 * res.getDisplayMetrics().density),
                    (int) (6 * res.getDisplayMetrics().density));

                // GridLayout 列均分
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                params.setGravity(Gravity.FILL_HORIZONTAL);
                params.leftMargin = (int) (2 * res.getDisplayMetrics().density);
                params.rightMargin = (int) (2 * res.getDisplayMetrics().density);
                params.bottomMargin = (int) (2 * res.getDisplayMetrics().density);
                slot.setLayoutParams(params);

                // ---- 槽位状态判定（保持 Pass 2 逻辑不变） ----
                boolean isPast = isToday && min <= nowMinute + SLOT_INTERVAL_MINUTES;
                boolean isInSelectedRange = selectedStart >= 0
                    && min >= selectedStart && min < selectedStart + focusMinutes;
                boolean isInOccupiedRange = occupied.contains(min);

                boolean exceedsPeriodEnd = min + focusMinutes > endMin;
                boolean overlapsOccupied = false;
                if (!exceedsPeriodEnd && focusMinutes > 0) {
                    for (int check = min; check < min + focusMinutes; check += SLOT_INTERVAL_MINUTES) {
                        if (occupied.contains(check)) {
                            overlapsOccupied = true;
                            break;
                        }
                    }
                }
                boolean canBeSlotStart = !isPast && !exceedsPeriodEnd && !overlapsOccupied;

                // ---- 颜色（保持 Pass 2 逻辑不变） ----
                if (isInSelectedRange) {
                    slot.setBackgroundColor(bgSelected);
                    slot.setTextColor(colorSelected);
                } else if (isInOccupiedRange || isPast) {
                    slot.setBackgroundColor(bgDisabled);
                    slot.setTextColor(colorDisabled);
                } else if (!canBeSlotStart) {
                    slot.setBackgroundColor(bgDisabled);
                    slot.setTextColor(colorDisabled);
                } else {
                    slot.setBackgroundColor(bgAvailable);
                    slot.setTextColor(colorAvailable);
                }

                // ---- 点击（保持 Pass 2 逻辑不变） ----
                if (canBeSlotStart) {
                    final int slotMinute = min;
                    slot.setOnClickListener(v -> {
                        mSelectedSlotMinute = slotMinute;
                        refreshSlotView();
                        updateSaveButton();
                    });
                }

                mSlotViews.add(slot);
                grid.addView(slot);
            }
            container.addView(grid);
        }
    }

    // ==================== 保存 ====================

    private void saveSchedule() {
        if (!mViewModel.hasRequiredPermissions()) {
            showPermissionDialog();
            return;
        }

        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.taskId = mTaskId;
        schedule.scheduledTime = mSelectedSlotMinute;
        schedule.linkedPeriodGroupType = mSelectedGroupType;
        schedule.scheduleSubType = mScheduleSubType;

        if (mSelectedGroupType.isEmpty()) {
            // 顶层单次：兼容旧字段
            schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
            schedule.scheduleValue = mSelectedDateMs;
        } else {
            // 时段组：旧字段兼容填充
            switch (mScheduleSubType) {
                case 0: // 每天
                    schedule.scheduleType = TaskScheduleEntity.TYPE_DAILY;
                    schedule.scheduleValue = 0;
                    break;
                case 1: // 每周
                    schedule.scheduleType = TaskScheduleEntity.TYPE_WEEKLY;
                    schedule.scheduleValue = mWeeklyBitmask;
                    break;
                case 2: // 单次
                    schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
                    schedule.scheduleValue = mGroupOnceDateMs;
                    break;
                default:
                    schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
                    schedule.scheduleValue = 0;
                    break;
            }
        }

        Runnable onSaved = () -> requireActivity().runOnUiThread(() -> {
            if (isAdded()) requireActivity().finish();
        });

        if (mExistingSchedule != null) {
            schedule.id = mExistingSchedule.id;
            schedule.createdAt = mExistingSchedule.createdAt;
            mViewModel.updateSchedule(schedule, onSaved);
        } else {
            mViewModel.insertSchedule(schedule, onSaved);
        }
    }

    private void showPermissionDialog() {
        boolean hasAlarm = PermissionHelper.hasExactAlarmPermission(requireContext());
        boolean hasNotify = PermissionHelper.hasNotificationPermission(requireContext());
        StringBuilder msg = new StringBuilder();
        if (!hasAlarm) msg.append(getString(R.string.s_exact_alarm_permission_message));
        if (!hasNotify) {
            if (msg.length() > 0) msg.append("\n\n");
            msg.append(getString(R.string.s_notification_permission_message));
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
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

    private void updateSaveButton() {
        boolean enabled = mSelectedSlotMinute >= 0;

        if (mSelectedGroupType.isEmpty()) {
            // 顶层单次：需日期已选
            enabled = enabled && mSelectedDateMs > 0;
        } else {
            // 时段组：需二级选择已选
            boolean secondarySelected = mEverydaySelected
                || (mScheduleSubType == 1 && mWeeklyBitmask != 0)
                || (mScheduleSubType == 2 && mGroupOnceDateMs > 0);
            enabled = enabled && secondarySelected;
        }

        getBinding().btnSaveSchedule.setEnabled(enabled);
    }

    // ==================== 工具方法 ====================

    private static int currentMinuteOfDay() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private String periodName(TimePeriodEntity period) {
        return com.nearby.justnow.ui.period.PeriodTextResolver.getPeriodName(
            getResources(), period.nameKey);
    }
}
