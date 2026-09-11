<div align="center">

# Just Now (恰恰有事)

**Right here, right now / 刚好，手边有事**

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Storage: 100% Offline](https://img.shields.io/badge/Storage-100%25%20Offline%20Local-success.svg)](#-data-storage--permissions)
[![Version](https://img.shields.io/badge/Version-v1.0-orange.svg)](https://github.com/BladeFong/JustNow/releases)

<p align="center">
  <a href="README.md"><b>简体中文</b></a> | <a href="README_EN.md"><b>English</b></a>
</p>

</div>

---

## 📖 Overview

**Just Now** is an open-source task and time management Android application designed for personal focus and family tablets.

Based on customizable time periods, statutory holiday schedules, and remaining available time, the app dynamically ranks and recommends tasks best suited for the current moment. For family tablet scenarios, it features a gamified "Seven Flowers" reward system, a "Time Capsule" photo gallery, zero-install QR photo uploads over local Wi-Fi, and BLE notification synchronization.

All data is stored locally via SQLite/Room with zero server dependencies and no account requirements, ensuring absolute privacy for personal and family data.

<div align="center">
  <img src="assets/readme/hero_preview.png" alt="Just Now Mobile and Tablet Interface Preview" width="85%" />
  <p><em>▲ Just Now: Mobile Focus Timeline and Tablet Family Reward Views</em></p>
</div>

---

## ⚙️ Core Mechanisms

- **Time-Period Awareness & Dynamic Ranking**: Divides the day into distinct time periods (e.g., Morning, Work Focus, Evening Leisure). The system evaluates holiday/workday calendars and remaining period time to compute recommended tasks dynamically.
- **Completion Quota Visibility Control**: Supports daily, weekly, monthly, and yearly completion quotas for recurring tasks. Once the quota is met within the current period, the task is automatically collapsed from the active list and restored in the next cycle.
- **100% Local Offline Storage**: All tasks, execution logs, configurations, and photos are stored within the device's private storage, with zero remote server connections or analytics trackers.

---

## 📱 Mobile Features

### 1. Smart Time-Periods & Dynamic Timeline
- **Period & Holiday Recognition**: Built-in statutory holiday and workday adjustment parsers for Mainland China, Hong Kong, Macau, and Apple Calendar ICS standards. Time-period rules automatically switch between workdays and holidays.
- **Timeline & Fluid Progress**: A vertical timeline displays today's tasks and execution history, with fluid color blocks indicating elapsed progress and remaining minutes in real time.

<div align="center">
  <img src="assets/readme/phone_timeline_stream.png" alt="Mobile Dynamic Timeline and Time Periods" width="45%" />
  <p><em>▲ Dynamic Timeline and Remaining Time Indicator</em></p>
</div>

### 2. Eisenhower Matrix & Completion Trends
- **Four-Quadrant Classification**: Categorizes tasks by Important/Urgent quadrants ("Do First", "Schedule", "Delegate", "Don't Do").
- **10-Cycle Trend Analysis**: Provides completion rate trend charts across the last 10 cycles for quota-enabled tasks, helping track habit consistency over time.

<div align="center">
  <img src="assets/readme/phone_quadrant_quota.png" alt="Four-Quadrant Management and Trend Chart" width="45%" />
  <p><em>▲ Eisenhower Matrix and 10-Cycle Completion Rate Trend Chart</em></p>
</div>

### 3. Immersive Task Input & App Linking
- **Markdown Notes & Checklists**: Edit and render rich Markdown notes directly within tasks, along with step-by-step checklist item management.
- **App Actions & Dual App (Clone) Support**: Bind third-party package names or Intent DeepLinks to tasks for one-click launching; recognizes and supports cloned apps on multi-user Android devices.

<div align="center">
  <img src="assets/readme/phone_task_editor_markdown.png" alt="Task Editor with Markdown Notes and Checklist" width="45%" />
  <p><em>▲ Task Entry, Markdown Notes, and Checklist Management</em></p>
</div>

### 4. AppWidget & BLE Notification Sync
- **Android AppWidget**: Place a homescreen widget to check current recommended tasks and mark completions without opening the app.
- **BLE Notification Synchronization**: Integrates the `BleNotificationSync` SDK to mirror scheduled task alarms directly to connected Bluetooth Low Energy wearables and peripherals.

<div align="center">
  <img src="assets/readme/phone_widget_and_ble.png" alt="AppWidget and BLE Synchronization" width="45%" />
  <p><em>▲ Homescreen AppWidget and BLE Device Notification Sync</em></p>
</div>

---

## 📟 Tablet & Family Features

### 1. Adaptive Layout & Child Activity Icons
- **Orientation & Grid Adaptation**: Fully supports screen rotation on tablets, adjusting the task grid between 3 columns (landscape) and 2 columns (portrait).
- **10 Built-in Activity Icons**: Features vector icons for reading, board games, sports, crafts, chores, art, music, homework, animation, and toys, with switchable "Vibrant Blue" and "Playful Pink" global themes.

<div align="center">
  <img src="assets/readme/tablet_landscape_overview.png" alt="Tablet Landscape Layout" width="75%" />
  <p><em>▲ Tablet Adaptive Landscape Layout with Child Activity Cards</em></p>
</div>

### 2. Seven Flowers Reward System
- **Petal Accumulation**: Taking a verification photo upon task completion lights up a petal for the corresponding day.
- **Workday Flower Center Pre-fill**: On non-vacation workdays (including official make-up workdays), flower centers are automatically pre-filled.
- **Weekly Target Goals**: Configure weekly target days (2–5 days). Achieving the target triggers a celebration dialog.

<div align="center">
  <img src="assets/readme/tablet_flower_rewards.png" alt="Seven Flowers Reward System" width="75%" />
  <p><em>▲ Flower Petal Lighting Mechanism and Weekly Goal Configuration</em></p>
</div>

### 3. Time Capsule Photo Gallery
- **Read-Only Achievement Gallery**: Displays task completion photos in a chronological photo wall, preserving daily milestones and habit accomplishments.

<div align="center">
  <img src="assets/readme/tablet_time_capsule_wall.png" alt="Time Capsule Photo Gallery" width="75%" />
  <p><em>▲ Time Capsule Achievement Photo Wall</em></p>
</div>

### 4. Zero-Install QR Photo Upload over LAN
- **Embedded Local HTTP Server**: For outdoor activities where taking a tablet is inconvenient, users can scan an on-screen QR code with any smartphone camera/browser to open a lightweight H5 upload page without installing an app.
- **Canvas Smart Compression & Heartbeat Detection**: Images larger than 3MB are automatically scaled down to 2048px on the phone before transmission. Features 5-second mutual heartbeat offline detection and a 5-photo quota per task.

<div align="center">
  <img src="assets/readme/tablet_qr_photo_upload.png" alt="LAN QR Photo Upload Flow" width="75%" />
  <p><em>▲ Tablet QR Code Scan and Smartphone Web Upload Workflow</em></p>
</div>

### 5. Multi-User Family Profile Switching
- **Shared Tablet Profiles**: Supports creating independent user profiles on a single family tablet. Switch profiles instantly from the top bar with segregated task and flower progress data.

<div align="center">
  <img src="assets/readme/tablet_user_switcher.png" alt="Family Multi-User Profile Switcher" width="75%" />
  <p><em>▲ Fast Multi-Member Family Profile Switching</em></p>
</div>

---

## 🔒 Data Storage & Permissions

### Privacy Principles
- **100% Offline**: Contains no telemetry SDKs, analytics trackers, or third-party advertising frameworks.
- **Complete Local Ownership**: All data, execution records, photos, and settings remain solely on the local device.

### System Permissions Breakdown
| Permission | Purpose |
| :--- | :--- |
| `CAMERA` | Take task verification photos |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Wake up exact alarms and notifications for scheduled tasks |
| `POST_NOTIFICATIONS` | Post task reminder notifications on Android 13+ |
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | Scan and connect to BLE peripherals for notification sync |
| `ACCESS_FINE_LOCATION` | Required by Android 11 and below for BLE hardware scanning |

---

## 🚀 Download & Installation

### 1. GitHub Releases
Download pre-built APK packages directly from the [Releases Page](https://github.com/BladeFong/JustNow/releases).

### 2. F-Droid
Includes a standard F-Droid build recipe (see [`metadata/com.nearby.justnow.yml`](metadata/com.nearby.justnow.yml)), fully compliant with F-Droid free and open-source software (FOSS) standards.

---

## 🛠️ Build & Development

### Requirements
- **JDK**: OpenJDK 17 or higher
- **Android SDK**: Compile SDK 34, Target SDK 34, Min SDK 26 (Android 8.0+)
- **IDE**: Android Studio Jellyfish / Koala or higher

### Build Commands
```bash
# Clone the repository
git clone https://github.com/BladeFong/JustNow.git
cd JustNow

# Build Debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

---

## 📜 License

This project is licensed under the **[GPL-3.0-or-later](LICENSE)**.
Issues and Pull Requests are welcome!
