# 代码审查报告（合并版）- 2026-06-02

## 合并说明

本报告由以下三份同日审查报告合并而成，去重后保留所有唯一问题：

| 原报告 | 来源 | 发现问题数 |
|--------|------|-----------|
| `code-review-20260602.md` | 全面审查 | 10 |
| `code-review-20260602-v1.md` | 子代理 a8b5f1368fcfab9aa | 12 |
| `code-review-20260602-v2.md` | 全面审查（第二次） | 14 |

去重后共 **31 个唯一问题**。严重级别统一为：blocking / important / suggestion / nit。

---

## 审查范围

**审查类型**：全面代码审查
**审查时间**：2026-06-02
**项目路径**：/mnt/d/Documents/AndroidStudioProjects/JustNow
**代码语言**：Java
**代码文件数**：约 100 个主代码文件 + 25 个测试文件 + 资源文件
**目标 SDK**：36
**最低 SDK**：33

---

## 审查结果概要

| 级别 | 数量 | 说明 |
|------|------|------|
| blocking | 8 | 必须修复（ANR 风险、NPE、Android 版本强制要求等） |
| important | 12 | 建议修复（正确性、可维护性、一致性） |
| suggestion | 9 | 优化建议（长期技术债务、性能边界） |
| nit | 2 | 微小优化（当前影响可忽略） |

---

## 正面发现

以下方面设计良好，综合自三份报告的一致评价：

1. **MVVM 分层清晰**：ViewModel 不持有 Context（通过 `JustNowApplication` 间接获取），Repository 封装 DAO，数据流单向清晰。
2. **基类设计合理**：`BaseViewModel`、`BaseTaskViewModel` 通过模板方法模式（`completeTaskFlow` / `shortCompleteFlow` / `archiveTaskFlow`）消除大量重复代码。
3. **防重入机制**：`MainViewModel.recompute()` 使用 `AtomicBoolean` + `mRecomputeQueued` 双标志防抖，避免高频 TIME_TICK 触发堆积。
4. **`SingleLiveEvent` 设计**：用 `AtomicBoolean.compareAndSet` 守门避免 LiveData 重放，比 `Event` wrapper 模式更简洁。
5. **线程模型成熟**：`runInBackground` / `runOnUiThread` 封装一致，`AtomicBoolean` 防抖处理得当。
6. **国际化完善**：4 种语言（EN、zh-CN、zh-TW、zh-HK），字符串资源化彻底。
7. **测试覆盖较好**：`src/test/` 下有 25 个测试文件，覆盖 DAO、Repository、Engine、Scheduler 等核心模块。
8. **降级与防御**：`DisplayEngine` 有 try-catch + fallback 机制，`HolidaySyncWorker` 重试策略区分瞬时/永久错误。
9. **样式规范统一**：`TextAppearance.JustNow.*` 体系覆盖完整，字号统一引用 `dimens.xml`。
10. **Widget 标签筛选独立存储**：`WidgetFilterStore` 以 widgetId 为 key 独立存储每个 Widget 实例的筛选状态，支持多 Widget 场景。
11. **自定义 View 渲染精细**：`TimelineView`、`HourColumnView` 等自定义视图实现精细的时间线 UI，onDraw 逻辑直接操作 Canvas。

---

## 详细问题列表

### blocking（必须修复）

### 1. [x] 缺少 Edge-to-Edge 支持

**位置**：所有 Activity（`MainActivity`、`TaskInputActivity`、`TagManageActivity`、`TaskScheduleActivity`、`PeriodConfigActivity`、`StatsActivity`、`ReminderDetailActivity`、`WidgetPermissionGateActivity`）

**问题描述**：项目 `targetSdk = 36`（>= 35），但所有 Activity 均未调用 `enableEdgeToEdge()`。Android 15（SDK 35）起强制要求 edge-to-edge 渲染。

`MainActivity.onCreate()` 中使用旧 API 模式：
```java
getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
setLegacyStatusBarColor(statusBarColor);  // 已废弃
```

主题中设置了 `android:statusBarColor` 和 `android:windowLightStatusBar`，`activity_main.xml` 中 `AppBarLayout` 使用 `android:fitsSystemWindows="true"`，`MainActivity` 手动管理状态栏颜色和图标亮度。

**修复建议**：
1. 每个 Activity 的 `onCreate` 中，在 `setContentView` 前调用 `enableEdgeToEdge()`
2. 移除 `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`、`setStatusBarColor()` 等旧 API
3. 改用 `WindowInsets` API 处理系统栏内边距
4. 移除 XML 中的 `fitsSystemWindows="true"`，改用 `WindowInsetsCompat` 的现代 padding 方式

**来源**：code-review-20260602.md #1

---

### 2. [x] 缺少 IME 适配（adjustResize）

**位置**：`app/src/main/AndroidManifest.xml`

**问题描述**：AndroidManifest.xml 中没有任何 Activity 声明 `android:windowSoftInputMode="adjustResize"`。当软键盘弹出时，默认行为（`adjustPan`）可能导致输入框被键盘遮挡。受影响 Activity：`TaskInputActivity`（任务录入，含 EditText 和搜索）、`TagManageActivity`（标签管理，含文本输入）、`PeriodConfigActivity`（时间段编辑）。

**修复建议**：在 AndroidManifest.xml 中为需要软键盘的 Activity 添加：
```xml
android:windowSoftInputMode="adjustResize"
```
并配合布局中的 IME inset 处理，确保输入区域不被遮挡。

**来源**：code-review-20260602.md #2

---

### 3. [x] `WidgetFilterStore.setFilterTagId()` 主线程同步 I/O，存在 ANR 风险

**位置**：`WidgetFilterStore.java` L30-38

**问题描述**：
```java
mPrefs.edit()
    .putLong(buildFilterKey(widgetId), tagId)
    .commit();  // 同步写磁盘
```
`commit()` 是同步 I/O 操作，将数据写入磁盘后才返回。`JustNowWidgetProvider.onReceive()` 在主线程回调中调用 `setFilterTagId()`，存储设备较慢时可触发 ANR。同类的 `clearFilter()` 已正确使用 `apply()`（异步）。

**修复建议**：将 `commit()` 改为 `apply()`。如需确保 Widget 更新前筛选状态已持久化，将写入操作提交到后台线程。

**来源**：code-review-20260602-v1.md #1、code-review-20260602-v2.md #3

---

### 4. [x] `TaskInputViewModel.checklistContentChanged()` 空指针风险

**位置**：`TaskInputViewModel.java` L346-354

**问题描述**：
```java
if (!oldItems.get(i).content.equals(newItems.get(i).content)) return true;
```
`TaskChecklistItem.content` 字段无 `@NonNull` 约束、无 `defaultValue`，可为 null。若某个 checklist item 的 content 为 null，调用链路 `saveTask()` → `checklistContentChanged()` 中的 `.equals()` 抛出 NullPointerException，导致整个保存操作失败。

**修复建议**：使用 `java.util.Objects.equals(oldItems.get(i).content, newItems.get(i).content)` 替代直接 `String.equals()`，天然处理 null 情况。

**来源**：code-review-20260602-v2.md #4

---

### 5. [x] `WidgetUpdateHelper.cancelMinuteBoundary()` 空指针风险

**位置**：`WidgetUpdateHelper.java` L265-275

**问题描述**：
```java
PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
```
`FLAG_NO_CREATE` 表示不创建新 PendingIntent，如果对应 PI 不存在（如 Widget 已全部移除后 `cancelMinuteBoundary` 仍被调用），`getBroadcast()` 返回 null。后续 `pi.cancel()` 未做空判断，抛出 NullPointerException，可能导致 `WidgetProvider.onDisabled()` 回调异常终止。

**修复建议**：`pi` 加空判断：
```java
if (pi != null) {
    pi.cancel();
}
```

**来源**：code-review-20260602-v2.md #2

---

### 6. [x] 多标签筛选与单标签筛选状态不一致

**位置**：`MainViewModel.java` L401-405（`recomputeSync` 过滤逻辑）、L241-248（`setMultiFilterTags`/`clearMultiFilter`）、L294-300（`isFiltering`/`getFilterTagId`）

**问题描述**：`mMultiFilterTagIds` 和 `mFilterTagId` 是两套独立的筛选状态变量。存在三条不一致路径：
1. `clearMultiFilter()` 仅清空 `mMultiFilterTagIds`，不清 `mFilterTagId`。如果单标签筛选也在生效中，调用方误以为清除后回到无筛选态，实际单标签筛选仍在。
2. `setFilterTagId(long)` 设置单标签时不主动清多标签，两套筛选可能同时有值但只有多标签生效，`getFilterTagId()` 返回值与 UI 实际筛选不一致。
3. `isFiltering()` 只检查单标签，多标签激活时返回 false。

**修复建议**：
- `clearMultiFilter()` 中同步 `mFilterTagId = -1`
- `setFilterTagId(long)` 中同步 `mMultiFilterTagIds = null`
- `isFiltering()` 同时检查 `isMultiFilterActive()`

**来源**：code-review-20260602-v2.md #1

---

### 7. [x] `HolidayJsonParser.extractValue()` 手动解析 JSON 字符串

**位置**：`HolidayJsonParser.java`

**问题描述**：`extractValue()` 方法手动解析 JSON 字符串，通过字符串分割和匹配提取字段值。遇到嵌套引号、转义字符或特殊格式（如字段值本身包含 `"` 或 `,`）会解析错误，导致假期数据填充失败。

**修复建议**：使用 `org.json.JSONObject` / `org.json.JSONArray` 替代手动字符串解析。

**来源**：code-review-20260602-v1.md #2

---

### 8. [x] 通知渠道缺少声音 / 振动 / 锁屏配置

**位置**：`ReminderNotifier.java` L29-39

**问题描述**：Android 14（API 34）+ 要求 `IMPORTANCE_HIGH` 渠道显式设置声音行为。当前仅为渠道设置了基本属性（名称、描述、取消角标），未设置 `setSound()`、`enableVibration()`、`setLockscreenVisibility()`。未配置时系统可能将渠道降级为默认重要性级别，导致提醒通知无声/无振动，或在勿扰模式/锁屏下不显示。

**修复建议**：
```java
channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
    new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .build());
channel.enableVibration(true);
channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
```

**来源**：code-review-20260602-v2.md #9

---

### important（建议修复）

### 9. [x] `IcsParser.isOffDay()` 使用 `indexOf` 在 JSON 中搜索日期，可能误判

**位置**：`IcsParser.java`

**问题描述**：`isOffDay()` 用 `indexOf` 在 JSON 字符串中搜索日期字符串。若某日期同时出现在其他字段（如 `festival.start`）中，可能误判该日期为假期日，导致工作日被标记为假日或被重复标记。

**修复建议**：解析 JSON 后在结构化数据（如 `Set<String>`）中精确查找，而非在原始 JSON 字符串中搜索。

**来源**：code-review-20260602-v1.md #3

---

### 10. [x] `TimelineTaskState` 构造函数中执行 Room 同步 I/O

**位置**：`TimelineTaskState.java`

**问题描述**：`TimelineTaskState` 内部类构造函数中调用 `getTaskByIdSync` / `countExecutionsSync` 等 Room 同步方法。作为数据类，构造时不应阻塞线程执行 I/O。Room 在主线程执行同步查询直接 crash（`IllegalStateException`），错误定位困难。

**修复建议**：将同步 I/O 调用移出构造函数，改为在调用方预先查询数据后通过参数传入，或改为工厂方法异步加载。

**来源**：code-review-20260602-v1.md #5

---

### 11. [x] `MainViewModel.recomputeSync()` 方法过长（约 140 行），职责过多

**位置**：`MainViewModel.java`

**问题描述**：`recomputeSync()` 集过滤、自动完成、降级清理、引擎计算、结果组装于一身，约 140 行。单一方法承担过多职责，难以单元测试和维护。

**修复建议**：按职责拆分为独立方法：`filterTasks()`、`applyAutoComplete()`、`computeEngineResult()`、`assembleDisplayItems()`。

**来源**：code-review-20260602-v1.md #4

---

### 12. [x] `recomputeSync()` 中查询与变更耦合

**位置**：`MainViewModel.java` L417-429

**问题描述**：`recomputeSync()` 负责"读取数据 → 排序 → 构造 EngineResult"，但方法内附带清理过期降级记录的写操作。`recompute()` 每分钟由 `TIME_TICK` 广播触发，意味着每分钟执行一次 DB 删除。查询与变更职责混杂，且调用方预期是"只读更新 UI"。

**修复建议**：将过期降级记录清理逻辑提取为独立方法（如 `cleanExpiredDegrades()`），放在每日刷新或应用启动时执行一次，而非每次 recompute 都遍历清理。

**来源**：code-review-20260602-v2.md #6

---

### 13. [跳过] `BaseRepository.assertNotMainThread()` 仅警告不阻断

> **跳过原因**：编译期拦不住，与 Room 自带 crash 差异不大，不值得改造自定义 Lint 规则。

**位置**：`BaseRepository.java` L20-24

**问题描述**：Sync 方法文档明确标注"不应在主线程调用"，但实现仅打 `Log.w` 不抛异常。Room 在主线程执行同步查询会直接 crash（`IllegalStateException`），crash 点发生在 Room 内部而非调用方，调用栈不清晰。如果开发者忽略了这条 Warning，定位问题反而更困难。

**修复建议**：Debug 构建中抛出 `IllegalStateException` 强制修复；Release 构建可保持 Log.w 行为。

**来源**：code-review-20260602-v1.md #6、code-review-20260602-v2.md #8

---

### 14. [x] `saveTask()` 完成回调线程不一致

**位置**：`TaskInputViewModel.java` L341

**问题描述**：`saveTask` 的完成回调 `onComplete` 在后台线程执行，而同项目中其他异步操作的回调均通过 `runOnUiThread` 投递到主线程（如 `MainViewModel.loadActiveSchedule()`、`MainViewModel.startTaskNow()`）。如果调用方（如 `TaskInputFragment`）在回调中执行导航或 UI 更新，会因线程不正确而异常。

**修复建议**：统一回调线程语义。在 `saveTask` 末尾改为 `runOnUiThread(onComplete)`，或建立项目级规范：所有 ViewModel 公开方法的回调一律在 UI 线程执行。

**来源**：code-review-20260602-v2.md #12

---

### 15. [x] `todayStartMs()` 在 3 个文件中重复定义

**位置**：
- `ReminderScheduler.java` L180-187
- `TaskScheduleViewModel.java` L200-206
- `TaskScheduleMatcher.java` L117-123

**问题描述**：完全相同的工具方法在三个不同类中各自定义：
```java
private static long todayStartMs() {
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTimeInMillis();
}
```
另外 `ReminderScheduler` 和 `TaskSchedulePostponeRepository` 中也有类似逻辑（v1 报告 #7）。违反 DRY 原则。

**修复建议**：提取到 `com.nearby.justnow.util.DateUtils` 公共工具类中。

**来源**：code-review-20260602.md #3、code-review-20260602-v1.md #7

---

### 16. [跳过] `ViewModelFactory` 使用 if-else 链做类型映射

> **跳过原因**：项目无新增 ViewModel 计划，分支数不会增长。改为 Map 注册后每个 VM 仍需一行注册代码，代码量不减少，仅从 if-else 换成 Map.put，未降低维护成本。

**位置**：`app/src/main/java/com/nearby/justnow/ui/base/ViewModelFactory.java` L31-57

**问题描述**：工厂方法使用 8 个 if-else 分支逐一匹配 ViewModel 类，每新增一个 ViewModel 子类就需要在此处增加一个分支。这是典型的"switch on type"反模式，随着项目增长维护成本递增。

**修复建议**：改为数据驱动方式，使用 `Map<Class<?>, Function<JustNowApplication, ? extends ViewModel>>` 注册。

**来源**：code-review-20260602.md #4

---

### 17. [x] 节假日数据源选择逻辑在两处独立实现

**位置**：
- `JustNowApplication.triggerHolidaySync()` L84-93
- `HolidaySyncWorker.selectSource()` L80-95

**问题描述**：根据设备地区选择 `HolidayDataSource` 的逻辑在两处独立实现。两处代码结构相似但不完全相同（`triggerHolidaySync` 顺序 CN→HK→MO，`selectSource` 顺序 HK→MO→CN）。若后续增加新数据源或调整优先级，必须两处同步修改，容易遗漏。

**修复建议**：提取 `HolidayDataSource` 工厂方法到共享位置（如 `HolidaySourceFactory.createForRegion(Context)`）。

**来源**：code-review-20260602.md #5

---

### 18. [x] ICS 解析器手动构建 JSON，存在 JSON 注入风险

**位置**：`app/src/main/java/com/nearby/justnow/data/holiday/IcsParser.java` L152-179

**问题描述**：`buildJson()` 使用 `StringBuilder` 手动拼接 JSON 字符串。虽对来源名称调用了 `escapeJson()`，但半年月数据也通过字符串拼接构建。若有任一字段包含未转义的特殊字符，生成的 JSON 将无效。当前数据来源格式确定暂无风险，但代码风格脆弱，未来修改容易引入 bug。

> 注：此问题与 #7（`HolidayJsonParser` 手动解析 JSON）互为镜像——前者是**构建** JSON 不规范，后者是**解析** JSON 不规范。

**修复建议**：使用 `org.json.JSONObject` / `org.json.JSONArray`：
```java
JSONObject json = new JSONObject();
json.put("year", year);
json.put("source", source);
JSONArray holidaysArray = new JSONArray();
for (String h : holidays) holidaysArray.put(h);
json.put("holidays", holidaysArray);
entity.dataJson = json.toString();
```

**来源**：code-review-20260602.md #6

---

### 19. [被动覆盖] `PeriodGroupRuleResolver` 实例化过于频繁

> **覆盖说明**：Repository 收归 Application 单例（F13），`PeriodGroupRuleResolver` 随之成为单例，所有调用方通过 `mApp.getPeriodGroupRuleResolver()` 获取同一实例。

**位置**：
- `MainViewModel` 构造函数 L178
- `TaskScheduleViewModel` 构造函数 L62
- `AlarmReceiver.handleAlarm()` 中 `new TimePeriodRepository(db)` 间接触发

**问题描述**：`PeriodGroupRuleResolver` 每个 ViewModel 实例都创建一个新实例。该 resolver 本身无状态（仅依赖传入的 `ActivePeriodGroup` 参数计算），可以做成单例或工具类，减少对象分配。

**修复建议**：将 resolver 改为 `Application` 级单例，缓存 `HolidayCacheManager` 和 `IWorkdayChecker`。

**来源**：code-review-20260602.md #7

---

### 20. [x] 新标签创建时颜色硬编码为 0（透明）

**位置**：`TaskInputViewModel.java` L279-283

**问题描述**：新建标签颜色固定为 `0`（全透明，ARGB = 0x00000000），视觉上不可见。后续编辑标签时用户需要手动选颜色，但首次使用时标签在界面中不会显示颜色标识。

**修复建议**：从预设颜色池中自动分配颜色，确保新标签有可辨识的默认颜色。`TagChipHelper` 已具备标签色板相关逻辑，可复用。

**来源**：code-review-20260602-v2.md #7

---

### suggestion（优化建议）

### 21. [x] Room 数据库 `exportSchema = false` 阻碍迁移

**位置**：`app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java` L63

**问题描述**：
```java
@Database(entities = { ... }, version = 1, exportSchema = false)
```
当前 `version = 1` 尚可接受，但一旦需要数据库迁移，缺少导出的 schema 文件会使迁移测试和自动迁移验证无法进行。

**修复建议**：将 `exportSchema` 改为 `true`，在 `app/build.gradle.kts` 中配置：
```kotlin
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
```
将 schemas 目录纳入版本控制。

**来源**：code-review-20260602.md #8、code-review-20260602-v2.md #5

---

### 22. [跳过] 全局屏幕方向锁定影响折叠屏 / 大屏体验

> **跳过原因**：暂不考虑折叠屏适配。

**位置**：`JustNowApplication.java` L59-67

**问题描述**：在 `ActivityLifecycleCallbacks.onActivityCreated` 中强制锁屏方向：
1. 折叠屏展开/折叠时应动态切换方向，锁定后体验割裂。
2. 分屏/多窗口模式下强制方向可能导致布局异常。
3. 回调全局生效，包括未来新增的 Activity。

**修复建议**：通过 `PackageManager.hasSystemFeature(FEATURE_SENSOR_HINGE_ANGLE)` 检测折叠屏并跳过锁定；或改为在需要锁定的具体 Activity 的 `AndroidManifest.xml` 中通过 `screenOrientation` 属性声明。

**来源**：code-review-20260602-v1.md #11、code-review-20260602-v2.md #11

---

### 23. [x] `formatMinute()` 在两处重复定义

**位置**：`MainViewModel.java`、`ReminderNotifier.java`

**问题描述**：`formatMinute()` 工具方法在两处独立定义，违反 DRY 原则。

**修复建议**：提取到 `DateUtils` 或 `FormatUtils` 公共工具类中。

**来源**：code-review-20260602-v1.md #8

---

### 24. [被动覆盖] `BaseTaskViewModel.completeRunningTaskSync()` 每次调用创建新 Repository 实例

> **覆盖说明**：F13 后 `BaseTaskViewModel` 全部改用 `mApp.getXxxRepository()`，不再 `new` Repository。

**位置**：`BaseTaskViewModel.java`

**问题描述**：`completeRunningTaskSync()` 内部 `new` Repository 实例，每次调用创建新对象增加 GC 压力。

**修复建议**：复用已有 Repository 实例或通过依赖注入共享。

**来源**：code-review-20260602-v1.md #9

---

### 25. [x] `JustNowApplication.onCreate()` 中裸线程创建

**位置**：`JustNowApplication.java`

**问题描述**：`new Thread("holiday-sync")` 裸线程创建，缺少统一的线程管理和异常处理。

**修复建议**：统一走线程池或 WorkManager（已有 `HolidaySyncWorker`）。

**来源**：code-review-20260602-v1.md #10

---

### 26. [跳过] 多数公开方法参数缺少 `@Nullable` / `@NonNull` 注解

> **跳过原因**：项目初期未建立规范，后期全量补注解成本高且收益有限。

**位置**：全局

**问题描述**：多数公开方法参数缺少 `@Nullable` / `@NonNull` 注解，调用方难以判断 null 是否允许，也难以利用 IDE 的 null-safety 检查。

**修复建议**：为公开 API 的方法参数和返回值统一添加 null-safety 注解。

**来源**：code-review-20260602-v1.md #12

---

### 27. [跳过] 搜索 SQL 使用前导通配符，全表扫描

> **跳过原因**：当前任务量百条内，已确认暂不处理。

**位置**：`TaskRepository.java` L179-180

**问题描述**：`LIKE '%keyword%'` 前导通配符阻止 SQLite 使用索引，每个 token 强制全表扫描。当前任务量较小（个人使用，通常百条以内）时性能可接受，但任务量增长后搜索响应时间线性增长。

**修复建议**：若任务量达到数百条以上，考虑迁移到 Room FTS4/FTS5 虚拟表。当前量级可暂不处理，建议在代码中加注释说明性能边界。

**来源**：code-review-20260602-v2.md #10

---

### 28. [x] 缺少对 Widget 更新、提醒通知流程、AlarmReceiver 的自动化测试

**位置**：`app/src/test/` 测试目录

**问题描述**：现有 25 个测试文件覆盖了数据层和部分 ViewModel，但以下关键流程缺少测试覆盖：
1. Widget 更新流程：`WidgetUpdateHelper` 的任务计算、RemoteViews 渲染。
2. 提醒通知：`ReminderNotifier.send()` 通知构建 + `AlarmReceiver` 的状态分支。
3. 闹钟调度：`ReminderScheduler.refreshToday()` 的 disable → cancel → register 流程。

**修复建议**：
- `AlarmReceiver` 逻辑分支可提取为包级可见静态方法，便于单元测试。
- `ReminderNotifier.send()` 可验证构造的 Notification 内容（使用 Robolectric `ShadowNotificationManager` 断言）。
- Widget 渲染逻辑可验证生成的 RemoteViews 文本和可见性。

**来源**：code-review-20260602-v2.md #13

---

### 29. [跳过] minSdk=33 限制了可安装设备范围

> **跳过原因**：项目初期已确定目标设备范围。

**位置**：`app/build.gradle.kts` L15

**问题描述**：`minSdk = 33`（Android 13）排除了 Android 12 及更低版本设备。截至 2026 年，Android 12 仍有可观的用户份额。如果 `SCHEDULE_EXACT_ALARM` 权限是选择 API 33 的原因——Android 12 也支持该权限（通过 `AlarmManager.canScheduleExactAlarms()` 检查）。

**修复建议**：考虑降低 `minSdk` 到 31（Android 12），对 `SCHEDULE_EXACT_ALARM` 做版本兼容处理。

**来源**：code-review-20260602-v2.md #14

---

### nit（微小优化）

### 30. [跳过] `AppDatabase` 双重检查锁定缺少局部变量优化

> **跳过原因**：微优化，无实际性能影响。

**位置**：`app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java` L101-127

**问题描述**：标准 DCL 模式建议使用局部变量保存 `volatile` 字段的引用，以减少不必要的 volatile 读取（约 25% 成本差异）。当前实际影响极小，属防御性优化。

**修复建议**：
```java
AppDatabase instance = sInstance;
if (instance == null) {
    synchronized (AppDatabase.class) {
        instance = sInstance;
        if (instance == null) {
            instance = Room.databaseBuilder(...).build();
            sInstance = instance;
        }
    }
}
return instance;
```

**来源**：code-review-20260602.md #9

---

### 31. [跳过] `WidgetPermissionGateActivity` 导出范围可能过宽

> **跳过原因**：该 Activity 通过 `android:configure` 由系统 Launcher 显式 Intent 启动，并非任意第三方可调。无 intent-filter 意味着普通隐式 Intent 无法匹配，实际攻击面极小。改为 `exported="false"` 反而可能在某些 Launcher 实现上中断 Widget 添加流程，代价高于收益。

**位置**：`app/src/main/AndroidManifest.xml` L75-78

**问题描述**：`WidgetPermissionGateActivity` 声明 `exported="true"` 但无 intent-filter，意味着其他应用可以通过显式 Intent 启动这个 Activity。Widget 配置流程中转 Activity 通常不需要外部访问。注意：如果 AppWidgetProvider 的配置 Activity 在 `appwidget-provider` XML 中通过 `android:configure` 声明，引用的 Activity 不需要 `exported="true"`。

**修复建议**：如果仅由本应用的 Widget 配置流程使用，改为 `exported="false"`。

**来源**：code-review-20260602.md #10

---

## 未处理项汇总

已修复项（含重构被动覆盖）共 23 条，不再列出。

| 编号 | 级别 | 问题简述 | 跳过原因 |
|------|------|----------|----------|
| 13 | important | `assertNotMainThread` 仅警告不阻断 | 编译期拦不住，与 Room 自带 crash 差异不大 |
| 16 | important | `ViewModelFactory` if-else 链 | 8 个 VM 远未到维护阈值 |
| 22 | suggestion | 全局方向锁定影响折叠屏体验 | 暂不考虑折叠屏适配 |
| 26 | suggestion | 缺少 `@Nullable`/`@NonNull` 注解 | 项目初期未建立规范，后期补注解成本高 |
| 27 | suggestion | 搜索 LIKE 前导通配符全表扫描 | 当前任务量百条内，已确认暂不处理 |
| 29 | suggestion | minSdk=33 限制安装范围 | 项目初期已确定目标设备范围 |
| 30 | nit | AppDatabase DCL 局部变量优化 | 微优化可忽略 |
| 31 | nit | WidgetPermissionGateActivity 导出范围 | `android:configure` 声明，Launcher 系统链路依赖 |

---

## 总体建议

### 短期（本迭代）

1. **Edge-to-Edge**：所有 Activity 添加 `enableEdgeToEdge()`，配合 WindowInsets API 做好系统栏适配
2. **IME 适配**：为有输入的 Activity 添加 `adjustResize`
3. **ANR 风险修复**：`WidgetFilterStore.commit()` 改 `apply()`，`cancelMinuteBoundary` 加空判断
4. **NPE 修复**：`checklistContentChanged` 用 `Objects.equals`，`HolidayJsonParser` 用标准 JSON 库
5. **通知渠道完善**：补全声音、振动、锁屏显示配置
6. **筛选状态一致性**：统一 `mMultiFilterTagIds` 和 `mFilterTagId` 的互斥逻辑

### 中期（后续迭代）

7. **工具类收敛**：提取 `DateUtils`、`FormatUtils`、`HolidaySourceFactory` 等公共方法
8. **工厂模式优化**：`ViewModelFactory` 改用数据驱动注册
9. **recomputeSync 拆分**：按职责拆分为独立方法，解耦查询与变更
10. **线程回调统一**：`saveTask` 回调改 UI 线程，建立项目级规范

### 长期（技术债务）

11. **JSON 工具统一**：禁用 StringBuilder 手写 JSON 和手动解析 JSON，统一使用 `org.json` 或 Gson
12. **测试补充**：Widget 更新、通知、AlarmReceiver 流程加测试覆盖
13. **null-safety 注解**：公开 API 统一添加 `@Nullable`/`@NonNull`
14. **Room schema 导出**：开启 `exportSchema`，为未来迁移做准备
15. **线程管理统一**：裸 `new Thread` 改为线程池或 WorkManager

---

## 最终处置记录

**排查日期**：2026-06-02

**处置概览**：

| 处置类型 | 数量 | 说明 |
|----------|------|------|
| 直接修复 | 21 | 已通过代码修改解决 |
| 重构被动覆盖 | 2 | #19 PeriodGroupRuleResolver 实例化收归 Application 单例；#24 BaseTaskViewModel 改用 app.getXxxRepository |
| 跳过 | 8 | 详见表中各条跳过原因 |

**跳过明细**：

| 编号 | 跳过原因 |
|------|----------|
| 13 | `assertNotMainThread` 仅警告不阻断。编译期拦不住，与 Room 自带 crash 差异不大，不值得改造自定义 Lint 规则 |
| 16 | `ViewModelFactory` if-else 链。当前仅 8 个 VM，远未到维护阈值，数据驱动注册反降低可读性 |
| 22 | 全局方向锁定影响折叠屏体验。暂不考虑折叠屏适配 |
| 26 | 缺少 `@Nullable`/`@NonNull` 注解。项目初期未建立规范，后期全量补注解成本高且收益有限 |
| 27 | 搜索 LIKE 前导通配符全表扫描。当前任务量在百条内，已确认暂不处理 |
| 29 | minSdk=33 限制安装范围。项目初期已确定目标设备范围，非本次审查可决策 |
| 30 | AppDatabase DCL 局部变量优化。微优化，无实际性能影响 |
| 31 | `WidgetPermissionGateActivity` 导出范围。`android:configure` 声明依赖 Launcher 系统链路，改为 `exported="false"` 可能导致 Widget 配置页无法启动 |

**处置结论**：31 条问题全部处置完毕，项目当前无待修复审查问题。
