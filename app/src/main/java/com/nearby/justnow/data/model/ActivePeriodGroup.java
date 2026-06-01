package com.nearby.justnow.data.model;

import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前日期命中的时间段组。
 */
public class ActivePeriodGroup {

    public final TimePeriodGroupEntity group;
    public final List<TimePeriodEntity> periods;

    public ActivePeriodGroup(TimePeriodGroupEntity group, List<TimePeriodEntity> periods) {
        this.group = group;
        this.periods = periods != null ? periods : new ArrayList<>();
    }

    public String getGroupType() {
        return group != null ? group.groupType : PeriodGroupType.REGULAR;
    }
}
