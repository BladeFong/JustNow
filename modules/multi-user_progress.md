# 进度日志

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
