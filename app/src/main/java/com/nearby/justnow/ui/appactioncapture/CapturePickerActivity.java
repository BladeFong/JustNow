package com.nearby.justnow.ui.appactioncapture;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.ui.base.TaskDisplayHelper;
import com.nearby.justnow.ui.taskinput.TaskInputActivity;
import com.nearby.justnow.util.TextTokenizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 捕获后任务选择 / 新建页 —
 * 列出含 APP 跳转附加模块、未归档的任务，支持选已有或新建（APP 跳转 / 笔记）
 */
public class CapturePickerActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_CAPTURE = "capture";  // 捕获 Intent
    public static final String MODE_NOTE = "note";        // 非 URL 文本笔记

    public static final String EXTRA_CAPTURED_INTENT_URI = "extra_captured_intent_uri";
    public static final String EXTRA_NOTE_TEXT = "extra_note_text";
    public static final String EXTRA_SHORT_TITLE = "extra_short_title";
    public static final String EXTRA_REFERRER_LABEL = "extra_referrer_label";
    public static final String EXTRA_ALLOW_NOTE_ENTRY = "extra_allow_note_entry";

    private String mMode;
    private String mCapturedIntentUri;
    private String mNoteText;
    private String mShortTitle;
    private String mReferrerLabel;
    private boolean mAllowNoteEntry;

    private final List<TaskEntity> mAllTasks = new ArrayList<>();
    private final List<TaskEntity> mFilteredTasks = new ArrayList<>();
    private Map<Long, String> mTagNames = new HashMap<>();
    private TaskAdapter mAdapter;
    private TextView mTvEmpty;
    private List<String> mTokens = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_picker);

        Intent intent = getIntent();
        mMode = intent.getStringExtra(EXTRA_MODE);
        mCapturedIntentUri = intent.getStringExtra(EXTRA_CAPTURED_INTENT_URI);
        mNoteText = intent.getStringExtra(EXTRA_NOTE_TEXT);
        mShortTitle = intent.getStringExtra(EXTRA_SHORT_TITLE);
        mReferrerLabel = intent.getStringExtra(EXTRA_REFERRER_LABEL);
        mAllowNoteEntry = intent.getBooleanExtra(EXTRA_ALLOW_NOTE_ENTRY, false);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            View appBar = findViewById(R.id.app_bar);
            appBar.setPadding(appBar.getPaddingLeft(), top,
                    appBar.getPaddingRight(), appBar.getPaddingBottom());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), imeBottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.s_capture_picker_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        setupList();
        setupSearch();
        setupBottomButtons();
        applyModeVisibility();
        loadTasks();
    }

    private void setupList() {
        RecyclerView rv = findViewById(R.id.rv_tasks);
        rv.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new TaskAdapter(mFilteredTasks, this::onTaskSelected);
        rv.setAdapter(mAdapter);
        mTvEmpty = findViewById(R.id.tv_empty);
    }

    private void setupSearch() {
        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                applyFilter(s.toString().trim());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBottomButtons() {
        View btnNewAppAction = findViewById(R.id.btn_new_app_action);
        View btnNewNote = findViewById(R.id.btn_new_note);
        btnNewAppAction.setOnClickListener(v -> onNewWithAppAction());

        // 动态切换笔记按钮文案与行为
        boolean isNoteShareMode = MODE_CAPTURE.equals(mMode) && mAllowNoteEntry;
        if (isNoteShareMode) {
            // SEND URL 流：笔记分享（新建任务 + 打开笔记分享 sheet 预填 URL）
            ((android.widget.Button) btnNewNote).setText(R.string.s_capture_picker_new_note_share);
            btnNewNote.setOnClickListener(v -> onNewWithNoteShare());
        } else {
            // MODE_NOTE（纯文本）：原行为（灌 markdown 任务正文）
            btnNewNote.setOnClickListener(v -> onNewWithNote());
        }
    }

    private void applyModeVisibility() {
        View btnNewAppAction = findViewById(R.id.btn_new_app_action);
        if (MODE_NOTE.equals(mMode)) {
            // 非 URL 笔记流：仅显示笔记入口；不允许加进已有任务的 APP 跳转 sheet
            btnNewAppAction.setVisibility(View.GONE);
        } else if (MODE_CAPTURE.equals(mMode) && !mAllowNoteEntry) {
            // VIEW 入口（非 SEND）：捕获到的 Intent 与笔记语义不符，隐藏"新建笔记"
            View btnNewNote = findViewById(R.id.btn_new_note);
            btnNewNote.setVisibility(View.GONE);
        }
    }

    private void loadTasks() {
        if (MODE_NOTE.equals(mMode)) {
            // 笔记流不需要列任务，直接显示空态文案
            mAllTasks.clear();
            mTagNames.clear();
            applyFilter("");
            return;
        }
        JustNowApplication app = (JustNowApplication) getApplication();
        TaskRepository repo = app.getTaskRepository();
        TagRepository tagRepo = app.getTagRepository();
        AppDatabase.execute(() -> {
            List<TaskEntity> tasks = repo.getTasksWithAppActionSync();
            Map<Long, TagEntity> tagMap = tagRepo.getAllTagsMapSync();
            Map<Long, String> tagNames = new HashMap<>();
            if (tagMap != null) {
                for (Map.Entry<Long, TagEntity> e : tagMap.entrySet()) {
                    TagEntity tag = e.getValue();
                    if (tag != null) tagNames.put(e.getKey(), tag.name);
                }
            }
            runOnUiThread(() -> {
                mAllTasks.clear();
                if (tasks != null) mAllTasks.addAll(tasks);
                mTagNames = tagNames;
                mAdapter.setTagNames(mTagNames);
                EditText etSearch = findViewById(R.id.et_search);
                applyFilter(etSearch.getText().toString().trim());
            });
        });
    }

    private void applyFilter(String keyword) {
        mFilteredTasks.clear();
        if (keyword.isEmpty()) {
            mFilteredTasks.addAll(mAllTasks);
            mTokens = new ArrayList<>();
        } else {
            List<String> tokens = TextTokenizer.tokenize(keyword);
            mTokens = tokens;
            for (TaskEntity t : mAllTasks) {
                if (matchesAllTokens(t, tokens)) mFilteredTasks.add(t);
            }
        }
        mAdapter.setTokens(mTokens);
        mAdapter.notifyDataSetChanged();
        boolean empty = mFilteredTasks.isEmpty();
        mTvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.rv_tasks).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean matchesAllTokens(TaskEntity t, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return true;
        String content = t.content != null ? t.content.toLowerCase() : "";
        String detail = t.detail != null ? t.detail.toLowerCase() : "";
        for (String token : tokens) {
            String lower = token.toLowerCase();
            if (!content.contains(lower) && !detail.contains(lower)) return false;
        }
        return true;
    }

    // ---- 出口 ----

    private void onTaskSelected(TaskEntity task) {
        // 入口 1：加进已有任务的 APP 跳转 sheet
        Intent intent = new Intent(this, TaskInputActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(TaskInputActivity.EXTRA_LOAD_TASK_ID, task.id);
        intent.putExtra(TaskInputActivity.EXTRA_OPEN_APP_ACTION_SHEET, true);
        intent.putExtra(TaskInputActivity.EXTRA_PREFILL_APP_ACTION_URI, mCapturedIntentUri);
        intent.putExtra(TaskInputActivity.EXTRA_PREFILL_APP_ACTION_HINT, mShortTitle);
        startActivity(intent);
        finish();
    }

    private void onNewWithAppAction() {
        // 入口 2：新建任务，预填 APP 跳转项
        Intent intent = new Intent(this, TaskInputActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TITLE,
                mShortTitle != null ? mShortTitle : "");
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TAG_NAME, mReferrerLabel);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_OPEN_APP_ACTION_SHEET, true);
        intent.putExtra(TaskInputActivity.EXTRA_PREFILL_APP_ACTION_URI, mCapturedIntentUri);
        intent.putExtra(TaskInputActivity.EXTRA_PREFILL_APP_ACTION_HINT, mShortTitle);
        startActivity(intent);
        finish();
    }

    /** 入口 3a：SEND URL 流 → 新建任务 + 笔记分享 sheet 预填 */
    private void onNewWithNoteShare() {
        String title = mShortTitle != null ? mShortTitle : "";
        Intent intent = new Intent(this, TaskInputActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TITLE, title);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TAG_NAME, mReferrerLabel);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_OPEN_NOTE_SHARE_SHEET, true);
        intent.putExtra(TaskInputActivity.EXTRA_PREFILL_NOTE_SHARE_URI,
                mCapturedIntentUri != null ? mCapturedIntentUri : resolveDataString());
        intent.putExtra(TaskInputActivity.EXTRA_PREFILL_NOTE_SHARE_HINT, mShortTitle);
        startActivity(intent);
        finish();
    }

    /** 入口 3b：MODE_NOTE（纯文本）→ 新建任务灌 markdown（原行为不动） */
    private void onNewWithNote() {
        String title = mShortTitle;
        if ((title == null || title.isEmpty()) && mReferrerLabel != null && !mReferrerLabel.isEmpty()) {
            title = getString(R.string.s_capture_share_title_fallback, mReferrerLabel);
        }
        String markdown = MODE_NOTE.equals(mMode) ? mNoteText : resolveDataString();
        Intent intent = new Intent(this, TaskInputActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TITLE, title != null ? title : "");
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_TAG_NAME, mReferrerLabel);
        intent.putExtra(TaskInputActivity.EXTRA_DRAFT_TASK_MARKDOWN, markdown != null ? markdown : "");
        startActivity(intent);
        finish();
    }

    /** MODE_CAPTURE (URL) → 从 capturedIntent 拿 data 字符串 */
    @Nullable
    private String resolveDataString() {
        if (mCapturedIntentUri == null) return null;
        try {
            Intent ci = Intent.parseUri(mCapturedIntentUri, Intent.URI_INTENT_SCHEME);
            Uri data = ci.getData();
            return data != null ? data.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- Adapter ----

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Holder> {
        interface OnClick { void on(TaskEntity task); }

        private final List<TaskEntity> mItems;
        private final OnClick mOnClick;
        private List<String> mTokens = new ArrayList<>();
        private Map<Long, String> mTagNames = new HashMap<>();

        TaskAdapter(List<TaskEntity> items, OnClick onClick) {
            mItems = items;
            mOnClick = onClick;
        }

        void setTokens(List<String> tokens) {
            mTokens = tokens != null ? tokens : new ArrayList<>();
        }

        void setTagNames(Map<Long, String> tagNames) {
            mTagNames = tagNames != null ? tagNames : new HashMap<>();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_result, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            TaskEntity task = mItems.get(position);
            String line = TaskDisplayHelper.formatTaskLine(task, mTagNames,
                    holder.itemView.getContext().getResources());
            holder.text1.setText(TaskDisplayHelper.highlightTitle(line, task.content, mTokens));
            holder.itemView.setOnClickListener(v -> mOnClick.on(task));
        }

        @Override public int getItemCount() { return mItems.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            TextView text1;
            Holder(View v) {
                super(v);
                text1 = v.findViewById(android.R.id.text1);
            }
        }
    }
}
