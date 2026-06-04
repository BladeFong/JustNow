# 安排功能重构 — 实现计划

> 设计文档：[2026-06-04-task-schedule-redesign.md](2026-06-04-task-schedule-redesign.md)
> 创建日期：2026-06-04

## 架构原则：与 6 天工作制自然对接

安排系统不自己判断"今天周几、是否 6 天"，只调用时段组命中接口问"该时段组今天是否生效"。命中算法改完后，6 天周六行为自然带出，安排侧零感知。

```
安排系统                        时段组命中算法
────────                       ──────────────
关联时段组 ──→ isGroupActive(groupType, dateMs) ──→ true/false
               （不关心 WHY，只关心 RESULT）
```

## 实现阶段

### F1：数据层 — 砍 MONTHLY + 时段组关联字段

**TaskScheduleEntity**
- 删除 `TYPE_MONTHLY = 3` 常量
- 新增 `linkedPeriodGroupType` 字段（int，0=不关联/顶层单次，其他值对应 period_group_type）
- 新增 `scheduleSubType` 字段（int，0=每天/DAILY，1=每周/WEEKLY，2=单次/ONCE）—— 仅当 linkedPeriodGroupType != 0 时有效

**TaskScheduleDao**
- `getActiveScheduleSync(taskId)` 保持不变（UNIQUE 约束仍生效）
- 新增 `getSchedulesByPeriodGroupType(int groupType)`：按关联时段组查询，供调试/迁移用

**DB 迁移**
- MONTHLY 记录：`scheduleType == 3` → 直接删除（迁前 `SELECT COUNT(*)` 记录日志）
- 新增列 `linkedPeriodGroupType` INTEGER NOT NULL DEFAULT 0
- 新增列 `scheduleSubType` INTEGER NOT NULL DEFAULT 0
- 现有 ONCE/DAILY/WEEKLY 记录保持 `scheduleType`，`scheduleSubType` 和 `linkedPeriodGroupType` 默认为 0（历史数据等价于顶层单次/DAILY/WEEKLY）

**注意**：`scheduleType` 和 `scheduleSubType` 短期共存。待所有旧记录自然过期/用户重设后，下一轮可合并。当前不强行迁移。

### F2：TaskScheduleMatcher — 砍 MONTHLY

- `matchesDate()`：删除 `case TYPE_MONTHLY` 分支
- `computeNextMatch()`：删除 `case TYPE_MONTHLY` 分支
- 单元测试：删除 MONTHLY 相关用例

### F3：时段组数据查询 — 安排页获取可用时段组列表

**PeriodGroupRuleResolver / TimePeriodRepository**
- 新增同步方法：`getEnabledPeriodGroupsSync()` → 返回当前开启的时段组列表（type + 名称 + 起止日期）
- 新增同步方法：`getWorkdayModeSync()` → 返回工作日模式（STANDARD_5 / SIX_DAY）
- 新增同步方法：`getEffectivePeriodGroupSync(long dateMs)` → 返回该日期命中的时段组（已有命中算法，封装为安排可调用的接口）

### F4：TaskScheduleViewModel 重构

**loadInitialState 变更**
- 额外加载：开启的时段组列表 + 工作日模式
- 传递给 Fragment 用于动态渲染选择器

**匹配判断变更**
- `getOccupiedSlots()` 中的 `matchesDate(s, dateMs)` → 改为先判断关联时段组当天是否生效，再生效才计入占用
- 「时段组不生效 → 安排不触发」统一在此处

**保存变更**
- `insertSchedule()` / `updateSchedule()`：填入 `linkedPeriodGroupType` 和 `scheduleSubType`

### F5：UI — 动态类型选择器

**布局变更**（`fragment_task_schedule.xml`）
- 删除 `rgScheduleType` RadioGroup（4 个 RadioButton：单次/每天/每周/每月）
- 新增顶层 RadioGroup：动态 addView 生成（单次 + 各开启的时段组）
- 每个时段组选项下方：两栏二级选择布局
  - 工作日：`[每天]` toggle | 周 chip 行（5 或 6 chip，动态）
  - 长假类：`[每天]` toggle | `[单次: yyyy-MM-dd]` 可点击文本
- 两栏互斥 toggle 逻辑在 Fragment 中处理

**交互**
- 选中顶层"单次"→ 隐藏二级选择区，显示日期选择器 + 槽位视图
- 选中某时段组 → 显示该组二级选择区 + 槽位视图（无日期选择器，除非二级选"单次"）
- 两栏均不默认选中，保存按钮初始禁用

### F6：UI — 槽位视图改用命中时段组

**refreshSlotView 变更**
- 当前：`mSelectedDateMs` 或 `DateUtils.todayStartMs()` 查时段
- 改为：
  - 顶层"单次"：`getEffectivePeriodGroupSync(selectedDateMs)` → 用命中时段组的时段
  - 工作日时段组：直接取工作日时段组时段
  - 长假类时段组：直接取该时段组时段
- "今天"不再作为默认槽位来源

### F7：时间线去时段最大集

**TimelineBuilder**
- 去掉 max-set 合并逻辑（合并所有时段组取并集）
- 改为只用当前生效时段组绘制时间线

### F8：清理

- 删除 `MonthlyDayPickerDialog.java`
- 删除 `dialog_monthly_day_picker.xml`
- 删除 `item_monthly_day_cell.xml`
- 删除布局中 `rb_schedule_monthly`
- 删除相关字符串资源中的 monthly 相关 key
- `TaskScheduleViewModel` 删除 `mMonthlyDay` 字段和相关方法
- `TaskScheduleFragment` 删除 `setupMonthlyDay()` / `updateMonthlyDayDisplay()`

### F9：单元测试

- `TaskScheduleMatcherTest`：更新（删 MONTHLY 用例）
- `TaskScheduleViewModel` 测试：新增（时段组关联、匹配判断）
- 全量 `testDebugUnitTest` 通过

## 验证标准

- `compileDebugJavaWithJavac` 通过
- `testDebugUnitTest` 全量 0 失败
- MONTHLY 相关代码/资源全部清理
