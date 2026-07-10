# 任务完成模式 — 实现计划

> 设计文档：[2026-07-10-task-completion-mode-design.md](2026-07-10-task-completion-mode-design.md)
> 创建日期：2026-07-10

## 架构原则：计算路径收敛，引擎与过滤分离

完成模式取代降级恢复后，每次 recompute 只需查 `task_executions`（今日完成状态）和 `task_completion_counter`（周期累计）；不再需要 degrade 表缓存、过期判定、象限变换。两层判定各自独立：

```
TaskFilterHelper（过滤可见性）          DisplayEngine（排序+截取）
────────────────────                   ────────────────────────
query task_executions today            effectiveQuadrant = task.quadrant
query task_completion_counter          不再受 degrade 影响
→ 决定隐藏/显示                        → 只关心排序与比例
```

完成计数器写入紧耦合在任务完成流程中，与写 `task_executions` 在同一个事务内完成，保证一致性。

---

## 实现阶段

### F1：数据层 — Entity + DAO + 迁移

**TaskEntity.java**
- 删除 `degradePeriod` 字段（含 `@ColumnInfo(name = "degrade_period")`）
- 新增 `completionMode` 字段：`int`，`@ColumnInfo(name = "completion_mode", defaultValue = "0")`
- 新增 `quota` 字段：`int`，`@ColumnInfo(name = "quota", defaultValue = "1")`

**新文件 TaskCompletionCounterEntity.java**

`/app/src/main/java/com/nearby/justnow/data/entity/TaskCompletionCounterEntity.java`

```java
@Entity(tableName = "task_completion_counter")
public class TaskCompletionCounterEntity {
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    public long taskId;

    @ColumnInfo(name = "period_key")
    @PrimaryKey
    public String periodKey;

    @ColumnInfo(name = "completed", defaultValue = "0")
    public int completed;
}
```

联合主键 `(task_id, period_key)`，需要 Room 复合主键语法。

**新文件 TaskCompletionCounterDao.java**

`/app/src/main/java/com/nearby/justnow/data/dao/TaskCompletionCounterDao.java`

```
- insertOrIncrement(taskId, periodKey)：通过 INSERT ... ON CONFLICT REPLACE 实现累计 +1
  → 等效：SELECT completed → completed+1 → INSERT REPLACE，或使用 UPDATE + INSERT 事务
- queryByTaskId(taskId)：查询某任务所有周期记录（供详情趋势图）
- queryByTaskIdAndPeriodKey(taskId, periodKey)：单个周期查询（供过滤判定）
- deleteByTaskId(taskId)：任务删除/归档时清理
```

**删除**
- 删除 `TaskQuadrantDegradeEntity.java`
- 删除 `TaskQuadrantDegradeDao.java`

**AppDatabase.java 变更**
- `@Database` entities：移除 `TaskQuadrantDegradeEntity.class`，新增 `TaskCompletionCounterEntity.class`
- 删除 `taskQuadrantDegradeDao()` 抽象方法
- 新增 `taskCompletionCounterDao()` 抽象方法
- **DB version 6 → 7**
- 新增 `MIGRATION_6_7`：
  - `ALTER TABLE tasks ADD COLUMN completion_mode INTEGER NOT NULL DEFAULT 0`
  - `ALTER TABLE tasks ADD COLUMN quota INTEGER NOT NULL DEFAULT 1`
  - `ALTER TABLE tasks DROP COLUMN degrade_period`（Room 2.6+ 支持）
  - `DROP TABLE IF EXISTS task_quadrant_degrade`
  - `CREATE TABLE IF NOT EXISTS task_completion_counter (task_id INTEGER NOT NULL, period_key TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(task_id, period_key), FOREIGN KEY(task_id) REFERENCES tasks(id) ON DELETE CASCADE)`
- `.addMigrations()` 链中追加 `MIGRATION_6_7`

**注意**：`DROP COLUMN degrade_period` 需要 Room 2.6+ + SQLite 3.35+。如不可行则保留列但不再读写。

### F2：TaskCompletionCounterDao — 累计方法

DAO 中 insertOrIncrement 有两种实现方案：

**方案 A（推荐）：事务内 SELECT + INSERT/UPDATE**

```java
@Transaction
default void insertOrIncrement(long taskId, String periodKey) {
    TaskCompletionCounterEntity existing = getSync(taskId, periodKey);
    if (existing != null) {
        existing.completed++;
        update(existing);
    } else {
        TaskCompletionCounterEntity entity = new TaskCompletionCounterEntity();
        entity.taskId = taskId;
        entity.periodKey = periodKey;
        entity.completed = 1;
        insert(entity);
    }
}
```

**方案 B：UPSERT（Room 2.5+ 支持 `@Upsert`）**

```java
@Upsert
void upsert(TaskCompletionCounterEntity entity);
```
配合外层先查询再设值。当前项目 Room 版本需确认是否支持。

### F3：TaskRepository — 移除降级，接入计数器

**移除**
- 构造器 `mDegradeDao` → 删除
- `mCachedDegrades` → 删除
- `insertDegradeSync()` → 删除
- `deleteDegradeSync()` → 删除
- `getAllDegradesSync()` → 删除
- `getNonExpiredDegradeMapSync()` → 删除
- `archiveTaskSync()` 内 `mDegradeDao.deleteByTaskId(taskId)` → 替换为 `mCompletionCounterDao.deleteByTaskId(taskId)`
- `delete()` / `deleteSync()` 内 `mDegradeDao.deleteByTaskId(taskId)` → 替换为 `mCompletionCounterDao.deleteByTaskId(taskId)`
- `TaskRepositoryTest` 中 degrade 相关测试 → 删除

**新增**
- 构造器新增 `mCompletionCounterDao` 引用
- `incrementCompletionCounterSync(long taskId, String periodKey)`：委托 DAO
- `getCompletionCounterSync(long taskId, String periodKey)`：单周期查询
- `getCompletionCountersByTaskIdSync(long taskId)`：趋势图数据源
- `deleteCompletionCountersByTaskIdSync(long taskId)`：清理

### F4：完成流程 — 写入计数器

**TaskExecutionAutoCompleter.java**
- `completeRunningTaskSync()`：移除降级写入（第 70-74 行 `if (task.degradePeriod > 0) { taskRepo.insertDegradeSync(...) }`）
- 替换为：`taskRepo.incrementCompletionCounterSync(task.id, computePeriodKey(task))`
- 新增 `computePeriodKey(TaskEntity task)` 辅助方法，根据 `task.completionMode` 计算 `period_key`：
  - `completionMode == 0`（日模式）：不写计数器，直接 return
  - `completionMode == 1`（周）：`yyyy-Www`
  - `completionMode == 2`（月）：`yyyy-MM`
  - `completionMode == 3`（年）：`yyyy`

**BaseTaskViewModel.java**
- `performShortCompletionSync()`：短完成同样需要写计数器（短完成虽不写 task_executions，但一样消耗配额）
  - 完成流程末尾追加：`mApp.getTaskRepository().incrementCompletionCounterSync(taskId, periodKey)`
- `completeRunningTaskSync()`：已通过 TaskExecutionAutoCompleter 写计数器，无需重复

**MainViewModel.java**
- `onPostComplete()`：移除 `cleanExpiredDegrades()` 调用（第 636-638 行）
- 删除 `cleanExpiredDegrades()` 方法（第 641-647 行）
- 删除 `import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity`

### F5：过滤逻辑 — TaskFilterHelper 替换

**TaskFilterHelper.computeFilteredTasks()**
- 移除步骤 5（第 177 行 `TimelineBuilder.hideCompletedChoresForToday(...)` 的影响保留，但需要评估是否仍与完成模式兼容）
- 保留：ChoreHiddenTodayStore 逻辑不受影响，继续使用
- **新增完成模式过滤步骤**（在标签过滤之后、保存缓存之前）：

```java
// 6. 完成模式：日/周/月/年隐藏判定
List<TaskExecutionEntity> todayExecutions = /* 已有 */;
Set<Long> todayCompletedIds = new HashSet<>();
for (TaskExecutionEntity e : todayExecutions) {
    todayCompletedIds.add(e.taskId);
}

List<TaskEntity> allTasks = /* 已有 tasks 列表 */;
for (TaskEntity task : allTasks) {
    if (todayCompletedIds.contains(task.id)) {
        // 今天完成过
        if (task.completionMode == 0) {
            // 日模式：完成即隐藏
            tasks.remove(task);
            continue;
        }
        // 周/月/年模式：还需检查周期配额
        String periodKey = computePeriodKey(task);
        TaskCompletionCounterEntity counter = mApp.getTaskRepository().getCompletionCounterSync(task.id, periodKey);
        if (counter != null && counter.completed >= task.quota) {
            tasks.remove(task);
        }
    }
}
```

**TaskFilterHelper.computeDisplayItems()**
- 删除第 198 行 `Map<Long, TaskQuadrantDegradeEntity> degradeMap = mApp.getTaskRepository().getNonExpiredDegradeMapSync();`
- `mDisplayEngine.compute(...)` 调用中删除 `degradeMap` 参数

### F6：DisplayEngine — 移除降级权重

**DisplayEngine.java**
- `compute()` 所有重载：删除 `Map<Long, TaskQuadrantDegradeEntity> degradeMap` 参数
  - 涉及 5 个重载方法（第 22-67 行）
- `buildSortedGroups()`：删除 `degradeMap` 参数
- `buildDisplayItem()`：删除 `degradeMap` 参数，`effectiveQuadrant` 直接等于 `task.quadrant`
  - 移除 `if (degradeMap != null) { ... }` 降级变换块
- `computeByQuadrant()` 相关方法：删除 `degradeMap` 参数传递

**DisplayItem.java**
- 更新 `effectiveQuadrant` 字段注释：删除"降级期内使用降级后的象限"，改为"直接等于 task.quadrant"

**WidgetUpdateHelper.java**
- `computeItems()` 方法签名：删除 `Map<Long, TaskQuadrantDegradeEntity> degradeMap` 参数
- 调用处删除 `degradeMap` 传递
- 删除 `import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity`

### F7：UI — QuadrantFragment 完成模式 Chip 行

**fragment_quadrant.xml**
- 替换 `ll_degrade_chips` LinearLayout 内容：
  - 删除 4 个 chip TextView（`chip_degrade_next_day`, `chip_degrade_next_week`, `chip_degrade_next_month`, `chip_degrade_none`）
  - 新增 4 个 chip TextView：`chip_mode_daily` / `chip_mode_weekly` / `chip_mode_monthly` / `chip_mode_yearly`
  - 文本引用新字符串：`s_mode_daily` / `s_mode_weekly` / `s_mode_monthly` / `s_mode_yearly`
  - 新增 `et_quota` EditText（默认隐藏，GONE），位于 chip 行右侧
  - 新增 `tv_quota_hint` TextView（默认隐藏，GONE），位于 EditText 下方
  - chip 背景沿用 `@drawable/bg_day_chip`

**QuadrantFragment.java**
- `setupDegradeChips()` → 重命名为 `setupCompletionModeChips()`
- 逻辑改为：
  - 默认选中 `chip_mode_daily`，配额 EditText 隐藏
  - 选中 `chip_mode_weekly`/`chip_mode_monthly`/`chip_mode_yearly`：显示配额 EditText，显示对应提示（最大 6/27/11）
  - 点击事件调用 `mViewModel.setCompletionMode(mode)` 和 `mViewModel.setQuota(quota)`
  - 超出最大配额：Toast 提示
- 删除对 `chipDegradeNextDay` / `chipDegradeNextWeek` / `chipDegradeNextMonth` / `chipDegradeNone` 的所有引用

**strings.xml**
- 删除 `s_degrade_next_day`, `s_degrade_next_week`, `s_degrade_next_month`, `s_degrade_none`
- 新增：
  - `s_mode_daily` = "每天"
  - `s_mode_weekly` = "每周"
  - `s_mode_monthly` = "每月"
  - `s_mode_yearly` = "每年"
  - `s_mode_quota_hint_weekly` = "(最大 6)"
  - `s_mode_quota_hint_monthly` = "(最大 27)"
  - `s_mode_quota_hint_yearly` = "(最大 11)"

### F8：TaskInputViewModel — 替换 degrade 字段

**TaskInputViewModel.java**
- `resetDraft()`：`mDraftTask.degradePeriod = 1` → `mDraftTask.completionMode = 0; mDraftTask.quota = 1`
- 删除 `setDegradePeriod(int period)` / `getDegradePeriod()` 方法
- 新增 `setCompletionMode(int mode)` / `getCompletionMode()`：
  ```java
  public void setCompletionMode(int mode) {
      mDraftTask.completionMode = mode;
      if (mode == 0) mDraftTask.quota = 1; // 日模式固定 1
  }
  ```
- 新增 `setQuota(int quota)` / `getQuota()`：
  ```java
  public void setQuota(int quota) {
      mDraftTask.quota = quota;
  }
  ```
- `saveTask()` 中：删除第 474-478 行象限变更 → 清理降级的逻辑（`mTaskRepo.deleteDegradeSync(mEditingTaskId)`）
  - 象限变更不再需要清理降级（降级表已删除）

### F9：详情页底部趋势图

**ReminderDetailActivity.java**
- 布局 `activity_reminder_detail.xml`：
  - 在底部按钮栏上方新增 `ll_trend_chart` 容器，id 为 `@+id/ll_trend_chart`
  - 此区域默认 GONE，仅当任务为周/月/年模式且存在 >= 2 周期数据时显示
- 新增 `TrendChartView`：自定义 View，绘制圆点连线折线图
  - 规格：最近 10 周期完成率曲线，Y 轴 5 档虚线（0%/25%/50%/75%/100%）
  - 数据点圆点 + 折线连接
  - 底部不标注周期名
  - 固定在底部不随 ScrollView 滚动（通过父布局 `RelativeLayout` 或 `CoordinatorLayout` 实现）
- 数据加载：
  - `loadTask()` 之后查 `task_completion_counter` 按 `period_key DESC LIMIT 10`
  - 计算完成率 `completed / quota`
  - 传给 TrendChartView

**activity_reminder_detail.xml**
- 当前布局若使用 ScrollView，趋势图区域需要 `android:layout_alignParentBottom="true"` 或类似固定底部手法
- 确保趋势图与操作按钮栏同层，不随内容滚动

### F10：Widget 更新 — 过滤一致性

**WidgetUpdateHelper.java**
- `applyFilterToTasks()` 中新增：完成模式过滤（与 TaskFilterHelper 一致）
- 确保 Widget 不展示已隐藏/满配任务（日模式当天完成隐藏、周/月/年模式满配额隐藏）
- 过滤逻辑抽取为公共静态方法，TaskFilterHelper 和 WidgetUpdateHelper 共用，避免逻辑不一致

### F11：清理

- 删除 `TaskQuadrantDegradeDao.java` 源文件
- 删除 `TaskQuadrantDegradeEntity.java` 源文件
- 删除 `TaskQuadrantDegradeDaoTest.java` 测试文件
- 删除 `fragment_quadrant.xml` 中 `chip_degrade_*` 相关的 binding 引用（生成代码自动清理）
- 确认 `AppDatabase` 中所有 degrade import 已清理
- 确认 UI 层没有残留 `degrade` 相关调用

### F12：测试

| # | 测试 | 范围 | 文件 |
|---|---|---|---|
| 1 | TaskCompletionCounterDao: insertOrIncrement 新记录 | DAO | 新建 `TaskCompletionCounterDaoTest.java` |
| 2 | TaskCompletionCounterDao: insertOrIncrement 累加 | DAO | 同上 |
| 3 | TaskCompletionCounterDao: 跨周期新起记录 | DAO | 同上 |
| 4 | 日模式：task_executions 有记录 → 当日隐藏 | Engine | `DisplayEngineTest.java` 更新 |
| 5 | 周模式：配额满 → 整周隐藏，下周一重置 | Engine | `TaskFilterHelper` 或 `TaskRepository` 对应测试 |
| 6 | 月/年模式同理 | Engine/DAO | 同上 |
| 7 | 短完成计入配额 | Repository | `TaskRepositoryTest.java` 更新 |
| 8 | 编辑任务减配额 < 已完成 → 即时隐藏 | Engine | 新增 |
| 9 | 主界面过滤保留四象限概览不受影响 | ViewModel | `MainViewModelTest` |
| 10 | Widget 过滤与主界面一致 | Widget | `WidgetUpdateHelper` 相关测试 |
| 11 | DB 迁移 6→7：旧 degrade 表删除 + 新 counter 表创建 | Migration | `AppDatabaseMigrationTest` |
| 12 | 全量 `testDebugUnitTest` 通过 | 全量 | — |

---

## 验证标准

- `compileDebugJavaWithJavac` 通过
- `testDebugUnitTest` 全量 0 失败
- degrade 相关代码/资源/测试全部清理干净
- 日模式任务完成 → 当天消失 → 次日重新出现（主界面右侧栏 + Widget）
- 周模式配额 N → 本周完成 N 次后整周隐藏 → 下周一 00:00 重新出现
- 月/年模式同理
- 四象限概览和单象限管理始终显示全部任务（不受完成模式影响）
- 任务详情页趋势图在周/月/年模式下正确渲染
- 数据库迁移后旧 degrade 数据被清理，新 counter 表可用
