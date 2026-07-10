package com.nearby.justnow.ui.quadrant;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.databinding.FragmentQuadrantBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.ui.taskinput.TaskInputViewModel;

/**
 * 四象限选择 — 点击象限卡片即保存，带动画效果
 */
public class QuadrantFragment extends BaseFragment<FragmentQuadrantBinding> {

    private TaskInputViewModel mViewModel;

    @Override
    protected FragmentQuadrantBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentQuadrantBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory(app))
            .get(TaskInputViewModel.class);

        showDraftSummary();
        setupCompletionModeChips();
        setupQuadrantCards();
    }

    private void showDraftSummary() {
        String summary = "\"" + mViewModel.getTitle() + "\"\n"
            + mViewModel.getFocusMinutesLabel(getResources()) + " · "
            + (mViewModel.getTagName() != null ? "#" + mViewModel.getTagName() : getString(R.string.s_no_tag_label));
        getBinding().tvDraftSummary.setText(summary);
    }

    private void setupQuadrantCards() {
        getBinding().cvUrgentImportant.setOnClickListener(v -> saveAndExit(0, v));
        getBinding().cvUrgentNotImportant.setOnClickListener(v -> saveAndExit(1, v));
        getBinding().cvNotUrgentImportant.setOnClickListener(v -> saveAndExit(2, v));
        getBinding().cvNotUrgentNotImportant.setOnClickListener(v -> saveAndExit(3, v));
    }

    /** 点击象限卡片：播放动画 → 保存 → 返回主界面 */
    private void saveAndExit(int quadrant, View cardView) {
        mViewModel.setQuadrant(quadrant);

        // 缩放动画：先放大再缩小，表示选中确认
        cardView.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(120)
            .withEndAction(() -> {
                cardView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        mViewModel.saveTask(() ->
                            requireActivity().runOnUiThread(() -> {
                                if (mViewModel.isFromCapture()) {
                                    // 外部捕获流：保存成功后引导用户留在 JustNow 主界面
                                    startActivity(new android.content.Intent(
                                        requireContext(),
                                        com.nearby.justnow.ui.main.MainActivity.class));
                                }
                                requireActivity().finish();
                            })
                        );
                    })
                    .start();
            })
            .start();
    }

    private void setupCompletionModeChips() {
        mViewModel.setCompletionMode(0); // 默认每天
        updateChipSelection(getBinding().chipModeDaily);

        View.OnClickListener chipListener = v -> {
            updateChipSelection(v);
            FragmentQuadrantBinding b = getBinding();
            int id = v.getId();

            if (id == b.chipModeDaily.getId()) {
                mViewModel.setCompletionMode(0);
                b.llQuotaInput.setVisibility(View.GONE);
            } else if (id == b.chipModeWeekly.getId()) {
                mViewModel.setCompletionMode(1);
                showQuotaInput(1, 6, R.string.s_mode_quota_hint_weekly);
            } else if (id == b.chipModeMonthly.getId()) {
                mViewModel.setCompletionMode(2);
                showQuotaInput(1, 27, R.string.s_mode_quota_hint_monthly);
            } else if (id == b.chipModeYearly.getId()) {
                mViewModel.setCompletionMode(3);
                showQuotaInput(1, 11, R.string.s_mode_quota_hint_yearly);
            }
        };

        getBinding().chipModeDaily.setOnClickListener(chipListener);
        getBinding().chipModeWeekly.setOnClickListener(chipListener);
        getBinding().chipModeMonthly.setOnClickListener(chipListener);
        getBinding().chipModeYearly.setOnClickListener(chipListener);
    }

    private void showQuotaInput(int defaultVal, int maxVal, int hintResId) {
        FragmentQuadrantBinding b = getBinding();
        b.llQuotaInput.setVisibility(View.VISIBLE);
        b.etQuota.setText(String.valueOf(defaultVal));
        b.tvQuotaHint.setText(getString(hintResId));
        b.etQuota.setTag(maxVal);
    }

    private void updateChipSelection(View selected) {
        FragmentQuadrantBinding b = getBinding();
        for (View chip : new View[]{
            b.chipModeDaily, b.chipModeWeekly, b.chipModeMonthly, b.chipModeYearly
        }) {
            chip.setSelected(chip == selected);
        }
    }
}
