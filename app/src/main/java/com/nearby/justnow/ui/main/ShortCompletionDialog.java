package com.nearby.justnow.ui.main;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.nearby.justnow.R;

/**
 * &lt; 15min 完成引导对话框：当前次专注任务实际耗时低于
 * {@link MainViewModel#SHORT_DURATION_THRESHOLD_MINUTES} 时弹出。
 *
 * <p>对话框文案矩阵按入口和是否有长期安排动态切换，详见 task-execution.md
 * 「&lt; 15min 完成引导」一节。</p>
 *
 * <p>按钮顺序：[取消] [直接完成] [第三按钮（高亮）]。</p>
 */
public final class ShortCompletionDialog {

    /**
     * 入口：左侧时间线 / 仅标题专注任务的「完成本次」路径。
     * 副作用上「直接完成」不动安排，「完成并调整」改琐碎 + 有长期安排时停安排。
     */
    public static final int ENTRY_COMPLETE_ONCE = 1;

    /**
     * 入口：详情页「完成并停止安排」按钮路径（仅在有长期安排时出现）。
     * 副作用上不论选哪个，都先停安排（这是上一步用户按钮的语义）。
     */
    public static final int ENTRY_COMPLETE_AND_STOP_SCHEDULE = 2;

    public interface Callback {
        /** 取消：任务回执行中，不写任何记录，不动 focusMinutes / 安排。 */
        void onCancel();

        /**
         * 直接完成：不写 task_executions，不改 focusMinutes，按入口决定是否停安排。
         */
        void onDirectComplete();

        /**
         * 第三按钮（完成并调整 / 不再安排并调整）：改琐碎（focus_minutes=0 +
         * 清执行中状态），按入口决定是否停安排。
         */
        void onConvertToChore();
    }

    private ShortCompletionDialog() {
    }

    /**
     * 显示对话框。
     *
     * @param context        宿主 Context（必须为 Activity / Fragment 上下文）
     * @param taskTitle      任务标题，作为对话框标题
     * @param entry          完成入口，影响文案与按钮文字
     * @param hasSchedule    任务是否带有效安排（影响入口1 的副文案与按钮文字）
     * @param callback       三选项回调
     */
    public static void show(@NonNull Context context, @NonNull String taskTitle,
                            int entry, boolean hasSchedule, @NonNull Callback callback) {
        int messageRes;
        int convertButtonRes;
        if (entry == ENTRY_COMPLETE_AND_STOP_SCHEDULE) {
            messageRes = R.string.s_short_completion_msg_stop_schedule;
            convertButtonRes = R.string.s_short_completion_btn_complete_and_convert;
        } else if (hasSchedule) {
            messageRes = R.string.s_short_completion_msg_complete_with_schedule;
            convertButtonRes = R.string.s_short_completion_btn_stop_schedule_and_convert;
        } else {
            messageRes = R.string.s_short_completion_msg_complete_no_schedule;
            convertButtonRes = R.string.s_short_completion_btn_complete_and_convert;
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(taskTitle)
            .setMessage(messageRes)
            // 按矩阵顺序：[取消] [直接完成] [第三按钮（高亮）]
            // 主题里 buttonBarNeutralButtonStyle / NegativeButton 走 Secondary 样式，
            // Positive 走主样式；因此把第三按钮挂到 Positive，让它高亮。
            .setNeutralButton(R.string.s_cancel, (d, w) -> callback.onCancel())
            .setNegativeButton(R.string.s_short_completion_btn_direct_complete,
                (d, w) -> callback.onDirectComplete())
            .setPositiveButton(convertButtonRes,
                (d, w) -> callback.onConvertToChore())
            .setOnCancelListener(d -> callback.onCancel())
            .show();
    }
}
