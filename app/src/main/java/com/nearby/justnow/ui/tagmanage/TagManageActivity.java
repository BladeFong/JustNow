package com.nearby.justnow.ui.tagmanage;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.nearby.justnow.R;
import com.nearby.justnow.databinding.ActivityTagManageBinding;

/**
 * 标签管理 Activity — 宿主标签管理→未使用标签
 */
public class TagManageActivity extends AppCompatActivity {

    private ActivityTagManageBinding mBinding;
    private NavController mNavController;
    private AppBarConfiguration mAppBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityTagManageBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBarLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        setSupportActionBar(mBinding.toolbar);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            mNavController = navHost.getNavController();
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
