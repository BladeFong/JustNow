package com.nearby.justnow.ui.taskinput;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskAppAction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * APP 跳转编辑器 BottomSheet
 */
public class TaskInputAppActionSheet extends BottomSheetDialogFragment {

    private final Consumer<List<TaskAppAction>> mOnSaved;
    private List<TaskAppAction> mActions = new ArrayList<>();
    private List<AppInfo> mInstalledApps = new ArrayList<>();
    private AppActionAdapter mAdapter;
    private AppInfo mSelectedApp;

    public TaskInputAppActionSheet(Consumer<List<TaskAppAction>> onSaved) {
        mOnSaved = onSaved;
    }

    public void setExistingActions(List<TaskAppAction> actions) {
        mActions = actions != null ? actions : new ArrayList<>();
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

        // 撑满可用高度
        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
            }
        });

        loadInstalledApps();

        AutoCompleteTextView etAppSearch = view.findViewById(R.id.et_app_search);
        EditText etHint = view.findViewById(R.id.et_app_hint);
        Button btnAdd = view.findViewById(R.id.btn_add);
        RecyclerView rvActions = view.findViewById(R.id.rv_actions);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        // 带图标的自动补全 Adapter
        AppSearchAdapter searchAdapter = new AppSearchAdapter(requireContext(), mInstalledApps);
        etAppSearch.setAdapter(searchAdapter);
        etAppSearch.setThreshold(1);

        etAppSearch.setOnItemClickListener((parent, v, pos, id) -> {
            mSelectedApp = (AppInfo) parent.getItemAtPosition(pos);
            applySelectedAppIcon(etAppSearch);
        });

        etAppSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (mSelectedApp != null
                    && !mSelectedApp.label.contentEquals(s)) {
                    mSelectedApp = null;
                    applySelectedAppIcon(etAppSearch);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        mAdapter = new AppActionAdapter(mActions, item -> {
            mActions.remove(item);
            mAdapter.notifyDataSetChanged();
        });
        rvActions.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvActions.setAdapter(mAdapter);

        btnAdd.setOnClickListener(v -> {
            if (mSelectedApp == null) {
                String text = etAppSearch.getText().toString().trim();
                if (text.isEmpty()) return;
                for (AppInfo info : mInstalledApps) {
                    if (info.label.toLowerCase().contains(text.toLowerCase())
                        || info.packageName.toLowerCase().contains(text.toLowerCase())) {
                        mSelectedApp = info;
                        break;
                    }
                }
                if (mSelectedApp == null) return;
            }
            TaskAppAction action = new TaskAppAction();
            action.packageName = mSelectedApp.packageName;
            action.hint = etHint.getText().toString().trim();
            action.orderIndex = mActions.size();
            mActions.add(action);
            mAdapter.notifyItemInserted(mActions.size() - 1);
            etAppSearch.setText("");
            etHint.setText("");
            mSelectedApp = null;
            applySelectedAppIcon(etAppSearch);
        });

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

        return dialog;
    }

    /** 把当前选中 APP 图标作为 compoundDrawableStart 展示在搜索框内，未选中则清除 */
    private void applySelectedAppIcon(AutoCompleteTextView etAppSearch) {
        Drawable icon = mSelectedApp != null ? mSelectedApp.icon : null;
        if (icon != null) {
            int size = (int) (etAppSearch.getResources().getDisplayMetrics().density * 36);
            icon.setBounds(0, 0, size, size);
        }
        etAppSearch.setCompoundDrawablesRelative(icon, null, null, null);
    }

    private void loadInstalledApps() {
        PackageManager pm = requireContext().getPackageManager();
        String selfPackage = requireContext().getPackageName();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
        for (ResolveInfo ri : apps) {
            String packageName = ri.activityInfo.packageName;
            if (selfPackage.equals(packageName)) continue;
            AppInfo info = new AppInfo();
            info.packageName = packageName;
            info.label = ri.loadLabel(pm).toString();
            info.icon = ri.loadIcon(pm);
            mInstalledApps.add(info);
        }
    }

    private static class AppInfo {
        String packageName;
        String label;
        Drawable icon;
    }

    /** 带应用图标的搜索下拉 Adapter */
    private static class AppSearchAdapter extends ArrayAdapter<AppInfo> {

        private final List<AppInfo> mAllApps;
        private List<AppInfo> mFilteredApps;
        private final AppFilter mFilter = new AppFilter();

        AppSearchAdapter(Context context, List<AppInfo> apps) {
            super(context, 0, apps);
            mAllApps = apps;
            mFilteredApps = new ArrayList<>(apps);
        }

        @Override
        public int getCount() {
            return mFilteredApps.size();
        }

        @Override
        public AppInfo getItem(int position) {
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

            AppInfo app = mFilteredApps.get(position);
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
                List<AppInfo> result = new ArrayList<>();
                if (constraint == null || constraint.length() == 0) {
                    result.addAll(mAllApps);
                } else {
                    String keyword = constraint.toString().toLowerCase();
                    for (AppInfo info : mAllApps) {
                        if (info.label.toLowerCase().contains(keyword)
                            || info.packageName.toLowerCase().contains(keyword)) {
                            result.add(info);
                        }
                    }
                }
                FilterResults fr = new FilterResults();
                fr.values = result;
                fr.count = result.size();
                return fr;
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return resultValue instanceof AppInfo ? ((AppInfo) resultValue).label : "";
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mFilteredApps = results.values != null
                    ? (List<AppInfo>) results.values
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

        AppActionAdapter(List<TaskAppAction> items, Consumer<TaskAppAction> onDelete) {
            mItems = items;
            mOnDelete = onDelete;
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
            // 查找已安装应用图标与名称
            Drawable icon = null;
            String label = null;
            for (AppInfo app : mInstalledApps) {
                if (app.packageName.equals(action.packageName)) {
                    icon = app.icon;
                    label = app.label;
                    break;
                }
            }
            holder.icon.setImageDrawable(icon);
            String fallback = label != null ? label : action.packageName;
            holder.text.setText(action.hint != null && !action.hint.isEmpty()
                ? action.hint : fallback);
            holder.btnDelete.setOnClickListener(v -> {
                int idx = holder.getAdapterPosition();
                if (idx != RecyclerView.NO_POSITION && mOnDelete != null) {
                    mOnDelete.accept(mItems.get(idx));
                }
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView text;
            Button btnDelete;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.iv_app_icon);
                text = v.findViewById(R.id.tv_action_text);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
