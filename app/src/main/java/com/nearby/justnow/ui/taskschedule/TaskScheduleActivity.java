package com.nearby.justnow.ui.taskschedule;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.fragment.NavHostFragment;

import com.nearby.justnow.R;
import com.nearby.justnow.databinding.ActivityTaskScheduleBinding;

/**
 * 任务安排 Activity
 */
public class TaskScheduleActivity extends AppCompatActivity {

    private ActivityTaskScheduleBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityTaskScheduleBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBarLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.s_schedule_task_title);
        }
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            long taskId = getIntent().getLongExtra("task_id", -1);
            Bundle args = new Bundle();
            args.putLong("task_id", taskId);
            navHost.getNavController().setGraph(R.navigation.nav_task_schedule, args);
        }
    }
}
