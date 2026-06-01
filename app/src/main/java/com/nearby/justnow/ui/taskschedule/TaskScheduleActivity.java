package com.nearby.justnow.ui.taskschedule;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.nearby.justnow.R;
import com.nearby.justnow.databinding.ActivityTaskScheduleBinding;

/**
 * 任务安排 Activity
 */
public class TaskScheduleActivity extends AppCompatActivity {

    private ActivityTaskScheduleBinding mBinding;
    private NavController mNavController;
    private AppBarConfiguration mAppBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityTaskScheduleBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        setSupportActionBar(mBinding.toolbar);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            mNavController = navHost.getNavController();
            long taskId = getIntent().getLongExtra("task_id", -1);
            Bundle args = new Bundle();
            args.putLong("task_id", taskId);
            mNavController.setGraph(R.navigation.nav_task_schedule, args);
            mAppBarConfiguration = new AppBarConfiguration.Builder().build();
            NavigationUI.setupWithNavController(mBinding.toolbar, mNavController, mAppBarConfiguration);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (NavigationUI.navigateUp(mNavController, mAppBarConfiguration)) {
            return true;
        }
        finish();
        return true;
    }
}
