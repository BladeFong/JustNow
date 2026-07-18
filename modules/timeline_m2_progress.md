### 2026-07-18 — 审查修复：TimelineView.onDraw() Paint 对象 GC 优化
- onDraw() 中 completedStripPaint 和 ongoingPaint 从每帧 new Paint() 改为成员变量 mCompletedStripPaint / mOngoingPaint，仅 setColor() 复用

### 2026-07-18 — 代码审查
- 代码审查记录于 `docs/code-review-20260718.md`，涉及 TimelineView 性能问题 1 项。

### 2026-07-16 — 主界面时间线 M2 视觉优化
- **状态**：进行中
- **内容**：
  - 编写时间线 M2 优化方案视觉设计 spec。
  - 已完成任务块的左侧灰色状态条宽度加宽为 2 倍（8dp）并填充，文本对齐与执行中卡片统一调整为 16dp；执行中任务卡片整体高亮填充为象限主题色背景，文字全白，去除 Ongoing 标签。
  - 解决在 Android 12/13/14+ 系统上启动任务时，因调用精确闹钟而报 `SecurityException` 导致崩溃的问题。在 `ReminderScheduler.java` 中封装 `setAlarmSafe` 方法，捕获 `SecurityException` 并在无权限时优雅降级为非精确闹钟。
  - 在 `MainFragment.java` 的 `onResume` 生命周期中增加对精确闹钟权限的检查和申请，提示未授权用户去设置页面开启，避免执行/添加任务闹钟时权限不足。
  - 编译成功，且成功解决真机安装与启动任务时的崩溃问题。
