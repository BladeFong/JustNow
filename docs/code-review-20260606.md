# Code Review Report — 2026-06-06

- 审查范围：今日全部代码提交（7fa547b..5716153）
- 审查时间：2026-06-06
- 审查人：code-auditor

## 审查范围

今日提交涵盖以下功能：
1. 忽略交互修复+对话框分流+TYPE_ONCE 超时+跳过表简化（`388044a`）
2. 安排任务感知的剩余时间（`5bf931b`）
3. 安排任务剩余时间显示和点击修复（`5716153`）

涉及文件：AlarmReceiver、TaskScheduleDao、TaskScheduleSkipDao、AppDatabase（v3->v4 迁移）、TaskScheduleEntity、TaskScheduleSkipEntity、TaskScheduleRepository、ReminderScheduler、TaskStartGuard、TimeRemainingCalculator、MainFragment、MainViewModel、TimelineView、WidgetUpdateHelper、strings.xml（4 语言）。

## 结果概要

| 级别 | 数量 |
|------|:--:|
| important | 1 |
| nit | 1 |
| suggestion | 1 |
| praise | 3 |
| **合计** | **6** |

---

## 问题详情

### [x] #1 [important] `ignoreSchedule` 逻辑在 `AlarmReceiver.handleIgnore` 和 `MainViewModel.ignoreSchedule` 中完全重复

**文件**：
- `app/src/main/java/com/nearby/justnow/broadcast/AlarmReceiver.java` 第 197-233 行
- `app/src/main/java/com/nearby/justnow/ui/main/MainViewModel.java` 第 788-821 行

两处跳过记录 upsert 逻辑（getSkip -> null 判断 -> 设置 lastSkippedDateMs/skipCount -> upsert）完全相同，包括 TYPE_ONCE 分支禁用逻辑和重复安排重调度逻辑。代码注释已标注"逻辑与 AlarmReceiver#handleIgnore 一致"，说明已知重复但未提取。

**风险**：未来修改跳过逻辑时必须同步改两处，遗漏任一即产生不一致。

**建议**：提取到 `TaskScheduleRepository` 或新建工具类，封装"忽略安排"的完整流程（取消通知 + 单次禁用 / 重复 upsert + 重调度），两处调用统一委托。

**审核结果**：已修复。`TaskScheduleRepository` 新增 `skipOrDisable(scheduleId)` 方法，返回 `boolean`（true=需重调度）。`AlarmReceiver.handleIgnore` 和 `MainViewModel.ignoreSchedule` 均委托调用，各自只负责取消通知 + 按返回值决定重调度。

---

### [x] #2 [nit] `ScheduleWithFocusMinutesFull.enabled` 声明为 `boolean`，同 POJO 其他字段用 `int`

**文件**：`app/src/main/java/com/nearby/justnow/data/dao/TaskScheduleDao.java`，`ScheduleWithFocusMinutesFull` 类

同类 POJO 中 `scheduleType`、`scheduleSubType` 等 INTEGER 列均声明为 `int`，唯独 `enabled` 声明为 `boolean`。Room 自动做 0/1 转换不会出错，但风格不一致，后续维护者可能困惑"为什么这个字段特殊"。

**建议**：统一为 `int` 或全部用 `boolean`，保持一致。

**审核结果**：已修复。`enabled` 改为 `int`，`toEntity()` 中 `(enabled == 1)` 显式转换，与同 POJO 其他字段风格一致。

---

### [x] #3 [suggestion] `MIGRATION_3_4` DROP TABLE 丢失 v3 已有跳过数据

**文件**：`app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java`，`MIGRATION_3_4`

迁移直接 `DROP TABLE IF EXISTS task_schedule_skips` 后重建，v3 阶段积累的跳过记录全部丢失。

**评估**：v3 引入时间短（6/5），数据量极小，实际影响可忽略。但若后续再有表结构变更需注意数据迁移策略。

**审核结果**：不处理。v3 是中间版本，未发布过，不存在用户数据丢失问题。已记录到 `docs/code-review-ignore.md`。

---

## 审核记录（2026-06-06）

审核人：code-auditor

| 编号 | 级别 | 审核结果 | 说明 |
|------|------|----------|------|
| #1 | important | 已修复 | `TaskScheduleRepository.skipOrDisable()` 提取共享逻辑，两处调用统一委托 |
| #2 | nit | 已修复 | `enabled` 改为 `int`，`toEntity()` 加 `(enabled == 1)` 转换 |
| #3 | suggestion | 不处理 | v3 是中间版本未发布，无用户数据丢失，已记录到 ignore |

误报：无。3 个发现均确认为真实问题，2 个已修复，1 个确认不处理。

## 未处理项汇总

无。全部 3 项均已处理（2 修复 + 1 确认关闭）。

---

## 值得肯定

### #4 [praise] 跳过表单行模式设计简洁有效

`task_schedule_skips` 从每次忽略一行重构为每个 schedule 一行（`schedule_id` PK + `lastSkippedDateMs` + `skipCount`），数据量从 O(忽略次数) 降到 O(安排数)。`ReminderScheduler.scheduleNextAfterSkip` 直接从 `lastSkippedDateMs + 1天` 开始计算，去掉了 365 天遍历，逻辑大幅简化。

### #5 [praise] `applyScheduleTruncation` 边界处理周全

三种场景覆盖完整：
1. 当前在安排范围内（`nowMinute >= startMinute && < startMinute + focusMinutes`）-> `effectiveRemaining = 0`，任务不可开始
2. 未来有安排 -> `effectiveRemaining = nearestStart - nowMinute`
3. 无安排 -> 保持原始值

`getRemainingText()` 的 `effectiveRemaining == 0` 时 fallback 到 `remainingMinutes` 显示原始时段剩余，避免显示"0m"的尴尬。在范围内时 `s_schedule_remaining_format` 条件 `effectiveRemaining < remainingMinutes` 也不满足，走标准格式。设计自洽。

### #6 [praise] 缓存 + POJO JOIN 查询的组合模式合理

`TaskScheduleRepository` 使用 `volatile CopyOnWriteArrayList` 缓存 enabled 安排，所有写操作均先清缓存再通知。`ScheduleWithFocusMinutesFull` POJO 通过 JOIN 查询一次性获取 focusMinutes，避免 N+1。`toEntity()` 转换在 Repository 层完成，DAO 层保持纯数据访问职责。
