package com.nearby.justnow.ui.main;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.nearby.justnow.R;
import com.nearby.justnow.ui.base.NumberPickerStyleHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 截止时间选择器 — 复用步进 + PopupWindow 滚轮模式。
 * 范围 [ceilToNext15(nowMinute), period.endMinute]，步进 15 分钟。
 */
public class CutoffTimePickerDialog {

    /** 选择结果回调 */
    public interface OnTimeSelectedListener {
        void onTimeSelected(int endMinute);
    }

    /** 重置回调 */
    public interface OnCutoffResetListener {
        void onCutoffReset();
    }

    private static final int STEP_MINUTES = 15;
    private static final String[] MINUTE_DISPLAY_VALUES = {"00", "15", "30", "45"};

    /**
     * 显示截止时间选择器弹窗。
     *
     * @param context        上下文
     * @param anchorView     锚点视图（弹窗定位参考）
     * @param periodEndMinute 时段结束分钟数（上限）
     * @param listener       选择结果回调
     */
    public static void show(Context context, View anchorView,
                            int periodEndMinute, OnTimeSelectedListener listener,
                            OnCutoffResetListener resetListener) {
        Calendar cal = Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        // 最小值：当前时间向上取整到下一个 15 分钟
        int minMinute = ceilToNext15(nowMinute);
        // 必须 > 当前时间（不允许设为"现在"）
        if (minMinute <= nowMinute) minMinute += STEP_MINUTES;

        // 时段即将结束，不弹出选择器
        if (nowMinute >= periodEndMinute || minMinute > periodEndMinute) return;

        int minHour = minMinute / 60;
        int maxHour = periodEndMinute / 60;

        // 构建合法 hour 列表
        List<Integer> validHours = new ArrayList<>();
        for (int h = minHour; h <= maxHour; h++) {
            if (getMinMinuteForHour(h, minMinute, periodEndMinute) <=
                getMaxMinuteForHour(h, minMinute, periodEndMinute)) {
                validHours.add(h);
            }
        }
        if (validHours.isEmpty()) return;

        // inflate 布局（复用 popup_time_picker）
        View contentView = LayoutInflater.from(context)
            .inflate(R.layout.popup_time_picker, null, false);

        TextView tvTitle = contentView.findViewById(R.id.tv_popup_title);
        tvTitle.setText(context.getString(R.string.s_cutoff_time_title));

        NumberPicker hourPicker = contentView.findViewById(R.id.np_hour);
        hourPicker.setWrapSelectorWheel(false);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(validHours.size() - 1);
        hourPicker.setDisplayedValues(buildHourDisplayValues(validHours));
        hourPicker.setValue(0);

        NumberPicker minutePicker = contentView.findViewById(R.id.np_minute);
        minutePicker.setWrapSelectorWheel(false);
        // 初始化默认范围，避免 validMinuteIndices 为空时 setMaxValue(-1) 崩溃
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(3);
        minutePicker.setDisplayedValues(MINUTE_DISPLAY_VALUES);
        NumberPickerStyleHelper.applyTimeTextSize(hourPicker, minutePicker);

        Button btnReset = contentView.findViewById(R.id.btn_popup_reset);
        Button btnCancel = contentView.findViewById(R.id.btn_popup_cancel);
        Button btnConfirm = contentView.findViewById(R.id.btn_popup_confirm);

        // 截止时间 picker：隐藏取消，显示重置
        btnCancel.setVisibility(View.GONE);
        btnReset.setVisibility(resetListener != null ? View.VISIBLE : View.GONE);

        PopupWindow popup = new PopupWindow(contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);

        // 当前选中的合法 minute 索引列表
        List<Integer> validMinuteIndices = new ArrayList<>(4);
        final int finalMinMinute = minMinute;
        final int finalPeriodEndMinute = periodEndMinute;

        // 刷新 minute picker 选项
        Runnable refreshMinutePicker = () -> {
            int selectedHour = validHours.get(hourPicker.getValue());
            validMinuteIndices.clear();
            for (int i = 0; i < MINUTE_DISPLAY_VALUES.length; i++) {
                int m = selectedHour * 60 + i * STEP_MINUTES;
                if (m >= finalMinMinute && m <= finalPeriodEndMinute) {
                    validMinuteIndices.add(i);
                }
            }
            if (validMinuteIndices.isEmpty()) {
                // 重置为默认范围，避免 setDisplayedValues(null) 后状态不一致
                minutePicker.setDisplayedValues(null);
                minutePicker.setMinValue(0);
                minutePicker.setMaxValue(3);
                minutePicker.setDisplayedValues(MINUTE_DISPLAY_VALUES);
                btnConfirm.setEnabled(false);
                return;
            }
            int newMax = validMinuteIndices.size() - 1;
            minutePicker.setDisplayedValues(null);
            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(newMax);

            String[] displayed = new String[validMinuteIndices.size()];
            for (int j = 0; j < validMinuteIndices.size(); j++) {
                displayed[j] = MINUTE_DISPLAY_VALUES[validMinuteIndices.get(j)];
            }
            minutePicker.setDisplayedValues(displayed);

            if (minutePicker.getValue() > newMax) {
                minutePicker.setValue(newMax);
            }
            btnConfirm.setEnabled(true);
        };

        refreshMinutePicker.run();

        hourPicker.setOnValueChangedListener((picker, oldVal, newVal) -> refreshMinutePicker.run());
        minutePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (!validMinuteIndices.isEmpty()) {
                btnConfirm.setEnabled(true);
            }
        });

        btnCancel.setOnClickListener(v -> popup.dismiss());
        btnReset.setOnClickListener(v -> {
            if (resetListener != null) resetListener.onCutoffReset();
            popup.dismiss();
        });
        btnConfirm.setOnClickListener(v -> {
            if (validMinuteIndices.isEmpty()) return;
            int selectedHour = validHours.get(hourPicker.getValue());
            int selectedMinuteIndex = validMinuteIndices.get(minutePicker.getValue());
            int result = selectedHour * 60 + selectedMinuteIndex * STEP_MINUTES;
            listener.onTimeSelected(result);
            popup.dismiss();
        });

        // 左下角弹出
        popup.showAtLocation(anchorView, android.view.Gravity.BOTTOM | android.view.Gravity.START, 16, 16);
    }

    /** 当前时间向上取整到下一个 15 分钟 */
    private static int ceilToNext15(int minute) {
        int remainder = minute % STEP_MINUTES;
        if (remainder == 0) return minute;
        return minute + (STEP_MINUTES - remainder);
    }

    /** 指定 hour 下的最小合法 minute */
    private static int getMinMinuteForHour(int hour, int minMinute, int periodEndMinute) {
        int hourStart = hour * 60;
        int result = Math.max(hourStart, minMinute);
        // 向上取整到 15 分钟
        int remainder = result % STEP_MINUTES;
        if (remainder != 0) result += STEP_MINUTES - remainder;
        return Math.min(result, periodEndMinute);
    }

    /** 指定 hour 下的最大合法 minute */
    private static int getMaxMinuteForHour(int hour, int minMinute, int periodEndMinute) {
        int hourEnd = hour * 60 + 45;
        return Math.min(hourEnd, periodEndMinute);
    }

    private static String[] buildHourDisplayValues(List<Integer> hours) {
        String[] values = new String[hours.size()];
        for (int i = 0; i < hours.size(); i++) {
            values[i] = String.format("%02d", hours.get(i));
        }
        return values;
    }
}
