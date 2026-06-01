package com.nearby.justnow.ui.engine;

import android.content.res.Resources;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TimePeriodEntity;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * TimeRemainingCalculator 单元测试
 */
public class TimeRemainingCalculatorTest {

    private final TimeRemainingCalculator mCalc = new TimeRemainingCalculator();
    private Resources mMockRes;

    @Before
    public void setUp() {
        mMockRes = Mockito.mock(Resources.class);
        when(mMockRes.getString(R.string.s_hour_unit)).thenReturn("小时");
        when(mMockRes.getString(R.string.s_minute_unit)).thenReturn("分钟");
        when(mMockRes.getString(R.string.s_remaining_format)).thenReturn("%s");
    }

    /** 创建测试时段 */
    private TimePeriodEntity createPeriod(int sortOrder, int startMinute, int endMinute) {
        TimePeriodEntity p = new TimePeriodEntity();
        p.groupType = "regular";
        p.nameKey = "test";
        p.sortOrder = sortOrder;
        p.startMinute = startMinute;
        p.endMinute = endMinute;
        return p;
    }

    /** 创建指定时间的 Calendar */
    private Calendar cal(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    @Test
    public void compute_inMorningPeriod_returnsCorrectPeriod() {
        // 早上 08:30-11:30，测试 09:00
        List<TimePeriodEntity> periods = Arrays.asList(
            createPeriod(0, 8 * 60 + 30, 11 * 60 + 30)
        );
        TimeRemainingCalculator.PeriodStatus status = mCalc.compute(periods, cal(9, 0));

        assertTrue(status.isInPeriod());
        assertEquals(0, status.period.sortOrder);
        assertEquals(11 * 60 + 30, status.endMinute);
        assertEquals(150, status.remainingMinutes); // 2.5小时 = 150分钟
    }

    @Test
    public void compute_outsideAnyPeriod_returnsMinusOne() {
        List<TimePeriodEntity> periods = Arrays.asList(
            createPeriod(0, 8 * 60 + 30, 11 * 60 + 30)
        );
        TimeRemainingCalculator.PeriodStatus status = mCalc.compute(periods, cal(7, 0));

        assertFalse(status.isInPeriod());
        assertNull(status.period);
    }

    @Test
    public void compute_exactStartBoundary_isInPeriod() {
        List<TimePeriodEntity> periods = Arrays.asList(
            createPeriod(0, 8 * 60 + 30, 11 * 60 + 30)
        );
        // 恰好 08:30 应在时段内
        TimeRemainingCalculator.PeriodStatus status = mCalc.compute(periods, cal(8, 30));

        assertTrue(status.isInPeriod());
        assertEquals(180, status.remainingMinutes); // 3小时
    }

    @Test
    public void compute_exactEndBoundary_isNotInPeriod() {
        List<TimePeriodEntity> periods = Arrays.asList(
            createPeriod(0, 8 * 60 + 30, 11 * 60 + 30)
        );
        // 恰好 11:30 不在时段内（endMinute 是排他的）
        TimeRemainingCalculator.PeriodStatus status = mCalc.compute(periods, cal(11, 30));

        assertFalse(status.isInPeriod());
    }

    @Test
    public void getRemainingText_lessThanHour() {
        TimeRemainingCalculator.PeriodStatus status = new TimeRemainingCalculator.PeriodStatus();
        status.remainingMinutes = 45;
        assertEquals("45分钟", status.getRemainingText(mMockRes));
    }

    @Test
    public void getRemainingText_exactlyOneHour() {
        TimeRemainingCalculator.PeriodStatus status = new TimeRemainingCalculator.PeriodStatus();
        status.remainingMinutes = 60;
        assertEquals("1小时", status.getRemainingText(mMockRes));
    }

    @Test
    public void getRemainingText_hoursAndMinutes() {
        TimeRemainingCalculator.PeriodStatus status = new TimeRemainingCalculator.PeriodStatus();
        status.remainingMinutes = 90;
        assertEquals("1小时30分钟", status.getRemainingText(mMockRes));
    }

    @Test
    public void compute_emptyPeriodsList_returnsMinusOne() {
        TimeRemainingCalculator.PeriodStatus status = mCalc.compute(new ArrayList<>(), cal(12, 0));
        assertFalse(status.isInPeriod());
    }
}
