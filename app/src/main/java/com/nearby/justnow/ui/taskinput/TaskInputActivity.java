package com.nearby.justnow.ui.taskinput;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavDestination;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.nearby.justnow.R;
import com.nearby.justnow.databinding.ActivityTaskInputBinding;

/**
 * 任务录入 Activity — 宿主输入→编辑→象限三步向导
 */
public class TaskInputActivity extends AppCompatActivity {

    private ActivityTaskInputBinding mBinding;
    private NavController mNavController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityTaskInputBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            mBinding.appBarLayout.setPadding(
                mBinding.appBarLayout.getPaddingLeft(), top,
                mBinding.appBarLayout.getPaddingRight(),
                mBinding.appBarLayout.getPaddingBottom());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                v.getPaddingRight(), imeBottom);
            return insets;
        });

        setSupportActionBar(mBinding.toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.s_add_task);
        }

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            mNavController = navHost.getNavController();
            mNavController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> updateTitle(destination));
        }

        mBinding.toolbar.setNavigationOnClickListener(v -> navigateBackOrFinish());
    }

    private void updateTitle(NavDestination destination) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;
        CharSequence label = destination.getLabel();
        actionBar.setTitle(label != null ? label : getString(R.string.s_add_task));
    }

    private boolean navigateBackOrFinish() {
        if (mNavController != null && mNavController.getCurrentDestination() != null
            && mNavController.getCurrentDestination().getId()
            != mNavController.getGraph().getStartDestinationId()
            && mNavController.popBackStack()) {
            return true;
        }
        finish();
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navigateBackOrFinish();
    }
}
