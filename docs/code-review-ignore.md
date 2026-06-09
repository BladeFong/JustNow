# 审查忽略项汇总

基于 `docs/` 下审查报告中有意跳过/不修/误报的问题。

---

## 按级别分组

### important

| 编号 | 来源 | 问题 | 理由 |
|------|------|------|------|
| #2 | 20260603-v2 | 四象限概览仍按原始象限分组，未应用降级后象限 | 设计如此。用户确认“四象限任务管理模块的目的在管理”，因此四象限概览/单象限管理页按原始象限分组，不应用降级后象限。确认日期：2026-06-03 |
| #2 | 20260609 | `onUpdate()` 的 `appWidgetIds` 可能是子集，建议 `getAppWidgetIds()` 查询全量后清理/刷新 | 确认关闭。重新核对 AOSP 常规路径和当前项目 Widget 配置页后，不再把“只传其中一个 ID”的 Robolectric 模拟作为有效风险前提；当前项目新增 Widget 首次渲染由配置页完成，周期 `onUpdate()` 通常使用已配置实例集合。最终处置是不在 `onUpdate()` 中执行筛选清理，删除清理交给 `onDeleted()` 精确处理；不要求每次 `onUpdate()` 额外 `getAppWidgetIds()` 查询全集或全量 `updateAllWidgets()`。确认日期：2026-06-09 |
| #13 | 20260602 | `BaseRepository.assertNotMainThread()` 仅 Log.w 不抛异常 | Room 自身在主线程执行同步查询已 crash，加一层 throw 无实质收益 |
| #16 | 20260602 | `ViewModelFactory` if-else 链做类型映射 | 项目无新增 ViewModel 计划，分支数不会增长。改为 Map 注册后每个 VM 仍需一行注册代码，代码量不减少，仅从 if-else 换成 Map.put，未降低维护成本 |
| #2 | 20260604 | `recomputeSync` 与 `computeQuadrantOverviewSync` 重复代码 | 误报。已多次优化，剩余相似调用错开、参数不同，无法自然提取。项目规范已加"有合理方法才提取""禁止硬造数据结构"约束。确认日期：2026-06-05 |

### suggestion

| 编号 | 来源 | 问题 | 理由 |
|------|------|------|------|
| #10 | 20260603 | ChoreHiddenTodayStore 跨进程读写无锁 | 误报。Widget 模块完全不引用 ChoreHiddenTodayStore，仅主进程使用。SharedPreferences.apply() 单进程线程安全，跨进程竞态前提不成立。确认日期：2026-06-03 |
| #11 | 20260603 | PeriodGroupRuleResolver 可能重复创建 | 误报。`getTimePeriodRepository()` 和 `getPeriodGroupRuleResolver()` 均有 null 保护，共享同一 `mPeriodGroupRuleResolver` 实例，不存在双重创建。确认日期：2026-06-03 |
| S1 | 20260530 | 搜索 SQL 使用前导通配符（建议 FTS） | 与 20260602 #27 同一底层问题。当前任务量百条内可接受 |
| S2 | 20260530 | 未检查网络可用性（ConnectivityManager） | WorkManager 已有网络约束，比 ConnectivityManager 更可靠 |
| #22 | 20260602 | 全局方向锁定影响折叠屏/大屏 | 个人效率工具，暂不考虑折叠屏适配 |
| #26 | 20260602 | 缺少 @Nullable/@NonNull 注解 | 项目初期未建立规范，全量补成本高。可改为新代码强制要求 |
| #27 | 20260602 | LIKE '%keyword%' 全表扫描 | 当前任务量百条内，已确认暂不处理。与 20260530 S1 合并为同一条决策 |
| #29 | 20260602 | minSdk=33 限制安装范围 | 项目初期已确定目标设备范围，属产品决策 |
| #4 | 20260608 | `CapturePickerActivity` 未调用 `enableEdgeToEdge()` | 误报。已用 `ViewCompat.setOnApplyWindowInsetsListener` 处理 insets，标准 edge-to-edge 适配方式。Android 15 强制 edge-to-edge 但不要求必须调用 `enableEdgeToEdge()`。确认日期：2026-06-08 |

### nit

| 编号 | 来源 | 问题 | 理由 |
|------|------|------|------|
| #30 | 20260602 | AppDatabase DCL 缺乏局部变量优化 | 微优化，约 25% volatile 读差异，实际影响可忽略 |
| #31 | 20260602 | WidgetPermissionGateActivity exported="true" | 无 intent-filter 意味着隐式 Intent 无法匹配，实际攻击面极小。改为 `false` 反而可能在某些 Launcher 上中断 Widget 添加流程，代价高于收益 |
| #5 | 20260604 | `widget_compact_padding_vertical = 1dp` 偏小 | 过度吹毛求疵。Widget 非触控目标，原值不算 bug。1dp→2dp 已调整，但在 report 中标记为过度审查。确认日期：2026-06-05 |
| #8 | 20260604 | `TimelineBuilder.build()` 双重遍历 | 误报。第一次遍历是缓存签名检测命中后 return，第二次是 miss 后构建，标准缓存模式。确认日期：2026-06-05 |
| #3 | 20260606 | `MIGRATION_3_4` DROP TABLE 丢失 v3 跳过数据 | v3 是中间版本，未发布过，不存在用户数据丢失问题。确认日期：2026-06-06 |
| #2 | 20260606 | POJO `enabled` 用 `boolean` 而非 `int` | 误报。Room 对 POJO 同样自动做 `int` → `boolean` 转换。`enabled` 是布尔语义用 `boolean`，`scheduleType`/`scheduleSubType` 是多值枚举（0/1/2/3…）用 `int`，二者不存在不一致。确认日期：2026-06-06 |

---

## 跨报告重复

20260530 S1（建议 FTS）和 20260602 #27（LIKE 全表扫描）为同一底层问题的两个角度，视为同一条决策。

---

## 建议重新评估

无。全部 20 项跳过/误报/确认关闭决策经审核确认合理，无需要重新评估的项。

---

## 汇总

| 级别 | 数量 |
|------|:--:|
| important | 5 |
| suggestion | 9 |
| nit | 6 |
| **合计** | **20** |

全部 20 项跳过/误报/确认关闭决策经审核确认合理。

---

> 生成日期：2026-06-02（最新更新：2026-06-09 追加 20260609 #2）  
> 来源：code-review-20260530.md / code-review-20260531.md / code-review-20260602.md / code-review-20260603.md / code-review-20260603-v2.md / code-review-20260604.md / code-review-20260606.md / code-review-20260608.md / code-review-20260609.md
