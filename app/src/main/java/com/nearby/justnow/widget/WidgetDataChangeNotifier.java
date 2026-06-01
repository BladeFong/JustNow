package com.nearby.justnow.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.nearby.justnow.data.observer.DataChangeNotifier;

/**
 * Widget 侧数据变更通知实现。
 */
public class WidgetDataChangeNotifier implements DataChangeNotifier {

    private static final long UPDATE_DELAY_MS = 300;

    private final Context mAppContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateRunnable;

    public WidgetDataChangeNotifier(Context context) {
        mAppContext = context.getApplicationContext();
        mUpdateRunnable = () -> WidgetUpdateHelper.updateAllWidgets(mAppContext);
    }

    @Override
    public void notifyTaskDataChanged() {
        mMainHandler.removeCallbacks(mUpdateRunnable);
        mMainHandler.postDelayed(mUpdateRunnable, UPDATE_DELAY_MS);
    }
}
