# 开源项目 README 体系设计文档

## 1. 概述与目标

为开源项目「恰恰有事 / Just Now」构建完整、客观、符合开源社区规范的中英文 README 体系。
采用双文件独立形式（根目录 `README.md` 默认简体中文，`README_EN.md` 为英文版），顶部提供双向语言切换索引。
文档聚焦于客观呈现产品功能、核心机制、手机与平板双端特性、权限与隐私透明度，并为核心界面规划标准化的截图占位符。

---

## 2. 文档规范与文件组织

### 2.1 文件清单
* **中文主文档**：`/README.md`（项目根目录）
* **英文文档**：`/README_EN.md`（项目根目录）
* **设计规范文档**：`/docs/superpowers/specs/2026-09-11-open-source-readme-design.md`

### 2.2 顶部语言切换栏
在两个文档首行居中放置互链索引：
```markdown
<div align="center">
  <a href="README.md">简体中文</a> | <a href="README_EN.md">English</a>
</div>
```

### 2.3 徽章规范 (Badges)
* **License**：`https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg`
* **Platform**：`https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg`
* **Offline First**：`https://img.shields.io/badge/Storage-100%25%20Offline%20Local-success.svg`
* **Version**：`https://img.shields.io/badge/Version-v1.0-orange.svg`

---

## 3. 截图资源与占位符规范

所有截图文件统一规划于根目录 `assets/readme/`，采用小写下划线命名。文档中采用居中包裹的标准 Markdown 图片占位语法，附带语义化 Alt 文本及功能说明。

| 序号 | 文件路径 | 对应模块/场景 | 说明 |
| :--- | :--- | :--- | :--- |
| 1 | `assets/readme/hero_preview.png` | 顶部预览画廊 | 手机与平板双端核心界面组合展示 |
| 2 | `assets/readme/phone_timeline_stream.png` | 手机端·时间线与时段 | 动态时间轴刻度、时段色块与剩余时间 |
| 3 | `assets/readme/phone_quadrant_quota.png` | 手机端·四象限与统计 | 四象限任务分类与近 10 周期完成率趋势折线图 |
| 4 | `assets/readme/phone_task_editor_markdown.png` | 手机端·任务录入与扩展 | 沉浸式 Markdown 笔记编辑渲染与子清单 |
| 5 | `assets/readme/phone_widget_and_ble.png` | 手机端·小组件与硬件 | Android 桌面小组件与 BLE 蓝牙通知同步管理 |
| 6 | `assets/readme/tablet_landscape_overview.png` | 平板端·大屏自适应 | 横竖屏自适应列数与 10 类儿童活动图标 |
| 7 | `assets/readme/tablet_flower_rewards.png` | 平板端·七朵花激励 | 每日花瓣点亮、工作日花芯填充与通关目标设置 |
| 8 | `assets/readme/tablet_time_capsule_wall.png` | 平板端·时光胶囊 | 只读照片成果墙沉浸式画廊展示 |
| 9 | `assets/readme/tablet_qr_photo_upload.png` | 平板端·扫码传图流程 | 平板局域网二维码弹窗与手机免安装 H5 传图流程 |
| 10 | `assets/readme/tablet_user_switcher.png` | 平板端·多用户切换 | 家庭多成员档案一键切换浮层 |

---

## 4. 中英文正文详细内容结构

### 4.1 Header & Hero
* **项目名称**：恰恰有事 / Just Now
* **产品标语**：刚好，手边有事 / Right here, right now
* **定位概述**：客观说明应用属性——面向个人与家庭平板的任务与时间管理 Android 应用。基于时段规则、剩余时间与四象限动态推荐任务，集成儿童花朵奖励、免安装局域网扫码传图打卡与 BLE 蓝牙通知同步，100% 离线本地存储，无需注册账号。

### 4.2 核心机制 (Core Mechanisms)
1. **时段感知与动态排序**：根据用户划分的时段、法定节假日/补班状态与剩余时长，动态计算并呈现当前时段推荐任务。
2. **周期配额显隐控制**：日/周/月/年完成配额制，配额满后自动在当前周期内收起，下一周期自动重现。
3. **本地离线与隐私**：基于 Room/SQLite，数据与照片存储于设备本地，无远程服务器与数据上报。

### 4.3 📱 手机端功能 (Mobile Features)
1. **智能时段与时间线**：自定义时段、大陆/港澳及 Apple 日历节假日与补班解析、时间轴液体进度与剩余时长。
2. **四象限管理与完成率趋势**：四象限分类、任务状态流转、10 周期完成率趋势折线图。
3. **沉浸录入与外部联动**：Markdown 富文本笔记、Checklist 子清单、第三方 App 包名/DeepLink 唤起与应用分身支持。
4. **小组件与硬件协同**：桌面 AppWidget 快捷打卡、BLE 蓝牙通知外设同步。

### 4.4 📟 平板端与家庭功能 (Tablet & Family Features)
1. **大屏自适应与儿童活动**：横屏 3 列 / 竖屏 2 列网格、10 种儿童日常活动矢量图标、活力蓝/童趣粉双主题色。
2. **七朵花奖励机制**：拍照完成任务累计花瓣、非假期工作日花芯默认填充、2~5 天每周目标通关祝贺。
3. **时光胶囊成果墙**：只读形式集中展示历史打卡照片。
4. **局域网扫码免安装传图**：基于内置 `HttpServer`，手机扫码访问 H5 上传照片，Canvas 智能压缩（超 3MB 压缩至 2048px），5 秒心跳断连感知，单任务上限 5 张。
5. **家庭多用户切换**：平板单机多成员独立档案一键切换。

### 4.5 权限与数据安全 (Permissions & Privacy)
* 系统权限透明说明：`CAMERA`（拍照打卡）、`SCHEDULE_EXACT_ALARM`（精准提醒）、`POST_NOTIFICATIONS`（系统通知）、`BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`（BLE 外设同步）。
* 隐私说明：零网络依赖，数据不离机。

### 4.6 获取与安装 (Download & Installation)
* GitHub Releases 下载 APK。
* F-Droid 构建支持说明（依据 `metadata/com.nearby.justnow.yml`）。

### 4.7 构建与开发 (Build & Development)
* 环境：JDK 17+, Android SDK 34 (Min API 26)。
* 构建命令：`./gradlew assembleDebug`、`./gradlew test`。

### 4.8 开源协议 (License)
* 采用 `GPL-3.0-or-later` 协议。
* 不声明企业版权所有信息。

---

## 5. 语言风格与文风规范
* **去 AI 味**：杜绝空洞营销词（如“震撼”、“颠覆”、“终极”、“告别焦虑”、“极致体验”等），以功能事实、技术实现与操作流程为核心。
* **准确性**：中英文专业术语（Room、SQLite、HttpServer、BLE、AppWidget 等）保持统一。
