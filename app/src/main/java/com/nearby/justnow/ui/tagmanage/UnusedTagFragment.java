package com.nearby.justnow.ui.tagmanage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.databinding.FragmentUnusedTagBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.TagChipHelper;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 清理未使用标签 — 查看并删除未被任何任务引用的标签，支持单个删除和一键清除
 */
public class UnusedTagFragment extends BaseFragment<FragmentUnusedTagBinding> {

    private UnusedTagViewModel mViewModel;

    @Override
    protected FragmentUnusedTagBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentUnusedTagBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
            .get(UnusedTagViewModel.class);

        loadUnusedTags();

        getBinding().btnDeleteSelected.setOnClickListener(v -> {
            List<Long> selectedIds = getSelectedTagIds();
            if (!selectedIds.isEmpty()) {
                new AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.s_confirm_delete_tags, selectedIds.size()))
                    .setPositiveButton(R.string.s_delete, (d, w) -> {
                        mViewModel.deleteTags(selectedIds);
                        Snackbar.make(v, getString(R.string.s_tags_deleted, selectedIds.size()),
                            Snackbar.LENGTH_SHORT).show();
                        loadUnusedTags();
                    })
                    .setNegativeButton(R.string.s_cancel, null)
                    .show();
            }
        });
    }

    private void loadUnusedTags() {
        mViewModel.loadUnusedTagsAsync(tags -> {
            com.google.android.material.chip.ChipGroup chipGroup = getBinding().cgUnusedTags;
            chipGroup.removeAllViews();
            Button deleteBtn = getBinding().btnDeleteSelected;
            View emptyHint = getBinding().tvUnusedEmpty;

            if (tags.isEmpty()) {
                deleteBtn.setVisibility(View.GONE);
                if (emptyHint != null) emptyHint.setVisibility(View.VISIBLE);
                return;
            }

            deleteBtn.setVisibility(View.VISIBLE);
            if (emptyHint != null) emptyHint.setVisibility(View.GONE);

            for (TagEntity tag : tags) {
                Chip chip = TagChipHelper.createSelectableChip(chipGroup.getContext(), tag);
                chip.setOnClickListener(v -> {
                    TagChipHelper.updateChipState(chip, chip.isChecked());
                    deleteBtn.setEnabled(!getSelectedTagIds().isEmpty());
                });
                chip.setCloseIconVisible(true);
                chip.setOnCloseIconClickListener(v -> {
                    new AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.s_confirm_delete_tag, tag.name))
                        .setPositiveButton(R.string.s_delete, (d, w) -> {
                            List<Long> ids = new ArrayList<>();
                            ids.add(tag.id);
                            mViewModel.deleteTags(ids);
                            Snackbar.make(getBinding().getRoot(),
                                getString(R.string.s_tag_deleted, tag.name), Snackbar.LENGTH_SHORT).show();
                            loadUnusedTags();
                        })
                        .setNegativeButton(R.string.s_cancel, null)
                        .show();
                });
                chipGroup.addView(chip);
            }

            deleteBtn.setEnabled(false);
        });
    }

    private List<Long> getSelectedTagIds() {
        List<Long> ids = new ArrayList<>();
        com.google.android.material.chip.ChipGroup chipGroup = getBinding().cgUnusedTags;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.isChecked()) {
                TagEntity tag = (TagEntity) chip.getTag();
                if (tag != null) ids.add(tag.id);
            }
        }
        return ids;
    }
}
