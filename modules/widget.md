# 桌面 Widget 模块

> 对应 task_plan.md M7 · 设计文档：[2026-05-26-widget-design.md](../docs/superpowers/specs/2026-05-26-widget-design.md)

# 阶段规划、决策记录 （拆分自 task_plan.md）

## 定位和功能描述

Android App Widget，在桌面展示当前时段推荐任务，底部固定增加任务入口。复用 M4 智能展示引擎，Widget 与 APP 共享同一 Room 数据库。

当前实现为两列 RemoteViews：顶部状态栏 + 两列任务列表 + 空状态。任务项布局与主界面右侧栏统一（`item_task_content.xml`），Widget 通过 `LinearLayout` 行容器 + 配对逻辑实现两列排列。

## 整体规划和决策

### 阶段 1 — ReminderDetailActivity 拆分（依赖）
- [x] 新建 `ReminderDetailActivity`，从 `ReminderDetailFragment` 迁移业务逻辑
- [x] 更新所有跳转点：`MainFragment` / `MainViewModel.resolveAndHandleTaskClick` / `AlarmReceiver` -> `startActivity(ReminderDetailActivity, taskId)`
- [x] `nav_graph.xml` 移除 `reminderDetailFragment` 目标及 action

### 阶段 2 — Widget 布局资源
- [x] 新建 `res/layout/widget_justnow.xml`（单列布局）
- [x] 新建 `res/layout/item_widget_task_row.xml`（单条任务行模板）
- [x] `res/xml/appwidget_provider.xml` 按 3x3 配置，开启 resize
- [x] 新增字符串 `s_widget_empty`（4 语言）

### 阶段 3 — JustNowWidgetProvider 核心逻辑
- [x] `WidgetUpdateHelper`：提取 `updateWidget(context, manager, widgetId)` 公共逻辑
- [x] `JustNowWidgetProvider.onUpdate()` / `onAppWidgetOptionsChanged()`
- [x] 任务行 RemoteViews 渲染
- [x] PendingIntent 配置：任务条目 -> `MainActivity.ACTION_WIDGET_TASK_CLICK` 后走 `MainViewModel.resolveAndHandleTaskClick()`；"+"按钮 -> `TaskInputActivity`
- [x] 标签点击筛选：`tv_task_tag` 单独 PendingIntent，per-widget 保存筛选 tagId，再次点击当前标签取消筛选

### 阶段 4 — 刷新机制
- [x] 新建 `MinuteBoundaryReceiver`（`BroadcastReceiver`）
- [x] `WidgetUpdateHelper.scheduleNextMinuteBoundary(context)`：有精确闹钟权限时用 `AlarmManager.setExact()` 整分钟唤醒；无权限时用普通 `set()` 兜底，实际触发时间由系统调度
- [x] `JustNowWidgetProvider`：onEnabled/onUpdate -> schedule；onDisabled -> cancel
- [x] APP 侧主动刷新：数据层调用 `DataChangeDispatcher.notifyTaskDataChanged()`；Widget 侧 `WidgetDataChangeNotifier` 统一合并刷新

### 阶段 5 — 编译验证与清理
- [x] `compileDebugJavaWithJavac` 通过
- [x] 确认 `ReminderDetailFragment` 及关联 layout 无残留引用后删除
- [ ] 标签筛选真机/桌面 Launcher 点击验证

### 技术要点
- 使用 Android App Widget + RemoteViews
- Widget 端直接读取 Room 数据库（同一进程），无需 IPC
- 布局需适配不同尺寸（小 2x2 / 中 3x3 / 大 4x4）
- Widget 高度使用 `AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT` 的 dp 值，不再按 px 除 density
- Widget 非时段顶部状态固定两行显示：“休息中”与“下个时段”之间换行；不再依赖 Launcher 宽度/最大格数判断
- Widget 顶部剩余时间：`<=60` 分钟保持分钟展示；`>60` 分钟按小时展示，整小时为 `x小时`，非整小时为 `x.y小时`
- Widget 任务行执行中态：`executingStartMs > 0 && executingEndMs == 0` 时，整行显示浅色高亮背景
- Widget 添加时通过 `WidgetPermissionGateActivity` 作为配置中转检查 `SCHEDULE_EXACT_ALARM`：已授权则返回 `RESULT_OK` 并初始化 widget；未授权则打开 `MainActivity` 的 widget 权限模式，由主界面弹权限引导，结果经 `WidgetConfigureResultBridge` 回到 Gate，再返回 Launcher 的 `RESULT_OK` / `RESULT_CANCELED`
- 任务变更主动刷新通过 `data.observer.DataChangeDispatcher` 解耦，数据层不感知 Widget

### 接口
```java
public class JustNowWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds);
}
```

### 依赖
- M4 智能展示引擎
- 时间段计算模块
- Room 数据库（直读）
- 数据变更通知边界：`DataChangeNotifier` / `DataChangeDispatcher`

# 研究发现、技术决策 （拆分自 findings.md）

> 详见：[findings.md](../findings.md) — 2026-05-26 桌面 Widget 实现

### 架构决策
- **ReminderDetailActivity 独立**：从 Fragment 拆为独立 Activity，Widget 和 APP 内部统一跳转
- **任务列表方案演进**：`addView` 逐条拼接 -> `TableLayout`（RemoteViews 禁止）-> 系统原生 `GridLayout` 容器 + `LinearLayout` 行模板（当前方案）
- **列宽对齐**：行模板固定 4 列，标签列 `minWidth=44dp`，无标签任务保持空 TextView 占位；时长列固定 `68dp`，标题列 `weight=1`
- **刷新策略**：有权限时 `AlarmManager.setExact(RTC_WAKEUP)` 整分钟唤醒；无精确闹钟权限时用普通 `set()` 兜底，仍依赖系统调度和数据变更主动刷新
- **降级**：`FallbackListProvider` 类不存在，由 `WidgetUpdateHelper.buildFallbackList()` 内建实现
- **统一任务点击语义**：Widget 任务行点击进入 `MainActivity.ACTION_WIDGET_TASK_CLICK`，再交给主界面 `resolveAndHandleTaskClick()`；未执行任务走开始/安排流程，执行中任务按主界面规则分流
- **标签筛选**：`WidgetFilterStore` 按 `appWidgetId` 持久化单标签筛选；点击 `#标签名` 设置筛选，再次点击当前标签取消。筛选在调用 `DisplayEngine.compute()` 前预过滤任务，不改引擎签名
- **数据变更主动刷新**：新增中立 `DataChangeNotifier` / `DataChangeDispatcher`，Repository 只通知“任务数据已变化”；`WidgetDataChangeNotifier` 作为 Widget 侧实现调用 `WidgetUpdateHelper.updateAllWidgets(context)` 并做短延迟合并刷新
- **状态栏展示细节**：剩余时间超过 60 分钟时按小时压缩展示；非时段状态固定换行分隔“休息中”和“下个时段”，避免 Launcher 宽度判断误差
- **添加权限中转**：`android:configure` 指向 `WidgetPermissionGateActivity`，避免 `BroadcastReceiver.startActivity()` 被后台启动限制拦截。Gate 不展示业务配置；缺少精确闹钟权限时打开 `MainActivity`，主界面展示权限引导并通过 `WidgetConfigureResultBridge` 回传授权结果，Gate 负责向 Launcher 返回添加结果

### 已修复 Bug
1. 原始 `<View>` 被 RemoteViews 禁止 -> 改用 `<TextView>` 无文本
2. `<TableLayout>` / `<TableRow>` 被禁止 -> 改用 `<LinearLayout>` 固定列宽 + `<GridLayout>` 对齐
3. AndroidX `GridLayout` 导致崩溃 -> 必须用系统原生 `android.widget.GridLayout`
4. Widget 字号违规 -> 改为项目规范三档（22/18/16sp）
5. 时长格式化 -> 纯分钟（"90 分钟"）
6. MaterialComponents TextAppearance 不可用 -> parent 改为 `android:TextAppearance.Material.*`
7. Widget 高度 options 误按 px 处理 -> 改为直接使用 dp 值
8. 空状态与任务列表同时占位 -> 无任务/异常时隐藏任务容器，显示空状态
9. 无标签任务列对齐回归 -> 无标签时保留标签 TextView 可见但空文本，维持标签列宽
10. Widget 整分钟闹钟无权限风险 -> `PermissionHelper.hasExactAlarmPermission()` 保护

### RemoteViews 框架限制（实测确认）

**类白名单**：RemoteViews 只允许 inflate 系统原生类（`android.widget.*`），AndroidX 同名组件不被支持。允许：`FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout`（系统原生）, `TextView`, `ImageView`, `ImageButton`, `Button`, `ListView`, `GridView`, `StackView`。禁止：原始 `View`, `TableLayout`, `TableRow`, AndroidX 包下所有组件, 自定义 View。

**布局属性限制**：`stretchColumns`, `shrinkColumns`, `layout_column` 仅 `TableLayout` 支持但该类被禁止。`?attr/xxx` 主题属性在 Launcher 进程无 App 主题。动态设 `layout_width`/`layout_height` 不支持。

### 当前限制 / 待验证
1. 标签筛选、再次点击取消、多 Widget 独立筛选仍需真机/桌面 Launcher 验证。
2. 无精确闹钟权限时无法整分钟更新剩余时间，按系统周期和数据变更刷新兜底。
3. Widget 添加权限中转主界面化已完成代码改动，仍需桌面 Launcher 实测：未授权时应进入主界面权限引导，授权成功后保留 widget，取消或返回未授权时移除 widget。

# 进度日志 （拆分自 progress.md）

> 详见：[progress.md](../progress.md) — 2026-05-26 桌面 Widget 实现（4 步分发）

### 已落实
- [x] 系统原生 `GridLayout`（`columnCount="1"`）+ `LinearLayout` 任务行 + `addView` 逐条拼接
- [x] 列对齐：4 列（色标 3dp + 标签 minWidth=44dp + 时长 68dp + 标题 weight=1）
- [x] 剩余时间（纯分钟单位）+ 非时段 + 空状态
- [x] AlarmManager 整分钟刷新（MinuteBoundaryReceiver）
- [x] "+" 按钮 -> TaskInputActivity
- [x] ReminderDetailActivity 独立 + 样式去 MaterialComponents 化
- [x] 编译 + 218 单测通过
- [x] Widget 任务点击走主界面统一 `resolveAndHandleTaskClick()` 语义
- [x] 返回栈复用：Widget 入口使用明确 action/flags，`MainActivity` singleTop
- [x] APP 侧主动刷新接入统一数据变更通知边界
- [x] Widget 标签点击筛选：per-widget 持久化、点击设置/取消、筛选态 `tag_active` + 下划线
- [x] Widget 加载 + 基础列对齐（真机验证通过）
- [x] Widget 顶部剩余时间超过 60 分钟时改为小时展示
- [x] Widget 非时段状态固定两行显示
- [x] Widget 执行中任务行浅色背景高亮
- [x] Widget 添加时通过配置中转页打开主界面申请精确闹钟权限；未授权取消添加

### 遗留问题
- [x] 标签筛选真机/桌面 Launcher 点击验证
- [ ] 多 Widget 独立筛选真机验证

### 2026-05-29 统一布局 + 两列 + 尺寸调整（第二轮迭代）
- [x] Widget 尺寸固定 4x2（`targetCellWidth=4` `targetCellHeight=2`，minWidth 250dp / minHeight 110dp）
- [x] 仅纵向缩放（`resizeMode=vertical`），`minResizeHeight=110dp` 禁止比预设短
- [x] 跨天逻辑同步底部栏（`buildRestingStatusText` 加入 `isTomorrow`，跨天时复用 `s_tomorrow`）
- [x] 统一任务项布局：`item_task_content.xml` 共享于主界面 RecyclerView（MaterialCardView 包裹）和 Widget RemoteViews
- [x] 两列布局：`widget_task_row_container.xml` 行容器 + `renderTaskItems` 每 2 个 item 配对一行
- [x] Widget 高度获取：优先 `OPTION_APPWIDGET_SIZES`，回退时竖屏用 `MAX_HEIGHT` / 横屏用 `MIN_HEIGHT`
- [x] `TASK_ROW_HEIGHT_DP`、顶栏高度、内边距改为读 dimen 资源，dimension 值静态缓存
- [x] Widget textAppearance 改为显式 `textSize` + `textColor`（MaterialComponents `?attr/xxx` 在 Launcher 进程不可用）
- [x] `item_widget_task_row.xml` 删除，`buildTaskRow()` 直接 inflate `item_task_content.xml`
- [x] 估算常量 `PADDING_DP`/`TOP_BAR_HEIGHT_DP` 删除，dimension 值静态缓存
- [x] `widget_content_padding` 调小，新增 `widget_action_bar_height`、`widget_action_bar_margin_bottom` 用于计算可用空间
- [x] 两列加间隙（`ll_row_left` `layout_marginEnd`）
- [x] 执行中高亮保留（`ll_task_item` 背景色切换）

### RemoteViews 框架限制（补充）
- `android.view.View` 不允许 inflate，色条必须用 `TextView` + `background`
- `GridLayout` + `addView` 不按 `columnCount` 自动分列（无法设 `layout_column`/`layout_row` 参数）
- `layout_weight` 在 RemoteViews `LinearLayout` 中可用
- `OPTION_APPWIDGET_SIZES` 在部分 Launcher 返回 `null`，需回退 `MIN_HEIGHT`/`MAX_HEIGHT`
- `<include>` 在 RemoteViews 自定义 `LayoutInflater` 中不支持

### 已知问题（待修）
- [ ] `QuadrantRatioFilter` 4:2:2:1 比例过滤导致 widget 实际返回 item 数 < maxItems。用户确认"算法跟需求不符，有 bug"，待后续修复

**状态**：🔨 已实现
