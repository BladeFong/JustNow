package com.nearby.justnow.ui.engine;

import java.util.Calendar;

/**
 * 工作日判断接口 — 抽象日期类型判断，便于后续接入节假日数据
 */
public interface IWorkdayChecker {
    boolean isWorkday(Calendar date);
}
