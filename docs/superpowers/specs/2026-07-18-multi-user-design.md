# 多用户支持设计（平板场景）

> 2026-07-18 · 脑暴产出

## 需求摘要

平板作为家庭共享设备，支持在 App 内创建和切换多个本地用户。每个用户拥有完全独立的数据空间（任务、标签、时间段、照片、偏好设置）。手机端自动维持单用户默认行为，不暴露切换入口。

---

## 一、架构总览

```
┌─ MainActivity ─────────────────────────────────────┐
│  Toolbar:  [用户名 ▼]  ⋯  [⋮ 菜单]                  │
├─────────────────────────────────────────────────────┤
│  MainFragment  (当前用户数据视图)                      │
│    ↕                                              │
│  MainViewModel ← Repository.getXxx(currentUserId) │
│                    ↕                              │
│  JustNowApplication                               │
│    ├─ Map<userId, AppDatabase>  // 多库实例管理      │
│    ├─ Map<userId, Repositories> // 每用户一套缓存     │
│    ├─ currentUserId             // 当前活跃用户       │
│    └─ UserStore (SharedPreferences) // 用户列表管理   │
│                                                       │
│  数据库文件系统:                                      │
│    justnow.db          ← 默认用户(userId=0)            │
│    justnow_u<id>.db    ← 新增用户                      │
│                                                       │
│  照片目录:                                            │
│    Pictures/JustNow/<userId>/   ← 每用户独立子目录     │
└─────────────────────────────────────────────────────┘
```

**核心决策**：每用户独立数据库文件（方案 A），而非单库加 user_id 列。好处是 14 张表、十几个 DAO、几十条查询全部零改动，出 bug 概率最小。

---

## 二、数据层

### 2.1 用户模型（UserStore）

极简设计，无密码无登录，仅名字即可创建。

```java
// UserStore.java — SharedPreferences 文件 "justnow_users"
// 存储结构：
//   "current_user_id"  → long
//   "user_count"        → int
//   "user_<id>_name"    → String
//   "user_<id>_created" → long

// 新增用户：
//   userId = System.currentTimeMillis()
//   Room 建库 justnow_u<userId>.db（复用 AppDatabase 建库逻辑）
//   写 UserStore，切为当前用户

// 切换用户：
//   清空 ViewModel 缓存 → 换 currentUserId → UI 重建刷新

// 用户不可删除（只能切换）

// 手机模式：
//   首次启动自动建默认用户（userId=0），DB 文件名 justnow.db
//   右上角不显示用户切换入口
```

### 2.2 AppDatabase 改造

```java
// 改造前：单例
private static AppDatabase sInstance;

// 改造后：按 userId 管理多实例
private static final Map<Long, AppDatabase> sInstances = new ConcurrentHashMap<>();

static AppDatabase getInstance(Context ctx, long userId) {
    return sInstances.computeIfAbsent(userId, id -> {
        String dbName = id == 0 ? "justnow.db" : "justnow_u" + id + ".db";
        return Room.databaseBuilder(ctx, AppDatabase.class, dbName).build();
    });
}
```

- **Entity / DAO 零改动** — 全部 14 张表、所有查询不动
- **数据库版本迁移** — 沿用原有 `Migration` 策略，每个 DB 实例独立升级
- **切换性能** — 数据库实例不销毁，切换时只换引用，无 I/O 开销

### 2.3 SharedPreferences 隔离

| 文件 | 策略 |
|------|------|
| `justnow_prefs` | 改为 `justnow_prefs_<userId>` |
| `capsule_settings` | 改为 `capsule_settings_<userId>` |
| `widget_filter_prefs` | 每个 Widget 绑定当前用户 |
| `justnow_users`（新增） | 全局共享，不按用户隔离 |

封装 `UserPrefs` 工具类统一获取隔离后的 Prefs 实例，避免各处拼接文件名。

### 2.4 照片目录隔离

```
改造前：Pictures/JustNow/<photo>.jpg
改造后：Pictures/JustNow/<userId>/<photo>.jpg
```

FileProvider 路径不变，仅子目录按 userId 区分。`TaskPhotoRepository` 构造时接收 userId，拼接对应目录路径。

---

## 三、UI 交互

### 3.1 用户切换入口

```
┌─ Toolbar ──────────────────────────────────────────┐
│  [JustNow]                   [用户名 ▼]   [⋮ 菜单]  │
└─────────────────────────────────────────────────────┘
                                    │
                             点击弹出 PopupMenu
                                    │
                              ┌─────┴──────┐
                              │ 用户1 ✓     │
                              │ 用户2       │
                              │ 用户3       │
                              ├─────────────│
                              │ + 添加用户   │
                              └─────────────┘
```

- 位置：右上角 ⋮ 菜单左侧，显示当前用户名
- 点击弹出下拉列表，当前用户前有 ✓
- 点击其他用户直接切换
- "添加用户" 弹输入框，输完名字即创建并切换
- **仅在平板端显示**，手机端由 `is_tablet` 资源限定符控制隐藏

### 3.2 切换行为

- **不影响进行中的计时**：各用户计时状态独立（存在各自数据库的 `TaskEntity.executingStartMs`），切回时继续走时
- **当前界面刷新**：切换后 MainFragment 重建数据视图（LiveData 重新绑定到新用户的 DAO 查询）
- **Widget 行为**：桌面 Widget 显示当前被切走的用户的最后活跃数据

### 3.3 添加用户流程

```
点击 "+ 添加用户" → 弹出输入框（输入名字）
    → 确定 → 建库 justnow_u<newId>.db
    → 更新 UserStore
    → 切换到新用户
    → MainFragment 刷新（空列表，全新开始）
```

---

## 四、测试策略

| 层级 | 内容 |
|------|------|
| **单元测试** | `UserStore` 增删改查；`AppDatabase.getInstance(ctx, userId)` 多实例创建与复用；`UserPrefs` 文件名拼接正确性 |
| **集成测试** | 切换用户后各 DAO 查询数据隔离（A 用户查不到 B 用户的数据）；照片目录按 userId 分路径 |
| **手工验证** | 平板：添加用户→切换→数据独立→返回原用户数据还在；手机：默认用户自动创建→不显示切换入口 |

---

## 五、改造范围清单

| 文件 | 改造内容 |
|------|----------|
| **新增** |
| `data/store/UserStore.java` | 用户列表管理 |
| `data/store/UserPrefs.java` | 按 userId 获取隔离的 SharedPreferences |
| **修改** |
| `data/db/AppDatabase.java` | 单例→Map 多实例，getInstance 加 userId |
| `JustNowApplication.java` | currentUserId 状态，Repository 获取逻辑，UserStore 初始化 |
| 全部 Repository（~12 个） | 方法加 userId 参数，透传到 DAO |
| 全部 ViewModel（~10 个） | 构造获取当前用户 |
| `TaskPhotoRepository.java` | 照片目录按 userId 分路径 |
| `MainFragment.java` | 用户名显示+切换入口（平板） |
| `res/layout/*.xml` | Toolbar 加用户名视图+切换下拉 |
| `res/values/strings.xml` 等 | 用户相关字符串资源化+i18n（4 语系） |
| **不改动** |
| 全部 Entity 类（14 个） | |
| 全部 DAO 接口 | |
| 数据库 Migration 脚本 | |
| Room 导出 Schema 逻辑 | |
| Widget / 通知 / 定时任务 | |
