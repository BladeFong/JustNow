# 时间段计算模块

> 对应 task_plan.md M3

# 阶段规划、决策记录 （拆分自 task_plan.md）

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

# 研究发现、技术决策 （拆分自 findings.md）

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
| holiday (春节) | 直接打开 | 直接打开 |
| vacation (寒暑假/长假) | 弹回 -> 弹编辑 | 直接打开 |

### 编辑对话框
- 日期范围：底层保存 `startMonthDay`/`endMonthDay`（MM-dd），保持年度循环匹配规则；编辑界面显示 `yyyy-MM-dd`。
- 时间行：时段名称在左，时间组整体右对齐；时间入口和日期入口统一浅蓝编辑块、黑色文字。

### 编辑约束（2026-05-26）
- 交互：步进按钮（+/-15min，长按加速）+ PopupWindow 浮层滚轮（hour + minute 双轮）
- 联动：磁盘分区式——调整边界只推相邻时段对应边界，另一端不动
- 约束：早上 >= 06:00，晚上 <= 23:00，午休/晚餐 >= 1h，其余 >= 30min，粒度 15min
- 移除 `PeriodTimePickerDialog.java`（第一版，findNumberPicker 跨版本闪退）

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

# 进度日志 （拆分自 progress.md）

- [x] 6 种时段组（REGULAR/WORKDAY/SPRING_FESTIVAL/LONG_VACATION/SUMMER_VACATION/WINTER_VACATION）
- [x] 常规 + 工作日默认时段，AppDatabase onCreate 自动填充
- [x] TimeRemainingCalculator 计算当前时段 + 剩余分钟数 + 容纳判断
- [x] RecyclerView 配置页面展示，PeriodConfigViewModel 管理
- [x] 字符串资源化（getPeriodName 使用资源 ID 映射）
- [x] 春节/新年互斥 -> 已删除新年，增长假；中国大陆春节、境外长假
- [x] 证券从业作息按设备国家/地区过滤，仅中国大陆地区显示
- [x] 作息类型切换：不可见组强制关闭，可见组保持原状
- [x] 时段组编辑对话框：日期范围 (DatePickerDialog) + 每区段时间 (TimePickerDialog)
- [x] 开关逻辑：非 holiday 组未编辑则弹回+弹编辑；holiday 组可直接打开
- [x] 初始化默认填充：vacation 组当天~3天后+常规时段；spring_festival 从缓存读日期+复制时段
- [x] PeriodGroupRuleResolver 异步 resolveActiveGroupTypeAsync（避免主线程 Room）
- [x] 编辑对话框布局优化：日期完整年份显示、时间行固定时间区、统一浅蓝编辑块和黑色文字
- [x] 日期/时间编辑入口抽取为统一时间编辑框风格
- [x] Android Studio `assembleDebug` 样式父级缺失问题已由用户修复并记录
- [x] 时段编辑约束重设计：步进按钮 + PopupWindow 浮层滚轮 + 磁盘分区联动 + 矢量箭头图标（2026-05-26）

### 2026-05-30 审查修复

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md)

- [x] PeriodConfigFragment require*() 异步崩溃修复 + Handler 泄漏修复
- [x] 编译通过

### 2026-06-03 审查修复

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- [x] TimePeriodRepository 缓存集合改为 CopyOnWriteArrayList；update/updateGroup 修复清缓存时序
- [x] PeriodConfigViewModel 假期初始化逻辑去重
- [x] 编译通过 + testDebugUnitTest 全通过

**状态**：🔨 已实现

### 2026-06-02 — code-review-20260602 修复

- [x] TimePeriodRepository 加实例级内存缓存：activeGroup（scheduleProfile 参数匹配）、timelinePeriods、allPeriods
- [x] 写操作（update/updateGroup）全清缓存（编辑稀缺，增量更新收益低）
- [x] `getAllPeriodsSync()` 返回防御性拷贝
- [x] Repository 收归 Application 单例，确保显示主界面/Widget/AlarmReceiver 共享同一缓存
