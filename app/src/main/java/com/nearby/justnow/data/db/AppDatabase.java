package com.nearby.justnow.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.nearby.justnow.data.dao.HolidayCacheDao;
import com.nearby.justnow.data.dao.TagDao;
import com.nearby.justnow.data.dao.TaskAppActionDao;
import com.nearby.justnow.data.dao.TaskChecklistItemDao;
import com.nearby.justnow.data.dao.TaskDao;
import com.nearby.justnow.data.dao.TaskExecutionDao;
import com.nearby.justnow.data.dao.TaskQuadrantDegradeDao;
import com.nearby.justnow.data.dao.TaskScheduleDao;
import com.nearby.justnow.data.dao.TaskSchedulePostponeDao;
import com.nearby.justnow.data.dao.TimePeriodDao;
import androidx.annotation.NonNull;
import androidx.room.RoomDatabase.Callback;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TaskSchedulePostponeEntity;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用主数据库 — Room
 */
@Database(
    entities = {
        TaskEntity.class,
        TagEntity.class,
        TimePeriodEntity.class,
        TimePeriodGroupEntity.class,
        PriorityTagRuleEntity.class,
        TaskExecutionEntity.class,
        TaskScheduleEntity.class,
        TaskSchedulePostponeEntity.class,
        HolidayCacheEntity.class,
        TaskChecklistItem.class,
        TaskAppAction.class,
        TaskQuadrantDegradeEntity.class
    },
    version = 1,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase sInstance;

    /** 数据库写操作线程池 */
    private static final ExecutorService sDatabaseWriteExecutor =
        Executors.newFixedThreadPool(2);

    /** 静态入口：提交后台写操作（供 BroadcastReceiver / Widget 等无实例场景使用） */
    public static void execute(Runnable r) {
        sDatabaseWriteExecutor.execute(r);
    }

    /** 实例便利方法：提交后台写操作 */
    public void runInBackground(Runnable r) {
        sDatabaseWriteExecutor.execute(r);
    }

    public abstract TaskDao taskDao();
    public abstract TagDao tagDao();
    public abstract TimePeriodDao timePeriodDao();
    public abstract TaskExecutionDao taskExecutionDao();
    public abstract TaskScheduleDao taskScheduleDao();
    public abstract TaskSchedulePostponeDao taskSchedulePostponeDao();
    public abstract HolidayCacheDao holidayCacheDao();
    public abstract TaskChecklistItemDao taskChecklistItemDao();
    public abstract TaskAppActionDao taskAppActionDao();
    public abstract TaskQuadrantDegradeDao taskQuadrantDegradeDao();

    /** 创建内存数据库，仅供测试使用。 */
    public static AppDatabase createInMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(context.getApplicationContext(), AppDatabase.class)
            .allowMainThreadQueries()
            .build();
    }

    public static AppDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AppDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "justnow.db"
                    ).addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            super.onCreate(db);
                            sDatabaseWriteExecutor.execute(() -> {
                                TimePeriodDao dao = sInstance.timePeriodDao();
                                if (dao.count() == 0) {
                                    dao.insertGroups(createDefaultGroups());
                                    dao.insertPeriods(createDefaultPeriods());
                                }
                            });
                        }
                    })
                    .build();
                }
            }
        }
        return sInstance;
    }

    private static List<TimePeriodGroupEntity> createDefaultGroups() {
        return Arrays.asList(
            createGroup(PeriodGroupType.REGULAR, true, false, null, null),
            createGroup(PeriodGroupType.WORKDAY, false, false, null, null),
            createGroup(PeriodGroupType.SPRING_FESTIVAL, false, true, null, null),
            createGroup(PeriodGroupType.LONG_VACATION, false, false, null, null),
            createGroup(PeriodGroupType.SUMMER_VACATION, false, false, "07-01", "08-31"),
            createGroup(PeriodGroupType.WINTER_VACATION, false, false, "01-15", "02-20")
        );
    }

    private static TimePeriodGroupEntity createGroup(String groupType, boolean enabled,
                                                      boolean useHolidayData,
                                                      String startMonthDay, String endMonthDay) {
        TimePeriodGroupEntity group = new TimePeriodGroupEntity();
        group.groupType = groupType;
        group.displayOrder = PeriodGroupType.getDisplayOrder(groupType);
        group.enabled = enabled;
        group.useHolidayData = useHolidayData;
        group.startMonthDay = startMonthDay;
        group.endMonthDay = endMonthDay;
        group.lastEditedAt = 0;
        group.lastReviewedKey = null;
        return group;
    }

    private static List<TimePeriodEntity> createDefaultPeriods() {
        List<TimePeriodEntity> periods = new ArrayList<>();
        periods.addAll(createRegularDefaultPeriods());
        periods.addAll(createWorkdayDefaultPeriods());
        return periods;
    }

    private static List<TimePeriodEntity> createRegularDefaultPeriods() {
        return Arrays.asList(
            createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.MORNING, 0, 9, 0, 11, 30, false, false, true),
            createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.NOON, 1, 11, 30, 13, 30, false, true, false),
            createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.AFTERNOON, 2, 13, 30, 17, 30, false, false, true),
            createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.DINNER, 3, 17, 30, 19, 0, false, true, false),
            createPeriod(PeriodGroupType.REGULAR, PeriodNameKey.EVENING, 4, 19, 0, 22, 0, true, false, false)
        );
    }

    private static List<TimePeriodEntity> createWorkdayDefaultPeriods() {
        return Arrays.asList(
            createPeriod(PeriodGroupType.WORKDAY, PeriodNameKey.MORNING, 0, 8, 30, 11, 30, false, false, true),
            createPeriod(PeriodGroupType.WORKDAY, PeriodNameKey.NOON, 1, 11, 30, 14, 0, false, true, false),
            createPeriod(PeriodGroupType.WORKDAY, PeriodNameKey.AFTERNOON, 2, 14, 0, 18, 0, false, false, true),
            createPeriod(PeriodGroupType.WORKDAY, PeriodNameKey.DINNER, 3, 18, 0, 20, 0, false, true, false),
            createPeriod(PeriodGroupType.WORKDAY, PeriodNameKey.EVENING, 4, 20, 0, 22, 0, true, false, false)
        );
    }

    /** 快捷创建时间段实体。 */
    private static TimePeriodEntity createPeriod(String groupType, String nameKey, int sortOrder,
                                                  int startH, int startM, int endH, int endM,
                                                  boolean reverseQuadrant, boolean preferChore,
                                                  boolean priorityEligible) {
        TimePeriodEntity p = new TimePeriodEntity();
        p.groupType = groupType;
        p.nameKey = nameKey;
        p.sortOrder = sortOrder;
        p.startMinute = startH * 60 + startM;
        p.endMinute = endH * 60 + endM;
        p.reverseQuadrant = reverseQuadrant;
        p.preferChore = preferChore;
        p.priorityEligible = priorityEligible;
        return p;
    }
}
