package com.nearby.justnow.ui.taskschedule;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Consumer;

import com.nearby.justnow.R;

/**
 * 每月日期选择 Dialog：展示不带年月的 7x5 月历网格，单选 1~31。
 */
public final class MonthlyDayPickerDialog {

    private static final int TOTAL_CELLS = 35;
    private static final int VALID_DAYS = 31;
    /** 1 号在网格中的 index（前面留 2 个占位空格，5 行 7 列对称布局）。 */
    private static final int DAY_START_INDEX = 2;

    private MonthlyDayPickerDialog() {
        // utility
    }

    /**
     * 弹出月历选择 Dialog。
     *
     * @param context     上下文
     * @param initialDay  初始选中日（1~31），超出范围按 1 处理
     * @param onConfirmed 点击确认时回调，传入选中日
     */
    public static void show(Context context, int initialDay, Consumer<Integer> onConfirmed) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_monthly_day_picker, null, false);
        GridLayout grid = view.findViewById(R.id.gl_monthly_grid);

        TextView[] dayCells = new TextView[VALID_DAYS];
        State state = new State();
        state.selectedDay = (initialDay >= 1 && initialDay <= VALID_DAYS) ? initialDay : 1;

        for (int i = 0; i < TOTAL_CELLS; i++) {
            TextView cell = (TextView) inflater.inflate(
                R.layout.item_monthly_day_cell, grid, false);

            GridLayout.LayoutParams params = (GridLayout.LayoutParams) cell.getLayoutParams();
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.width = 0;
            cell.setLayoutParams(params);

            int dayIndex = i - DAY_START_INDEX; // 0~30 对应 1~31，其余为占位
            if (dayIndex >= 0 && dayIndex < VALID_DAYS) {
                int day = dayIndex + 1;
                cell.setText(String.valueOf(day));
                cell.setSelected(day == state.selectedDay);
                cell.setOnClickListener(v -> {
                    if (state.selectedDay >= 1 && state.selectedDay <= VALID_DAYS) {
                        dayCells[state.selectedDay - 1].setSelected(false);
                    }
                    state.selectedDay = day;
                    v.setSelected(true);
                });
                dayCells[dayIndex] = cell;
            } else {
                cell.setText("");
                cell.setBackground(null);
                cell.setClickable(false);
                cell.setFocusable(false);
            }
            grid.addView(cell);
        }

        new AlertDialog.Builder(context)
            .setTitle(R.string.s_schedule_day_of_month)
            .setView(view)
            .setPositiveButton(R.string.s_confirm, (dialog, which) -> {
                if (onConfirmed != null) {
                    onConfirmed.accept(state.selectedDay);
                }
            })
            .setNegativeButton(R.string.s_cancel, null)
            .show();
    }

    /** 用于在内部类中持有可变的选中状态。 */
    private static final class State {
        int selectedDay;
    }
}
