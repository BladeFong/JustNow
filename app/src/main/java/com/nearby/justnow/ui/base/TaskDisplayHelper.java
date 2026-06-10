package com.nearby.justnow.ui.base;

import android.content.res.Resources;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.ui.engine.FocusDurationOptions;

import java.util.List;
import java.util.Map;

/**
 * 任务列表项展示工具 — 提取自 CapturePickerActivity / TaskInputFragment 重复代码
 */
public final class TaskDisplayHelper {

    private static final int HIGHLIGHT_COLOR = Color.parseColor("#FFF176");

    private TaskDisplayHelper() {}

    /**
     * 格式化任务行：优先级分钟 + 标签 + 标题
     *
     * @param task     任务实体
     * @param tagNames tagId -> 标签名
     * @param resources 用于获取格式化字符串
     */
    @NonNull
    public static String formatTaskLine(@NonNull TaskEntity task,
                                        @Nullable Map<Long, String> tagNames,
                                        @NonNull Resources resources) {
        StringBuilder sb = new StringBuilder();
        if (task.focusMinutes > 0) {
            sb.append(FocusDurationOptions.format(resources, task.focusMinutes));
        }
        if (task.tagId != null && task.tagId > 0 && tagNames != null) {
            String tagName = tagNames.get(task.tagId);
            if (tagName != null && !tagName.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("#").append(tagName);
            }
        }
        String title = task.content != null ? task.content : "";
        if (sb.length() > 0 && !title.isEmpty()) sb.append(" ");
        sb.append(title);
        return sb.toString();
    }

    /**
     * 对任务标题部分应用搜索关键词高亮
     *
     * @param fullLine  formatTaskLine 返回的完整行文本
     * @param title     任务标题（仅高亮此区域）
     * @param tokens    搜索分词
     */
    @NonNull
    public static SpannableString highlightTitle(@NonNull String fullLine,
                                                  @Nullable String title,
                                                  @Nullable List<String> tokens) {
        if (title == null) title = "";
        int titleStart = fullLine.length() - title.length();
        if (titleStart < 0) titleStart = 0;
        SpannableString ss = new SpannableString(fullLine);
        String lower = fullLine.toLowerCase();
        if (tokens != null) {
            for (String token : tokens) {
                String lt = token.toLowerCase();
                int start = lower.indexOf(lt, titleStart);
                while (start >= 0) {
                    int end = start + lt.length();
                    ss.setSpan(new BackgroundColorSpan(HIGHLIGHT_COLOR),
                            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = lower.indexOf(lt, end);
                }
            }
        }
        return ss;
    }
}
