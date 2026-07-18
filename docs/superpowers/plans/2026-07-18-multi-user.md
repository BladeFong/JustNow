# 多用户支持实施计划

> **For agentic workers:** 按 Task 顺序逐条实施，每步 checkbox 完成后勾选。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 平板端支持应用内多用户切换，每用户独立数据库+偏好+照片目录；手机端自动创建默认用户，不可见切换入口

**Architecture:** 每用户独立 Room 数据库文件（`justnow_u<id>.db`），通过 `Map<userId, AppDatabase>` 管理多实例，Entity/DAO 零改动。`UserStore` 管理用户列表，`UserPrefs` 统一获取隔离后的 SharedPreferences。Repository/ViewModel 透传 userId。平板 Toolbar 增加用户名下拉切换入口

**Tech Stack:** Room, SharedPreferences, LiveData, DataBinding, PopupMenu, resource qualifiers (`is_tablet`)

## Global Constraints

- 手机端：自动创建默认用户（userId=0，DB 名 `justnow.db`），不显示切换入口，`is_tablet` 资源限定符控制可见性
- 平板端：首次启动强制创建用户，后续可在 Toolbar 切换/新增
- 用户不可删除（只能切换）
- 切换用户时各用户计时状态保持独立（计时器不中断）
- 全部 14 个 Entity、DAO 接口、Migration 脚本零改动
- 字符串必须资源化到 4 语系（values, values-zh-rCN, values-zh-rTW, values-zh-rHK）
- 编码规范：成员变量 `m` 前缀、静态 final 基本类型 `UPPER_SNAKE_CASE`
- 新建带点号样式必须显式声明 `parent` 属性

---

### Task 1: UserStore — 用户列表持久化

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/data/store/UserStore.java`

**Interfaces:**
- Produces: `UserStore` 类，提供以下方法：
  - `long getCurrentUserId()` — 获取当前活跃用户 ID
  - `void setCurrentUserId(long userId)` — 切换当前用户
  - `List<UserInfo> getAllUsers()` — 获取用户列表
  - `UserInfo addUser(String name)` — 新增用户（userId = System.currentTimeMillis()）
  - `UserInfo getUserInfo(long userId)` — 获取单个用户信息
  - `boolean hasUsers()` — 判断是否已有用户（平板首次启动判定）
  - `UserInfo` 内部类：`long userId`, `String name`, `long createdAt`

- [ ] **Step 1: 创建 UserStore.java**

```java
package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户列表持久化存储。独立 SharedPreferences 文件 "justnow_users"。
 * 不按用户隔离——所有用户共享同一份用户列表。
 */
public class UserStore {

    private static final String PREFS_NAME = "justnow_users";
    private static final String KEY_CURRENT_USER_ID = "current_user_id";
    private static final String KEY_USER_COUNT = "user_count";
    private static final String KEY_USER_NAME_PREFIX = "user_";
    private static final String KEY_USER_NAME_SUFFIX = "_name";
    private static final String KEY_USER_CREATED_SUFFIX = "_created";

    private final SharedPreferences mPrefs;

    public UserStore(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public long getCurrentUserId() {
        return mPrefs.getLong(KEY_CURRENT_USER_ID, -1);
    }

    public void setCurrentUserId(long userId) {
        mPrefs.edit().putLong(KEY_CURRENT_USER_ID, userId).apply();
    }

    public boolean hasUsers() {
        return mPrefs.getInt(KEY_USER_COUNT, 0) > 0;
    }

    public List<UserInfo> getAllUsers() {
        int count = mPrefs.getInt(KEY_USER_COUNT, 0);
        List<UserInfo> users = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String userIdStr = mPrefs.getString(KEY_USER_NAME_PREFIX + i + "_id", null);
            if (userIdStr == null) continue;
            long userId = Long.parseLong(userIdStr);
            String name = mPrefs.getString(userNameKey(userId), "");
            long createdAt = mPrefs.getLong(userCreatedKey(userId), 0);
            users.add(new UserInfo(userId, name, createdAt));
        }
        return users;
    }

    public UserInfo getUserInfo(long userId) {
        String name = mPrefs.getString(userNameKey(userId), null);
        if (name == null) return null;
        long createdAt = mPrefs.getLong(userCreatedKey(userId), 0);
        return new UserInfo(userId, name, createdAt);
    }

    public UserInfo addUser(String name) {
        long userId = System.currentTimeMillis();
        int count = mPrefs.getInt(KEY_USER_COUNT, 0);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(KEY_USER_NAME_PREFIX + count + "_id", String.valueOf(userId));
        editor.putString(userNameKey(userId), name);
        editor.putLong(userCreatedKey(userId), System.currentTimeMillis());
        editor.putInt(KEY_USER_COUNT, count + 1);
        editor.putLong(KEY_CURRENT_USER_ID, userId);
        editor.apply();
        return new UserInfo(userId, name, System.currentTimeMillis());
    }

    private static String userNameKey(long userId) {
        return KEY_USER_NAME_PREFIX + userId + KEY_USER_NAME_SUFFIX;
    }

    private static String userCreatedKey(long userId) {
        return KEY_USER_NAME_PREFIX + userId + KEY_USER_CREATED_SUFFIX;
    }

    /** 用户信息数据类 */
    public static class UserInfo {
        public final long userId;
        public final String name;
        public final long createdAt;

        public UserInfo(long userId, String name, long createdAt) {
            this.userId = userId;
            this.name = name;
            this.createdAt = createdAt;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/store/UserStore.java
git commit -m "feat: 新增 UserStore 用户列表持久化存储

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: UserPrefs — 按用户隔离 SharedPreferences

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/data/store/UserPrefs.java`

**Interfaces:**
- Produces: `UserPrefs` 工具类
  - `SharedPreferences getPrefs(Context ctx, long userId, String baseName)` — 返回 `justnow_prefs_<userId>` 等隔离后的 Prefs 实例
  - `SharedPreferences getGlobalPrefs(Context ctx, String baseName)` — 返回全局 Prefs（不按用户隔离，向后兼容）

- [ ] **Step 1: 创建 UserPrefs.java**

```java
package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 按用户 ID 获取隔离的 SharedPreferences 实例。
 * <p>
 * 规则：userId == 0 → 保持原文件名（向后兼容手机端单用户）
 *      userId != 0 → 文件名加 _<userId> 后缀
 */
public class UserPrefs {

    private UserPrefs() {}

    /**
     * 获取按用户隔离的 SharedPreferences。
     *
     * @param ctx      Context
     * @param userId   用户 ID（0 = 默认用户，文件名不变）
     * @param baseName 基础文件名（如 "justnow_prefs", "capsule_settings"）
     */
    public static SharedPreferences getPrefs(Context ctx, long userId, String baseName) {
        String name = userId == 0 ? baseName : baseName + "_" + userId;
        return ctx.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /**
     * 获取全局 SharedPreferences（不按用户隔离）。
     */
    public static SharedPreferences getGlobalPrefs(Context ctx, String baseName) {
        return ctx.getSharedPreferences(baseName, Context.MODE_PRIVATE);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -5
```
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/store/UserPrefs.java
git commit -m "feat: 新增 UserPrefs 按用户隔离 SharedPreferences 工具类

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: AppDatabase 多实例管理

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java`

**Interfaces:**
- Consumes: (无依赖——独立改造)
- Produces:
  - `static AppDatabase getInstance(Context ctx, long userId)` — 按 userId 获取/创建数据库实例
  - `static AppDatabase getInstance(Context ctx)` — 向后兼容重载，默认 userId=0
  - `static void clearInstance(long userId)` — 关闭并移除指定用户实例（切换用户时用）
  - `static void clearAllInstances()` — 关闭所有实例（进程销毁时用）

- [ ] **Step 1: 改造 AppDatabase.java**

读当前文件，定位 `sInstance` 和 `getInstance(Context)`，替换为多实例版本：

```java
// 改造前（删除）：
// private static AppDatabase sInstance;
// public static AppDatabase getInstance(Context ctx) { ... 单例逻辑 ... }

// 改造后（替换）：
private static final java.util.Map<Long, AppDatabase> sInstances =
    new java.util.concurrent.ConcurrentHashMap<>();

/**
 * 按用户 ID 获取数据库实例。每个用户拥有独立的数据库文件。
 *
 * @param ctx    Context
 * @param userId 用户 ID（0 = 默认用户，使用 justnow.db）
 */
public static AppDatabase getInstance(Context ctx, long userId) {
    AppDatabase existing = sInstances.get(userId);
    if (existing != null && existing.isOpen()) {
        return existing;
    }
    synchronized (AppDatabase.class) {
        existing = sInstances.get(userId);
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        String dbName = userId == 0
            ? DB_NAME
            : DB_NAME.replace(".db", "") + "_u" + userId + ".db";
        AppDatabase db = Room.databaseBuilder(
            ctx.getApplicationContext(), AppDatabase.class, dbName)
            .addMigrations(/* 现有 Migration 列表保持不变 */)
            .build();
        sInstances.put(userId, db);
        return db;
    }
}

/**
 * 向后兼容重载——默认用户 (userId=0)。
 */
public static AppDatabase getInstance(Context ctx) {
    return getInstance(ctx, 0);
}

/** 关闭并移除指定用户的数据库实例。 */
public static void clearInstance(long userId) {
    AppDatabase db = sInstances.remove(userId);
    if (db != null && db.isOpen()) {
        db.close();
    }
}

/** 关闭所有数据库实例。 */
public static void clearAllInstances() {
    for (java.util.Map.Entry<Long, AppDatabase> entry : sInstances.entrySet()) {
        if (entry.getValue().isOpen()) {
            entry.getValue().close();
        }
    }
    sInstances.clear();
}
```

注意：需保留现有的 `MIGRATION_X_Y` 列表和 `DB_NAME` 常量，仅将 `getInstance` 和 `sInstance` 替换。

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
预期：BUILD SUCCESSFUL（全部现有调用 `getInstance(ctx)` 仍走默认 userId=0）

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java
git commit -m "refactor: AppDatabase 单例改为按 userId 多实例管理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: JustNowApplication — 用户上下文管理

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/JustNowApplication.java`

**Interfaces:**
- Consumes: `UserStore`, `AppDatabase.getInstance(ctx, userId)`
- Produces:
  - `UserStore getUserStore()` — 获取用户列表管理器
  - `long getCurrentUserId()` — 获取当前活跃用户
  - `void switchToUser(long userId)` — 切换用户（清 Repository 缓存，通知观察者）
  - `void addUser(String name, Runnable onCreated)` — 创建用户并切换
  - `void ensureUserExists()` — 手机端首次启动自动创建默认用户

- [ ] **Step 1: 读当前 JustNowApplication.java**

```bash
# 已在上下文中，关键行在 ~30-80 行间的初始化逻辑
```

- [ ] **Step 2: 加 UserStore 字段和初始化**

在 `onCreate()` 中，数据库初始化之前插入：

```java
private UserStore mUserStore;

// 在 onCreate() 中：
mUserStore = new UserStore(this);
// 手机端：自动创建默认用户
if (!getResources().getBoolean(R.bool.is_tablet)) {
    if (!mUserStore.hasUsers()) {
        mUserStore.addUser(getString(R.string.s_default_user_name));
    }
}
```

- [ ] **Step 3: 加 getCurrentUserId() 和 getUserStore()**

```java
public long getCurrentUserId() {
    if (mUserStore == null) return 0;
    long id = mUserStore.getCurrentUserId();
    return id >= 0 ? id : 0;
}

public UserStore getUserStore() {
    return mUserStore;
}
```

- [ ] **Step 4: 加 switchToUser()**

```java
/**
 * 切换到指定用户。清除所有 ViewModel 级数据缓存。
 * 调用方需自行重建当前 Activity/Fragment UI。
 */
public void switchToUser(long userId) {
    mUserStore.setCurrentUserId(userId);
    // 清除 Repository 内存缓存（各 Repository 需提供 clearCache() 方法）
    clearRepositoryCaches();
}
```

- [ ] **Step 5: 加 addUser()**

```java
/**
 * 创建新用户并切换。回调在创建完成后执行。
 */
public void addUser(String name, Runnable onCreated) {
    UserStore.UserInfo info = mUserStore.addUser(name);
    switchToUser(info.userId);
    if (onCreated != null) {
        onCreated.run();
    }
}
```

- [ ] **Step 6: 修改所有 Repository getter——从 Application 获取 userId 并透传**

当前 Repository getter 形如：
```java
public TaskRepository getTaskRepository() {
    if (mTaskRepo == null) {
        mTaskRepo = new TaskRepository(getDatabase());
    }
    return mTaskRepo;
}
```

需要改为每用户独立缓存（`Map<userId, Repository>`）或每次传 userId。选择**每次传 userId**方案以最小改动（Repository 不缓存状态，仅缓存无状态实例）：

```java
// Repository 实例仍是 Application 级单例（无状态缓存层），
// 每个方法调用时从 Application.getCurrentUserId() 获取 userId
// 具体透传在 Task 5（Repository 层）实现
```

此处只需确保 `getCurrentUserId()` 在全部 Repository 方法中可访问。

- [ ] **Step 7: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
预期：BUILD SUCCESSFUL

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/JustNowApplication.java
git commit -m "feat: JustNowApplication 增加 UserStore 和当前用户上下文管理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Repository 层 — 全量透传 userId

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/BaseRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TagRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskExecutionRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskScheduleRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskSchedulePostponeRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskChecklistRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskAppActionRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskNoteShareRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TimePeriodRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/DisplayPolicyRepository.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/HolidayCacheRepository.java` (如存在)

**改造策略：最小侵入**

所有 Repository 当前通过 `AppDatabase.getInstance(Context)` 或 `AppDatabase` 引用获取 DAO。改造方式：

1. 每个 Repository 构造时记录 `Context`（已有则跳过）
2. 每个 public 方法内部第一行：`long userId = ((JustNowApplication) ctx.getApplicationContext()).getCurrentUserId();`
3. 所有 DAO 调用不变（DAO 查询无需 userId，因为数据库文件本身已物理隔离）

实际上 **DAO 层完全不需要改**——只要 Repository 每次从正确的 DB 实例拿 DAO，数据就是隔离的。

- [ ] **Step 1: 改造 BaseRepository**

```java
// 当前大致：
public class BaseRepository {
    protected final AppDatabase mDb;
    public BaseRepository(AppDatabase db) { mDb = db; }
}

// 改为持有 Context 引用以获取当前 userId：
public class BaseRepository {
    protected final Context mAppContext;
    public BaseRepository(AppDatabase db, Context appContext) {
        mDb = db;
        mAppContext = appContext;
    }
    protected long getCurrentUserId() {
        if (mAppContext instanceof JustNowApplication) {
            return ((JustNowApplication) mAppContext).getCurrentUserId();
        }
        return 0;
    }
    protected AppDatabase getDbForCurrentUser() {
        return AppDatabase.getInstance(mAppContext, getCurrentUserId());
    }
}
```

- [ ] **Step 2: 逐 Repository 改造**

以 `TaskRepository` 为例——将其 public 方法内的 `mDb.taskDao()` 调用替换为 `getDbForCurrentUser().taskDao()`，并清理内存缓存：

```java
// 在 TaskRepository 中加入清理方法供 Application.switchToUser() 调用：
public void clearCache() {
    synchronized (mCacheLock) {
        mCachedActiveTasks.clear();
    }
}
```

每个 Repository 仅需：
1. 改为继承新的 `BaseRepository`（如已继承则不变）
2. 将 `mDb.xxxDao()` → `getDbForCurrentUser().xxxDao()`
3. 如有内存缓存，加 `clearCache()` 方法

- [ ] **Step 3: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -15
```
预期：如有编译错误则逐个修复（主要是 import 缺失和方法签名变更）

- [ ] **Step 4: 运行现有单元测试确保不回归**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```
预期：全部现有测试通过

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/
git commit -m "refactor: Repository 层全量透传 userId，数据按用户物理隔离

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: ViewModel 层 — 获取当前用户

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/base/BaseViewModel.java`
- Modify: 全部 ViewModel（~10 个）

**改造策略：**

BaseViewModel 提供 `getCurrentUserId()` 便捷方法即可。各 ViewModel 在调用 Repository 方法时不需要显式传 userId——Repository 内部已从 Application 获取。

实际上 **ViewModel 层不需要改动**——因为 userId 的获取已经在 Repository 层通过 `getCurrentUserId()` 完成。ViewModel 只负责将 LiveData 从 Repository 暴露给 UI。

- [ ] **Step 1: 验证 BaseViewModel 不需要改动**

基类已持有 `JustNowApplication` 引用。只需确认 `getApplication()` → `getCurrentUserId()` 链路畅通。

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交（如有改动）**

> 如果 ViewModel 无实际改动，跳过此 Task 的提交。在最终提交信息中注明即可。

---

### Task 7: SharedPreferences 全线隔离

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/store/ChoreHiddenTodayStore.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/store/CutoffTimeStore.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/model/PeriodGroupRuleResolver.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/engine/PriorityTagConfig.java`
- Modify: `app/src/main/java/com/nearby/justnow/widget/WidgetFilterStore.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java`（`default_filter_tag_id`）
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（`capsule_settings`）

**改造策略：**

每个使用 `context.getSharedPreferences("xxx", MODE_PRIVATE)` 的地方，改为 `UserPrefs.getPrefs(context, userId, "xxx")`。

获取 userId 的方式：
- Store 类：构造加 userId 参数，或从 Application.getCurrentUserId() 获取
- Fragment/Activity：直接用 `((JustNowApplication) requireActivity().getApplication()).getCurrentUserId()`

- [ ] **Step 1: 逐文件改造**

以 `ChoreHiddenTodayStore` 为例：

```java
// 改造前：
SharedPreferences prefs = context.getSharedPreferences("justnow_prefs", Context.MODE_PRIVATE);

// 改造后：
long userId = ((JustNowApplication) context.getApplicationContext()).getCurrentUserId();
SharedPreferences prefs = UserPrefs.getPrefs(context, userId, "justnow_prefs");
```

MainFragment 中的 `capsule_settings`：

```java
// 改造前：
SharedPreferences sp = context.getSharedPreferences("capsule_settings", Context.MODE_PRIVATE);

// 改造后：
long userId = ((JustNowApplication) requireContext().getApplicationContext()).getCurrentUserId();
SharedPreferences sp = UserPrefs.getPrefs(requireContext(), userId, "capsule_settings");
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/store/ app/src/main/java/com/nearby/justnow/data/model/ app/src/main/java/com/nearby/justnow/ui/
git commit -m "refactor: SharedPreferences 全线按 userId 隔离

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: 照片目录按用户隔离

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`
- Modify: 拍照/FileProvider 相关代码（如 `MainFragment.java` 中的拍照逻辑）

**Interfaces:**
- Consumes: `JustNowApplication.getCurrentUserId()`
- Produces: 照片 URI 指向 `Pictures/JustNow/<userId>/` 子目录

- [ ] **Step 1: 改造 TaskPhotoRepository 目录路径**

```java
// 在 TaskPhotoRepository 中添加：
private java.io.File getPhotoDir() {
    long userId = ((JustNowApplication) mAppContext).getCurrentUserId();
    java.io.File dir;
    if (userId == 0) {
        dir = new java.io.File(mAppContext.getExternalFilesDir(
            android.os.Environment.DIRECTORY_PICTURES), "JustNow");
    } else {
        dir = new java.io.File(mAppContext.getExternalFilesDir(
            android.os.Environment.DIRECTORY_PICTURES), "JustNow/" + userId);
    }
    if (!dir.exists()) {
        dir.mkdirs();
    }
    return dir;
}
```

- [ ] **Step 2: 改造 FileProvider 路径生成**

搜索所有生成照片 URI 的代码（`FileProvider.getUriForFile`），确保路径使用 `getPhotoDir()`：

```bash
grep -rn "getUriForFile\|DIRECTORY_PICTURES\|Pictures/JustNow" app/src/main/java/ --include="*.java"
```

逐个替换。

- [ ] **Step 3: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
git commit -m "feat: 照片目录按 userId 隔离

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: 字符串资源化 + i18n

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`

- [ ] **Step 1: 加字符串资源**

在 4 个 `strings.xml` 中分别添加：

```xml
<!-- values/strings.xml (英文默认) -->
<string name="s_default_user_name">Me</string>
<string name="s_add_user">Add User</string>
<string name="s_add_user_hint">Enter name</string>
<string name="s_switch_user">Switch User</string>

<!-- values-zh-rCN/strings.xml (简体中文) -->
<string name="s_default_user_name">我</string>
<string name="s_add_user">添加用户</string>
<string name="s_add_user_hint">输入名称</string>
<string name="s_switch_user">切换用户</string>

<!-- values-zh-rTW/strings.xml (繁体中文-台湾) -->
<string name="s_default_user_name">我</string>
<string name="s_add_user">新增使用者</string>
<string name="s_add_user_hint">輸入名稱</string>
<string name="s_switch_user">切換使用者</string>

<!-- values-zh-rHK/strings.xml (繁体中文-香港) -->
<string name="s_default_user_name">我</string>
<string name="s_add_user">加入用户</string>
<string name="s_add_user_hint">輸入名稱</string>
<string name="s_switch_user">切換用户</string>
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
```
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh-rCN/strings.xml app/src/main/res/values-zh-rTW/strings.xml app/src/main/res/values-zh-rHK/strings.xml
git commit -m "feat: 多用户相关字符串四语资源化

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Toolbar 用户切换入口（平板）+ 首次启动用户创建

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`（Toolbar 区域加用户名 TextView）
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`（用户切换 PopupMenu 逻辑 + 首次启动强制创建用户）
- Modify: `app/src/main/res/layout/fragment_main_page0.xml`（如需要）

- [ ] **Step 1: 读当前 activity_main.xml 的 Toolbar 布局**

```bash
# 已在上下文中，确认 Toolbar 结构
```

- [ ] **Step 2: 在 Toolbar 菜单左侧加用户名按钮**

在 Toolbar 内的菜单 ImageButton 左侧加一个 TextView：

```xml
<TextView
    android:id="@+id/tv_current_user"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="end"
    android:layout_marginEnd="8dp"
    android:textAppearance="@style/TextAppearance.JustNow.Body"
    android:textColor="?android:attr/textColorPrimary"
    android:drawableEnd="@drawable/ic_arrow_drop_down"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true"
    tools:visibility="visible"
    tools:text="用户1" />
```

注意：`android:visibility="gone"` 默认隐藏，代码中根据 `is_tablet` 控制显示。

- [ ] **Step 3: 在 MainFragment 中实现切换逻辑**

```java
import android.widget.PopupMenu;
import android.widget.TextView;
import com.nearby.justnow.data.store.UserStore;

// 成员变量
private TextView mTvCurrentUser;
private UserStore mUserStore;

// 在 onViewCreated 中初始化：
mTvCurrentUser = view.findViewById(R.id.tv_current_user);
mUserStore = ((JustNowApplication) requireActivity().getApplication()).getUserStore();

// 平板端显示切换入口
if (getResources().getBoolean(R.bool.is_tablet)) {
    mTvCurrentUser.setVisibility(View.VISIBLE);
    // 首次启动无用户 → 弹出创建对话框
    if (!mUserStore.hasUsers()) {
        showCreateUserDialog(true); // true = 强制创建（不能取消）
    } else {
        updateCurrentUserDisplay();
    }
    mTvCurrentUser.setOnClickListener(v -> showUserSwitchMenu());
} else {
    // 手机端：确保默认用户存在
    if (!mUserStore.hasUsers()) {
        mUserStore.addUser(getString(R.string.s_default_user_name));
    }
}

// ---- 关键方法 ----

private void updateCurrentUserDisplay() {
    long userId = mUserStore.getCurrentUserId();
    UserStore.UserInfo info = mUserStore.getUserInfo(userId);
    if (info != null) {
        mTvCurrentUser.setText(info.name);
    }
}

private void showUserSwitchMenu() {
    PopupMenu popup = new PopupMenu(requireContext(), mTvCurrentUser);
    java.util.List<UserStore.UserInfo> users = mUserStore.getAllUsers();
    long currentId = mUserStore.getCurrentUserId();
    for (int i = 0; i < users.size(); i++) {
        UserStore.UserInfo user = users.get(i);
        String label = user.name;
        if (user.userId == currentId) {
            label += " ✓";
        }
        popup.getMenu().add(0, (int) user.userId, i, label);
    }
    popup.getMenu().add(1, -1, users.size(), R.string.s_add_user);
    popup.setOnMenuItemClickListener(item -> {
        if (item.getItemId() == -1) {
            showCreateUserDialog(false);
        } else {
            switchToUser(item.getItemId());
        }
        return true;
    });
    popup.show();
}

private void showCreateUserDialog(boolean forced) {
    android.widget.EditText input = new android.widget.EditText(requireContext());
    input.setHint(R.string.s_add_user_hint);
    AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
        .setTitle(R.string.s_add_user)
        .setView(input);
    if (forced) {
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.s_confirm, (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = getString(R.string.s_default_user_name);
            createAndSwitchUser(name);
        });
    } else {
        builder.setPositiveButton(R.string.s_confirm, (d, w) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) name = getString(R.string.s_default_user_name);
            createAndSwitchUser(name);
        });
        builder.setNegativeButton(R.string.s_cancel, null);
    }
    builder.show();
}

private void createAndSwitchUser(String name) {
    JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
    app.addUser(name, () -> {
        requireActivity().runOnUiThread(() -> {
            updateCurrentUserDisplay();
            // 重建 MainFragment 刷新数据
            refreshMainData();
        });
    });
}

private void switchToUser(long userId) {
    JustNowApplication app = (JustNowApplication) requireActivity().getApplication();
    app.switchToUser(userId);
    updateCurrentUserDisplay();
    // 重建数据视图
    refreshMainData();
}

private void refreshMainData() {
    // 通过 MainViewModel 重新加载当前用户的数据
    // 具体方式取决于当前 ViewModel 的刷新机制
    if (mViewModel != null) {
        mViewModel.reloadForCurrentUser();
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -15
```
预期：解决编译错误后 BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
git commit -m "feat: 平板 Toolbar 增加用户切换入口，首次启动强制创建用户

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: MainViewModel 用户切换数据刷新

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java`

- [ ] **Step 1: 添加 reloadForCurrentUser()**

```java
/**
 * 用户切换后重新加载数据。
 * 重新绑定所有 DAO 查询到新的数据库实例。
 */
public void reloadForCurrentUser() {
    long userId = getApplication().getCurrentUserId();
    AppDatabase db = AppDatabase.getInstance(getApplication(), userId);
    // 重新初始化所有 LiveData 源（重新查询）
    mAllTasks.removeSource(/* ... */);
    mAllTasks.addSource(db.taskDao().getAllTasksLive(), tasks -> {
        mCachedTasks = tasks;
        recompute();
    });
    // 其他 LiveData 同理
    // ...
    recompute();
}
```

- [ ] **Step 2: 编译 + 提交**

```bash
./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -10
git add app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java
git commit -m "feat: MainViewModel 支持用户切换后数据重新加载

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: 集成测试 + 手工验证

- [ ] **Step 1: 创建 UserStore 单元测试**

文件：`app/src/test/java/com/nearby/justnow/data/store/UserStoreTest.java`

```java
package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class UserStoreTest {

    private UserStore mUserStore;

    @Before
    public void setUp() {
        // 清理测试环境
        Context ctx = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = ctx.getSharedPreferences("justnow_users", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        mUserStore = new UserStore(ctx);
    }

    @Test
    public void testAddUser() {
        assertFalse(mUserStore.hasUsers());

        UserStore.UserInfo user = mUserStore.addUser("TestUser");
        assertNotNull(user);
        assertEquals("TestUser", user.name);
        assertTrue(user.userId > 0);
        assertTrue(mUserStore.hasUsers());
        assertEquals(user.userId, mUserStore.getCurrentUserId());
    }

    @Test
    public void testGetAllUsers() {
        mUserStore.addUser("User1");
        mUserStore.addUser("User2");
        List<UserStore.UserInfo> users = mUserStore.getAllUsers();
        assertEquals(2, users.size());
    }

    @Test
    public void testSwitchUser() {
        UserStore.UserInfo u1 = mUserStore.addUser("User1");
        mUserStore.addUser("User2");
        mUserStore.setCurrentUserId(u1.userId);
        assertEquals(u1.userId, mUserStore.getCurrentUserId());
    }

    @Test
    public void testGetUserInfo() {
        UserStore.UserInfo created = mUserStore.addUser("TestUser");
        UserStore.UserInfo fetched = mUserStore.getUserInfo(created.userId);
        assertNotNull(fetched);
        assertEquals(created.userId, fetched.userId);
        assertEquals("TestUser", fetched.name);
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./gradlew :app:testDebugUnitTest --tests "com.nearby.justnow.data.store.UserStoreTest" 2>&1 | tail -15
```
预期：4 tests PASS

- [ ] **Step 3: 创建 UserPrefs 单元测试**

文件：`app/src/test/java/com/nearby/justnow/data/store/UserPrefsTest.java`

```java
package com.nearby.justnow.data.store;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class UserPrefsTest {

    @Test
    public void testGetPrefs_defaultUser() {
        Context ctx = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = UserPrefs.getPrefs(ctx, 0, "test_prefs");
        assertNotNull(prefs);
        prefs.edit().putString("key", "value").commit();
        assertEquals("value", prefs.getString("key", null));
        // 清理
        prefs.edit().clear().commit();
    }

    @Test
    public void testGetPrefs_nonDefaultUser() {
        Context ctx = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = UserPrefs.getPrefs(ctx, 12345L, "test_prefs");
        assertNotNull(prefs);
        prefs.edit().putString("key", "u2value").commit();
        assertEquals("u2value", prefs.getString("key", null));
        // 清理
        prefs.edit().clear().commit();
    }

    @Test
    public void testGetPrefs_isolation() {
        Context ctx = RuntimeEnvironment.getApplication();
        SharedPreferences p0 = UserPrefs.getPrefs(ctx, 0L, "test_prefs");
        SharedPreferences p1 = UserPrefs.getPrefs(ctx, 99L, "test_prefs");
        p0.edit().putString("key", "v0").commit();
        p1.edit().putString("key", "v1").commit();
        assertEquals("v0", p0.getString("key", null));
        assertEquals("v1", p1.getString("key", null));
        // 清理
        p0.edit().clear().commit();
        p1.edit().clear().commit();
    }
}
```

- [ ] **Step 4: 运行测试**

```bash
./gradlew :app:testDebugUnitTest --tests "com.nearby.justnow.data.store.UserPrefsTest" 2>&1 | tail -15
```
预期：3 tests PASS

- [ ] **Step 5: 运行全部现有单元测试确保不回归**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -20
```
预期：ALL PASS

- [ ] **Step 6: 提交**

```bash
git add app/src/test/java/com/nearby/justnow/data/store/
git commit -m "test: UserStore 和 UserPrefs 单元测试

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: 模块文档 + 进度日志

**Files:**
- Create: `modules/multi-user.md`
- Create: `modules/multi-user_progress.md`
- Modify: `progress.md`

- [ ] **Step 1: 创建模块文档**

```markdown
# 阶段规划、决策记录 （拆分自 task_plan.md）

## 平板多用户支持
- **定位**：App 内多用户切换，每用户独立数据库+偏好+照片，零密码零登录
- **阶段状态**：
  - [x] Phase 1: 需求分析与脑暴 (完成 — 2026-07-18)
  - [x] Phase 2: 方案设计与 Spec 编写 (完成 — 2026-07-18)
  - [ ] Phase 3: 代码实现 (进行中)
  - [ ] Phase 4: 测试与验证

---

# 研究发现、技术决策、需求分析 （拆分自 findings.md）

## 方案选型

### 数据隔离：每用户独立 DB vs 单库加 userId 列
- **选择**：每用户独立数据库文件（`justnow_u<id>.db`）
- **理由**：14 张表、十几个 DAO、几十条查询全部零改动，出 bug 概率最小。代价仅为 Application 层多一层 Map 管理

### SharedPreferences 隔离
- **选择**：文件名加 `_<userId>` 后缀（userId=0 保持原文件名向后兼容）
- **理由**：编码简单，无需改动 Reader 侧代码逻辑

### 照片目录隔离
- **选择**：`Pictures/JustNow/<userId>/` 子目录
- **理由**：物理隔离最彻底，FileProvider 路径无需改动

## 约束
- 手机端：自动创建默认用户，不显示切换入口（`is_tablet` 控制）
- 平板端：首次启动强制创建用户
- 用户不可删除
- 切换时计时状态独立
```

- [ ] **Step 2: 创建模块进度文件**

```markdown
# 进度日志

### 2026-07-18 — 脑暴设计：多用户支持方案
- 确定方案 A：每用户独立数据库文件，Entity/DAO 零改动
- 设计文档：docs/superpowers/specs/2026-07-18-multi-user-design.md
- 实施计划：docs/superpowers/plans/2026-07-18-multi-user.md
```

- [ ] **Step 3: 追加 progress.md**

在 `progress.md` 最顶部插入（与其他模块相同格式）：

```markdown
### 2026-07-18 — 脑暴设计：多用户支持方案
- 确定方案 A（每用户独立数据库文件），设计文档和实施方案已准备就绪
- 详见 modules/multi-user.md、modules/multi-user_progress.md
```

- [ ] **Step 4: 提交**

```bash
git add modules/multi-user.md modules/multi-user_progress.md progress.md
git commit -m "docs: 多用户模块文档和进度日志

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 实施顺序总结

```
Task 1 (UserStore) ──┐
                     ├──→ Task 4 (Application) ──→ Task 9 (i18n 字符串)
Task 2 (UserPrefs) ──┤                                    │
                     │                                    ├──→ Task 10 (Toolbar UI)
Task 3 (AppDatabase) ┤                                    │
                     │                                    │
                     └──→ Task 5 (Repositories) ──→ Task 6 (ViewModels)
                              │
                              ├──→ Task 7 (SP 隔离)
                              │
                              └──→ Task 8 (照片目录)
                                                          │
                     Task 11 (ViewModel 刷新) ←────────────┘

                     Task 12 (测试) ← 以上全部完成之后

                     Task 13 (文档) ← 随时可做
```

- Task 1/2/3 互相独立，可并行
- Task 5 依赖 Task 3
- Task 7/8 依赖 Task 4 + Task 5（需要 Application 提供 userId）
- Task 10 依赖 Task 1/4/9
- Task 12 依赖全部完成
