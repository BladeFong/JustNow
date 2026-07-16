# JustNow 主界面时间线 M2 视觉优化设计规范

## 1. 概述 (Overview)
针对平板端儿童使用场景与 Material Design 2 (M2) 风格微调，优化主界面左侧时间线组件 `TimelineView` 的任务块视觉呈现。已完成任务左侧灰色状态条加宽至 2 倍（8dp）并填充，且文本对齐与执行中任务一致（16dp 缩进）；执行中任务整体高亮填充为象限主题色背景，提升专注状态。

## 2. 视觉设计细节 (Visual Specifications)

### 2.1 任务卡片呈现 (Task Card Presentation)
- **已完成任务卡片加宽左色条**：
  - 卡片左侧配有**填充的灰色状态条**（使用 `R.color.timeline_tick` 填充色值），其宽度由原来的 `4dp` **加宽为 2 倍（8dp）**，增强其状态存在感。
  - 文字缩进调整为 `16dp`（`barX + 16 * mDensity`），与执行中任务的对齐一致。
- **执行中专注任务卡片**：
  - 背景整体填充为该任务的象限主题颜色（如蓝色 `#2196F3`）。
  - 任务标题、副标题等文字全部变成纯白色（`#FFFFFF`），小图标/副文字转为浅白灰色（`#E0E0E0`）。
  - 去除卡片顶部的 `ONGOING` 英文标签，简化视觉信息。
  - 文字缩进为 `16dp`（`barX + 16 * mDensity`）。

### 2.2 自由空闲时间色块 (Liquid Fill)
- 当没有执行中专注任务时，从当前时间游标向下延伸填充淡黄色半透明色块（`rgba(254, 240, 138, 0.25)`），内部不添加任何文字框。
- **极小色块防护**：当剩余自由时间极少（如少于 10 分钟），色块被挤压严重时，在绘制时强制保留最小高度 `12dp`，保证直观感知有微小空闲，同时防止布局挤压崩溃。

## 3. 技术实现方案 (Technical Plan)

### 3.1 数据层变更
- **`TimelineItem.java`**：
  - 增加 `public final int quadrant;` 字段，存储任务 of 象限值（0 - 3），用于在绘制时决定对应的卡片象限颜色。
- **`TimelineBuilder.java`**：
  - 在组装 `TimelineItem` 的构造函数中传入 `task.quadrant`。

### 3.2 绘制层与权限处理变更 (`TimelineView.java` & `MainFragment.java`)
- **`TimelineView.java`**：
  - `onDraw()` 调整：
    1. 绘制已完成任务时，在左侧裁剪并绘制宽度为 `8dp` 的填充灰色状态条。
    2. 绘制执行中任务时，直接使用 Paint 配合对应的象限色绘制全色背景卡片。
    3. `drawTaskText` 中将已完成和执行中任务的文字起点（`textX`）统一调整为 `barX + 16 * mDensity`。
- **`ReminderScheduler.java`**：
  - 封装 `setAlarmSafe`，使用 try-catch 包裹 `setExactAndAllowWhileIdle`，捕获到 `SecurityException` 崩溃时降级为 `setAndAllowWhileIdle` 启动闹钟。
- **`MainFragment.java`**：
  - 在 `onResume` 生命周期中检查是否已授权精确闹钟权限，未授权时主动提示并跳转系统设置页，确保在添加/执行任务调度闹钟前置拥有权限，彻底杜绝权限不足引起的潜在异常。
