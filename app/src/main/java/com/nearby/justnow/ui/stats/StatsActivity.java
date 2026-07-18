package com.nearby.justnow.ui.stats;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nearby.justnow.R;
import com.nearby.justnow.databinding.ActivityStatsBinding;

/**
 * 数据统计 Activity
 */
public class StatsActivity extends AppCompatActivity {

    private ActivityStatsBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(com.nearby.justnow.ui.main.MainFragment.resolveThemeStyle(this));
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityStatsBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBarLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.menu_stats);
        }
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());
    }
}
