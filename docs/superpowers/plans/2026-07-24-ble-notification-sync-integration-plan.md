# BleNotificationSync SDK 集成与桌面通知同步实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 JustNow 项目中集成公开的 BleNotificationSync 蓝牙通知同步 SDK，并在手机端支持右上角绑定桌面设备，以及在任务提醒与超时通知时同步推送 BLE 通知。

**Architecture:** 
1. 通过 settings.gradle.kts 配置 JitPack 远端仓库，通过 app/build.gradle.kts 引入 `com.github.BladeFong:BleNotificationSync:v1.0.0`，并在 `JustNowApplication` 进行初始化。
2. 更改 `menu_main.xml` 增加设备管理菜单项，并在 `MainFragment` 的 `onPrepareOptionsMenu` 和 `onOptionsItemSelected` 中控制平板设备的隐藏以及点击跳转。
3. 代理 `ReminderNotifier` 中到期通知与超时通知的 notify 发送逻辑，重构为调用 SDK 带有 notificationId 的 `sendNotification` 重载方法。

**Tech Stack:** Gradle, Kotlin/Java, Android SDK, BleNotificationSync SDK

## Global Constraints

- 保证本地手机端 Native 系统通知 100% 弹出且不被 BLE 推送失败阻塞。
- 本地系统通知的 `notificationId` 由宿主 App（即本项目）决定，以便后续取消逻辑（cancel）正常运作。
- 手机端右上角显示绑定菜单项，平板端隐藏（使用 DeviceUtils.isTablet() 过滤）。

---

### Task 1: Gradle 依赖引入与全局 SDK 初始化

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/nearby/justnow/JustNowApplication.java`

- [ ] **Step 1: 添加 JitPack 仓库**
  在 `settings.gradle.kts` 的 `dependencyResolutionManagement.repositories` 底部，引入 `jitpack.io`：
  ```kotlin
  maven { url = uri("https://jitpack.io") }
  ```

- [ ] **Step 2: 声明 SDK 开源依赖**
  在 `app/build.gradle.kts` 的 `dependencies` 块中添加 `BleNotificationSync`：
  ```kotlin
  implementation("com.github.BladeFong:BleNotificationSync:bc32846ed17e0691a015789bc720a5d9b6ee5077")
  ```

- [ ] **Step 3: 初始化 SDK**
  在 `app/src/main/java/com/nearby/justnow/JustNowApplication.java` 的 `onCreate()` 中，紧接在 `ReminderNotifier.createChannel(this);` 之后添加 SDK 初始化：
  ```java
  // 导入 SDK
  import com.ble.notification.sdk.BleNotificationSDK;
  
  // 在 onCreate 内部调用
  BleNotificationSDK.Companion.init(this);
  ```

- [ ] **Step 4: 编译检查**
  在项目根目录运行 `./gradlew assembleDebug`，确保 JitPack 依赖能够顺利解析拉取并编译通过。

---

### Task 2: UI 绑定菜单增加与平板设备隐藏

**Files:**
- Modify: `app/src/main/res/menu/menu_main.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

- [ ] **Step 1: 新增菜单项到 menu_main.xml**
  在 `app/src/main/res/menu/menu_main.xml` 的底部添加“桌面设备同步”菜单项：
  ```xml
      <item
          android:id="@+id/action_ble_device_manager"
          android:title="桌面设备同步"
          app:showAsAction="never" />
  ```

- [ ] **Step 2: 在 MainFragment 中过滤平板模式**
  在 `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java` 中的 `onPrepareOptionsMenu(Menu menu)`，添加对平板形态的隐藏过滤：
  ```java
  MenuItem bleItem = menu.findItem(R.id.action_ble_device_manager);
  if (bleItem != null) {
      boolean isTablet = com.nearby.justnow.util.DeviceUtils.isTablet(requireContext());
      bleItem.setVisible(!isTablet);
  }
  ```

- [ ] **Step 3: 处理菜单项点击跳转**
  在 `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java` 的 `onOptionsItemSelected(MenuItem item)` 中，增加对 `action_ble_device_manager` 点击事件的捕获：
  ```java
  if (id == R.id.action_ble_device_manager) {
      com.ble.notification.sdk.BleNotificationSDK.Companion.getInstance().openDeviceManager(requireContext());
      return true;
  }
  ```

---

### Task 3: 通知代理重构与 ID 自定义对齐

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java`

- [ ] **Step 1: 重构到期通知 ReminderNotifier.send**
  在 `app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java` 的 `send()` 底部：
  
  将原本的：
  ```java
  NotificationManagerCompat.from(context).notify(notificationId(schedule.id), builder.build());
  ```
  替换为调用 SDK 方法，并将原有的 `notificationId(schedule.id)` 传入：
  ```java
  com.ble.notification.sdk.BleNotificationSDK.Companion.getInstance().sendNotification(
      builder,
      notificationId(schedule.id),
      null
  );
  ```

- [ ] **Step 2: 重构超时通知 ReminderNotifier.sendOvertime**
  在 `app/src/main/java/com/nearby/justnow/broadcast/ReminderNotifier.java` 的 `sendOvertime()` 底部：
  
  将原本的：
  ```java
  NotificationManagerCompat.from(context).notify((int) (task.id + 8000), builder.build());
  ```
  替换为调用 SDK 方法，将原有的 `(int) (task.id + 8000)` 传入：
  ```java
  com.ble.notification.sdk.BleNotificationSDK.Companion.getInstance().sendNotification(
      builder,
      (int) (task.id + 8000),
      null
  );
  ```

- [ ] **Step 3: 清理未使用或冲突的 Import**
  确保 `ReminderNotifier.java` 中导入了 `com.ble.notification.sdk.BleNotificationSDK`。
