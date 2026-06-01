package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import okhttp3.OkHttpClient;

/**
 * 香港公众假期数据源 — 香港政府 1823 订阅（繁体中文 ICS）。
 */
public class HongKongGovSource extends HolidayDataSource {

    private static final String URL = "https://www.1823.gov.hk/common/ical/tc.ics";
    private static final String SOURCE_NAME = "gov-hk";

    public HongKongGovSource() {
        super();
    }

    public HongKongGovSource(OkHttpClient client) {
        super(client);
    }

    @Override
    protected String getUrl(int year) {
        return URL;
    }

    @Override
    protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
        IcsParser.fill(entity, content, year, SOURCE_NAME);
    }
}
