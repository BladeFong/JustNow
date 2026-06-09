package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import okhttp3.OkHttpClient;

/**
 * 中国大陆节假日备用数据源 — Apple Calendar 公开订阅（ICS 格式）。
 * 单 URL 返回多年数据，IcsParser 按 year 过滤。
 */
public class AppleCalendarSource extends HolidayDataSource {

    private static final String URL = "https://calendars.icloud.com/holidays/cn_zh.ics";
    private static final String SOURCE_NAME = "apple-calendar";

    public AppleCalendarSource() {
        super();
    }

    public AppleCalendarSource(OkHttpClient client) {
        super(client);
    }

    @Override
    protected String getUrl(int year) {
        return URL;
    }

    @Override
    protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
        IcsParser.fillAppleCalendar(entity, content, year, SOURCE_NAME);
    }
}
