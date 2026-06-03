package com.nearby.justnow.data.holiday;

import android.content.Context;

import com.nearby.justnow.util.RegionSettings;

/**
 * 工厂方法：根据设备地区选择对应的 HolidayDataSource。
 * 优先级：CN（中国大陆） > HK（香港） > MO（澳门）。
 */
public final class HolidaySourceFactory {

    private HolidaySourceFactory() {}

    /**
     * 按设备地区返回数据源，不匹配任何已知地区时返回 null。
     */
    public static HolidayDataSource createForRegion(Context context) {
        String country = RegionSettings.getDeviceRegionCode(context);
        if ("CN".equals(country)) return new ChinaGovSource();
        if ("HK".equals(country)) return new HongKongGovSource();
        if ("MO".equals(country)) return new MacauGovSource();
        return null;
    }
}
