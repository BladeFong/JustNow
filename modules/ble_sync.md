# 模块设计：BleNotificationSync 蓝牙通知同步集成

# 阶段规划、决策记录 （拆分自 task_plan.md）

**定位与功能描述**：
将开源的 `BleNotificationSync` SDK 集成到 `JustNow` 中。当任务到期或专注任务超时在手机端触发系统通知时，同步通过 BLE 推送到配对的桌面端（Windows / macOS / Linux）并显示。并在手机端右上角提供“桌面设备同步”菜单入口（平板模式下隐藏该菜单）。

**阶段规划**：
- **阶段 1：依赖引入与初始化**：在 `settings.gradle.kts` 添加 JitPack 仓库，在 `app/build.gradle.kts` 引入依赖，并在 `JustNowApplication` 初始化 SDK。
- **阶段 2：UI 菜单配置与设备适配**：在 `MainFragment` 的右上角 Toolbar 菜单添加设备同步管理项，并通过 `DeviceUtils.isTablet()` 在平板设备上自动隐藏该项。
- **阶段 3：通知代理重构**：将 `ReminderNotifier.java` 中原本直接弹出的到期通知与超时通知，代理给 SDK 的 `sendNotification(builder, notificationId, callback)` 方法，以保证 Native 弹出与 BLE 推送同步进行且不影响原本的通知注销。
- **阶段 4：联调与清理**：在模拟器/真机上验证依赖加载，检查日志及菜单状态，确认功能正常。

---

# 研究发现、技术决策、需求分析 （拆分自 findings.md）

### 1. 依赖管理选型
- **选型方案**：使用 **implementation("com.github.BladeFong:BleNotificationSync:master-SNAPSHOT")** 引入。
- **决策取舍**：由于 SDK 仓库已开源，线上依赖引入最为干净，可避免引入本地绝对路径 `includeBuild`，从而不影响其他协作者的编译，且无需配置任何私有 GitHub 凭证（Token）。

### 2. 通知发送 API 对齐
- **根本原因**：`JustNow` 自建了带有“开始/忽略/延迟”交互 Action 按钮的 `NotificationCompat.Builder` 通知。如果采用 SDK 原有不带 Builder 传入的 API，会导致 Native 通知样式缺失或 notificationId 冲突无法取消。
- **技术决策**：使用 SDK 最新升级的重载方法：
  ```kotlin
  fun sendNotification(
      builder: NotificationCompat.Builder,
      notificationId: Int? = null,
      callback: SendCallback? = null
  )
  ```
  通过显式传递 `notificationId`（如任务到期通知的 `notificationId(schedule.id)` 和超时通知的 `task.id + 8000`），完美保留 `JustNow` 自身对通知生命周期的完全掌控（可通过 ID 精确 cancel）。

### 3. 平板形态下的入口控制
- **需求取舍**：平板端不需要显示同步入口。
- **技术决策**：在 `MainFragment.onPrepareOptionsMenu` 阶段拦截，如果 `DeviceUtils.isTablet(context)` 为真，则直接将菜单项的 visible 设为 false，确保零侵入性。

### 4. 运行时权限生命周期绑定
- **根本原因**：SDK 内部需要使用 `registerForActivityResult` 来注册位置与蓝牙权限的回调。根据 Android 官方规范，这**必须在 Activity 创建（`onCreate`）或 `onStart` 时完成**，否则在后续请求时会抛出崩溃。
- **技术决策**：在宿主 `MainActivity` 中：
  - 在 `onCreate` 阶段执行 `BleNotificationSDK.Companion.getInstance().registerPermissionLaunchers(this)`。
  - 在 `onResume` 阶段执行 `BleNotificationSDK.Companion.getInstance().ensurePermissions(this)`，以便在每次进入主界面时，对必要权限进行统一的动态检查和弹窗引导。

### 5. 混淆保护与第三方库 Proguard 踩坑
- **SnakeYAML 混淆 NPE**：
  - **原因**：宿主直接引用的 `org.yaml:snakeyaml` 依赖在 R8 混淆（开启 Full Mode）时包名元数据被剔除，导致执行 `TypeDescription.class.getPackage()` 时返回了 `null`，进而在 `.getName()` 处触发空指针闪退。
  - **决策**：在宿主 `app/proguard-rules.pro` 中增加对 `org.yaml.snakeyaml` 类的 Keep 规则，并保留 `Signature` 等属性。
- **ML Kit & CameraX 自动混淆**：
  - **原因**：SDK 自带的 `barcode-scanning` 和 `CameraX` 在宿主混淆编译时，ML Kit 内部子类字段被剥离，导致 `BarcodeScanning.getClient()` 发生空指针闪退。
  - **决策**：将这些属于 SDK 自持依赖的混淆规则（`com.google.mlkit.**`、`com.google.android.gms.**` 及 `androidx.camera.**`）从宿主 Proguard 中移除，统一挪入 SDK 模块的 `consumer-rules.pro` 中，由 AAR 打包发布后自动向宿主合并。

### 6. 入口文本多语言国际化
- **决策**：将右上角同步入口菜单项由硬编码改为 `@string/menu_ble_device_manager`。
  - 简体中文（`values-zh-rCN`）："绑定通知设备"
  - 繁体中文（`values-zh-rHK`/`values-zh-rTW`）："綁定通知設備"
  - 默认英文（`values`）："Bind Notification Device"
