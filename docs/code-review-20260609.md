# Code Review Report — 2026-06-09

- 审查范围：今日全部代码提交（`1204d858^..1eb5440`）
- 审查时间：2026-06-09
- 审查人：code-auditor

## 审查范围

今日提交涵盖以下功能：
1. 琐碎任务允许时段外开始
2. 时间线刻度色值统一
3. Apple Calendar 节假日备用数据源适配
4. Widget `onUpdate()` 触发节假日同步与日内节流
5. Widget `onUpdate()` 数量变化刷新优化与 `scheduleNextMinuteBoundary` 收敛

涉及文件：`TaskStartGuard`、`MainViewModel`、`TimelineView`、`IcsParser`、`AppleCalendarSource`、`HolidaySourceFactory`、`HolidaySyncWorker`、`JustNowApplication`、`JustNowWidgetProvider`、`WidgetUpdateHelper`、`WidgetPermissionGateActivity`、节假日测试资源与模块文档。

## 结果概要

| 级别 | 数量 |
|------|:--:|
| important | 3 |
| nit | 0 |
| suggestion | 0 |
| praise | 0 |
| **合计** | **3** |

---

## 问题详情

### [x] #1 [important] Apple ICS 中无 `X-APPLE-SPECIAL-DAY` 的节气/普通节日会被误判为休息日

**文件**：`app/src/main/java/com/nearby/justnow/data/holiday/IcsParser.java` 第 150-157 行

`IcsParser.buildJson()` 在 `event.specialDay == null` 时沿用传统 ICS 逻辑，把事件日期加入 `holidays`。但新增的 Apple Calendar 源里大量节气/传统节日没有 `X-APPLE-SPECIAL-DAY`，例如测试资源中的 `小寒`、`大寒`、`立春`、`除夕`、`春节` 等事件都没有该字段。

**结果**：当 CN 主源失败并使用 Apple 备用源时，节气和普通节日会进入节假日缓存，`IcsParser.isOffDay()` 返回 `true`，常规作息会把这些日期当作休息日，工作日判断错误。

**建议**：为 Apple 源使用独立解析口径，或在 `IcsParser.fill()` 中按 source/mode 区分：Apple Calendar 只允许 `WORK-HOLIDAY` 写入 `holidays`、`ALTERNATE-WORKDAY` 写入 `makeupWorkdays`；无 `specialDay` 的 Apple 事件不应写入 `holidays`。HK/MO 传统 ICS 仍保留无 `specialDay` 即假期的旧逻辑。

**建议补测**：新增用例断言 Apple ICS 中 `SUMMARY;LANGUAGE=zh_CN:小寒` 且无 `X-APPLE-SPECIAL-DAY` 时，`isOffDay(2026-01-05)` 返回 `null`。

**修复结果**：已修复。`IcsParser` 新增 `fillAppleCalendar()` 和 `IcsParseMode.APPLE_SPECIAL_DAY_ONLY`，`AppleCalendarSource` 改为调用 Apple 专用入口。Apple 模式下只有 `WORK-HOLIDAY` 写入 `holidays`、`ALTERNATE-WORKDAY` 写入 `makeupWorkdays`；HK/MO 仍通过 `fill()` 保持传统 ICS 全事件假日模式。

**审核意见**：通过。`fill_appleMixedEvents_correctlyClassifies()` 已覆盖无 `specialDay` 的节气不进入休息日缓存，原误判路径已关闭。

---

### [x] #2 [important] `onUpdate()` 用本次 `appWidgetIds` 清理状态，会误删其他 Widget 实例筛选

**文件**：`app/src/main/java/com/nearby/justnow/widget/JustNowWidgetProvider.java` 第 47-50 行

`onUpdate(Context, AppWidgetManager, int[] appWidgetIds)` 的 `appWidgetIds` 表示本次需要更新的 Widget ID，不保证是当前全部活跃实例。当前代码直接用 `appWidgetIds.length` 维护 `sLastWidgetCount`，并把 `appWidgetIds` 传给 `WidgetFilterStore.clearMissingWidgets()`。

**结果**：多 Widget 场景下，如果系统只对部分实例触发 `onUpdate()`，其他仍存在的实例会被当作 missing widget，筛选状态被误删。进程重启后 `sLastWidgetCount` 归零，第一次收到部分更新时风险更明显。

**最终处置建议**：确认关闭，并已记录到 `docs/code-review-ignore.md`。不要在 `onUpdate()` 中执行筛选清理。当前项目有 Widget 配置页，新增 Widget 首次渲染由配置页完成；周期 `onUpdate()` 在 AOSP 常规路径下使用已配置实例集合。为避免每次 `onUpdate()` 额外跨 service 查询全集并触发不必要全量刷新，保留 `appWidgetIds.length + sLastWidgetCount` 的数量变化刷新优化；删除清理交给 `onDeleted()` 精确处理。原风险点是把本次 `appWidgetIds` 当全集传给 `clearMissingWidgets()`，移除该调用即可关闭状态误删问题；不再要求采用 `getAppWidgetIds()` 查询全集或对全量 ID 执行 `updateAllWidgets()`。

**补测结论**：不适用。Robolectric 模拟“只传其中一个 ID”不再作为有效风险前提；后续不以该场景要求补测。

**处理结果**：确认关闭。`JustNowWidgetProvider.onUpdate()` 不再调用 `clearMissingWidgets()`，避免把本次 `appWidgetIds` 当全集清理筛选状态；`onDeleted()` 继续按系统回调精确清理被删除 Widget 的筛选。数量变化刷新仍沿用本次 `appWidgetIds.length`，避免每次 `onUpdate()` 额外跨 service 查询全量 ID。

**审核意见**：通过。该项已作为确认关闭记录到 `docs/code-review-ignore.md`；后续审查不应再把 `getAppWidgetIds()` / 全量 `updateAllWidgets()` 作为本项仍需采纳的建议。按“不假设不存在场景”的规则，不保留 Robolectric 人工构造缺失 ID 的测试。

---

### [x] #3 [important] 多数据源 fallback 会把 HTTP 失败返回的空实体当成功保存

**文件**：
- `app/src/main/java/com/nearby/justnow/data/holiday/HolidayDataSource.java` 第 43-44 行
- `app/src/main/java/com/nearby/justnow/data/holiday/HolidaySyncWorker.java` 第 67-71 行
- `app/src/main/java/com/nearby/justnow/JustNowApplication.java` 第 128-133 行

`HolidayDataSource.fetch()` 在 HTTP 非成功或 body 为空时返回 `HolidayCacheManager.emptyEntity(year)`，不会抛出 `IOException`。新增多源循环只要 `source.fetch(year)` 返回实体就立即 `cacheManager.save(entity)` 并 `return`。

**结果**：CN 主源 GitHub 404、限流返回非 2xx、或 body 为空时，代码会保存空缓存并提前结束，Apple Calendar 备用源不会被尝试。`HolidaySyncWorker` 同样会返回 `Result.success()`，后续重试也不会触发。

**建议**：把 HTTP 非 2xx / 空 body 视为数据源失败，抛出 `IOException` 或返回可区分的失败状态；多源循环只在实体包含有效 `dataJson` 时保存并停止，否则继续下一个 source。所有 source 失败后再进入 WorkManager retry/failure 逻辑。

**建议补测**：构造 CN 主源返回 404、Apple 源返回有效 ICS 的 fake client，断言最终缓存 source 为 `apple-calendar` 且不会保存空实体。

**修复结果**：已修复。`HolidayDataSource.fetch()` 对 HTTP 非成功、空 body、解析后 `dataJson` 为空均抛 `IOException`。`HolidaySyncWorker` 和 `JustNowApplication.triggerHolidaySync()` 的多源循环因此会继续尝试下一个 source，只有成功解析出有效 `dataJson` 才保存并停止。

**审核意见**：通过。`HolidayDataSourceTest` 已覆盖 404、500、空 body、解析无 `dataJson` 的失败语义；多源 fallback 不再会把这些无效响应当成功保存。

---

## 审核记录（2026-06-09）

审核人：code-auditor

| 编号 | 级别 | 审核结果 | 说明 |
|------|------|----------|------|
| #1 | important | 已修复 | Apple 源使用专用 ICS 解析模式，只按 `X-APPLE-SPECIAL-DAY` 写入休/班缓存 |
| #2 | important | 确认关闭 | 已记录到 `docs/code-review-ignore.md`；`onUpdate()` 移除筛选清理，不要求 `getAppWidgetIds()` 全量查询或全量刷新 |
| #3 | important | 已修复 | 无效响应、空 body、无效解析结果改为抛 `IOException`，多源 fallback 继续尝试 |

已运行定向测试：

```bash
~/gradlew-wsl.sh --no-daemon testDebugUnitTest \
  --tests com.nearby.justnow.data.holiday.IcsParserTest \
  --tests com.nearby.justnow.data.holiday.HolidayDataSourceTest \
  --tests com.nearby.justnow.data.holiday.HolidaySyncFlowTest
```

结果：`BUILD SUCCESSFUL`。

## 未处理项汇总

无。#1/#3 已修复；#2 已确认关闭并记录到 `docs/code-review-ignore.md`。

> 生成日期：2026-06-09
> 审核日期：2026-06-09
