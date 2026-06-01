package com.nearby.justnow.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

/**
 * 设备国家/地区读取入口。节假日模块接入后继续复用这里的地区口径。
 */
public final class RegionSettings {

    public static final String REGION_MAINLAND_CHINA = "CN";

    private RegionSettings() {
    }

    public static boolean isMainlandChina(Context context) {
        return REGION_MAINLAND_CHINA.equals(getDeviceRegionCode(context));
    }

    /**
     * 当前地区是否有节假日数据源。
     * CN（大陆 holiday-cn）、HK（香港 ICS）、MO（澳门 ICS）有数据源，其余地区暂无。
     */
    public static boolean isHolidayDataAvailable(Context context) {
        String country = getDeviceRegionCode(context);
        return REGION_MAINLAND_CHINA.equals(country)
            || "HK".equals(country)
            || "MO".equals(country);
    }

    public static String getDeviceRegionCode(Context context) {
        Locale locale = getPrimaryLocale(context);
        return locale.getCountry().toUpperCase(Locale.US);
    }

    private static Locale getPrimaryLocale(Context context) {
        if (context != null) {
            Configuration configuration = context.getResources().getConfiguration();
            LocaleList locales = configuration.getLocales();
            if (locales != null && !locales.isEmpty()) {
                return locales.get(0);
            }
        }
        return Locale.getDefault();
    }
}
