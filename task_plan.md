# 任务规划

## 已完成：桌面 Widget 实现（2026-05-26）

> 设计文档：[docs/superpowers/specs/2026-05-26-widget-design.md](docs/superpowers/specs/2026-05-26-widget-design.md)
> 模块文档：[modules/widget.md](modules/widget.md)
> 详情页：[modules/reminder-detail.md](modules/reminder-detail.md)

按 5 个功能阶段完成：ReminderDetailActivity 拆分、Widget 布局资源、Provider 核心逻辑、刷新机制、验证与清理。全部编译通过 + 218 单元测试 0 失败。关键决策：ReminderDetailActivity 独立、系统原生 GridLayout + addView 行模板、AlarmManager 整分钟刷新。

后续修复已落地：Widget 任务点击走主界面统一 `resolveAndHandleTaskClick()`、返回栈复用、精确闹钟权限保护、高度 dp 计算修正、空状态占位修正、APP 侧主动刷新通过 `DataChangeDispatcher` 解耦、标签点击 per-widget 筛选、添加时配置中转页打开主界面引导精确闹钟权限且未授权取消添加。待真机/桌面 Launcher 复验标签筛选和多 Widget 独立筛选。

## 已完成：安排页 UI 细节调整（2026-05-24）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 类型切换控件、底部保存按钮

切换类型不抖动（minHeight=42dp）、每周 chip 字号统一到 Body（18sp）、保存按钮圆角填充。

## 已完成：安排页面选择器视觉统一（2026-05-24）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 选择器视觉统一

三种类型选择器对象 chip 样式按钮：每周 TextView + state_selected、每月月历 Dialog（GridLayout 7x5）、DAILY 类型不留空白。

## 已完成：安排任务模块重设计（2026-05-23）

> 详见：[modules/task-execution.md](modules/task-execution.md)
> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

关键决策：一个任务一条有效安排（UNIQUE）、字段统一表达、槽位视图共用、闹钟调度三步骤、双权限门禁。D024 统一匹配/校验收口。

## 已完成：安排模块后续排查修复（2026-05-24）

> 详见：[modules/task-execution.md](modules/task-execution.md) — 排查修复

4 Pack 修复：TaskScheduleMatcher 权威实现 + 双权限前置 + UNIQUE 保留（insert 前清理）+ 双层缓存。`TaskScheduleMatcherTest` 34 用例。全量 218 用例 0 失败。

## 已完成：任务详情页系统（2026-05-20）

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md)
> 详见：[modules/task-edit.md](modules/task-edit.md)
> 详见：[modules/task-input.md](modules/task-input.md)

任务详情页 Fragment + Markdown 渲染 + todo 清单 / APP 跳转附加模块 + 统一入口 + 完成前确认机制。录入/编辑页面拆分为独立 Fragment。

## 已完成：任务详情页 UI 修复 + 按钮均匀分布（2026-05-22）

> 详见：[modules/reminder-detail.md](modules/reminder-detail.md) — UI 修复、按钮均匀分布

Toolbar 复用 Activity、APP 项视觉对齐、整行点击跳转。底部按钮 Space 等权重均匀分布。APP 项跳转后 click handler 顺序修正防重复点击。

## 已完成：D020 提醒延迟（2026-05-19/20）

> 详见：[modules/reminder-delay.md](modules/reminder-delay.md)

全链路 AlarmManager + 常驻通知 + +15/+30 动态延迟。权限引导：精确闹钟崩溃修复 + PermissionHelper 统一工具。53 个新增测试用例。

状态/遗留：安排页时间未约束落入时段内、自动插入安排任务到时间线、通知点击详情页交互细节。

## 已完成：任务录入四象限保存崩溃修复（2026-05-18）

> 详见：[modules/task-input.md](modules/task-input.md)

无标签任务 `tag_id` 写 null 而非 0（D022）。loadTaskForEdit 清空旧标签草稿。

## 已完成：主界面非时段直接开始保护（2026-05-18）

> 详见：[modules/task-execution.md](modules/task-execution.md)

非时段右侧栏任务可查看详情但不能开始，开始按钮禁用并显示原因。专注时长任务"安排"切为主按钮。

## 已完成：时间段组编辑对话框布局优化（2026-05-17）

> 详见：[modules/time-period.md](modules/time-period.md)

日期完整年份显示、时间行固定时间区、统一浅蓝编辑块+黑色文字。底层仍保存 MM-dd。

## 已完成：节假日数据月度下载频率控制（2026-05-16）

> 详见：[modules/holiday-data.md](modules/holiday-data.md)

fetch() 返回 Entity + throws IOException；lastSyncMonth 月度标记；天数比较决定是否写入；移除 on-demand fetch。

---

## 当前聚焦：四象限任务管理（2026-05-27）

> 设计文档：[docs/superpowers/specs/2026-05-27-quadrant-task-manage-design.md](docs/superpowers/specs/2026-05-27-quadrant-task-manage-design.md)
> 模块文档：[modules/quadrant-task-manage.md](modules/quadrant-task-manage.md)

**定位**：主界面四象限全任务浏览 + 单象限筛选删除。

**关键决策**：ViewPager2 嵌入 MainFragment（Page 0 主界面 + Page 1 四象限概览）；DisplayEngine.computeByQuadrant 按需计算跳过比例截取；PopupWindow 下拉多选筛选；TaskDao.delete 物理删除。ReminderDetailActivity MODE_VIEW 区分执行/查看模式。

**状态**：5 阶段完成；2026-05-28 收尾修复单象限页面 Toolbar/系统状态栏跟随象限色，返回主界面恢复默认主题色。

---

## 当前聚焦：四象限降级恢复（2026-05-29）

> 设计文档：[docs/superpowers/specs/2026-05-29-quadrant-degrade-design.md](docs/superpowers/specs/2026-05-29-quadrant-degrade-design.md)

**定位**：高频周期任务完成后自动降级一级象限，按次日/下周/下月恢复，避免反复占据推荐引擎顶部。

**关键决策**：`tasks` 表加 `degrade_period`（0=不降级/1=次日/2=下周/3=下月）；新表 `task_quadrant_degrade`（`task_id`+`original_quadrant`+`recover_ms`）；`QuadrantFragment` 顶部 chip 行选恢复周期，默认次日；完成时写降级表，recompute 时检测到期自清理；象限变更/删除/归档主动清降级记录。

**状态**：已完成，编译 + 测试通过

---

## 代码审查：重复业务逻辑排查（2026-05-31）

> 审查报告：[docs/code-review-20260531.md](docs/code-review-20260531.md)

**审查重点**：排查因缺少统一回调设计导致的重复业务逻辑

**发现问题**：8 个（高优先级 3 个，中优先级 4 个，低优先级 1 个）
- 任务完成流程重复：三处独立实现相似逻辑
- 短完成流程重复：两处实现相同的 < 15min 完成逻辑
- 闹钟取消逻辑重复：七处重复调用
- 归档任务流程重复：两处实现相同逻辑
- Holiday 数据源重复：三个数据源实现相同 HTTP 请求模式
- Widget 和主界面重复计算引擎逻辑
- 清单状态确认检查重复
- 时段状态文本构建重复

**建议方案**：引入服务层（Service Layer）统一封装跨 Repository 的业务流程，使用回调接口处理差异化需求

**状态**：待重构

---

## 待实现：全局横竖屏锁定（2026-05-27）

> 设计文档：[docs/superpowers/specs/2026-05-27-screen-orientation-lock-design.md](docs/superpowers/specs/2026-05-27-screen-orientation-lock-design.md)

- [x] `JustNowApplication.onCreate` 中 `registerActivityLifecycleCallbacks`：手机->PORTRAIT，平板->LANDSCAPE
- [x] `compileDebugJavaWithJavac` 通过
- **Status:** complete

---

## 品牌 -> [详细文档](modules/brand.md)

企业：邻近科技 / Nearby Tech。产品：恰恰有事 / Just Now。标语：刚好，手边有事 / Right here, right now。

## 项目概述

- **核心理念**：记录任务缓急，但不催促安排任务；专注记录任务，完成当下事，选择想做的事。
- **与传统 Todo 的区别**：不堆积任务列表、不设截止日期催促、任务属性在执行时才揭晓、基于四象限+时间段+专注时长的智能展示

## 核心功能模块

### 测试策略 -> [详细文档](modules/testing.md)

编译验证优先；低成本业务规则使用 JVM 单元测试或已有 Robolectric 基础设施。UI 验证不作为强制收尾要求。

### M1：任务录入（已打磨） -> [进展](modules/search-engine.md)

关键字检索 + 分词匹配 + 标签推荐 + 专注时长设定。详见 [检索引擎模块](modules/search-engine.md)、[任务录入模块](modules/task-input.md)。

### M2：四象限矩阵（已打磨）

紧急/重要四个象限，任务编辑完成后必须选择象限，按优先级排序。

### M3：时间段系统（已实现） -> [进展](modules/time-period.md)

6 种时段组 + 默认时段填充 + 编辑约束（步进按钮 + PopupWindow 浮层滚轮 + 磁盘分区联动）。

### M4：智能展示引擎（已打磨） -> [进展](modules/smart-display.md)

四象限 4:2:2:1 比例 + 标签优先展示。Widget 与 APP 复用同一引擎。标签筛选/优先 -> [进展](modules/tag.md)。

### M5：任务执行（已实现） -> [进展](modules/task-execution.md) | [详情页](modules/reminder-detail.md) | [延迟](modules/reminder-delay.md)

右侧查看/安排/开始；左侧处理完成动作。非时段禁止直接开始。安排任务模块重设计完成（UNIQUE + 统一字段 + 槽位视图 + 闹钟调度）。

### M6：数据统计（待开始） -> [进展](modules/stats.md)

按四象限统计每日完成，月度趋势图表。预留调侃评价和增长鼓励。

### M7：桌面 Widget（已实现） -> [进展](modules/widget.md)

复用 M4 智能展示引擎。系统原生 GridLayout + RemoteViews.addView 行模板 + AlarmManager 整分钟刷新；标签点击支持 per-widget 筛选。

### M8：节假日数据获取（已实现） -> [进展](modules/holiday-data.md)

按年缓存，WorkManager 保障获取不被退出打断。月度频率控制 + 工作日判断 + 无数据兜底。

### M9：四象限任务管理（已实现） -> [进展](modules/quadrant-task-manage.md)

ViewPager2 全任务浏览 + 单象限列表筛选删除 + ReminderDetailActivity MODE_VIEW。

### M10：四象限降级恢复（已实现） -> [进展](modules/quadrant-degrade.md)

高频周期任务完成后自动降一级象限，次日/下周/下月恢复。tasks 表加 `degrade_period`，新表 `task_quadrant_degrade` 存临时降级状态。

---

## 技术选型

- **Java** 为主 + View-based UI（XML 布局 + ViewBinding + Material Design 3）
- **MVVM** + **Repository 模式** + Room 数据库
- Navigation Component / App Widget / WorkManager / OkHttp
- 图表库：MPAndroidChart（预留）

---

## 决策记录

- **D001**：保持 Java + View-based UI（XML + ViewBinding + Material 3）
- **D002**：专注时长上限 2 小时，30 分钟递增，5 档
- **D003**：选择 Room 持久化
- **D004**：常规/工作日默认 5 段时段
- **D005**：晚上时段四象限展示优先级反转
- **D006**：节假日数据——中国 holiday-cn，境外 ICS；按年缓存，月度频率控制
- **D017**：春节与新年互斥，仅中国大陆显示春节
- **D018**：设备国家/地区由主线统一读取（RegionSettings）；证券从业仅 CN
- **D007**："琐碎"任务首次完成弹窗确认
- **D019**：任务执行入口统一为详情弹窗；15 分钟容差只作缓冲区
- **D020**：任务推迟仅限已安排专注时长任务；+15/+30 动态延迟
- **D008**：任务搜索仅匹配内容，标签填写时关键字推荐
- **D011**：标签 UI 超链接交互——蓝色/深蓝/下划线切换
- **D009**：设定了时间的专注时长任务需系统通知提醒
- **D010**：Widget 与 APP 复用 M4 智能展示引擎
- **D012**：任务录入三屏流程——搜索/编辑/象限
- **D013**：全文检索 jieba 分词 + UnicodeScript 多语言检测
- **D015**：标签优先展示通用化条件->标签集合映射
- **D014**：项目国际化默认英文，支持 zh-CN/zh-TW/zh-HK
- **D016**：主界面非紧凑底部时段栏（72dp）+ 按钮替代 FAB
- **D021**：项目级对话框/时间编辑框风格只抽取字号颜色，不抽取业务文案
- **D022**：任务无标签数据库统一表示为 NULL
- **D023**：Robolectric 新增测试固定 `@Config(sdk = 35)`
- **D024**：安排模块匹配/触发/开始校验统一收口（TaskScheduleMatcher + TaskStartGuard）
- **D025**：四象限降级恢复——`tasks.degrade_period` 持久周期；`task_quadrant_degrade` 临时降级状态（自清理）；DisplayEngine recompute 时统一处理到期待删
## 风险与阻碍

- 节假日数据源的加载与解析
- 晚上时段四象限反转逻辑
- `mergeDebugResources` 增量资源缓存可能触发 AGP NPE
- 带点号样式必须显式声明 parent
- Robolectric 新增测试需固定 `@Config(sdk = 35)`
