# 进度日志
 
### 2026-09-08 — 任务执行中状态判定收口与全工程内联重复消除

- **实体级状态判定收口 (`TaskEntity.isExecuting`)**：
  - 在 `TaskEntity.java` 中新增公共方法 `isExecuting()`，严格统一 `executingStartMs > 0 && executingEndMs == 0` 的执行中状态标准。
- **统一调度与到点提醒门控 (`ReminderScheduler`)**：
  - 在 `ReminderScheduler.java` 中封装 `shouldRegisterAlarm(task)` 与 `shouldTriggerAlarm(task)`，统一闹钟注册与到点广播的过滤规则。
- **全工程调用点重构与内联重复消除**：
  - 重构 `AlarmReceiver.java`、`TaskAdapter.java`、`TimelineBuilder.java`、`MainViewModel.java`、`ReminderDetailViewModel.java` 与 `WidgetUpdateHelper.java`，全量替换为 `task.isExecuting()` 与 `ReminderScheduler.shouldTriggerAlarm(task)`，彻底消除分散的内联状态判定代码与潜在遗漏风险。

### 2026-08-22 — 安排任务闹钟调度与周期配额完成状态全面挂钩

- **封装通用周期配额判断接口**：
  - 在 `TaskRepository.java` 中新增 `isPeriodQuotaReachedSync(TaskEntity task)`，统一替换 `TaskFilterHelper.java` 中的内联重复判断逻辑，并在 `TaskRepositoryTest.java` 补充单元测试。
- **闹钟调度与到点触发过滤**：
  - 在 `ReminderScheduler.java` 的 `refreshToday()` 与 `schedule()` 中增加配额已满校验，当任务在当前周期内已达配额上限时不再注册当天闹钟。
  - 在 `AlarmReceiver.java` 的 `handleAlarm()` 中增加到点双重校验，杜绝周期内达标后弹出无效提醒。

### 2026-08-21 — 任务通知点击跳转主界面唤起操作弹窗与任务启动即时消除通知

- **通知点击目标修正与操作弹窗联动**：
  - 在 `ReminderNotifier.java` 中，将任务到期通知的 `buildDetailIntent` 改为跳转 `MainActivity` 并携带 `task_id` 与 `schedule_id`。
  - 在 `MainActivity.java` 中接收到通知点击时，派发给 `MainFragment` 由 `MainViewModel.resolveAndHandleTaskClick(taskId)` 自动弹出任务操作/进行中弹窗。
- **任务启动全链路通知自动消除**：
  - 在 `MainViewModel.java` 的 `startExecutionSync`、`AlarmReceiver.java` 与 `BaseTaskViewModel.java` 处统一调用 `ReminderNotifier.cancelForTask`，在任务真正进入执行状态时全量消除对应任务的安排提醒与超时通知，确保用户无论从弹窗、主界面列表或任意入口启动任务时均能即时清理通知栏残留。

### 2026-08-21 — 闹钟调度升级为 setAlarmClock 彻底解决息屏待机延迟与扎堆推送

- **升级系统法定闹钟 API (`setAlarmClock`)**：
  - 在 `ReminderScheduler.java` 的 `setAlarmSafe` 中，将底层的闹钟注册 API 切换为 `AlarmManager.setAlarmClock(new AlarmClockInfo(triggerAtMillis, showPi), operation)`。
  - 构建指向 `MainActivity` 的 `showPi`，使系统在状态栏和锁屏时钟正常展示即将到来的任务/时段提醒。
- **根除息屏 Doze 挂起与亮屏扎堆**：
  - 彻底解决由于系统省电管理服务（Doze/SSRU）在熄屏待机期间拦截非闹钟类广播、并在用户亮屏点开应用时集中冲刷释放导致的通知扎堆弹出问题。
  - 严格规范权限管控，缺少精确闹钟权限时跳过注册并由前台 `onResume` 统一弹窗引导授权，不进行静默降级。

### 2026-08-14 — 统一通知基类分发管道、支持开机广播恢复与时段闹钟自续期

- **开机广播自动恢复闹钟机制 (`RECEIVE_BOOT_COMPLETED`)**：
  - 在 `AndroidManifest.xml` 中声明 `RECEIVE_BOOT_COMPLETED` 权限，并为 `AlarmReceiver` 添加 `BOOT_COMPLETED` 与 `LOCKED_BOOT_COMPLETED` 意图过滤器。
  - 手机重启后系统自动触发开机广播，在后台静默执行 `refreshToday()` 与 `scheduleDailyRefresh()`，全量恢复当天任务提醒、时段结束闹钟与每日 3 点刷新闹钟。
- **全类型闹钟注册前置物理注销 (`cancel`)**：
  - 在 `ReminderScheduler` 中增加 `cancelAllPeriodEndAlarms`、`cancelPeriodEndAlarm`、`cancelUnfinishedCheckAlarm`、`cancelDailyRefresh` 等注销方法。
  - 所有时段结束闹钟、未处理检查及任务提醒在注册或重新调度前，均先执行物理注销，彻底清理系统 `AlarmManager` 内核中的脏残留。
- **通知分发通道统一收口 (`ReminderNotifier`)**：
  - 提取基础 Builder 构造方法 `createBaseBuilder(context, ongoing, autoCancel)`，统一集成渠道创建 `createChannel`、应用小图标 `R.drawable.ic_launcher_foreground` 与 `PRIORITY_HIGH`，彻底根除渠道遗漏和优先级配置漂移。
  - 提取统一分发入口 `dispatchNotification(context, builder, notifyId)`，`send`、`sendOvertime`、`sendPeriodEnd`、`sendUnfinishedCheck` 均只负责业务参数组装并委托给核心分发通道。
- **时段闹钟按时段实例封装与触发自续期**：
  - `ReminderScheduler` 增加按时段实例与按 `periodKey` 查找并续期的 `schedulePeriodEndAlarm` 方法，参数化管理各时段闹钟。
  - `AlarmReceiver.handlePeriodEnd` 消费通知后立即为该时段调用 `schedulePeriodEndAlarm(periodKey)` 接力续期，实现时段闹钟闭环自运转。
- **权限链式申请与时段变更实时联动**：
  - 移除 `MainFragment` 中权限单次锁死标记，实现通知权限回调后链式引导精确闹钟。
  - `TimePeriodRepository` 与 `PeriodConfigViewModel` 时段保存或更新后自动触发 `schedulePeriodEndChecks()` 刷新闹钟。

### 2026-07-31 — 对齐 BleNotificationSDK 精简通知渠道

- 清理此前盲目尝试的多余音效/渠道测试代码，`CHANNEL_ID` 严格恢复原始的 `"task_reminder"`
- 参考 BleNotificationSDK 源码精简 `createChannel` 实现，仅保留 `IMPORTANCE_HIGH` 与 `description`
- 在 `send` 与 `sendOvertime` 入口补充 `createChannel(context)` 兜底调用，确保直接发送通知路径下的渠道注册

### 2026-07-30 — 确认 BleNotificationSDK 系统通知发送机制

- 确认所有通知均直接交由 BleNotificationSDK.sendNotification(builder, ...) 发送，SDK 内部会自动调用系统的 NotificationManager.notify 弹出本地通知，无需在宿主侧重复调用 NotificationManagerCompat.notify

### 2026-07-30 — 修复时段结束通知未弹出及续期失效问题

- 移除 ReminderScheduler.schedulePeriodEndChecks 中的死锁探针，确保每次调用均更新下发下一个触发点的闹钟
- ReminderNotifier.getPeriodEndMessage 与 AlarmReceiver.handlePeriodEnd 对 periodKey 进行忽略大小写匹配，解决全大写 Key 导致标题为空的问题
- Release 编译并安装到设备验证成功

### 2026-07-30 — 统一通知与闹钟权限在 onResume 检查与申请

- MainFragment 在 onResume 中统一检查并发起 POST_NOTIFICATIONS 通知权限系统申请，解决此前用户不手动安排任务导致通知权限缺失问题
- 提取 requestNotificationPermission 与 promptExactAlarmPermissionDialog 独立 helper 方法，实现 onResume 检查与 navigateToSchedule 兜底调用的逻辑复用与去重
- onResume 中获得精确闹钟权限时自动在后台触发 refreshToday 与 scheduleDailyRefresh 调度保底，确保非休息时段结束闹钟正常注册

### 2026-07-28 — v2 实现完成并安装验证

- schedulePeriodEndChecks：遍历三个时段注册闹钟，跳 NOON/DINNER，EVENING 额外注册时机1
- sendPeriodEnd：时段结束语 + 超时提示 + 琐碎叠加，全部走 BleNotificationSDK
- handlePeriodEnd：不设门控 + 自动完成过期 + EVENING 琐碎判定
- handleUnfinishedCheck 简化为时机1专用
- Release 编译安装成功

### 2026-07-28 — 需求变更为每时段结束通知（v2）

- 每时段结束发休息提醒（MORNING/AFTERNOON/EVENING），跳 NOON/DINNER
- 超时任务在通知中提示
- 最后时段叠加琐碎提醒，判定从所有任务改为琐碎任务
- 时机1保留
- 设计文档已更新

### 2026-07-25 — 实现完成并安装验证

- 提取 `TaskFilterHelper.filterDisplayableTasks` 静态方法，`computeFilteredTasks` 调用之
- `ReminderNotifier.sendUnprocessedCheck` 新增通知发送（通过 BleNotificationSDK）
- `ReminderScheduler.scheduleUnprocessedCheckIfNeeded` 幂等注册两个时机闹钟
- `AlarmReceiver.handleUnfinishedCheck` 处理闹钟触发判定
- `TaskRepository` 保存任务后触发首次注册
- Release 编译安装成功，AlarmReceiver 测试通过

### 2026-07-25 — 设计完成，进入实现阶段

- 完成需求确认（brainstorming）：两个时机独立判定，过滤复用，幂等注册
- 设计文档和实现计划落地为模块文档，过滤方案确定为抽取而非复制
- settings.gradle.kts 仓库顺序伴随修正：添加阿里云 google 镜像并前置
