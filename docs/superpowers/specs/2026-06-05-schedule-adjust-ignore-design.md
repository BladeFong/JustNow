# 安排调整 & 忽略交互设计

## 背景

任务改为每个只有一条安排后，安排过的任务点击"安排"按钮文字未变、安排页未正确加载已有时间、通知缺少忽略选项、过期处理需统一。

## 改动点

### 1. 按钮文字：区分有无未触发安排

**涉及文件**：`MainFragment.java`、`strings.xml` × 4

- `configureScheduleButton()` 当前写死 `s_schedule_task`
- 改为：有 `enabled=1` 的安排 → `s_adjust_schedule`（"调整安排"/"Adjust Schedule"），否则 `s_schedule_task`（"安排"/"Schedule"）
- 调用方已有 `schedule` 对象，传入即可
- 时间线上已安排任务弹窗不显示此按钮（见 #5）

### 2. 安排页加载 bug：槽位被覆盖

**涉及文件**：`TaskScheduleFragment.java`

**根因**：`restoreExistingSchedule()` 第 162 行恢复 `mSelectedSlotMinute`，紧接着 `selectTypeRadioByTag()` 触发 `onTypeSelected()`，后者第 284 行把 `mSelectedSlotMinute` 重置为 `-1`。

**修复**：将 `mSelectedSlotMinute = s.scheduledTime` 移到 `restoreExistingSchedule()` 末尾，在 `onTypeSelected()` 和 `switch(subType)` 全部执行完之后再设，然后调 `refreshSlotViewAsync()` + `updateSaveButton()`。

覆盖四种场景：顶层单次、时段组每天、时段组每周、时段组单次。

### 3. 通知"忽略"操作

**涉及文件**：`ReminderNotifier.java`、`AlarmReceiver.java`、`ReminderScheduler.java`、`TaskScheduleDao.java`、新增 `TaskScheduleSkipEntity` + DAO + Repository

**行为**：

| 安排类型 | 忽略后 |
|---------|--------|
| TYPE_ONCE（单次） | 禁用安排 `enabled=0`，关闭通知 |
| 重复（每天/每周） | 写入当天跳过记录，关闭通知，重新调度下次闹钟 |

**UI**：
- 忽略按钮位于通知操作最左侧
- 按钮可加关闭图标（Android 10+ 折叠不显示，展开/旧设备/Wear OS 可见）

**数据层**：
- 新增 `task_schedule_skips` 表：`id`, `schedule_id`, `date_ms`, `created_at`
- `ReminderScheduler.computeNextMatch()` 计算下次触发时排除有跳过记录的日期

### 4. 超时统一为忽略

**涉及文件**：`ReminderScheduler.java`、`TaskScheduleDao.java`

- 移除 `disableExpiredOnceToday()` 专用的 `REASON_EXPIRED` 逻辑
- 凌晨 3 点 `refreshToday()` 中过期处理改为走忽略同一套：
  - TYPE_ONCE → 禁用
  - 重复 → 追加跳过记录
- 同时检查 `TaskExecutionAutoCompleter` 是否仍需要保留（过期时段自动完成执行中任务）

### 5. 时间线已安排任务交互

**涉及文件**：`MainFragment.java`、`TimelineItem.java`、`TimelineBuilder.java`

- 时间线上处于安排时段的已安排任务块可点击
- 点击弹出确认弹窗：**"开始"** + **"取消"**（无安排/调整安排按钮）
- 点击"开始"走正常流程：`TaskStartGuard` 校验 → 有附加模块则跳 `ReminderDetailActivity`，无则直接开始
- 任务开始后在时间线上按"执行中"规则排列

## 字符串资源

| Key | 英文 | 简体中文 | 繁体（台湾） | 繁体（香港） |
|-----|------|---------|------------|------------|
| `s_adjust_schedule` | Adjust Schedule | 调整安排 | 調整安排 | 調整安排 |
| `s_ignore` | Ignore | 忽略 | 忽略 | 忽略 |

## 备注

- `TaskExecutionAutoCompleter`（过期时段内自动完成执行中任务）是独立逻辑，与安排过期无关，保留不动
- 安排页加载 bug（#2）四种场景覆盖：顶层单次、时段组每天、时段组每周、时段组单次
