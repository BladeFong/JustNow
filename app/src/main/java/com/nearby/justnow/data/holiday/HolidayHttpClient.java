package com.nearby.justnow.data.holiday;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * OkHttpClient 全局共享单例，避免各 DataSource 各自创建实例浪费连接/线程池。
 */
public final class HolidayHttpClient {

    private static final OkHttpClient sInstance = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private HolidayHttpClient() {}

    public static OkHttpClient get() {
        return sInstance;
    }
}
