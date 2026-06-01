# 四象限任务管理设计

> 设计日期 2026-05-27

## 概述

在主界面增加四象限全任务浏览页（左划进入），支持按象限查看/编辑任务，以及单象限列表中的筛选、多选批量删除。删除任务后产生的未使用标签可经由现有 `UnusedTagFragment` 清理。

## 导航结构

```
MainFragment (ViewPager2)
  ├── Page 0: 现有主界面
  │     ├── 时间线 + 引擎任务列表（双栏）
  │     └── 底部时段栏
  └── Page 1: QuadrantOverviewFragment（childFragment，不可滚动）
        ├── 2×2 象限网格
        ├── 每格按引擎顺序展示任务（溢出自然截断）
        ├── 点击任务 → ReminderDetailActivity（MODE_VIEW）
        └── 点击象限标题 ✏️ → QuadrantTaskListFragment

QuadrantTaskListFragment（nav_graph 新目的地，可滚动）
  ├── Toolbar: ← 象限名  [时长▼] [标签▼]
  ├── 单象限全任务列表，引擎排序，无比例截取
  ├── 单击任务 → ReminderDetailActivity（MODE_VIEW）
  └── 长按 → 多选模式 → 永久删除
```

## 架构

```
MainActivity
  └── MainFragment
        └── ViewPager2
              ├── Page 0: 现有 fragment_main.xml 布局（main_content + 底部时段栏）
              └── Page 1: QuadrantOverviewFragment
                    └── 观察 MainViewModel.mQuadrantResults

MainViewModel
  └── mDisplayEngine.computeByQuadrant(quadrantMask, ...)
        → EngineResult[4]

QuadrantTaskListFragment
  ├── 通过 nav argument 传入 quadrant 索引
  ├── 自有 ViewModel，调用 DisplayEngine.computeByQuadrant()
  ├── 筛选：时长分段 + 标签多选，取交集
  └── 长按多选 → TaskDao.delete() 永久删除

ReminderDetailActivity（现有，新增模式参数）
  ├── MODE_EXECUTE（默认）→ 现有交互：执行 / 完成 / 安排
  └── MODE_VIEW（四象限进入）→ 编辑 + 删除，无执行按钮
```

## 四象限概览布局（Page 1）

### 页面结构

- 整个页面**不滚动**，2×2 网格填满 ViewPager2 可用高度
- 每个象限格：标题栏（象限原色背景 + 白色名称 + ✏️ 图标）+ 任务列表区（象限浅色背景）
- 任务按引擎优先级排序，不做 4:2:2:1 截取，空间用尽自然截断
- 标题名称单行，不换行、不加粗
- 每个任务显示标题 + 专注时长（琐碎不显示），不显示标签名

### 交互

| 目标 | 行为 |
|------|------|
| 点击任务 | 跳转 `ReminderDetailActivity`，模式 `MODE_VIEW` |
| 点击象限标题 ✏️ | 导航到 `QuadrantTaskListFragment`，传入 quadrant 索引 |

## 单象限任务列表（QuadrantTaskListFragment）

### Toolbar

- 左侧：返回箭头 + 象限名称
- 右侧：时长分段下拉 + 标签多选下拉（PopupWindow + CheckBox）
- Toolbar 底色：象限对应原色，退出时恢复透明
- 进入多选模式后切换为：✕ 取消 | "已选 N 项" | 🗑 删除

### 筛选

时长（多选，挡位名）：

| 选项 | 条件 |
|------|------|
| 琐碎 | `focusMinutes == 0` |
| 30分钟 | `focusMinutes == 30` |
| 60分钟 | `focusMinutes == 60` |
| 90分钟 | `focusMinutes == 90` |
| 120分钟 | `focusMinutes == 120` |

标签（多选）：只列出当前象限任务中实际使用的标签。OR 逻辑。

时长与标签**取交集**。默认均不筛选。

### 列表项

`#标签名`（tag.color）+ 标题 + 专注时长。GridLayoutManager + 固定列宽跨行对齐。

### 交互

| 目标 | 行为 |
|------|------|
| 单击任务 | 跳转 `ReminderDetailActivity`，模式 `MODE_VIEW` |
| 长按任务 | 进入多选模式 |

### 多选模式

- 经典 ActionBar 多选交互
- 长按首个任务后进入，每项左侧出现 CheckBox
- Toolbar 切换：✕ 取消 | "已选 N 项" | 🗑 删除
- **不提供全选按钮**
- 点击 🗑 → 确认弹窗 → `TaskDao.delete()` 永久删除
- 删除完成后退出多选模式，列表刷新
- 点击 ✕ 或返回键退出多选模式

## 任务详情页模式（ReminderDetailActivity）

### MODE_EXECUTE（默认，主页进入）

现有交互不变。

### MODE_VIEW（四象限进入）

底部按钮：编辑（跳转 TaskInputActivity 编辑模式）+ 删除（确认 → 永久删除 → finish）+ 关闭。无论任务是否仅标题，始终显示按钮行。不显示执行/完成/安排按钮。

### 实现方式

- 通过 Intent extra 传入 `EXTRA_MODE`（`"execute"` / `"view"`）
- `ReminderDetailViewModel` 新增 `deleteTask()` 方法

## 数据层

### DisplayEngine

新增方法：

```java
/**
 * @param quadrantMask 长度 4 的 int 数组，1=需要该象限，0=不需要，下标即象限号
 * @return 长度 4 的 EngineResult 数组，仅 mask=1 的位置有数据
 */
public EngineResult[] computeByQuadrant(int[] quadrantMask,
    List<TaskEntity> tasks, Map<Long, TagEntity> tagMap,
    int remainingMinutes, boolean reverseQuadrant, Set<Long> priorityTagIds)
```

内部逻辑：
1. 按 `quadrant` 分组
2. mask=1 的象限，各组独立排序（优先标签 → 时段匹配度 → 专注时长），复用 `buildSortedItems()`
3. **不经过 QuadrantRatioFilter**，返回各组全量排序结果

调用示例：
- 四象限概览：`quadrantMask = {1,1,1,1}` → 4 组结果
- 单象限列表（象限 0）：`quadrantMask = {1,0,0,0}` → 只算第 0 象限

### DAO

无需新增。`TaskDao.getAllActiveTasks()` 返回所有未归档任务；`TaskDao.delete()` 永久删除。

### ViewModel

| ViewModel | 职责 |
|-----------|------|
| `MainViewModel` | 新增 `mQuadrantResults` LiveData（`EngineResult[4]`），供 Page 1 观察；同时保留现有 `mDisplayResult` |
| `QuadrantTaskListViewModel`（新建） | 管理筛选状态（时长段、标签集合），调用 `DisplayEngine.computeByQuadrant()`，筛选在 ViewModel 层做交集过滤 |

## 级联删除

`TaskDao.delete()` 为物理删除。Room 外键定义：
- `task_executions` → `ON DELETE CASCADE`
- `task_schedules` → `ON DELETE CASCADE`
- `task_checklist_items` → `ON DELETE CASCADE`
- `task_app_actions` → `ON DELETE CASCADE`
- `task_schedule_postpones` → `ON DELETE CASCADE`
- `tasks.tag_id` → `ON DELETE SET NULL`

删除任务后，该任务关联的标签可能变为"未使用"，用户可在 `UnusedTagFragment` 中清理。

## 新的导航图目的地

`nav_graph.xml` 新增：

```xml
<fragment
    android:id="@+id/quadrantTaskListFragment"
    android:name="com.nearby.justnow.ui.quadrant.QuadrantTaskListFragment"
    android:label="@{quadrantName}">
    <argument
        android:name="quadrant"
        app:argType="integer" />
</fragment>
```

## 边界情况

| 场景 | 处理 |
|------|------|
| 某象限无任务 | 该象限格显示空状态图标 + "暂无任务"提示 |
| 筛选结果为空 | 列表显示空状态提示，筛选项保持不变 |
| 删除全部任务后 | 列表刷新为空，留在当前页面 |
| 引擎计算失败 | 降级使用 fallback（按 createdAt 倒序） |
| 详情页删除后返回 | 列表/概览自动刷新（LiveData 观察） |
| Page 1 按返回键 | 切回 Page 0（OnBackPressedCallback） |
| ViewPager2 滑动与主页 RecyclerView 冲突 | Page 0 的 RecyclerView 已禁用垂直滚动，不冲突 |
