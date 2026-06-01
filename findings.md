# 研究发现

## 2026-05-30 全项目代码审查 — 关键发现

> 审查报告：[docs/code-review-20260530.md](../docs/code-review-20260530.md)

（原内容保留）

> 审查报告（47 项）→ 排除 5 项误报 → 分 6 批修复 24 项。详见 [progress.md](progress.md) 2026-05-30 条目。

**数据层修复**：
- `HolidayJsonParser.extractValue()` 布尔值解析 Bug：`isOffDay: true` 被截取为 `true,` → `"true".equals()` 永不成立。改用 `"isOffDay": true` 字符串直接匹配
- `PeriodGroupRuleResolver.matchesSync()` 节假日分支恒 `return false`，与 `participatesInTimelineSync` 及异步路径不一致 → 补 `matchesHolidayDataSync()` 调用
- 6 处"先删后插"写操作非原子，崩溃可致数据不一致 → 统一包裹 `mDb.runInTransaction()`
- `HolidayCacheManager.save()` 查比写非原子 → 改为 DAO `@Transaction default` 方法单次原子写入
- `BaseRepository` 从空壳重构为提供 `protected mDb` 字段 + `assertNotMainThread()` 警告
- `HolidaySyncWorker` IOException 区分：`UnknownHostException`/`SocketTimeoutException` → retry，其余 → failure

**数据层退回的修复点**：
- HTTP 404/403 区分未落地：需改 `HolidayDataSource` 接口让 fetch() 可抛出 HTTP 状态码异常
- SQL 列名 `Detail`（B9）确认为误报：代码中已为小写 `detail`
- `sync` 方法保护由 `throw IllegalStateException` 改为 `Log.w`：Robolectric 测试中主 Looper 即测试线程

**UI/广播/Widget 修复**：
- `AlarmReceiver` 3 处：`ACTION_POSTPONE` 补 executor 分发；`ACTION_START_TASK`/`ACTION_CHECK_ALARM` 加 `goAsync()` WakeLock；`canPostpone()` 消除临时对象
- 3 处 `require*()` 异步崩溃加 `isAdded()`/`getView()` null 守卫
- `MainViewModel.recompute()` CAS 防重入 → CAS 排队模式，避免静默丢更新
- 两 Adapter 非静态内部类 → 静态，移除隐式 Activity 引用
- `WidgetConfigureResultBridge` 移除 `WeakReference`，改强引用 + `onDestroy` 可靠清理
- `WidgetUpdateHelper`：`cancelMinuteBoundary` 补 `FLAG_NO_CREATE`；`DisplayEngine` 改静态单例复用

**误报排除**：
- W2 RemoteViews 跨线程：单一所有权转移，实际线程安全
- W3 原判误报 → 用户质疑后复核：设备重启/清后台 `onDestroy` 不被调用，WeakRef 有实效 → 列入修复
- E1 `mEngineFailed` 非线程安全：所有调用场景单线程
- I7 线程池 shutdown：Android 进程模型，无需
- I17 VM 持有 Application：`AndroidViewModel` 标准模式，确认误报
- B9 `Detail` 拼写：已为小写

**I15/I16 架构收口（F7）**：
- `AppDatabase.sDatabaseWriteExecutor` 私有化，API 收口为 `execute()` / `runInBackground()`
- 新建 `BaseViewModel`（mApp/mDb/runInBackground/runOnUiThread），8 个 ViewModel 统一继承
- Fragment/Activity 的 10 处直接 executor 调用移到 ViewModel，Repository 层统一走 `mDb.runInBackground()`
- 同方法内重复 `getInstance()` 收为局部变量（AlarmReceiver、WidgetUpdateHelper 等）

## 2026-05-29 四象限降级恢复

> 详见：[modules/quadrant-degrade.md](modules/quadrant-degrade.md)

降级周期（`degrade_period`）放 `tasks` 表作为任务持久属性；降级临时状态（`task_quadrant_degrade`）独立表，自清理。降级与 schedule 无关。

## 2026-05-28 四象限任务管理 Toolbar/状态栏颜色收尾

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — Toolbar/状态栏颜色收尾

- `MainActivity` 作为 `nav_graph` 宿主统一管理 App chrome，比 `QuadrantTaskListFragment.onDestroyView()` 恢复全局颜色更稳定。
- Android 15+ 目标应用状态栏透明，`setStatusBarColor` 对状态栏不再可靠；单象限页通过 `AppBarLayout` 背景 + `fitsSystemWindows=true` 让状态栏区域透出同一象限色。
- Android 14 及以下仍保留 legacy `Window.setStatusBarColor()`，覆盖旧系统非透明状态栏。
- `compileDebugJavaWithJavac` 最终通过；期间遇到一次 AGP `mergeDebugResources` 增量缓存 NPE，`clean` 后恢复。

## 2026-05-27 四象限任务管理

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md) — 研究发现、技术决策

- 现有代码基础（DisplayEngine 无缓存、MainFragment 无 ViewPager2、TaskDao.delete 物理删除）
- 设计决策（ViewPager2 嵌入、computeByQuadrant、不滚动/可滚动、PopupWindow 筛选、MODE_VIEW）

## 2026-05-26 桌面 Widget 实现

> 详见：[modules/widget.md](modules/widget.md) — 研究发现、技术决策

- 架构决策：ReminderDetailActivity 独立、系统原生 GridLayout + addView 行模板、固定列宽对齐、AlarmManager 整分钟刷新
- 后续决策：任务点击走主界面统一语义；数据变更主动刷新经 `DataChangeDispatcher` 解耦；Widget 标签筛选按 `appWidgetId` 持久化并在引擎前预过滤
- Widget 添加权限门禁：Launcher 只可靠接收 `android:configure` Activity 的 `RESULT_OK` / `RESULT_CANCELED`；`BroadcastReceiver.startActivity()` 会被后台启动限制影响。`MainActivity` 是 `singleTop`，直接作为 `startActivityForResult` 目标不稳，因此用 `WidgetPermissionGateActivity` 承接 Launcher 结果，用 `WidgetConfigureResultBridge` 接收主界面权限流程结果
- 已修复 10 个 Bug（RemoteViews 类白名单限制、字号违规、MaterialComponents 不可用、高度 dp 误算、空状态占位、无标签列对齐等）
- RemoteViews 框架限制实测确认（类白名单、布局属性限制）
- 遗留：标签筛选需真机/桌面 Launcher 复验，多 Widget 独立筛选需实机确认

## 2026-05-25 优先标签状态行

> 详见：[modules/tag.md](modules/tag.md) — 优先标签状态行

- 主界面右侧栏顶部优先标签生效状态标注
- 生效态/暂停态双视觉 + `mSuppressPriority` 会话级标记
- 后台恢复：ProcessLifecycleOwner + `resetFiltersAndPriority()`

## 2026-05-25 时间段编辑约束（第二版）

> 详见：[modules/time-period.md](modules/time-period.md) — 编辑约束

- 步进按钮（+/-15min）+ PopupWindow 浮层滚轮 + 磁盘分区联动
- 约束：早上 >= 06:00，晚上 <= 23:00，午休/晚餐 >= 1h，其余 >= 30min
- 移除 PeriodTimePickerDialog.java（第一版跨版本闪退）

## 2026-05-24 安排任务模块重设计修复

> 详见：[modules/task-execution.md](modules/task-execution.md) — 研究发现、技术决策

关键决策：TaskScheduleMatcher 权威实现（bit0=周日）、TaskStartGuard 统一校验、UNIQUE 保留+insert 前清理、v10->v11 DROP+CREATE、权限前置到入口、cancel 先于 disable。

## 2026-05-23 安排任务模块重设计

> 详见：[modules/task-execution.md](modules/task-execution.md) — 研究发现、技术决策

核心决策：一个任务一条有效安排 + 字段统一表达 + DAO 变更 + 闹钟调度三步骤。

## 2026-05-21 APP 图标设计

> 详见：[modules/app-icon.md](modules/app-icon.md) — 研究发现、技术决策

设计目标、方向取舍、关键决策（配色、安全区、任务条形态、不参与 Themed Icons）。

## 2026-05-20 精确闹钟权限崩溃与统一权限引导

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md) — 精确闹钟权限崩溃

`SecurityException` 根因（Android 12+ 运行时权限）+ PermissionHelper 统一方案。

## 2026-05-20 任务详情页系统

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — 研究发现、技术决策

设计约束、数据模型、附加模块、完成前确认机制、统一入口、导航。

## 2026-05-20 编辑页样式修复与录入/编辑拆分

> 详见：[modules/task-edit.md](modules/task-edit.md) — 研究发现、技术决策
> 详见：[modules/task-input.md](modules/task-input.md) — 研究发现、技术决策

专注时长布局方案（两行 RadioButton）、清单编辑器防删问题、按钮样式规范、图标选择、拆分原因。

## 2026-05-18 主界面非时段直接开始保护

> 详见：[modules/task-execution.md](modules/task-execution.md)

UI 状态复盘：非时段将"安排"切为主按钮、"开始"置灰；琐碎任务只保留置灰"开始"。

## 2026-05-18 任务录入四象限保存崩溃修复

> 详见：[modules/task-input.md](modules/task-input.md) — 研究发现、技术决策

无标签统一使用 null（D022）。Robolectric runtime dependency 镜像配置。

## 2026-05-17 时间段组编辑对话框布局优化

> 详见：[modules/time-period.md](modules/time-period.md) — 编辑对话框

已确认视觉方向 + 技术决策（布局+少量显示格式化、保留 MM-dd）。统一样式整理 + AAPT 编译问题记录。

## 2026-05-16 节假日数据月度下载频率控制

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 研究发现、技术决策

架构决策（fetch() 返回 Entity + parser 填 Entity + 月度标记 + 天数比较）。

## 2026-05-15 节假日数据驱动工作日判断

> 详见：[modules/holiday-data.md](modules/holiday-data.md)

共用缓存+共用流程、补班差异、JSON key 改名、测试矩阵（23 用例）。

## 2026-05-15 任务执行规则收敛

> 详见：[modules/task-execution.md](modules/task-execution.md)

琐碎任务完全不进入左侧时间线、不能安排、完成后当天不列入右侧栏。

## 2026-05-15 androidTest 测试维护

> 详见：[modules/testing.md](modules/testing.md)

现有 androidTest 修正（匹配底部添加按钮、双屏录入、点击象限即保存新流程）。

## 2026-05-15 无节假日数据兜底逻辑

> 详见：[modules/holiday-data.md](modules/holiday-data.md)

非 CN/HK/MO 地区禁用 WORKDAY 组，策略回退 STANDARD_WEEK。

## 2026-05-12/13 主界面工作

> 详见：[modules/ui-design.md](modules/ui-design.md) — 底部时段栏

底部时段栏设计确认（紧凑->非紧凑）、黄金比例双栏。

## 品牌体系 -> [详细文档](modules/brand.md)

企业：邻近科技 / Nearby Tech。产品：恰恰有事 / Just Now。标语：刚好，手边有事 / Right here, right now。

## 项目初始状态

- 全新 Android 项目，包名 `com.nearby.justnow`
- Java + View-based 模板，SDK 36，minSdk 33，targetSdk 36

## 技术调研

### 依赖选型
- ViewBinding、Navigation Component、Room、Material 3
- 图表库：待评估 MPAndroidChart

### 节假日数据方案
- 中国境内：holiday-cn；境外：Google Calendar API（预留）
- 按年缓存，月度重新检查
- WorkManager 保障获取不被退出打断

### Room 数据库表设计（初步）
| 表名 | 用途 | 核心字段 |
|------|------|----------|
| `tasks` | 任务主表 | id, content, tag_id, quadrant, focus_minutes, created_at |
| `tags` | 标签表 | id, name, color |
| `time_periods` | 时间段配置 | id, period_type, start_time, end_time |
| `task_executions` | 执行记录 | id, task_id, date, status, period_slot |
| `holiday_config` | 节假日配置 | date, name, is_holiday |

## 已确认决策
1. 节假日：中国 holiday-cn；境外 Google Calendar API（预留）；按年缓存
2. "琐碎"任务：二阶段点击
3. 搜索：仅匹配任务内容；标签填写时关键字推荐
4. 通知：设定了时间的任务需系统通知
5. 常规/工作日默认 5 段时段
6. 晚上四象限反转；午休+晚餐优先"琐碎"
7. Widget 与 APP 复用 M4 智能展示引擎

## 模块拆分文档

模块已拆分为独立文档，存放于 `modules/` 目录：
- [品牌体系](modules/brand.md)
- [检索引擎](modules/search-engine.md) — M1
- [智能展示引擎](modules/smart-display.md) — M4
- [时间段计算](modules/time-period.md) — M3
- [剩余时间计算](modules/time-remaining.md)
- [节假日数据](modules/holiday-data.md) — M8
- [桌面 Widget](modules/widget.md) — M7
- [数据统计](modules/stats.md) — M6
- [UI 设计](modules/ui-design.md)
- [APP 图标](modules/app-icon.md)
- [测试策略](modules/testing.md)
- [提醒延迟](modules/reminder-delay.md) — D020
- [提醒详情页](modules/reminder-detail.md) — M5
- [标签](modules/tag.md)
- [任务编辑页](modules/task-edit.md)
- [任务录入](modules/task-input.md)
- [任务执行](modules/task-execution.md) — M5
- [四象限任务管理](modules/quadrant-task-manage.md) — M9

## 待解决问题
- holiday-cn 是否含国务院公告抓取实现
- 港澳 ICS 数据中假期名称是否匹配中文/英文关键词
- Nager.Date API 接入（其他地区）
