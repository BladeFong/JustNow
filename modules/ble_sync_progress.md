# 进度日志

### 2026-07-24 — 补充 ML Kit 扫码与 CameraX 的混淆保护规则以修复运行闪退

- 在 `app/proguard-rules.pro` 写入 ML Kit 扫码库底层类的 keep 保护，解决 Release 混淆下 `BarcodeScanning.getClient()` 触发的 `getClass()` 空指针闪退问题。
- 一并追加 `androidx.camera` 包的完整 Proguard 保护配置，确保相机的生命周期和初始化稳定。

### 2026-07-24 — 补全 MainActivity 的 SDK 权限检查与 Launcher 注册

- 在 `MainActivity.java` 导入 `BleNotificationSDK`。
- 在 `MainActivity.onCreate()` 阶段调用 `registerPermissionLaunchers` 统一注册所需的所有位置及蓝牙权限 Launcher。
- 在 `MainActivity.onResume()` 阶段调用 `ensurePermissions` 触发应用启动时的权限动态检查与引导。

### 2026-07-24 — 蓝牙通知同步集成与编译验证全部完成

- 解决 SDK 依赖在 JitPack 上由于 git submodule 和 maven-publish 引起的编译失败问题，锁定最新可用版本 `fc91c446c0eb346eec980d930800d5841ac14012`。
- 完成主界面 `MainFragment` 的菜单逻辑重构及平板隐藏，点击拉起设备管理绑定。
- 重构 `ReminderNotifier`，将到期提醒和超时提醒原生 `notify` 成功代理给 SDK 的 `sendNotification` 发送，实现 Native 通知正常弹出且蓝牙后台静默同步。
- 完整通过项目 `./gradlew compileDebugJavaWithJavac` 编译验证，所有接口参数和类型完全匹配。

### 2026-07-24 — 蓝牙通知同步集成设计完成与实现启动

- 写入设计文档 `docs/superpowers/specs/2026-07-24-ble-notification-sync-integration-design.md`
- 确定 JitPack 依赖引入方案，以及使用 `sendNotification(builder, notificationId, null)` 进行通知同步代理的决策
- 主界面右上角添加设备管理菜单，且通过 isTablet 在平板形态上予以过滤
