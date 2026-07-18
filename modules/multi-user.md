# 阶段规划、决策记录

## 平板多用户支持

- **定位**：App 内多用户切换，每用户独立数据库+偏好+照片，零密码零登录。手机端自动创建默认用户，不暴露切换入口。
- **阶段状态**：
  - [x] Phase 1: 需求分析与脑暴 (完成 — 2026-07-18)
  - [x] Phase 2: 方案设计与 Spec 编写 (完成 — 2026-07-18)
  - [x] Phase 3: 核心代码实现 (进行中 — 数据层隔离完成，UI 入口完成，测试通过)
  - [x] Phase 4: SP 隔离 + 照片目录隔离 (完成)
- **设计文档**：`docs/superpowers/specs/2026-07-18-multi-user-design.md`
- **实施计划**：`docs/superpowers/plans/2026-07-18-multi-user.md`

---

# 研究发现、技术决策、需求分析

## 方案选型

### 数据隔离：每用户独立 DB vs 单库加 userId 列

- **选择**：每用户独立数据库文件（`justnow_u<id>.db`）
- **理由**：14 张表、十几个 DAO、几十条查询全部零改动。代价仅为 Application 层多一层 `Map<Long, AppDatabase>` 管理。切换用户时 Activity recreate 确保所有 ViewModel 和 Repository 引用指向新用户数据库。

### Repository 层改造策略

- **选择**：JustNowApplication 中每 Repository 维护 `Map<Long, Repo>` 缓存。`getDatabase()` 按 `getCurrentUserId()` 动态解析当前用户的 DB 实例。
- **理由**：Entity/DAO 零改动。各 Repository 保持原有构造参数不变。`getXxxRepository()` 从 Map 中 `computeIfAbsent`，同一用户多次调用复用同一实例。

### SharedPreferences 隔离 ✅

- **选择**：文件名加 `_<userId>` 后缀（userId=0 保持原文件名向后兼容）
- 已迁移：CutoffTimeStore、ChoreHiddenTodayStore、PeriodGroupRuleResolver、PriorityTagConfig、MainViewModel（default_filter_tag_id + schedule_profile）、MainFragment（capsule_settings）
- WidgetFilterStore 保持全局（Widget 按设备实例绑定，不按用户）

### 照片目录隔离 ✅

- **选择**：临时拍照文件写入 `Pictures/<userId>/` 子目录，避免多用户并发冲突
- 相册副本保持共享 `Pictures/JustNow`（DB 已按用户隔离，照片归属由 DB 记录决定）
- FileProvider 路径无需改动

## UI 模块化

- 用户切换 UI 逻辑封装为独立 `UserSwitcherManager.java`（`ui/main/` 包内）
- MainFragment 仅需 3 行委托调用，不耦合用户管理细节
- 提供 `setOnUserChangedListener(Runnable)` 回调，切换/创建用户后触发 Activity recreate

## 约束

- 手机端：自动创建默认用户（userId=0），不显示切换入口（`is_tablet` 控制）
- 平板端：首次启动强制创建用户，后续可自由切换/新增
- 用户不可删除（只能切换）
- 切换时通过 Activity recreate 刷新所有数据层引用
- 切换时各用户计时状态独立（存在各自数据库）
