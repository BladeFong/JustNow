# 时间段计算模块

> 对应 task_plan.md M3

# 阶段规划、决策记录

## 定位和功能描述

计算每日时间段的起止和剩余时间。常规时间段组长期生效，其他时间段组在开启且日期匹配时替代常规时间段。

时间段模块只负责解释"哪些时间段组可配置、哪一组覆盖常规生效"。主界面、推荐引擎、任务执行等调用方业务逻辑不在本模块讨论范围内。

## 整体规划和决策

### 时段定义（常规默认）

| 序号 | 名称 | 开始 | 结束 | 特殊规则 |
|------|------|------|------|----------|
| 0 | 早上 | 09:00 | 11:30 | — |
| 1 | 午休 | 11:30 | 13:30 | 优先"片刻" |
| 2 | 下午 | 13:30 | 17:30 | — |
| 3 | 晚餐 | 17:30 | 19:00 | 优先"片刻" |
| 4 | 晚上 | 19:00 | 22:00 | 四象限反转 |

### 时段定义（工作日默认）

| 序号 | 名称 | 开始 | 结束 | 特殊规则 |
|------|------|------|------|----------|
| 0 | 早上 | 08:30 | 11:30 | — |
| 1 | 午休 | 11:30 | 14:00 | 优先"片刻" |
| 2 | 下午 | 14:00 | 18:00 | — |
| 3 | 晚餐 | 18:00 | 20:00 | 优先"片刻" |
| 4 | 晚上 | 20:00 | 22:00 | 四象限反转 |

### 可复用模型

```java
public interface PeriodModel {
    List<TimePeriod> getPeriods();
    boolean isWorkday(LocalDate date);
}
```

| 模式 | 说明 | 状态 |
|------|------|------|
| `RegularModel` | 常规默认 5 个时段，长期生效 | 已实现 |
| `WorkdayModel` | 工作日默认 5 个时段，开启后在工作日替代常规时间段 | 已实现 |
| `SpringFestivalModel` | 春节，日期由数据源获取，时段从常规复制 | 已实现 |
| `LongVacationModel` | 长假（境外），用户自定义日期+时段 | 已实现 |
| `SummerVacationModel` | 暑假，默认 07-01~08-31 | 已实现 |
| `WinterVacationModel` | 寒假，默认 01-15~02-20 | 已实现 |

### 时段组命中优先级
SUMMER_VACATION > WINTER_VACATION > SPRING_FESTIVAL > LONG_VACATION > WORKDAY > REGULAR（兜底始终命中）

### 核心计算

```java
public class TimeCalculator {
    public int getCurrentPeriodType(LocalTime now, List<TimePeriod> periods);
    public int getRemainingMinutes(LocalTime now, TimePeriod period);
    public boolean canFit(TimePeriod period, LocalTime now, int focusMinutes);
}
```

# 研究发现、技术决策

### 春节与新年显示规则

- 春节和新年互斥显示，不能同时作为可配置时间段组出现。
- 新年是否显示由用户所在地区获取到的节假日数据决定。
- 春节只在整个中国范围显示。
- 节假日数据源、节假日与国家/地区的关系由 [节假日数据获取模块](holiday-data.md) 处理，时间段模块只引用其最终判断结果。

### 作息类型与地区

- `证券从业` 只在中国大陆地区显示；其他地区如果已保存为 `证券从业`，自动回退到 `常规作息`。
- 设备国家/地区由主线统一读取，入口为 `RegionSettings`。

### 开关行为
| 组类型 | lastEditedAt=0 | lastEditedAt>0 |
|--------|---------------|----------------|
| holiday (春节) | 有未来数据→直接打开；无数据→锁灰不可开 | 有未来数据→直接打开；无数据→锁灰不可开 |
| vacation (寒暑假/长假) | 弹回 -> 弹编辑 | 直接打开 |

### 编辑对话框
- 日期范围：底层保存 `startMonthDay`/`endMonthDay`（MM-dd），保持年度循环匹配规则；编辑界面显示 `yyyy-MM-dd`。
- 时间行：时段名称在左，时间组整体右对齐；时间入口和日期入口统一浅蓝编辑块、黑色文字。

### 编辑约束（2026-05-26）
- 交互：步进按钮（+/-15min，长按加速）+ PopupWindow 浮层滚轮（hour + minute 双轮）
- 联动：磁盘分区式——调整边界只推相邻时段对应边界，另一端不动
- 约束：早上 >= 06:00，晚上 <= 23:00，午休/晚餐 >= 1h，其余 >= 30min，粒度 15min
- 移除 `PeriodTimePickerDialog.java`（第一版，findNumberPicker 跨版本闪退）

### 时间选择 PopupWindow 显示约定（2026-06-06）

时间段编辑与主界面底部截止时间共用 `popup_time_picker`。根布局不能依赖 `wrap_content + match_parent` 按钮行反向撑宽，否则真机上可能出现右侧按钮被裁切或不可见。统一约定：
- `popup_time_picker_min_width` 在 `dimens.xml` 配置（当前 160dp）
- 标题、滚轮行、按钮组均居中；加宽不能改成内容靠左、按钮靠右
- `NumberPicker` 时间字号用 `text_size_title`
- 时间段编辑入口按屏幕边缘限制 `showAsDropDown` 的水平偏移，靠右点击时优先保持弹窗完整显示

### 全项目审查修复（2026-05-30）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F2/F5

- [x] `PeriodConfigFragment` `require*()` 异步崩溃加 `isAdded()`/`getView()` null 守卫
- [x] `PeriodConfigFragment` Handler 泄漏改单例 + `onViewRecycled` 清理

### Repository 缓存并发 + 假期初始化去重（2026-06-03）

- `TimePeriodRepository.update()`/`updateGroup()` 先清缓存再异步写 DB，窗口期内缓存可被旧数据回填 → 改为先写 DB 成功后清缓存

### 全项目审查修复（2026-05-30）

- `PeriodConfigFragment` requireContext/requireView 在异步回调时可能崩溃 → 加 null 守卫
- Handler 泄漏：匿名 Handler 持有 Fragment 引用 → 改单例 + onViewRecycled 清理
- `PeriodConfigViewModel` sync/async 假期初始化重复 → 提取 `fillVacationDefaultsCore` / `fillSpringFestivalDefaultsCore`，async 版委托 Core 后自行持久化

### 默认值填充
- vacation 组首次启用：日期 = 当天~3天后，时段 = 从 REGULAR 复制
- spring_festival 首次启用：日期 = 从 holiday_cache 读 range，时段 = 从 REGULAR 复制
- 编辑对话框弹出前调用 `ensureGroupDefaults` 同步保证数据就绪

### 假期组弹窗时段预写入修复（2026-06-04）

`fillVacationDefaultsCore` / `fillSpringFestivalDefaultsCore` 方法注释写"不持久化"，但实际通过 `copyPeriodsFromTemplate()` → `insertPeriods()` 在**打开弹窗时**就把时段写入了 DB。用户取消返回后时段已留在 DB，造成不一致状态。

修复：
- `fillVacationDefaultsCore` / `fillSpringFestivalDefaultsCore`：不再写 DB，改为从 REGULAR 加载内存模板返回
- `ensureDefaultsAndLoadPeriods`：模板时段直接传 UI 展示，不写 DB
- `updateGroupAndPeriods`：按 `p.id == 0` 分流，新时段走 `insertPeriods`，已有走 `update`
- `initVacationDefaultsIfNeeded` / `initSpringFestivalPeriodsIfNeeded`：保留显式写入（开关开启时组需要时段才能运作）

### 春节无未来数据锁灰（2026-06-04）

春节日期依赖 `holiday_cache` 中 `festivals[type=spring_festival]`，无固定 MM-dd 兜底。今年春节已过、来年数据未下载时，时段组形同虚设。

规则：
- `hasSpringFestivalFutureDataSync()`：查当年+来年 `holiday_cache`，解析 JSON 找 `start >= 今天`
- 无未来数据时：开关锁灰 + 行尾提示"暂无来年数据" + 点击不弹编辑窗
- `updateGroupEnabled()` 也做拦截，无数据时静默拒绝开启
- 有未来数据时恢复正常

### 安排页时段组选项 3 个月窗口过滤（2026-06-04）

> 设计文档：[../docs/superpowers/specs/2026-06-04-task-schedule-redesign.md](../docs/superpowers/specs/2026-06-04-task-schedule-redesign.md)

安排页类型选择器只显示未来 3 个月内能命中的时段组，避免用户安排太长远的事情。

`PeriodGroupRuleResolver.canMatchInNextThreeMonths()`：
- 窗口 `[today, today+3months]`，只比 MM-dd
- vacation 类型：MM-dd 区间与窗口交集 → 可命中
- 春节：今年+来年 holiday_cache 节日 start 在窗口内 → 可命中
- 工作日/REGULAR：始终可命中
- `TaskScheduleViewModel.buildEnabledPeriodGroups()` 调用过滤

### canMatchInNextThreeMonths 跨年窗口修复（2026-06-05 审查修复）

> 审查报告：[../docs/code-review-20260604.md](../docs/code-review-20260604.md) #1

`vacationCanMatch()` 原用 `windowStart <= groupEnd && groupStart <= windowEnd` 判断区间重叠，未处理窗口跨年（windowStart > windowEnd）场景，导致 11-12 月时次年 1-2 月的假期组被错误排除。修复：跨年时复用 `isInMonthDayRange()` 判断假期组两端点是否落入窗口范围。

### 假期初始化逻辑去重 + 缓存修复（2026-06-03 审查修复）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #3 #7

- [x] `TimePeriodRepository.mCachedTimelinePeriods`、`mCachedAllPeriods`：`ArrayList` → `volatile CopyOnWriteArrayList`
- [x] `update()` / `updateGroup()`：先写 DB 成功后再清缓存，修复"先清缓存再异步写 DB"导致缓存在窗口期被旧数据回填
- [x] `PeriodConfigViewModel`：`fillVacationDefaultsSync` / `initVacationDefaultsIfNeeded` 提取 `fillVacationDefaultsCore`；`fillSpringFestivalDefaultsSync` / `initSpringFestivalPeriodsIfNeeded` 提取 `fillSpringFestivalDefaultsCore`。async 版通过 `runInBackground` 包装调用 Core 方法后自行持久化

### 依赖
- 节假日数据（判断当天是否为节假日）
- 用户自定义时段配置（Room `time_periods` 表）

### 扩展点
- 新增模式只需实现 `PeriodModel` 接口
- 模式切换逻辑集中在一处
