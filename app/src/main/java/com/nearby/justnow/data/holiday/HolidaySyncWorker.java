package com.nearby.justnow.data.holiday;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.HolidayCacheEntity;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * WorkManager Worker — 后台获取节假日数据并写入缓存。
 * 根据设备地区选择对应数据源。
 */
public class HolidaySyncWorker extends Worker {

    private static final String DATA_KEY_YEAR = "year";

    public HolidaySyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static OneTimeWorkRequest createRequest(int year) {
        Data inputData = new Data.Builder()
            .putInt(DATA_KEY_YEAR, year)
            .build();

        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        return new OneTimeWorkRequest.Builder(HolidaySyncWorker.class)
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        int year = getInputData().getInt(DATA_KEY_YEAR, -1);
        if (year <= 0) return Result.failure();

        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        HolidayCacheManager cacheManager = new HolidayCacheManager(db.holidayCacheDao());

        if (!cacheManager.shouldSyncThisMonth(year)) return Result.success();

        HolidayDataSource source = HolidaySourceFactory.createForRegion(getApplicationContext());
        if (source == null) return Result.failure();

        try {
            HolidayCacheEntity entity = source.fetch(year);
            cacheManager.save(entity);
            return Result.success();
        } catch (IOException e) {
            Log.w("HolidaySyncWorker", "Fetch failed", e);
            // DNS 失败、连接超时为瞬时错误，可重试；其他为永久失败
            if (e instanceof UnknownHostException || e instanceof SocketTimeoutException) {
                return Result.retry();
            }
            return Result.failure();
        }
    }
}
