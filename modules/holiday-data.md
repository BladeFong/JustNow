# 节假日数据获取模块

> 对应 task_plan.md M8

# 阶段规划、决策记录 （拆分自 task_plan.md）

## 定位和功能描述

获取中国节假日数据，支持按需扩展为不同数据源和不同模式。

## 整体规划和决策

### 数据源

| 地区 | 数据源 | 格式 | 状态 |
|------|--------|------|------|
| 中国大陆 | [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn) | JSON | 已实现 |
| 香港 | [1823.gov.hk](https://www.1823.gov.hk/common/ical/tc.ics) | ICS | 已实现 |
| 澳门 | [gov.mo](https://www.gov.mo/zh-hant/public-holidays/ical/) | ICS | 已实现 |
| 其他地区 | Nager.Date API | — | 待实现 |

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

### 核心接口

```java
public interface HolidayDataSource {
    HolidayCacheEntity fetch(int year) throws IOException;
}
```

`fetch()` 直接返回 `HolidayCacheEntity`，裸 JSON 封装在数据层内。

数据源选择按设备地区分发：
- CN -> `ChinaGovSource` -> `HolidayJsonParser.fill(entity, ...)`
- HK -> `HongKongGovSource` -> `IcsParser.fill(entity, ...)`
- MO -> `MacauGovSource` -> `IcsParser.fill(entity, ...)`
- 其他 -> null（不缓存）

### 模式扩展点

| 扩展点 | 说明 | 状态 |
|--------|------|------|
| ChinaGovSource | holiday-cn JSON | 已实现 |
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

# 研究发现、技术决策 （拆分自 findings.md）

### 数据源详情

**中国大陆 — holiday-cn**
- URL：`https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/{year}.json`
- 解析器：`HolidayJsonParser`，按 `name="春节"` + `isOffDay=true` 提取假期区间

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
- GitHub raw 在大陆可能被限速或屏蔽
- 当年数据可能未公布（404）
- 香港/澳门 ICS 数据中假期名称关键词匹配不完整

# 进度日志 （拆分自 progress.md）

- [x] Room holiday_cache 表 + HolidayCacheEntity + DAO
- [x] `HolidayDataSource` 接口（2026-06-01 改为抽象类：`fetch()` 设 `final` 提取 HTTP 模板，`getUrl` + `parseAndFill` 抽象方法让子类自选解析器）
- [x] IcsParser（ICS 解析，供港澳使用）
- [x] HolidayJsonParser（JSON 解析，供中国大陆使用）
- [x] ChinaGovSource（holiday-cn JSON 数据源）
- [x] HongKongGovSource（1823.gov.hk ICS 数据源）
- [x] MacauGovSource（gov.mo ICS 数据源）
- [x] HolidayCacheManager（缓存读写，异步+同步双接口）
- [x] HolidaySyncWorker（WorkManager 后台同步）
- [x] triggerHolidaySync（APP 启动时直接 fetch + WorkManager 兜底）
- [x] **节假日数据驱动的工作日判断**（2026-05-15）
  - [x] `IcsParser.isOffDay()` 通用日期缓存查询
  - [x] `PeriodGroupRuleResolver.isWorkdaySync()` / `isWorkdayAsync()` 双策略
  - [x] 常规作息和证券从业共用 `LEGAL_HOLIDAY` 策略
  - [x] 证券从业不跟补班
  - [x] 完整业务流程测试（23 用例）
- [x] **无节假日数据时的兜底逻辑**（2026-05-15）
  - [x] `RegionSettings.isHolidayDataAvailable()` 判断数据源
  - [x] `applyProfileDefaults()`：无数据源时禁用 WORKDAY 组
  - [x] 寒暑假 + 长假组不依赖节假日数据，直接暴露可用
- [x] **节假日数据月度下载频率控制**（2026-05-16）
  - [x] fetch() 返回 Entity + throws IOException
  - [x] holidayCount / lastSyncMonth 列 + year 唯一索引
  - [x] shouldSyncThisMonth() 月度检查 + save(entity) 天数比较
  - [x] applyProfileDefaults() 查实际缓存 + 构造时初始化策略
  - [x] refreshGroupRows() 先渲染 REGULAR，异步追加其他组
  - [x] 移除 UI 路径 on-demand 网络请求
- [ ] Nager.Date API 接入（其他地区）
- [ ] 港澳订阅实时联网验证
