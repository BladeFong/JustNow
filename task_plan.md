# 任务规划

## 当前聚焦：平板端任务扫码传图系统（2026-08-18）

> 设计文档：[docs/superpowers/specs/2026-08-18-qr-task-photo-upload-design.md](docs/superpowers/specs/2026-08-18-qr-task-photo-upload-design.md)
> 实现计划：[docs/superpowers/plans/2026-08-18-qr-task-photo-upload.md](docs/superpowers/plans/2026-08-18-qr-task-photo-upload.md)
> 详见：[modules/tablet-flower-rewards.md](modules/tablet-flower-rewards.md)

**定位**：支持户外活动等不便携带平板的场景下，手机在不装任何额外 APP 的前提下，通过局域网扫码打开平板内置极简 H5 页面将照片（可多选）极速传输给平板并与任务关联，写入 `task_photos` 并联动更新花朵奖励。采用 Android 原生内置 `com.sun.net.httpserver.HttpServer`、双端 5 秒对等心跳感知与系统休眠联动，手机端 Canvas 智能高清压缩 (<3MB)。

**状态**：已完成落地、真机验证与审查复核。

---

## 历史聚焦：任务完成统一流程 + 拍照条件统一判断（2026-07-26）

> 设计文档：[docs/superpowers/specs/2026-07-26-task-completion-unified-design.md](docs/superpowers/specs/2026-07-26-task-completion-unified-design.md)
> 实现计划：[docs/superpowers/plans/2026-07-26-task-completion-unified-plan.md](docs/superpowers/plans/2026-07-26-task-completion-unified-plan.md)
> 详见：[modules/tablet-flower-rewards.md](modules/tablet-flower-rewards.md)

**定位**：统一 `completeTaskFlow`（正常/琐碎完成）和 `shortCompleteFlow`（短完成）为单一 `completeTaskUnified` 方法，参数控制时间线记录；封装 `isChildTask`（`isTablet && iconName != null`）统一拍照条件判断；短完成写 status=3 执行记录，拍照列表可查。移除 `ChoreHiddenTodayStore.hideForToday`。

**状态**：设计文档已完成，实现计划已完成，待进入实现。

---

## 当前聚焦：蓝牙通知同步集成与桌面推送（2026-07-24）

> 设计文档：[docs/superpowers/specs/2026-07-24-ble-notification-sync-integration-design.md](docs/superpowers/specs/2026-07-24-ble-notification-sync-integration-design.md)
> 详见：[modules/ble_sync.md](modules/ble_sync.md)

**定位**：将开源的 `BleNotificationSync` SDK 通过 JitPack 远端依赖引入。在 `MainFragment` 的右上角 Toolbar 菜单添加设备同步管理入口，通过 `DeviceUtils.isTablet()` 在平板设备上隐藏。同时，在 `ReminderNotifier` 中拦截 Native 通知发送逻辑，使用 SDK 的 `sendNotification(builder, notificationId, callback)` 方法，确保 Native 弹出与 BLE 推送同步进行，完美保留原有通知的可控注销特性。

**状态**：功能代码（JitPack依赖引入、MainFragment 菜单与平板隐藏、ReminderNotifier 通知代理发送）已全部开发完毕并通过编译验证。

---

## 历史聚焦：平板端横竖屏放开与儿童兴趣活动图标适配脑暴（2026-07-16）

> 设计文档：[docs/superpowers/specs/2026-07-16-tablet-orientation-and-child-icons-design.md](docs/superpowers/specs/2026-07-16-tablet-orientation-and-child-icons-design.md)
> 详见：[modules/tablet-adapt.md](modules/tablet-adapt.md)

**定位**：优化平板端方向锁定逻辑（允许转屏），通过资源限定符自适应主界面右侧网格列数（横屏 3 列，竖屏 2 列），并在任务实体中扩展 `icon_name` 字段（支持 10 个内置儿童兴趣图标），升级 Room 数据库至版本 8 并添加 Migration，同时更新新建任务页 of 图标选择器和主页卡片布局。

**状态**：设计方案脑暴完成，Spec 文档已编写完毕，已与用户达成一致。待下一步编写具体实现计划并落实。

---

## 历史聚焦：主界面时间线 M2 视觉优化（2026-07-16）

> 设计文档：[docs/superpowers/specs/2026-07-16-timeline-m2-optimization-design.md](docs/superpowers/specs/2026-07-16-timeline-m2-optimization-design.md)
> 实现计划：[docs/superpowers/plans/2026-07-16-timeline-m2-optimization.md](docs/superpowers/plans/2026-07-16-timeline-m2-optimization.md)
> 详见：[modules/timeline_m2.md](modules/timeline_m2.md)

**定位**：优化主界面时间线任务卡片样式，已完成任务卡片恢复为有灰色背景和灰色边框的外观，并在左侧绘制加宽为 2 倍（8dp）的填充式灰色状态栏，且文本对齐统一至 16dp；执行中任务卡片高亮为象限背景全背景色；同时解决设备上的精确闹钟崩溃，并增设 onResume 时的精确闹钟权限引导流程。

**状态**：全部代码优化及修复已完美落地，编译成功并已验证。

---

## 代码审查（2026-06-17）

审查6月12日之后的修改，发现 7 个问题（1 important / 2 suggestion / 4 nit）。#1/#2 确认为误报，#3-#7 已修复。重审 20260531 #6（主界面 vs Widget 业务对齐）确认已通过 TaskFilterHelper 妥善解决。

> 审查报告：[docs/code-review-20260617.md](docs/code-review-20260617.md)

---

## 当前聚焦：TaskFilterHelper 提取业务逻辑（2026-06-17）

**定位**：提取主界面右侧栏和 Widget 共用的任务数据获取和过滤逻辑到 `TaskFilterHelper`，解决业务代码重复问题。

**关键决策**：
- 单实例模式：`TaskFilterHelper.getInstance(JustNowApplication)`
- 防抖机制：实时执行 + 1 秒延迟再执行一次，保证最终一致性
- 缓存机制：中间数据存储为成员变量，通过 getter 方法获取
- 后台线程：`compute()` 在 `AppDatabase.execute()` 中执行，避免主线程访问数据库

**状态**：编译通过，待真机验证。

---

## 当前聚焦：应用分身支持（2026-06-16）

> 设计文档：[docs/superpowers/specs/2026-06-16-app-clone-support-design.md](docs/superpowers/specs/2026-06-16-app-clone-support-design.md)
> 实现计划：[docs/superpowers/specs/2026-06-16-app-clone-support-plan.md](docs/superpowers/specs/2026-06-16-app-clone-support-plan.md)

**定位**：APP 跳转功能支持小米等设备的应用分身，应用列表能识别分身应用，详情页动态显示标识。

**关键决策**：
- 查询：反射调用 `queryIntentActivitiesAsUser()` + `getIdentifier()` 遍历所有用户
- 存储：复用 `deepLink` 字段，存储带 `S.launch_user_id` extra 的 intent URI
- 显示：分身应用名后加"（分身）"，详情页解析 deepLink 动态添加
- 跳转：使用系统选择器（普通应用无 `INTERACT_ACROSS_USERS` 权限）

**状态**：编译通过，待真机验证。

---

## 当前聚焦：智能展示策略 YAML 配置化（2026-06-10）

> 设计文档：[docs/superpowers/specs/2026-06-10-display-policy-yaml-design.md](docs/superpowers/specs/2026-06-10-display-policy-yaml-design.md)
> 实现计划：[docs/superpowers/specs/2026-06-10-display-policy-yaml-plan.md](docs/superpowers/specs/2026-06-10-display-policy-yaml-plan.md)
> 详见：[modules/smart-display.md](modules/smart-display.md)

**定位**：将智能展示引擎优先级顺序、时间容差、四象限比例和专注时长最大档位配置化，用户可导入、编辑、导出 YAML，主界面、Widget、四象限共用同一策略源。

**关键决策**：
- YAML 字段名固定英文，默认/导出内容带中文注释
- 优先级只配置排序规则顺序，不暴露权重
- 用户 YAML 原文保存到 App 私有文件
- `focus_max_minutes` 只允许 `120/150`；升档不限制，降档不能低于未归档任务中已有最大专注时长
- 专注时长档位按 `FOCUS_SLOT_MINUTES` 动态生成，不新增 `s_150min` 等固定字符串

**状态**：设计文档已提交，实现计划已完成，待进入实现。

---

## 当前聚焦：安排任务推荐化重构（2026-06-06）

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-recommend-design.md](docs/superpowers/specs/2026-06-06-schedule-recommend-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**定位**：旧版安排任务"占用"时间槽，边界情况多。新版改为"到点优先推荐"，不占用时间槽，不截断剩余时间，不阻断其他任务。

**关键变更**：
- 移除 `applyScheduleTruncation`、`effectiveRemaining`/`effectiveEndMinute`、底部栏安排提示、时间线安排块、槽位占位判断
- 推荐引擎：安排任务到点后 30 分钟内优先排序（weight -= 300）
- 通知选项：忽略→取消优先级；延迟30分钟→再通知+顺延（仅一次）；开始→取消优先级
- 跨时段：只取消已过时段内的安排任务优先级
- 槽位表：30 分钟粒度，每行 4 格

**状态**：编译通过，431 测试 0 新增失败。实现完成。

---

## 已完成：安排任务感知的剩余时间（2026-06-06）

> 设计文档：[docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md](docs/superpowers/specs/2026-06-06-schedule-aware-remaining-time-design.md)
> 详见：[modules/time-remaining.md](modules/time-remaining.md)

**定位**：剩余时间计算未考虑时段内已安排的任务块，导致液体色块、底部栏、展示引擎、任务开始守卫四处不一致。

**关键决策**：
- `TimeRemainingCalculator.compute()` 新增重载接收今日安排列表，内部 `applyScheduleTruncation` 截断到最近安排开始
- `PeriodStatus` 新增 `effectiveRemaining` / `effectiveEndMinute`，原字段不动
- `TaskScheduleRepository` 新增 `volatile CopyOnWriteArrayList` 缓存
- `TimelineView` 液体色块改用 `effectiveEndMinute` 截断，数据源统一

**状态**：编译通过，用户验证通过。（注：后续被推荐化重构方案替代）

---

## 已完成：忽略交互修复+对话框分流+TYPE_ONCE 超时+跳过表简化（2026-06-06）

> 审查报告：[docs/code-review-20260605.md](docs/code-review-20260605.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**定位**：审查 6/5 改动 + 修复回归 + 新增忽略交互 + 跳过表重构。

**关键决策**：
- `update()` 无条件设 `enabled=true` 回归：TYPE_ONCE 忽略改用 `disableScheduleSync`
- 对话框分流：右侧栏 `showTaskDetailDialog`（开始/安排/取消）与时间线 `handleTimelineScheduledTaskClick`（开始/忽略/取消）分离
- `isScheduleActionable`：`enabled + matchesToday`，超时由 disable 机制处理
- TYPE_ONCE 超时：`disableExpiredOnceSchedules` 通过 TIME_TICK + onResume 触发，deadline = `scheduledTime + focusMinutes`，DAO JOIN tasks 获取 focusMinutes
- 跳过表简化：每次忽略一行 → 每个 schedule 一行（lastSkippedDateMs + skipCount），DAO 改 upsert，`ReminderScheduler` 去掉 365 天遍历，数据库迁移 v3→v4

**状态**：编译通过。审查 3 个发现全部修复，0 误报。

---

## 已完成：安排调整&忽略交互（2026-06-05）

> 设计文档：[docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md](docs/superpowers/specs/2026-06-05-schedule-adjust-ignore-design.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**定位**：任务变为一任务一安排后，补齐已有安排的"调整安排"入口、安排页恢复时间、通知忽略和时间线已安排任务点击。

**关键决策**：
- 有当天可命中安排时，主界面按钮显示"调整安排"，否则显示"安排"
- 安排页恢复已有安排时，槽位选择放在 `onTypeSelected()` 后恢复，避免被类型切换重置
- 通知新增"忽略"操作：单次安排禁用，重复安排写跳过记录并调度下次
- 时间线已安排任务点击走独立弹窗，不复用右侧栏安排入口

**状态**：编译通过，82 相关测试通过。后续 2026-06-06 审查修复已更新跳过表和对话框分流实现。

---

## 已完成：安排保存链路 upsert + 通知刷新（2026-06-05）

> 设计文档：[docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md](docs/superpowers/specs/2026-06-05-task-schedule-save-upsert-design.md)
> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)

**定位**：修复安排保存无响应/闪退、保存后主界面不刷新、到点无通知。

**关键决策**：
- `TaskScheduleRepository.insert()` 改为按 `taskId` 串行 upsert，仓库层兜底一任务一安排
- 新建安排后回填真实 `schedule.id`，调度层继续使用该主键传递广播和通知 ID
- `MainViewModel` 观察安排表变化，`TimelineBuilder` 缓存签名纳入 schedule 数据
- 单页 `TaskScheduleActivity` 保存成功后用 `finish()` 关闭

**状态**：编译通过，用户复测通过。

---

## 已完成：连续安排通知回归修复 + 当日槽粒缓冲（2026-06-05）

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

**定位**：修复接连保存安排时后续闹钟未注册；降低选择过近槽位导致保存时触发时间已过的风险。

**关键决策**：
- 保存回调顺序恢复为 `scheduleTaskAlarm()` → `refreshCaches()` → `onComplete.run()`
- 页面返回回调不能阻断闹钟注册
- `TaskScheduleFragment` 提取 `SLOT_INTERVAL_MINUTES = 10`
- 当天槽位禁用增加一个槽粒缓冲，当前槽粒和下一个槽粒均不可选

**状态**：代码已落盘提交，未单独编译；后续编译通过覆盖相关代码。

---

## 已完成：6月4日安排重构、Widget打磨、5项Bug修复与审查收口（2026-06-04~2026-06-05）

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/time-period.md](modules/time-period.md)、[modules/widget.md](modules/widget.md)、[modules/smart-display.md](modules/smart-display.md)、[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)、[modules/reminder-detail.md](modules/reminder-detail.md)、[modules/task-input.md](modules/task-input.md)

**定位**：安排功能关联时段组落地后，补齐时段组选项过滤、Widget 视觉打磨、5 项回归修复和代码审查修复。

**关键决策**：
- 安排功能砍掉 MONTHLY，关联时段组，时段组关闭时安排不触发
- 安排页时段组选项只显示未来 3 个月可命中的时段组
- Widget 顶部栏可跳主界面，根布局圆角半透，紧凑档字号/行高 dimen 化
- 时间线缓存签名增加执行中状态，已完成专注任务显示真实耗时
- Edge-to-edge 输入法遮挡通过 IME bottom inset 处理
- 6/4 审查修复跨年窗口、switch 映射、颜色资源和残留资源清理

**状态**：相关提交已落盘，编译和测试状态见各模块进度文件。

---

## 已完成：桌面 Widget 实现（2026-05-26）

> 设计文档：[docs/superpowers/specs/2026-05-26-widget-design.md](docs/superpowers/specs/2026-05-26-widget-design.md)
> 模块文档：[modules/widget.md](modules/widget.md)
> 详情页：[modules/reminder-detail.md](modules/reminder-detail.md)

按 5 个功能阶段完成：ReminderDetailActivity 拆分、Widget 布局资源、Provider 核心逻辑、刷新机制、验证与清理。全部编译通过 + 218 单元测试 0 失败。关键决策：ReminderDetailActivity 独立、系统原生 GridLayout + addView 行模板、AlarmManager 整分钟刷新。

后续修复已落地：Widget 任务点击走主界面统一 `resolveAndHandleTaskClick()`、返回栈复用、精确闹钟权限保护、高度 dp 计算修正、空状态占位修正、APP 侧主动刷新通过 `DataChangeDispatcher` 解耦、标签点击 per-widget 筛选、添加时配置中转页打开主界面引导精确闹钟权限且未授权取消添加。待真机/桌面 Launcher 复验标签筛选和多 Widget 独立筛选。

## 已完成：安排页 UI 细节调整（2026-05-24）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 类型切换控件、底部保存按钮

切换类型不抖动（minHeight=42dp）、每周 chip 字号统一到 Body（18sp）、保存按钮圆角填充。

## 已完成：安排页面选择器视觉统一（2026-05-24）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 选择器视觉统一

三种类型选择器对象 chip 样式按钮：每周 TextView + state_selected、每月月历 Dialog（GridLayout 7x5）、DAILY 类型不留空白。

## 已完成：安排任务模块重设计（2026-05-23）

> 详见：[modules/task-execution.md](modules/task-execution.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

关键决策：一个任务一条有效安排（UNIQUE）、字段统一表达、槽位视图共用、闹钟调度三步骤、双权限门禁。D024 统一匹配/校验收口。

## 已完成：安排模块后续排查修复（2026-05-24）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 排查修复

4 Pack 修复：TaskScheduleMatcher 权威实现 + 双权限前置 + UNIQUE 保留（insert 前清理）+ 双层缓存。`TaskScheduleMatcherTest` 34 用例。全量 218 用例 0 失败。

## 已完成：任务详情页系统（2026-05-20）

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md)
> 详见：[modules/task-edit.md](modules/task-edit.md)
> 详见：[modules/task-input.md](modules/task-input.md)

任务详情页 Fragment + Markdown 渲染 + todo 清单 / APP 跳转附加模块 + 统一入口 + 完成前确认机制。录入/编辑页面拆分为独立 Fragment。

## 已完成：任务详情页 UI 修复 + 按钮均匀分布（2026-05-22）

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — UI 修复、按钮均匀分布

Toolbar 复用 Activity、APP 项视觉对齐、整行点击跳转。底部按钮 Space 等权重均匀分布。APP 项跳转后 click handler 顺序修正防重复点击。

## 已完成：D020 提醒延迟（2026-05-19/20）

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

全链路 AlarmManager + 常驻通知 + +15/+30 动态延迟。权限引导：精确闹钟崩溃修复 + PermissionHelper 统一工具。53 个新增测试用例。

状态/遗留：安排页时间未约束落入时段内、自动插入安排任务到时间线、通知点击详情页交互细节。

## 已完成：任务录入四象限保存崩溃修复（2026-05-18）

> 详见：[modules/task-input.md](modules/task-input.md)

无标签任务 `tag_id` 写 null 而非 0（D022）。loadTaskForEdit 清空旧标签草稿。

## 已完成：主界面非时段直接开始保护（2026-05-18）

> 详见：[modules/task-execution.md](modules/task-execution.md)

非时段右侧栏任务可查看详情但不能开始，开始按钮禁用并显示原因。专注时长任务"安排"切为主按钮。

## 已完成：时间段组编辑对话框布局优化（2026-05-17）

> 详见：[modules/time-period.md](modules/time-period.md)

日期完整年份显示、时间行固定时间区、统一浅蓝编辑块+黑色文字。底层仍保存 MM-dd。

## 已完成：节假日数据月度下载频率控制（2026-05-16）

> 详见：[modules/holiday-data.md](modules/holiday-data.md)

fetch() 返回 Entity + throws IOException；lastSyncMonth 月度标记；天数比较决定是否写入；移除 on-demand fetch。

---

## 当前聚焦：四象限任务管理（2026-05-27）

> 设计文档：[docs/superpowers/specs/2026-05-27-quadrant-task-manage-design.md](docs/superpowers/specs/2026-05-27-quadrant-task-manage-design.md)
> 模块文档：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)

**定位**：主界面四象限全任务浏览 + 单象限筛选删除。

**关键决策**：ViewPager2 嵌入 MainFragment（Page 0 主界面 + Page 1 四象限概览）；DisplayEngine.computeByQuadrant 按需计算跳过比例截取；PopupWindow 下拉多选筛选；TaskDao.delete 物理删除。ReminderDetailActivity MODE_VIEW 区分执行/查看模式。

**状态**：5 阶段完成；2026-05-28 收尾修复单象限页面 Toolbar/系统状态栏跟随象限色，返回主界面恢复默认主题色。

---

## 已完成：四象限降级恢复（2026-05-29）→ 已被任务完成模式替代（2026-07-10）

> 原设计文档：[docs/superpowers/specs/2026-05-29-quadrant-degrade-design.md](docs/superpowers/specs/2026-05-29-quadrant-degrade-design.md)
> 新设计文档：[docs/superpowers/specs/2026-07-10-task-completion-mode-design.md](docs/superpowers/specs/2026-07-10-task-completion-mode-design.md)
> 实现计划：[docs/superpowers/plans/2026-07-10-task-completion-mode.md](docs/superpowers/plans/2026-07-10-task-completion-mode.md)
> 详见：[modules/task-completion-mode.md](modules/task-completion-mode.md)

**定位**：四象限降级策略已废弃，改为任务完成模式——每个任务完成一次当天即隐藏、次日重现；周/月/年模式叠加配额控制。

**关键决策**：废弃 `degrade_period` + `task_quadrant_degrade`，新增 `completion_mode`（0=日/1=周/2=月/3=年）+ `quota` + `task_completion_counter` 表；QuadrantFragment 降级 chip 行改为完成模式 chip 行；详情页底部新增趋势图。

**状态**：设计文档和实现计划已完成，待进入实现。

## 当前聚焦：任务完成模式（2026-07-10）

---

## 代码审查：重复业务逻辑排查（2026-05-31）

> 审查报告：[docs/code-review-20260531.md](docs/code-review-20260531.md)

**审查重点**：排查因缺少统一回调设计导致的重复业务逻辑

**发现问题**：8 个（高优先级 3 个，中优先级 4 个，低优先级 1 个）
- 任务完成流程重复：三处独立实现相似逻辑
- 短完成流程重复：两处实现相同的 < 15min 完成逻辑
- 闹钟取消逻辑重复：七处重复调用
- 归档任务流程重复：两处实现相同逻辑
- Holiday 数据源重复：三个数据源实现相同 HTTP 请求模式
- Widget 和主界面重复计算引擎逻辑
- 清单状态确认检查重复
- 时段状态文本构建重复

**建议方案**：引入服务层（Service Layer）统一封装跨 Repository 的业务流程，使用回调接口处理差异化需求

**状态**：待重构

---

## 待实现：全局横竖屏锁定（2026-05-27）

> 设计文档：[docs/superpowers/specs/2026-05-27-screen-orientation-lock-design.md](docs/superpowers/specs/2026-05-27-screen-orientation-lock-design.md)

- [x] `JustNowApplication.onCreate` 中 `registerActivityLifecycleCallbacks`：手机->PORTRAIT，平板->LANDSCAPE
- [x] `compileDebugJavaWithJavac` 通过
- **Status:** complete

---

## 品牌 -> [详细文档](modules/brand.md)

企业：邻近科技 / Nearby Tech。产品：恰恰有事 / Just Now。标语：刚好，手边有事 / Right here, right now。

## 项目概述

- **核心理念**：记录任务缓急，但不催促安排任务；专注记录任务，完成当下事，选择想做的事。
- **与传统 Todo 的区别**：不堆积任务列表、不设截止日期催促、任务属性在执行时才揭晓、基于四象限+时间段+专注时长的智能展示

## 核心功能模块

### 测试策略 -> [详细文档](modules/testing.md)

编译验证优先；低成本业务规则使用 JVM 单元测试或已有 Robolectric 基础设施。UI 验证不作为强制收尾要求。

### M1：任务录入（已打磨） -> [进展](modules/search-engine.md)

关键字检索 + 分词匹配 + 标签推荐 + 专注时长设定。详见 [检索引擎模块](modules/search-engine.md)、[任务录入模块](modules/task-input.md)。

### M2：四象限矩阵（已打磨）

紧急/重要四个象限，任务编辑完成后必须选择象限，按优先级排序。

### M3：时间段系统（已实现） -> [进展](modules/time-period.md)

6 种时段组 + 默认时段填充 + 编辑约束（步进按钮 + PopupWindow 浮层滚轮 + 磁盘分区联动）。

### M4：智能展示引擎（已打磨） -> [进展](modules/smart-display.md)

四象限 4:2:2:1 比例 + 标签优先展示。Widget 与 APP 复用同一引擎。标签筛选/优先 -> [进展](modules/tag.md)。

### M5：任务执行（已实现） -> [进展](modules/task-execution.md) | [详情页](modules/reminder-detail.md) | [延迟](modules/reminder-delay.md)

右侧查看/安排/开始；左侧处理完成动作。非时段禁止直接开始。安排任务模块重设计完成（UNIQUE + 统一字段 + 槽位视图 + 闹钟调度）。

### M6：数据统计（待开始） -> [进展](modules/stats.md)

按四象限统计每日完成，月度趋势图表。预留调侃评价和增长鼓励。

### M7：桌面 Widget（已实现） -> [进展](modules/widget.md)

复用 M4 智能展示引擎。系统原生 GridLayout + RemoteViews.addView 行模板 + AlarmManager 整分钟刷新；标签点击支持 per-widget 筛选。

### M8：节假日数据获取（已实现） -> [进展](modules/holiday-data.md)

按年缓存，WorkManager 保障获取不被退出打断。月度频率控制 + 工作日判断 + 无数据兜底。

### M9：四象限任务管理（已实现） -> [进展](modules/quadrant-task-manage.md)

ViewPager2 全任务浏览 + 单象限列表筛选删除 + ReminderDetailActivity MODE_VIEW。

### M10：任务完成模式（替代四象限降级恢复） -> [进展](modules/task-completion-mode.md)

每天完成一次当天隐藏；周/月/年模式叠加周期配额控制显示。

---

## 技术选型

- **Java** 为主 + View-based UI（XML 布局 + ViewBinding + Material Design 3）
- **MVVM** + **Repository 模式** + Room 数据库
- Navigation Component / App Widget / WorkManager / OkHttp
- 图表库：MPAndroidChart（预留）

---

## 决策记录

- **D001**：保持 Java + View-based UI（XML + ViewBinding + Material 3）
- **D002**：专注时长上限 2 小时，30 分钟递增，5 档
- **D003**：选择 Room 持久化
- **D004**：常规/工作日默认 5 段时段
- **D005**：晚上时段四象限展示优先级反转
- **D006**：节假日数据——中国 holiday-cn，境外 ICS；按年缓存，月度频率控制
- **D017**：春节与新年互斥，仅中国大陆显示春节
- **D018**：设备国家/地区由主线统一读取（RegionSettings）；证券从业仅 CN
- **D007**："琐碎"任务首次完成弹窗确认
- **D019**：任务执行入口统一为详情弹窗；15 分钟容差只作缓冲区
- **D020**：任务推迟仅限已安排专注时长任务；+15/+30 动态延迟
- **D008**：任务搜索仅匹配内容，标签填写时关键字推荐
- **D011**：标签 UI 超链接交互——蓝色/深蓝/下划线切换
- **D009**：设定了时间的专注时长任务需系统通知提醒
- **D010**：Widget 与 APP 复用 M4 智能展示引擎
- **D012**：任务录入三屏流程——搜索/编辑/象限
- **D013**：全文检索 jieba 分词 + UnicodeScript 多语言检测
- **D015**：标签优先展示通用化条件->标签集合映射
- **D014**：项目国际化默认英文，支持 zh-CN/zh-TW/zh-HK
- **D016**：主界面非紧凑底部时段栏（72dp）+ 按钮替代 FAB
- **D021**：项目级对话框/时间编辑框风格只抽取字号颜色，不抽取业务文案
- **D022**：任务无标签数据库统一表示为 NULL
- **D023**：Robolectric 新增测试固定 `@Config(sdk = 35)`
- **D024**：安排模块匹配/触发/开始校验统一收口（TaskScheduleMatcher + TaskStartGuard）
- **D025**：四象限降级恢复（已废弃）——被任务完成模式替代
- **D030**：任务完成模式——`tasks.completion_mode` + `tasks.quota` + `task_completion_counter` 表；每日完成当天隐藏，周/月/年配额控制周期显示；日模式不写计数器
- **D026**：安排关联时段组——类型改为动态列表（单次+各开启时段组）；MONTHLY 砍掉；时段组关闭关联安排失效；槽位取命中时段组；时间线去最大集；工作日模式标准/6天；旧 scheduleType 与新字段共存
- **D027**：对话框分流——右侧栏 `showTaskDetailDialog` 与时间线 `handleTimelineScheduledTaskClick` 分离，互不影响
- **D028**：TYPE_ONCE 超时 disable——deadline = `scheduledTime + focusMinutes`，TIME_TICK + onResume 触发，不依赖 AlarmManager
- **D029**：跳过表单行模式——每个 schedule 一行（lastSkippedDateMs + skipCount），DAO 改 upsert，去掉 365 天遍历
## 风险与阻碍

- 节假日数据源的加载与解析
- 晚上时段四象限反转逻辑
- `mergeDebugResources` 增量资源缓存可能触发 AGP NPE
- 带点号样式必须显式声明 parent
- Robolectric 新增测试需固定 `@Config(sdk = 35)`
