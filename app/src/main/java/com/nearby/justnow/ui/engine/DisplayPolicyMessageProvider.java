package com.nearby.justnow.ui.engine;

/**
 * 展示策略错误文案提供器。
 */
interface DisplayPolicyMessageProvider {
    String get(int messageResId, Object... args);

    default String getFocusDurationText(int focusMinutes) {
        return String.valueOf(focusMinutes);
    }
}
