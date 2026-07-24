# BleNotificationSync SDK 集成与桌面通知同步设计文档

## 1. 概述与目标

在 `JustNow` Android 客户端中集成公开的 `BleNotificationSync` SDK，实现手机端的任务到期提醒与专注任务超时通知实时推送到配对的桌面端（Windows / macOS / Linux）。

核心目标：
- 采用 **JitPack 线上依赖** 引入开源 SDK，无本地路径关联及私有凭证配置。
- 在手机主界面右上角添加“桌面设备同步”入口，可扫描二维码绑定桌面设备；在平板形态下自动隐藏。
- 代理 `ReminderNotifier` 的通知发送逻辑，使用 SDK 升级后的 `sendNotification(builder, notificationId, callback)` API，传入本项目原有的 `notificationId` 以确保通知的可控与正确注销。

---

## 2. 依赖引入与全局配置

### 2.1 `settings.gradle.kts`
在 `dependencyResolutionManagement.repositories` 中添加 JitPack 线上仓库：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2.2 `app/build.gradle.kts`
在 `dependencies` 中添加 `BleNotificationSync` 开源库（使用公开版本）：

```kotlin
dependencies {
    implementation("com.github.BladeFong:BleNotificationSync:fc91c446c0eb346eec980d930800d5841ac14012")
}
```

### 2.3 `JustNowApplication.java`
在 `onCreate()` 中进行 SDK 全局初始化：

```java
@Override
public void onCreate() {
    super.onCreate();
    // ...
    BleNotificationSDK.Companion.init(this);
}
```

---

## 3. UI 设备管理入口与平板适配

### 3.1 菜单入口设计
- **菜单位置**：`MainFragment` 右上角 Toolbar 菜单（`R.menu.menu_main`）。
- **菜单项定义**：`action_ble_device_manager` ("桌面设备同步")。

### 3.2 平板模式隔离
在 `MainFragment.onPrepareOptionsMenu(Menu menu)` 中，通过 `DeviceUtils.isTablet(requireContext())` 进行判断：
- **手机形态**（`isTablet == false`）：正常显示 `action_ble_device_manager` 菜单项。
- **平板形态**（`isTablet == true`）：设 `menu.findItem(R.id.action_ble_device_manager).setVisible(false)`。

### 3.3 交互逻辑
点击 `action_ble_device_manager` 菜单项时：
```java
BleNotificationSDK.Companion.getInstance().openDeviceManager(requireContext());
```
直接打开 SDK 内置的扫码绑定与设备管理 Activity。

---

## 4. 通知发送代理机制

### 4.1 核心切入点
在 `com.nearby.justnow.broadcast.ReminderNotifier` 中：

1. **到期提醒通知 (`ReminderNotifier.send`)**：
   将原有的 `NotificationManagerCompat.from(context).notify(...)` 替换为调用：
   ```java
   BleNotificationSDK.Companion.getInstance().sendNotification(
       builder, 
       notificationId(schedule.id), 
       null
   );
   ```

2. **专注任务超时通知 (`ReminderNotifier.sendOvertime`)**：
   将原有的 `NotificationManagerCompat.from(context).notify(...)` 替换为调用：
   ```java
   BleNotificationSDK.Companion.getInstance().sendNotification(
       builder, 
       (int) (task.id + 8000), 
       null
   );
   ```


### 4.2 内部行为说明
- SDK 的 `sendNotification(builder, notificationId, null)` 会优先在本地执行 `NotificationManager.notify()`，保障手机 Native 系统通知 100% 弹出。
- 随后抽取 `builder` 中的 `EXTRA_TITLE` 和 `EXTRA_TEXT`，如果有绑定的桌面端设备，在后台服务中异步完成 BLE 消息包的广播与传输。
- 传 `null` 表示不需要在宿主侧处理 BLE 回调，保证调用简洁且零阻塞。

---

## 5. 校验与验证方案

1. **编译校验**：执行 `./gradlew assembleDebug`，确保 JitPack 依赖顺利拉取并无 Class 冲突。
2. **UI 验证**：
   - 手机形态下，主界面右上角菜单可看到“桌面设备同步”入口，点击可唤起扫码/设备管理页。
   - 平板形态下，确认菜单项已被隐藏。
3. **通知验证**：到期任务触发 notification 时，手机上原生通知正常显示，并且开启 Logcat 可观察到 BLE 发送动作。
