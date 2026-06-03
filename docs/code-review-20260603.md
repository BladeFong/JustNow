# 代码审查报告 2026-06-03

## [blocking] 并发安全问题

- [x] ### #1 TaskExecutionRepository 缓存并发修改风险
- **文件**: `TaskExecutionRepository.java:98-103`
- **问题**: `addToTodayCache()` 直接修改 `mCachedTodayExecutions` ArrayList。`mDb.runInBackground()` 走 2 线程池，两个任务同时完成时可能并发修改同一个 ArrayList，导致数据损坏或 `ConcurrentModificationException`
- **建议**: 用 `CopyOnWriteArrayList` 或在 add 位置同步

- [x] ### #2 TagRepository 缓存并发修改风险
- **文件**: `TagRepository.java:206-211, 214-217`
- **问题**: `mCachedTags` ArrayList 的 add/removeIf 操作在 2 线程池中无同步保护
- **建议**: 同 #1，加同步保护

- [x] ### #3 TimePeriodRepository 缓存与写入竞态
- **文件**: `TimePeriodRepository.java:184-192`
- **问题**: `update()` 先调 `clearCache()` 再异步写 DB。clear 后、DB 写入完成前的窗口期，其他线程读操作命中 DB 查询并重新填充缓存为旧值，DB 写入完成后缓存不再被清除，长期持有过期数据
- **建议**: 先写 DB，成功后再清缓存

## [important] 逻辑问题

- [x] ### #4 DisplayEngine.computeByQuadrant 的 reverseQuadrant 参数被忽略
- **文件**: `DisplayEngine.java:177-221`
- **问题**: `computeByQuadrant()` 接收 `reverseQuadrant` 参数但未传给 `buildSortedItemsForQuadrant()`
- **建议**: 确认是否需要传给排序方法，或移除该参数

- [x] ### #5 AlarmReceiver 部分 handler 缺少 goAsync()
- **文件**: `AlarmReceiver.java:52-56, 57-62`
- **问题**: `ACTION_POSTPONE` 和 `ACTION_DAILY_REFRESH` handler 未调用 `goAsync()`，进程可能在后台 DB 操作完成前被系统回收
- **建议**: 统一加 `goAsync()` + `pendingResult.finish()`

- [x] ### #6 MainViewModel 两个方法大量重复
- **文件**: `MainViewModel.java:351-458`
- **问题**: `recomputeSync()` 和 `computeQuadrantOverviewSync()` 约 30 行完全相同的逻辑
- **建议**: 提取公共方法

- [x] ### #7 PeriodConfigViewModel 假期初始化逻辑重复
- **文件**: `PeriodConfigViewModel.java:110-155, 192-243`
- **问题**: sync 版和 async 版几乎完全重复
- **建议**: 抽取 private 方法共享核心逻辑

- [x] ### #13 DisplayEngine.computeByQuadrant 的 degradeMap 参数被忽略
- **文件**: `DisplayEngine.java:191-221`
- **问题**: `computeByQuadrant()` 接收 `degradeMap` 参数，MainViewModel 调用时也传入了实际数据，但未传给 `buildSortedItemsForQuadrant()`，象限视图不应用降级逻辑（任务在降级期间仍显示在原象限而非降级后象限）
- **建议**: 在 `buildSortedItemsForQuadrant()` 中依据 `degradeMap` 调整任务有效象限，或在分组时使用 `effectiveQuadrant` 而非 `t.quadrant`

## [suggestion] 其他问题

- [x] ### #8 硬编码颜色值
- **文件**: `TaskInputViewModel.java:271`
- **问题**: 新建标签颜色硬编码 `0xFF1A73E8`
- **建议**: 提取为常量

- [x] ### #9 死代码
- **文件**: `HolidayJsonParser.java:96-102`
- **问题**: `escapeJson()` 无调用方
- **建议**: 删除

- [x] ### #10 ChoreHiddenTodayStore 跨进程读写无锁（误报）
- **文件**: `ChoreHiddenTodayStore.java`
- **问题**: ~~SharedPreferences 读写无原子性，两进程同时写入同一日期会互相覆盖~~ 误报。核实：Widget 模块完全不引用 ChoreHiddenTodayStore，仅主进程内 BaseTaskViewModel / MainViewModel 使用。SharedPreferences.apply() 单进程内线程安全，跨进程竞态前提不成立。
- **结果**: 误报，已记录到 `docs/code-review-ignore.md`

- [x] ### #11 PeriodGroupRuleResolver 可能重复创建（误报）
- **文件**: `JustNowApplication.java:129-182`
- **问题**: ~~创建逻辑脆弱，调用顺序颠倒会创建两次~~ 误报。`getTimePeriodRepository()` 和 `getPeriodGroupRuleResolver()` 均有 null 保护，共享同一 `mPeriodGroupRuleResolver` 实例，不存在双重创建。
- **结果**: 误报，已记录到 `docs/code-review-ignore.md`

- [x] ### #12 ReminderDetailViewModel 字符串常量耦合
- **文件**: `ReminderDetailViewModel.java:149`
- **问题**: 使用字符串常量做类型匹配，不够类型安全
- **建议**: 改用 enum 或 sealed class

---

## 未处理项汇总

无未处理项。全部 13 项已关闭（11 修复 + 2 误报）。
