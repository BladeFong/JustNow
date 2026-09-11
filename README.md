<div align="center">

# 恰恰有事 (Just Now)

**刚好，手边有事 / Right here, right now**

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg)](https://developer.android.com)
[![Storage: 100% Offline](https://img.shields.io/badge/Storage-100%25%20Offline%20Local-success.svg)](#-数据存储与权限说明)
[![Version](https://img.shields.io/badge/Version-v1.0-orange.svg)](https://github.com/BladeFong/JustNow/releases)

<p align="center">
  <a href="README.md"><b>简体中文</b></a> | <a href="README_EN.md"><b>English</b></a>
</p>

</div>

---

## 📖 项目简介

**恰恰有事（Just Now）** 是一款面向个人与家庭平板的任务与时间管理 Android 应用。

应用基于用户设定的时段划分、法定节假日与调休补班状态，结合当前剩余可用时间，动态排序并推荐最适合当下执行的任务；同时专为家庭平板场景设计了儿童七朵花激励机制、时光胶囊照片成果墙、免安装局域网扫码传图打卡以及 BLE 蓝牙通知同步。

全系统采用纯本地 SQLite/Room 存储，无中心服务器依赖，无需注册登录，保障个人与家庭数据的绝对私密。

<div align="center">
  <img src="assets/readme/hero_preview.png" alt="恰恰有事 手机与平板端界面预览" width="85%" />
  <p><em>▲ 恰恰有事：手机端专注时间线与平板端家庭激励视图</em></p>
</div>

---

## ⚙️ 核心机制

- **时段感知与动态排序**：支持将一天划分为若干时段（如晨间、专注工作、晚间休闲等）。系统结合法定节假日/补班日历与当前时段剩余时长，动态计算推荐任务列表。
- **周期配额显隐控制**：支持为任务配置日、周、月、年维度的完成配额。在当前周期内达成配额后，任务自动从推荐列表中收起，进入新周期后自动恢复显示。
- **100% 本地离线运行**：数据、配置与打卡照片均保存在设备本地私有目录，不发起任何后台数据收集或云端上传。

---

## 📱 手机端功能

### 1. 智能时段与动态时间线
- **时段与节假日解析**：内置中国大陆、中国香港、中国澳门及 Apple 日历规范的法定节假日与调休补班数据解析，时段规则根据工作日/休息日自动匹配。
- **时间轴与液体进度**：时间线以垂直轴展示今日任务分布与执行记录，辅以液体色块动态展示当前时段流逝进度与剩余分钟数。

<div align="center">
  <img src="assets/readme/phone_timeline_stream.png" alt="手机端动态时间线与时段" width="45%" />
  <p><em>▲ 动态时间线与时段剩余时间指示</em></p>
</div>

### 2. 四象限管理与完成率趋势
- **四象限分类**：按“重要-紧急”、“重要-不紧急”、“不重要-紧急”、“不重要-不紧急”四个维度分类管理任务。
- **10 周期趋势分析**：针对配置了周期配额的任务，提供最近 10 个周期的完成率折线图，直观展现执行连续性。

<div align="center">
  <img src="assets/readme/phone_quadrant_quota.png" alt="四象限管理与趋势图" width="45%" />
  <p><em>▲ 四象限管理与完成率趋势折线图</em></p>
</div>

### 3. 沉浸式任务录入与扩展
- **Markdown 富文本与子清单**：支持直接在任务内编辑和渲染沉浸式 Markdown 格式笔记，支持分步 Checklist 子清单管理。
- **第三方应用与分身唤起**：支持为任务绑定特定应用的 PackageName 或 Intent DeepLink，点击任务可直接唤起对应应用；支持识别并启动多用户应用分身。

<div align="center">
  <img src="assets/readme/phone_task_editor_markdown.png" alt="任务编辑与 Markdown 笔记" width="45%" />
  <p><em>▲ 任务录入、Markdown 笔记与子清单</em></p>
</div>

### 4. 桌面小组件与 BLE 硬件通知同步
- **桌面 AppWidget**：支持在 Android 主屏幕添加桌面小组件，无需打开应用即可快速查看当前推荐任务并一键完成打卡。
- **BLE 蓝牙通知同步**：集成 `BleNotificationSync` SDK，任务到点提醒时可将通知同步推送至已连接的 BLE 蓝牙手环或外设。

<div align="center">
  <img src="assets/readme/phone_widget_and_ble.png" alt="桌面小组件与 BLE 同步" width="45%" />
  <p><em>▲ 桌面 AppWidget 与 BLE 设备通知同步</em></p>
</div>

---

## 📟 平板端与家庭功能

### 1. 大屏自适应与儿童活动分类
- **方向与列数自适应**：平板端解除方向锁定，支持横屏（3 列网格）与竖屏（2 列网格）自由旋转自适应。
- **10 类儿童活动图标**：内置阅读、桌游、运动、手工、家务、美术、音乐、作业、动画、玩具 10 种矢量活动图标，支持“活力蓝”与“童趣粉”全局主题色切换。

<div align="center">
  <img src="assets/readme/tablet_landscape_overview.png" alt="平板端大屏横屏布局" width="75%" />
  <p><em>▲ 平板横屏大屏自适应与儿童活动卡片</em></p>
</div>

### 2. 七朵花奖励机制
- **花瓣累计**：完成日常任务并拍照打卡，即可点亮对应日期的花瓣。
- **工作日花芯感知**：非寒暑假的工作日（含法定补班日）系统自动预填充花芯。
- **每周通关目标**：支持在设置中自定义每周达成目标天数（2~5 天），达标时触发通关祝贺动画。

<div align="center">
  <img src="assets/readme/tablet_flower_rewards.png" alt="七朵花花瓣奖励机制" width="75%" />
  <p><em>▲ 七朵花点亮机制与每周通关设置</em></p>
</div>

### 3. 时光胶囊照片成果墙
- **只读成果画廊**：以沉浸式照片墙形式集中展示历史任务打卡照片，按时间流动呈现成长与习惯养成记录。

<div align="center">
  <img src="assets/readme/tablet_time_capsule_wall.png" alt="时光胶囊照片成果墙" width="75%" />
  <p><em>▲ 时光胶囊照片成果墙</em></p>
</div>

### 4. 手机免安装·局域网扫码极速传图
- **嵌入式本地 HTTP 服务**：户外活动不便携带平板时，手机无需安装任何 App，直接使用自带相机/扫码工具扫描平板屏幕上的二维码，即可在手机浏览器打开极简 H5 上传页面。
- **Canvas 智能压缩与对等心跳**：手机端自动对大于 3MB 或超大分辨率图片等比缩放至 2048px 高清后上传；双端保持 5 秒心跳断连检测，支持单任务最多 5 张照片配额管理。

<div align="center">
  <img src="assets/readme/tablet_qr_photo_upload.png" alt="局域网扫码传图流程" width="75%" />
  <p><em>▲ 平板局域网扫码与手机端 H5 传图流程</em></p>
</div>

### 5. 家庭多用户档案切换
- **单机多档案**：家庭共享平板场景下，支持建立多个独立用户档案，点击顶部头像即可一键平滑切换，各成员任务与花朵数据相互独立。

<div align="center">
  <img src="assets/readme/tablet_user_switcher.png" alt="家庭多用户切换" width="75%" />
  <p><em>▲ 家庭多成员档案快速切换</em></p>
</div>

---

## 🔒 数据存储与权限说明

### 隐私原则
- **100% 离线运行**：应用不包含任何网络统计、广告 SDK 或第三方追踪组件。
- **数据完全本地化**：所有任务信息、执行日志、配置和照片均保存在设备本地私有目录中。

### 系统权限用途说明
| 权限名称 | 用途说明 |
| :--- | :--- |
| `CAMERA` | 任务完成时拍摄打卡照片 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 到达设定的提醒时间时唤醒精准闹钟与通知 |
| `POST_NOTIFICATIONS` | Android 13+ 发送任务到点状态栏提醒通知 |
| `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` | 扫描并连接支持 BLE 蓝牙的外设设备进行通知同步 |
| `ACCESS_FINE_LOCATION` | Android 11 及以下系统执行 BLE 蓝牙扫描时的系统强制要求 |

---

## 🚀 获取与安装

### 1. GitHub Releases
前往 [Releases 页面](https://github.com/BladeFong/JustNow/releases) 下载最新的 Release APK 并直接安装。

### 2. F-Droid
项目包含规范的 F-Droid 构建配方（见 [`metadata/com.nearby.justnow.yml`](metadata/com.nearby.justnow.yml)），符合 F-Droid 纯开源与无专有依赖标准。

---

## 🛠️ 构建与开发

### 环境要求
- **JDK**：OpenJDK 17 或更高版本
- **Android SDK**：Compile SDK 34，Target SDK 34，Min SDK 26（Android 8.0+）
- **开发工具**：Android Studio Jellyfish / Koala 或更高版本

### 常用命令
```bash
# 克隆代码仓库
git clone https://github.com/BladeFong/JustNow.git
cd JustNow

# 编译 Debug APK
./gradlew assembleDebug

# 执行单元测试
./gradlew test
```

---

## 📜 开源协议

本项目基于 **[GPL-3.0-or-later](LICENSE)** 协议开源。
欢迎提交 Issue 与 Pull Request 共同改进项目。
