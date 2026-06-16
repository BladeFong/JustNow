# 应用分身支持设计

## 背景

JustNow 的 APP 跳转功能（`TaskAppAction`）当前不支持双开/分身应用。用户在小米等设备上开启应用分身后，无法在应用列表中看到分身应用，也无法跳转到分身应用。

## 目标

1. 应用列表查询时能识别分身应用
2. 分身应用在列表中显示为"应用名（分身）"
3. 选择分身应用后能正确跳转

## 技术方案

### 1. 查询分身应用

**现状**：`AppLaunchCatalogCache.loadInBackground()` 使用 `pm.queryIntentActivities(intent, 0)` 只查询主空间应用。

**方案**：使用 `UserManager.getUserProfiles()` 获取所有用户/配置文件，对每个用户调用 `PackageManager.queryIntentActivitiesAsUser()`。

```java
// 获取所有用户
UserManager um = context.getSystemService(UserManager.class);
List<UserHandle> users = um.getUserProfiles();

// 查询每个用户的应用
for (UserHandle user : users) {
    int userId = user.getIdentifier();
    List<ResolveInfo> apps = pm.queryIntentActivitiesAsUser(intent, 0, userId);
    // 处理应用列表...
}
```

**兼容性**：
- Android 5.0+：`UserManager.getUserProfiles()` 可用
- Android 14+：支持 `USER_TYPE_PROFILE_CLONE`（官方 Clone Profile）
- 各厂商双开：基于多用户机制，userId 不同（如小米双开 userId=999）

### 2. 存储分身应用信息

**现状**：`TaskAppAction` 只有 `packageName` 和 `deepLink`，无法区分分身。

**方案**：复用 `deepLink` 字段，存储带 userId 的 intent URI。

**格式**：
```
intent:#Intent;launchFlags=0x10000000;package=com.tencent.mm;S.launch_user_id=999;end
```

注意：`S.` 前缀表示 String 类型 extra，`Intent.parseUri()` 会自动解析到 Intent 的 extras 中。

**优点**：
- 无需修改数据库 schema
- 与现有 deepLink 解析逻辑兼容
- 跳转时直接解析 intent URI

### 3. 显示分身应用

**现状**：`AppInfo` 只有 `packageName`、`label`、`icon`。

**方案**：`AppInfo` 增加 `userId` 字段，分身应用名后加"（分身）"。

```java
public static class AppInfo {
    public final String packageName;
    public final String label;
    public final Drawable icon;
    public final int userId;  // 新增

    public AppInfo(String packageName, String label, Drawable icon, int userId) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.userId = userId;
    }
}
```

**显示逻辑**：
- 主空间应用（userId=0）：显示原始应用名
- 分身应用（userId>0）：显示"应用名（分身）"

### 4. 跳转分身应用

**现状**：`ReminderDetailActivity` 使用 `pm.getLaunchIntentForPackage()` 或 `Intent.parseUri(deepLink)` 跳转。

**方案**：使用系统默认行为，由系统弹出选择器让用户选择本体或分身应用。

**原因**：普通应用没有 `INTERACT_ACROSS_USERS` 权限，无法调用 `startActivityAsUser()` 跳转到其他用户空间。系统会自动检测到有多个用户空间（主空间 + 分身空间），弹出选择器让用户选择。

**显示标识**：详情页加载时解析 deepLink 中的 `launch_user_id`，动态添加"（分身）"标识，帮助用户区分本体和分身。

### 5. 匹配分身应用

**现状**：`findByPackageName()` 只按包名匹配。

**方案**：增加 `findByPackageNameAndUserId()` 方法，同时匹配包名和 userId。

```java
public AppInfo findByPackageNameAndUserId(String packageName, int userId) {
    synchronized (mLock) {
        for (AppInfo app : mApps) {
            if (packageName.equals(app.packageName) && app.userId == userId) {
                return app;
            }
        }
    }
    return null;
}
```

## 修改文件清单

1. **AppLaunchCatalogCache.java**
   - `loadInBackground()`：反射调用 `queryIntentActivitiesAsUser()` + `getIdentifier()` 遍历所有用户
   - `AppInfo`：增加 `userId` 字段
   - `findByPackageNameAndUserId()`：新增方法
   - 分身应用 label 后加"（分身）"

2. **TaskInputAppActionSheet.java**
   - 选择分身应用时生成带 `S.launch_user_id` extra 的 deepLink

3. **ReminderDetailActivity.java**
   - `AppActionAdapter.onBindViewHolder()`：解析 deepLink 中的 `launch_user_id`，动态添加"（分身）"标识
   - 跳转使用系统默认行为（弹出选择器）

4. **UriParser.java**
   - 无需改动

## 边界情况

1. **主空间应用**：userId=0，行为不变
2. **分身应用**：userId>0，显示"（分身）"标识，跳转时系统弹出选择器
3. **多用户设备**：如工作资料（Work Profile），也应支持
4. **应用卸载**：分身应用卸载后，已保存的跳转应提示失败

## 测试场景

1. 小米设备开启应用分身，验证列表显示和跳转
2. 无分身设备，验证主空间应用行为不变
3. 分身应用卸载后，验证跳转失败提示
