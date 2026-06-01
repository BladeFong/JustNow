package com.nearby.justnow.ui.main;

/**
 * 任务直接执行请求结果。
 */
public class TaskStartResult {

    public static final int OK = 0;
    public static final int BLOCKED_RUNNING = 1;
    public static final int BLOCKED_OUT_OF_PERIOD = 2;
    public static final int BLOCKED_TIME_NOT_ENOUGH = 3;
    public static final int BLOCKED_TASK_MISSING = 4;

    public final int code;

    public TaskStartResult(int code) {
        this.code = code;
    }
}
