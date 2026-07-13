package com.nearby.justnow.ui.quadrant;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
        setQuotaDisabled(); // 日模式：空内容，禁用

        View.OnClickListener chipListener = v -> {
            updateChipSelection(v);
            FragmentQuadrantBinding b = getBinding();
            int id = v.getId();

            if (id == b.chipModeDaily.getId()) {
                mViewModel.setCompletionMode(0);
                setQuotaDisabled();
            } else if (id == b.chipModeWeekly.getId()) {
                mViewModel.setCompletionMode(1);
                setQuotaEnabled(1, 6, R.string.s_mode_quota_hint_weekly);
            } else if (id == b.chipModeMonthly.getId()) {
                mViewModel.setCompletionMode(2);
                setQuotaEnabled(1, 27, R.string.s_mode_quota_hint_monthly);
            } else if (id == b.chipModeYearly.getId()) {
                mViewModel.setCompletionMode(3);
                setQuotaEnabled(1, 11, R.string.s_mode_quota_hint_yearly);
            }
        };

        getBinding().chipModeDaily.setOnClickListener(chipListener);
        getBinding().chipModeWeekly.setOnClickListener(chipListener);
        getBinding().chipModeMonthly.setOnClickListener(chipListener);
        getBinding().chipModeYearly.setOnClickListener(chipListener);

        // 配额输入联动 ViewModel
        getBinding().etQuota.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    int val = Integer.parseInt(s.toString());
                    mViewModel.setQuota(val);
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void setQuotaDisabled() {
        FragmentQuadrantBinding b = getBinding();
        b.etQuota.setText("");
        b.etQuota.setHint("");
        b.etQuota.setEnabled(false);
        mViewModel.setQuota(1); // 日模式固定 1
    }

    private void setQuotaEnabled(int defaultVal, int maxVal, int hintResId) {
        FragmentQuadrantBinding b = getBinding();
        b.etQuota.setText("");
        // hint: "1 (max 6)" 等，全部在 EditText 内
        b.etQuota.setHint(getString(R.string.s_mode_quota_format, defaultVal,
                getString(hintResId)));
        b.etQuota.setEnabled(true);
        b.etQuota.setTag(maxVal);
        mViewModel.setQuota(defaultVal); // 默认值同步到 ViewModel
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
