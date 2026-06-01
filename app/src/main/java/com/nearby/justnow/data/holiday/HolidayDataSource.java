package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 节假日数据源抽象类 — 提取 HTTP 请求通用逻辑。
 */
public abstract class HolidayDataSource {

    protected final OkHttpClient mClient;

    protected HolidayDataSource() {
        mClient = HolidayHttpClient.get();
    }

    protected HolidayDataSource(OkHttpClient client) {
        mClient = client;
    }

    /**
     * 获取指定年份的 URL
     */
    protected abstract String getUrl(int year);

    /**
     * 解析并填充数据（子类实现各自解析逻辑）
     */
    protected abstract void parseAndFill(HolidayCacheEntity entity, String content, int year);

    /**
     * 获取指定年份的节假日数据（统一 HTTP 请求逻辑）
     */
    public final HolidayCacheEntity fetch(int year) throws IOException {
        String url = getUrl(year);
        Request request = new Request.Builder().url(url).build();
        try (Response response = mClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return HolidayCacheManager.emptyEntity(year);
            }
            String content = response.body().string();
            HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(year);
            parseAndFill(entity, content, year);
            return entity;
        }
    }
}
