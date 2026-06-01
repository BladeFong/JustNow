package com.nearby.justnow.ui.base;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

/**
 * Fragment 基类 — 简化 ViewBinding 绑定
 */
public abstract class BaseFragment<VB extends ViewBinding> extends Fragment {

    private VB mBinding;

    protected abstract VB inflateBinding(LayoutInflater inflater, ViewGroup container);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = inflateBinding(inflater, container);
        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    protected VB getBinding() {
        return mBinding;
    }
}
