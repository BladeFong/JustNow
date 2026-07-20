# time-period 进度日志

### 2026-07-19 — 成果墙趋势图修复：onMeasure 定高 + 裁前导零 + 主题色

- onMeasure wrap_content 固定 120dp，不再撑满布局
- trimLeadingZeros 从有数据的周期开始画，不足 2 点空图表（与 TrendChartView 一致）
- setColor 跟随主题色，折线/圆点/填充区域统一变色

### 2026-07-19 — 成果墙花瓣统计 + 假期提醒修复 + 趋势图 实现完成

- D: updateGroupAndPeriods 假期组保存时写 lastReviewedKey，修复"假期安排确认了吗？"误提示
- B: TimeCapsuleWallActivity 横屏 3 列（竖屏 2）
- C: 标题栏右侧花瓣总数 + PopupMenu 下拉切换 周/月/暑假/寒假，暑假/寒假条件显示
- A: 新建 PetalTrendChartView 折线+圆点趋势图，竖屏底部显示，10 周/月趋势或寒暑假按实际周数

### 2026-07-19 — 成果墙花瓣统计 + 假期提醒修复 + 趋势图 设计完成

- spec：[docs/superpowers/specs/2026-07-19-timecapsule-petal-stats-design.md](../docs/superpowers/specs/2026-07-19-timecapsule-petal-stats-design.md)
- D: save 时写 lastReviewedKey，修复"假期安排确认了吗？"误提示
- B: 成果墙横屏 3 列；C: 标题栏花瓣统计 + 周/月/寒暑假下拉切换；A: 竖屏底部花瓣趋势折线图

### 2026-07-19 — 暑假开关 disabled + 时段重复修复

- **ViewHolder 复用**：春节 blocked 组 setEnabled(false) 后复用到暑假，else 分支未恢复 → 补 setEnabled(true) + setClickable(true)
- **时段重复**：initVacationDefaultsIfNeeded / initSpringFestivalPeriodsIfNeeded 无条件调 copyPeriodsFromTemplate → 改为仅在 fill*DefaultsCore 返回非 null（无已有数据）时才复制
- **历史数据清理**：新增 deduplicatePeriods()，同 group_type + name_key 保留 MIN(id) 删其余；在 fillVacationDefaultsCore / fillSpringFestivalDefaultsCore / ensureDefaultsAndLoadPeriods 三入口调用

### 2026-06-06 — 时间选择 PopupWindow 显示修复

**状态**：代码改动编译通过；文档整理后未再编译。时间段编辑与主界面底部截止时间复用的 `popup_time_picker` 改为可配置最小宽度（当前 160dp）并保持标题、滚轮、按钮组居中；`NumberPicker` 时间字号提升到 `text_size_title`；时间段编辑入口增加屏幕边缘偏移限制，避免靠右点击时弹窗被裁切。

### 2026-06-05 — 审查修复：跨年边界 bug + 日期 API 统一 + 残留资源清理

> 审查报告：[../docs/code-review-20260604.md](../docs/code-review-20260604.md)

- [x] `vacationCanMatch()` 跨年窗口区间重叠判断未处理 `windowStart > windowEnd`，改用 `isInMonthDayRange()` 跨年逻辑
- [x] `hasSpringFestivalFutureDataSync()` Calendar/LocalDate 混用统一为 `LocalDate.now().getYear()`
- [x] 删除 MONTHLY 残留 `bg_monthly_cell.xml`
- [x] `getGroupDisplayName()` switch 改 static Map 数据驱动
- [x] 测试：PeriodGroupRuleResolverTest 34 passed（含 3 个新增跨年用例）

### 2026-06-04 — 安排页时段组选项 3 个月窗口过滤

> 设计文档：[docs/superpowers/specs/2026-06-04-task-schedule-redesign.md](docs/superpowers/specs/2026-06-04-task-schedule-redesign.md)
> 详见：[modules/time-period.md](modules/time-period.md)

**状态**：编译通过。`PeriodGroupRuleResolver.canMatchInNextThreeMonths()` 判断时段组未来 3 个月能否命中，`TaskScheduleViewModel.buildEnabledPeriodGroups()` 过滤已过期选项。

### 2026-06-04 — Toolbar 改为 NavigationUI 模式（已回退）

**状态**：已回退。`PeriodConfigActivity` 从手动 `setDisplayHomeAsUpEnabled` + `setTitle` + `setNavigationOnClickListener` 改为 `NavigationUI.setupWithNavController()`——此方案对单目的地 Activity 无效：空 `AppBarConfiguration.Builder().build()` 导致所有目的地被视为顶级，返回箭头不显示。已恢复为原始简单写法。

### 2026-06-04 — 假期组弹窗时段预写入修复 + 春节无数据锁灰

> 设计文档：[docs/superpowers/specs/2026-06-04-period-group-rule-update.md](docs/superpowers/specs/2026-06-04-period-group-rule-update.md)
> 详见：[modules/time-period.md](modules/time-period.md)

**状态**：编译通过。`fillVacationDefaultsCore`/`fillSpringFestivalDefaultsCore` 开弹窗不再写 DB，改保存时统一写入；春节组无未来数据时开关锁灰、禁止编辑。

### 2026-06-04 — 四象限概览编辑图标 + 设置页返回箭头修复

> 详见：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)、[modules/time-period.md](modules/time-period.md)、[modules/stats.md](modules/stats.md)、[modules/tag.md](modules/tag.md)

**状态**：完成。编辑图标白色、设置页 Toolbar 返回箭头。

### 2026-05-30 — 全项目代码审查与修复

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译 + 全量测试通过。47 项发现 → 排除 5 误报 → 分 8 批修复 24 项。

后续验证建议：[ ] 节假日数据真机验证、[ ] Widget 标签筛选真机验证、[ ] logcat assertNotMainThread 检查

详见：[modules/task-execution.md](modules/task-execution.md)、[modules/holiday-data.md](modules/holiday-data.md)、[modules/widget.md](modules/widget.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/smart-display.md](modules/smart-display.md)、[modules/time-period.md](modules/time-period.md)、[modules/tag.md](modules/tag.md)、[modules/task-input.md](modules/task-input.md)

### 2026-05-17/18 — 时间段组编辑对话框布局优化 + 统一样式收尾

> 详见：[modules/time-period.md](modules/time-period.md) — 进度日志

日期完整年份、时间行固定时间区、浅蓝编辑块+黑色文字。Dialog/TimeEdit 统一样式。AAPT 样式父级问题记录。
