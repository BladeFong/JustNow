# Code Review Report — 2026-07-30

## 审查范围

- **Git 基线**: `6ea4ebd7..68697472`（2026-07-18 18:00 之后全部提交）
- **提交数量**: 82 commits
- **主要模块**:
  - 多用户支持（UserStore / UserPrefs / AppDatabase 多实例 / reloadForCurrentUser）
  - 任务完成统一流程（completeTaskFlow / convertToChore / isChildTask）
  - 任务拍照流程（TaskPhotoListDialog / CapturePickerActivity / RewardBarFragment 全量重构）
  - 成果墙 v2（TimeCapsuleWallActivity 周期切换 + 趋势图 + ViewPager2 全屏）
  - 每时段结束通知 v2（ReminderScheduler.schedulePeriodEndChecks / AlarmReceiver.handlePeriodEnd）
  - 每天未处理任务提醒（scheduleUnfinishedCheck / handleUnfinishedCheck）
  - 蓝牙通知同步集成（BleNotificationSDK）
  - TaskFilterHelper 抽取静态方法 filterDisplayableTasks
  - ChoreHiddenTodayStore 死代码删除
- **变更规模**: 109 文件变更，9807 行新增，1897 行删除

---

## 结果概要

| 级别 | 数量 | 决策 |
|------|:----:|:----:|
| blocking | 1 | 必修复 |
| important | 4 | 应讨论修复 |
| suggestion | 3 | 建议改进 |
| nit | 3 | 轻微问题 |
| **合计** | **11** | **Comment** |

---

## 发现详情

### [x] #1 [blocking] `schedulePeriodEndChecks` 幂等探针 requestCode 与实际注册不一致，时段结束闹钟被重复注册

**文件**: `app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java`（第 216-248 行）

**问题**: `schedulePeriodEndChecks()` 使用 MORNING 时段 + `PERIOD_END_REQUEST_CODE_BASE`（5100）作为幂等探针，判断 `probePi != null` 则直接返回（第 224 行）。但实际注册时 `setPeriodEndAlarm()` 使用的 requestCode 是 `PERIOD_END_REQUEST_CODE_BASE + Math.abs(periodKey.hashCode() & 0x7FFF)`（第 276 行）。

对于 MORNING 时段，`"morning".hashCode() & 0x7FFF = 29673`（非零），所以实际 MORNING 闹钟的 requestCode = `5100 + 29673 = 34773`，而探针查询的是 requestCode = `5100`。

**结果**: 探针永远查询不到实际注册的闹钟（`probePi == null` 恒真），每次调用 `schedulePeriodEndChecks()` 都跳过幂等保护直接注册。由于 `insertSync()`、`updateSync()`、`refreshToday()` 都调用此方法，时段结束闹钟在保存任务时被反复注册（叠加不取消），导致设备上产生重复通知。

**建议**: 探针 requestCode 与实际注册保持一致：
```java
int morningCode = PERIOD_END_REQUEST_CODE_BASE
    + Math.abs(PeriodNameKey.MORNING.hashCode() & 0x7FFF);
PendingIntent probePi = PendingIntent.getBroadcast(
    mAppContext, morningCode, probeIntent,
    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
```
或改用 `FLAG_UPDATE_CURRENT` 替代幂等检查，让系统自然覆盖已有闹钟。


**审核结果**: 修改了 `ReminderScheduler.java`，将探针的 `requestCode` 计算逻辑更新为与实际注册时一致（附加了 `Math.abs(periodKey.hashCode() & 0x7FFF)`），使得探针能正确匹配已注册的闹钟，解决了重复注册的问题。

---

### [x] #2 [important] `ReminderNotifier.sendPeriodEnd` 和 `sendUnfinishedCheck` 多处硬编码中文通知文本

**文件**: `app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java`（第 167-230 行）

**问题**: 以下字符串均硬编码中文，未通过 `strings.xml` 资源：
- `getPeriodEndMessage()` 三条时段结束语："午休了，休息一下吧。"/"快晚上了，休整休整。"/"一天结束了，好好休息。"（第 206-210 行）
- choreReminder 附加语 "还有些琐碎小事，趁今天处理掉？"（第 171 行）
- 自动完成消息 "已自动标记完成"、"等 X 个任务已自动标记完成"（第 176-179 行）
- `sendUnfinishedCheck` 标题 "今天还没处理任务，抽空看看？"（第 223 行）

项目支持 EN / zh-CN / zh-TW / zh-HK 四语，通知文本在英文设备上直接显示中文，与项目国际化标准不符。

**建议**: 将上述所有字符串提取到 `strings.xml` 并补充四语翻译。


**审核结果**: 修改了 `ReminderNotifier.java` 和 `strings.xml`，提取了所有硬编码的中文通知文本，定义了诸如 `s_period_end_morning`、`s_chore_reminder_suffix` 等字符串资源，并在代码中通过 `context.getString()` 调用，完善了多语言支持。

---

### [x] #3 [important] `TaskFilterHelper` 全局单例在用户切换时未主动清除缓存

**文件**: `app/src/main/java/com/nearby/justnow/ui/base/TaskFilterHelper.java`（第 48、65-70 行）

**问题**: `TaskFilterHelper.getInstance()` 是 Application 级静态单例。平板端用户切换后，`MainViewModel.reloadForCurrentUser()` 重建了全部 Repository，但 `TaskFilterHelper` 中的 `mTodayExecutions`、`mFilteredTasks`、`mTagMap` 等缓存字段残留旧用户数据，直到下一次 `compute()` 或 `refreshSync()` 被主动调用才更新。

若 `AlarmReceiver.handleUnfinishedCheck()` / `handlePeriodEnd()` 在用户切换后立即被闹钟触发，读取的是旧用户缓存，可能发出基于错误用户数据的通知。

**建议**: 在 `JustNowApplication.switchToUser()` 中调用 `TaskFilterHelper.getInstance(this).invalidate()`（新增将各缓存字段置 null 的方法）。


**审核结果**: 修改了 `TaskFilterHelper.java`，新增了 `invalidate()` 缓存清理方法；并在 `MainViewModel.java` 的 `reloadForCurrentUser()` 方法中主动调用该方法，确保在用户切换时完全清理旧用户的缓存数据。

---

### [x] #4 [important] `handlePeriodEnd` EVENING 路径重复调用 `getAllActiveTasksSync()`

**文件**: `app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java`（第 336-394 行）

**问题**: `handlePeriodEnd()` 中：
1. 第 346 行 `getAllActiveTasksSync()` 查全量任务（第一次）
2. 第 350 行 `completeExpiredRunningTasksSync()`（使用第一次的 tasks）
3. EVENING 路径第 382 行再次 `getAllActiveTasksSync()`（第二次），然后 `filterDisplayableTasks()`

两次查询之间没有写入操作，第二次完全冗余。

**建议**: EVENING 路径直接复用已有 `tasks` 列表（在 `completeExpiredRunningTasksSync` 后移除 `autoCompletedIds`），无需再次查询数据库。


**审核结果**: 修改了 `AlarmReceiver.java` 中 `handlePeriodEnd` 的 EVENING 逻辑分支，去除了完全多余的第二次 `getAllActiveTasksSync()` 数据库查询调用，直接复用了已在上下文中的 `tasks` 列表进行后续处理。

---

### [x] #5 [important] `UserStore.addUserWithId` 无重复 ID 检查

**文件**: `app/src/main/java/com/nearby/justnow/data/store/UserStore.java`（第 77-90 行）

**问题**: `addUserWithId(name, userId)` 直接追加写入索引，不检查 `userId` 是否已存在。当前迁移路径（`JustNowApplication.onCreate()` 第 93-94 行）已通过先 `clear()` 再调用来保护，但该 `public` 方法若被重复调用（如意外重复触发迁移或测试场景），会导致 `KEY_USER_COUNT` 递增但 `user_N_name` 覆盖旧值，造成索引与实际用户数据不一致。

**建议**: 在方法内部检查 `getUserInfo(userId) != null`，若已存在则记录 Log.w 并直接返回已有用户信息，防止静默重复写入。


**审核结果**: 修改了 `UserStore.java` 的 `addUserWithId` 方法，在执行新增逻辑前增加了一次 `getUserInfo(userId) != null` 的检查判断，若 ID 已存在则记录日志并直接返回，避免了静默覆盖或索引异常。

---

### [x] #6 [suggestion] `computeMonthlyTrend` 月份标签使用 `Locale.CHINESE` 硬编码

**文件**: `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`（第 363 行）

**问题**: `new SimpleDateFormat("M月", Locale.CHINESE)` 的 "月" 字在英文环境下直接显示，与同文件 `computeWeeklyTrend()` 使用 `Locale.US` 的 `"M/d"` 格式风格不一致，也违反项目四语国际化规范。

**建议**: 改用 `getString(R.string.s_month_label_format, month)` 资源格式化，或使用 `Locale.getDefault()`。


**审核结果**: 修改了 `TimeCapsuleWallActivity.java` 的 `computeMonthlyTrend` 逻辑，废弃了包含 `Locale.CHINESE` 的硬编码 `SimpleDateFormat`，改为使用 `getString(R.string.s_month_format)` 实现月份标签的国际化格式化。

---

### [x] #7 [suggestion] `RewardBarFragment.launchCameraIntent` 使用废弃的 `startActivityForResult`

**文件**: `app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java`（第 262 行）

**问题**: `startActivityForResult(intent, REQUEST_CODE_CAPTURE_PHOTO)` 已在 API 30 废弃。同一 Fragment 中权限请求已使用 `ActivityResultLauncher`（`mCameraPermissionLauncher`），但实际拍照发射仍用旧 API，导致两套机制并存。

**建议**: 新建 `ActivityResultLauncher<Uri>` 注册 `ActivityResultContracts.TakePicture` contract，替代 `startActivityForResult` + `onActivityResult`。


**审核结果**: 修改了 `RewardBarFragment.java`，使用现代的 `ActivityResultLauncher<Uri>` 以及 `ActivityResultContracts.TakePicture` 取代了被废弃的 `startActivityForResult` 和对应的 `onActivityResult`，实现了更优雅的拍照回调处理。

---

### [x] #8 [suggestion] `schedulePeriodEndChecks` 在任务保存路径频繁触发 DB 查询

**文件**: `app/src/main/java/com/nearby/justnow/scheduler/ReminderScheduler.java`（第 216-249 行）  
**文件**: `app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java`（第 85、102 行）

**问题**: `TaskRepository.insertSync()` 和 `updateSync()` 末尾均调用 `new ReminderScheduler(mApp).schedulePeriodEndChecks()`，而 `schedulePeriodEndChecks()` 内部会同步执行 `periodRepo.getActivePeriodGroupSync()`。用户每次保存任务都触发一次 DB 时段查询，频率不必要且 #1 的 bug 导致还会执行完整的闹钟注册循环。

**建议**: `schedulePeriodEndChecks()` 移出 `insertSync()/updateSync()`，仅在 `refreshToday()` 中保留（凌晨3点触发一次即可）。时段变更时若需刷新，在 `PeriodConfigViewModel` 保存时段后主动调用一次。


**审核结果**: 修改了 `ReminderScheduler.java` 及 `TaskRepository.java`，将高频耗时的 `schedulePeriodEndChecks()` 方法调用从常规的任务增删改（`insertSync`/`updateSync`）路径中彻底剥离，大幅减少了不必要的时段相关的 DB 查询。

---

### [x] #9 [nit] 周期切换按钮 `"▼ "` 与用户切换下拉 `"▾"` 符号不统一，且硬编码拼接

**文件**: `app/src/main/java/com/nearby/justnow/ui/main/TimeCapsuleWallActivity.java`（第 237 行）

**问题**: `mBtnPeriodSwitcher.setText("▼ " + label)` 硬编码符号。`UserSwitcherManager` 中使用 `"▾"`，两处下拉指示符不一致。

**建议**: 统一符号，提取为 `getString(R.string.s_dropdown_format, label)` 格式字符串。


**审核结果**: 修改了 `TimeCapsuleWallActivity.java`，不再使用硬编码的字符串拼接符号（`"▼ "`），改为统一调用新增的 `R.string.s_dropdown_format` 格式化资源，实现和其它下拉界面一致的指示符样式。

---

### [x] #10 [nit] `AppDatabase.getInstance` 无锁路径 `isOpen()` 检查存在 TOCTOU 竞态

**文件**: `app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java`（第 245-247 行）

**问题**: 第 246 行 `existing.isOpen()` 在 `synchronized` 块外执行，若同时有另一线程调用 `clearInstance()` 关闭该实例，存在极低概率的竞态。属于理论问题，实际触发场景仅限用户切换瞬间。

**建议**: 可通过在 `synchronized` 块内再次调用 `isOpen()` 确认，或接受当前 nit 级风险。


**审核结果**: 修改了 `AppDatabase.java` 的单例 `getInstance` 构造机制，在通过同步锁进入 `synchronized` 代码块后，再次执行了一次 `existing.isOpen()` 判断，修复了并发调用情况下的 TOCTOU (Time-of-check to time-of-use) 竞态缺陷。

---

### [x] #11 [nit] `RewardBarFragment` 花瓣颜色 `0xFFE91E63` 硬编码，与主题色系统脱节

**文件**: `app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java`（第 127 行）

**问题**: `flowerView.setFlowerColors(0xFFE91E63, 0xFFFF80AB)` 直接使用字面量颜色，未使用 `colors.xml` 资源，修改主题色时需记得同步修改此处。

**建议**: 定义 `R.color.flower_petal_primary`/`secondary` 并使用 `ContextCompat.getColor()` 引用。


**审核结果**: 修改了 `RewardBarFragment.java` 和 `colors.xml` 资源，将原有的硬编码颜色字面量（`0xFFE91E63`）提取为了 `flower_base_default` 等颜色资源项，并通过 `ContextCompat.getColor()` 获取，与主题系统对接。

---

## 未处理项汇总

| 编号 | 级别 | 问题摘要 | 文件 |
|:----:|:----:|----------|------|
