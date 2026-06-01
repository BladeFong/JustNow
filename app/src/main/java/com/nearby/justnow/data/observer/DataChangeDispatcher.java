package com.nearby.justnow.data.observer;

/**
 * 数据变更统一调度入口。
 */
public final class DataChangeDispatcher {

    private static volatile DataChangeNotifier sNotifier;

    private DataChangeDispatcher() {}

    public static void setNotifier(DataChangeNotifier notifier) {
        sNotifier = notifier;
    }

    public static void notifyTaskDataChanged() {
        DataChangeNotifier notifier = sNotifier;
        if (notifier != null) {
            notifier.notifyTaskDataChanged();
        }
    }
}
