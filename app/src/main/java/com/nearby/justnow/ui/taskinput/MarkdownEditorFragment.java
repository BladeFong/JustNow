package com.nearby.justnow.ui.taskinput;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.nearby.justnow.R;
import com.nearby.justnow.databinding.FragmentMarkdownEditorBinding;
import com.nearby.justnow.util.MarkdownActionHandler;

import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import io.noties.markwon.editor.MarkwonEditor;
import io.noties.markwon.editor.MarkwonEditorTextWatcher;
import io.noties.markwon.ext.tasklist.TaskListPlugin;

/**
 * 任务内容全屏 Markdown 编辑器 Fragment。
 */
public class MarkdownEditorFragment extends Fragment {

    private FragmentMarkdownEditorBinding mBinding;
    private TaskInputViewModel mViewModel;
    private Markwon mMarkwon;
    private boolean mIsPreviewMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentMarkdownEditorBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewModel = new ViewModelProvider(requireActivity()).get(TaskInputViewModel.class);

        initMarkwon();
        initContent();
        initFormatToolbar();
        initModeToggle();
        initDoneButton();
        initBackHandler();
    }

    private void initMarkwon() {
        Context context = requireContext();
        mMarkwon = Markwon.builder(context)
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(io.noties.markwon.SoftBreakAddsNewLinePlugin.create())
                .build();

        MarkwonEditor editor = MarkwonEditor.create(mMarkwon);
        mBinding.etMarkdown.addTextChangedListener(
                MarkwonEditorTextWatcher.withPreRender(editor, Executors.newCachedThreadPool(), mBinding.etMarkdown)
        );
    }

    private void initContent() {
        String currentMarkdown = mViewModel.getMarkdown();
        if (currentMarkdown != null) {
            mBinding.etMarkdown.setText(currentMarkdown);
            mBinding.etMarkdown.setSelection(mBinding.etMarkdown.getText().length());
        }
    }

    private void initFormatToolbar() {
        mBinding.btnH1.setOnClickListener(v -> MarkdownActionHandler.insertLinePrefix(mBinding.etMarkdown, "# "));
        mBinding.btnH2.setOnClickListener(v -> MarkdownActionHandler.insertLinePrefix(mBinding.etMarkdown, "## "));
        mBinding.btnBold.setOnClickListener(v -> MarkdownActionHandler.wrapSelection(mBinding.etMarkdown, "**", "**"));
        mBinding.btnItalic.setOnClickListener(v -> MarkdownActionHandler.wrapSelection(mBinding.etMarkdown, "*", "*"));
        mBinding.btnBulletList.setOnClickListener(v -> MarkdownActionHandler.insertLinePrefix(mBinding.etMarkdown, "- "));
        mBinding.btnNumberedList.setOnClickListener(v -> MarkdownActionHandler.insertLinePrefix(mBinding.etMarkdown, "1. "));
        mBinding.btnTaskList.setOnClickListener(v -> MarkdownActionHandler.insertLinePrefix(mBinding.etMarkdown, "- [ ] "));
        mBinding.btnQuote.setOnClickListener(v -> MarkdownActionHandler.insertLinePrefix(mBinding.etMarkdown, "> "));
        mBinding.btnCode.setOnClickListener(v -> MarkdownActionHandler.wrapSelection(mBinding.etMarkdown, "`", "`"));
        mBinding.btnDivider.setOnClickListener(v -> MarkdownActionHandler.insertBlock(mBinding.etMarkdown, "---"));
    }

    private void initModeToggle() {
        mBinding.btnToggleMode.setOnClickListener(v -> {
            mIsPreviewMode = !mIsPreviewMode;
            if (mIsPreviewMode) {
                // 切换到预览模式
                hideKeyboard();
                mBinding.hsvToolbar.setVisibility(View.GONE);
                mBinding.etMarkdown.setVisibility(View.GONE);
                mBinding.svPreview.setVisibility(View.VISIBLE);
                mBinding.btnToggleMode.setText(R.string.s_markdown_edit);

                String text = mBinding.etMarkdown.getText() != null ? mBinding.etMarkdown.getText().toString() : "";
                mMarkwon.setMarkdown(mBinding.tvPreview, text);
            } else {
                // 切换回编辑模式
                mBinding.svPreview.setVisibility(View.GONE);
                mBinding.hsvToolbar.setVisibility(View.VISIBLE);
                mBinding.etMarkdown.setVisibility(View.VISIBLE);
                mBinding.btnToggleMode.setText(R.string.s_markdown_preview);
                mBinding.etMarkdown.requestFocus();
            }
        });
    }

    private void initDoneButton() {
        mBinding.btnDone.setOnClickListener(v -> saveAndNavigateUp());
    }

    private void initBackHandler() {
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        saveAndNavigateUp();
                    }
                }
        );
    }

    private void saveAndNavigateUp() {
        hideKeyboard();
        String text = mBinding.etMarkdown.getText() != null ? mBinding.etMarkdown.getText().toString() : "";
        mViewModel.setMarkdown(text);
        Navigation.findNavController(requireView()).navigateUp();
    }

    private void hideKeyboard() {
        View view = getView();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
