package com.nearby.justnow.ui.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 智能展示策略。由 YAML 解析得到，供展示引擎和专注时长选项复用。
 */
public final class DisplayPolicy {

    public static final int VERSION = 1;
    public static final int FOCUS_MAX_120 = 120;
    public static final int FOCUS_MAX_150 = 150;

    public enum PriorityRule {
        SCHEDULE_PRIORITY("schedule_priority"),
        TAG_PRIORITY("tag_priority"),
        QUADRANT("quadrant"),
        FOCUS_DURATION("focus_duration");

        private final String mYamlName;

        PriorityRule(String yamlName) {
            mYamlName = yamlName;
        }

        public String getYamlName() {
            return mYamlName;
        }

        public static PriorityRule fromYamlName(String yamlName) {
            for (PriorityRule rule : values()) {
                if (rule.mYamlName.equals(yamlName)) return rule;
            }
            return null;
        }
    }

    public enum FocusDurationOrder {
        ASC("asc"),
        DESC("desc");

        private final String mYamlName;

        FocusDurationOrder(String yamlName) {
            mYamlName = yamlName;
        }

        public String getYamlName() {
            return mYamlName;
        }

        public static FocusDurationOrder fromYamlName(String yamlName) {
            for (FocusDurationOrder order : values()) {
                if (order.mYamlName.equals(yamlName)) return order;
            }
            return null;
        }
    }

    private final int mVersion;
    private final List<PriorityRule> mPriorityOrder;
    private final int mFitToleranceMinutes;
    private final int mFocusMaxMinutes;
    private final FocusDurationOrder mFocusDurationOrder;
    private final int[] mQuadrantRatio;

    public DisplayPolicy(int version, List<PriorityRule> priorityOrder,
                         int fitToleranceMinutes, int focusMaxMinutes,
                         FocusDurationOrder focusDurationOrder, int[] quadrantRatio) {
        mVersion = version;
        mPriorityOrder = Collections.unmodifiableList(new ArrayList<>(priorityOrder));
        mFitToleranceMinutes = fitToleranceMinutes;
        mFocusMaxMinutes = focusMaxMinutes;
        mFocusDurationOrder = focusDurationOrder;
        mQuadrantRatio = quadrantRatio.clone();
    }

    public static DisplayPolicy defaultPolicy() {
        List<PriorityRule> order = new ArrayList<>();
        order.add(PriorityRule.SCHEDULE_PRIORITY);
        order.add(PriorityRule.TAG_PRIORITY);
        order.add(PriorityRule.QUADRANT);
        order.add(PriorityRule.FOCUS_DURATION);
        return new DisplayPolicy(VERSION, order, 15, FOCUS_MAX_120,
                FocusDurationOrder.DESC, new int[]{4, 2, 2, 1});
    }

    public int getVersion() {
        return mVersion;
    }

    public List<PriorityRule> getPriorityOrder() {
        return mPriorityOrder;
    }

    public int getFitToleranceMinutes() {
        return mFitToleranceMinutes;
    }

    public int getFocusMaxMinutes() {
        return mFocusMaxMinutes;
    }

    public FocusDurationOrder getFocusDurationOrder() {
        return mFocusDurationOrder;
    }

    public int[] getQuadrantRatio() {
        return mQuadrantRatio.clone();
    }
}
