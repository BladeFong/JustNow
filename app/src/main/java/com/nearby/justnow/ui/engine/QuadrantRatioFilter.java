package com.nearby.justnow.ui.engine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 4:2:2:1 比例截取。
 * 比例仅在总任务数超出 maxDisplayItems 时生效，否则全量展示。
 * 超出时迭代多轮：每轮按剩余空位数计算配额并取出匹配项，
 * 先持续从 groupA 取，groupA 空了才取 groupB，直到填满或无可取。
 * 比例 = 紧急重要 : 紧急不重要 : 不紧急重要 : 不紧急不重要
 */
public class QuadrantRatioFilter {

    private static final int[] RATIO = {4, 2, 2, 1};

    /**
     * @param groupA          时间容纳组（优先筛选，方法会修改此列表）
     * @param groupB          时间不足组（方法会修改此列表）
     * @param maxDisplayItems 界面可显示的最大任务数
     */
    public static List<DisplayItem> apply(List<DisplayItem> groupA, List<DisplayItem> groupB,
                                          int maxDisplayItems) {
        int total = groupA.size() + groupB.size();

        if (total <= maxDisplayItems) {
            List<DisplayItem> all = new ArrayList<>(groupA.size() + groupB.size());
            all.addAll(groupA);
            all.addAll(groupB);
            return all;
        }

        List<DisplayItem> result = new ArrayList<>();
        int remaining = maxDisplayItems;

        if (groupA.size() <= remaining) {
            // A组全收，剩余位置从B组中按比例挑选
            result.addAll(groupA);
            groupA.clear();
            remaining -= result.size();
            collectLoop(groupB, result, remaining);
        } else {
            // A组已超出容纳数，只从A组挑选，不碰B组
            collectLoop(groupA, result, remaining);
        }

        return result;
    }

    /** 对单个组迭代多轮，直到填满或该组为空。已耗尽象限后续轮次不再分配配额。 */
    private static int collectLoop(List<DisplayItem> group, List<DisplayItem> result, int remaining) {
        boolean[] exhausted = new boolean[4];

        while (remaining > 0 && !group.isEmpty()) {
            // 计算剩余活跃象限的比例总和
            int activeRatioSum = 0;
            for (int i = 0; i < 4; i++) {
                if (!exhausted[i]) {
                    activeRatioSum += RATIO[i];
                }
            }
            if (activeRatioSum == 0) {
                break;
            }

            int[] limits = new int[4];
            double[] fractions = new double[4];
            int floorSum = 0;
            for (int i = 0; i < 4; i++) {
                if (!exhausted[i]) {
                    double quota = (double) remaining * RATIO[i] / activeRatioSum;
                    limits[i] = (int) quota; // floor
                    fractions[i] = quota - limits[i];
                    floorSum += limits[i];
                }
            }
            // 将剩余名额按小数部分从大到小分配，确保 sum(limits) == remaining
            int extraSlots = remaining - floorSum;
            for (int slot = 0; slot < extraSlots; slot++) {
                int bestIdx = -1;
                double bestFrac = -1.0;
                for (int i = 0; i < 4; i++) {
                    if (!exhausted[i] && fractions[i] > bestFrac) {
                        bestFrac = fractions[i];
                        bestIdx = i;
                    }
                }
                if (bestIdx >= 0) {
                    limits[bestIdx]++;
                    fractions[bestIdx] = 0.0; // 已分配，后续不再参与
                }
            }
            int[] counts = new int[4];
            int collected = 0;

            Iterator<DisplayItem> it = group.iterator();
            while (it.hasNext()) {
                DisplayItem item = it.next();
                int q = item.task.quadrant;
                if (q >= 0 && q < 4 && counts[q] < limits[q]) {
                    result.add(item);
                    counts[q]++;
                    collected++;
                    it.remove();
                }
            }

            if (collected == 0) {
                break;
            }

            for (int i = 0; i < 4; i++) {
                if (!exhausted[i] && counts[i] < limits[i]) {
                    exhausted[i] = true;
                }
            }

            remaining -= collected;
        }
        return remaining;
    }
}
