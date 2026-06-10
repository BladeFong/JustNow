package com.nearby.justnow.ui.engine;

import android.content.Context;

/**
 * 从 Android 字符串资源读取展示策略错误文案。
 */
class AndroidDisplayPolicyMessageProvider implements DisplayPolicyMessageProvider {

    private final Context mContext;

    AndroidDisplayPolicyMessageProvider(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public String get(int messageResId, Object... args) {
        return mContext.getString(messageResId, args);
    }

    @Override
    public String getFocusDurationText(int focusMinutes) {
        return FocusDurationOptions.format(mContext.getResources(), focusMinutes);
    }
}
