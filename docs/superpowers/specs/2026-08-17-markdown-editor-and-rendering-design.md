# 任务 Markdown 编辑器与多端排版渲染设计规范

> 相关模块：[task-input](../../../modules/task-input.md)，[task-edit](../../../modules/task-edit.md)，[reminder-detail](../../../modules/reminder-detail.md)

## 1. 概述与背景

### 1.1 当前痛点
1. **编辑页长文本拉伸页面**：在 `TaskEditFragment` 中，任务内容（Markdown）卡片包裹在 `NestedScrollView` 内部，当内容较长时，输入框会被无限撑高拉长，导致整体编辑页面的其他关键控件（标签、象限、按钮）被挤出屏幕。
2. **编辑框内部无法滚动**：因外层 `NestedScrollView` 拦截了垂直滑动触摸事件，长文本在 `EditText` 内部无法正常上下滚动，只能依赖拖拽光标。
3. **缺少 Markdown 格式交互与所见即所得体验**：现有输入框仅为普通纯文本框，无格式快捷栏，输入或粘贴 Markdown 字符时无任何视觉高亮与排版反馈。
4. **详情页 Markdown 渲染缺失**：`ReminderDetailActivity` 中虽然有 `tv_markdown` 控件，但底层仅直接调用 `setText(rawMarkdown)`，并未真正接入 Markdown 渲染引擎。

### 1.2 改造目标
1. **视觉规范**：全面遵循项目统一的 **Material 2 (MaterialComponents)** 规范（不使用 M3）。
2. **表单卡片防拉伸**：`TaskEditFragment` 常规态保持现有卡片外观不变，设定固定/最大高度（`120dp`~`140dp`），超出内容截断显示，点击卡片进入全屏编辑。
3. **独立全屏 Markdown 编辑器**：新建 `MarkdownEditorFragment`，提供独立顺畅滚动视口、顶部 M2 风格 Markdown 快捷工具条、一键“✏️ 编辑 / 👁 预览”双模切换、以及底部常驻“完成编辑”按钮。
4. **官方 Markwon 引擎赋能**：引入成熟且与现代 Android 14+ 稳定兼容的 `noties/Markwon` 官方核心库，实现编辑实时排版着色与详情页出版级富文本渲染。
5. **粘贴保护**：确保外部复制的 Markdown 格式文本在粘贴时标记字符不丢失，且实时应用排版。

---

## 2. 界面与交互设计

### 2.1 任务编辑页常规态（`TaskEditFragment`）
- **样式与卡片结构**：严格保持既有 MaterialCardView 边框、圆角与布局层级不变。
- **高度约束**：
  - `card_markdown` 设定固定高度 `130dp`，内部 `et_markdown` 设为不可直接编辑状态（`focusable="false"`、`clickable="true"`）或拦截点击事件。
  - 内部展示前几行内容摘要，底部通过轻微渐变遮罩自然过渡，卡片右下角带有“点击展开编辑 ↗”标识。
  - 用户点击卡片任意区域，立即导航至全屏编辑器 `MarkdownEditorFragment`。

### 2.2 独立全屏 Markdown 编辑器（`MarkdownEditorFragment`）
- **顶部 Toolbar**：
  - 左侧返回箭头（Navigation UP 图标）。
  - 标题：`@string/s_edit_task_content`（"编辑任务内容"）。
  - 右侧提供模式切换按钮：`✏️ 编辑` 与 `👁 预览`。
- **Markdown 快捷格式工具条**（M2 风格 `HorizontalScrollView`）：
  - 采用 M2 `Widget.MaterialComponents.Button.OutlinedButton` 或无边框图标按钮样式，间距紧凑（`horizontal_gap=4dp`），高度 `36dp`。
  - 工具按钮清单：
    1. `H1`：一级标题（行首插入 `# `）
    2. `H2`：二级标题（行首插入 `## `）
    3. `B`：粗体（包裹 `**`，光标智能居中）
    4. `I`：斜体（包裹 `*`）
    5. `• 列表`：无序列表（行首插入 `- `）
    6. `1. 有序`：有序列表（行首插入 `1. `）
    7. `☑ 待办`：任务复选框（行首插入 `- [ ] `）
    8. `> 引用`：块引用（行首插入 `> `）
    9. `</> 代码`：行内代码或代码块（包裹 ` ``` ` 或 ` ` `）
    10. `--- 分割`：插入水平分割线
- **独立编辑区**：
  - 占据中间全部剩余视口（`layout_weight="1"`），设置 `overScrollMode="always"` 和 `scrollbars="vertical"`，光标移动与上下滑动彻底解耦。
  - 挂载 `MarkwonEditor` 实时监听器，实现标题字号放大、粗体着色加重、标记符号浅灰淡化。
- **预览视图区**：
  - 切换到“👁 预览”时，隐藏编辑框与工具条，显示带有垂直滚动的 `TextView`，通过 `Markwon.setMarkdown()` 渲染为最终富文本排版。
- **底部操作栏**：
  - 底部常驻卡片/底栏，右下角放置 M2 主按钮 `Widget.MaterialComponents.Button`（"完成编辑"）。
  - 点击“完成编辑”或 Toolbar 返回箭头，将最新 Markdown 文本同步回 `TaskInputViewModel` 并回退。

### 2.3 任务详情页（`ReminderDetailActivity`）
- 替换现有裸文本赋值逻辑，改为：
  ```java
  Markwon markwon = Markwon.builder(this)
      .usePlugin(TaskListPlugin.create(this))
      .build();
  markwon.setMarkdown(mBinding.tvMarkdown, task.detailMarkdown);
  ```
- 使得会议纪要、工作清单、格式文档在详情页中具备专业的层级排版。

---

## 3. 核心技术架构与光标交互引擎

### 3.1 光标与选区快捷处理器（`MarkdownActionHandler`）
封装独立、无依赖、高可测的纯 Java 辅助类，核心方法规范：

```java
public class MarkdownActionHandler {

    /**
     * 包裹选区或光标处插入对称标记（如粗体、斜体、代码）。
     * - 有选中文本时：用 prefix 和 suffix 包裹选区，并保持文本选中或光标置于末尾。
     * - 无选中文本时：在光标处插入 prefix + suffix，并将光标移动到中间。
     */
    public static void wrapSelection(EditText editText, String prefix, String suffix);

    /**
     * 在当前光标所在行的起始位置插入行首标记（如标题、列表、引用、待办）。
     * - 若行首已有该标记则不重复累加；若为不同标记可进行替换或前置。
     */
    public static void insertLinePrefix(EditText editText, String prefix);

    /**
     * 插入独立块（如分割线 ---）。
     */
    public static void insertBlock(EditText editText, String blockContent);
}
```

### 3.2 依赖引入
在 `gradle/libs.versions.toml` 与 `app/build.gradle.kts` 中引入成熟官方依赖：
- `io.noties.markwon:core:4.6.2`
- `io.noties.markwon:editor:4.6.2`
- `io.noties.markwon:ext-tasklist:4.6.2`

---

## 4. 文件变动清单

| 文件路径 | 变动类型 | 职责与改动内容 |
|---|---|---|
| `gradle/libs.versions.toml` | 修改 | 添加 `markwon` 核心及扩展库版本与库定义 |
| `app/build.gradle.kts` | 修改 | 引入 markwon 依赖 |
| `app/src/main/java/com/nearby/justnow/util/MarkdownActionHandler.java` | 新增 | 工具条光标/选区交互通用处理器（纯逻辑） |
| `app/src/test/java/com/nearby/justnow/util/MarkdownActionHandlerTest.java` | 新增 | 工具条各快捷动作的单测覆盖（包裹、行首插入、边界条件） |
| `app/src/main/java/com/nearby/justnow/ui/taskinput/MarkdownEditorFragment.java` | 新增 | 全屏 Markdown 编辑界面实现 |
| `app/src/main/res/layout/fragment_markdown_editor.xml` | 新增 | 全屏 Markdown 编辑器布局（M2 规范） |
| `app/src/main/res/navigation/nav_task_input.xml` | 修改 | 注册 `markdownEditorFragment` 导航节点与跳转 Action |
| `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java` | 修改 | Markdown 卡片点击跳转全屏编辑器，返回时刷新显示 |
| `app/src/main/res/layout/fragment_task_edit.xml` | 修改 | `card_markdown` 设为固定高度防拉伸 |
| `app/src/main/java/com/nearby/justnow/ui/reminderdetail/ReminderDetailActivity.java` | 修改 | 接入 Markwon 进行正式富文本渲染 |
| `app/src/main/res/values/strings.xml`（及中/繁资源） | 修改 | 补充工具条提示文案与操作按钮字符串 |

---

## 5. 测试与验证计划

1. **单测验证（`MarkdownActionHandlerTest`）**：
   - 选中文本加粗、未选中加粗光标居中测试。
   - 单行/多行行首插入 `#`、`- `、`- [ ] ` 测试。
   - 空文本、光标位于行首/行尾/中间边界测试。
2. **UI 与交互集成验证**：
   - 在 `TaskEditFragment` 中加载超过 50 行的长文本，验证表单卡片高度是否稳定在 130dp，整体页面不拉长变形。
   - 点击卡片跳转全屏编辑器，验证纵向独立滚动、工具条快捷输入、实时语法淡化高亮。
   - 点击“👁 预览”，验证 Markwon 完整渲染。
   - 外部复制一段含有 Markdown 的文本粘贴至编辑器，验证格式无损保留。
   - 点击“完成编辑”，验证数据正确同步回 `TaskInputViewModel`，保存后在 `ReminderDetailActivity` 验证完整渲染效果。
