# 任务详情页模块

> 对应 task_plan.md M5 任务执行

# 阶段规划、决策记录

## 定位和功能描述

任务详情页承载 Markdown 内容 + 可选附加模块，执行中有内容的任务点击后进入详情页处理完成。与主界面任务详情弹窗（AlertDialog，仅标题任务）不同，此页面承载更完整的任务信息与操作入口。

## 整体规划和决策

### 任务详情页系统阶段（task_plan.md）
- [x] 数据层：`tasks` 新增 `detail_markdown`、`detail_module_type` 字段；新增 `task_checklist_items`、`task_app_actions` 表及对应 Entity/DAO/Repository
- [x] 任务编辑页：Markdown 编辑框 + 附加模块图标按钮（单选，可取消）+ BottomSheet 编辑器
- [x] todo 清单编辑器：多行 EditText，Enter 自动补 check box，首行不可删
- [x] APP 跳转编辑器：搜索匹配已安装 APP + 填写提示文字
- [x] 编辑已有任务：加载模块数据（不读勾选/划掉状态），保存时内容有变化 + 有状态记录 -> 清除状态
- [x] 任务详情页 Fragment：Toolbar + Markdown 渲染区 + 模块 RecyclerView（如有）+ 底部按钮区
- [x] todo 清单模块（详情页）：RecyclerView，勾选/划掉互斥，状态即时写库
- [x] APP 跳转模块（详情页）：RecyclerView，点击整条跳转 + 当次标记完成（仅内存）
- [x] 统一任务点击入口：`resolveAndHandleTaskClick(taskId)` -> LiveData 事件
- [x] 完成前通用确认机制：`PreCompleteConfirmCallback` 接口
- [x] 详情页底部按钮（仅执行中）：琐碎 [取消] [不再需要] [完成] / 专注无安排 [取消] [完成本次] / 专注有安排 [取消] [停止安排] [完成本次]
- [x] Navigation 路由注册 + task_id SafeArgs 参数
- [x] compileDebugJavaWithJavac 通过

### UI 修复阶段（task_plan.md）
- [x] 删除 `fragment_reminder_detail.xml` 自带 `MaterialToolbar`，标题在 `renderTask` 中通过 `SupportActionBar.setTitle` 动态设置
- [x] 重写 `item_detail_app_action.xml`：图标 36dp + Title 字体 + `?attr/selectableItemBackground`
- [x] `AppActionAdapter`：文本 fallback 加入 app label 兜底；点击监听挂在 `holder.itemView`
- [x] 清理已无引用的 `s_jump` / `s_completed` 字符串（4 语言）
- [x] APP 项跳转后仍可点击缺陷修复：click handler 改为「准备 Intent -> markCompleted -> 同步置灰 -> notifyItemChanged -> startActivity」

### 按钮均匀分布阶段（task_plan.md）
- [x] `ll_bottom_buttons` 的 `gravity` 从 `center` 改为 `center_vertical`
- [x] 按钮前后及之间插入 4 个等权重 `Space`（`layout_weight="1"`），2/3 按钮自适应均匀分布

### 已确认规则

**入口判定**
- 仅标题任务（无 `detail_markdown` 且无 `detail_module_type`）：保持既有弹窗流程。
- 有内容任务（上述任一非空）：执行中点击 -> 详情页 -> 底部按钮完成。

**详情页结构**
- 全屏 Fragment，复用 Activity 顶部 Toolbar。
- 中间可滚动内容区：Markdown 渲染区（如有）+ 附加模块 RecyclerView（如有）。
- 底部固定按钮区：对话框风格。

**Markdown 渲染**：使用 Markwon 库。

**todo 清单模块**：RecyclerView，勾选/划掉互斥，状态即时写库。

**APP 跳转模块**：RecyclerView，整行点击跳转 + 当次会话标记完成（仅内存）。文本 fallback：`hint` -> app label -> packageName。

**完成前确认**：通用 `PreCompleteConfirmCallback` 接口。仅清单状态有变化时弹窗。

**统一入口**：`resolveAndHandleTaskClick(taskId)` 统一入口 -> LiveData 事件。

### 文件结构

```
ui/taskdetail/
├── TaskDetailFragment.java
└── TaskDetailViewModel.java

data/entity/
├── TaskChecklistItem.java
└── TaskAppAction.java

data/dao/
├── TaskChecklistItemDao.java
└── TaskAppActionDao.java

data/repository/
├── TaskChecklistRepository.java
└── TaskAppActionRepository.java
```

# 研究发现、技术决策

> 详见：[findings.md](../findings.md) — 任务详情页系统（2026-05-20）

### 设计约束
- 仅标题任务保持既有弹窗流程，本次不涉及。
- 有内容任务：执行中点击 -> 详情页 -> 底部按钮完成。
- 未执行任务详情页不在本次范围。

### 数据模型
- `tasks.detail_markdown TEXT`：Markdown 内容
- `tasks.detail_module_type TEXT`：附加模块类型，null / `checklist` / `app_actions`，单选
- `task_checklist_items`：task_id, order_index, content, checked, crossed_out
- `task_app_actions`：task_id, order_index, package_name, deep_link, hint

### 完成前通用确认机制
- `PreCompleteConfirmCallback` 接口：`onConfirmNeeded(taskId, confirmType, onConfirmed)`
- 当前仅实现 `CONFIRM_TYPE_CHECKLIST_STATE`
- 弹窗：[保存] [不保存] [取消]

### 统一任务点击入口
- `resolveAndHandleTaskClick(taskId)` 统一入口 -> LiveData 事件
- 有内容 -> `navigateToDetail`；仅标题 -> `showOnlyTitleDialog`
