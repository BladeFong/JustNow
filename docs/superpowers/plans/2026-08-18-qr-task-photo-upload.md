# 平板端任务扫码传图 (QR Task Photo Upload) 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为平板端添加“扫码传图”功能，支持手机在无需安装任何 APP 的情况下，通过扫码打开平板内置 H5 网页，将照片（可多选）极速同步给平板并与任务关联，刷新花朵奖励。

**Architecture:** 平板端按需启动基于标准 ServerSocket 的轻量嵌入式 HTTP 服务，弹窗展示包含局域网 IP、端口和安全 Token 的二维码。手机原生扫码打开静态 H5 单页，通过 1 秒心跳保持连接、Canvas 智能压缩（确保 < 3MB），使用 Multipart POST 传输照片。平板流式保存至私有目录并通过 `TaskPhotoRepository` 入库，实现 5 秒对等离线感知与系统休眠联动。

**Tech Stack:** Java 11, Android SDK (API 33~36), `ServerSocket`, ZXing Core (3.5.3), Room DB, HTML5 / CSS / Vanilla JS / Canvas API.

## Global Constraints
- **零安装**：手机端必须为纯标准 HTML5，兼容 iOS Safari 及各大 Android 原生浏览器/相册。
- **局域网通信**：支持家庭 Wi-Fi 及手机热点直连，零公网云端依赖。
- **对等心跳**：手机端 1 秒 ping，双端统一在 5 秒无心跳时对等感知离线。
- **超时保护**：平板二维码弹窗超时以 `min(systemScreenOffTimeout, 3分钟)` 为准，手机连接期间临时常亮。
- **照片限制**：单任务最多 5 张，超出部分前端截断与服务端拦截。
- **文档事实**：花瓣点亮动画与 TTS 语音播报此前已取消，文档需保持事实一致。

---

### Task 1: 局域网网络与 IP 识别工具类 (`NetworkUtils`)

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/util/NetworkUtils.java`
- Create: `app/src/test/java/com/nearby/justnow/util/NetworkUtilsTest.java`

**Interfaces:**
- Produces:
  - `String NetworkUtils.getLocalIpAddress(Context context)`: 返回活动局域网 IPv4 地址（如 `192.168.1.100` 或 `192.168.43.123`），无有效网络时返回 `null`。
  - `boolean NetworkUtils.isWifiOrHotspotConnected(Context context)`: 判断当前是否已接入 Wi-Fi 或热点。

- [x] **Step 1: 编写 NetworkUtilsTest 单元测试**
- [x] **Step 2: 运行测试并验证失败**
- [x] **Step 3: 实现 NetworkUtils (遍历网络接口，优先过滤 wlan0/eth0/热点网卡)**
- [x] **Step 4: 运行测试并验证全部通过**

---

### Task 2: 二维码生成工具 (`QrCodeUtils`)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/nearby/justnow/util/QrCodeUtils.java`
- Create: `app/src/test/java/com/nearby/justnow/util/QrCodeUtilsTest.java`

**Interfaces:**
- Consumes: `com.google.zxing:core:3.5.3`
- Produces:
  - `Bitmap QrCodeUtils.generateQrCode(String content, int width, int height)`: 将 URL 文本转为黑白 `Bitmap`。

- [x] **Step 1: 在 `libs.versions.toml` 和 `build.gradle.kts` 引入 `zxing-core` 依赖**
- [x] **Step 2: 编写 QrCodeUtilsTest 单元测试验证位图尺寸与生成逻辑**
- [x] **Step 3: 实现 QrCodeUtils (基于 `MultiFormatWriter` 和 `BitMatrix`)**
- [x] **Step 4: 运行测试并验证全部通过**

---

### Task 3: 手机端极简 H5 传输页面 (`upload.html`)

**Files:**
- Create: `app/src/main/assets/web/upload.html`

**Interfaces:**
- Produces:
  - 自包含 HTML5/CSS/JavaScript 单页。
  - 支持 `GET /upload` 模板参数插值：`{{TASK_TITLE}}`, `{{TASK_ICON}}`, `{{REMAINING_COUNT}}`, `{{THEME_COLOR}}`, `{{TOKEN}}`, `{{TASK_ID}}`。
  - 1 秒心跳机制：`setInterval` 调用 `GET /api/ping?taskId=..&token=..`，阶梯式重试（3s 警告，5s 禁用）。
  - Canvas 智能压缩：>3MB 或超大图等比缩放至 2048px / 0.92 质量，<=3MB 原图直传。
  - Multipart POST 提交及进度展示。

- [x] **Step 1: 编写 `upload.html` 基础布局与响应式样式（自适应手机屏幕与暗色模式）**
- [x] **Step 2: 实现 JavaScript 1 秒心跳与 5 秒对等离线状态机**
- [x] **Step 3: 实现 Canvas 图片智能缩放与九宫格预览/移除逻辑**
- [x] **Step 4: 实现 `FormData` 异步上传与成功/失败结果展示**

---

### Task 4: 嵌入式 HTTP 服务 (`TaskPhotoHttpServer`)

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/server/TaskPhotoHttpServer.java`
- Create: `app/src/main/java/com/nearby/justnow/server/MultipartStreamParser.java`
- Create: `app/src/test/java/com/nearby/justnow/server/TaskPhotoHttpServerTest.java`

**Interfaces:**
- Consumes:
  - `TaskPhotoRepository.bindPhotoToTask(long taskId, String photoUri)`
  - `TaskPhotoRepository.getPhotoCountForTask(long taskId)`
- Produces:
  - `void start(int port)`: 启动服务（支持 8888~8899 端口自适应）。
  - `void stop()`: 即时停止服务并释放线程池。
  - `void setHeartbeatListener(OnHeartbeatListener listener)`: 通知双端心跳状态（手机连接 / 丢失）。
  - `void setUploadCompleteListener(OnUploadCompleteListener listener)`: 通知图片接收完成。

- [x] **Step 1: 编写 Multipart 流式解析器 `MultipartStreamParser` 与测试**
- [x] **Step 2: 编写 `TaskPhotoHttpServer` 的路由分发与 Token/上限校验逻辑**
- [x] **Step 3: 实现文件保存至 `context.getExternalFilesDir(PICTURES)` 并调用 Repository 入库**
- [x] **Step 4: 运行单元测试验证 HTTP GET / POST 接口与心跳更新逻辑**

---

### Task 5: 平板端二维码交互弹窗 (`QrUploadDialog`)

**Files:**
- Create: `app/src/main/res/layout/dialog_qr_upload.xml`
- Create: `app/src/main/java/com/nearby/justnow/ui/dialog/QrUploadDialog.java`

**Interfaces:**
- Consumes:
  - `TaskEntity task`
  - `TaskPhotoHttpServer`
  - `NetworkUtils`
  - `QrCodeUtils`
- Produces:
  - 完整的二维码展示与状态联动弹窗。
  - 生命周期管理：打开启动服务，关闭/超时（`min(systemTimeout, 3min)`）停止服务。
  - 手机连接时启用 `FLAG_KEEP_SCREEN_ON`，心跳丢失 5 秒后释放。
  - 上传成功后展示绿勾提示并在 1.5 秒后自动关闭。

- [x] **Step 1: 创建 `dialog_qr_upload.xml` 布局（顶部任务卡片、中间二维码/状态文案、底部关闭按钮）**
- [x] **Step 2: 实现 `QrUploadDialog.java` 的网络检查与二维码生成渲染**
- [x] **Step 3: 实现心跳监听、常亮锁控制与系统休眠超时倒计时**
- [x] **Step 4: 实现上传成功反馈与回调通知**

---

### Task 6: 接入 `TaskPhotoListDialog` 与七朵花业务联动

**Files:**
- Modify: `app/src/main/res/layout/item_task_photo_list.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/TaskPhotoListDialog.java`
- Modify: `modules/tablet-flower-rewards.md`

**Interfaces:**
- Consumes:
  - `QrUploadDialog`
  - `RewardBarFragment.refreshWeeklyFlowers()`
- Produces:
  - 任务列表项增加「📱 扫码传图」按钮，点击弹出 `QrUploadDialog`。
  - 照片上传成功后自动刷新列表已拍张数徽标，并触发花瓣更新。

- [x] **Step 1: 在 `item_task_photo_list.xml` 增加扫码传图按钮图标与布局**
- [x] **Step 2: 在 `TaskPhotoListDialog.java` 中绑定点击事件，调起 `QrUploadDialog`**
- [x] **Step 3: 在传图成功回调中刷新当前 Adapter 数据并通知外部刷新花瓣**
- [x] **Step 4: 更新 `modules/tablet-flower-rewards.md`，清除残留的 TTS/动画描述，记录扫码传图技术决策**

---

### Task 7: 完整编译构建与功能联调验证

**Files:**
- Test all unit tests
- Run Gradle check & assembleDebug

- [x] **Step 1: 运行全量单元测试 (`./gradlew testDebugUnitTest`)**
- [x] **Step 2: 编译 Debug APK (`./gradlew assembleDebug`) 验证零编译/混淆告警**
- [x] **Step 3: 核对各端交互细节，完成功能交付**
