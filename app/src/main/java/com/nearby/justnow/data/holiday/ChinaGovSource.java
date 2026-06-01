package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import okhttp3.OkHttpClient;

/**
 * 中国大陆节假日数据源 — 从 NateScarlet/holiday-cn 获取 JSON。
 */
public class ChinaGovSource extends HolidayDataSource {

    private static final String BASE_URL = "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/";
    private static final String SOURCE_NAME = "holiday-cn";

    public ChinaGovSource() {
        super();
    }

    public ChinaGovSource(OkHttpClient client) {
        super(client);
    }

    @Override
    protected String getUrl(int year) {
        return BASE_URL + year + ".json";
    }

    @Override
    protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
        HolidayJsonParser.fill(entity, content, year, SOURCE_NAME);
    }
}
