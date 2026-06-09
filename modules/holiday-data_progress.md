# holiday-data 进度日志

### 2026-06-09 — Widget 触发节假日同步 + 日内节流

`triggerHolidaySync()` 改为 public，Widget `onUpdate()`（系统标准刷新，约 30 分钟一次）新增触发调用。日内节流：已有缓存数据时同一天内不重复检查；无缓存时不节流，保证首次拉取不被跳过。

### 2026-06-09 — Apple Calendar 备用数据源适配

Apple Calendar 公开订阅（`calendars.icloud.com/holidays/cn_zh.ics`）适配为 CN 地区备用数据源。

改动：
- `IcsParser`：新增 `X-APPLE-SPECIAL-DAY` 解析（`WORK-HOLIDAY` → holidays，`ALTERNATE-WORKDAY` → makeupWorkdays），兼容 `SUMMARY;LANGUAGE=` 前缀
- 新建 `AppleCalendarSource`：继承 `HolidayDataSource`，单 URL 多年数据
- `HolidaySourceFactory`：`createForRegion` → `createSourcesForRegion` 返回 `List`，CN 地区 `[ChinaGovSource, AppleCalendarSource]`
- `HolidaySyncWorker` / `triggerHolidaySync`：遍历数据源列表，首个成功即停止

状态：编译 + IcsParserTest + HolidaySyncFlowTest 全部通过。

### 2026-06-01 — 重复业务逻辑全面重构

> 详见：[modules/task-execution.md](modules/task-execution.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/holiday-data.md](modules/holiday-data.md)

**状态**：编译+251 测试通过。BaseTaskViewModel 模板方法、HolidayDataSource 抽象类。

### 2026-05-30 — 全项目代码审查与修复

> 审查报告：[docs/code-review-20260530.md](docs/code-review-20260530.md)

**状态**：编译 + 全量测试通过。47 项发现 → 排除 5 误报 → 分 8 批修复 24 项。

后续验证建议：[ ] 节假日数据真机验证、[ ] Widget 标签筛选真机验证、[ ] logcat assertNotMainThread 检查

详见：[modules/task-execution.md](modules/task-execution.md)、[modules/holiday-data.md](modules/holiday-data.md)、[modules/widget.md](modules/widget.md)、[modules/reminder-delay.md](modules/reminder-delay.md)、[modules/smart-display.md](modules/smart-display.md)、[modules/time-period.md](modules/time-period.md)、[modules/tag.md](modules/tag.md)、[modules/task-input.md](modules/task-input.md)

### 2026-05-16 — 节假日数据月度下载频率控制

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 进度日志

架构重构：fetch() 返回 Entity + throws IOException + parser 填 fill()。compileDebugJavaWithJavac + testDebugUnitTest BUILD SUCCESSFUL。

### 2026-05-15 — 节假日数据驱动工作日判断 + 无数据兜底 + androidTest 维护

> 详见：[modules/holiday-data.md](modules/holiday-data.md) — 进度日志
> 详见：[modules/testing.md](modules/testing.md) — 进度日志

完整业务流程测试（77 用例 0 失败）。无节假日数据兜底逻辑实现。androidTest 源码修正。
