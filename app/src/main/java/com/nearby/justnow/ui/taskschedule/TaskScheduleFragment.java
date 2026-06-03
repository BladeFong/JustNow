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
import com.nearby.justnow.databinding.FragmentTaskScheduleBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.util.DateUtils;
import com.nearby.justnow.util.PermissionHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 任务安排界面。
 */
public class TaskScheduleFragment extends BaseFragment<FragmentTaskScheduleBinding> {

    private static final int SLOTS_PER_ROW = 6;

    private TaskScheduleViewModel mViewModel;
    private long mTaskId;
    private TaskScheduleEntity mExistingSchedule;

    /** 当前选中状态 */
    private long mSelectedDateMs;
    private int mSelectedScheduleType = TaskScheduleEntity.TYPE_ONCE;
    private int mSelectedSlotMinute = -1;
    private int mWeeklyBitmask;
    /** 当前任务的专注时长（分钟），用于槽位范围计算。从 loadInitialState 异步加载。 */
    private int mTaskFocusMinutes;
    private int mMonthlyDay = 1;

    /** 动态槽位视图引用 */
    private final List<TextView> mSlotViews = new ArrayList<>();
    /** 星期 Chip 数组 */
    private TextView[] mDayChips;

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

        setupTypeSelector();
        setupDatePicker();
        setupWeeklyDays();
        setupMonthlyDay();
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
            if (state.schedule != null) {
                restoreExistingSchedule(state.schedule);
            } else {
                mSelectedDateMs = DateUtils.todayStartMs();
                mSelectedScheduleType = TaskScheduleEntity.TYPE_ONCE;
                mSelectedSlotMinute = -1;
                getBinding().rgScheduleType.check(R.id.rb_schedule_once);
                refreshSlotView();
            }
            updateSaveButton();
        });
    }

    private void restoreExistingSchedule(TaskScheduleEntity s) {
        mSelectedScheduleType = s.scheduleType;
        mSelectedSlotMinute = s.scheduledTime;

        switch (s.scheduleType) {
            case TaskScheduleEntity.TYPE_ONCE:
                getBinding().rgScheduleType.check(R.id.rb_schedule_once);
                mSelectedDateMs = s.scheduleValue;
                updateDateDisplay();
                break;
            case TaskScheduleEntity.TYPE_DAILY:
                getBinding().rgScheduleType.check(R.id.rb_schedule_daily);
                mSelectedDateMs = DateUtils.todayStartMs();
                break;
            case TaskScheduleEntity.TYPE_WEEKLY:
                getBinding().rgScheduleType.check(R.id.rb_schedule_weekly);
                mWeeklyBitmask = (int) s.scheduleValue;
                updateDayChips();
                mSelectedDateMs = DateUtils.todayStartMs();
                break;
            case TaskScheduleEntity.TYPE_MONTHLY:
                getBinding().rgScheduleType.check(R.id.rb_schedule_monthly);
                mMonthlyDay = (int) s.scheduleValue;
                updateMonthlyDayDisplay();
                mSelectedDateMs = DateUtils.todayStartMs();
                break;
        }
        updateTypeExtras();
        refreshSlotView();
    }

    // ==================== 类型选择 ====================

    private void setupTypeSelector() {
        getBinding().rgScheduleType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_schedule_once) {
                mSelectedScheduleType = TaskScheduleEntity.TYPE_ONCE;
            } else if (checkedId == R.id.rb_schedule_daily) {
                mSelectedScheduleType = TaskScheduleEntity.TYPE_DAILY;
                mSelectedDateMs = DateUtils.todayStartMs();
            } else if (checkedId == R.id.rb_schedule_weekly) {
                mSelectedScheduleType = TaskScheduleEntity.TYPE_WEEKLY;
                mSelectedDateMs = DateUtils.todayStartMs();
            } else if (checkedId == R.id.rb_schedule_monthly) {
                mSelectedScheduleType = TaskScheduleEntity.TYPE_MONTHLY;
                mSelectedDateMs = DateUtils.todayStartMs();
            }
            updateTypeExtras();
            refreshSlotViewAsync();
            updateSaveButton();
        });
    }

    private void updateTypeExtras() {
        getBinding().llDatePicker.setVisibility(
            mSelectedScheduleType == TaskScheduleEntity.TYPE_ONCE ? View.VISIBLE : View.GONE);
        getBinding().llWeeklyDays.setVisibility(
            mSelectedScheduleType == TaskScheduleEntity.TYPE_WEEKLY ? View.VISIBLE : View.GONE);
        getBinding().llMonthlyDay.setVisibility(
            mSelectedScheduleType == TaskScheduleEntity.TYPE_MONTHLY ? View.VISIBLE : View.GONE);
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

    // ==================== 星期选择 ====================

    private void setupWeeklyDays() {
        for (TextView chip : mDayChips) {
            chip.setOnClickListener(view -> {
                view.setSelected(!view.isSelected());
                updateWeeklyBitmask();
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

    // ==================== 月日期选择 ====================

    private void setupMonthlyDay() {
        getBinding().tvMonthlyDay.setOnClickListener(v ->
            MonthlyDayPickerDialog.show(requireContext(), mMonthlyDay, day -> {
                mMonthlyDay = day;
                updateMonthlyDayDisplay();
                refreshSlotViewAsync();
                updateSaveButton();
            }));
        updateMonthlyDayDisplay();
    }

    private void updateMonthlyDayDisplay() {
        getBinding().tvMonthlyDay.setText(getString(R.string.s_monthly_day_format, mMonthlyDay));
    }

    // ==================== 槽位视图 ====================

    /** 异步版 refreshSlotView：确保当前日期时段缓存就绪后再刷新 UI。 */
    private void refreshSlotViewAsync() {
        long dateMs = mSelectedScheduleType == TaskScheduleEntity.TYPE_ONCE
            ? mSelectedDateMs : DateUtils.todayStartMs();
        mViewModel.ensurePeriodsCached(dateMs, this::refreshSlotView);
    }

    private void refreshSlotView() {
        LinearLayout container = getBinding().llSlotContainer;
        container.removeAllViews();
        mSlotViews.clear();

        long dateMs = mSelectedScheduleType == TaskScheduleEntity.TYPE_ONCE
            ? mSelectedDateMs : DateUtils.todayStartMs();

        List<TimePeriodEntity> periods = mViewModel.getActivePeriodsForDate(dateMs);
        if (periods.isEmpty()) {
            return;
        }

        Set<Integer> occupied = mViewModel.getOccupiedSlots(
            mExistingSchedule != null ? mExistingSchedule.id : -1,
            dateMs);

        boolean isToday = dateMs == DateUtils.todayStartMs();
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

            for (int min = startMin; min < endMin; min += 10) {
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
                boolean isPast = isToday && min <= nowMinute;
                boolean isInSelectedRange = selectedStart >= 0
                    && min >= selectedStart && min < selectedStart + focusMinutes;
                boolean isInOccupiedRange = occupied.contains(min);

                boolean exceedsPeriodEnd = min + focusMinutes > endMin;
                boolean overlapsOccupied = false;
                if (!exceedsPeriodEnd && focusMinutes > 0) {
                    for (int check = min; check < min + focusMinutes; check += 10) {
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

    private void highlightSelectedSlot() {
        if (mSelectedSlotMinute < 0) return;
        for (TextView slot : mSlotViews) {
            // Check if this slot matches the selected minute
            CharSequence text = slot.getText();
            if (text != null && text.toString().equals(DateUtils.formatMinute(mSelectedSlotMinute))) {
                // We can't easily know from here, so just rely on refreshSlotView recreating everything
            }
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
        schedule.scheduleType = mSelectedScheduleType;
        schedule.scheduledTime = mSelectedSlotMinute;

        switch (mSelectedScheduleType) {
            case TaskScheduleEntity.TYPE_ONCE:
                schedule.scheduleValue = mSelectedDateMs;
                break;
            case TaskScheduleEntity.TYPE_DAILY:
                schedule.scheduleValue = 0;
                break;
            case TaskScheduleEntity.TYPE_WEEKLY:
                schedule.scheduleValue = mWeeklyBitmask;
                break;
            case TaskScheduleEntity.TYPE_MONTHLY:
                schedule.scheduleValue = mMonthlyDay;
                break;
        }

        Runnable onSaved = () -> requireActivity().runOnUiThread(() ->
            Navigation.findNavController(requireView()).popBackStack());

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
        if (mSelectedScheduleType == TaskScheduleEntity.TYPE_ONCE) {
            enabled = enabled && mSelectedDateMs > 0;
        } else if (mSelectedScheduleType == TaskScheduleEntity.TYPE_WEEKLY) {
            enabled = enabled && mWeeklyBitmask != 0;
        } else if (mSelectedScheduleType == TaskScheduleEntity.TYPE_MONTHLY) {
            enabled = enabled && mMonthlyDay >= 1 && mMonthlyDay <= 31;
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
