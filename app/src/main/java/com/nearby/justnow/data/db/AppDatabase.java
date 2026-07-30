package com.nearby.justnow.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.nearby.justnow.data.dao.HolidayCacheDao;
import com.nearby.justnow.data.dao.TagDao;
import com.nearby.justnow.data.dao.TaskAppActionDao;
import com.nearby.justnow.data.dao.TaskChecklistItemDao;
import com.nearby.justnow.data.dao.TaskCompletionCounterDao;
import com.nearby.justnow.data.dao.TaskDao;
import com.nearby.justnow.data.dao.TaskNoteShareDao;
import com.nearby.justnow.data.dao.TaskExecutionDao;
import com.nearby.justnow.data.dao.TaskScheduleDao;
import com.nearby.justnow.data.dao.TaskSchedulePostponeDao;
import com.nearby.justnow.data.dao.TaskPhotoDao;
import com.nearby.justnow.data.dao.TaskScheduleSkipDao;
import com.nearby.justnow.data.dao.TimePeriodDao;
import androidx.annotation.NonNull;
import androidx.room.RoomDatabase.Callback;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.nearby.justnow.data.entity.HolidayCacheEntity;
import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskAppAction;
import com.nearby.justnow.data.entity.TaskChecklistItem;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.entity.TaskSchedulePostponeEntity;
import com.nearby.justnow.data.entity.TaskPhotoEntity;
import com.nearby.justnow.data.entity.TaskScheduleSkipEntity;
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
        TaskNoteShare.class,
        TaskCompletionCounterEntity.class,
        TaskScheduleSkipEntity.class,
        TaskPhotoEntity.class
    },
    version = 9,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    private static final java.util.Map<Long, AppDatabase> sInstances =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** @deprecated 保留向后兼容，新代码使用 {@link #getInstance(Context, long)} */
    @Deprecated
    private static volatile AppDatabase sInstance;

    /** 数据库写操作线程池 */
    private static final ExecutorService sDatabaseWriteExecutor =
        Executors.newFixedThreadPool(2);

    /** 迁移 6→7：废弃降级策略，新增完成模式。 */
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN completion_mode INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE tasks ADD COLUMN quota INTEGER NOT NULL DEFAULT 1");
            database.execSQL("ALTER TABLE tasks DROP COLUMN degrade_period");
            database.execSQL("CREATE TABLE IF NOT EXISTS task_completion_counter ("
                + "task_id INTEGER NOT NULL, "
                + "period_key TEXT NOT NULL, "
                + "completed INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY(task_id, period_key))");
            database.execSQL("DROP TABLE IF EXISTS task_quadrant_degrade");
        }
    };

    /** 迁移 7→8：新增任务内置儿童兴趣活动图标字段。 */
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN icon_name TEXT DEFAULT NULL");
        }
    };

    /** 迁移 8→9：新增任务时光胶囊关联照片表及索引。 */
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `task_photos` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`task_id` INTEGER NOT NULL, " +
                    "`photo_uri` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_photos_task_id` ON `task_photos` (`task_id`)");
        }
    };

    /** 迁移 5→6：新增笔记分享列表表。 */
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS task_note_shares ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "task_id INTEGER NOT NULL, "
                + "order_index INTEGER NOT NULL, "
                + "deep_link TEXT, "
                + "hint TEXT, "
                + "FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                + "index_task_note_shares_task_id ON task_note_shares(task_id)");
        }
    };

    /** 迁移 4→5：新增安排延迟时间戳字段。 */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_schedules ADD COLUMN postponed_until_ms INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** 迁移 3→4：重构安排跳过记录表为每 schedule 一行。 */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS task_schedule_skips");
            database.execSQL("CREATE TABLE task_schedule_skips ("
                + "schedule_id INTEGER NOT NULL PRIMARY KEY, "
                + "last_skipped_date_ms INTEGER NOT NULL DEFAULT 0, "
                + "skip_count INTEGER NOT NULL DEFAULT 0, "
                + "updated_at INTEGER NOT NULL DEFAULT 0, "
                + "FOREIGN KEY(schedule_id) REFERENCES task_schedules(id) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                + "index_task_schedule_skips_schedule_id ON task_schedule_skips(schedule_id)");
        }
    };

    /** 迁移 2→3：新增安排跳过记录表。 */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS task_schedule_skips ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "schedule_id INTEGER NOT NULL, "
                + "date_ms INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL DEFAULT 0, "
                + "FOREIGN KEY(schedule_id) REFERENCES task_schedules(id) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                + "index_task_schedule_skips_schedule_id ON task_schedule_skips(schedule_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                + "index_task_schedule_skips_date_ms ON task_schedule_skips(date_ms)");
        }
    };

    /** 迁移 1→2：新增时段组关联字段，清理 MONTHLY(type=3) 记录。 */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_schedules ADD COLUMN linked_period_group_type TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE task_schedules ADD COLUMN schedule_sub_type INTEGER NOT NULL DEFAULT 0");
            database.execSQL("DELETE FROM task_schedules WHERE schedule_type = 3");
        }
    };

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
    public abstract TaskNoteShareDao taskNoteShareDao();
    public abstract TaskCompletionCounterDao taskCompletionCounterDao();
    public abstract TaskScheduleSkipDao taskScheduleSkipDao();
    public abstract TaskPhotoDao taskPhotoDao();

    /** 创建内存数据库，仅供测试使用。 */
    public static AppDatabase createInMemory(Context context) {
        return Room.inMemoryDatabaseBuilder(context.getApplicationContext(), AppDatabase.class)
            .allowMainThreadQueries()
            .build();
    }

    /**
     * 按用户 ID 获取数据库实例。每个用户拥有独立的数据库文件。
     *
     * @param ctx    Context
     * @param userId 用户 ID（0 = 默认用户，使用 justnow.db）
     */
    /** 测试用数据库实例，设置后所有 getInstance 调用优先返回此实例。 */
    private static volatile AppDatabase sTestInstance;

    /** 注入测试数据库（Robolectric 用），调用 clearTestInstance() 清除。 */
    public static void setTestInstance(AppDatabase db) {
        sTestInstance = db;
    }

    /** 清除测试数据库注入。 */
    public static void clearTestInstance() {
        sTestInstance = null;
    }

    public static AppDatabase getInstance(Context ctx, long userId) {
        if (sTestInstance != null) return sTestInstance;
        AppDatabase existing = sInstances.get(userId);
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        synchronized (AppDatabase.class) {
            existing = sInstances.get(userId);
            if (existing != null) {
                if (existing.isOpen()) {
                    return existing;
                } else {
                    sInstances.remove(userId);
                }
            }
            String dbName = userId == 0
                ? "justnow.db"
                : "justnow_u" + userId + ".db";
            // 用于在 Callback 中捕获当前实例（避免引用静态字段）
            AppDatabase[] holder = new AppDatabase[1];
            AppDatabase db = Room.databaseBuilder(
                ctx.getApplicationContext(),
                AppDatabase.class,
                dbName
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .addCallback(new Callback() {
                @Override
                public void onCreate(@NonNull SupportSQLiteDatabase database) {
                    super.onCreate(database);
                    sDatabaseWriteExecutor.execute(() -> {
                        TimePeriodDao dao = holder[0].timePeriodDao();
                        if (dao.count() == 0) {
                            dao.insertGroups(createDefaultGroups());
                            dao.insertPeriods(createDefaultPeriods());
                        }
                    });
                }
            })
            .build();
            holder[0] = db;
            sInstances.put(userId, db);
            return db;
        }
    }

    /**
     * 向后兼容重载——默认用户 (userId=0)。
     */
    public static AppDatabase getInstance(Context context) {
        return getInstance(context, 0);
    }

    /** 关闭并移除指定用户的数据库实例。 */
    public static void clearInstance(long userId) {
        AppDatabase db = sInstances.remove(userId);
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    /** 关闭所有数据库实例。 */
    public static void clearAllInstances() {
        for (java.util.Map.Entry<Long, AppDatabase> entry : sInstances.entrySet()) {
            if (entry.getValue().isOpen()) {
                entry.getValue().close();
            }
        }
        sInstances.clear();
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
