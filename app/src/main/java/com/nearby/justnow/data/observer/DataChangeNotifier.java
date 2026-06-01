package com.nearby.justnow.data.observer;

/**
 * 数据变更通知接口。数据层只表达任务数据已变化，不感知具体刷新目标。
 */
public interface DataChangeNotifier {
    void notifyTaskDataChanged();
}
