package com.nearby.justnow.util;

import android.content.Context;
import com.nearby.justnow.R;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置图标标签多语言动态映射与翻译工具类。
 * 数据库中统一存储固定的中文简称（如 "美术"）。
 * 运行时根据设备语言翻译并输出本地化文本，同时支持录入时逆向转换。
 */
public class TagLocalizer {

    private static final Map<String, Integer> NAME_TO_RES_MAP = new HashMap<>();
    static {
        NAME_TO_RES_MAP.put("玩具", R.string.tag_blocks);
        NAME_TO_RES_MAP.put("阅读", R.string.tag_book);
        NAME_TO_RES_MAP.put("美术", R.string.tag_palette);
        NAME_TO_RES_MAP.put("音乐", R.string.tag_music);
        NAME_TO_RES_MAP.put("运动", R.string.tag_ball);
        NAME_TO_RES_MAP.put("益智", R.string.tag_game_puzzle);
        NAME_TO_RES_MAP.put("手工", R.string.tag_craft);
        NAME_TO_RES_MAP.put("动画", R.string.tag_animation);
        NAME_TO_RES_MAP.put("学习", R.string.tag_study);
        NAME_TO_RES_MAP.put("家务", R.string.tag_chores);
    }

    public static String getLocalizedName(Context context, String dbTagName) {
        if (dbTagName == null) return null;
        Integer resId = NAME_TO_RES_MAP.get(dbTagName);
        if (resId != null) {
            return context.getString(resId);
        }
        return dbTagName;
    }

    public static String getDbTagName(Context context, String inputTagName) {
        if (inputTagName == null || inputTagName.trim().isEmpty()) return inputTagName;
        String trimmed = inputTagName.trim();
        for (Map.Entry<String, Integer> entry : NAME_TO_RES_MAP.entrySet()) {
            String localized = context.getString(entry.getValue());
            if (localized.equalsIgnoreCase(trimmed) || entry.getKey().equalsIgnoreCase(trimmed)) {
                return entry.getKey();
            }
        }
        return trimmed;
    }
}
