package com.nearby.justnow.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.nearby.justnow.databinding.FragmentMainPage0Binding;

/**
 * ViewPager2 Page 0：承载主界面现有内容（黄金比例双栏 + 底部时段栏）。
 * 由 MainFragment 通过 FragmentStateAdapter 以 childFragment 形式管理。
 */
public class MainPage0Fragment extends Fragment {

    private FragmentMainPage0Binding mBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentMainPage0Binding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    public FragmentMainPage0Binding getBinding() {
        return mBinding;
    }
}
