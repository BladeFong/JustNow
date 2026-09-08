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
import java.util.List;
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

        List<HolidayDataSource> sources = HolidaySourceFactory.createSourcesForRegion(getApplicationContext());
        if (sources.isEmpty()) return Result.failure();

        IOException lastError = null;
        for (HolidayDataSource source : sources) {
            try {
                HolidayCacheEntity entity = source.fetch(year);
                Context appContext = getApplicationContext();
                com.nearby.justnow.data.store.UserStore userStore =
                    new com.nearby.justnow.data.store.UserStore(appContext);
                com.nearby.justnow.data.migration.DataMigrationManager.dispatchHolidayUpdate(
                    appContext, entity, userStore);
                return Result.success();
            } catch (IOException e) {
                Log.w("HolidaySyncWorker", "Fetch failed for " + source.getClass().getSimpleName(), e);
                lastError = e;
            }
        }

        // 所有源均失败
        if (lastError instanceof UnknownHostException || lastError instanceof SocketTimeoutException) {
            return Result.retry();
        }
        return Result.failure();
    }
}
