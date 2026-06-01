package com.nearby.justnow.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nearby.justnow.ui.main.MainActivity;
import com.nearby.justnow.util.PermissionHelper;

/**
 * Widget 添加中转页。无业务配置；精确闹钟授权失败时让 Launcher 取消添加。
 */
public class WidgetPermissionGateActivity extends AppCompatActivity
    implements WidgetConfigureResultBridge.Callback {

    private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private long mConfigureRequestId = 0;
    private boolean mHasStartedPermissionFlow = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        readAppWidgetId();
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        if (PermissionHelper.hasExactAlarmPermission(this)) {
            completeWidgetConfiguration();
            return;
        }
        launchMainPermissionFlow();
    }

    @Override
    protected void onDestroy() {
        if (mConfigureRequestId > 0) {
            WidgetConfigureResultBridge.unregister(mConfigureRequestId);
        }
        super.onDestroy();
    }

    @Override
    public void onWidgetConfigurePermissionResult(boolean granted) {
        if (granted && PermissionHelper.hasExactAlarmPermission(this)) {
            completeWidgetConfiguration();
        } else {
            finish();
        }
    }

    private void readAppWidgetId() {
        Intent intent = getIntent();
        if (intent == null || intent.getExtras() == null) return;
        mAppWidgetId = intent.getExtras().getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    private void completeWidgetConfiguration() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        WidgetUpdateHelper.updateWidget(this, manager, mAppWidgetId,
            manager.getAppWidgetOptions(mAppWidgetId));
        WidgetUpdateHelper.scheduleNextMinuteBoundary(this);

        Intent resultIntent = new Intent();
        resultIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void launchMainPermissionFlow() {
        if (mHasStartedPermissionFlow) return;
        mHasStartedPermissionFlow = true;
        mConfigureRequestId = WidgetConfigureResultBridge.register(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(MainActivity.ACTION_WIDGET_CONFIGURE_EXACT_ALARM_PERMISSION);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        intent.putExtra(MainActivity.EXTRA_WIDGET_CONFIGURE_REQUEST_ID, mConfigureRequestId);
        startActivity(intent);
    }
}
