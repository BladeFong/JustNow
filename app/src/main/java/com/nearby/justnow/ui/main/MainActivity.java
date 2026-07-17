package com.nearby.justnow.ui.main;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.nearby.justnow.R;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.broadcast.ReminderNotifier;
import com.nearby.justnow.databinding.ActivityMainBinding;
import com.nearby.justnow.ui.reminderdetail.ReminderDetailActivity;
import com.nearby.justnow.widget.WidgetConfigureResultBridge;

/**
 * 主 Activity — 宿主 Navigation
 */
public class MainActivity extends AppCompatActivity {

    public static final String ACTION_WIDGET_TASK_CLICK =
        "com.nearby.justnow.ACTION_WIDGET_TASK_CLICK";
    public static final String ACTION_WIDGET_CONFIGURE_EXACT_ALARM_PERMISSION =
        "com.nearby.justnow.ACTION_WIDGET_CONFIGURE_EXACT_ALARM_PERMISSION";
    public static final String EXTRA_WIDGET_CONFIGURE_REQUEST_ID =
        "widget_configure_request_id";

    private static final int[] sQuadrantColorKeys = {
        R.color.quadrant_urgent_important,
        R.color.quadrant_urgent_not_important,
        R.color.quadrant_not_urgent_important,
        R.color.quadrant_not_urgent_not_important,
    };

    private ActivityMainBinding mBinding;
    private NavController mNavController;
    private AppBarConfiguration mAppBarConfiguration;
    private long mPendingWidgetTaskId = -1;
    private long mWidgetConfigureRequestId = 0;
    private boolean mWidgetConfigureExactAlarmFlowActive = false;
    private boolean mPendingWidgetConfigureExactAlarmPrompt = false;
    private int mDefaultAppBarColor;
    private int mDefaultStatusBarColor;
    private boolean mDefaultLightStatusBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.appBarLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(mBinding.navHostFragment, (v, insets) -> {
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, 0, 0, navBarHeight);
            return insets;
        });

        mDefaultAppBarColor = ContextCompat.getColor(this, R.color.purple_500);
        mDefaultStatusBarColor = resolveColorAttr(android.R.attr.statusBarColor, mDefaultAppBarColor);
        mDefaultLightStatusBar = resolveBooleanAttr(android.R.attr.windowLightStatusBar, false);

        setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setOverflowIcon(getDrawable(R.drawable.ic_person_28));

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost != null) {
            mNavController = navHost.getNavController();
            mAppBarConfiguration = new AppBarConfiguration.Builder(mNavController.getGraph()).build();
            NavigationUI.setupWithNavController(mBinding.toolbar, mNavController, mAppBarConfiguration);
            mNavController.addOnDestinationChangedListener(
                (controller, destination, arguments) ->
                    applyChromeForDestination(destination, arguments));
        }

        handleReminderIntent(getIntent());
    }

    private void applyChromeForDestination(NavDestination destination, Bundle arguments) {
        if (destination.getId() == R.id.quadrantTaskListFragment) {
            int quadrant = readQuadrantArgument(arguments);
            int quadrantColor = ContextCompat.getColor(this, sQuadrantColorKeys[quadrant]);
            applyChromeColors(quadrantColor, quadrantColor, false);
            return;
        }

        applyChromeColors(mDefaultAppBarColor, mDefaultStatusBarColor, mDefaultLightStatusBar);
    }

    private int readQuadrantArgument(Bundle arguments) {
        int quadrant = arguments != null ? arguments.getInt("quadrant", 0) : 0;
        if (quadrant < 0 || quadrant >= sQuadrantColorKeys.length) {
            return 0;
        }
        return quadrant;
    }

    private void applyChromeColors(int appBarColor, int statusBarColor, boolean lightStatusBar) {
        mBinding.appBarLayout.setBackgroundColor(appBarColor);
        mBinding.toolbar.setBackgroundColor(appBarColor);

        WindowInsetsControllerCompat insetsController =
            WindowCompat.getInsetsController(getWindow(), mBinding.getRoot());
        insetsController.setAppearanceLightStatusBars(lightStatusBar);
    }

    private int resolveColorAttr(int attrResId, int fallback) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attrResId, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return ContextCompat.getColor(this, value.resourceId);
        }
        return value.data;
    }

    private boolean resolveBooleanAttr(int attrResId, boolean fallback) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(attrResId, value, true)) {
            return fallback;
        }
        return value.data != 0;
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleReminderIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((JustNowApplication) getApplication()).getAppLaunchCatalogCache().clear();
    }

    @Override
    protected void onDestroy() {
        if (mWidgetConfigureExactAlarmFlowActive && !isChangingConfigurations()) {
            dispatchWidgetConfigureExactAlarmResult(false);
        }
        super.onDestroy();
    }

    private void handleReminderIntent(android.content.Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (ACTION_WIDGET_CONFIGURE_EXACT_ALARM_PERMISSION.equals(action)) {
            int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
            mWidgetConfigureRequestId = intent.getLongExtra(EXTRA_WIDGET_CONFIGURE_REQUEST_ID, 0);
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                || mWidgetConfigureRequestId <= 0) {
                finish();
                return;
            }
            mWidgetConfigureExactAlarmFlowActive = true;
            mPendingWidgetConfigureExactAlarmPrompt = true;
            dispatchPendingWidgetConfigureExactAlarmPrompt();
            clearWidgetConfigureIntent(intent);
            return;
        }

        long taskId = intent.getLongExtra("task_id", -1);
        if (taskId <= 0) return;

        if (ACTION_WIDGET_TASK_CLICK.equals(action)) {
            mPendingWidgetTaskId = taskId;
            dispatchPendingWidgetTaskClick();
            clearTaskIntent(intent);
            return;
        }

        if (ReminderNotifier.ACTION_START.equals(action) || action == null) {
            Intent detailIntent = new Intent(this, ReminderDetailActivity.class);
            detailIntent.putExtra("task_id", taskId);
            startActivity(detailIntent);
            clearTaskIntent(intent);
        }
    }

    public long consumePendingWidgetTaskId() {
        long taskId = mPendingWidgetTaskId;
        mPendingWidgetTaskId = -1;
        return taskId;
    }

    public boolean consumePendingWidgetConfigureExactAlarmPrompt() {
        if (!mWidgetConfigureExactAlarmFlowActive) return false;
        boolean pending = mPendingWidgetConfigureExactAlarmPrompt;
        mPendingWidgetConfigureExactAlarmPrompt = false;
        return pending;
    }

    public boolean isWidgetConfigureExactAlarmFlowActive() {
        return mWidgetConfigureExactAlarmFlowActive;
    }

    public void finishWidgetConfigureExactAlarmFlow(boolean granted) {
        if (!mWidgetConfigureExactAlarmFlowActive) return;
        dispatchWidgetConfigureExactAlarmResult(granted);
        finish();
    }

    private void dispatchWidgetConfigureExactAlarmResult(boolean granted) {
        mWidgetConfigureExactAlarmFlowActive = false;
        long requestId = mWidgetConfigureRequestId;
        mWidgetConfigureRequestId = 0;
        if (requestId > 0) {
            WidgetConfigureResultBridge.dispatch(requestId, granted);
        }
    }

    private void dispatchPendingWidgetTaskClick() {
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) return;

        androidx.fragment.app.Fragment fragment = navHost.getChildFragmentManager()
            .getPrimaryNavigationFragment();
        if (fragment instanceof MainFragment) {
            ((MainFragment) fragment).consumePendingWidgetTaskClick();
        }
    }

    private void dispatchPendingWidgetConfigureExactAlarmPrompt() {
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
            .findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) return;

        androidx.fragment.app.Fragment fragment = navHost.getChildFragmentManager()
            .getPrimaryNavigationFragment();
        if (fragment instanceof MainFragment) {
            ((MainFragment) fragment).consumePendingWidgetConfigureExactAlarmPrompt();
        }
    }

    private void clearTaskIntent(Intent intent) {
        intent.setAction(null);
        intent.removeExtra("task_id");
        setIntent(intent);
    }

    private void clearWidgetConfigureIntent(Intent intent) {
        intent.setAction(null);
        setIntent(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(mNavController, mAppBarConfiguration)
            || super.onSupportNavigateUp();
    }
}
