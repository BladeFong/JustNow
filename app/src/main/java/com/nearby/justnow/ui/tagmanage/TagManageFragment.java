package com.nearby.justnow.ui.tagmanage;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.databinding.FragmentTagManageBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.TagChipHelper;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.ui.period.PeriodTextResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * 标签管理 — 按作息类型展示时间段组优先场景 + 清理未使用标签
 */
public class TagManageFragment extends BaseFragment<FragmentTagManageBinding> {

    private TagManageViewModel mViewModel;
    private PriorityGroupAdapter mAdapter;

    @Override
    protected FragmentTagManageBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentTagManageBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
            .get(TagManageViewModel.class);

        setupPriorityGroups();

        getBinding().btnCleanupTags.setOnClickListener(v ->
            Navigation.findNavController(v)
                .navigate(R.id.action_tagManageFragment_to_unusedTagFragment));
    }

    private void setupPriorityGroups() {
        mAdapter = new PriorityGroupAdapter();
        getBinding().rvPriorityGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        getBinding().rvPriorityGroups.setAdapter(mAdapter);

        // 异步加载可见+已启用的组类型
        loadVisibleEnabledGroupTypes();

        mViewModel.getGroupPriorityEnabledLiveData().observe(getViewLifecycleOwner(), state -> {
            mAdapter.setEnabledState(state);
        });
        mViewModel.getPriorityTagIdsLiveData().observe(getViewLifecycleOwner(), ids -> {
            mAdapter.refreshVisibleTags();
        });
        mViewModel.getCacheRefreshEvent().observe(getViewLifecycleOwner(), event -> {
            if (Boolean.TRUE.equals(event)) {
                mAdapter.refreshVisibleTags();
            }
        });
    }

    private void loadVisibleEnabledGroupTypes() {
        mViewModel.loadVisibleEnabledGroupTypesAsync(types -> {
            mAdapter.setGroupTypes(types);
            mViewModel.refreshPriorityTagCounts(types);
        });
    }

    /** 弹出标签选择对话框，已选标签预勾选 */
    private void showTagEditDialog(String groupType) {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_select_tags, null);
        ChipGroup chipGroup = dialogView.findViewById(R.id.cg_dialog_tags);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.s_confirm, null) // 稍后绑定
            .setNegativeButton(R.string.s_cancel, null)
            .create();
        dialog.show();

        // 确认按钮初始禁用，选中后启用
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Set<Long> selectedIds = getSelectedIds(chipGroup);
            if (!selectedIds.isEmpty()) {
                mViewModel.setTagsPriority(groupType, selectedIds);
                mViewModel.setPriorityEnabled(groupType, true);
                dialog.dismiss();
            }
        });

        // 异步加载标签
        mViewModel.loadTagsForGroupEditAsync(groupType, (allTags, priorityIds) -> {
            chipGroup.removeAllViews();
            for (TagEntity tag : allTags) {
                Chip chip = TagChipHelper.createSelectableChip(chipGroup.getContext(), tag);
                // 预勾选已优先的标签
                if (priorityIds.contains(tag.id)) {
                    chip.setChecked(true);
                    TagChipHelper.updateChipState(chip, true);
                }
                chip.setOnClickListener(v -> {
                    TagChipHelper.updateChipState(chip, chip.isChecked());
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setEnabled(!getSelectedIds(chipGroup).isEmpty());
                });
                chipGroup.addView(chip);
            }
            // 已有预勾选标签时，启用确认按钮
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setEnabled(!getSelectedIds(chipGroup).isEmpty());
        });
    }

    private Set<Long> getSelectedIds(ChipGroup chipGroup) {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            if (chip.isChecked()) {
                TagEntity tag = (TagEntity) chip.getTag();
                if (tag != null) ids.add(tag.id);
            }
        }
        return ids;
    }

    private class PriorityGroupAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<
        PriorityGroupAdapter.ViewHolder> {

        private List<String> mGroupTypes = new ArrayList<>();
        private Map<String, Boolean> mEnabledState = java.util.Collections.emptyMap();

        void setGroupTypes(List<String> groupTypes) {
            mGroupTypes = groupTypes != null ? groupTypes : new ArrayList<>();
            notifyDataSetChanged();
        }

        void setEnabledState(Map<String, Boolean> enabledState) {
            mEnabledState = enabledState != null ? enabledState : java.util.Collections.emptyMap();
            notifyDataSetChanged();
        }

        void refreshVisibleTags() {
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_priority_group, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String groupType = mGroupTypes.get(position);
            boolean enabled = Boolean.TRUE.equals(mEnabledState.get(groupType));

            holder.title.setText(PeriodTextResolver.getGroupName(
                holder.itemView.getResources(), groupType));
            holder.sw.setOnCheckedChangeListener(null);
            holder.sw.setChecked(enabled);
            holder.sw.setOnCheckedChangeListener((button, checked) ->
                onGroupToggle(holder, groupType, checked));

            holder.row.setOnClickListener(v -> {
                if (mViewModel.isPriorityEnabled(groupType)) {
                    showTagEditDialog(groupType);
                }
            });

            holder.tags.setVisibility(enabled ? View.VISIBLE : View.GONE);
            if (enabled) {
                refreshGroupTags(holder.tags, groupType);
            } else {
                holder.tags.removeAllViews();
            }
        }

        @Override
        public int getItemCount() {
            return mGroupTypes.size();
        }

        private void onGroupToggle(ViewHolder holder, String groupType, boolean checked) {
            if (checked) {
                if (mViewModel.getPriorityTagCount(groupType) == 0) {
                    holder.sw.setOnCheckedChangeListener(null);
                    holder.sw.setChecked(false);
                    holder.sw.setOnCheckedChangeListener((button, c) ->
                        onGroupToggle(holder, groupType, c));
                    showTagEditDialog(groupType);
                } else {
                    mViewModel.setPriorityEnabled(groupType, true);
                }
            } else {
                mViewModel.setPriorityEnabled(groupType, false);
            }
        }

        private void refreshGroupTags(ChipGroup chipGroup, String groupType) {
            mViewModel.loadPriorityTagsForGroupAsync(groupType, priorityTags -> {
                chipGroup.removeAllViews();
                for (TagEntity tag : priorityTags) {
                    Chip chip = TagChipHelper.createSelectableChip(chipGroup.getContext(), tag);
                    chip.setCheckable(false);
                    chip.setClickable(false);
                    chipGroup.addView(chip);
                }
            });
        }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            View row;
            TextView title;
            CompoundButton sw;
            ChipGroup tags;

            ViewHolder(View view) {
                super(view);
                row = view.findViewById(R.id.ll_priority_group_row);
                title = view.findViewById(R.id.tv_priority_group_title);
                sw = view.findViewById(R.id.sw_priority_group_enabled);
                tags = view.findViewById(R.id.cg_priority_group_tags);
            }
        }
    }
}
