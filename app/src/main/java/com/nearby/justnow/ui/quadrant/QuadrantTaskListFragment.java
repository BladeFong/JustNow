package com.nearby.justnow.ui.quadrant;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.databinding.FragmentQuadrantTaskListBinding;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单象限全任务列表 — 从四象限概览点 ✏️ 进入。
 * 支持 Toolbar 时长/标签筛选（PopupWindow 下拉）、长按多选删除。
 */
public class QuadrantTaskListFragment extends Fragment {

    private static final int[] sQuadrantTitleKeys = {
            R.string.s_quadrant_task_list_title_0,
            R.string.s_quadrant_task_list_title_1,
            R.string.s_quadrant_task_list_title_2,
            R.string.s_quadrant_task_list_title_3,
    };

    /** 时长筛选档位的 focusMinutes 值，与 sFocusFilterLabelKeys 一一对应 */
    private static final int[] sFocusFilterValues = {0, 30, 60, 90, 120};

    private static final int[] sFocusFilterLabelKeys = {
            R.string.s_chore_label,
            R.string.s_30min_label,
            R.string.s_60min_label,
            R.string.s_90min_label,
            R.string.s_120min_label,
    };

    private FragmentQuadrantTaskListBinding mBinding;
    private QuadrantTaskListViewModel mViewModel;
    private QuadrantTaskListAdapter mAdapter;
    private MaterialToolbar mToolbar;
    private NavController mNavController;
    private boolean mSelectionModeHandled = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentQuadrantTaskListBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupOptionsMenu();

        mToolbar = requireActivity().findViewById(R.id.toolbar);
        mNavController = NavHostFragment.findNavController(this);

        // 从 nav argument 获取象限索引
        int quadrant = readQuadrantArgument();

        // 创建 ViewModel
        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(this, new ViewModelFactory(app))
                .get(QuadrantTaskListViewModel.class);
        mViewModel.setQuadrant(quadrant);

        // 设置标题
        requireActivity().setTitle(getString(sQuadrantTitleKeys[quadrant]));

        // RecyclerView
        mAdapter = new QuadrantTaskListAdapter();
        mAdapter.setViewModel(mViewModel);
        mBinding.rvTaskList.setLayoutManager(new GridLayoutManager(requireContext(), 1));
        mBinding.rvTaskList.setAdapter(mAdapter);

        // 观察筛选结果
        mViewModel.getFilteredItems().observe(getViewLifecycleOwner(), items -> {
            mAdapter.setItems(items);
            boolean empty = items == null || items.isEmpty();
            mBinding.rvTaskList.setVisibility(empty ? View.GONE : View.VISIBLE);
            mBinding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        // 观察多选模式 — 跳过首次回调（初始值 false），避免覆盖 NavigationUI 已设的返回箭头
        mViewModel.getSelectionMode().observe(getViewLifecycleOwner(), inSelection -> {
            if (mSelectionModeHandled) {
                onSelectionModeChanged(inSelection);
            } else {
                mSelectionModeHandled = true;
                mAdapter.setSelectionMode(false);
            }
        });

        // 单击 → 任务详情（查看模式）
        mAdapter.setOnItemClickListener(item -> {
            Intent intent = new Intent(getActivity(),
                    com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
            intent.putExtra("task_id", item.task.id);
            intent.putExtra(com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.EXTRA_MODE,
                    com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.MODE_VIEW);
            startActivity(intent);
        });

        // 长按 → 进入多选
        mAdapter.setOnItemLongClickListener(item -> {
            if (!Boolean.TRUE.equals(mViewModel.getSelectionMode().getValue())) {
                mViewModel.enterSelectionMode(item.task.id);
            }
        });

        // 系统返回键拦截
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (Boolean.TRUE.equals(mViewModel.getSelectionMode().getValue())) {
                            mViewModel.exitSelectionMode();
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });
    }

    /** 多选模式切换时更新 Toolbar */
    private void onSelectionModeChanged(boolean inSelection) {
        mAdapter.setSelectionMode(inSelection);
        requireActivity().invalidateOptionsMenu();

        ActionBar actionBar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (actionBar == null) return;

        if (inSelection) {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_24);
            mToolbar.setNavigationOnClickListener(v -> mViewModel.exitSelectionMode());
            updateSelectionTitle();
        } else {
            // 恢复默认返回箭头（使用 AppCompat 内置图标）
            actionBar.setHomeAsUpIndicator(
                    androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            mToolbar.setNavigationOnClickListener(v -> {
                if (mNavController != null) {
                    NavigationUI.navigateUp(mNavController,
                            new AppBarConfiguration.Builder(mNavController.getGraph()).build());
                }
            });
            int quadrant = mViewModel.getQuadrant();
            if (quadrant >= 0 && quadrant < 4) {
                requireActivity().setTitle(getString(sQuadrantTitleKeys[quadrant]));
            }
        }
    }

    private int readQuadrantArgument() {
        int quadrant = getArguments() != null ? getArguments().getInt("quadrant", 0) : 0;
        if (quadrant < 0 || quadrant >= sQuadrantTitleKeys.length) {
            return 0;
        }
        return quadrant;
    }

    private void updateSelectionTitle() {
        int count = mViewModel.getSelectedCount();
        requireActivity().setTitle(getString(R.string.s_selected_count, count));
    }

    // ---- 选项菜单 ----

    private void setupOptionsMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_quadrant_task_list, menu);
            }

            @Override
            public void onPrepareMenu(@NonNull Menu menu) {
                prepareOptionsMenu(menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return handleOptionsItemSelected(menuItem);
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void prepareOptionsMenu(Menu menu) {
        boolean inSelection = Boolean.TRUE.equals(mViewModel.getSelectionMode().getValue());
        menu.findItem(R.id.action_focus_filter).setVisible(!inSelection);
        menu.findItem(R.id.action_tag_filter).setVisible(!inSelection);
        menu.findItem(R.id.action_delete_selected).setVisible(inSelection);
    }

    private boolean handleOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            if (Boolean.TRUE.equals(mViewModel.getSelectionMode().getValue())) {
                mViewModel.exitSelectionMode();
                return true;
            }
            return NavigationUI.onNavDestinationSelected(item, mNavController)
                    || false;
        }

        if (itemId == R.id.action_focus_filter) {
            showFocusFilterPopup(item);
            return true;
        }
        if (itemId == R.id.action_tag_filter) {
            showTagFilterPopup(item);
            return true;
        }
        if (itemId == R.id.action_delete_selected) {
            showDeleteConfirmDialog();
            return true;
        }

        return false;
    }

    // ---- 时长筛选 PopupWindow ----

    private void showFocusFilterPopup(MenuItem anchorItem) {
        View anchor = mToolbar.findViewById(anchorItem.getItemId());
        if (anchor == null) anchor = mToolbar;

        final int n = sFocusFilterValues.length;
        final CheckBox[] checkBoxes = new CheckBox[n];
        final Set<Integer> selected = mViewModel.getFocusFilterMinutes();

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 8, 0, 8);
        content.setBackgroundColor(Color.WHITE);

        for (int i = 0; i < n; i++) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setText(getString(sFocusFilterLabelKeys[i]));
            cb.setTextAppearance(R.style.TextAppearance_JustNow_Caption);
            cb.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            cb.setChecked(selected.contains(sFocusFilterValues[i]));
            cb.setMinHeight(getResources().getDimensionPixelSize(R.dimen.task_item_total_height) * 6 / 10);
            cb.setPadding(24, 4, 24, 4);
            checkBoxes[i] = cb;
            content.addView(cb);
        }

        PopupWindow popup = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setElevation(12);

        popup.setOnDismissListener(() -> {
            Set<Integer> pending = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if (checkBoxes[i].isChecked()) {
                    pending.add(sFocusFilterValues[i]);
                }
            }
            mViewModel.setFocusFilterMinutes(pending);
        });

        popup.showAsDropDown(anchor, 0, 0);
    }

    // ---- 标签筛选 PopupWindow（仅显示当前象限列表中实际使用的标签） ----

    private void showTagFilterPopup(MenuItem anchorItem) {
        final View anchor = mToolbar.findViewById(anchorItem.getItemId());
        final View fallbackAnchor = (anchor != null) ? anchor : mToolbar;

        // 直接从 ViewModel 获取当前列表中使用的标签（已在 mAllItems 中缓存，无 DB 查询）
        final List<TagEntity> usedTags = mViewModel.getTagsInCurrentList();
        final Set<Long> selected = mViewModel.getSelectedTagIds();
        final CheckBox[] checkBoxes = new CheckBox[usedTags.size()];

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 8, 0, 8);
        content.setBackgroundColor(Color.WHITE);

        if (usedTags.isEmpty()) {
            CheckBox cb = new CheckBox(requireContext());
            cb.setText(R.string.s_no_tag);
            cb.setTextAppearance(R.style.TextAppearance_JustNow_Caption);
            cb.setEnabled(false);
            cb.setPadding(24, 4, 24, 4);
            content.addView(cb);
        } else {
            for (int i = 0; i < usedTags.size(); i++) {
                TagEntity tag = usedTags.get(i);
                CheckBox cb = new CheckBox(requireContext());
                cb.setText(getString(R.string.s_tag_name_format, tag.name));
                cb.setTextAppearance(R.style.TextAppearance_JustNow_Caption);
                cb.setTextColor(tag.color != 0 ? tag.color
                        : ContextCompat.getColor(requireContext(), R.color.tag_normal));
                cb.setChecked(selected.contains(tag.id));
                cb.setMinHeight(getResources().getDimensionPixelSize(
                        R.dimen.task_item_total_height) * 6 / 10);
                cb.setPadding(24, 4, 24, 4);
                checkBoxes[i] = cb;
                content.addView(cb);
            }
        }

        PopupWindow popup = new PopupWindow(content,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setElevation(12);

        popup.setOnDismissListener(() -> {
            Set<Long> pending = new HashSet<>();
            for (int i = 0; i < checkBoxes.length; i++) {
                if (checkBoxes[i] != null && checkBoxes[i].isChecked()) {
                    pending.add(usedTags.get(i).id);
                }
            }
            mViewModel.setSelectedTagIds(pending);
        });

        popup.showAsDropDown(fallbackAnchor, 0, 0);
    }

    // ---- 删除确认 ----

    private void showDeleteConfirmDialog() {
        int count = mViewModel.getSelectedCount();
        String message = getString(R.string.s_confirm_delete_tasks, count);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.s_delete)
                .setMessage(message)
                .setPositiveButton(R.string.s_delete, (dialog, which) -> {
                    mViewModel.deleteSelectedTasks(this::updateSelectionTitle);
                })
                .setNegativeButton(R.string.s_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
