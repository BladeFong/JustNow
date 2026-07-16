# 主界面时间线 M2 视觉优化已落地实现计划

**Goal:** 优化主界面左侧时间线任务块视觉呈现。已完成任务左侧绘制加宽至 2 倍（8dp）的填充灰色状态条，文本缩进与执行中任务块一致（16dp）；执行中任务整体高亮为象限主题色，文字全白，去除 Ongoing 标签。

**Tech Stack:** Android View (Canvas/Paint), Java

---

### Task 1: 数据层调整 (TimelineItem & TimelineBuilder)

- [x] **Step 1: 修改 TimelineItem.java，增加 quadrant 字段**
  - 增加 `public final int quadrant;` 成员属性，并在构造器中接收和赋值。
- [x] **Step 2: 修改 TimelineBuilder.java，在装配 TimelineItem 时传入 task.quadrant**
  - 在装配执行中和已完成任务时，传入 `task.quadrant` 以便 View 层获取象限色。
- [x] **Step 3: 运行本地 Java 编译确保通过**

---

### Task 2: 已完成任务左状态条加宽 2 倍与统一文本缩进

- [x] **Step 1: 绘制加宽 2 倍的填充灰色状态条**
  - 左侧灰色状态条使用 `Paint.Style.FILL` 并填充 `R.color.timeline_tick`。
  - 宽度从原本的 `timeline_task_status_strip_width` (4dp) 提升至 2 倍（8dp），以强化已完成卡片的状态提示。
- [x] **Step 2: 统一文本缩进对齐**
  - 在 `drawTaskText` 中将已完成与执行中任务文本的左起点统一调整为 `barX + 16 * mDensity`。

---

### Task 3: 执行中任务高亮卡片与空闲高度防护

- [x] **Step 1: 执行中卡片全色渲染**
  - 遇到执行中任务时，使用对应的象限色填充整个卡片底框。
  - 标题文本转为全白，副文本转为浅白灰色，并去除 Ongoing 英文标签。
- [x] **Step 2: 自由空闲时间色块极小高度保护**
  - 在没有执行中任务时，限制液态自由时间色块的最小高度为 `12dp`。

---

### Task 4: 闹钟异常降级防护与主动权限引导 (ReminderScheduler & MainFragment)

- [x] **Step 1: 精确闹钟 SecurityException 崩溃降级防护**
  - 在 `ReminderScheduler.java` 中增加 `setAlarmSafe` 辅助方法，使用 try-catch 包裹 `setExactAndAllowWhileIdle`，发生 `SecurityException` 时降级注册非精确闹钟 `setAndAllowWhileIdle`。
- [x] **Step 2: onResume 中主动检查与权限引导**
  - 在 `MainFragment.java` 的 `onResume` 周期中进行精确闹钟权限校验，若未授权则弹出弹窗引导用户去设置页面授权，确保在添加/执行任务调度闹钟前置拥有权限，防患于未然。
