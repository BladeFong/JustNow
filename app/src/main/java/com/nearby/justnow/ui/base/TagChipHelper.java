package com.nearby.justnow.ui.base;

import android.content.Context;

import com.google.android.material.chip.Chip;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;

/**
 * 标签 Chip 创建工具 — 主界面筛选覆盖层和任务录入界面共用
 */
public class TagChipHelper {

    /** 创建一个可选中标签 Chip，含统一视觉样式 */
    public static Chip createSelectableChip(Context context, TagEntity tag) {
        return createSelectableChip(context, tag, false);
    }

    /** 创建一个可选中标签 Chip，含统一视觉样式；compact=true 时关闭最小触摸区，便于多行紧凑排布 */
    public static Chip createSelectableChip(Context context, TagEntity tag, boolean compact) {
        Chip chip = new Chip(context);
        chip.setText("#" + tag.name);
        chip.setTag(tag);
        chip.setCheckable(true);
        chip.setTextAppearance(R.style.TextAppearance_JustNow_Body);
        chip.setChipBackgroundColorResource(R.color.tag_normal);
        chip.setTextColor(context.getResources().getColor(android.R.color.white, null));
        chip.setChipStrokeColorResource(R.color.tag_normal);
        chip.setChipStrokeWidth(1f);
        if (compact) {
            chip.setEnsureMinTouchTargetSize(false);
        }
        return chip;
    }

    /** 切换 Chip 选中态颜色 */
    public static void updateChipState(Chip chip, boolean isChecked) {
        if (isChecked) {
            chip.setChipBackgroundColorResource(R.color.tag_active);
        } else {
            chip.setChipBackgroundColorResource(R.color.tag_normal);
        }
    }
}
