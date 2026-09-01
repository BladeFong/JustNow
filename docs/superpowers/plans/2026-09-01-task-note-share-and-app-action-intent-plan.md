# 任务附加模块交互优化（关联笔记直编 & 应用跳转 Intent 粘贴）实现计划

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构任务编辑页笔记模块为单项直编模式并支持剪贴板自动识别；升级应用跳转模块添加弹窗支持直接粘贴并解析 Intent 深链。

**Architecture:** 
- 笔记模块（`TaskInputNoteShareSheet`）：改造 `sheet_note_share_editor.xml` 为单项编辑表单（链接 + 描述 + 清除/取消/保存），进入时自动探测剪贴板有效链接/Intent 并预填；统一外部分享入口。
- 应用跳转模块（`TaskInputAppActionSheet`）：在 `dialog_app_action_add` 中将 `et_app_search` 扩展为智能合一输入框，支持检测并解析 Intent URI（`Intent.parseUri`），提取包名并展示应用图标，持久化保存 `deepLink`。

**Tech Stack:** Java, Android SDK, AndroidX Material, Room

**相关 Spec:** `docs/superpowers/specs/2026-09-01-task-note-share-and-app-action-intent-design.md`

## Global Constraints

- Java 命名：成员变量 `m` 前缀，局部变量 `camelCase`
- 严格复用现有 Sheet、Dialog 样式与组件，不重写风格不一致的 UI 代码
- 多语言处理：修改/新增的字符串需覆盖 en、zh-CN、zh-TW、zh-HK 四套 `strings.xml`
- 修改前方案确认，Git 提交严格遵循审批流

---

### Task 1: 字符串资源补齐与更新（4 语言）

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`

- [ ] **Step 1: 更新各语言 strings.xml**
  - 新增/更新 `s_note_share_add_link_hint`
  - 新增 `s_note_share_clipboard_detected`
  - 新增 `s_note_share_clear`
  - 新增 `s_app_search_or_intent_hint`

---

### Task 2: 笔记模块 Sheet 布局重构（`sheet_note_share_editor.xml`）

**Files:**
- Modify: `app/src/main/res/layout/sheet_note_share_editor.xml`

**Interfaces / Views:**
- Produces: `et_note_link` (EditText, textUri), `et_note_hint` (EditText, text), `btn_clear` (Button), `btn_cancel` (Button), `btn_confirm` (Button)

- [ ] **Step 1: 改造布局为单项编辑表单**
  - 移除 `RecyclerView`
  - 添加链接输入框 `et_note_link`（占位提示、边距与字体风格对齐项目标准）
  - 添加描述输入框 `et_note_hint`
  - 底部操作栏包含「清除关联」(`btn_clear`)、「取消」(`btn_cancel`) 与「保存」(`btn_confirm`)

---

### Task 3: 笔记模块交互与剪贴板识别实现（`TaskInputNoteShareSheet.java`）

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputNoteShareSheet.java`

- [ ] **Step 1: 重写 `TaskInputNoteShareSheet` 交互逻辑**
  - 绑定表单控件与按键事件
  - 检查传入的 existing share：若有则回显并显示 `btn_clear`
  - 若无 existing share：检查系统剪贴板（`ClipboardManager`），若匹配有效 URL 或 Intent 则自动填入并 Toast 提示 `s_note_share_clipboard_detected`，聚焦描述框
  - 消费外部分享预填项 `consumePendingNoteSharePrefill()`
  - 保存与清除回调处理（`mOnSaved.accept(...)`）

---

### Task 4: 应用跳转模块 Intent 粘贴与解析升级（`dialog_app_action_add.xml` & `TaskInputAppActionSheet.java`）

**Files:**
- Modify: `app/src/main/res/layout/dialog_app_action_add.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputAppActionSheet.java`

- [ ] **Step 1: 更新 `dialog_app_action_add.xml` 的 Hint 资源引用**
- [ ] **Step 2: 在 `TaskInputAppActionSheet.showDialog` 中实现 Intent 智能识别**
  - 检查剪贴板：若弹窗打开且输入框为空时检测到 Intent 字符串，自动填入
  - `etAppSearch` 文本监听：检测 Intent 格式（`intent:`, `#Intent;`, `xxx://`）
  - 尝试调用 `Intent.parseUri` 解析目标包名与 Intent 数据
  - 匹配并展示应用图标，启用确定按钮
  - 保存时生成包含完整 `deepLink` 的 `TaskAppAction`

---

### Task 5: 单元测试与验证

**Files:**
- Create/Modify: `app/src/test/java/com/nearby/justnow/ui/taskinput/TaskNoteShareAndIntentTest.java`

- [ ] **Step 1: 编写 Intent 解析与剪贴板 URL 匹配测试用例**
- [ ] **Step 2: 运行单元测试**
  - `./gradlew testDebugUnitTest`

---

### Task 6: 编译验证与文档更新

- [ ] **Step 1: 全量编译验证**
  - `./gradlew :app:compileDebugJavaWithJavac`
- [ ] **Step 2: 按照规范更新项目文档与进度日志**
