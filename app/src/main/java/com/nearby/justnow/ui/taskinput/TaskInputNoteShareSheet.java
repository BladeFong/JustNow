package com.nearby.justnow.ui.taskinput;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 关联笔记单项编辑器 BottomSheet — 支持直编、回显、清空及剪贴板自动识别
 */
public class TaskInputNoteShareSheet extends BottomSheetDialogFragment {

    private final Consumer<List<TaskNoteShare>> mOnSaved;
    private TaskNoteShare mExistingShare;
    private EditText mEtLink;
    private EditText mEtHint;
    private Button mBtnClear;

    public TaskInputNoteShareSheet(Consumer<List<TaskNoteShare>> onSaved) {
        mOnSaved = onSaved;
    }

    public void setExistingShares(List<TaskNoteShare> shares) {
        if (shares != null && !shares.isEmpty()) {
            mExistingShare = shares.get(0);
        } else {
            mExistingShare = null;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_note_share_editor, null);
        dialog.setContentView(view);

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
            }
        });

        mEtLink = view.findViewById(R.id.et_note_link);
        mEtHint = view.findViewById(R.id.et_note_hint);
        mBtnClear = view.findViewById(R.id.btn_clear);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        // 消费外部捕获预填项
        TaskInputViewModel vm = new ViewModelProvider(requireActivity(),
                new ViewModelFactory((JustNowApplication) requireActivity().getApplication()))
                .get(TaskInputViewModel.class);
        TaskNoteShare prefill = vm.consumePendingNoteSharePrefill();
        if (prefill != null) {
            mExistingShare = prefill;
        }

        final View focusTarget;
        if (mExistingShare != null) {
            mEtLink.setText(mExistingShare.deepLink != null ? mExistingShare.deepLink : "");
            mEtHint.setText(mExistingShare.hint != null ? mExistingShare.hint : "");
            mBtnClear.setVisibility(View.VISIBLE);
            if (mExistingShare.hint != null && !mExistingShare.hint.isEmpty()) {
                mEtHint.setSelection(mExistingShare.hint.length());
            }
            focusTarget = mEtHint;
        } else {
            mBtnClear.setVisibility(View.GONE);
            String clipboardLink = getValidClipboardLink();
            if (clipboardLink != null) {
                mEtLink.setText(clipboardLink);
                Toast.makeText(requireContext(), R.string.s_note_share_clipboard_detected, Toast.LENGTH_SHORT).show();
                focusTarget = mEtHint;
            } else {
                focusTarget = mEtLink;
            }
        }

        mBtnClear.setOnClickListener(v -> {
            if (mOnSaved != null) {
                mOnSaved.accept(Collections.emptyList());
            }
            dismiss();
        });

        btnCancel.setOnClickListener(v -> dismiss());

        btnConfirm.setOnClickListener(v -> {
            String link = mEtLink.getText().toString().trim();
            String hint = mEtHint.getText().toString().trim();

            if (link.isEmpty()) {
                if (!hint.isEmpty()) {
                    mEtLink.setError(getString(R.string.s_content_error));
                    return;
                }
                if (mOnSaved != null) {
                    mOnSaved.accept(Collections.emptyList());
                }
                dismiss();
                return;
            }

            TaskNoteShare share = mExistingShare != null ? mExistingShare : new TaskNoteShare();
            share.deepLink = link;
            share.hint = hint;
            share.orderIndex = 0;

            if (mOnSaved != null) {
                List<TaskNoteShare> result = new ArrayList<>();
                result.add(share);
                mOnSaved.accept(result);
            }
            dismiss();
        });

        // 自动拉起软键盘
        view.postDelayed(() -> {
            if (focusTarget != null) {
                focusTarget.requestFocus();
                Context context = getContext();
                if (context != null) {
                    InputMethodManager imm = (InputMethodManager) context
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(focusTarget, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        }, 150);

        return dialog;
    }

    @Nullable
    private String getValidClipboardLink() {
        try {
            Context context = getContext();
            if (context == null) return null;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).getText();
            if (text == null) return null;
            String str = text.toString().trim();
            if (str.isEmpty()) return null;

            if (Patterns.WEB_URL.matcher(str).matches()
                    || str.startsWith("http://")
                    || str.startsWith("https://")
                    || str.startsWith("intent:")
                    || str.contains("#Intent;")
                    || str.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                return str;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
