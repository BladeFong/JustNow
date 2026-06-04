package com.nearby.justnow.ui.period;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.card.MaterialCardView;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;

import com.nearby.justnow.databinding.DialogScheduleProfileBinding;
import com.nearby.justnow.databinding.FragmentPeriodConfigBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 时间段配置 — 显示5个时段的默认值，支持修改
 */
public class PeriodConfigFragment extends BaseFragment<FragmentPeriodConfigBinding> {

    private static final DateTimeFormatter sDisplayDateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private PeriodConfigViewModel mViewModel;
    private PeriodGroupAdapter mRegularAdapter;
    private PeriodGroupAdapter mGroupAdapter;
    private List<TimePeriodGroupEntity> mGroups = new ArrayList<>();
    private List<TimePeriodEntity> mPeriods = new ArrayList<>();

    @Override
    protected FragmentPeriodConfigBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentPeriodConfigBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
            .get(PeriodConfigViewModel.class);

        mRegularAdapter = new PeriodGroupAdapter(mViewModel, this);
        mGroupAdapter = new PeriodGroupAdapter(mViewModel, this);
        getBinding().rvRegularGroup.setLayoutManager(new LinearLayoutManager(requireContext()));
        getBinding().rvRegularGroup.setAdapter(mRegularAdapter);
        getBinding().rvRegularGroup.setNestedScrollingEnabled(false);
        getBinding().rvPeriodGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        getBinding().rvPeriodGroups.setAdapter(mGroupAdapter);

        updateScheduleProfileRow();
        getBinding().cardScheduleProfile.setOnClickListener(v -> showScheduleProfileDialog());
        getBinding().scheduleProfileRow.setOnClickListener(v -> showScheduleProfileDialog());
        getBinding().btnScheduleProfileExpand.setOnClickListener(v -> showScheduleProfileDialog());

        mViewModel.getAllGroups().observe(getViewLifecycleOwner(), groups -> {
            mGroups = groups != null ? groups : new ArrayList<>();
            refreshGroupRows();
        });
        mViewModel.getAllPeriods().observe(getViewLifecycleOwner(), periods -> {
            mPeriods = periods != null ? periods : new ArrayList<>();
            refreshGroupRows();
        });
    }

    private void updateScheduleProfileRow() {
        getBinding().tvScheduleProfileTitle.setText(
            mViewModel.getScheduleProfileName(getResources()));
        getBinding().tvScheduleProfileDesc.setText(
            mViewModel.getScheduleProfileDescription(getResources()));
    }

    private void showScheduleProfileDialog() {
        List<String> profiles = mViewModel.getAvailableScheduleProfiles();
        String current = mViewModel.getScheduleProfile();
        int checkedIndex = 0;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).equals(current)) checkedIndex = i;
        }

        final int[] selectedIndex = {checkedIndex};
        DialogScheduleProfileBinding dialogBinding = DialogScheduleProfileBinding.inflate(
            LayoutInflater.from(requireContext()));
        List<TextView> optionViews = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            TextView optionView = createProfileOptionView(profiles.get(i), profiles, selectedIndex,
                dialogBinding, optionViews);
            optionViews.add(optionView);
            dialogBinding.llScheduleProfileOptions.addView(optionView);
        }
        updateProfileDialogSelection(dialogBinding, optionViews, profiles, selectedIndex[0]);

        AlertDialog scheduleDialog = new AlertDialog.Builder(requireContext())
            .setTitle(R.string.s_schedule_profile)
            .setView(dialogBinding.getRoot())
            .setPositiveButton(R.string.s_confirm, (confirmDialog, which) -> {
                mViewModel.setScheduleProfile(profiles.get(selectedIndex[0]));
                updateScheduleProfileRow();
                refreshGroupRows();
            })
            .setNegativeButton(R.string.s_cancel, null)
            .create();
        scheduleDialog.show();
    }

    private TextView createProfileOptionView(String profile, List<String> profiles,
                                             int[] selectedIndex,
                                             DialogScheduleProfileBinding dialogBinding,
                                             List<TextView> optionViews) {
        TextView optionView = new TextView(requireContext());
        optionView.setText(PeriodTextResolver.getProfileName(getResources(), profile));
        optionView.setTextAppearance(requireContext(), R.style.TextAppearance_JustNow_Dialog_Body);
        optionView.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        optionView.setLayoutParams(params);
        optionView.setClickable(true);
        optionView.setFocusable(true);
        optionView.setOnClickListener(v -> {
            selectedIndex[0] = profiles.indexOf(profile);
            updateProfileDialogSelection(dialogBinding, optionViews, profiles, selectedIndex[0]);
        });
        return optionView;
    }

    private void updateProfileDialogSelection(DialogScheduleProfileBinding dialogBinding,
                                              List<TextView> optionViews,
                                              List<String> profiles,
                                              int selectedIndex) {
        for (int i = 0; i < optionViews.size(); i++) {
            optionViews.get(i).setBackgroundResource(i == selectedIndex
                ? R.drawable.bg_schedule_profile_option_selected
                : R.drawable.bg_schedule_profile_option_normal);
        }
        dialogBinding.tvScheduleProfileSelectedDesc.setText(
            PeriodTextResolver.getProfileDescription(getResources(), profiles.get(selectedIndex)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String minuteToTime(int minute) {
        return String.format("%02d:%02d", minute / 60, minute % 60);
    }

    private void refreshGroupRows() {
        // 先检查春节未来数据，再构建列表
        mViewModel.checkSpringFestivalDataAsync(hasSpringFuture -> {
            if (!isAdded()) return;
            mViewModel.resolveActiveGroupTypeAsync(mGroups, activeGroupType -> {
                if (!isAdded()) return;
                List<PeriodConfigViewModel.PeriodGroupItem> items =
                    mViewModel.buildGroupItems(mGroups, mPeriods, getResources(), activeGroupType, hasSpringFuture);
                List<PeriodConfigViewModel.PeriodGroupItem> regular = new ArrayList<>();
                for (PeriodConfigViewModel.PeriodGroupItem item : items) {
                    if (PeriodGroupType.isRegular(item.group.groupType)) {
                        regular.add(item);
                    }
                }
                // 先展示常规组
                mRegularAdapter.setItems(regular, mPeriods);
                mGroupAdapter.setItems(new ArrayList<>(), mPeriods);

                // 异步检查节假日数据，有则追加其他组
                mViewModel.checkHolidayDataAsync(hasData -> {
                    if (!isAdded()) return;
                    if (hasData) {
                        List<PeriodConfigViewModel.PeriodGroupItem> others = new ArrayList<>();
                        for (PeriodConfigViewModel.PeriodGroupItem item : items) {
                            if (!PeriodGroupType.isRegular(item.group.groupType)
                                && mViewModel.isGroupVisibleForCurrentProfile(item.group.groupType)) {
                                others.add(item);
                            }
                        }
                        mGroupAdapter.setItems(others, mPeriods);
                    }
                });
            });
        });
    }

    /** 展示时间段组信息。 */
    private static class PeriodGroupAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<
        PeriodGroupAdapter.ViewHolder> {

        private static final int VIEW_TYPE_REGULAR = 1;
        private static final int VIEW_TYPE_GROUP = 2;

        /** 时段编辑 — 步进粒度（分钟） */
        private static final int STEP_MINUTES = 15;
        /** 早上最早可选时间：06:00 */
        private static final int MORNING_MIN_MINUTE = 360;
        /** 晚上最晚可选时间：23:00 */
        private static final int EVENING_MAX_MINUTE = 1380;
        /** 午休/晚餐最短时长（分钟） */
        private static final int MIN_SLOT_MINUTES = 60;
        /** 其余时段最短时长（分钟） */
        private static final int MIN_OTHER_MINUTES = 30;
        /** 分钟滚轮显示值：仅 00/15/30/45 */
        private static final String[] MINUTE_DISPLAY_VALUES = {"00", "15", "30", "45"};

        private final PeriodConfigViewModel vm;
        private final WeakReference<PeriodConfigFragment> mFragmentRef;
        private final Handler mLongPressHandler = new Handler(Looper.getMainLooper());
        private List<PeriodConfigViewModel.PeriodGroupItem> items = new ArrayList<>();
        private List<TimePeriodEntity> mAllPeriods = new ArrayList<>();

        PeriodGroupAdapter(PeriodConfigViewModel vm, PeriodConfigFragment fragment) {
            this.vm = vm;
            this.mFragmentRef = new WeakReference<>(fragment);
        }

        void setItems(List<PeriodConfigViewModel.PeriodGroupItem> items, List<TimePeriodEntity> allPeriods) {
            this.items = items != null ? items : new ArrayList<>();
            this.mAllPeriods = allPeriods != null ? allPeriods : new ArrayList<>();
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            PeriodConfigViewModel.PeriodGroupItem item = items.get(position);
            return PeriodGroupType.isRegular(item.group.groupType)
                ? VIEW_TYPE_REGULAR : VIEW_TYPE_GROUP;
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = viewType == VIEW_TYPE_REGULAR
                ? R.layout.item_regular_period_group : R.layout.item_period;
            View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PeriodConfigViewModel.PeriodGroupItem item = items.get(position);
            TimePeriodGroupEntity group = item.group;
            boolean isRegular = PeriodGroupType.isRegular(group.groupType);
            if (isRegular) {
                bindRegularPeriods(holder, item.periodLabels, item.periodRanges);
                holder.row.setOnClickListener(v -> showPeriodGroupDetails(holder, item));
                holder.regularEdit.setOnClickListener(v -> showPeriodGroupDetails(holder, item));
                return;
            }

            holder.name.setText(vm.getGroupName(group.groupType, holder.itemView.getResources()));
            holder.time.setText(item.periodSummary);
            holder.time.setVisibility(item.periodSummary.isEmpty() ? View.GONE : View.VISIBLE);
            holder.notice.setVisibility(item.noticeMessageResId != 0 ? View.VISIBLE : View.GONE);
            if (item.noticeMessageResId != 0) {
                holder.notice.setText(item.noticeMessageResId);
            }

            bindGroupHighlight(holder, item.active);
            holder.enabled.setVisibility(View.VISIBLE);
            holder.enabled.setOnCheckedChangeListener(null);
            holder.enabled.setChecked(group.enabled);

            if (item.springFestivalBlocked) {
                // 春节无未来数据：禁用开关、屏蔽点击编辑
                holder.enabled.setEnabled(false);
                holder.row.setOnClickListener(null);
                holder.row.setClickable(false);
            } else {
                holder.row.setOnClickListener(v -> showPeriodGroupDetails(holder, item));
                boolean isHoliday = PeriodGroupType.isHoliday(group.groupType);
                holder.enabled.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && !isHoliday && item.periodSummary.isEmpty()) {
                        // 无预设时段的非假日组：弹回开关，打开编辑对话框
                        button.setOnCheckedChangeListener(null);
                        button.setChecked(false);
                        button.setOnCheckedChangeListener((b, c) -> vm.updateGroupEnabled(group, c));
                        showPeriodGroupDetails(holder, item);
                    } else {
                        vm.updateGroupEnabled(group, checked);
                    }
                });
            }
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            super.onViewRecycled(holder);
            mLongPressHandler.removeCallbacksAndMessages(null);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            MaterialCardView card;
            LinearLayout regularLines;
            LinearLayout row;
            TextView name, time, notice;
            ImageButton regularEdit;
            CompoundButton enabled;

            ViewHolder(View v) {
                super(v);
                card = v.findViewById(R.id.card_period_group);
                row = v.findViewById(R.id.period_group_row);
                if (row == null) row = v.findViewById(R.id.regular_period_group_row);
                name = v.findViewById(R.id.tv_period_name);
                time = v.findViewById(R.id.tv_period_time);
                notice = v.findViewById(R.id.tv_period_notice);
                enabled = v.findViewById(R.id.sw_period_group_enabled);
                regularLines = v.findViewById(R.id.ll_regular_period_lines);
                regularEdit = v.findViewById(R.id.btn_regular_period_edit);
            }
        }

        private void bindRegularPeriods(ViewHolder holder, List<String> labels,
                                        List<String> ranges) {
            holder.regularLines.removeAllViews();
            for (int rowIndex = 0; rowIndex < ranges.size(); rowIndex += 2) {
                LinearLayout row = new LinearLayout(holder.itemView.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.addView(createRegularPeriodCell(holder, labels, ranges, rowIndex),
                    createRegularCellParams(holder, true));
                row.addView(createRegularPeriodCell(holder, labels, ranges, rowIndex + 1),
                    createRegularCellParams(holder, false));
                holder.regularLines.addView(row);

                View divider = new View(holder.itemView.getContext());
                divider.setBackgroundResource(R.drawable.bg_regular_period_line);
                holder.regularLines.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(holder, 1)));
            }
        }

        private LinearLayout createRegularPeriodCell(ViewHolder holder, List<String> labels,
                                                     List<String> ranges, int index) {
            LinearLayout cell = new LinearLayout(holder.itemView.getContext());
            cell.setGravity(android.view.Gravity.CENTER_VERTICAL);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(0, dp(holder, 4), dp(holder, 8), dp(holder, 4));
            if (index >= ranges.size()) return cell;

            TextView label = new TextView(holder.itemView.getContext());
            label.setText(labels.get(index));
            label.setTextAppearance(holder.itemView.getContext(), R.style.TextAppearance_JustNow_Caption);
            label.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
            cell.addView(label);

            TextView range = new TextView(holder.itemView.getContext());
            range.setText(ranges.get(index));
            range.setTextAppearance(holder.itemView.getContext(), R.style.TextAppearance_JustNow_Caption);
            range.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_tertiary));
            cell.addView(range);
            return cell;
        }

        private LinearLayout.LayoutParams createRegularCellParams(ViewHolder holder,
                                                                  boolean firstColumn) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (firstColumn) params.rightMargin = dp(holder, 16);
            return params;
        }

        private int dp(ViewHolder holder, int value) {
            return (int) (value * holder.itemView.getResources().getDisplayMetrics().density + 0.5f);
        }

        private static int dp(android.content.Context ctx, int value) {
            return (int) (value * ctx.getResources().getDisplayMetrics().density + 0.5f);
        }

        private void bindGroupHighlight(ViewHolder holder, boolean active) {
            if (holder.card == null) return;
            int backgroundRes = active
                ? R.color.period_group_active_background : android.R.color.white;
            int strokeRes = active
                ? R.color.period_group_active_stroke : R.color.divider;
            holder.card.setCardBackgroundColor(ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.getContext(), backgroundRes)));
            holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), strokeRes));
            holder.card.setStrokeWidth(active
                ? holder.itemView.getResources().getDimensionPixelSize(R.dimen.period_group_active_stroke_width)
                : holder.itemView.getResources().getDimensionPixelSize(R.dimen.period_group_stroke_width));
        }

        private void showPeriodGroupDetails(ViewHolder holder,
                                            PeriodConfigViewModel.PeriodGroupItem item) {
            TimePeriodGroupEntity group = item.group;
            android.content.Context ctx = holder.itemView.getContext();

            // 在后台线程初始化默认数据，完成后回主线程展示
            vm.ensureDefaultsAndLoadPeriods(group, groupPeriods -> {
                PeriodConfigFragment fragment = mFragmentRef.get();
                if (fragment == null || !fragment.isAdded()) return;
                    View dialogView = LayoutInflater.from(ctx)
                        .inflate(R.layout.dialog_edit_period_group, null);

                    TextView tvTitle = dialogView.findViewById(R.id.tv_period_edit_title);
                    LinearLayout llDateRange = dialogView.findViewById(R.id.ll_date_range);
                    TextView tvStartDate = dialogView.findViewById(R.id.et_start_date);
                    TextView tvEndDate = dialogView.findViewById(R.id.et_end_date);
                    LinearLayout llPeriods = dialogView.findViewById(R.id.ll_periods);
                    Button btnCancel = dialogView.findViewById(R.id.btn_period_edit_cancel);
                    Button btnSave = dialogView.findViewById(R.id.btn_period_edit_save);

                    boolean hasDateRange = !PeriodGroupType.isRegular(group.groupType)
                        && !PeriodGroupType.WORKDAY.equals(group.groupType);
                    llDateRange.setVisibility(hasDateRange ? View.VISIBLE : View.GONE);
                    tvTitle.setText(vm.getGroupName(group.groupType, ctx.getResources()));

                    Calendar cal = Calendar.getInstance();
                    if (hasDateRange) {
                        setupDatePicker(ctx, tvStartDate, group.startMonthDay, cal);
                        setupDatePicker(ctx, tvEndDate, group.endMonthDay, cal);
                    }

                    // 每行视图引用，用于步进/滚轮后刷新
                    final List<PeriodEditRowView> rowViews = new ArrayList<>();

                    // 刷新所有行的时间文字和按钮启用状态
                    final Runnable refreshRows = () -> {
                        for (int i = 0; i < rowViews.size(); i++) {
                            PeriodEditRowView rv = rowViews.get(i);
                            TimePeriodEntity p = groupPeriods.get(i);
                            rv.tvStart.setText(minuteToTime(p.startMinute));
                            rv.tvEnd.setText(minuteToTime(p.endMinute));
                            updateStepButtonState(rv.btnStartDec, canStep(groupPeriods, i, true, true));
                            updateStepButtonState(rv.btnStartInc, canStep(groupPeriods, i, true, false));
                            updateStepButtonState(rv.btnEndDec, canStep(groupPeriods, i, false, true));
                            updateStepButtonState(rv.btnEndInc, canStep(groupPeriods, i, false, false));
                        }
                    };

                    // 构建每行：label | ◀ start | HH:MM | ▶ start | — | ◀ end | HH:MM | ▶ end
                    for (int i = 0; i < groupPeriods.size(); i++) {
                        TimePeriodEntity period = groupPeriods.get(i);
                        final int idx = i;
                        PeriodEditRowView rv = buildPeriodEditRow(ctx, period, groupPeriods, idx, refreshRows);
                        rowViews.add(rv);
                        llPeriods.addView(rv.row);
                    }

                    // 约束说明文字（常驻，XML inflate）
                    View constraintHint = LayoutInflater.from(ctx)
                        .inflate(R.layout.view_period_constraint_hint, llPeriods, false);
                    llPeriods.addView(constraintHint);

                    // 初始刷新按钮状态
                    refreshRows.run();

                    AlertDialog dialog = new AlertDialog.Builder(ctx)
                        .setView(dialogView)
                        .create();
                    btnCancel.setOnClickListener(v -> dialog.dismiss());
                    btnSave.setOnClickListener(v -> {
                        if (hasDateRange) {
                            group.startMonthDay =
                                formatMonthDayFromDisplay(tvStartDate.getText().toString().trim());
                            group.endMonthDay =
                                formatMonthDayFromDisplay(tvEndDate.getText().toString().trim());
                        }
                        group.lastEditedAt = System.currentTimeMillis();
                        vm.updateGroupAndPeriods(group, groupPeriods);
                        dialog.dismiss();
                    });
                    dialog.show();
            });
        }

        /** 单行视图引用 */
        private static class PeriodEditRowView {
            LinearLayout row;
            TextView tvLabel;
            TextView tvStart;
            TextView tvEnd;
            ImageButton btnStartDec;
            ImageButton btnStartInc;
            ImageButton btnEndDec;
            ImageButton btnEndInc;
        }

        /** 构建时段编辑行：inflate XML 布局，绑定事件 */
        private PeriodEditRowView buildPeriodEditRow(android.content.Context ctx,
                                                      TimePeriodEntity period,
                                                      List<TimePeriodEntity> allPeriods,
                                                      int index,
                                                      Runnable onChanged) {
            PeriodEditRowView rv = new PeriodEditRowView();
            Resources res = ctx.getResources();

            LinearLayout row = (LinearLayout) LayoutInflater.from(ctx)
                .inflate(R.layout.item_period_edit_row, null, false);
            rv.row = row;

            // 时段名称
            rv.tvLabel = row.findViewById(R.id.tv_period_edit_label);
            rv.tvLabel.setText(vm.getPeriodName(period.nameKey, res));

            // 开始时间
            rv.tvStart = row.findViewById(R.id.tv_period_edit_start);
            rv.tvStart.setText(minuteToTime(period.startMinute));
            rv.tvStart.setOnClickListener(v -> showTimePopup(rv.tvStart, allPeriods, index, true, onChanged));

            // 结束时间
            rv.tvEnd = row.findViewById(R.id.tv_period_edit_end);
            rv.tvEnd.setText(minuteToTime(period.endMinute));
            rv.tvEnd.setOnClickListener(v -> showTimePopup(rv.tvEnd, allPeriods, index, false, onChanged));

            // 步进按钮
            rv.btnStartDec = row.findViewById(R.id.btn_start_dec);
            rv.btnStartInc = row.findViewById(R.id.btn_start_inc);
            rv.btnEndDec = row.findViewById(R.id.btn_end_dec);
            rv.btnEndInc = row.findViewById(R.id.btn_end_inc);

            // 绑定步进事件
            setupStepButton(rv.btnStartDec, () -> {
                applyStep(allPeriods, index, true, true);
                onChanged.run();
            });
            setupStepButton(rv.btnStartInc, () -> {
                applyStep(allPeriods, index, true, false);
                onChanged.run();
            });
            setupStepButton(rv.btnEndDec, () -> {
                applyStep(allPeriods, index, false, true);
                onChanged.run();
            });
            setupStepButton(rv.btnEndInc, () -> {
                applyStep(allPeriods, index, false, false);
                onChanged.run();
            });

            return rv;
        }

        /** 更新步进按钮启用/禁用外观（tint 由 @color/step_button_tint selector 自动处理） */
        private void updateStepButtonState(ImageButton btn, boolean enabled) {
            btn.setEnabled(enabled);
        }

        /** 设置步进按钮：短按单次步进，长按加速重复 */
        private void setupStepButton(ImageButton btn, Runnable onStep) {
            btn.setOnClickListener(v -> onStep.run());

            btn.setOnLongClickListener(v -> {
                mLongPressHandler.removeCallbacksAndMessages(null);
                mLongPressHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onStep.run();
                        mLongPressHandler.postDelayed(this, 100);
                    }
                }, 400);
                return true;
            });

            btn.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    mLongPressHandler.removeCallbacksAndMessages(null);
                }
                return false;
            });
        }

        /** 磁盘分区联动：应用步进，自动调整相邻时段边界 */
        private void applyStep(List<TimePeriodEntity> periods, int index,
                               boolean isStart, boolean decrease) {
            int delta = decrease ? -STEP_MINUTES : STEP_MINUTES;
            TimePeriodEntity period = periods.get(index);
            if (isStart) {
                period.startMinute += delta;
                // 左边时段 end 同步
                if (index > 0) {
                    periods.get(index - 1).endMinute = period.startMinute;
                }
            } else {
                period.endMinute += delta;
                // 右边时段 start 同步
                if (index < periods.size() - 1) {
                    periods.get(index + 1).startMinute = period.endMinute;
                }
            }
        }

        /** 模拟步进后是否合规（用于按钮启用/禁用判断） */
        private boolean canStep(List<TimePeriodEntity> periods, int index,
                                boolean isStart, boolean decrease) {
            int delta = decrease ? -STEP_MINUTES : STEP_MINUTES;
            TimePeriodEntity p = periods.get(index);
            int currentValue = isStart ? p.startMinute : p.endMinute;
            return isTimeValid(periods, index, isStart, currentValue + delta);
        }

        /** 指定分钟值是否合规（约束校验，供步进按钮和 PopupWindow 共用） */
        private boolean isTimeValid(List<TimePeriodEntity> periods, int index,
                                    boolean isStart, int newValue) {
            TimePeriodEntity p = periods.get(index);
            String nameKey = p.nameKey;

            // 编辑开始时间：不能 >= endMinute
            if (isStart && newValue >= p.endMinute) return false;
            // 编辑结束时间：不能 <= startMinute
            if (!isStart && newValue <= p.startMinute) return false;

            // 早上 start 不得早于 06:00
            if (isStart && PeriodNameKey.MORNING.equals(nameKey) && newValue < MORNING_MIN_MINUTE) {
                return false;
            }
            // 晚上 end 不得晚于 23:00
            if (!isStart && PeriodNameKey.EVENING.equals(nameKey) && newValue > EVENING_MAX_MINUTE) {
                return false;
            }

            // 检查本时段自身时长
            int selfStart = isStart ? newValue : p.startMinute;
            int selfEnd = isStart ? p.endMinute : newValue;
            if (!isPeriodDurationValid(nameKey, selfStart, selfEnd)) return false;

            // 检查受影响的相邻时段时长
            if (isStart && index > 0) {
                TimePeriodEntity prev = periods.get(index - 1);
                if (!isPeriodDurationValid(prev.nameKey, prev.startMinute, newValue)) return false;
            }
            if (!isStart && index < periods.size() - 1) {
                TimePeriodEntity next = periods.get(index + 1);
                if (!isPeriodDurationValid(next.nameKey, newValue, next.endMinute)) return false;
            }

            return true;
        }

        /** 时段时长是否合规 */
        private boolean isPeriodDurationValid(String nameKey, int start, int end) {
            int duration = end - start;
            if (PeriodNameKey.NOON.equals(nameKey) || PeriodNameKey.DINNER.equals(nameKey)) {
                return duration >= MIN_SLOT_MINUTES;
            }
            return duration >= MIN_OTHER_MINUTES;
        }

        /** 当前 hour 下合法 minute 索引列表（0=00,1=15,2=30,3=45），被 updateMinutePicker 复用 */
        private final List<Integer> mValidMinuteIndices = new ArrayList<>(4);

        /** 根据当前 hour 更新 minute NumberPicker 的可选项和确认按钮状态 */
        private void updateMinutePickerForHour(List<TimePeriodEntity> periods, int index,
                                               boolean isStart, int hour,
                                               NumberPicker minutePicker, Button btnConfirm) {
            mValidMinuteIndices.clear();
            for (int i = 0; i < MINUTE_DISPLAY_VALUES.length; i++) {
                if (isTimeValid(periods, index, isStart, hour * 60 + i * 15)) {
                    mValidMinuteIndices.add(i);
                }
            }

            if (mValidMinuteIndices.isEmpty()) {
                minutePicker.setDisplayedValues(null);
                btnConfirm.setEnabled(false);
                return;
            }

            // 安全顺序：先清空旧值 → 设范围 → 设新值 → 设选中项
            int newMax = mValidMinuteIndices.size() - 1;
            minutePicker.setDisplayedValues(null);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(newMax);

            // 构造展示值数组并应用
            String[] displayed = new String[mValidMinuteIndices.size()];
            for (int j = 0; j < mValidMinuteIndices.size(); j++) {
                displayed[j] = MINUTE_DISPLAY_VALUES[mValidMinuteIndices.get(j)];
            }
            minutePicker.setDisplayedValues(displayed);

            // 当前选中值若已被过滤掉，移到最近的合法值
            int currentPickerVal = minutePicker.getValue();
            if (currentPickerVal > newMax) {
                minutePicker.setValue(newMax);
            }

            // 确认按钮跟随当前选中值是否合法
            int selectedIndex = mValidMinuteIndices.get(minutePicker.getValue());
            int selectedMinute = hour * 60 + selectedIndex * 15;
            btnConfirm.setEnabled(isTimeValid(periods, index, isStart, selectedMinute));
        }

        /** 弹出含 hour + minute 双滚轮的 PopupWindow 时间选择器 */
        private void showTimePopup(View anchor, List<TimePeriodEntity> periods,
                                    int index, boolean isStart, Runnable onChanged) {
            android.content.Context ctx = anchor.getContext();
            TimePeriodEntity period = periods.get(index);
            int currentMinute = isStart ? period.startMinute : period.endMinute;
            int currentHour = currentMinute / 60;
            int currentMinuteIndex = (currentMinute % 60) / 15;

            // 遍历 0-23，找到至少有一个合法 minute 的 hour 范围
            int minValidHour = 0;
            int maxValidHour = 23;
            boolean foundMin = false;
            for (int h = 0; h <= 23; h++) {
                boolean hourValid = false;
                for (int m = 0; m <= 3; m++) {
                    if (isTimeValid(periods, index, isStart, h * 60 + m * 15)) {
                        hourValid = true;
                        break;
                    }
                }
                if (hourValid) {
                    if (!foundMin) { minValidHour = h; foundMin = true; }
                    maxValidHour = h;
                }
            }

            // inflate XML 布局
            LinearLayout content = (LinearLayout) LayoutInflater.from(ctx)
                .inflate(R.layout.popup_time_picker, null, false);

            TextView tvTitle = content.findViewById(R.id.tv_popup_title);
            tvTitle.setText(vm.getPeriodName(period.nameKey, ctx.getResources())
                + " · " + (isStart ? ctx.getString(R.string.s_start_date) : ctx.getString(R.string.s_end_date)));

            NumberPicker hourPicker = content.findViewById(R.id.np_hour);
            hourPicker.setWrapSelectorWheel(false);
            hourPicker.setMinValue(minValidHour);
            hourPicker.setMaxValue(maxValidHour);
            hourPicker.setValue(currentHour);

            NumberPicker minutePicker = content.findViewById(R.id.np_minute);
            minutePicker.setWrapSelectorWheel(false);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(3);
            minutePicker.setValue(currentMinuteIndex);

            Button btnCancel = content.findViewById(R.id.btn_popup_cancel);
            Button btnConfirm = content.findViewById(R.id.btn_popup_confirm);

            PopupWindow popup = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);

            // 初始化 minute 选项 + 确认按钮状态
            updateMinutePickerForHour(periods, index, isStart, currentHour, minutePicker, btnConfirm);

            // hour 改变时动态过滤 minute 选项
            hourPicker.setOnValueChangedListener((picker, oldVal, newVal) ->
                updateMinutePickerForHour(periods, index, isStart, newVal, minutePicker, btnConfirm));

            // minute 改变时刷新确认按钮状态
            minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                if (!mValidMinuteIndices.isEmpty() && newVal < mValidMinuteIndices.size()) {
                    int idx = mValidMinuteIndices.get(newVal);
                    int minute = hourPicker.getValue() * 60 + idx * 15;
                    btnConfirm.setEnabled(isTimeValid(periods, index, isStart, minute));
                }
            });

            btnCancel.setOnClickListener(v -> popup.dismiss());
            btnConfirm.setOnClickListener(v -> {
                if (mValidMinuteIndices.isEmpty()) return;
                int idx = mValidMinuteIndices.get(minutePicker.getValue());
                int newMinute = hourPicker.getValue() * 60 + idx * 15;

                // 应用变更 + 磁盘分区联动
                if (isStart) {
                    period.startMinute = newMinute;
                    if (index > 0) periods.get(index - 1).endMinute = newMinute;
                } else {
                    period.endMinute = newMinute;
                    if (index < periods.size() - 1) periods.get(index + 1).startMinute = newMinute;
                }

                onChanged.run();
                popup.dismiss();
            });

            // 测量并居中定位
            content.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int popupWidth = content.getMeasuredWidth();
            int anchorWidth = anchor.getWidth();
            int xOff = (anchorWidth - popupWidth) / 2;
            popup.showAsDropDown(anchor, xOff, 0);
        }

        private void setupDatePicker(android.content.Context ctx, TextView tv,
                                      String currentValue, Calendar cal) {
            tv.setText(formatDisplayDate(currentValue, cal));
            tv.setOnClickListener(v -> {
                int[] parts = parseDisplayDateParts(tv.getText().toString().trim(), cal);
                new android.app.DatePickerDialog(ctx,
                    (picker, y, m, d) -> tv.setText(String.format(java.util.Locale.US,
                        "%04d-%02d-%02d", y, m + 1, d)),
                    parts[0], parts[1], parts[2]).show();
            });
        }

        private static String formatDisplayDate(String monthDay, Calendar cal) {
            if (monthDay == null || monthDay.trim().isEmpty()) return "";
            String trimmed = monthDay.trim();
            if (trimmed.length() == 10 && trimmed.charAt(4) == '-') return trimmed;
            String[] parts = trimmed.split("-");
            if (parts.length != 2) return trimmed;
            try {
                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                LocalDate date = LocalDate.of(cal.get(Calendar.YEAR), month, day);
                return date.format(sDisplayDateFormat);
            } catch (RuntimeException ignored) {
                return trimmed;
            }
        }

        private static int[] parseDisplayDateParts(String value, Calendar cal) {
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            if (value == null || value.isEmpty()) {
                return new int[] {year, month, day};
            }
            String trimmed = value.trim();
            try {
                if (trimmed.length() == 10 && trimmed.charAt(4) == '-') {
                    LocalDate date = LocalDate.parse(trimmed, sDisplayDateFormat);
                    return new int[] {date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()};
                }
                String[] parts = trimmed.split("-");
                if (parts.length == 2) {
                    month = Integer.parseInt(parts[0]) - 1;
                    day = Integer.parseInt(parts[1]);
                }
            } catch (RuntimeException ignored) {}
            return new int[] {year, month, day};
        }

        private static String formatMonthDayFromDisplay(String value) {
            if (value == null || value.isEmpty()) return "";
            String trimmed = value.trim();
            try {
                if (trimmed.length() == 10 && trimmed.charAt(4) == '-') {
                    LocalDate date = LocalDate.parse(trimmed, sDisplayDateFormat);
                    return String.format(java.util.Locale.US, "%02d-%02d",
                        date.getMonthValue(), date.getDayOfMonth());
                }
                if (trimmed.length() == 5 && trimmed.charAt(2) == '-') {
                    return trimmed;
                }
            } catch (RuntimeException ignored) {}
            return trimmed;
        }
    }
}
