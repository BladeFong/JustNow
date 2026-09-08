# 进度日志

### 2026-09-07 — 重构节假日同步分发机制 + 业务查询全面回归单库自闭环

- **业务查询零跨库**：彻底移除 `PeriodGroupRuleResolver` 与 `PeriodConfigViewModel` 对 `userId=0` 的硬编码 DAO 依赖，统一回归各用户独立数据库，彻底消除跨库连接池冲突与通知调度失效隐患。
- **公共底座集中备份与逐用户分发**：
  - 节假日网络同步（`triggerHolidaySync` 与 `HolidaySyncWorker`）下载成功后，写入公共底座库 `justnow.db`（`userId=0`）备份，并在平板场景下由 `DataMigrationManager.dispatchHolidayUpdate` 逐一同步至所有现有用户库；
  - 平板新建用户时，通过 `copyHolidayCacheToNewUser` 自动从公共底座库克隆已有节假日缓存，建好即用。
- **手机端初始化与旧数据平滑迁移保持**：手机端无用户无条件使用 `userId=0`（杜绝 `deleteDatabase`）；平板端全库迁移时将业务数据迁移至首个用户库，并在公共库保留节假日数据备份。
- **覆盖安装与冷启动闹钟自恢复**：在 `AndroidManifest.xml` 中为 `AlarmReceiver` 注册 `MY_PACKAGE_REPLACED` 广播，并在 `JustNowApplication.onCreate` 异步执行 `refreshToday()`，解决应用覆盖更新后底层闹钟被系统注销导致的通知空窗；同时 `PeriodGroupRuleResolver` 支持传入显式 `userId` 精准绑定。

### 2026-09-01 — 修复手机端数据误删漏洞 + 统一公共节假日底座与平板历史数据平滑迁移

- **根因修复与删除禁令**：彻底移除 `JustNowApplication` 手机端启动时对 `justnow_u<id>.db` 的误判物理删除逻辑，手机端无用户时无条件初始化 `userId = 0`（固定对应 `justnow.db`）。
- **统一公共节假日底座（userId=0）**：将节假日缓存（`holiday_cache`）作为设备级公共数据收口到 `userId = 0`（`justnow.db`），`PeriodGroupRuleResolver`、`PeriodConfigViewModel` 与 `triggerHolidaySync()` 均统一读写该库。
- **存量节假日数据回迁**：新增 `DataMigrationManager`，后台静默将平板各分用户数据库中的节假日缓存合并回公共基础库（`justnow.db`），一次性执行并打标记。
- **平板分用户前旧业务数据按需平滑迁移**：检测到 `justnow.db` 中残留旧版任务等业务数据时，通过 SQLite 事务原子迁移至平板首个用户的独立库中，并清理公共库中的用户数据。

### 2026-08-01 — 修复 PeriodConfig/TagManage 多用户 SP 隔离遗漏 + 实现旧配置自动平滑迁移

- 修复 `PeriodConfigViewModel` 与 `TagManageViewModel` 未使用 `UserPrefs` 隔离 SP 的问题，补齐多用户 SP 隔离链条。
- 在 `UserPrefs.getPrefs()` 添加自动平滑迁移机制：用户专属 SP 建立时，自动将旧全局 SP 中的 `schedule_profile` 等配置迁移至专属 SP，确保升级后作息类型不丢失。
- themes.xml 加 colorControlActivated，全局光标和 RadioButton 选中态跟随主题色
- 创建用户对话框 M2 风格化：MaterialAlertDialogBuilder + 焦点下划线主题色

### 2026-07-18 — 审查修复 + SP/照片隔离收尾
- 审查发现 2 个关键 Bug 已修复：menu item ID long→int 截断、AtomicLong 替代 System.currentTimeMillis
- ViewModel.reloadForCurrentUser() 替代 Activity.recreate()，无闪烁切换
- SharedPreferences 全线按 userId 隔离（CutoffTimeStore/ChoreHiddenTodayStore/PeriodGroupRuleResolver/PriorityTagConfig/MainViewModel/capsule_settings）
- 照片临时目录按 userId 分子目录

### 2026-07-18 — 实施：多用户数据层基础设施 + UI 入口 + 单元测试
- UserStore（用户列表 CRUD）+ UserPrefs（SP 隔离工具）+ AppDatabase 多实例管理
- JustNowApplication 每用户 Repository 缓存，getDatabase() 按当前用户动态解析
- UserSwitcherManager 独立模块封装 Toolbar 用户入口、下拉切换、创建对话框
- UserStoreTest（14 用例）+ UserPrefsTest（5 用例），全部通过
- 手机端自动创建默认用户，平板端首次启动强制创建

### 2026-07-18 — 脑暴设计：多用户支持方案
- 确定方案 A：每用户独立数据库文件，Entity/DAO 零改动
- 设计文档：docs/superpowers/specs/2026-07-18-multi-user-design.md
- 实施计划：docs/superpowers/plans/2026-07-18-multi-user.md
