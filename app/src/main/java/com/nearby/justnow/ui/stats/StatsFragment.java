package com.nearby.justnow.ui.stats;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.nearby.justnow.databinding.FragmentStatsBinding;
import com.nearby.justnow.ui.base.BaseFragment;

/**
 * 数据统计 — 四象限完成统计 + 月度趋势
 */
public class StatsFragment extends BaseFragment<FragmentStatsBinding> {

    @Override
    protected FragmentStatsBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentStatsBinding.inflate(inflater, container, false);
    }
}
