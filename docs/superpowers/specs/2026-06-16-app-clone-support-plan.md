# 应用分身支持实现计划

> 设计文档：[2026-06-16-app-clone-support-design.md](2026-06-16-app-clone-support-design.md)

## 阶段 1：AppLaunchCatalogCache 查询分身应用

**目标**：应用列表查询时识别分身应用。

**改动**：
1. `AppLaunchCatalogCache.loadInBackground()`：
   - 获取 `UserManager`，调用 `getUserProfiles()` 获取所有用户
   - 反射调用 `pm.queryIntentActivitiesAsUser(intent, 0, userId)` 遍历每个用户
   - 主空间应用（userId=0）正常添加
   - 分身应用（userId>0）label 后加"（分身）"

2. `AppLaunchCatalogCache.AppInfo`：
   - 新增 `int userId` 字段
   - 构造函数增加 `userId` 参数

3. `AppLaunchCatalogCache.findByPackageName()`：
   - 保留原方法（仅匹配主空间 userId=0）
   - 新增 `findByPackageNameAndUserId(String packageName, int userId)`

4. `AppLaunchCatalogCache.filter()`：
   - 匹配时同时考虑 label 和 packageName（分身应用 label 包含"（分身）"，自然可被搜到）

## 阶段 2：TaskAppAction 存储分身应用

**目标**：选择分身应用后，deepLink 存储带 userId 的 intent URI。

**改动**：
1. `TaskInputAppActionSheet`：
   - 选择分身应用时，生成带 `S.launch_user_id` extra 的 intent URI
   - 格式：`intent:#Intent;launchFlags=0x10000000;package=xxx;S.launch_user_id=999;end`
   - 存入 `TaskAppAction.deepLink`

2. 无需修改 `TaskAppAction` schema，复用 `deepLink` 字段

## 阶段 3：ReminderDetailActivity 显示分身标识

**目标**：详情页加载时动态添加"（分身）"标识。

**改动**：
1. `ReminderDetailActivity.AppActionAdapter.onBindViewHolder()`：
   - 解析 deepLink 中的 `launch_user_id`
   - 如果存在，在应用名后添加"（分身）"标识

2. 跳转使用系统默认行为（弹出选择器），原因：普通应用没有 `INTERACT_ACROSS_USERS` 权限，无法调用 `startActivityAsUser()`

## 阶段 4：编译验证

**验证**：
- `compileDebugJavaWithJavac` 通过
- 无分身设备：主空间应用行为不变
- 有分身设备：列表显示"（分身）"标识，跳转时系统弹出选择器

## 依赖关系

```
阶段 1 (查询) → 阶段 2 (存储) → 阶段 3 (显示) → 阶段 4 (验证)
```

## 文件清单

| 文件 | 改动 |
|------|------|
| `AppLaunchCatalogCache.java` | 反射查询分身、AppInfo 增加 userId、新增 findByPackageNameAndUserId |
| `TaskInputAppActionSheet.java` | 选择分身应用时生成带 userId 的 deepLink |
| `ReminderDetailActivity.java` | 解析 deepLink 动态添加"（分身）"标识 |
