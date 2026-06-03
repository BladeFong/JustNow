# 四象限任务管理模块

> 设计文档：[2026-05-27-quadrant-task-manage-design.md](../docs/superpowers/specs/2026-05-27-quadrant-task-manage-design.md)

# 阶段规划、决策记录 （拆分自 task_plan.md）

## 定位和功能描述

在主界面增加四象限全任务浏览页（ViewPager2 左划进入），支持按象限查看/编辑任务，以及单象限列表中的筛选（时长 + 标签）、多选批量永久删除。

## 整体规划和决策

## 阶段划分

### Phase 1: DisplayEngine 改造
- [x] 新增 `computeByQuadrant(int[] quadrantMask, ...)` 方法
  - 参数：`quadrantMask` 长度 4，1=需要该象限，0=不需要，下标即象限号
  - 提取 `buildSortedItems()` 共用排序逻辑，各组独立排序，不经过 QuadrantRatioFilter
  - 降级 `fallbackQuadrantList()` 按 createdAt 倒序分组
- [x] `MainViewModel` 新增 `mQuadrantResults` LiveData（`MediatorLiveData<EngineResult[]>`）
- [x] **2026-06-03**：移除 `computeByQuadrant()` 中未使用的 `reverseQuadrant` 和 `degradeMap` 参数

### Phase 2: MainFragment ViewPager2 改造 + 四象限概览
- [x] `fragment_main.xml`：ViewPager2 根布局，提取 `fragment_main_page0.xml` 为 Page 0
- [x] 新建 `QuadrantOverviewFragment`（childFragment，Page 1）
  - 2×2 网格（嵌套 LinearLayout + weight 等分），不滚动
  - 标题栏：象限原色背景 + 白色文字，单行 `TextAppearance.JustNow.Body`
  - 列表区：象限浅色背景（`quadrant_bg_0/1/2/3`），padding 8dp
  - 观察 `MainViewModel.mQuadrantResults`
  - 点击任务 → `ReminderDetailActivity` MODE_VIEW
  - 点击 ✏️ → `NavHostFragment.findNavController` 导航到 `QuadrantTaskListFragment`
- [x] 新建 `MainPage0Fragment` + `fragment_quadrant_overview.xml`
- [x] `OnBackPressedCallback`：Page 1 按返回键切回 Page 0

### Phase 3: QuadrantTaskListFragment（单象限任务列表）
- [x] 新建 `QuadrantTaskListFragment` + `QuadrantTaskListViewModel` + `QuadrantTaskListAdapter`
  - nav argument: quadrant 索引
  - Toolbar / 状态栏：由 `MainActivity` 按导航目的地统一管理，单象限页使用所选象限色，返回后恢复默认主题色
  - 筛选：时长多选 + 标签多选（PopupWindow + CheckBox），取交集
- [x] 时长筛选：琐碎 / 30分钟 / 60分钟 / 90分钟 / 120分钟（多选，OR）
- [x] 标签筛选：`getTagsInCurrentList()` 从 `mAllItems` 内存去重，只列当前有的
- [x] 列表项：`#标签名`（tag.color，80dp）+ 标题（weight=1）+ 专注时长（64dp），GridLayoutManager
- [x] 长按多选模式（手动 Toolbar 切换），不提供全选
- [x] `nav_graph.xml` 新增 `quadrantTaskListFragment` 目的地 + quadrant argument

### Phase 4: ReminderDetailActivity MODE_VIEW
- [x] Intent extra `EXTRA_MODE`（`"execute"` / `"view"`）
- [x] MODE_VIEW 底部按钮：编辑 + 删除 + 关闭，无论任务是否仅标题始终显示
- [x] ViewModel 新增 `deleteTask()` 方法（取消闹钟 + 删任务）

### Phase 5: 编译验证
- [x] 各 Phase 实现代理均独立编译通过

## 遗留问题
- [x] 单象限页面系统状态栏不跟随 Toolbar 象限色。2026-05-28 已改为 `MainActivity` 目的地级 App chrome 管理；Android 15+ 通过透明状态栏下的 `AppBarLayout` 背景透出，Android 14 及以下用 legacy `setStatusBarColor()`。

## 涉及文件

### 四象限概览
| `QuadrantOverviewFragment.java` | ViewPager2 Page 1 childFragment |
| `fragment_quadrant_overview.xml` | 2×2 网格布局 |
| `item_quadrant_overview_task.xml` | 四象限概览任务行布局 |
| `MainPage0Fragment.java` | Page 0 轻量 childFragment |
| `fragment_main_page0.xml` | Page 0 布局 |
| `fragment_main.xml` | ViewPager2 根布局 |
| `MainFragment.java` | ViewPager2 装配 + OnBackPressedCallback |

### 单象限列表
| `QuadrantTaskListFragment.java` | 筛选 + 多选删除 |
| `QuadrantTaskListViewModel.java` | 筛选状态 + `getTagsInCurrentList()` |
| `QuadrantTaskListAdapter.java` | RecyclerView Adapter |
| `fragment_quadrant_task_list.xml` | RecyclerView + 空状态 |
| `item_quadrant_task.xml` | 列表项布局 |
| `menu_quadrant_task_list.xml` | Toolbar 菜单 |
| `nav_graph.xml` | `quadrantTaskListFragment` 目的地 |
| `MainActivity.java` | 目的地级 Toolbar / AppBarLayout / 状态栏颜色管理 |

### 详情页
| `ReminderDetailActivity.java` | `EXTRA_MODE` 常量 + MODE_VIEW 分支 |
| `ReminderDetailViewModel.java` | `deleteTask()` 方法 |

### 数据层
| `DisplayEngine.java` | `buildSortedItems()` + `computeByQuadrant()` |
| `MainViewModel.java` | `mQuadrantResults` LiveData |

# 研究发现、技术决策 （拆分自 findings.md）

## 现有代码基础
- **DisplayEngine**：无状态，排序权重：优先标签(-10000) → 象限(×1000，夜间反转) → 时段匹配(+0/+500/+900) → 专注时长(-min/10)。QuadrantRatioFilter 做 4:2:2:1 比例截取。
- **MainFragment 布局**：水平 LinearLayout 双栏，无 ViewPager2，RecyclerView 垂直滚动被显式禁用。
- **MainActivity**：nav_graph 仅 mainFragment 一个目的地。
- **TaskDao**：`getAllActiveTasks()` 返回所有未归档任务，`delete()` 永久删除（级联 CASCADE）。
- **ReminderDetailActivity**：独立 Activity，Widget 和 APP 内部统一入口，当前仅支持执行模式。
- **UnusedTagFragment**：已存在于 TagManageActivity 导航图，展示并清理未被任何任务引用的标签。

## 设计决策
- ViewPager2 嵌入 MainFragment（非替换 FragmentContainerView），Page 0 保留现有布局，Page 1 为四象限概览
- `computeByQuadrant(quadrantMask)` 按需计算指定象限，跳过比例截取
- 四象限概览不滚动（2×2 均分），单象限列表可滚动
- 删除走 `TaskDao.delete()` 物理删除，级联 CASCADE
- `ReminderDetailActivity` 加 MODE 参数区分执行/查看模式
- 筛选：时长 + 标签均多选（PopupWindow），取交集；标签只列当前列表有的
- 四象限概览任务项不显示标签名，只在单象限列表显示
- 底部时段栏在 Page 0 内部，四象限页不需要
- 全局横竖屏锁定：`JustNowApplication` 中 `registerActivityLifecycleCallbacks`（独立需求）

## 2026-05-28 Toolbar/状态栏颜色收尾

### 死参数清理（2026-06-03）

### Bug 5 修复：删除后列表不刷新 + 多选状态未退出（2026-06-04）

**根因**：`AppDatabase` 写线程池为 2 线程，异步 `delete()` 和 `loadData()` 可能并行执行，`loadData()` 读到旧缓存。`deleteSelectedTasks()` 中 `postValue` 在后台线程延迟生效，多选状态退出和 `onComplete` 回调时序不确定。

**技术决策**：

- **LiveData 观察选型**：ViewModel 层用 `observeForever` 而非 Fragment 层 `observe(getViewLifecycleOwner)`。理由：ViewModel 已持有 `mTaskRepo`，无需向 Fragment 暴露 Repository；`observeForever` 在 `onCleared` 中移除，生命周期与 ViewModel 一致，不受 Fragment 前后台切换影响。
- **双路刷新**：`onResume` 即时触发 `loadData()`（覆盖返回场景）+ Room LiveData 观察异步落盘后自动刷新（覆盖竞态窗口）。两路互补，不做互斥去重——重复 `loadData()` 仅多一次缓存读取，无副作用。
- **`deleteSync` 替代 `delete`**：`deleteSync` 在同一后台任务内完成删库 + 清缓存 + `notifyTaskDataChanged`，缩小"删库和 loadData 之间的窗口"；同时 `notifyTaskDataChanged` 触发 Room LiveData 失效，保证观察者收到最新数据。
- **`mSelectionMode.setValue` 替代 `postValue`**：`deleteSelectedTasks` 中 `runOnUiThread(() -> setValue(false))` 确保多选模式退出和 `onComplete` 回调在 `loadData` 完成后的同一 UI 帧内执行，避免 Toolbar 标题更新发生在模式切换之前。

**涉及文件**：`QuadrantTaskListViewModel`（LiveData 观察 + deleteSync + setValue）、`QuadrantTaskListFragment`（onResume）、`ReminderDetailViewModel`（deleteSync）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- `computeByQuadrant()` 的 `reverseQuadrant` 和 `degradeMap` 参数在四象限管理专用方法中无实际作用，已移除。四象限管理页面以查看/编辑为主，不需要象限反转和降级规则。

- 颜色职责从 `QuadrantTaskListFragment` 收口到 `MainActivity`：
  - `NavController.addOnDestinationChangedListener()` 监听当前目的地；
  - 进入 `quadrantTaskListFragment` 时读取 `quadrant` argument；
  - 同步设置 `AppBarLayout`、`Toolbar` 为象限色；
  - 离开单象限页时恢复 `purple_500` / theme `android:statusBarColor`。
- Android 15+：目标应用状态栏透明，依靠 `activity_main.xml` 中 `AppBarLayout android:fitsSystemWindows="true"` 和动态背景色覆盖状态栏区域。
- Android 14 及以下：继续通过 legacy `Window.setStatusBarColor()` 设置非透明状态栏。
- 单象限 Fragment 不再直接设置或恢复状态栏 / Toolbar 背景，避免返回时生命周期时机导致颜色恢复异常。
- 顺手对齐模块实现：
  - `QuadrantTaskListFragment` 使用 `MenuProvider` 替代旧 Fragment 菜单 API；
  - 复用项目 `ViewModelFactory`；
  - `quadrant` 参数做边界保护；
  - 概览标题和 `#%s` 标签显示资源化，并补齐默认英文、简中、繁中台湾、繁中香港；
  - 四象限概览任务行从代码创建 View 改为 `item_quadrant_overview_task.xml`；
  - 静态数组、`ViewHolder` 字段命名按项目 Java 规范修正。

# 进度日志 （拆分自 progress.md）

- 2026-05-27：brainstorming 设计确认 → 设计文档 → plan-then-delegate 5 阶段串行分发（F1 横竖屏 → F2 DisplayEngine → F3 ViewPager2 + 四象限概览 → F4 单象限列表 → F5 详情页 MODE_VIEW）
- 2026-05-27：多轮 SendMessage 续接 F3/F4/F5 修复 UI 细节（象限色块、字号、专注时长显示、Toolbar 颜色恢复、筛选交互统一、列表列对齐、标签显示、返回键拦截）
- 2026-05-27：状态栏颜色遗留确认，文档结构重构（spec 保留原样，module doc 拆分为全量）
- 2026-05-28：状态栏/Toolbar 颜色收尾完成；`MainActivity` 目的地级 App chrome 管理落地；模块对齐问题清理；`compileDebugJavaWithJavac` 最终 BUILD SUCCESSFUL。过程中遇到 AGP `mergeDebugResources` 增量缓存 NPE，按项目规则 `clean` 后继续；另遇到一次 Gradle `FileHasher` I/O 启动错误，重试后正常。
- 2026-06-03：`computeByQuadrant()` 移除 `reverseQuadrant` 和 `degradeMap` 死参数，编译通过。
- 2026-06-04：Bug 5 已修复（单象限列表删除后不刷新 + 多选状态未退出）。修复内容：`QuadrantTaskListViewModel` 增加 Room LiveData 观察（异步删除落盘后自动刷新）+ `deleteSelectedTasks()` 改用 `deleteSync` 保证删除先于 loadData 完成 + 多选退出改用 `runOnUiThread` + `setValue`；`QuadrantTaskListFragment.onResume()` 主动 `loadData()` 覆盖返回刷新；`ReminderDetailViewModel.deleteTask()` 改用 `deleteSync` 缩小缓存不一致窗口。编译通过。
