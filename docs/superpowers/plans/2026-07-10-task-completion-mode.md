# 任务完成模式 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 废弃四象限降级恢复策略，替换为任务完成模式（日/周/月/年配额），完成后当天隐藏、次日重现。

**Architecture:** 新增 `task_completion_counter` 表记录周期完成次数；TaskFilterHelper 每次 recompute 查今日执行记录 + 周期计数器判定显隐；DisplayEngine 移除降级权重，effectiveQuadrant 退化；QuadrantFragment 替换降级 chip 行为完成模式 chip 行；详情页底部新增趋势图。

**Tech Stack:** Java, Room, Android View-based UI, ViewBinding, Canvas (趋势图)

## Global Constraints

- SDK 36, minSdk 33, targetSdk 36
- Java 成员变量 `m` 前缀，静态非 final 变量 `s` 前缀
- 用户可见字符串必须资源化到 `strings.xml`（4 语言：en/zh-CN/zh-TW/zh-HK）
- 字号走 `@dimen/text_size_*` + `TextAppearance.JustNow.*`，禁止硬编码 sp
- Compose 禁用 Material 3，统一 Material 2
- 编译命令：`~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
- 单元测试：`~/gradlew-wsl.sh --no-daemon testDebugUnitTest --tests <测试类>`
- Robolectric 新测试固定 `@Config(sdk = 35)`
- Room 复合主键通过 `@Entity(primaryKeys = {...})` 声明，实体类中每个 PK 字段加 `@PrimaryKey`

---

### Task 1: 数据层 — Entity + DAO + 迁移

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/entity/TaskEntity.java:78-80`
- Create: `app/src/main/java/com/nearby/justnow/data/entity/TaskCompletionCounterEntity.java`
- Create: `app/src/main/java/com/nearby/justnow/data/dao/TaskCompletionCounterDao.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java:53-67,69,157-168`
- Delete: `app/src/main/java/com/nearby/justnow/data/entity/TaskQuadrantDegradeEntity.java`
- Delete: `app/src/main/java/com/nearby/justnow/data/dao/TaskQuadrantDegradeDao.java`

**Interfaces:**
- Produces: `TaskEntity.completionMode : int` (0=日 1=周 2=月 3=年), `TaskEntity.quota : int` (默认 1)
- Produces: `TaskCompletionCounterEntity` — `taskId : long`, `periodKey : String`, `completed : int`
- Produces: `TaskCompletionCounterDao.insertOrIncrement(taskId, periodKey)`, `queryByTaskId(taskId): List<TaskCompletionCounterEntity>`, `queryByTaskIdAndPeriodKey(taskId, periodKey): TaskCompletionCounterEntity`, `deleteByTaskId(taskId)`
- Produces: `AppDatabase.taskCompletionCounterDao() : TaskCompletionCounterDao`

- [x] **Step 1: 修改 TaskEntity — 删除 degradePeriod，新增 completionMode + quota**

```java
// 删除第 78-80 行：
// /** 降级恢复周期：0=不降级 1=次日 2=下周 3=下月 */
// @ColumnInfo(name = "degrade_period", defaultValue = "0")
// public int degradePeriod;

// 新增（在同一位置）：
/** 完成模式：0=日 1=周 2=月 3=年 */
@ColumnInfo(name = "completion_mode", defaultValue = "0")
public int completionMode;

/** 配额：日模式固定1，周/月/年由用户设置 */
@ColumnInfo(name = "quota", defaultValue = "1")
public int quota;
```

- [x] **Step 2: 创建 TaskCompletionCounterEntity**

```java
// 文件: app/src/main/java/com/nearby/justnow/data/entity/TaskCompletionCounterEntity.java
package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = "task_completion_counter",
        primaryKeys = {"task_id", "period_key"})
public class TaskCompletionCounterEntity {
    @ColumnInfo(name = "task_id")
    public long taskId;

    @ColumnInfo(name = "period_key")
    public String periodKey;

    @ColumnInfo(name = "completed", defaultValue = "0")
    public int completed;

    public TaskCompletionCounterEntity() {}

    public TaskCompletionCounterEntity(long taskId, String periodKey, int completed) {
        this.taskId = taskId;
        this.periodKey = periodKey;
        this.completed = completed;
    }
}
```

- [x] **Step 3: 创建 TaskCompletionCounterDao**

```java
// 文件: app/src/main/java/com/nearby/justnow/data/dao/TaskCompletionCounterDao.java
package com.nearby.justnow.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;

import java.util.List;

@Dao
public interface TaskCompletionCounterDao {

    @Query("SELECT * FROM task_completion_counter WHERE task_id = :taskId AND period_key = :periodKey")
    TaskCompletionCounterEntity getByTaskIdAndPeriodKey(long taskId, String periodKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(TaskCompletionCounterEntity entity);

    @Query("SELECT * FROM task_completion_counter WHERE task_id = :taskId ORDER BY period_key DESC LIMIT 10")
    List<TaskCompletionCounterEntity> queryByTaskIdDesc(long taskId);

    @Query("DELETE FROM task_completion_counter WHERE task_id = :taskId")
    void deleteByTaskId(long taskId);

    @Query("DELETE FROM task_completion_counter WHERE task_id IN (:taskIds)")
    void deleteByTaskIds(List<Long> taskIds);

    @Transaction
    default void insertOrIncrement(long taskId, String periodKey) {
        TaskCompletionCounterEntity existing = getByTaskIdAndPeriodKey(taskId, periodKey);
        if (existing != null) {
            existing.completed++;
            insertOrReplace(existing);
        } else {
            insertOrReplace(new TaskCompletionCounterEntity(taskId, periodKey, 1));
        }
    }
}
```

- [x] **Step 4: 修改 AppDatabase — entities、DAO、MIGRATION_6_7**

```java
// 第 53-67 行 entities 列表：删除 TaskQuadrantDegradeEntity.class，新增 TaskCompletionCounterEntity.class
@Database(entities = {
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
    TaskScheduleSkipEntity.class
}, version = 7, exportSchema = true)

// 第 69 行：version = 6 → version = 7
```

```java
// DAO 列表：替换 taskQuadrantDegradeDao() → taskCompletionCounterDao()
public abstract TaskCompletionCounterDao taskCompletionCounterDao();
// 删除：public abstract TaskQuadrantDegradeDao taskQuadrantDegradeDao();
```

```java
// 新增 MIGRATION_6_7
static final Migration MIGRATION_6_7 = new Migration(6, 7) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase database) {
        // 1. 新增 completion_mode + quota 列
        database.execSQL("ALTER TABLE tasks ADD COLUMN completion_mode INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE tasks ADD COLUMN quota INTEGER NOT NULL DEFAULT 1");

        // 2. 创建 task_completion_counter 表
        database.execSQL("CREATE TABLE IF NOT EXISTS task_completion_counter ("
                + "task_id INTEGER NOT NULL, "
                + "period_key TEXT NOT NULL, "
                + "completed INTEGER NOT NULL DEFAULT 0, "
                + "PRIMARY KEY(task_id, period_key), "
                + "FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE"
                + ")");

        // 3. 删除 degrade 表
        database.execSQL("DROP TABLE IF EXISTS task_quadrant_degrade");
    }
};
```

```java
// .addMigrations() 链中追加 MIGRATION_6_7
```

- [x] **Step 5: 删除旧文件**

```bash
rm app/src/main/java/com/nearby/justnow/data/entity/TaskQuadrantDegradeEntity.java
rm app/src/main/java/com/nearby/justnow/data/dao/TaskQuadrantDegradeDao.java
```

- [x] **Step 6: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/data/entity/TaskEntity.java \
        app/src/main/java/com/nearby/justnow/data/entity/TaskCompletionCounterEntity.java \
        app/src/main/java/com/nearby/justnow/data/dao/TaskCompletionCounterDao.java \
        app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java
git rm app/src/main/java/com/nearby/justnow/data/entity/TaskQuadrantDegradeEntity.java \
       app/src/main/java/com/nearby/justnow/data/dao/TaskQuadrantDegradeDao.java
git commit -m "feat: 数据层 — 完成模式替代降级策略（Entity + DAO + DB 6→7）"
```

---

### Task 2: TaskRepository — 移除降级，接入计数器

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java`

**Interfaces:**
- Consumes: `TaskCompletionCounterDao` (from Task 1)
- Produces: `incrementCompletionCounterSync(taskId, periodKey)`, `getCompletionCounterSync(taskId, periodKey): TaskCompletionCounterEntity`, `getCompletionCountersByTaskIdSync(taskId): List<TaskCompletionCounterEntity>`, `deleteCompletionCountersByTaskIdSync(taskId)`

- [x] **Step 1: 构造器新增 `mCompletionCounterDao`**

```java
// 构造器中新增字段和参数：
private final TaskCompletionCounterDao mCompletionCounterDao;

// 构造器参数新增：
// , TaskCompletionCounterDao completionCounterDao
// 赋值：
// this.mCompletionCounterDao = completionCounterDao;
```

- [x] **Step 2: 删除所有 degrade 方法和字段**

```bash
# 删除以下方法（及其他 degrade 相关）：
# - insertDegradeSync()
# - deleteDegradeSync()
# - getAllDegradesSync()
# - getNonExpiredDegradeMapSync()
# 删除字段：
# - mDegradeDao
# - mCachedDegrades
```

- [x] **Step 3: 新增计数器方法**

```java
// 周期 key 计算辅助方法
public static String computePeriodKey(TaskEntity task) {
    Calendar cal = Calendar.getInstance();
    switch (task.completionMode) {
        case 0: // 日模式 — 不写计数器
            return null;
        case 1: // 周
            cal.setFirstDayOfWeek(Calendar.MONDAY);
            int weekOfYear = cal.get(Calendar.WEEK_OF_YEAR);
            return cal.get(Calendar.YEAR) + "-W" + String.format("%02d", weekOfYear);
        case 2: // 月
            return cal.get(Calendar.YEAR) + "-" + String.format("%02d", cal.get(Calendar.MONTH) + 1);
        case 3: // 年
            return String.valueOf(cal.get(Calendar.YEAR));
        default:
            return null;
    }
}

// 计数器写入（可能不写入日模式）
public void incrementCompletionCounterSync(long taskId, String periodKey) {
    if (periodKey == null) return; // 日模式不写
    mCompletionCounterDao.insertOrIncrement(taskId, periodKey);
}

// 单个周期查询
public TaskCompletionCounterEntity getCompletionCounterSync(long taskId, String periodKey) {
    if (periodKey == null) return null;
    return mCompletionCounterDao.getByTaskIdAndPeriodKey(taskId, periodKey);
}

// 趋势图数据
public List<TaskCompletionCounterEntity> getCompletionCountersByTaskIdSync(long taskId) {
    return mCompletionCounterDao.queryByTaskIdDesc(taskId);
}

// 清理
public void deleteCompletionCountersByTaskIdSync(long taskId) {
    mCompletionCounterDao.deleteByTaskId(taskId);
}
```

```java
// 在 archiveTaskSync(), delete(), deleteSync() 中：
// 将 mDegradeDao.deleteByTaskId(taskId) → mCompletionCounterDao.deleteByTaskId(taskId)
```

- [x] **Step 4: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java
git commit -m "feat: TaskRepository — 移除降级方法，接入完成计数器"
```

---

### Task 3: 完成流程 — 写入计数器

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskExecutionAutoCompleter.java:60-75`
- Modify: `app/src/main/java/com/nearby/justnow/ui/base/BaseTaskViewModel.java:45-69`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java:636-648`

**Interfaces:**
- Consumes: `TaskRepository.incrementCompletionCounterSync(taskId, periodKey)` (from Task 2)
- Consumes: `TaskRepository.computePeriodKey(task)` (from Task 2)

- [x] **Step 1: 修改 TaskExecutionAutoCompleter.completeRunningTaskSync — 替换降级写入为计数器写入**

```java
// 第 60-75 行修改后：
public static void completeRunningTaskSync(TaskRepository taskRepo,
                                           TaskExecutionRepository executionRepo,
                                           TaskEntity task,
                                           long endMs) {
    if (task == null || task.executingStartMs <= 0) return;
    long safeEndMs = Math.max(endMs, task.executingStartMs + 1);
    int actualMinutes = Math.max(1, (int) ((safeEndMs - task.executingStartMs) / 60000));
    executionRepo.recordCompleteSync(task.id, task.executingStartMs, safeEndMs, actualMinutes);
    taskRepo.clearExecutionSync(task.id);

    // 完成计数器：日模式不写，周/月/年模式写入
    String periodKey = TaskRepository.computePeriodKey(task);
    taskRepo.incrementCompletionCounterSync(task.id, periodKey);
}
```

```java
// 删除 computeDegradeRecoverMs 方法（第 104-128 行）
```

- [x] **Step 2: 修改 BaseTaskViewModel.performShortCompletionSync — 短完成也写计数器**

```java
// 第 45-69 行，在 hideForToday(taskId) 之后追加：
// 短完成也消耗配额
String periodKey = TaskRepository.computePeriodKey(task);
taskRepo.incrementCompletionCounterSync(taskId, periodKey);
```

- [x] **Step 3: 修改 MainViewModel — 删除 cleanExpiredDegrades**

```java
// 删除 cleanExpiredDegrades() 方法（第 641-648 行）

// onPostComplete() 中删除 cleanExpiredDegrades() 调用（第 638 行）：
@Override
protected void onPostComplete() {
    super.onPostComplete();
    // cleanExpiredDegrades() 已删除 — 完成模式不需要过期清理
}
```

- [x] **Step 4: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskExecutionAutoCompleter.java \
        app/src/main/java/com/nearby/justnow/ui/base/BaseTaskViewModel.java \
        app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java
git commit -m "feat: 完成流程 — 计数器替代降级记录写入"
```

---

### Task 4: TaskFilterHelper — 替换降级过滤为完成模式可见性判定

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java:149-206`

**Interfaces:**
- Consumes: `TaskRepository.getCompletionCounterSync(taskId, periodKey)` (from Task 2)
- Consumes: `TaskRepository.computePeriodKey(task)` (from Task 2)
- Produces: 过滤后的任务列表，主界面右侧栏 + Widget 共用

- [x] **Step 1: 在 computeFilteredTasks 末端新增完成模式过滤步骤**

```java
// 在 computeFilteredTasks 方法的标签过滤之后、缓存保存之前，新增步骤 6：

// 6. 完成模式：日/周/月/年隐藏判定
List<TaskExecutionEntity> todayExecutions = mApp.getTaskExecutionRepository()
        .getTodayExecutionsSync(); // 假设已有此方法，或使用 mTodayExecutions 成员
Set<Long> todayCompletedIds = new HashSet<>();
if (todayExecutions != null) {
    for (TaskExecutionEntity e : todayExecutions) {
        todayCompletedIds.add(e.taskId);
    }
}

Iterator<TaskEntity> iter = tasks.iterator();
while (iter.hasNext()) {
    TaskEntity task = iter.next();
    if (todayCompletedIds.contains(task.id)) {
        // 今天完成过 → 日模式直接隐藏
        if (task.completionMode == 0) {
            iter.remove();
            continue;
        }
        // 周/月/年模式：还需检查周期配额
        String periodKey = TaskRepository.computePeriodKey(task);
        TaskCompletionCounterEntity counter = mApp.getTaskRepository()
                .getCompletionCounterSync(task.id, periodKey);
        if (counter != null && counter.completed >= task.quota) {
            iter.remove();
        }
    }
}
```

- [x] **Step 2: computeDisplayItems 中删除 degradeMap**

```java
// 第 197-206 行修改后：
private List<DisplayItem> computeDisplayItems(List<TaskEntity> tasks, int maxDisplayItems) {
    List<TaskScheduleEntity> todaySchedules = mApp.getTaskScheduleRepository().getAllEnabledSchedulesSync();
    Set<Long> schedulePriorityIds = MainViewModel.computeSchedulePriorityIds(todaySchedules);
    DisplayPolicy displayPolicy = mApp.getDisplayPolicyRepository().getEffectivePolicySync();

    return mDisplayEngine.compute(tasks, mTagMap, mStatus.remainingMinutes,
            mStatus.isReverseQuadrant(), maxDisplayItems,
            Collections.emptySet(), schedulePriorityIds, displayPolicy);
}
// 删除 degradeMap 变量和参数传递
```

- [x] **Step 3: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java
git commit -m "feat: TaskFilterHelper — 完成模式可见性判代替降解过滤"
```

---

### Task 5: DisplayEngine — 移除降级权重参数链

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/engine/DisplayEngine.java:22-79,199-213`
- Modify: `app/src/main/java/com/nearby/justnow/ui/engine/DisplayItem.java`

**Interfaces:**
- Produces: `DisplayItem.effectiveQuadrant` 始终等于 `task.quadrant`

- [x] **Step 1: 删除所有 compute 重载中的 degradeMap 参数**

```java
// 第 22-79 行，删除所有重载中的 Map<Long, TaskQuadrantDegradeEntity> degradeMap 参数
// 每个重载只传不含 degradeMap 的参数给下一级重载

// 第 22-27 行改为（删除 6 参数重载）：
public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                  int remainingMin, boolean reverseQuadrant,
                                  int maxDisplayItems) {
    return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
            Collections.emptySet());
}

// 第 32-37 行改为：
public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                  int remainingMin, boolean reverseQuadrant,
                                  int maxDisplayItems, Set<Long> priorityTagIds) {
    return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
            priorityTagIds, Collections.emptySet(), DisplayPolicy.defaultPolicy());
}

// 第 53-60 行改为（跳过原 7 参数含 degradeMap 的重载）：
public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                  int remainingMin, boolean reverseQuadrant,
                                  int maxDisplayItems, Set<Long> priorityTagIds,
                                  Set<Long> schedulePriorityTaskIds) {
    return compute(tasks, tagMap, remainingMin, reverseQuadrant, maxDisplayItems,
            priorityTagIds, schedulePriorityTaskIds, DisplayPolicy.defaultPolicy());
}

// 第 62-79 行改为（最终实现，移除 degradeMap 参数）：
public List<DisplayItem> compute(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
                                  int remainingMin, boolean reverseQuadrant,
                                  int maxDisplayItems, Set<Long> priorityTagIds,
                                  Set<Long> schedulePriorityTaskIds,
                                  DisplayPolicy policy) {
    // 内部调用 buildSortedGroups 和 buildDisplayItem 时不再传 degradeMap
}
```

- [x] **Step 2: buildDisplayItem 移除 degradeMap 参数**

```java
// 第 199-213 行修改后：
private DisplayItem buildDisplayItem(TaskEntity task, Map<Long, TagEntity> tagMap) {
    TagEntity tag = (tagMap != null && task.tagId != null) ? tagMap.get(task.tagId) : null;
    DisplayItem item = new DisplayItem(task, tag);
    item.effectiveQuadrant = task.quadrant; // 直接等于原始象限，不再变换
    return item;
}
```

- [x] **Step 3: buildSortedGroups 方法删除 degradeMap 参数并更新调用**

- [x] **Step 4: computeByQuadrant 方法删除 degradeMap 参数传递**

- [x] **Step 5: 更新 DisplayItem.effectiveQuadrant 注释**

```java
// DisplayItem.java 中：
/** 生效象限，直接等于 task.quadrant（完成模式无需降级变换） */
public int effectiveQuadrant;
```

- [x] **Step 6: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 7: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/ui/engine/DisplayEngine.java \
        app/src/main/java/com/nearby/justnow/ui/engine/DisplayItem.java
git commit -m "feat: DisplayEngine — 移除降级权重参数链，effectiveQuadrant 退化"
```

---

### Task 6: UI — QuadrantFragment 完成模式 Chip 行

**Files:**
- Modify: `app/src/main/res/layout/fragment_quadrant.xml` — 降级 chip 行改完成模式 chip 行
- Modify: `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantFragment.java:89-120`
- Modify: `app/src/main/res/values/strings.xml` — 新增/删除字符串
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`

**Interfaces:**
- Consumes: `TaskInputViewModel.setCompletionMode(mode)`, `TaskInputViewModel.setQuota(quota)` (from Task 7)
- Produces: 完成模式 chip 行 UI

- [x] **Step 1: strings.xml 四语言修改**

```xml
<!-- 删除 -->
<!-- s_degrade_next_day, s_degrade_next_week, s_degrade_next_month, s_degrade_none -->

<!-- 新增（values/strings.xml 英文为默认语言） -->
<string name="s_mode_daily">Daily</string>
<string name="s_mode_weekly">Weekly</string>
<string name="s_mode_monthly">Monthly</string>
<string name="s_mode_yearly">Yearly</string>
<string name="s_mode_quota_hint_weekly">(max 6)</string>
<string name="s_mode_quota_hint_monthly">(max 27)</string>
<string name="s_mode_quota_hint_yearly">(max 11)</string>
<string name="s_mode_quota_label">Quota:</string>
<string name="s_mode_quota_exceed">The maximum value is %d</string>

<!-- zh-CN -->
<string name="s_mode_daily">每天</string>
<string name="s_mode_weekly">每周</string>
<string name="s_mode_monthly">每月</string>
<string name="s_mode_yearly">每年</string>
<string name="s_mode_quota_hint_weekly">(最大 6)</string>
<string name="s_mode_quota_hint_monthly">(最大 27)</string>
<string name="s_mode_quota_hint_yearly">(最大 11)</string>
<string name="s_mode_quota_label">配额：</string>
<string name="s_mode_quota_exceed">最大值为 %d</string>
```

- [x] **Step 2: fragment_quadrant.xml — 替换 chip 行布局**

```xml
<!-- 删除整个 ll_degrade_chips 内容，替换为 -->
<LinearLayout
    android:id="@+id/ll_completion_mode"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingHorizontal="16dp"
    android:paddingVertical="8dp">

    <TextView
        android:id="@+id/chip_mode_daily"
        style="@style/TextAppearance.JustNow.Body"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_day_chip"
        android:paddingHorizontal="12dp"
        android:paddingVertical="6dp"
        android:text="@string/s_mode_daily"
        android:clickable="true"
        android:focusable="true"
        android:layout_marginEnd="8dp" />

    <TextView
        android:id="@+id/chip_mode_weekly"
        style="@style/TextAppearance.JustNow.Body"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_day_chip"
        android:paddingHorizontal="12dp"
        android:paddingVertical="6dp"
        android:text="@string/s_mode_weekly"
        android:clickable="true"
        android:focusable="true"
        android:layout_marginEnd="8dp" />

    <TextView
        android:id="@+id/chip_mode_monthly"
        style="@style/TextAppearance.JustNow.Body"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_day_chip"
        android:paddingHorizontal="12dp"
        android:paddingVertical="6dp"
        android:text="@string/s_mode_monthly"
        android:clickable="true"
        android:focusable="true"
        android:layout_marginEnd="8dp" />

    <TextView
        android:id="@+id/chip_mode_yearly"
        style="@style/TextAppearance.JustNow.Body"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_day_chip"
        android:paddingHorizontal="12dp"
        android:paddingVertical="6dp"
        android:text="@string/s_mode_yearly"
        android:clickable="true"
        android:focusable="true"
        android:layout_marginEnd="8dp" />

    <EditText
        android:id="@+id/et_quota"
        style="@style/TextAppearance.JustNow.Body"
        android:layout_width="56dp"
        android:layout_height="wrap_content"
        android:inputType="number"
        android:text="1"
        android:textAlignment="center"
        android:background="@drawable/bg_search_box"
        android:visibility="gone"
        android:layout_marginStart="4dp" />

    <TextView
        android:id="@+id/tv_quota_hint"
        style="@style/TextAppearance.JustNow.Caption"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:visibility="gone" />
</LinearLayout>
```

- [x] **Step 3: QuadrantFragment — 重写 chip 行逻辑**

```java
// setupDegradeChips() → setupCompletionModeChips()
private void setupCompletionModeChips() {
    mViewModel.setCompletionMode(0); // 默认每天
    mViewModel.setQuota(1);
    updateChipSelection(getBinding().chipModeDaily);

    View.OnClickListener chipListener = v -> {
        updateChipSelection(v);
        int id = v.getId();
        com.nearby.justnow.databinding.FragmentQuadrantBinding b = getBinding();

        if (id == b.chipModeDaily.getId()) {
            mViewModel.setCompletionMode(0);
            mViewModel.setQuota(1);
            b.etQuota.setVisibility(View.GONE);
            b.tvQuotaHint.setVisibility(View.GONE);
        } else if (id == b.chipModeWeekly.getId()) {
            mViewModel.setCompletionMode(1);
            showQuotaInput(1, 6, R.string.s_mode_quota_hint_weekly);
        } else if (id == b.chipModeMonthly.getId()) {
            mViewModel.setCompletionMode(2);
            showQuotaInput(1, 27, R.string.s_mode_quota_hint_monthly);
        } else if (id == b.chipModeYearly.getId()) {
            mViewModel.setCompletionMode(3);
            showQuotaInput(1, 11, R.string.s_mode_quota_hint_yearly);
        }
    };

    getBinding().chipModeDaily.setOnClickListener(chipListener);
    getBinding().chipModeWeekly.setOnClickListener(chipListener);
    getBinding().chipModeMonthly.setOnClickListener(chipListener);
    getBinding().chipModeYearly.setOnClickListener(chipListener);

    // EtQuota 输入监听
    getBinding().etQuota.addTextChangedListener(new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            try {
                int val = Integer.parseInt(s.toString());
                mViewModel.setQuota(val);
            } catch (NumberFormatException ignored) {}
        }
    });
}

private void showQuotaInput(int defaultVal, int maxVal, int hintResId) {
    FragmentQuadrantBinding b = getBinding();
    b.etQuota.setVisibility(View.VISIBLE);
    b.etQuota.setText(String.valueOf(defaultVal));
    b.tvQuotaHint.setVisibility(View.VISIBLE);
    b.tvQuotaHint.setText(getString(hintResId));
    // 保存 maxVal 用于校验
    b.etQuota.setTag(maxVal);
}

// 在保存前校验配额
private boolean validateQuota() {
    if (getBinding().etQuota.getVisibility() != View.VISIBLE) return true;
    int quota = mViewModel.getQuota();
    int max = (int) getBinding().etQuota.getTag();
    if (quota > max) {
        Toast.makeText(getContext(), getString(R.string.s_mode_quota_exceed, max), Toast.LENGTH_SHORT).show();
        return false;
    }
    return true;
}
```

```java
// updateChipSelection 更新引用：
private void updateChipSelection(View selected) {
    FragmentQuadrantBinding b = getBinding();
    for (View chip : new View[]{
        b.chipModeDaily, b.chipModeWeekly, b.chipModeMonthly, b.chipModeYearly
    }) {
        chip.setSelected(chip == selected);
    }
}
```

- [x] **Step 4: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantFragment.java \
        app/src/main/res/layout/fragment_quadrant.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-zh-rCN/strings.xml \
        app/src/main/res/values-zh-rTW/strings.xml \
        app/src/main/res/values-zh-rHK/strings.xml
git commit -m "feat: UI — 完成模式 chip 行替代降级 chip 行"
```

---

### Task 7: TaskInputViewModel — 替换 degrade 字段

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java:112-119,242-248,473-478`

**Interfaces:**
- Consumes: `TaskEntity.completionMode`, `TaskEntity.quota` (from Task 1)
- Produces: `setCompletionMode(int mode)`, `getCompletionMode(): int`, `setQuota(int quota)`, `getQuota(): int`

- [x] **Step 1: resetDraft 替换默认值**

```java
// 第 117 行：
// mDraftTask.degradePeriod = 1; → 删除
// 新增：
mDraftTask.completionMode = 0; // 默认每天
mDraftTask.quota = 1;
```

- [x] **Step 2: 替换 setDegradePeriod / getDegradePeriod**

```java
// 删除 setDegradePeriod / getDegradePeriod（第 242-248 行）
// 新增：
public void setCompletionMode(int mode) {
    mDraftTask.completionMode = mode;
    if (mode == 0) {
        mDraftTask.quota = 1; // 日模式固定 1
    }
}

public int getCompletionMode() {
    return mDraftTask.completionMode;
}

public void setQuota(int quota) {
    mDraftTask.quota = quota;
}

public int getQuota() {
    return mDraftTask.quota;
}
```

- [x] **Step 3: saveTask 中删除象限变更清理降级逻辑**

```java
// 第 473-478 行修改后：
if (mEditingTaskId > 0) {
    // 象限变更不再需要清理降级（降级表已删除）
    mTaskRepo.updateSync(mDraftTask);
} else {
    mDraftTask.createdAt = System.currentTimeMillis();
    long newTaskId = mTaskRepo.insertSync(mDraftTask);
    mDraftTask.id = newTaskId;
}
```

- [x] **Step 4: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java
git commit -m "feat: TaskInputViewModel — degradePeriod → completionMode + quota"
```

---

### Task 8: 详情页底部趋势图

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/ui/trendchart/TrendChartView.java`
- Modify: `app/src/main/res/layout/activity_reminder_detail.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/reminderdetail/ReminderDetailActivity.java`

**Interfaces:**
- Consumes: `TaskRepository.getCompletionCountersByTaskIdSync(taskId): List<TaskCompletionCounterEntity>` (from Task 2)
- Produces: `TrendChartView` — 圆点连线折线图，Y 轴 5 档虚线，最近 10 周期完成率

- [x] **Step 1: 创建 TrendChartView**

```java
// 文件: app/src/main/java/com/nearby/justnow/ui/trendchart/TrendChartView.java
package com.nearby.justnow.ui.trendchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;

import java.util.ArrayList;
import java.util.List;

public class TrendChartView extends View {

    private static final int Y_LEVELS = 5; // 0%, 25%, 50%, 75%, 100%
    private static final float DOT_RADIUS_DP = 4f;
    private static final float LINE_WIDTH_DP = 1.5f;
    private static final float BASELINE_WIDTH_DP = 2f;

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBaselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Float> mRatios = new ArrayList<>(); // 完成率 0..1，第 0 个=最新
    private int mQuota = 1;

    public TrendChartView(Context context) { super(context); init(); }
    public TrendChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        float dotRadius = DOT_RADIUS_DP * density;
        float lineWidth = LINE_WIDTH_DP * density;
        float baselineWidth = BASELINE_WIDTH_DP * density;

        mLinePaint.setColor(Color.parseColor("#2196F3"));
        mLinePaint.setStrokeWidth(lineWidth);
        mLinePaint.setStyle(Paint.Style.STROKE);

        mDotPaint.setColor(Color.parseColor("#2196F3"));
        mDotPaint.setStyle(Paint.Style.FILL);

        mDashPaint.setColor(Color.parseColor("#E0E0E0"));
        mDashPaint.setStrokeWidth(lineWidth);
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setPathEffect(new DashPathEffect(new float[]{8f * density, 4f * density}, 0));

        mBaselinePaint.setColor(Color.parseColor("#BDBDBD"));
        mBaselinePaint.setStrokeWidth(baselineWidth);
        mBaselinePaint.setStyle(Paint.Style.STROKE);

        setMinimumHeight((int) (80 * density));
    }

    public void setData(List<TaskCompletionCounterEntity> counters, int quota) {
        mQuota = Math.max(1, quota);
        mRatios.clear();
        if (counters != null) {
            for (TaskCompletionCounterEntity c : counters) {
                mRatios.add(Math.min(1f, (float) c.completed / mQuota));
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mRatios.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        float paddingLeft = 32f;
        float paddingRight = 32f;
        float paddingTop = 12f;
        float paddingBottom = 12f;
        float chartLeft = paddingLeft;
        float chartRight = w - paddingRight;
        float chartTop = paddingTop;
        float chartBottom = h - paddingBottom;
        float chartWidth = chartRight - chartLeft;
        float chartHeight = chartBottom - chartTop;

        // Y 轴 5 档横线（从上到下：100% → 0%）
        for (int i = 0; i < Y_LEVELS; i++) {
            float y = chartTop + chartHeight * i / (Y_LEVELS - 1);
            if (i == Y_LEVELS - 1) {
                // 0% 基线：浅色实线 + 竖线刻度
                canvas.drawLine(chartLeft, y, chartRight, y, mBaselinePaint);
                for (int tick = 0; tick <= 10; tick++) {
                    float tx = chartLeft + chartWidth * tick / 10;
                    canvas.drawLine(tx, y - 3f, tx, y + 3f, mBaselinePaint);
                }
            } else {
                canvas.drawLine(chartLeft, y, chartRight, y, mDashPaint);
            }
        }

        // X 轴数据点位置（从右到左：第 0 个=最新）
        int count = mRatios.size();
        if (count < 2) return;

        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = chartRight - chartWidth * i / (count - 1);
            ys[i] = chartBottom - chartHeight * mRatios.get(i); // 0% 在底部
        }

        // 折线
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        canvas.drawPath(path, mLinePaint);

        // 圆点
        for (int i = 0; i < count; i++) {
            canvas.drawCircle(xs[i], ys[i], DOT_RADIUS_DP * getResources().getDisplayMetrics().density, mDotPaint);
        }
    }
}
```

- [x] **Step 2: activity_reminder_detail.xml 底部新增趋势图容器**

```xml
<!-- 在 ll_bottom_buttons 之前，ScrollView 外部新增 -->
<com.nearby.justnow.ui.trendchart.TrendChartView
    android:id="@+id/trend_chart"
    android:layout_width="match_parent"
    android:layout_height="80dp"
    android:layout_above="@id/ll_bottom_buttons"
    android:visibility="gone" />
```

- [x] **Step 3: ReminderDetailActivity 加载趋势图数据**

```java
// 在 loadTask() 或 setupBottomButtons() 之后调用：
private void setupTrendChart(TaskEntity task) {
    if (task.completionMode == 0) { // 日模式不显示
        mBinding.trendChart.setVisibility(View.GONE);
        return;
    }
    List<TaskCompletionCounterEntity> counters = mViewModel.getCompletionCounters(task.id);
    if (counters == null || counters.size() < 2) { // 不足 2 个周期不显示
        mBinding.trendChart.setVisibility(View.GONE);
        return;
    }
    mBinding.trendChart.setData(counters, task.quota);
    mBinding.trendChart.setVisibility(View.VISIBLE);
}

// 在 ViewModel 中新增：
public List<TaskCompletionCounterEntity> getCompletionCounters(long taskId) {
    return mApp.getTaskRepository().getCompletionCountersByTaskIdSync(taskId);
}
```

- [x] **Step 4: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/ui/trendchart/TrendChartView.java \
        app/src/main/res/layout/activity_reminder_detail.xml \
        app/src/main/java/com/nearby/justnow/ui/reminderdetail/ReminderDetailActivity.java
git commit -m "feat: 详情页底部趋势图 — 最近10周期完成率折线图"
```

---

### Task 9: Widget 过滤一致性

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/widget/WidgetUpdateHelper.java:310-311,433-454`

**Interfaces:**
- Consumes: TaskFilterHelper 完成模式过滤（from Task 4）

- [x] **Step 1: WidgetUpdateHelper.computeItems 删除 degradeMap 参数**

```java
// 删除所有重载中的 Map<Long, TaskQuadrantDegradeEntity> degradeMap 参数
// 第 433-454 行修改后：

static List<DisplayItem> computeItems(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
        TimeRemainingCalculator.PeriodStatus status, int maxItems,
        Set<Long> schedulePriorityIds) {
    return computeItems(tasks, tagMap, status, maxItems, schedulePriorityIds,
        DisplayPolicy.defaultPolicy());
}

static List<DisplayItem> computeItems(List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
        TimeRemainingCalculator.PeriodStatus status, int maxItems,
        Set<Long> schedulePriorityIds, DisplayPolicy policy) {
    int remainingMin = status.isInPeriod() ? status.remainingMinutes : 0;
    boolean reverseQuadrant = status.isReverseQuadrant();
    try {
        List<DisplayItem> result = sDisplayEngine.compute(tasks, tagMap, remainingMin, reverseQuadrant,
            maxItems, java.util.Collections.emptySet(), schedulePriorityIds, policy);
        return result;
    } catch (Exception e) {
        return buildFallbackList(tasks, tagMap);
    }
}
// 删除 import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity
```

- [x] **Step 2: 确认 Widget 的 updateWidget 中通过 TaskFilterHelper 获取已过滤的 tasks**

```java
// Widget 的 updateWidget 方法（第 182 行附近）中：
// filterHelper.getFilteredTasks() 或 filterHelper.getDisplayItems() → 已经过完成模式过滤
// 确认 TaskFilterHelper 在 Widget 中也生效
```

- [x] **Step 3: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 4: Commit**

```bash
git add app/src/main/java/com/nearby/justnow/widget/WidgetUpdateHelper.java
git commit -m "feat: Widget — 移除 degradeMap，复用完成模式过滤"
```

---

### Task 10: 清理与收尾

**Files:**
- 全局搜索并删除 degrade 相关 import 和代码

- [x] **Step 1: 全局搜索残留的 degrade 引用**

```bash
grep -rni "degrade" app/src/main/java/ app/src/main/res/ app/src/test/
grep -rni "TaskQuadrantDegrade" app/src/main/java/ app/src/test/
grep -rni "degradeMap" app/src/main/java/
grep -rni "insertDegrade" app/src/main/java/
grep -rni "cleanExpiredDegrades" app/src/main/java/
```

- [x] **Step 2: 逐个修复残留引用（编译错误会暴露所有残留）**

- [x] **Step 3: 删除测试文件中的 degrade 相关测试**

```bash
# 查找并删除：
# - TaskQuadrantDegradeDao 相关测试
# - DisplayEngine 降级权重测试（修改为完成模式测试）
# - 降级恢复逻辑测试
```

- [x] **Step 4: 编译验证**

Run: `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL

- [x] **Step 5: Commit**

```bash
git add -u
git commit -m "chore: 清理 degrade 相关残留代码和引用"
```

---

### Task 11: 测试

**Files:**
- Create: `app/src/test/java/com/nearby/justnow/data/dao/TaskCompletionCounterDaoTest.java`
- Modify: `app/src/test/java/com/nearby/justnow/data/dao/TaskQuadrantDegradeDaoTest.java` → 删除
- Modify: `app/src/test/java/com/nearby/justnow/ui/engine/DisplayEngineTest.java`

- [ ] **Step 1: TaskCompletionCounterDao 测试**

```java
// 文件: app/src/test/java/com/nearby/justnow/data/dao/TaskCompletionCounterDaoTest.java
package com.nearby.justnow.data.dao;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@Config(sdk = 35)
public class TaskCompletionCounterDaoTest {

    private AppDatabase mDb;
    private TaskCompletionCounterDao mDao;

    @Before
    public void setUp() {
        mDb = Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase.class).build();
        mDao = mDb.taskCompletionCounterDao();
    }

    @After
    public void tearDown() { mDb.close(); }

    @Test
    public void insertOrIncrement_createsNewRecord() {
        mDao.insertOrIncrement(1, "2026-W28");
        TaskCompletionCounterEntity result = mDao.getByTaskIdAndPeriodKey(1, "2026-W28");
        assertNotNull(result);
        assertEquals(1, result.completed);
    }

    @Test
    public void insertOrIncrement_incrementsExisting() {
        mDao.insertOrIncrement(1, "2026-W28");
        mDao.insertOrIncrement(1, "2026-W28");
        TaskCompletionCounterEntity result = mDao.getByTaskIdAndPeriodKey(1, "2026-W28");
        assertEquals(2, result.completed);
    }

    @Test
    public void insertOrIncrement_differentPeriods_areIndependent() {
        mDao.insertOrIncrement(1, "2026-W28");
        mDao.insertOrIncrement(1, "2026-W29");
        assertEquals(1, mDao.getByTaskIdAndPeriodKey(1, "2026-W28").completed);
        assertEquals(1, mDao.getByTaskIdAndPeriodKey(1, "2026-W29").completed);
    }

    @Test
    public void queryByTaskIdDesc_returnsOrderedByPeriodKeyDesc() {
        mDao.insertOrIncrement(1, "2026-W25");
        mDao.insertOrIncrement(1, "2026-W26");
        mDao.insertOrIncrement(1, "2026-W27");
        List<TaskCompletionCounterEntity> results = mDao.queryByTaskIdDesc(1);
        assertEquals(3, results.size());
        assertTrue(results.get(0).periodKey.compareTo(results.get(1).periodKey) > 0);
    }

    @Test
    public void deleteByTaskId_removesRecords() {
        mDao.insertOrIncrement(1, "2026-W28");
        mDao.insertOrIncrement(1, "2026-W29");
        mDao.deleteByTaskId(1);
        assertNull(mDao.getByTaskIdAndPeriodKey(1, "2026-W28"));
    }
}
```

- [ ] **Step 2: 运行 DAO 测试**

Run: `~/gradlew-wsl.sh --no-daemon testDebugUnitTest --tests com.nearby.justnow.data.dao.TaskCompletionCounterDaoTest`
Expected: All tests PASS

- [ ] **Step 3: 更新 DisplayEngineTest 移除 degrade 测试**

```java
// DisplayEngineTest.java：
// 删除与 degradeMap 相关的测试用例
// 修改测试代码中所有 compute() 调用，移除 degradeMap 参数
```

- [ ] **Step 4: 运行全量单元测试**

Run: `~/gradlew-wsl.sh --no-daemon testDebugUnitTest`
Expected: 0 failures

- [ ] **Step 5: Commit**

```bash
git add app/src/test/
git commit -m "test: 完成模式 DAO 测试 + 移除 degrade 测试"
```

---

## Validation Criteria

- `compileDebugJavaWithJavac` 通过
- `testDebugUnitTest` 全量 0 失败
- degrade 相关代码/资源/测试全部清理干净
- 日模式任务完成 → 当天隐藏 → 次日重新出现（主界面右侧栏 + Widget）
- 周模式配额 N → 本周完成 N 次后整周隐藏 → 下周一 00:00 重新出现
- 月/年模式同理
- 四象限概览和单象限管理始终显示全部任务（不受完成模式影响）
- 任务详情页趋势图在周/月/年模式下正确渲染
- 数据库迁移 6→7 后旧 degrade 数据被清理，新 counter 表可用
