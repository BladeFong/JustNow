# 每时段结束 + 未处理任务提醒通知

> 对应 task_plan.md 未处理任务提醒
> 设计文档：[../docs/superpowers/specs/2026-07-25-daily-unprocessed-check-design.md](../docs/superpowers/specs/2026-07-25-daily-unprocessed-check-design.md)（v2）
> 实现计划 v1：[../docs/superpowers/plans/2026-07-25-daily-unprocessed-check.md](../docs/superpowers/plans/2026-07-25-daily-unprocessed-check.md)
> 实现计划 v2：[../docs/superpowers/plans/2026-07-28-daily-period-end-notify.md](../docs/superpowers/plans/2026-07-28-daily-period-end-notify.md)

# 阶段规划、决策记录

## 定位和功能描述

每时段结束时发送休息提醒通知。最后时段叠加未处理任务提醒。涉及三个时段（跳午休/晚餐），不设门控。

## 整体规划和决策

### 需求 v2（brainstorming 确认）

- **每时段结束**：MORNING/AFTERNOON/EVENING 到时发休息提醒，不设门控。NOON/DINNER 跳过
- **超时任务提示**：时段切换时自动完成的任务，通知中追加提示
- **时机1（保留）**：最后时段结束前 30 分钟，当天未开始任何任务 + 可展示任务不为空 → 提醒
- **最后时段琐碎叠加**：EVENING 结束通知时，当天未开始琐碎任务 + 有可展示琐碎任务 → 合并琐碎提醒
  - 判定从"所有任务"改为"琐碎任务"

### 时段结束语

| 时段 | 文案 |
|------|------|
| MORNING | 午休了，休息一下吧。 |
| AFTERNOON | 快晚上了，休整休整。 |
| EVENING | 一天结束了，好好休息。 |

### 其他通知文案

- 时机1：*今天还没处理任务，抽空看看？*
- 超时提示：*「xxx」等 N 个任务已自动标记完成*
- 琐碎叠加：*{时段结束语}还有些琐碎小事，趁今天处理掉？*

### 技术决策（增量变动）

- `scheduleUnprocessedCheckIfNeeded` → `schedulePeriodEndChecks`，改为遍历三个时段注册闹钟
- 新增 `ACTION_PERIOD_END` action，extra `period_key` 区分时段
- 闹钟触发时先跑 `TaskExecutionAutoCompleter` 获取超时完成任务
- 时段结束通知新增超时提示拼接逻辑
- EVENING 额外判定琐碎任务叠加

### 涉及文件（同 v1）

| 改动 | 文件 |
|------|------|
| 调度方法重写 | `scheduler/ReminderScheduler.java` |
| action 处理重写 | `broadcast/AlarmReceiver.java` |
| 通知发送扩展 | `broadcast/ReminderNotifier.java` |
| 调用方不变 | `data/repository/TaskRepository.java` |
| 过滤静态方法不变 | `ui/base/TaskFilterHelper.java` |

# 研究发现、技术决策

### 过滤复用：抽取而非复制

原 `computeFilteredTasks` 76 行中包含过滤管线（自动完成过期、隐藏短完成专注任务、隐藏已完成琐碎任务、完成模式配额）。提取为 `public static filterDisplayableTasks()` 约 50 行，原方法改为调用它 + 补标签过滤 + 存缓存。净增约 4 行。

通知侧直接调用同一静态方法，不需另写筛选逻辑。

### settings.gradle.kts 仓库顺序（伴随发现）

阿里云 `public` 仓库只聚合 mavenCentral + jcenter，不含 Google Maven。需单独 `repository/google` 镜像。已将镜像前置、直连源后置兜底。

### 统一在 onResume 中进行权限申请与闹钟刷新补救

- **根因**：`POST_NOTIFICATIONS` 权限此前仅在“安排任务”或保存安排时触发请求，未安排任务的用户无法获取通知权限导致时段结束提醒静默无效果；`Application.onCreate` 时若缺少精确定时闹钟权限，`scheduleDailyRefresh` 调度会被跳过，后续授权后未重新触发补救。
- **解决方案**：在 `MainFragment.onResume` 中统一检查并申请 `POST_NOTIFICATIONS` 权限（API 33+）与引导 `SCHEDULE_EXACT_ALARM` 权限（API 31+）。在授权成功或检测到具备闹钟权限时，后台触发 `ReminderScheduler.refreshToday()` 与 `scheduleDailyRefresh()`，确保时段结束闹钟被正确调度。

### 修复时段闹钟续期失效与 Key 大小写不匹配根因

- **根因**：一次性 RTC_WAKEUP 闹钟触发后 `PendingIntent` 未注销，`schedulePeriodEndChecks` 中的 `probePi == null` 探针死锁拦截了后续/次日闹钟的刷新；同时 `PeriodNameKey` 为大写（`MORNING` 等），但 `ReminderNotifier.getPeriodEndMessage` 仅硬编码了小写匹配，导致标题为空。
- **修复**：移除探针拦截并确保 `setPeriodEndAlarm` 每次均被正确调用下发新触发点；在 `ReminderNotifier` 与 `AlarmReceiver` 中统一对 `periodKey` 进行不区分大小写比较（`.toLowerCase(Locale.ROOT)` / `equalsIgnoreCase`）。

### 确认 BleNotificationSDK 系统通知发送机制

- **说明**：`BleNotificationSDK.sendNotification(builder, ...)` 内部会自动调用系统 `NotificationManager.notify` 弹出本地通知，宿主 App 无需且不得手动额外调用 `NotificationManagerCompat.notify`。

### 确认 MIUI 白名单限制并对齐 BleNotificationSDK 精简通知渠道

- **说明**：系统通知无声音系 MIUI 白名单限制所致。清理此前盲目添加的多余音效/渠道测试代码，`CHANNEL_ID` 严格保持原始的 `"task_reminder"`，`createChannel` 彻底对齐 `BleNotificationSDK` 的精简标准实现（仅保留 `IMPORTANCE_HIGH` 与 `description`）。
- **修复**：在 `ReminderNotifier.send` 与 `sendOvertime` 入口添加 `createChannel(context)` 保护，确保直接发送通知路径下渠道得到正确初始化。

### 闹钟调度切换为 setAlarmClock 保证息屏精准唤醒

- **根因**：`setExactAndAllowWhileIdle` 在 Android 系统及定制 ROM（如小米 SmartPower、SSRU 资源调度）深度休眠待机时，非 `AlarmClock` 类型的后台闹钟会被系统电源管理服务对齐合并或延迟拦截；当用户解锁或打开手机时，系统 AlarmManagerService 会瞬间冲刷派发此前积压的过期广播，导致任务到期与时段结束通知扎堆并发弹出。
- **技术决策**：在 `ReminderScheduler.setAlarmSafe` 中将闹钟设置方式升级为官方推荐的 `setAlarmClock(new AlarmClockInfo(triggerAtMillis, showPi), operation)`。
- **效果**：系统底层将闹钟识别为硬件 RTC 级别的法定物理时钟，直接进入系统的 `Next wake from idle` 唤醒队列，彻底豁免 Doze Mode 与系统省电引擎的拦截与推迟，保证息屏状态下准时唤醒；缺少精确闹钟权限时由前台 `onResume` 统一引导授权。

### 任务提醒通知跳转主界面与任务启动即时消除

- **根因**：通知本体点击此前直接指向 `ReminderDetailActivity`，无法进入主界面唤起任务操作弹窗；且任务开始执行时未主动联动消除通知栏对应的常驻提醒。
- **技术决策**：
  1. `ReminderNotifier.buildDetailIntent` 改为跳转 `MainActivity` 并携带 `schedule_id` 与 `task_id`；
  2. `MainActivity.handleReminderIntent` 接收到任务 Intent 时，派发 `MainFragment` 弹出任务操作对话框；
  3. `MainViewModel.startExecutionSync` 在任务真正开始执行时，主动查询关联安排并消除通知，确保用户在弹窗、列表或任意入口启动任务时均能即时清理状态栏常驻通知；未开始执行前保持通知常驻提醒。

### 安排闹钟调度与周期配额挂钩

- **说明**：此前每日凌晨 3 点重新注册当天闹钟时，未校验任务在当前周/月/年周期内的完成配额情况，导致配额已达标的任务仍会设置闹钟并在到点时弹出提醒。
- **技术决策**：
  1. 在 `TaskRepository` 封装公共方法 `isPeriodQuotaReachedSync(TaskEntity task)`，同时统一替换 `TaskFilterHelper` 中的内联过滤逻辑；
  2. 在 `ReminderScheduler.refreshToday()` 每日刷新与 `schedule()` 单个调度入口增加配额校验，配额已满的任务不在当天注册闹钟；
  3. 在 `AlarmReceiver.handleAlarm()` 增加到点双重校验，杜绝因周期内刚完成达标而误发过期提醒。

### 任务执行中状态判定收口与到点提醒过滤重构

- **说明**：此前各处对任务是否处于“正在执行中”存在分散的内联判定（`executingStartMs > 0 && executingEndMs == 0`），且 `AlarmReceiver.handleAlarm()` 存在早期的不一致状态判定，容易引发边缘判定偏差与维护遗漏。
- **技术决策**：
  1. 在 `TaskEntity` 封装公共方法 `isExecuting()`（`executingStartMs > 0 && executingEndMs == 0`），作为实体级唯一标准状态判定；
  2. 在 `ReminderScheduler` 封装公共方法 `shouldRegisterAlarm(TaskEntity task)` 与 `shouldTriggerAlarm(TaskEntity task)`，统一闹钟调度与到点提醒的过滤逻辑；
  3. 重构 `AlarmReceiver`、`TaskAdapter`、`TimelineBuilder`、`MainViewModel`、`ReminderDetailViewModel`、`WidgetUpdateHelper` 等全工程调用点，统一使用实体公共方法，彻底消除内联重复代码。




