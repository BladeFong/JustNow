package com.nearby.justnow.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.databinding.FragmentQuadrantOverviewBinding;
import com.nearby.justnow.ui.base.ViewModelFactory;
import com.nearby.justnow.ui.engine.DisplayItem;

import java.util.List;

/**
 * ViewPager2 Page 1：四象限全任务概览。
 * 作为 MainFragment 的 childFragment，通过 getParentFragment() 共享 MainViewModel。
 */
public class QuadrantOverviewFragment extends Fragment {

    private static final int[] sQuadrantTitleKeys = {
            R.string.s_quadrant_overview_title_0,
            R.string.s_quadrant_overview_title_1,
            R.string.s_quadrant_overview_title_2,
            R.string.s_quadrant_overview_title_3,
    };

    private FragmentQuadrantOverviewBinding mBinding;
    private MainViewModel mViewModel;
    private final LinearLayout[] mTaskLists = new LinearLayout[4];
    private final LinearLayout[] mEmptyViews = new LinearLayout[4];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentQuadrantOverviewBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
        mViewModel = new ViewModelProvider(requireParentFragment(), new ViewModelFactory(app))
                .get(MainViewModel.class);

        // 收集各象限的容器引用
        mTaskLists[0] = mBinding.taskList0;
        mTaskLists[1] = mBinding.taskList1;
        mTaskLists[2] = mBinding.taskList2;
        mTaskLists[3] = mBinding.taskList3;

        mEmptyViews[0] = mBinding.empty0;
        mEmptyViews[1] = mBinding.empty1;
        mEmptyViews[2] = mBinding.empty2;
        mEmptyViews[3] = mBinding.empty3;

        mBinding.btnEdit0.setOnClickListener(v -> onEditClicked(0));
        mBinding.btnEdit1.setOnClickListener(v -> onEditClicked(1));
        mBinding.btnEdit2.setOnClickListener(v -> onEditClicked(2));
        mBinding.btnEdit3.setOnClickListener(v -> onEditClicked(3));

        mBinding.tvTitle0.setText(sQuadrantTitleKeys[0]);
        mBinding.tvTitle1.setText(sQuadrantTitleKeys[1]);
        mBinding.tvTitle2.setText(sQuadrantTitleKeys[2]);
        mBinding.tvTitle3.setText(sQuadrantTitleKeys[3]);

        // 观察象限分组结果
        mViewModel.getQuadrantResults().observe(getViewLifecycleOwner(), this::onQuadrantResults);
    }

    private void onQuadrantResults(MainViewModel.EngineResult[] results) {
        if (results == null) return;

        for (int q = 0; q < 4; q++) {
            List<DisplayItem> items = (results[q] != null && results[q].items != null)
                    ? results[q].items : null;
            renderQuadrant(q, items);
        }
    }

    private void renderQuadrant(int quadrantIndex, List<DisplayItem> items) {
        LinearLayout taskList = mTaskLists[quadrantIndex];
        LinearLayout emptyView = mEmptyViews[quadrantIndex];

        // 清除旧任务行
        taskList.removeAllViews();

        if (items == null || items.isEmpty()) {
            taskList.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        emptyView.setVisibility(View.GONE);
        taskList.setVisibility(View.VISIBLE);

        for (DisplayItem item : items) {
            View row = createTaskRow(taskList, item);
            taskList.addView(row);
        }
    }

    private View createTaskRow(ViewGroup parent, DisplayItem item) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quadrant_overview_task, parent, false);
        TextView titleTv = row.findViewById(R.id.tv_overview_task_title);
        titleTv.setText(item.task.content != null ? item.task.content : "");

        TextView durTv = row.findViewById(R.id.tv_overview_task_duration);
        if (item.task.focusMinutes > 0) {
            durTv.setText(getString(R.string.s_focus_minutes_format, item.task.focusMinutes));
            durTv.setVisibility(View.VISIBLE);
        } else {
            durTv.setVisibility(View.GONE);
        }

        row.setOnClickListener(v -> onTaskClicked(item.task.id));
        return row;
    }

    private void onTaskClicked(long taskId) {
        Intent intent = new Intent(getActivity(),
                com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.class);
        intent.putExtra("task_id", taskId);
        intent.putExtra(com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.EXTRA_MODE,
                com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity.MODE_VIEW);
        startActivity(intent);
    }

    private void onEditClicked(int quadrantIndex) {
        Bundle args = new Bundle();
        args.putInt("quadrant", quadrantIndex);
        NavHostFragment.findNavController(this)
                .navigate(R.id.quadrantTaskListFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }
}
