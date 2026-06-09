package com.nearby.justnow.data.holiday;

import android.content.Context;

import com.nearby.justnow.util.RegionSettings;

import java.util.Collections;
import java.util.List;

/**
 * 工厂方法：根据设备地区选择对应的 HolidayDataSource。
 * CN 地区返回主源 + 备用源列表，HK/MO 返回单源。
 */
public final class HolidaySourceFactory {

    private HolidaySourceFactory() {}

    /**
     * 按设备地区返回数据源列表（按优先级排列），不匹配任何已知地区时返回空列表。
     */
    public static List<HolidayDataSource> createSourcesForRegion(Context context) {
        String country = RegionSettings.getDeviceRegionCode(context);
        if ("CN".equals(country)) return List.of(new ChinaGovSource(), new AppleCalendarSource());
        if ("HK".equals(country)) return List.of(new HongKongGovSource());
        if ("MO".equals(country)) return List.of(new MacauGovSource());
        return Collections.emptyList();
    }
}
