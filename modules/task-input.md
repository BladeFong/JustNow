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
