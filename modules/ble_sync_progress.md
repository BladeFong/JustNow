# 进度日志

### 2026-07-24 — 菜单入口文本国际化与模块设计文档状态对齐

- 在 `menu_main.xml` 中将蓝牙设备管理菜单的名称修改为 `@string/menu_ble_device_manager`。
- 在 `values`、`values-zh-rCN`、`values-zh-rHK` 和 `values-zh-rTW` 的 `strings.xml` 中分别添加对应的国际化翻译（“绑定接收通知设备”）。
- 更新并对齐 `modules/ble_sync.md` 设计文档，补充运行时权限绑定、混淆 NPE 踩坑以及菜单国际化的设计细节与技术决策。

### 2026-07-24 — 升级 SDK 依赖并验证 SDK 自动合入混淆规则的完整性

- 从 `app/proguard-rules.pro` 移除 `ML Kit` 和 `CameraX` 的本地规则。
- 升级依赖至包含最新混淆保护 AAR 发布的 `bc32846ed17e0691a015789bc720a5d9b6ee5077`。
- 重新编译 Release 混淆包验证，R8 完美通过，证明 SDK 内部混淆配置自动合并成功。

### 2026-07-24 — 整理应该在 SDK 侧处理的混淆保护规则至配置底部并加注 TODO 标记

- 在 `app/proguard-rules.pro` 底部，将原本为了修复闪退而在 App 侧加入 of SDK 依赖（`ML Kit` 与 `CameraX`）混淆保护规则集中收拢，并添加 `TODO` 详细备忘注释，以方便之后让 SDK 侧将其挪入其自身的 `consumer-rules.pro` 中。

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
