# 任务 Markdown 编辑器与多端排版渲染实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现任务内容 Markdown 的全屏独立沉浸式编辑、M2 格式快捷工具条、独立纵向滚动与防拉伸，并在详情页接入 Markwon 真实富文本渲染。

**Architecture:** 
1. 引入官方权威库 `noties/Markwon`（`core` + `editor` + `ext-tasklist`）作为 Markdown AST 实时排版与最终视图渲染引擎。
2. 封装纯 Java 工具类 `MarkdownActionHandler` 统一处理光标选区包裹与行首标记插入，配齐完整单元测试。
3. 新建全屏 `MarkdownEditorFragment`（遵循 Material 2 规范），提供独立视口、工具条、双模预览及“完成编辑”常驻操作；改造 `TaskEditFragment` 卡片为固定高度防拉伸并支持跳转；在 `ReminderDetailActivity` 中接入 Markwon 渲染。

**Tech Stack:** Java 11, AndroidX, MaterialComponents (M2), Navigation Component, noties/Markwon 4.6.2, JUnit 4, Robolectric.

## Global Constraints

- 全面遵循项目统一的 **Material 2 (MaterialComponents)** 规范，**严禁引入 M3 (Theme.Material3 / Material3 属性)**。
- 遵循编码规范：公共方法委托给 private 核心逻辑，数据驱动替代复杂 if/else 分支。
- 每次代码修改前严格遵循修改确认协议与 Git 提交锁。

---

### Task 1: 引入 Markwon 官方依赖与版本配置

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:57-100`

**Interfaces:**
- Produces: `io.noties.markwon:core:4.6.2`, `io.noties.markwon:editor:4.6.2`, `io.noties.markwon:ext-tasklist:4.6.2`

- [ ] **Step 1: 在 libs.versions.toml 中增加 markwon 相关定义**
```toml
[versions]
# ...
markwon = "4.6.2"

[libraries]
# ...
markwon-core = { group = "io.noties.markwon", name = "core", version.ref = "markwon" }
markwon-editor = { group = "io.noties.markwon", name = "editor", version.ref = "markwon" }
markwon-ext-tasklist = { group = "io.noties.markwon", name = "ext-tasklist", version.ref = "markwon" }
```

- [ ] **Step 2: 在 app/build.gradle.kts 中引入依赖**
```kotlin
dependencies {
    // ...
    // Markdown
    implementation(libs.markwon.core)
    implementation(libs.markwon.editor)
    implementation(libs.markwon.ext.tasklist)
}
```

- [ ] **Step 3: 验证 Gradle 依赖解析与基础构建**
运行 `./gradlew compileDebugJavaWithJavac` 确认依赖解析无误。

---

### Task 2: 实现光标与选区快捷处理器及单元测试

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/util/MarkdownActionHandler.java`
- Create: `app/src/test/java/com/nearby/justnow/util/MarkdownActionHandlerTest.java`

**Interfaces:**
- Produces:
  - `MarkdownActionHandler.wrapSelection(EditText editText, String prefix, String suffix)`
  - `MarkdownActionHandler.insertLinePrefix(EditText editText, String prefix)`
  - `MarkdownActionHandler.insertBlock(EditText editText, String blockContent)`

- [ ] **Step 1: 编写失败的单元测试 MarkdownActionHandlerTest.java**
覆盖：选中文本包裹粗体/斜体、未选中文本插入粗体并将光标定位中间、行首插入 `# ` / `- ` / `- [ ] `、连续换行边界等。

- [ ] **Step 2: 实现 MarkdownActionHandler.java 逻辑**
基于 `editText.getSelectionStart()`, `getSelectionEnd()` 和 `getText().replace()` 纯 Java 实现，不侵入第三方组件。

- [ ] **Step 3: 运行测试并确保全部通过**
运行 `./gradlew testDebugUnitTest --tests "com.nearby.justnow.util.MarkdownActionHandlerTest"`。

---

### Task 3: 布局与字符串资源准备

**Files:**
- Create: `app/src/main/res/layout/fragment_markdown_editor.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/res/navigation/nav_task_input.xml`

**Interfaces:**
- Produces:
  - `@layout/fragment_markdown_editor`
  - 导航节点 `@id/markdownEditorFragment` 与 action `@id/action_taskEditFragment_to_markdownEditorFragment`

- [ ] **Step 1: 在 strings.xml 及各多语言文件中添加文案**
添加：`s_edit_task_content`（编辑任务内容）、`s_markdown_preview`（预览）、`s_markdown_edit`（编辑）、`s_done_editing`（完成编辑）、`s_expand_to_edit`（点击展开编辑）。

- [ ] **Step 2: 创建 fragment_markdown_editor.xml 布局**
采用 MaterialComponents M2 规范：
  - 顶部 AppBarLayout + MaterialToolbar（包含返回按钮与预览/编辑切换按钮）。
  - 下方 HorizontalScrollView 工具条（包含 H1, H2, B, I, 列表, 有序, 待办, 引用, 代码块, 分割线按钮）。
  - 中间 ViewFlipper 或双布局（独立垂直滚动 `EditText` + `NestedScrollView` 包裹的 `TextView` 预览）。
  - 底部固定 CardView 底栏（包含右下角“完成编辑” MaterialButton）。

- [ ] **Step 3: 在 nav_task_input.xml 中注册节点**
注册 `MarkdownEditorFragment` 并添加从 `taskEditFragment` 相互导航的 actions。

---

### Task 4: 实现全屏 Markdown 编辑器 Fragment

**Files:**
- Create: `app/src/main/java/com/nearby/justnow/ui/taskinput/MarkdownEditorFragment.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java`

**Interfaces:**
- Consumes: `MarkdownActionHandler`, `TaskInputViewModel`, `Markwon`
- Produces: `MarkdownEditorFragment` 完整的编辑与预览交互

- [ ] **Step 1: 实现 MarkdownEditorFragment.java**
- 初始化 Toolbar 返回箭头与标题。
- 使用 `Markwon.builder(context).usePlugin(TaskListPlugin.create(context)).build()` 初始化 Markwon 实例。
- 使用 `MarkwonEditor.create(markwon)` 绑定到 `EditText` 实现输入时实时语法淡化与排版高亮。
- 绑定工具条各按钮点击事件到 `MarkdownActionHandler`。
- 实现“✏️ 编辑 / 👁 预览”双模切换。
- 点击“完成编辑”或返回时：调用 `mViewModel.setMarkdown(currentText)`，然后 `findNavController().navigateUp()`。

---

### Task 5: 改造任务编辑页 TaskEditFragment 与卡片防拉伸

**Files:**
- Modify: `app/src/main/res/layout/fragment_task_edit.xml:57-84`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`

**Interfaces:**
- Consumes: `TaskInputViewModel`, `action_taskEditFragment_to_markdownEditorFragment`

- [ ] **Step 1: 修改 fragment_task_edit.xml 约束卡片高度**
将 `card_markdown` 高度固定为 `130dp`（或最大 `140dp`），移除 `layout_height="0dp"` + `layout_weight="1"` 的弹性拉伸，设置 `et_markdown` 为非获焦状态但可点击，右下角增加“点击展开编辑 ↗”徽标。

- [ ] **Step 2: 修改 TaskEditFragment.java 点击跳转逻辑**
点击 `card_markdown` 或 `et_markdown` 时，跳转至 `markdownEditorFragment`。在 `restoreState()` / `onResume()` 中实时刷新摘要内容。

---

### Task 6: 改造任务详情页 ReminderDetailActivity 接入 Markwon

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/reminderdetail/ReminderDetailActivity.java:141-146`

**Interfaces:**
- Consumes: `Markwon`

- [ ] **Step 1: 接入 Markwon 富文本渲染**
替换原 `mBinding.tvMarkdown.setText(task.detailMarkdown)`：
```java
if (hasMarkdown) {
    mBinding.tvMarkdown.setVisibility(View.VISIBLE);
    Markwon markwon = Markwon.builder(this)
        .usePlugin(TaskListPlugin.create(this))
        .build();
    markwon.setMarkdown(mBinding.tvMarkdown, task.detailMarkdown);
}
```

---

### Task 7: 完整验证与模块进度文档维护

**Files:**
- Test: `app/src/test/java/com/nearby/justnow/util/MarkdownActionHandlerTest.java`
- Modify: `modules/task-input.md`
- Modify: `modules/task-input_progress.md`
- Modify: `progress.md`

- [ ] **Step 1: 运行全部相关单元测试**
执行 `./gradlew testDebugUnitTest --tests "com.nearby.justnow.util.*" --tests "com.nearby.justnow.ui.taskinput.*"` 确保零回归。

- [ ] **Step 2: 记录进度日志与技术决策**
更新 `progress.md`、`modules/task-input_progress.md` 与 `modules/task-input.md`。
