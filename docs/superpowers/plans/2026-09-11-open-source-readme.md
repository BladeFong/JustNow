# 开源项目 README 体系实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 编写开源项目「恰恰有事 / Just Now」的标准开源 README 体系，包含中文主文档 `README.md` 与英文文档 `README_EN.md`，实现双向索引互链，规划标准截图占位符，保持客观、去 AI 味的技术文风。

**Architecture:** 采用根目录双文件独立形式（`README.md` 默认简体中文，`README_EN.md` 为英文）。首行提供语言切换导航栏，正文统一规划 10 张规范命名的截图占位符（`assets/readme/` 目录），覆盖手机端核心流程与平板端家庭/儿童专属能力。

**Tech & Constraints:** 标准 Markdown / GitHub Flavored Markdown 语法，Shields.io 徽章，GPL-3.0-or-later 协议，客观事实陈述，不含空洞营销词与企业版权声明。

---

### Task 1: 编写中文主文档 (`README.md`)

**Files:**
- Create: `README.md`

**Content Requirements:**
- 顶部语言切换导航栏：`[简体中文](README.md) | [English](README_EN.md)`
- 标准徽章（License GPL-3.0-or-later, Android 8.0+, 100% Offline Local, Version v1.0）
- 顶部 Hero 画廊截图占位（`assets/readme/hero_preview.png`）
- 客观的项目简介与核心机制说明
- 📱 手机端特性模块（时间线、四象限配额、Markdown 与应用分身、小组件与 BLE 通知）及对应 4 个截图占位
- 📟 平板与家庭端特性模块（横竖屏自适应与儿童图标、七朵花激励、时光胶囊、局域网免安装扫码传图、多用户切换）及对应 5 个截图占位
- 权限说明与数据安全、获取与安装（Releases & F-Droid）、构建命令（JDK 17 / SDK 34）、GPL-3.0-or-later 协议

- [ ] **Step 1: 编写 `README.md` 完整中文内容**
- [ ] **Step 2: 校验 Markdown 格式、图片占位符语法及链接有效性**

---

### Task 2: 编写英文对应文档 (`README_EN.md`)

**Files:**
- Create: `README_EN.md`

**Content Requirements:**
- 结构与中文版严格 1:1 镜像对应
- 顶部语言切换导航栏：`[简体中文](README.md) | [English](README_EN.md)`
- 英文专业术语保持精准规范（Four Quadrants, Timeline, Time Periods, Flower Rewards, Time Capsule, QR Photo Upload, BLE Notification Sync, AppWidget）
- 相同的截图占位路径与 Alt 描述英文对应
- 英文版权限说明、F-Droid 元数据说明、构建指引与 GPL-3.0-or-later 协议

- [ ] **Step 1: 编写 `README_EN.md` 完整英文内容**
- [ ] **Step 2: 校验英文表达客观准确度与格式一致性**

---

### Task 3: 整体核对与文档归档

**Files:**
- Modify: `task_plan.md`
- Modify: `progress.md`

- [ ] **Step 1: 验证中英文文档各级标题锚点与互相跳转链接**
- [ ] **Step 2: 核对 10 个截图占位符路径规范**
- [ ] **Step 3: 更新 `task_plan.md` 与 `progress.md` 记录本次文档体系落地进展**
