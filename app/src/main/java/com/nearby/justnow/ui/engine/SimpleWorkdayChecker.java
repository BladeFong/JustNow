package com.nearby.justnow.ui.engine;

import java.util.Calendar;

/**
 * 简单工作日判断：周一至周五 = 工作日
 */
public class SimpleWorkdayChecker implements IWorkdayChecker {

    @Override
    public boolean isWorkday(Calendar date) {
        int dow = date.get(Calendar.DAY_OF_WEEK);
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY;
    }
}
