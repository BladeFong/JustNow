package com.nearby.justnow.ui.taskinput;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.databinding.FragmentTaskInputBinding;
import com.nearby.justnow.ui.base.BaseFragment;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务录入 — 输入/检索，匹配已有任务可跳转编辑，或输入新任务内容后进入编辑页
 */
public class TaskInputFragment extends BaseFragment<FragmentTaskInputBinding> {

    private TaskInputViewModel mViewModel;
    private SearchResultAdapter mSearchAdapter;

    @Override
    protected FragmentTaskInputBinding inflateBinding(LayoutInflater inflater, ViewGroup container) {
        return FragmentTaskInputBinding.inflate(inflater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory(app))
            .get(TaskInputViewModel.class);

        mViewModel.clearSearchResults();

        // 检查是否从外部传入编辑任务 ID（从 ReminderDetailActivity 编辑按钮进入）
        long editTaskId = requireActivity().getIntent().getLongExtra(
                com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.EXTRA_EDIT_TASK_ID, -1);
        if (editTaskId > 0) {
            mViewModel.loadTaskForEdit(editTaskId);
            getBinding().etTaskContent.postDelayed(() -> {
                View currentView = getView();
                if (currentView == null) return;
                Navigation.findNavController(currentView)
                    .navigate(R.id.action_taskInputFragment_to_taskEditFragment);
            }, 150);
        }

        setupSearchInput();
        setupSearchResults();
        setupNextButton();

        // 外部捕获入口（CapturePickerActivity → TaskInputActivity）会立即导航到编辑页，
        // 不应在录入首屏弹键盘
        boolean hasCaptureExtras = requireActivity().getIntent()
            .hasExtra(TaskInputActivity.EXTRA_LOAD_TASK_ID)
            || requireActivity().getIntent()
                .hasExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TITLE)
            || requireActivity().getIntent()
                .hasExtra(TaskInputActivity.EXTRA_DRAFT_TASK_MARKDOWN)
            || requireActivity().getIntent()
                .hasExtra(TaskInputActivity.EXTRA_PREFILL_APP_ACTION_URI)
            || requireActivity().getIntent()
                .hasExtra(TaskInputActivity.EXTRA_PREFILL_NOTE_SHARE_URI);

        // 自动聚焦（编辑模式不自动弹键盘，外部捕获入口也跳过）
        if (editTaskId <= 0 && !hasCaptureExtras) {
            getBinding().etTaskContent.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(getBinding().etTaskContent, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void setupSearchInput() {
        getBinding().etTaskContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                boolean hasContent = !input.isEmpty();
                getBinding().btnNext.setEnabled(hasContent);
                if (hasContent) {
                    mViewModel.searchTasks(input);
                } else {
                    mSearchAdapter.setTasks(new ArrayList<>());
                    getBinding().rvSearchResults.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSearchResults() {
        mSearchAdapter = new SearchResultAdapter(task -> {
            hideKeyboard();
            mViewModel.loadTaskForEdit(task.id);
            // 等待后台加载完后跳转编辑页
            getBinding().etTaskContent.postDelayed(() -> {
                View currentView = getView();
                if (currentView == null) return;
                Navigation.findNavController(currentView)
                    .navigate(R.id.action_taskInputFragment_to_taskEditFragment);
            }, 150);
        });
        getBinding().rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        getBinding().rvSearchResults.setAdapter(mSearchAdapter);

        mViewModel.getSearchResults().observe(getViewLifecycleOwner(), tasks -> {
            if (tasks == null || tasks.isEmpty()) {
                getBinding().rvSearchResults.setVisibility(View.GONE);
            } else {
                getBinding().rvSearchResults.setVisibility(View.VISIBLE);
                mSearchAdapter.setTasks(tasks);
            }
        });

        mViewModel.getSearchTokens().observe(getViewLifecycleOwner(), tokens -> {
            mSearchAdapter.setTokens(tokens);
        });

        mViewModel.getTagNamesMap().observe(getViewLifecycleOwner(), tagNames -> {
            mSearchAdapter.setTagNames(tagNames);
        });
        mViewModel.loadTagNamesMap();
    }

    private void setupNextButton() {
        getBinding().btnNext.setOnClickListener(v -> {
            String fullInput = getBinding().etTaskContent.getText().toString();
            String[] lines = fullInput.split("\\n", 2);
            mViewModel.setTitle(lines[0].trim());
            mViewModel.setMarkdown(lines.length > 1 ? lines[1].trim() : "");
            Navigation.findNavController(v).navigate(R.id.action_taskInputFragment_to_taskEditFragment);
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE);
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    // ==================== 搜索结果 Adapter ====================

    private static class SearchResultAdapter extends
        androidx.recyclerview.widget.RecyclerView.Adapter<SearchResultAdapter.Holder> {

        private List<TaskEntity> mTasks = new ArrayList<>();
        private List<String> mTokens = new ArrayList<>();
        private java.util.Map<Long, String> mTagNames = new java.util.HashMap<>();
        private final OnTaskClickListener mListener;
        private static final int HIGHLIGHT_COLOR = Color.parseColor("#FFF176");

        interface OnTaskClickListener {
            void onTaskClick(TaskEntity task);
        }

        SearchResultAdapter(OnTaskClickListener listener) {
            this.mListener = listener;
        }

        void setTasks(List<TaskEntity> tasks) {
            this.mTasks = tasks;
            notifyDataSetChanged();
        }

        void setTokens(List<String> tokens) {
            this.mTokens = tokens != null ? tokens : new ArrayList<>();
            notifyDataSetChanged();
        }

        void setTagNames(java.util.Map<Long, String> tagNames) {
            this.mTagNames = tagNames != null ? tagNames : new java.util.HashMap<>();
            notifyDataSetChanged();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            TaskEntity task = mTasks.get(position);
            String line = formatTaskLine(task);
            holder.text1.setText(highlightTitle(line, task.content));
            holder.itemView.setOnClickListener(v -> mListener.onTaskClick(task));
        }

        private String formatTaskLine(TaskEntity task) {
            StringBuilder sb = new StringBuilder();
            if (task.focusMinutes > 0) {
                sb.append(task.focusMinutes).append("分钟");
            }
            if (task.tagId != null && task.tagId > 0) {
                String tagName = mTagNames.get(task.tagId);
                if (tagName != null && !tagName.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append("#").append(tagName);
                }
            }
            String title = task.content != null ? task.content : "";
            if (sb.length() > 0 && !title.isEmpty()) sb.append(" ");
            sb.append(title);
            return sb.toString();
        }

        /** 仅对任务标题部分应用搜索高亮 */
        private SpannableString highlightTitle(String fullLine, String title) {
            if (title == null) title = "";
            int titleStart = fullLine.length() - title.length();
            if (titleStart < 0) titleStart = 0;
            SpannableString spannable = new SpannableString(fullLine);
            String lowerText = fullLine.toLowerCase();
            for (String token : mTokens) {
                String lowerToken = token.toLowerCase();
                int start = lowerText.indexOf(lowerToken, titleStart);
                while (start >= 0) {
                    int end = start + lowerToken.length();
                    spannable.setSpan(new BackgroundColorSpan(HIGHLIGHT_COLOR),
                        start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = lowerText.indexOf(lowerToken, end);
                }
            }
            return spannable;
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }

        static class Holder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            android.widget.TextView text1;
            Holder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
            }
        }
    }
}
