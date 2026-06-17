# Code Review Report — 2026-06-17

## 审查范围

- **提交**：
  - `1b6a551` refactor: 提取 TaskFilterHelper 统一主界面和 Widget 业务逻辑
  - `7aea726` fix(app-action): 修复 hint 不为空时分身标识丢失
  - `9313596` feat(app-action): 支持应用分身查询与标识显示
- **模块**：widget、main、task-input、reminder-detail
- **变更规模**：+610 / -78 行（3 个代码提交）

## 结果概要

| 级别 | 数量 | 决策 |
|------|:----:|------|
| important | 1 | 误报 1 |
| suggestion | 2 | 误报 1 / 已修复 1 |
| nit | 4 | 已修复 4 |

**决策**：全部处理完毕。5 项已修复，2 项误报。

## 发现

### [x] #1 [important] TaskFilterHelper 线程安全问题 — 误报

**文件**：`app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java`

**问题**：`mPendingCompute` 和 `mHasPendingDelayed` 无同步保护。`compute()` 通过 `runInBackground()` 从后台线程调用，存在竞态窗口。

**误报判定**：`compute()` 调用方 `recomputeSync()` 和 `updateWidget()` 都在后台线程，`mPendingFilterTagIds` 的竞态最坏结果是多算一次，不会崩溃。防抖重写后 `mDelayedCompute` 是 final 的，消除了原来的 Runnable 引用竞态。确认日期：2026-06-17

---

### [x] #2 [suggestion] getDisplayItems() 可能在主线程执行数据库操作 — 误报

**文件**：`app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java:116-122`

**问题**：`mFilteredTasks == null` 时会调用 `computeFilteredTasks(null)`，内含同步数据库查询。若在主线程调用会 ANR。

**误报判定**：当前所有调用方都在后台线程，不会主线程 ANR。null fallback 是防御性设计，保留合理。确认日期：2026-06-17

---

### [x] #3 [suggestion] 防抖逻辑复杂且有竞态 — 已修复

**文件**：`app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java:79-110`

**问题**：`mPendingCompute` 在 lambda 内被设为 null，与主线程 Handler 检查存在竞态。整体防抖逻辑比标准 debounce 模式复杂。

**修复**：重写为标准防抖 — 首次即时执行 + 1秒兜底，防抖窗口内重置为 500ms。`mDelayedCompute` 从构造器创建一次（final），`mPendingFilterTagIds` 字段替代 lambda 捕获。确认日期：2026-06-17

---

### [x] #4 [nit] 重复创建 DisplayEngine — 已修复

**文件**：`app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java:216`

**问题**：`computeDisplayItems()` 每次创建新 `DisplayEngine()`，该类无状态可复用。

**修复**：提升为 `private final DisplayEngine mDisplayEngine = new DisplayEngine()` 成员变量。确认日期：2026-06-17

---

### [x] #5 [nit] 异常处理缺少日志 — 已修复

**文件**：`app/src/main/java/com/nearby/justnow/ui/taskinput/AppLaunchCatalogCache.java:163`

**问题**：catch 块改为 `Exception` 但未记录日志，反射调用异常调试困难。

**修复**：添加 `android.util.Log.e("AppLaunchCatalogCache", "loadInBackground failed", e)`。确认日期：2026-06-17

---

### [x] #6 [nit] 分身标识硬编码 — 已修复

**文件**：
- `AppLaunchCatalogCache.java:151`
- `ReminderDetailActivity.java:452`

**问题**：`"（分身）"` 两处硬编码，未走 strings.xml 国际化。

**修复**：改为 `getString(R.string.s_app_clone_suffix)`，4 语言 strings.xml 均已添加（英文 `(Clone)`，中/繁中/繁港 `（分身）`）。确认日期：2026-06-17

---

### [x] #7 [nit] findByPackageName 和 findByPackageNameAndUserId 未被调用 — 已修复

**文件**：`AppLaunchCatalogCache.java:87, 99`

**问题**：两个方法均无调用方，属死代码。

**修复**：两个方法已删除。确认日期：2026-06-17

---

## 重审项

### 20260531 #6 — MainViewModel 与 WidgetUpdateHelper 重复计算引擎逻辑

**原状态**：已解决

**重审结论**：确认已解决。两侧通过 `TaskFilterHelper` 妥善对齐。

**分析**：

共用逻辑（TaskFilterHelper.computeFilteredTasks）：
- 任务获取（getAllActiveTasksSync）
- 自动完成过期任务（TaskExecutionAutoCompleter）
- 今日隐藏过滤（ChoreHiddenTodayStore）
- 标签过滤（filterTagIds 参数）
- 琐碎任务隐藏（hideCompletedChoresForToday）
- 时段状态计算（TimeRemainingCalculator.compute）
- 引擎计算（mDisplayEngine.compute）

合理差异（各自保留）：
- 主界面：timeline、statusText、priorityTagIds、assembleDisplayItems
- Widget：compact 模式、renderWidgetTasks/renderWidgetStatus

确认日期：2026-06-17

---

## 未处理项汇总

无。全部 7 项已处理完毕（5 项修复，2 项误报）。
