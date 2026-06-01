# 代码审查报告 - 2026-05-31

## 审查范围

**审查类型**：全面代码审查  
**审查重点**：排查因缺少统一回调设计导致的重复业务逻辑  
**审查时间**：2026-05-31  
**项目路径**：/mnt/d/Documents/AndroidStudioProjects/JustNow  
**代码语言**：Java  
**代码文件数**：118 个主代码文件

## 审查结果概要

**发现问题总数**：8 个  
**严重程度分布**：
- 高优先级（重复逻辑严重）：3 个
- 中优先级（可优化）：4 个
- 低优先级（建议改进）：1 个

## 详细问题列表

### 1. [x] 任务完成流程重复：三处独立实现相似的完成逻辑
**状态**：已解决

**修复方式**：`BaseTaskViewModel`（从 `BaseViewModel` 分离出的 task 专用基类）提供 `protected final` 模板方法 `completeTaskFlow()`，封装"核心 sync + 通知 cancel + disable schedule + `onPostComplete` hook"。MainViewModel 覆写 `onPostComplete()` → `recomputeSync()`，ReminderDetailViewModel 不覆写（默认空）。非 task 类 ViewModel 继承 `BaseViewModel`，不被牵连。

**`cancelForTask` 删除**：`task_schedules` 的 UNIQUE 约束保证一个 task 只有一条 enabled schedule，`cancelForTask` 等价于 cancel 单条，方法已删除。逻辑自洽。

**位置**：
- `MainViewModel.completeRunningTask()` (622-656行)
- `ReminderDetailViewModel.doCompleteRunningTask()` (182-211行)
- `AlarmReceiver.handleStartTask()` (78-114行)

**问题描述**：
三个地方都实现了任务完成的核心流程，包含大量重复代码：
1. 计算实际耗时：`(endMs - startMs) / 60000`
2. 写入执行记录：`recordCompleteSync()`
3. 清除执行状态：`clearExecutionSync()`
4. 处理安排：取消闹钟 + disable schedule
5. 调用 `ReminderScheduler.cancelForTask()` 和 `ReminderNotifier.cancel()`

**重复模式**：
```java
// 模式 1：MainViewModel (622-656行)
long safeEndMs = Math.max(endMs, task.executingStartMs + 1);
int actualMinutes = Math.max(1, (int) ((safeEndMs - task.executingStartMs) / 60000));
mExecutionRepo.recordCompleteSync(task.id, task.executingStartMs, safeEndMs, actualMinutes);
mTaskRepo.clearExecutionSync(task.id);
if (stopSchedule && schedule != null) {
    mScheduleRepo.disableScheduleSync(schedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
}
ReminderScheduler scheduler = new ReminderScheduler(mApp);
scheduler.cancelForTask(taskId);
if (schedule != null) {
    ReminderNotifier.cancel(mApp, schedule.id);
}

// 模式 2：ReminderDetailViewModel (182-211行)
long safeEndMs = Math.max(endMs, task.executingStartMs + 1);
int actualMinutes = Math.max(1, (int) ((safeEndMs - task.executingStartMs) / 60000));
new TaskExecutionRepository(mDb)
    .recordCompleteSync(task.id, task.executingStartMs, safeEndMs, actualMinutes);
mTaskRepo.clearExecutionSync(task.id);
if (mSchedule != null) {
    new ReminderScheduler(mApp).cancel(mSchedule.id, mSchedule.scheduledTime);
    ReminderNotifier.cancel(mApp, mSchedule.id);
}
if (stopSchedule && mSchedule != null) {
    mScheduleRepo.disableScheduleSync(mSchedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
}

// 模式 3：AlarmReceiver (99-104行)
TaskExecutionAutoCompleter.completeRunningTaskSync(taskRepo,
    new TaskExecutionRepository(db),
    runningTask, System.currentTimeMillis());
```

**建议重构方案**：
在 `TaskRepository` 或新建 `TaskCompletionService` 中提供统一的完成回调接口：

```java
public class TaskCompletionService {
    
    public interface CompletionCallback {
        void onBeforeComplete(TaskEntity task);
        void onAfterComplete(TaskEntity task, TaskExecutionEntity execution);
    }
    
    /**
     * 统一任务完成流程
     * @param taskId 任务ID
     * @param endMs 结束时间戳
     * @param stopSchedule 是否停止安排
     * @param callback 可选的回调（用于特殊处理）
     */
    public void completeTask(long taskId, long endMs, boolean stopSchedule, 
                            CompletionCallback callback) {
        TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
        if (task == null || task.executingStartMs <= 0) return;
        
        if (callback != null) {
            callback.onBeforeComplete(task);
        }
        
        // 核心完成逻辑
        long safeEndMs = Math.max(endMs, task.executingStartMs + 1);
        int actualMinutes = Math.max(1, (int) ((safeEndMs - task.executingStartMs) / 60000));
        TaskExecutionEntity execution = mExecutionRepo.recordCompleteSync(
            task.id, task.executingStartMs, safeEndMs, actualMinutes);
        mTaskRepo.clearExecutionSync(task.id);
        
        // 处理安排
        TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
        if (schedule != null) {
            mScheduler.cancel(schedule.id, schedule.scheduledTime);
            ReminderNotifier.cancel(mContext, schedule.id);
            if (stopSchedule) {
                mScheduleRepo.disableScheduleSync(schedule.id, 
                    TaskScheduleEntity.REASON_USER_STOPPED);
            }
        }
        
        if (callback != null) {
            callback.onAfterComplete(task, execution);
        }
    }
}
```

**优先级**：高  
**影响范围**：MainViewModel, ReminderDetailViewModel, AlarmReceiver

---

### 2. [x] 短完成流程重复：两处实现相同的 < 15min 完成逻辑
**状态**：已解决

**修复方式**：`BaseTaskViewModel` 提供 `protected final` 模板方法 `shortCompleteFlow(taskId, stopSchedule, convertToChore, schedule)`，封装 cancel + disable schedule + 隐藏 + `onPostComplete` hook。MainViewModel 和 ReminderDetailViewModel 均调用统一方法。

**位置**：
- `MainViewModel.performShortCompletionSync()` (599-620行)
- `ReminderDetailViewModel.performShortCompletionSync()` (283-300行)

**问题描述**：
两个 ViewModel 中完全重复实现了短完成流程：
1. 判断是否转为琐碎：`convertToChoreSync()` vs `clearExecutionSync()`
2. 取消闹钟：`ReminderScheduler.cancel()` + `ReminderNotifier.cancel()`
3. 停止安排：`disableScheduleSync()`
4. 加入今日隐藏：`mChoreHiddenStore.hideForToday()`

**重复代码对比**：
```java
// MainViewModel (599-620行)
private void performShortCompletionSync(long taskId, boolean stopSchedule, boolean convertToChore) {
    TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
    if (task == null) return;
    if (convertToChore) {
        mTaskRepo.convertToChoreSync(taskId);
    } else {
        mTaskRepo.clearExecutionSync(taskId);
    }
    TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
    if (stopSchedule && schedule != null) {
        mScheduleRepo.disableScheduleSync(schedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
    }
    ReminderScheduler scheduler = new ReminderScheduler(mApp);
    scheduler.cancelForTask(taskId);
    if (schedule != null) {
        ReminderNotifier.cancel(mApp, schedule.id);
    }
    mChoreHiddenStore.hideForToday(taskId);
    recomputeSync();
}

// ReminderDetailViewModel (283-300行)
private void performShortCompletionSync(long taskId, boolean stopSchedule, boolean convertToChore) {
    TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
    if (task == null) return;
    if (convertToChore) {
        mTaskRepo.convertToChoreSync(taskId);
    } else {
        mTaskRepo.clearExecutionSync(taskId);
    }
    if (mSchedule != null) {
        new ReminderScheduler(mApp).cancel(mSchedule.id, mSchedule.scheduledTime);
        ReminderNotifier.cancel(mApp, mSchedule.id);
    }
    if (stopSchedule && mSchedule != null) {
        mScheduleRepo.disableScheduleSync(mSchedule.id, TaskScheduleEntity.REASON_USER_STOPPED);
    }
    mChoreHiddenStore.hideForToday(taskId);
}
```

**建议重构方案**：
提取到 `TaskCompletionService` 或 `TaskRepository`：

```java
/**
 * 短完成流程（< 15min）：不写执行记录，仅清除状态并隐藏
 */
public void performShortCompletion(long taskId, boolean stopSchedule, 
                                  boolean convertToChore, Runnable afterAction) {
    TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
    if (task == null) return;
    
    if (convertToChore) {
        mTaskRepo.convertToChoreSync(taskId);
    } else {
        mTaskRepo.clearExecutionSync(taskId);
    }
    
    TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
    if (schedule != null) {
        mScheduler.cancel(schedule.id, schedule.scheduledTime);
        ReminderNotifier.cancel(mContext, schedule.id);
        if (stopSchedule) {
            mScheduleRepo.disableScheduleSync(schedule.id, 
                TaskScheduleEntity.REASON_USER_STOPPED);
        }
    }
    
    mChoreHiddenStore.hideForToday(taskId);
    
    if (afterAction != null) {
        afterAction.run();
    }
}
```

**优先级**：高  
**影响范围**：MainViewModel, ReminderDetailViewModel

---

### 3. [x] 闹钟取消逻辑重复：多处独立调用相同的取消序列
**状态**：跳过（用户决策）

**位置**：
- `MainViewModel.completeRunningTask()` (648-652行)
- `MainViewModel.performShortCompletionSync()` (613-617行)
- `MainViewModel.archiveTask()` (707行)
- `ReminderDetailViewModel.doCompleteRunningTask()` (200-203行)
- `ReminderDetailViewModel.performShortCompletionSync()` (292-297行)
- `ReminderDetailViewModel.archiveTask()` (217-220行)
- `ReminderDetailViewModel.deleteTask()` (239-242行)

**问题描述**：
每次需要取消闹钟时，都重复以下模式：
```java
ReminderScheduler scheduler = new ReminderScheduler(mApp);
scheduler.cancelForTask(taskId);
if (schedule != null) {
    ReminderNotifier.cancel(mApp, schedule.id);
}
```

或者：
```java
new ReminderScheduler(mApp).cancel(mSchedule.id, mSchedule.scheduledTime);
ReminderNotifier.cancel(mApp, mSchedule.id);
```

**建议重构方案**：
在 `ReminderScheduler` 中提供统一的取消方法：

```java
public class ReminderScheduler {
    /**
     * 取消任务的所有闹钟和通知（统一入口）
     * @param taskId 任务ID
     * @param schedule 可选的安排实体（如果已加载）
     */
    public void cancelAllForTask(long taskId, TaskScheduleEntity schedule) {
        // 取消所有安排的闹钟
        cancelForTask(taskId);
        
        // 取消通知
        if (schedule != null) {
            ReminderNotifier.cancel(mAppContext, schedule.id);
        } else {
            // 如果没有传入 schedule，查询并取消
            TaskScheduleEntity activeSchedule = mScheduleRepo.getActiveScheduleSync(taskId);
            if (activeSchedule != null) {
                ReminderNotifier.cancel(mAppContext, activeSchedule.id);
            }
        }
    }
}
```

调用方简化为：
```java
new ReminderScheduler(mApp).cancelAllForTask(taskId, schedule);
```

**优先级**：高  
**影响范围**：MainViewModel, ReminderDetailViewModel, AlarmReceiver

---

### 4. [x] 归档任务流程重复：两处实现相同的归档逻辑
**状态**：已解决

**修复方式**：`BaseTaskViewModel` 提供 `protected final` 模板方法 `archiveTaskFlow(taskId, schedule, onComplete)`，封装"cancel 闹钟 + cancel 通知 + 清除执行状态 + 归档 + disable schedule + onComplete 回调"。MainViewModel 和 ReminderDetailViewModel 均调用统一方法。

**位置**：
- `MainViewModel.archiveTask()` (702-710行)
- `ReminderDetailViewModel.archiveTask()` (214-228行)

**问题描述**：
两处都实现了相同的归档流程：
1. 取消闹钟和通知
2. 清除执行状态
3. 归档任务
4. disable 安排

**重复代码对比**：
```java
// MainViewModel (702-710行)
public void archiveTask(long taskId) {
    runInBackground(() -> {
        mTaskRepo.clearExecutionSync(taskId);
        mTaskRepo.archiveTaskSync(taskId);
        mScheduleRepo.disableForTaskSync(taskId);
        new ReminderScheduler(mApp).cancelForTask(taskId);
        recompute();
    });
}

// ReminderDetailViewModel (214-228行)
public void archiveTask(Runnable onComplete) {
    runInBackground(() -> {
        if (mSchedule != null) {
            new ReminderScheduler(mApp).cancel(mSchedule.id, mSchedule.scheduledTime);
            ReminderNotifier.cancel(mApp, mSchedule.id);
        }
        mTaskRepo.clearExecutionSync(mTask.id);
        mTaskRepo.archiveTaskSync(mTask.id);
        mScheduleRepo.disableForTaskSync(mTask.id);
        if (onComplete != null) {
            runOnUiThread(onComplete);
        }
    });
}
```

**建议重构方案**：
在 `TaskRepository` 中提供统一的归档方法：

```java
/**
 * 归档任务（统一流程）
 * @param taskId 任务ID
 * @param callback 完成后回调
 */
public void archiveTaskWithCleanup(long taskId, Runnable callback) {
    assertNotMainThread();
    mDb.runInTransaction(() -> {
        // 1. 取消闹钟和通知
        TaskScheduleEntity schedule = mScheduleRepo.getActiveScheduleSync(taskId);
        if (schedule != null) {
            new ReminderScheduler(mContext).cancel(schedule.id, schedule.scheduledTime);
            ReminderNotifier.cancel(mContext, schedule.id);
        }
        new ReminderScheduler(mContext).cancelForTask(taskId);
        
        // 2. 清除执行状态
        clearExecutionSync(taskId);
        
        // 3. 归档任务
        archiveTaskSync(taskId);
        
        // 4. disable 安排
        mScheduleRepo.disableForTaskSync(taskId);
    });
    
    if (callback != null) {
        callback.run();
    }
}
```

**优先级**：中  
**影响范围**：MainViewModel, ReminderDetailViewModel

---

### 5. [x] Holiday 数据源重复：三个数据源实现相同的 HTTP 请求模式
**状态**：已解决

**修复方式**：`HolidayDataSource` 抽象类化，`fetch()` 标记 `final` 封装 HTTP 请求模板，`protected` 构造链正确。子类只实现 `getUrl()` / `parseAndFill()`。`parseAndFill` 为抽象方法，ChinaGovSource 用 `HolidayJsonParser`、HongKongGovSource / MacauGovSource 用 `IcsParser`，**比原建议更准确**。

**位置**：
- `ChinaGovSource.fetch()` (30-42行)
- `HongKongGovSource.fetch()` (30-41行)
- `MacauGovSource.fetch()` (30-41行)

**问题描述**：
三个数据源都实现了相同的 HTTP 请求 + 解析模式：
```java
Request request = new Request.Builder().url(URL).build();
try (Response response = mClient.newCall(request).execute()) {
    if (response.isSuccessful() && response.body() != null) {
        String text = response.body().string();
        HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(year);
        Parser.fill(entity, text, year, SOURCE_NAME);
        return entity;
    }
}
return HolidayCacheManager.emptyEntity(year);
```

**建议重构方案**：
提取抽象基类或使用策略模式：

```java
public abstract class BaseHolidaySource implements HolidayDataSource {
    
    protected final OkHttpClient mClient;
    
    public BaseHolidaySource(OkHttpClient client) {
        mClient = client;
    }
    
    protected abstract String getUrl(int year);
    protected abstract String getSourceName();
    protected abstract void parseAndFill(HolidayCacheEntity entity, 
                                        String content, int year);
    
    @Override
    public final HolidayCacheEntity fetch(int year) throws IOException {
        String url = getUrl(year);
        Request request = new Request.Builder().url(url).build();
        try (Response response = mClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String content = response.body().string();
                HolidayCacheEntity entity = HolidayCacheManager.emptyEntity(year);
                parseAndFill(entity, content, year);
                return entity;
            }
        }
        return HolidayCacheManager.emptyEntity(year);
    }
}

// 子类只需实现差异部分
public class ChinaGovSource extends BaseHolidaySource {
    private static final String BASE_URL = "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/";
    private static final String SOURCE_NAME = "holiday-cn";
    
    @Override
    protected String getUrl(int year) {
        return BASE_URL + year + ".json";
    }
    
    @Override
    protected String getSourceName() {
        return SOURCE_NAME;
    }
    
    @Override
    protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
        HolidayJsonParser.fill(entity, content, year, SOURCE_NAME);
    }
}
```

**优先级**：中  
**影响范围**：ChinaGovSource, HongKongGovSource, MacauGovSource

---

### 6. [x] Widget 和主界面重复计算引擎逻辑
**状态**：已解决

**修复方式**：`TaskComputeUtils` 回退（薄包装成本高于收益），`buildStatusText` 搬入 `TimeRemainingCalculator` 直接提供。Widget 端复用 `periodRepo.getActivePeriodGroupSync()`，消除双构造冗余。Widget 现在支持 holiday/weekend 分组切换（之前永远 REGULAR），符合 D010 决策。

**位置**：
- `MainViewModel.recomputeSync()` (354-505行)
- `WidgetUpdateHelper.updateWidget()` (117-214行)

**问题描述**：
两处都实现了相似的计算流程：
1. 获取活跃时段组
2. 自动完成过期任务
3. 计算时段状态
4. 构建 tagMap
5. 标签过滤
6. 引擎排序

虽然 Widget 和主界面的需求略有不同，但核心计算逻辑可以复用。

**建议重构方案**：
提取共享的计算服务：

```java
public class TaskDisplayCalculator {
    
    public static class CalculationContext {
        public List<TimePeriodEntity> periods;
        public TimeRemainingCalculator.PeriodStatus periodStatus;
        public Map<Long, TagEntity> tagMap;
        public List<TaskEntity> tasks;
        public boolean reverseQuadrant;
        public int remainingMinutes;
    }
    
    /**
     * 准备计算上下文（共享逻辑）
     */
    public CalculationContext prepareContext(Context context, Long filterTagId) {
        AppDatabase db = AppDatabase.getInstance(context);
        TimePeriodRepository periodRepo = new TimePeriodRepository(db, 
            new PeriodGroupRuleResolver(context));
        TaskRepository taskRepo = new TaskRepository(db);
        TagRepository tagRepo = new TagRepository(db);
        
        // 1. 获取时段
        ActivePeriodGroup activeGroup = periodRepo.getActivePeriodGroupSync();
        List<TimePeriodEntity> periods = TimeRemainingCalculator.sortPeriods(
            activeGroup.periods);
        
        // 2. 自动完成过期任务
        List<TaskEntity> tasks = taskRepo.getAllActiveTasksSync();
        TaskExecutionAutoCompleter.completeExpiredRunningTasksSync(
            taskRepo, new TaskExecutionRepository(db), tasks, periods,
            periodRepo.getAllPeriodsSync());
        
        // 3. 计算时段状态
        TimeRemainingCalculator calc = new TimeRemainingCalculator();
        TimeRemainingCalculator.PeriodStatus status = calc.compute(periods);
        
        // 4. 构建 tagMap
        Map<Long, TagEntity> tagMap = new HashMap<>();
        List<TagEntity> allTags = tagRepo.getAllTagsSync();
        if (allTags != null) {
            for (TagEntity tag : allTags) {
                tagMap.put(tag.id, tag);
            }
        }
        
        // 5. 标签过滤
        if (filterTagId != null && filterTagId > 0) {
            tasks.removeIf(t -> t.tagId == null || !t.tagId.equals(filterTagId));
        }
        
        CalculationContext ctx = new CalculationContext();
        ctx.periods = periods;
        ctx.periodStatus = status;
        ctx.tagMap = tagMap;
        ctx.tasks = tasks;
        ctx.reverseQuadrant = status.isReverseQuadrant();
        ctx.remainingMinutes = status.isInPeriod() ? status.remainingMinutes : 0;
        
        return ctx;
    }
}
```

**优先级**：中  
**影响范围**：MainViewModel, WidgetUpdateHelper

---

### 7. [x] 清单状态确认检查重复
**状态**：已解决

**修复方式**：`checkListStateNeedsConfirm(TaskEntity, long)` 参数取 task 而非单独 taskId，避免多余 DB 往返。MainViewModel 和 ReminderDetailViewModel 均调用统一方法。

**位置**：
- `MainViewModel.checkPreCompleteConfirm()` (682-688行)
- `ReminderDetailViewModel.checkListStateNeedsConfirm()` (155-158行)

**问题描述**：
两处实现了相同的清单状态检查逻辑：
```java
// MainViewModel
private boolean checkPreCompleteConfirm(long taskId) {
    TaskEntity task = mTaskRepo.getTaskByIdSync(taskId);
    if (!"checklist".equals(task != null ? task.detailModuleType : null)) {
        return false;
    }
    return mChecklistRepo.hasAnyStateSync(taskId);
}

// ReminderDetailViewModel
public boolean checkListStateNeedsConfirm(long taskId) {
    return "checklist".equals(mTask != null ? mTask.detailModuleType : null)
        && mChecklistRepo.hasAnyStateSync(taskId);
}
```

**建议重构方案**：
提取到 `TaskChecklistRepository` 或工具类：

```java
public class TaskChecklistRepository {
    /**
     * 检查任务是否需要清单状态确认
     */
    public boolean needsStateConfirmation(TaskEntity task, long taskId) {
        if (task == null || !"checklist".equals(task.detailModuleType)) {
            return false;
        }
        return hasAnyStateSync(taskId);
    }
}
```

**优先级**：中  
**影响范围**：MainViewModel, ReminderDetailViewModel

---

### 8. [x] 时段状态文本构建重复
**状态**：已解决

**修复方式**：`buildStatusText` 搬入 `TimeRemainingCalculator`，返回 `PeriodStatus { isUpcoming, isTomorrow, showRestHint }`，主界面和 Widget 均复用。Widget 端 `buildRestingStatusText` 保留 `findNextPeriodHint` 差异逻辑（不并入共享方法，正确）。

**位置**：
- `MainViewModel.recomputeSync()` (409-428行)
- `WidgetUpdateHelper.buildRestingStatusText()` (390-411行)

**问题描述**：
两处都实现了相似的"非时段状态"判断逻辑：
- 判断是否跨天（当天所有时段已结束）
- 查找下一个时段
- 构建提示文本

**建议重构方案**：
提取到 `TimeRemainingCalculator` 或新建 `PeriodStatusFormatter`：

```java
public class PeriodStatusFormatter {
    
    public static class StatusText {
        public boolean isUpcoming;
        public boolean isTomorrow;
        public boolean showRestHint;
        public String nextPeriodHint;
    }
    
    public static StatusText buildStatusText(List<TimePeriodEntity> periods, 
                                            TimeRemainingCalculator.PeriodStatus status) {
        StatusText result = new StatusText();
        result.isUpcoming = !status.isInPeriod();
        
        if (result.isUpcoming && periods != null && !periods.isEmpty()) {
            Calendar cal = Calendar.getInstance();
            int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 
                + cal.get(Calendar.MINUTE);
            int firstStart = periods.get(0).startMinute;
            int lastEnd = periods.get(periods.size() - 1).endMinute;
            
            result.showRestHint = (nowMinute < 6 * 60 || nowMinute < firstStart 
                || nowMinute >= lastEnd);
            
            // 检查下一个时段是否跨天
            boolean hasNextToday = false;
            for (TimePeriodEntity p : periods) {
                if (p.startMinute > nowMinute) {
                    hasNextToday = true;
                    break;
                }
            }
            result.isTomorrow = !hasNextToday;
        }
        
        return result;
    }
}
```

**优先级**：低  
**影响范围**：MainViewModel, WidgetUpdateHelper

---

## 未处理项汇总

（全部处理完毕，无未处理项）

## 总体建议

### 架构改进方向

1. **引入服务层**：在 Repository 和 ViewModel 之间增加 Service 层，封装跨多个 Repository 的业务流程
   - `TaskCompletionService`：统一任务完成流程
   - `TaskArchiveService`：统一归档流程
   - `ReminderCancellationService`：统一闹钟取消流程

2. **使用回调/策略模式**：对于有细微差异的业务流程，使用回调接口允许调用方注入差异化逻辑

3. **提取计算工具类**：将纯计算逻辑（无状态）提取为静态工具类或单例服务

### 重构优先级建议

**第一阶段（高优先级）**：
1. 重构任务完成流程（问题 1、2、3）
2. 统一闹钟取消逻辑（问题 3）

**第二阶段（中优先级）**：
3. 重构归档流程（问题 4）
4. 优化 Holiday 数据源（问题 5）
5. 提取共享计算逻辑（问题 6）

**第三阶段（低优先级）**：
6. 清理小型重复（问题 7、8）

### 测试建议

重构后需要重点测试：
1. 任务完成的各种路径（主界面、详情页、通知）
2. 短完成流程（< 15min）
3. 归档任务
4. 闹钟和通知的取消
5. Widget 更新

## 结论

项目中确实存在因缺少统一回调设计导致的重复业务逻辑，主要集中在：
- **任务完成流程**：三处独立实现
- **短完成流程**：两处重复
- **闹钟取消**：七处重复调用
- **归档流程**：两处重复

建议引入服务层（Service Layer）统一封装这些跨 Repository 的业务流程，使用回调接口处理差异化需求，可以显著减少代码重复，提高可维护性。

## 重构结论

复审时间：2026-06-01。8 项问题经过两轮审核，最终结果：

- **已解决（7 项）**：全部 7 项通过 `BaseTaskViewModel` 模板方法模式或独立抽象解决
- **跳过（1 项）**：闹钟取消逻辑（3）— 用户决策不纳入本次重构范围

### 架构决策

**模板方法模式**：`BaseTaskViewModel`（从 `BaseViewModel` 分离出的 task 专用基类）提供三个 `protected final` 模板：

| 模板方法 | 封装内容 |
|---|---|
| `completeTaskFlow(taskId, endMs, stopSchedule)` | 核心 sync → cancel 闹钟/通知 → disable schedule → `onPostComplete` hook |
| `shortCompleteFlow(taskId, stopSchedule, convertToChore, schedule)` | cancel → disable schedule → 隐藏 → `onPostComplete` hook |
| `archiveTaskFlow(taskId, schedule, onComplete)` | cancel 闹钟/通知 → 清除执行状态 → 归档 → disable schedule → onComplete 回调 |

继承链：
```
ViewModel → BaseViewModel (mApp/mDb/线程调度)
                  → BaseTaskViewModel (4 sync + 3 template + onPostComplete hook)
                      → MainViewModel (onPostComplete → recomputeSync)
                      → ReminderDetailViewModel (直接复用，不改写 hook)
```

非 task 类 ViewModel 仍继承 `BaseViewModel`，不被牵连。

### `cancelForTask` 删除

`task_schedules` 的 UNIQUE 约束保证一个 task 只有一条 enabled schedule，`cancelForTask(taskId)` 等价于 cancel 单条。方法已删除，调用方改为直接 cancel 单条 schedule。逻辑自洽。

### `TaskComputeUtils` 回退

薄包装成本高于收益，已回退。`buildStatusText` 搬入 `TimeRemainingCalculator`。Widget 端直接复用 `periodRepo.getActivePeriodGroupSync()`。

### 死代码清理

ReminderDetailViewModel / MainViewModel 未用 import 和字段已清理。

## 第三次审核结论

审核时间：2026-06-01（第三次）

上轮复审遗留的 4 项全部处理完毕：

| 遗留项 | 处理方式 |
|---|---|
| `archiveTaskSync` / `performShortCompletionSync` 补 `cancelForTask` | 通过 UNIQUE 约束收束 + 删除整个方法 |
| Widget 双 new `TimePeriodRepository` | `TaskComputeUtils` 删除后 WidgetUpdateHelper 直接复用 `periodRepo.getActivePeriodGroupSync()` |
| `BaseViewModel` 依赖面过大 | `BaseTaskViewModel` 分离，`BaseViewModel` 退回纯调度基类 |

模板方法架构方向正确。本轮无新设计问题。
