package com.nearby.justnow.widget;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Widget 配置页与主界面权限流程的进程内结果桥。
 */
public final class WidgetConfigureResultBridge {

    public interface Callback {
        void onWidgetConfigurePermissionResult(boolean granted);
    }

    private static final AtomicLong sNextRequestId = new AtomicLong(1);
    private static final Map<Long, Callback> sCallbacks = new HashMap<>();

    private WidgetConfigureResultBridge() {}

    public static long register(Callback callback) {
        long requestId = sNextRequestId.getAndIncrement();
        synchronized (sCallbacks) {
            sCallbacks.put(requestId, callback);
        }
        return requestId;
    }

    public static void unregister(long requestId) {
        synchronized (sCallbacks) {
            sCallbacks.remove(requestId);
        }
    }

    public static void dispatch(long requestId, boolean granted) {
        Callback callback;
        synchronized (sCallbacks) {
            callback = sCallbacks.remove(requestId);
        }
        if (callback != null) {
            callback.onWidgetConfigurePermissionResult(granted);
        }
    }
}
