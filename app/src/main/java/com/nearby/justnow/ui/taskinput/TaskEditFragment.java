package com.nearby.justnow.ui.taskinput;

import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.databinding.FragmentTaskEditBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.TagChipHelper;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.ui.engine.DisplayPolicy;
import com.nearby.justnow.ui.engine.FocusDurationOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务编辑页 — 标题、标签、专注时长、Markdown 正文、附加模块、象限选择
 */
public class TaskEditFragment extends BaseFragment<FragmentTaskEditBinding> {

    private TaskInputViewModel mViewModel;
    private final List<RadioButton> mFocusButtons = new ArrayList<>();

    @Override
    protected FragmentTaskEditBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentTaskEditBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory(app))
            .get(TaskInputViewModel.class);

        setupTagAutoComplete();
        setupTagChips();
        setupFocusMinutes();
        setupModuleButtons();
        setupBottomButton();
        restoreState();
        maybeAutoOpenAppActionSheet();
        maybeAutoOpenNoteShareSheet();
    }

    /** 外部捕获入口（CapturePicker → TaskInputActivity）要求进入即打开 APP 跳转 sheet */
    private void maybeAutoOpenAppActionSheet() {
        if (mViewModel.consumePendingOpenAppActionSheet()) {
            // 走完 restoreState 后再 post 一次确保 ChipGroup 等已经布局
            getBinding().getRoot().post(() -> openModuleEditor("app_actions"));
        }
    }

    /** 外部捕获入口（CapturePicker → TaskInputActivity）要求进入即打开笔记分享 sheet */
    private void maybeAutoOpenNoteShareSheet() {
        if (mViewModel.consumePendingOpenNoteShareSheet()) {
            getBinding().getRoot().post(() -> openModuleEditor("note_shares"));
        }
    }

    // ==================== 标签 ====================

    private void setupTagAutoComplete() {
        mViewModel.getAllTags().observe(getViewLifecycleOwner(), tags -> {
            List<String> tagNames = new ArrayList<>();
            for (TagEntity t : tags) tagNames.add(t.name);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, tagNames);
            ((AutoCompleteTextView) getBinding().etTagName).setAdapter(adapter);
        });

        getBinding().etTagName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                if (!input.isEmpty()) syncChipSelection(input);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupTagChips() {
        mViewModel.getTopTags(6).observe(getViewLifecycleOwner(), tags -> {
            com.google.android.material.chip.ChipGroup chipGroup = getBinding().cgExistingTags;
            chipGroup.removeAllViews();
            for (TagEntity tag : tags) {
                Chip chip = TagChipHelper.createSelectableChip(chipGroup.getContext(), tag, true);
                chip.setOnClickListener(v -> {
                    TagChipHelper.updateChipState(chip, chip.isChecked());
                    if (chip.isChecked()) {
                        getBinding().etTagName.setText(tag.name);
                    } else {
                        getBinding().etTagName.setText("");
                    }
                });
                chipGroup.addView(chip);
            }
        });
    }

    private void syncChipSelection(String input) {
        com.google.android.material.chip.ChipGroup chipGroup = getBinding().cgExistingTags;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            Chip chip = (Chip) chipGroup.getChildAt(i);
            TagEntity tag = (TagEntity) chip.getTag();
            if (tag != null && tag.name.equals(input)) {
                chip.setChecked(true);
                return;
            }
        }
        chipGroup.clearCheck();
    }

    // ==================== 专注时长 ====================

    private void setupFocusMinutes() {
        DisplayPolicy policy = ((JustNowApplication) requireActivity().getApplication())
                .getDisplayPolicyRepository().getEffectivePolicySync();
        List<Integer> options = FocusDurationOptions.buildOptions(policy);
        int currentFocusMinutes = mViewModel.getFocusMinutes();
        if (!options.contains(currentFocusMinutes)) {
            options.add(currentFocusMinutes);
        }

        LinearLayout container = getBinding().llFocusOptions;
        container.removeAllViews();
        mFocusButtons.clear();

        LinearLayout row = null;
        for (int i = 0; i < options.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                container.addView(row);
            }
            RadioButton radioButton = createFocusRadioButton(options.get(i));
            mFocusButtons.add(radioButton);
            if (row != null) {
                row.addView(radioButton);
            }
        }
        fillLastFocusRow(container);
    }

    private RadioButton createFocusRadioButton(int minutes) {
        RadioButton radioButton = new RadioButton(requireContext());
        radioButton.setTag(minutes);
        radioButton.setText(FocusDurationOptions.format(getResources(), minutes));
        radioButton.setTextAppearance(R.style.TextAppearance_JustNow_Body);
        radioButton.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!isChecked) return;
            for (RadioButton other : mFocusButtons) {
                if (other != buttonView && other.isChecked()) {
                    other.setChecked(false);
                }
            }
            Object tag = buttonView.getTag();
            if (tag instanceof Integer) {
                mViewModel.setFocusMinutes((Integer) tag);
            }
        });
        return radioButton;
    }

    private void fillLastFocusRow(LinearLayout container) {
        if (container.getChildCount() == 0) return;
        LinearLayout lastRow = (LinearLayout) container.getChildAt(container.getChildCount() - 1);
        while (lastRow.getChildCount() < 3) {
            View spacer = new View(requireContext());
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    0, 0, 1f));
            lastRow.addView(spacer);
        }
    }

    // ==================== 附加模块 ====================

    private void setupModuleButtons() {
        getBinding().btnModuleChecklist.setOnClickListener(v ->
            toggleModule("checklist"));
        getBinding().btnModuleAppAction.setOnClickListener(v ->
            toggleModule("app_actions"));
        getBinding().btnModuleNoteShare.setOnClickListener(v ->
            toggleModule("note_shares"));
        getBinding().llModuleHint.setOnClickListener(v -> {
            String currentModule = mViewModel.getSelectedModuleType();
            if (currentModule != null) openModuleEditor(currentModule);
        });
    }

    private void toggleModule(String type) {
        String current = mViewModel.getSelectedModuleType();
        if (type.equals(current)) {
            mViewModel.setSelectedModuleType(null);
            updateModuleButtonStates();
            updateModuleHintRow();
        } else {
            mViewModel.setSelectedModuleType(type);
            updateModuleButtonStates();
            updateModuleHintRow();
            openModuleEditor(type);
        }
    }

    private void updateModuleButtonStates() {
        String selected = mViewModel.getSelectedModuleType();
        getBinding().btnModuleChecklist.setSelected("checklist".equals(selected));
        getBinding().btnModuleAppAction.setSelected("app_actions".equals(selected));
        getBinding().btnModuleNoteShare.setSelected("note_shares".equals(selected));
    }

    private void updateModuleHintRow() {
        String selected = mViewModel.getSelectedModuleType();
        String prefix;
        if ("checklist".equals(selected)) {
            prefix = getString(R.string.s_module_checklist_prefix);
        } else if ("app_actions".equals(selected)) {
            prefix = getString(R.string.s_module_app_action_prefix);
        } else if ("note_shares".equals(selected)) {
            prefix = getString(R.string.s_module_note_share_prefix);
        } else {
            getBinding().tvModuleHint.setText("");
            return;
        }
        String action = getString(R.string.s_tap_to_edit);
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(prefix);
        int actionStart = ssb.length();
        ssb.append(action);
        int actionEnd = ssb.length();
        int linkColor = ContextCompat.getColor(requireContext(), R.color.purple_500);
        ssb.setSpan(new ForegroundColorSpan(linkColor),
                actionStart, actionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new UnderlineSpan(),
                actionStart, actionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        getBinding().tvModuleHint.setText(ssb);
    }

    private void openModuleEditor(String type) {
        if ("checklist".equals(type)) {
            TaskInputChecklistSheet sheet = new TaskInputChecklistSheet(this::onChecklistSaved);
            if (mViewModel.getPendingChecklistItems() != null) {
                sheet.setExistingItems(new ArrayList<>(mViewModel.getPendingChecklistItems()));
            }
            sheet.show(getParentFragmentManager(), "checklist_editor");
        } else if ("app_actions".equals(type)) {
            TaskInputAppActionSheet sheet = new TaskInputAppActionSheet(this::onAppActionsSaved);
            if (mViewModel.getPendingAppActions() != null) {
                sheet.setExistingActions(new ArrayList<>(mViewModel.getPendingAppActions()));
            }
            sheet.show(getParentFragmentManager(), "app_action_editor");
        } else if ("note_shares".equals(type)) {
            TaskInputNoteShareSheet sheet = new TaskInputNoteShareSheet(this::onNoteSharesSaved);
            if (mViewModel.getPendingNoteShares() != null) {
                sheet.setExistingShares(new ArrayList<>(mViewModel.getPendingNoteShares()));
            }
            sheet.show(getParentFragmentManager(), "note_share_editor");
        }
    }

    private void onChecklistSaved(List<TaskChecklistItem> items) {
        mViewModel.setPendingChecklistItems(items);
        updateModuleHintRow();
    }

    private void onAppActionsSaved(List<TaskAppAction> actions) {
        mViewModel.setPendingAppActions(actions);
        updateModuleHintRow();
    }

    private void onNoteSharesSaved(List<TaskNoteShare> shares) {
        mViewModel.setPendingNoteShares(shares);
        updateModuleHintRow();
    }

    // ==================== 底部按钮 ====================

    private void setupBottomButton() {
        getBinding().btnNextQuadrant.setOnClickListener(v -> {
            String title = getBinding().etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                getBinding().etTitle.setError(getString(R.string.s_content_error));
                return;
            }
            mViewModel.setTitle(title);
            mViewModel.setMarkdown(getBinding().etMarkdown.getText().toString().trim());
            mViewModel.setTagName(getBinding().etTagName.getText().toString().trim());
            Navigation.findNavController(v).navigate(R.id.action_taskEditFragment_to_quadrantFragment);
        });
    }

    // ==================== 状态恢复 ====================

    private void restoreState() {
        // 标题 & Markdown
        String title = mViewModel.getTitle();
        getBinding().etTitle.setText(title != null ? title : "");
        String markdown = mViewModel.getMarkdown();
        getBinding().etMarkdown.setText(markdown != null ? markdown : "");

        // 标签
        String tagName = mViewModel.getTagName();
        if (tagName != null && !tagName.isEmpty()) {
            getBinding().etTagName.setText(tagName);
        }

        // 专注时长
        int focusMinutes = mViewModel.getFocusMinutes();
        for (RadioButton radioButton : mFocusButtons) {
            Object tag = radioButton.getTag();
            if (tag instanceof Integer && (Integer) tag == focusMinutes) {
                radioButton.setChecked(true);
                break;
            }
        }

        // 附加模块选中状态
        String moduleType = mViewModel.getSelectedModuleType();
        updateModuleButtonStates();
        updateModuleHintRow(); // 始终更新提示行（固定占位）
    }
}
