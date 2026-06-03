# 审查忽略项汇总

基于 `docs/` 下审查报告中有意跳过/不修/误报的问题。

---

## 按级别分组

### important

| 编号 | 来源 | 问题 | 理由 |
|------|------|------|------|
| #2 | 20260603-v2 | 四象限概览仍按原始象限分组，未应用降级后象限 | 设计如此。用户确认“四象限任务管理模块的目的在管理”，因此四象限概览/单象限管理页按原始象限分组，不应用降级后象限。确认日期：2026-06-03 |
| #13 | 20260602 | `BaseRepository.assertNotMainThread()` 仅 Log.w 不抛异常 | Room 自身在主线程执行同步查询已 crash，加一层 throw 无实质收益 |
| #16 | 20260602 | `ViewModelFactory` if-else 链做类型映射 | 项目无新增 ViewModel 计划，分支数不会增长。改为 Map 注册后每个 VM 仍需一行注册代码，代码量不减少，仅从 if-else 换成 Map.put，未降低维护成本 |

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

### nit

| 编号 | 来源 | 问题 | 理由 |
|------|------|------|------|
| #30 | 20260602 | AppDatabase DCL 缺乏局部变量优化 | 微优化，约 25% volatile 读差异，实际影响可忽略 |
| #31 | 20260602 | WidgetPermissionGateActivity exported="true" | 无 intent-filter 意味着隐式 Intent 无法匹配，实际攻击面极小。改为 `false` 反而可能在某些 Launcher 上中断 Widget 添加流程，代价高于收益 |

---

## 跨报告重复

20260530 S1（建议 FTS）和 20260602 #27（LIKE 全表扫描）为同一底层问题的两个角度，视为同一条决策。

---

## 建议重新评估

无。全部 12 项跳过/误报决策经审核确认合理，无需要重新评估的项。

---

## 汇总

| 级别 | 数量 |
|------|:--:|
| 用户决策 | 2 |
| important | 3 |
| suggestion | 8 |
| nit | 2 |
| **合计** | **14** |

其中 1 项（#16）标注为建议重新评估，12 项确认合理。

---

> 生成日期：2026-06-02（最新更新：2026-06-03 追加 #10、#11、20260603-v2 #2）  
> 来源：code-review-20260530.md / code-review-20260531.md / code-review-20260602.md / code-review-20260603.md / code-review-20260603-v2.md
