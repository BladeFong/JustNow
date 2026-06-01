package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.dao.HolidayCacheDao;
import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.db.AppDatabase;

import java.time.YearMonth;
import java.util.function.Consumer;

/**
 * 节假日缓存管理器 — 封装 holiday_cache 表的读写。
 */
public class HolidayCacheManager {

    private final HolidayCacheDao mDao;

    public HolidayCacheManager(HolidayCacheDao dao) {
        mDao = dao;
    }

    /**
     * 异步获取某年的缓存 JSON，回调在主线程执行。
     */
    public void get(int year, Consumer<String> callback) {
        AppDatabase.execute(() -> {
            HolidayCacheEntity entity = mDao.getByYearSync(year);
            String json = entity != null ? entity.dataJson : null;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.accept(json));
        });
    }

    /**
     * 同步获取某年的缓存 JSON（仅供后台线程使用）。
     */
    public String getSync(int year) {
        HolidayCacheEntity entity = mDao.getByYearSync(year);
        return entity != null ? entity.dataJson : null;
    }

    /** 当月是否需要重新下载。lastSyncMonth < 当月 → true。 */
    public boolean shouldSyncThisMonth(int year) {
        Integer lastSyncMonth = mDao.getLastSyncMonthByYear(year);
        if (lastSyncMonth == null) return true;
        return lastSyncMonth < currentMonth();
    }

    /**
     * 保存节假日缓存。仅查询 holiday_count (int) 列做比较，不加载 data_json。
     * 月份标记总是更新；JSON 数据仅在新数据假日天数更多时才写入。
     * 判断与写入在 DAO 层 @Transaction 中原子执行，避免竞态。
     */
    public void save(HolidayCacheEntity entity) {
        if (entity == null) return;
        int month = currentMonth();
        AppDatabase.execute(() -> {
            mDao.upsertWithCountCheck(entity, month);
        });
    }

    /** 网络可达但无数据时返回的空 Entity（year 已设置，holidayCount=0）。 */
    public static HolidayCacheEntity emptyEntity(int year) {
        HolidayCacheEntity e = new HolidayCacheEntity();
        e.year = year;
        e.holidayCount = 0;
        return e;
    }

    private static int currentMonth() {
        YearMonth now = YearMonth.now();
        return now.getYear() * 100 + now.getMonthValue();
    }
}
