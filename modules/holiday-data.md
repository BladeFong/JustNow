# 节假日数据获取模块

> 对应 task_plan.md M8

# 阶段规划、决策记录

## 定位和功能描述

获取中国节假日数据，支持按需扩展为不同数据源和不同模式。

## 整体规划和决策

### 数据源

| 地区 | 数据源 | 格式 | 优先级 | 状态 |
|------|--------|------|--------|------|
| 中国大陆 | [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn) | JSON | 主源 | 已实现 |
| 中国大陆 | [Apple Calendar](https://calendars.icloud.com/holidays/cn_zh.ics) | ICS | 备用 | 已实现 |
| 香港 | [1823.gov.hk](https://www.1823.gov.hk/common/ical/tc.ics) | ICS | 唯一 | 已实现 |
| 澳门 | [gov.mo](https://www.gov.mo/zh-hant/public-holidays/ical/) | ICS | 唯一 | 已实现 |
| 其他地区 | Nager.Date API | — | — | 待实现 |

### 地区节假日口径
- 设备国家/地区由主线统一读取，入口为 `RegionSettings`。
- 中国大陆：春节由 holiday-cn JSON 数据源获取，日期取 `name="春节"` + `isOffDay=true` 的 min-max。
- 香港/澳门：假期列表由 ICS 订阅获取。
- `证券从业` 仅中国大陆地区可选。
- 新增了"新年" -> 已删除，用"长假"替代（境外地区）。

### 缓存策略
- 按年缓存，存储在 Room `holiday_cache` 表，JSON 格式
- 新增列：`holiday_count`(INT) / `last_sync_month`(INT, yyyyMM)
- `year` 列设唯一索引
- APP 启动时 `triggerHolidaySync()` 独立线程执行：`shouldSyncThisMonth()` 月度检查 + fetch + save
- 网络成功 -> `lastSyncMonth` 标记当月，当月不再重复下载
- 网络失败(IOException) -> WorkManager `HolidaySyncWorker` 延迟重试
- 新数据 `holidayCount > 缓存` -> 写入 dataJson + holidayCount；否则仅 updateSyncMonth
- 移除 on-demand fetch

### 全项目审查修复（2026-05-30）+ 接口重构（2026-06-01）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F1/F3/F4、[../docs/code-review-20260531.md](../docs/code-review-20260531.md) #5

- [x] `HolidayJsonParser.extractValue()` 布尔值解析 Bug：`"isOffDay": true` 被截取为 `"true,"` → 改用字符串直接匹配
- [x] `PeriodGroupRuleResolver.matchesSync()` 节假日恒 false → 补 `matchesHolidayDataSync()`
- [x] `HolidayCacheManager.save()` 查比写非原子 → DAO `@Transaction default` 单次原子写入
- [x] `HolidayJsonParser`/`IcsParser` 加 `Log.w` 异常日志
- [x] `HolidaySyncWorker` IOException 区分：`UnknownHostException`/`SocketTimeoutException` → retry，其余 → failure
- [x] **2026-06-01**：`HolidayDataSource` 接口改抽象类，`fetch()` 设 `final` 提取 HTTP 模板，`getUrl` + `parseAndFill` 抽象方法

### 核心接口

```java
public interface HolidayDataSource {
    HolidayCacheEntity fetch(int year) throws IOException;
}
```

`fetch()` 直接返回 `HolidayCacheEntity`，裸 JSON 封装在数据层内。

数据源选择按设备地区分发，CN 支持多源 fallback：
- CN -> `[ChinaGovSource, AppleCalendarSource]` -> 遍历列表，首个成功即停止
- HK -> `[HongKongGovSource]`
- MO -> `[MacauGovSource]`
- 其他 -> 空列表（不缓存）

### 模式扩展点

| 扩展点 | 说明 | 状态 |
|--------|------|------|
| ChinaGovSource | holiday-cn JSON | 已实现 |
| AppleCalendarSource | Apple Calendar ICS（CN 备用） | 已实现 |
| HongKongGovSource | 1823.gov.hk ICS | 已实现 |
| MacauGovSource | gov.mo ICS | 已实现 |
| NagerDateSource | Nager.Date API | 待实现 |
| LocalJsonSource | 本地 JSON 兜底 | 预留 |

### 触发时机
- APP 启动时 `JustNowApplication.triggerHolidaySync()` — 独立线程
- WorkManager `HolidaySyncWorker` — 后台重试（NetworkType.CONNECTED）
- `PeriodConfigViewModel.applyProfileDefaults()` — 构造时调用
- `PeriodConfigFragment.refreshGroupRows()` — 立即渲染 REGULAR，异步确认缓存后追加其他组

### 依赖
- OkHttp（网络请求）
- WorkManager（后台保障）
- Room（缓存存储）

# 研究发现、技术决策

### 数据源详情

**中国大陆 — holiday-cn（主源）**
- URL：`https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/{year}.json`
- 解析器：`HolidayJsonParser`，按 `name="春节"` + `isOffDay=true` 提取假期区间

**中国大陆 — Apple Calendar（备用）**
- URL：`https://calendars.icloud.com/holidays/cn_zh.ics`（单 URL 多年数据）
- 解析器：`IcsParser`，通过 `X-APPLE-SPECIAL-DAY` 属性区分 `WORK-HOLIDAY`（假日）和 `ALTERNATE-WORKDAY`（补班）
- 仅在 GitHub 主源失败时使用（网络不通、限速等）
- 覆盖 2024-2029 年数据，含节气、传统节日等额外信息

**香港 — 1823.gov.hk**
- URL：`https://www.1823.gov.hk/common/ical/{lang}.ics`
- 解析器：`IcsParser`

**澳门 — gov.mo**
- URL：`https://www.gov.mo/{lang}/public-holidays/ical/`
- 解析器：`IcsParser`

### 缓存 JSON 格式
```json
{"year":2026,"source":"holiday-cn","holidays":["2026-01-01",...],"makeupWorkdays":["2026-01-04",...],"festivals":[{"type":"spring_festival","start":"2026-02-15","end":"2026-02-23"}]}
```

### 架构决策（2026-05-16）
- fetch() 返回 Entity 而非 String，裸 JSON 不出数据层
- parser 直接填 Entity：`HolidayJsonParser.fill(entity, ...)` / `IcsParser.fill(entity, ...)`
- 月份标记存缓存同表：`lastSyncMonth` 与 dataJson 同一记录
- 网络成功即标记月份，不管是否有数据
- 数据更新条件：新数据 holidayCount > 缓存 holidayCount

### 工作日判断设计决策（2026-05-15）
- 常规作息和证券从业共用 `LEGAL_HOLIDAY` 策略 + 同一判断流程
- 证券从业不跟补班（`isOffDay=false` 时跳过补班检查，回落 Mon-Fri）
- JSON key `workdays` -> `makeupWorkdays`，读时兼容旧 key
- `IcsParser.isOffDay()` 通用日期缓存查询（一次 indexOf 定位）

### 已知局限
- ~~GitHub raw 在大陆可能被限速或屏蔽~~ → 已有 Apple Calendar 备用源
- 当年数据可能未公布（404）→ 备用源覆盖多年
- 香港/澳门 ICS 数据中假期名称关键词匹配不完整

### 全项目审查修复（2026-05-30）+ 接口重构（2026-06-01）

- `HolidayJsonParser.extractValue()` 布尔值解析 Bug：原始 JSON `"isOffDay": true` 被截取后 `Boolean.parseBoolean("true,")` 永 false
- `PeriodGroupRuleResolver.matchesSync()` 节假日分支遗漏调用 → 补 `matchesHolidayDataSync()`
- `HolidayCacheManager.save()` 非原子 → DAO `@Transaction`
- `HolidayDataSource` 接口改抽象类：提取 HTTP 模板，子类仅需实现 `getUrl()` + `parseAndFill()`

### 死代码清理（2026-06-03）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #9

- `HolidayJsonParser.escapeJson()` 无任何调用方，已删除。相关测试用例同步移除。
