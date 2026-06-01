package com.nearby.justnow.data.model;

import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间段组及其组内时段。
 */
public class PeriodGroupWithPeriods {

    public final TimePeriodGroupEntity group;
    public final List<TimePeriodEntity> periods;

    public PeriodGroupWithPeriods(TimePeriodGroupEntity group, List<TimePeriodEntity> periods) {
        this.group = group;
        this.periods = periods != null ? periods : new ArrayList<>();
    }
}
