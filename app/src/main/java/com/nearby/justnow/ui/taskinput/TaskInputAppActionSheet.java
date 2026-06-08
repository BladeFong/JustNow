package com.nearby.justnow.ui.taskinput;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * APP 跳转编辑器 BottomSheet
 */
public class TaskInputAppActionSheet extends BottomSheetDialogFragment {

    private final Consumer<List<TaskAppAction>> mOnSaved;
    private final List<TaskAppAction> mActions = new ArrayList<>();
    private final IdentityHashMap<TaskAppAction, AppLaunchCatalogCache.AppInfo> mAddedAppInfos =
        new IdentityHashMap<>();
    private AppLaunchCatalogCache mCatalogCache;
    private AppActionAdapter mAdapter;
    private Button mBtnAdd;
    private RecyclerView mRvActions;

    public TaskInputAppActionSheet(Consumer<List<TaskAppAction>> onSaved) {
        mOnSaved = onSaved;
    }

    public void setExistingActions(List<TaskAppAction> actions) {
        mActions.clear();
        mAddedAppInfos.clear();
        if (actions != null) {
            mActions.addAll(actions);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_app_action_editor, null);
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

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mCatalogCache = app.getAppLaunchCatalogCache();

        mBtnAdd = view.findViewById(R.id.btn_add);
        mRvActions = view.findViewById(R.id.rv_actions);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        mAdapter = new AppActionAdapter(mActions,
            item -> {
                mActions.remove(item);
                mAddedAppInfos.remove(item);
                mAdapter.notifyDataSetChanged();
            },
            this::showEditDialog);
        mRvActions.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRvActions.setAdapter(mAdapter);

        mBtnAdd.setOnClickListener(v -> handleAddClick());
        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (mOnSaved != null) {
                for (int i = 0; i < mActions.size(); i++) {
                    mActions.get(i).orderIndex = i;
                }
                mOnSaved.accept(new ArrayList<>(mActions));
            }
            dismiss();
        });

        mCatalogCache.getStatus().observe(this, this::updateAddButtonState);
        updateAddButtonState(mCatalogCache.getCurrentStatus());
        mCatalogCache.loadIfNeeded();

        // 消费外部捕获预填项（CapturePicker → 编辑页 → 自动打开 sheet 路径）
        TaskInputViewModel vm = new ViewModelProvider(requireActivity(),
                new ViewModelFactory((JustNowApplication) requireActivity().getApplication()))
                .get(TaskInputViewModel.class);
        TaskAppAction prefill = vm.consumePendingAppActionPrefill();
        if (prefill != null) {
            mActions.add(0, prefill);
            mAdapter.notifyItemInserted(0);
            mRvActions.post(() -> mRvActions.scrollToPosition(0));
        }

        return dialog;
    }

    private void handleAddClick() {
        AppLaunchCatalogCache.Status status = mCatalogCache.getCurrentStatus();
        if (status == AppLaunchCatalogCache.Status.FAILED
            || status == AppLaunchCatalogCache.Status.NOT_LOADED) {
            mCatalogCache.reload();
            return;
        }
        if (status != AppLaunchCatalogCache.Status.LOADED) return;
        showAddDialog();
    }

    private void updateAddButtonState(AppLaunchCatalogCache.Status status) {
        if (mBtnAdd == null || status == null) return;
        if (status == AppLaunchCatalogCache.Status.LOADING) {
            mBtnAdd.setEnabled(false);
            mBtnAdd.setText(R.string.s_loading);
        } else if (status == AppLaunchCatalogCache.Status.FAILED) {
            mBtnAdd.setEnabled(true);
            mBtnAdd.setText(R.string.s_retry);
        } else {
            mBtnAdd.setEnabled(status == AppLaunchCatalogCache.Status.LOADED);
            mBtnAdd.setText(R.string.s_add);
        }
    }

    /** 编辑模式：仅允许改 hint，APP 选择只读 */
    private void showEditDialog(TaskAppAction existing) {
        showDialog(existing);
    }

    private void showAddDialog() {
        showDialog(null);
    }

    private void showDialog(@Nullable TaskAppAction existing) {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_app_action_add, null);
        AutoCompleteTextView etAppSearch = dialogView.findViewById(R.id.et_app_search);
        EditText etHint = dialogView.findViewById(R.id.et_app_hint);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialogView.findViewById(R.id.btn_add_to_list);

        final AppLaunchCatalogCache.AppInfo[] selectedApp = new AppLaunchCatalogCache.AppInfo[1];

        AlertDialog alertDialog = new AlertDialog.Builder(
            requireContext(), R.style.ThemeOverlay_JustNow_AlertDialog)
            .setView(dialogView)
            .create();

        if (existing != null) {
            // 编辑模式：APP 选择只读，仅改 hint
            etAppSearch.setEnabled(false);
            etAppSearch.setFocusable(false);
            etAppSearch.setFocusableInTouchMode(false);
            AppLaunchCatalogCache.AppInfo existingAppInfo = mAddedAppInfos.get(existing);
            Drawable icon = null;
            String label = existing.packageName;
            if (existingAppInfo != null) {
                icon = existingAppInfo.icon;
                label = existingAppInfo.label;
            } else if (existing.packageName != null && !existing.packageName.isEmpty()) {
                PackageManager pm = requireContext().getPackageManager();
                try {
                    icon = pm.getApplicationIcon(existing.packageName);
                } catch (PackageManager.NameNotFoundException ignored) {}
                try {
                    CharSequence l = pm.getApplicationLabel(
                        pm.getApplicationInfo(existing.packageName, 0));
                    if (l != null) label = l.toString();
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
            etAppSearch.setText(label != null ? label : "");
            if (icon != null) {
                int size = (int) (etAppSearch.getResources().getDisplayMetrics().density * 36);
                icon.setBounds(0, 0, size, size);
                etAppSearch.setCompoundDrawablesRelative(icon, null, null, null);
            }
            etHint.setText(existing.hint != null ? existing.hint : "");
            btnConfirm.setEnabled(true);
            btnConfirm.setText(R.string.s_confirm);
        } else {
            // 新增模式：启用 APP 搜索
            AppSearchAdapter searchAdapter = new AppSearchAdapter(requireContext(), mCatalogCache);
            etAppSearch.setAdapter(searchAdapter);
            etAppSearch.setThreshold(0);
            etAppSearch.setOnItemClickListener((parent, v, pos, id) -> {
                selectedApp[0] = (AppLaunchCatalogCache.AppInfo) parent.getItemAtPosition(pos);
                applySelectedAppIcon(etAppSearch, selectedApp[0]);
                btnConfirm.setEnabled(true);
            });
            etAppSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    if (selectedApp[0] != null
                        && !selectedApp[0].label.contentEquals(s)) {
                        selectedApp[0] = null;
                        applySelectedAppIcon(etAppSearch, null);
                        btnConfirm.setEnabled(false);
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        btnCancel.setOnClickListener(v -> alertDialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (existing != null) {
                existing.hint = etHint.getText().toString().trim();
                int idx = mActions.indexOf(existing);
                if (idx >= 0) mAdapter.notifyItemChanged(idx);
            } else {
                if (selectedApp[0] == null) return;
                TaskAppAction action = new TaskAppAction();
                action.packageName = selectedApp[0].packageName;
                action.hint = etHint.getText().toString().trim();
                mAddedAppInfos.put(action, selectedApp[0]);
                mActions.add(0, action);
                mAdapter.notifyItemInserted(0);
                mRvActions.scrollToPosition(0);
            }
            alertDialog.dismiss();
        });

        View focusTarget = existing != null ? etHint : etAppSearch;
        alertDialog.setOnShowListener(d -> {
            focusTarget.requestFocus();
            focusTarget.postDelayed(() -> {
                Context context = getContext();
                if (context == null) return;
                InputMethodManager imm = (InputMethodManager) context
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(focusTarget, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        });
        alertDialog.show();
    }

    /** 把当前选中 APP 图标作为 compoundDrawableStart 展示在搜索框内，未选中则清除 */
    private void applySelectedAppIcon(AutoCompleteTextView etAppSearch,
                                      AppLaunchCatalogCache.AppInfo selectedApp) {
        Drawable icon = selectedApp != null ? selectedApp.icon : null;
        if (icon != null) {
            int size = (int) (etAppSearch.getResources().getDisplayMetrics().density * 36);
            icon.setBounds(0, 0, size, size);
        }
        etAppSearch.setCompoundDrawablesRelative(icon, null, null, null);
    }

    /** 带应用图标的搜索下拉 Adapter */
    private static class AppSearchAdapter extends ArrayAdapter<AppLaunchCatalogCache.AppInfo> {

        private final AppLaunchCatalogCache mCatalogCache;
        private List<AppLaunchCatalogCache.AppInfo> mFilteredApps;
        private final AppFilter mFilter = new AppFilter();

        AppSearchAdapter(Context context, AppLaunchCatalogCache catalogCache) {
            super(context, 0, catalogCache.getApps());
            mCatalogCache = catalogCache;
            mFilteredApps = catalogCache.getApps();
        }

        @Override
        public int getCount() {
            return mFilteredApps.size();
        }

        @Override
        public AppLaunchCatalogCache.AppInfo getItem(int position) {
            return mFilteredApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return mFilter;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_search_dropdown, parent, false);
            }
            ImageView ivIcon = v.findViewById(R.id.iv_app_icon);
            TextView tvLabel = v.findViewById(R.id.tv_app_label);

            AppLaunchCatalogCache.AppInfo app = mFilteredApps.get(position);
            ivIcon.setImageDrawable(app.icon);
            tvLabel.setText(app.label);
            return v;
        }

        @NonNull
        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return getView(position, convertView, parent);
        }

        private class AppFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                List<AppLaunchCatalogCache.AppInfo> result = mCatalogCache.filter(
                    constraint != null ? constraint.toString() : "");
                FilterResults results = new FilterResults();
                results.values = result;
                results.count = result.size();
                return results;
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return resultValue instanceof AppLaunchCatalogCache.AppInfo
                    ? ((AppLaunchCatalogCache.AppInfo) resultValue).label : "";
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mFilteredApps = results.values != null
                    ? (List<AppLaunchCatalogCache.AppInfo>) results.values
                    : new ArrayList<>();
                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        }
    }

    private class AppActionAdapter extends RecyclerView.Adapter<AppActionAdapter.Holder> {

        private final List<TaskAppAction> mItems;
        private final Consumer<TaskAppAction> mOnDelete;
        private final Consumer<TaskAppAction> mOnEdit;

        AppActionAdapter(List<TaskAppAction> items,
                         Consumer<TaskAppAction> onDelete,
                         Consumer<TaskAppAction> onEdit) {
            mItems = items;
            mOnDelete = onDelete;
            mOnEdit = onEdit;
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_action, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int pos) {
            TaskAppAction action = mItems.get(pos);
            AppDisplayInfo displayInfo = resolveDisplayInfo(holder, action);
            if (displayInfo.icon != null) {
                holder.icon.setImageDrawable(displayInfo.icon);
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            holder.text.setText(action.hint != null && !action.hint.isEmpty()
                ? action.hint : displayInfo.label);
            holder.btnEdit.setOnClickListener(v -> {
                int idx = holder.getBindingAdapterPosition();
                if (idx != RecyclerView.NO_POSITION && mOnEdit != null) {
                    mOnEdit.accept(mItems.get(idx));
                }
            });
            holder.btnDelete.setOnClickListener(v -> {
                int idx = holder.getBindingAdapterPosition();
                if (idx != RecyclerView.NO_POSITION && mOnDelete != null) {
                    mOnDelete.accept(mItems.get(idx));
                }
            });
        }

        private AppDisplayInfo resolveDisplayInfo(Holder holder, TaskAppAction action) {
            AppLaunchCatalogCache.AppInfo addedAppInfo = mAddedAppInfos.get(action);
            if (addedAppInfo != null) {
                return new AppDisplayInfo(addedAppInfo.icon, addedAppInfo.label);
            }
            if (action.packageName == null || action.packageName.isEmpty()) {
                return new AppDisplayInfo(null, "");
            }

            PackageManager pm = holder.itemView.getContext().getPackageManager();
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon(action.packageName);
            } catch (PackageManager.NameNotFoundException ignored) {}

            String label = null;
            try {
                label = pm.getApplicationLabel(
                    pm.getApplicationInfo(action.packageName, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) {}
            if (label == null || label.isEmpty()) {
                label = action.packageName;
            }
            return new AppDisplayInfo(icon, label);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView text;
            Button btnEdit;
            Button btnDelete;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.iv_app_icon);
                text = v.findViewById(R.id.tv_action_text);
                btnEdit = v.findViewById(R.id.btn_edit);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }

    private static class AppDisplayInfo {
        final Drawable icon;
        final String label;

        AppDisplayInfo(Drawable icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }
}
