package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import okhttp3.OkHttpClient;

/**
 * 澳门公众假期数据源 — 澳门政府订阅（繁体中文 ICS）。
 */
public class MacauGovSource extends HolidayDataSource {

    private static final String URL = "https://www.gov.mo/zh-hant/public-holidays/ical/";
    private static final String SOURCE_NAME = "gov-mo";

    public MacauGovSource() {
        super();
    }

    public MacauGovSource(OkHttpClient client) {
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
