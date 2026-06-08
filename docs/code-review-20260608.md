# Code Review Report — 2026-06-08

- 审查范围：2026-06-06 之后全部改动（跨应用 Intent 捕获、笔记分享附加模块、APP 跳转 UI 收尾、任务编辑页 Chip 紧凑模式、DB 迁移、测试补充）
- 审查时间：2026-06-08
- 发现问题：12 个（important 3 / suggestion 7 / nit 2）
- 表扬项：3 个
- 审核时间：2026-06-08

---

## important

### [x] #1 硬编码中文字符串 "分钟"

`CapturePickerActivity.TaskAdapter.formatTaskLine()` 和 `TaskInputFragment.SearchAdapter.formatTaskLine()` 均硬编码 `"分钟"`。违反项目国际化规范（四语言支持）。

应提取到 `strings.xml`，通过 `Resources` 获取。

**文件：**
- `app/src/main/java/com/nearby/justnow/ui/appactioncapture/CapturePickerActivity.java` ~line 336
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputFragment.java` ~line 214

**审核结果：** 已修复。两处均改为调用 `TaskDisplayHelper.formatTaskLine()`，内部使用 `resources.getString(R.string.s_focus_minutes_format, task.focusMinutes)` 替代硬编码。`TaskDisplayHelper` 为新提取的共享工具类，同时包含 `highlightTitle()` 方法。

---

### [x] #2 `AppLaunchCatalogCache.loadIfNeeded()` TOCTOU 竞态

`loadIfNeeded()` 先通过 `getCurrentStatus()`（synchronized）读取 `mCurrentStatus`，再调用 `reload()`，`reload()` 内部再次在 `mLock` 下读取 `mCurrentStatus`。两次读取之间存在时间窗口，另一线程可能改变状态。

建议：在 `loadIfNeeded()` 内直接在 `mLock` 下读取并判断，或直接调用 `reload()`（`reload()` 自身已有守卫）。

**文件：** `app/src/main/java/com/nearby/justnow/ui/taskinput/AppLaunchCatalogCache.java` lines 56-60

**审核结果：** 已修复。`loadIfNeeded()` 现直接调用 `reload()`，`reload()` 内部在同一 `synchronized (mLock)` 块中检查状态并设置 LOADING，消除 TOCTOU。`reload()` 的守卫条件也扩展为 `LOADING || LOADED`。

---

### [x] #3 `TaskInputActivity.applyCaptureExtras()` 中 `postDelayed(150)` 时序脆弱

`loadTaskId > 0` 分支用 `postDelayed(150)` 延迟导航到编辑页。`loadTaskForEdit()` 在后台线程执行，150ms 是经验值，慢设备或高负载下数据可能未就绪。

建议：改用 ViewModel 回调 / LiveData observer，在加载完成后触发导航。

**文件：** `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputActivity.java` lines 96-100

**审核结果：** 已修复。`postDelayed(150)` 替换为 `viewModel.getTaskLoaded().observe(this, loaded -> { ... })`，在 `loadTaskForEdit()` 完成后通过 LiveData 信号驱动导航，不再依赖时序猜测。

---

## suggestion

### [x] #4 `CapturePickerActivity` 未调用 `enableEdgeToEdge()`

该 Activity 手动通过 `ViewCompat.setOnApplyWindowInsetsListener` 处理 insets，未调用 `enableEdgeToEdge()`，不绘制到系统栏后面。若项目统一走 edge-to-edge 方向，应补齐；当前手动处理也可接受。

**文件：** `app/src/main/java/com/nearby/justnow/ui/appactioncapture/CapturePickerActivity.java`

**审核结果：** 误报。`CapturePickerActivity` 已通过 `ViewCompat.setOnApplyWindowInsetsListener` 处理 insets，这是标准的 edge-to-edge 适配方式之一。Android 15 强制 edge-to-edge，但不要求必须调用 `enableEdgeToEdge()`，手动处理 insets 同样有效。已记录到 `docs/code-review-ignore.md`。

---

### [x] #5 `AppLaunchCatalogCache.clear()` 使用 `setValue()` 无主线程保护

`clear()` 当前仅从 `MainActivity.onResume()`（主线程）调用，安全。但方法本身未强制主线程约束，若将来从后台线程调用会导致 `setValue()` 崩溃。建议改用 `postValue()` 或添加 `@MainThread` 注解。

**文件：** `app/src/main/java/com/nearby/justnow/ui/taskinput/AppLaunchCatalogCache.java` line 80

**审核结果：** 已修复。`clear()` 方法已添加 `@MainThread` 注解，明确约束调用线程。

---

### [x] #6 `CapturePickerActivity.loadTasks()` 标签名映射冗余拷贝

代码从 `tagRepo.getAllTagsMapSync()` 获取 `Map<Long, TagEntity>`，再遍历提取 `tag.name` 到新 `Map<Long, String>`。中间实体 Map 多余。可考虑在 `TagRepository` 上提供 `getAllTagNamesMapSync()` 直接返回名称映射。

**文件：** `app/src/main/java/com/nearby/justnow/ui/appactioncapture/CapturePickerActivity.java` lines 168-178

**审核结果：** 已确认关闭。代码仍使用 `getAllTagsMapSync()` + 手动提取，但这是单次后台调用、数据量小（标签数通常个位数），额外 Map 创建开销可忽略。属正当设计取舍，不做强制优化。

---

### [x] #7 `AppLaunchCatalogCache.reload()` 每次创建裸 `Thread`

`reload()` 每次 `new Thread(...)` 创建线程。建议改用单线程 Executor 或复用 `AppDatabase.execute()` 模式，保持项目线程管理一致性。

**文件：** `app/src/main/java/com/nearby/justnow/ui/taskinput/AppLaunchCatalogCache.java` line 70

**审核结果：** 已修复。`reload()` 改为 `AppDatabase.execute(() -> loadInBackground(generation))`，复用项目统一的数据库写线程池。

---

### [x] #8 `TaskInputViewModel.resolvePackageFromIntentUri()` 使用全限定类名

方法内使用 `android.content.Intent`、`android.content.pm.PackageManager` 等全限定名而非 import，与文件其余部分风格不一致。

**文件：** `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java` lines 413-425

**审核结果：** 已修复。`resolvePackageFromIntentUri()` 改为使用 import 的 `Intent`、`PackageManager`、`ResolveInfo`，文件顶部已有对应 import 语句。

---

### [x] #9 `formatTaskLine` / `highlightTitle` 重复代码

`CapturePickerActivity.TaskAdapter` 和 `TaskInputFragment.SearchAdapter` 含近乎相同的 `formatTaskLine()` 和 `highlightTitle()` 方法。建议提取为共享工具类（如 `TaskDisplayHelper`）。

**文件：**
- `app/src/main/java/com/nearby/justnow/ui/appactioncapture/CapturePickerActivity.java`
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputFragment.java`

**审核结果：** 已修复。两处均改为调用 `TaskDisplayHelper.formatTaskLine()` 和 `TaskDisplayHelper.highlightTitle()`（`app/src/main/java/com/nearby/justnow/ui/base/TaskDisplayHelper.java`），彻底消除重复代码。

---

### [x] #10 `ReminderDetailActivity.launchDeepLink()` 非 http scheme 无 data 时静默走原 intent

若 `deepLink` 为非 http scheme（如 `myapp://something`），`Intent.parseUri()` 可能产生无 `getData()` 的 intent。fallback 仅对 http/https 前缀触发 `ACTION_VIEW`，其他 scheme 会尝试直接启动解析后的 intent，可能抛异常。当前 catch-all + toast 可接受，但值得留意。

**文件：** `app/src/main/java/com/nearby/justnow/ui/reminderdetail/ReminderDetailActivity.java` lines 520-532

**审核结果：** 已修复。`launchDeepLink()` 改为调用 `UriParser.parse(deepLink)`（`app/src/main/java/com/nearby/justnow/ui/base/UriParser.java`），集中处理各种 scheme 的解析和 fallback 逻辑，异常统一走 toast 提示。

---

## nit

### [x] #11 `showAddDialog` / `showEditDialog` 共用模板代码

`TaskInputAppActionSheet` 中 `showAddDialog()` 和 `showEditDialog()` 均 inflate `dialog_app_action_add.xml`、绑定 cancel/confirm、show dialog。可提取公共 setup 方法减少重复。

**文件：** `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputAppActionSheet.java`

**审核结果：** 已修复。`showEditDialog()` 和 `showAddDialog()` 均改为委托给 `showDialog(@Nullable TaskAppAction existing)`，通过 `existing != null` 区分编辑/新增模式，消除重复代码。

---

### [x] #12 已废弃的 `getAdapterPosition()` 调用

`TaskInputAppActionSheet.AppActionAdapter` 和 `TaskInputNoteShareSheet.NoteShareAdapter` 使用 `holder.getAdapterPosition()`（已废弃）。应改用 `holder.getBindingAdapterPosition()`。

**文件：**
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputAppActionSheet.java`
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputNoteShareSheet.java`

**审核结果：** 已修复。两处均改为 `holder.getBindingAdapterPosition()`。

---

## praise

### P1 `TaskInputViewModel.save()` 模块切换逻辑清晰

`normalizeSelectedModuleType()` + 旧模块清理模式结构良好。选中模块但无有效条目时正确返回 null；编辑模式切换模块类型时正确清理旧子表。`mOriginalModuleType` 跟踪是合理做法。

### P2 `AppLaunchCatalogCache` 基于 generation 的过期加载保护

`mLoadGeneration` 机制正确处理了 `clear()` 与后台加载并发场景 -- 过期加载的 generation 检查阻止其覆盖新状态。

### P3 新模块保存/清理逻辑测试覆盖充分

`TaskInputViewModelTest` 新增 7 个测试，覆盖空/非空 APP 操作和清单、编辑清空场景。`saveAndDrain()` 的 `CountDownLatch` + `ShadowLooper` 模式是测试异步 ViewModel 操作的可靠做法。

---

## 审核汇总

| 状态 | 数量 |
|------|:--:|
| 已修复 | 10 |
| 误报 | 1 (#4) |
| 确认关闭 | 1 (#6) |
| **仍需处理** | **0** |

> 生成日期：2026-06-08
> 审核日期：2026-06-08
