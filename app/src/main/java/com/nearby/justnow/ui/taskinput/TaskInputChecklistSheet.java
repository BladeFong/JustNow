package com.nearby.justnow.ui.taskinput;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskChecklistItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * todo 清单编辑器 BottomSheet — Markdown 方式：每行 ☐ 前缀为真实文本，禁止删除
 */
public class TaskInputChecklistSheet extends BottomSheetDialogFragment {

    private static final String CHECKBOX_PREFIX = "☐ ";

    private final Consumer<List<TaskChecklistItem>> mOnSaved;
    private List<TaskChecklistItem> mItems;
    private EditText mEditText;
    private boolean mIsApplyingPrefix;

    public TaskInputChecklistSheet(Consumer<List<TaskChecklistItem>> onSaved) {
        mOnSaved = onSaved;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_checklist_editor, null);
        dialog.setContentView(view);

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        mEditText = view.findViewById(R.id.et_checklist);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        loadExistingItems();

        mEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mIsApplyingPrefix) return;
                // Enter 换行时立刻补 ☐
                if (count == 1 && before == 0 && start > 0 && s.charAt(start) == '\n') {
                    mIsApplyingPrefix = true;
                    mEditText.getText().insert(start + 1, CHECKBOX_PREFIX);
                    mEditText.setSelection(start + 1 + CHECKBOX_PREFIX.length());
                    mIsApplyingPrefix = false;
                }
            }

            @Override public void afterTextChanged(Editable s) {
                if (mIsApplyingPrefix) return;
                String text = s.toString();

                // 全部被删：补回首行 ☐
                if (text.isEmpty()) {
                    mIsApplyingPrefix = true;
                    s.replace(0, 0, CHECKBOX_PREFIX);
                    mEditText.setSelection(CHECKBOX_PREFIX.length());
                    mIsApplyingPrefix = false;
                    return;
                }

                int sel = mEditText.getSelectionStart();

                // 首行勾选框不可删除
                if (!text.startsWith(CHECKBOX_PREFIX)) {
                    mIsApplyingPrefix = true;
                    if (text.startsWith(" ")) {
                        s.replace(0, 1, CHECKBOX_PREFIX);
                        sel += CHECKBOX_PREFIX.length() - 1;
                    } else if (text.startsWith("☐")) {
                        s.insert(1, " ");
                        sel += 1;
                    } else {
                        s.insert(0, CHECKBOX_PREFIX);
                        sel += CHECKBOX_PREFIX.length();
                    }
                    sel = Math.min(sel, s.length());
                    mEditText.setSelection(sel);
                    mIsApplyingPrefix = false;
                    return;
                }

                // 清理非首行：空行 或 ☐ 后无空格 → 删除整行
                String[] lines = text.split("\n", -1);
                boolean needCleanup = false;
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i];
                    if (line.isEmpty()
                        || (line.startsWith("☐") && !line.startsWith(CHECKBOX_PREFIX))) {
                        needCleanup = true;
                        break;
                    }
                }
                if (needCleanup) {
                    StringBuilder sb = new StringBuilder(lines[0]);
                    for (int i = 1; i < lines.length; i++) {
                        String line = lines[i];
                        if (!line.isEmpty()
                            && !(line.startsWith("☐") && !line.startsWith(CHECKBOX_PREFIX))) {
                            sb.append('\n').append(line);
                        }
                    }
                    String cleaned = sb.toString();
                    if (cleaned.isEmpty()) cleaned = CHECKBOX_PREFIX;
                    mIsApplyingPrefix = true;
                    s.replace(0, s.length(), cleaned);
                    mEditText.setSelection(Math.min(sel, cleaned.length()));
                    mIsApplyingPrefix = false;
                }
            }
        });

        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> {
            parseAndSave();
            dismiss();
        });

        // 撑满可用高度 + 自动进入编辑模式
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
            }
            mEditText.requestFocus();
            mEditText.postDelayed(() -> {
                Context context = getContext();
                if (context == null) return;
                InputMethodManager imm = (InputMethodManager) context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        });

        return dialog;
    }

    private void loadExistingItems() {
        if (mItems != null && !mItems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (TaskChecklistItem item : mItems) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(CHECKBOX_PREFIX).append(item.content);
            }
            mEditText.setText(sb.toString());
            mEditText.setSelection(sb.length());
        } else {
            mEditText.setText(CHECKBOX_PREFIX);
            mEditText.setSelection(CHECKBOX_PREFIX.length());
        }
    }

    public void setExistingItems(List<TaskChecklistItem> items) {
        mItems = items;
    }

    private void parseAndSave() {
        String text = mEditText.getText().toString().trim();
        List<TaskChecklistItem> items = new ArrayList<>();
        if (!text.isEmpty()) {
            String[] lines = text.split("\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (line.startsWith(CHECKBOX_PREFIX.trim())) {
                    line = line.substring(CHECKBOX_PREFIX.trim().length()).trim();
                }
                if (line.isEmpty()) continue;
                TaskChecklistItem item = new TaskChecklistItem();
                item.content = line;
                item.orderIndex = i;
                item.checked = false;
                item.crossedOut = false;
                items.add(item);
            }
        }
        if (mOnSaved != null) {
            mOnSaved.accept(items);
        }
    }
}
