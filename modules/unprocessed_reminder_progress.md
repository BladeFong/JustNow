# 进度日志

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
