# 任务录入模块

> 对应 task_plan.md 任务录入 / 编辑页样式修复与录入/编辑拆分阶段

# 阶段规划、决策记录

## 定位和功能描述

TaskInputFragment 提供任务录入入口：关键字检索自动匹配已有任务可点选编辑，以及输入新任务内容进入编辑页。

与 TaskEditFragment 共用 `TaskInputViewModel`（Activity 级 scope）。

## 整体规划和决策

### 全项目审查修复（2026-05-30）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F2

- [x] `TaskInputFragment`/`TaskInputChecklistSheet` `require*()` 异步崩溃加 `isAdded()`/`getView()` null 守卫

### 录入/编辑页面拆分（task_plan.md）
- [x] 录入/编辑页面拆分：新建 `TaskEditFragment` + `fragment_task_edit.xml`；`TaskInputFragment` 精简为纯搜索页
- [x] ViewModel 移除 `mIsDetailScreenVisible`；nav_graph 新增 `action_taskInputFragment_to_taskEditFragment` + `taskEditFragment` 目标
- [x] 录入页只写标题/Markdown 或调用 `loadTaskForEdit`；不涉及标签、时长、模块等编辑态字段

### 已确认规则

**页面结构**
- 全屏 Fragment，无 Toolbar。
- 16dp 内边距竖向布局：多行输入区（160dp）-> 搜索提示行 -> 搜索结果 RecyclerView -> 下一步按钮。

**输入/检索**
- 用户输入触发 `TextTokenizer.tokenize()` 分词，`TaskRepository.searchTasksByTokens()` 多 token AND 匹配。
- 无匹配时 RecyclerView 隐藏；有匹配时显示，匹配关键词黄色高亮（`#FFF176`）。

**下一步按钮**
- 输入为空时禁用；非空时启用。
- 点击解析：第一行->标题（`mDraftTask.content`），后续行->Markdown 正文（`mDraftTask.detailMarkdown`）。
- 解析后导航到 `TaskEditFragment`。

**匹配项点击**
- 点击搜索结果 -> `loadTaskForEdit(taskId)` 后台加载已有任务数据到 ViewModel -> 导航到 `TaskEditFragment`。

### 文件结构

```
ui/taskinput/
├── TaskInputActivity.java         # 宿主 Activity（独立，自带 nav graph）
├── TaskInputFragment.java         # 录入页 Fragment
├── TaskEditFragment.java          # 编辑页 Fragment（共用 ViewModel）
├── TaskInputViewModel.java      # 共享 ViewModel（搜索 + 草稿 + 保存）
├── TaskInputChecklistSheet.java # todo 清单编辑器 BottomSheet
├── TaskInputAppActionSheet.java # APP 跳转编辑器 BottomSheet
└── SearchResultAdapter (inner)  # 检索结果 RecyclerView 适配器（内部类）

res/layout/
├── activity_task_input.xml       # TaskInputActivity 布局
├── fragment_task_input.xml      # 录入页布局（纯搜索屏）
├── fragment_task_edit.xml       # 编辑页布局
├── sheet_checklist_editor.xml   # 清单编辑器布局
├── sheet_app_action_editor.xml  # APP 跳转编辑器布局
├── item_search_result.xml       # 检索结果项布局
├── item_app_action.xml          # APP 跳转编辑项布局
└── item_app_search_dropdown.xml # APP 搜索下拉项布局（带图标）
```

# 研究发现、技术决策

> 详见：[findings.md](../findings.md) — 编辑页样式修复与录入/编辑拆分（2026-05-20）

### 任务录入三屏流程（D012）
- 首屏多行输入+全文检索（不区分检索/直接输入）
- 中屏标题/正文/标签/专注时长（添加=编辑同一界面）
- 末屏象限选择
- `TaskEntity` 新增 `detail`（正文）字段，`content` 为首行标题

### 全文检索方案（D013）
- 使用 jieba 分词 + UnicodeScript 多语言字符检测
- CJK->jieba，拉丁->空格分词
- 停用词过滤（全部被过滤时保留原始词）
- Application.onCreate 预热词典避免首次延迟
- 匹配逻辑：按比例命中（token 命中数 >= ceil(token总数/2)）

### 任务无标签语义（D022）
- 无标签状态在数据库中统一表示为 `NULL`，不使用 `0` 作为哨兵值
- `tasks.tag_id` 是指向 `tags.id` 的可空外键
- 保存链路和测试预置数据均应遵守这一语义

### 全项目审查修复（2026-05-30）

- `TaskInputFragment`/`TaskInputChecklistSheet` 在异步回调中调用 `requireContext()`/`requireView()` 可能崩溃 → 加 `isAdded()`/`getView()` null 守卫

### 颜色常量提取（2026-06-03）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #8

- `TaskInputViewModel` 新建标签颜色 `0xFF1A73E8` 提取为 `DEFAULT_TAG_COLOR` 常量

### Edge-to-edge 下 IME 遮挡修复（2026-06-04）

`TaskInputActivity` 启用 Edge-to-edge 后，`windowSoftInputMode="adjustResize"` 不再可靠。标签输入框聚焦时会被输入法遮挡。修复：在根布局 `WindowInsetsCompat` 监听中处理 `Type.ime()` bottom inset，把根布局 bottom padding 调整为 IME 高度；状态栏 top padding 仍由 `Type.statusBars()` 处理。

### 应用分身支持（2026-06-16）

> 设计文档：[../docs/superpowers/specs/2026-06-16-app-clone-support-design.md](../docs/superpowers/specs/2026-06-16-app-clone-support-design.md)
> 实现计划：[../docs/superpowers/specs/2026-06-16-app-clone-support-plan.md](../docs/superpowers/specs/2026-06-16-app-clone-support-plan.md)

**定位**：APP 跳转编辑器支持小米等设备的应用分身。查询阶段通过反射调用 `queryIntentActivitiesAsUser()` + `getIdentifier()` 遍历所有用户；存储阶段复用 `deepLink` 字段存储带 `S.launch_user_id` extra 的 intent URI。

**关键决策**：
- `AppLaunchCatalogCache.AppInfo` 增加 `userId` 字段
- 分身应用 label 后加"（分身）"
- `findByPackageNameAndUserId()` 同时匹配包名和 userId

**状态**：编译通过，待真机验证。

### 添加任务返回与 APP 跳转附加模块优化（2026-06-07）

> 设计文档：[../docs/superpowers/specs/2026-06-07-task-input-app-action-polish-design.md](../docs/superpowers/specs/2026-06-07-task-input-app-action-polish-design.md)

- `TaskInputActivity` 是独立多目的地向导页，但首屏也需要返回箭头，因此不使用 `NavigationUI.setupWithNavController()` 管理 Toolbar；改为 `setSupportActionBar()` 后通过 `ActionBar.setDisplayHomeAsUpEnabled(true)` 始终显示返回箭头，并用 `NavController.addOnDestinationChangedListener` + `ActionBar.setTitle()` 更新标题。
- APP 跳转附加模块拆分为主 Sheet 与居中添加对话框：主 Sheet 只维护已添加列表和确认保存；添加对话框负责 APP 搜索、描述输入、选择后启用“添加到列表”，添加后立即关闭并把新项插入列表顶部。
- `JustNowApplication` 持有 `AppLaunchCatalogCache`，缓存 `packageName` / `label` / `icon` 与加载状态；Sheet 打开时异步加载，加载期间禁用添加入口；`MainActivity.onResume()` 统一清理缓存，Sheet 关闭和 `TaskInputActivity` 销毁不清理。
- 主 Sheet 内已有 APP 项不等待完整缓存加载，按 `packageName` 单条查询 `PackageManager` 取得图标和名称，避免进入 Sheet 时先显示包名；本次通过添加对话框新增的项直接复用选择时的缓存 `AppInfo` 展示，不重复查询。主 Sheet 按钮使用普通 `MaterialButton` 样式，弹窗按钮保留 Dialog TextButton 风格。
- `TaskInputViewModel.saveTask()` 保存前按有效条目归一化模块状态：清单必须有非空内容项，APP 跳转必须有有效 `packageName`；空模块写 `detailModuleType = null`。编辑旧任务时，如果原模块被删空或切换，清理不再使用的旧子表，避免残留模块内容下次加载回来。

### Markdown 编辑器与排版渲染升级（2026-08-17）

> 设计文档：[../docs/superpowers/specs/2026-08-17-markdown-editor-and-rendering-design.md](../docs/superpowers/specs/2026-08-17-markdown-editor-and-rendering-design.md)
> 实施计划：[../docs/superpowers/plans/2026-08-17-markdown-editor-and-rendering.md](../docs/superpowers/plans/2026-08-17-markdown-editor-and-rendering.md)

**背景与根因**：
1. `TaskEditFragment` 中 `card_markdown` 在 `NestedScrollView` 内部无限撑高，长文本导致表单变形，且内部滚动事件被外层截获无法滚动。
2. 缺乏 Markdown 格式工具与排版反馈，粘贴外部 Markdown 文本无着色。
3. `ReminderDetailActivity` 虽有 `tvMarkdown` 字段但未接入 Markdown 解析引擎，仅作为裸文本显示。

**技术决策与实现**：
- **规范遵循**：严格遵循既有 **Material 2 (MaterialComponents)** 规范，不引入 M3。
- **常规态单屏精确填充防拉伸**：移除 `TaskEditFragment` 多余的外层 `NestedScrollView` 及其 `wrap_content` 嵌套，根布局采用纯正 `LinearLayout`；`card_markdown` 设为 `layout_height="0dp"` + `layout_weight="1"`，在单屏精确测量体系下自适应瓜分全部剩余垂直高度，内部 `TextView` 设为 `match_parent` 自动填充并按卡片边缘截断，展示“点击展开编辑 ↗”徽标，点击跳转全屏编辑器。
- **沉浸式全屏编辑器**：新建 `MarkdownEditorFragment` + `fragment_markdown_editor.xml`，复用 Activity 单层顶栏，拥有独立滚动视口、38dp 高度 M2 横向格式快捷工具条（H1/H2、粗体、斜体、列表、编号、待办、引用、代码、分割线）、底栏“👁 预览 / ✏️ 编辑”双模切换、以及底部常驻“完成编辑”主操作。
- **光标动作引擎**：新增 `MarkdownActionHandler.java`，纯 Java 处理光标选区包裹（`wrapSelection`）、行首标记前缀插入与 Toggle（`insertLinePrefix`）及分割线插入（`insertBlock`），配套完整单元测试。
- **Markwon 引擎与单换行插件引入**：接入 `io.noties.markwon:core:4.6.2`、`io.noties.markwon:editor:4.6.2` 与 `io.noties.markwon:ext-tasklist:4.6.2`。启用 `SoftBreakAddsNewLinePlugin` 支持单次换行（`\n`）即换行排版；编辑器挂载 `MarkwonEditor` 实时排版弱化标记符；`ReminderDetailActivity` 接入 Markwon 实现出版级富文本与待办列表渲染。

