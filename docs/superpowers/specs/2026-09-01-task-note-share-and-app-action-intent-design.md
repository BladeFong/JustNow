# 任务附加模块交互优化设计（关联笔记直编 & 应用跳转 Intent 粘贴）

日期：2026-09-01

## 背景与问题根因

在现有的任务编辑流程中：
1. **笔记模块交互缺失**：
   - 外部捕获（`CapturePickerActivity`）虽然支持从浏览器/APP 接收 URL 并通过 Intent 预填打开 `TaskInputNoteShareSheet`，但如果在任务编辑页（`TaskEditFragment`）中**直接点击笔记模块**进入，由于 `sheet_note_share_editor.xml` 仅包含 `RecyclerView` 且未提供「添加」按钮和录入表单，导致页面为空且无任何交互可用。
   - 此外，现有设计将笔记模块视为与“APP 跳转”相同的多项列表结构，对于绝大多数只关联单个网页/笔记链接的场景，多层列表与弹窗层级冗余、体验繁琐。
2. **应用跳转模块缺少 Intent 粘贴支持**：
   - 当前在 `TaskInputAppActionSheet` 中添加项目时仅支持在搜索框内检索已安装应用的名称，无法直接粘贴特定的 `Intent URI`（如 `intent:#Intent;...;end` 或自定义 Scheme 深链），限制了深度跳转与快捷直达配置。

## 目标

- **笔记模块（`TaskNoteShare`）单项直编化**：
  - 移除多层列表层级，进入 `TaskInputNoteShareSheet` 直接展示单项编辑表单（链接/深链输入框、描述/别名输入框、清空/取消/保存按钮）。
  - 进入模块时自动检测系统剪贴板：若包含有效 URL 或 Intent，自动填入链接输入框并将光标定位到描述框；若未识别，展示清晰的占位引导说明。
  - 统一外部分享（`CapturePickerActivity`）链路，进入即呈现预填好的单项编辑表单。
- **应用跳转模块（`TaskAppAction`）支持粘贴 Intent**：
  - 在添加弹窗（`dialog_app_action_add`）中将搜索框升级为“智能合一输入框”，既支持模糊搜索已安装应用，也支持直接粘贴 Intent 字符串。
  - 粘贴 Intent 时自动解析目标应用并展示图标与名称，允许用户自定义别名并持久化保存完整的 `deepLink`。
- **视觉风格与组件复用**：
  - 100% 严格复用现有 Sheet、Dialog、按钮样式与主题属性，不重写风格不一致的 UI 代码。

## 详细设计

### 一、笔记模块重构（`TaskInputNoteShareSheet`）

#### 1.1 界面布局（`sheet_note_share_editor.xml`）
复用现有项目 Sheet 风格（同 `sheet_checklist_editor.xml` / `sheet_app_action_editor.xml`），改造成单项编辑表单：
- **标题**：`TextView`（`@string/s_note_share_module_title`，即“关联笔记”）；
- **链接输入框**（`et_note_link`，`EditText`）：
  - `inputType="textUri"`
  - `hint="@string/s_note_share_add_link_hint"`（“输入/粘贴网页链接或深链（复制后进入自动识别）”）
  - `textAppearance="@style/TextAppearance.JustNow.Body"`
- **描述输入框**（`et_note_hint`，`EditText`）：
  - `inputType="text"`
  - `hint="@string/s_note_share_add_hint_label"`（“描述 / 别名（可选，默认展示链接）”）
  - `textAppearance="@style/TextAppearance.JustNow.Caption"`
- **底部操作栏**（`LinearLayout`，靠右对齐）：
  - `btn_clear`（「清除关联」，当已有内容时显示）：用于清空/移除该任务的笔记关联；
  - `btn_cancel`（「取消」）：关闭 Sheet 且不保存修改；
  - `btn_confirm`（「保存」）：校验链接有效性并保存。

#### 1.2 剪贴板识别与状态加载逻辑
在 `TaskInputNoteShareSheet` 的 `onCreateDialog` / `onViewCreated` 中：
1. **优先加载已有数据**：
   - 若传入了现有的 `TaskNoteShare`，回显 `deepLink` 至 `et_note_link`，回显 `hint` 至 `et_note_hint`；显示「清除关联」按钮；光标定位于 `et_note_hint`。
2. **无数据时检测剪贴板**：
   - 通过 `ClipboardManager` 获取剪贴板首项文本；
   - 过滤与校验：使用 `android.util.Patterns.WEB_URL` 或正则检测是否为有效 URL（`http://`、`https://`）或 Intent 格式（`intent://`、`#Intent;`、自定义 scheme 如 `weixin://`）；
   - **命中**：自动将链接填入 `et_note_link`，提示 Toast（“已自动填入剪贴板链接”），光标定位到 `et_note_hint`，并弹出软键盘；
   - **未命中 / 剪贴板为空**：保持输入框空白，输入框内部占位文字（Hint）承担使用说明，无弹窗打扰。
3. **消费外部分享预填项**：
   - 消费 `vm.consumePendingNoteSharePrefill()`，若存在则直接填充并展示，保存时无缝统一。

---

### 二、应用跳转模块升级（`TaskInputAppActionSheet` & `dialog_app_action_add`）

#### 2.1 弹窗交互升级
保持现有 `dialog_app_action_add.xml` 的布局结构与样式：
- **搜索与 Intent 合一输入框**（`AutoCompleteTextView et_app_search`）：
  - Hint 更新为：`@string/s_app_search_or_intent_hint`（“搜索应用名，或直接粘贴 Intent / 深链”）；
- **描述输入框**（`EditText et_app_hint`）：
  - Hint 更新为：`@string/s_app_hint_hint`（“描述 / 别名（可选）”）；

#### 2.2 Intent 智能解析流程
在 `et_app_search` 的 `TextWatcher` 中：
1. **判断是否为 Intent / 深链格式**：
   - 包含 `#Intent;`、以 `intent:` 开头，或包含标准 Scheme URI（如 `xxx://`）；
2. **Intent 解析处理**：
   - 尝试通过 `Intent.parseUri(text, Intent.URI_INTENT_SCHEME)` 解析；
   - 从解析出的 Intent 中获取 `targetPackage = intent.getPackage()`，若为空则从 ComponentName 获取，或通过 `PackageManager.resolveActivity(intent, 0)` 获取；
   - 若能匹配到本地已安装应用：
     - 从 `PackageManager` 提取该 App 的图标与 Label；
     - 调用 `applySelectedAppIcon` 在输入框左侧展示 App 图标；
     - 启用「添加」按钮；
     - 将解析出的完整 Intent 字符串暂存为 `selectedDeepLink`，目标包名暂存为 `selectedPackageName`；
   - 若未匹配到本地应用（通用/未安装）：
     - 显示默认 Android 机器人图标；
     - 启用「添加」按钮；
     - `selectedDeepLink = text`，`selectedPackageName = null` 或提取到的包名；
3. **普通文本处理**：
   - 走原有的 `AppSearchAdapter` 本地应用列表模糊搜索下拉。

#### 2.3 剪贴板辅助
在打开添加弹窗（`showDialog(null)`）时：
- 若剪贴板内容符合 Intent 格式且输入框为空，直接自动填入 `et_app_search` 并触发解析，极大提升配置效率。

---

### 三、数据流与架构流转

```
[用户触发] ────────────────────────────────────────┐
  ├─ 外部 Intent 捕获 (CapturePickerActivity)      │
  │    └─ 传递 EXTRA_PREFILL_NOTE_SHARE_*          │
  └─ 任务编辑页 (TaskEditFragment) 点击「笔记」模块 ──▼
               [TaskInputNoteShareSheet]
                    │ (单项编辑表单)
                    ├─ 检测剪贴板 URL / Intent 自动填入
                    ├─ 用户输入 / 确认修改
                    ▼
          [TaskInputViewModel] (持有单项 List<TaskNoteShare>)
                    │
                    ▼
           [TaskNoteShareDao] (保存任务时写入数据库)
                    │
                    ▼
         [ReminderDetailActivity] (详情页一键唤起深链)
```

1. **数据库表与实体**：
   - 沿用 `task_note_shares` 表结构与 `TaskNoteShare` 实体，无需任何数据库变更。
2. **ViewModel 状态绑定**：
   - `TaskInputViewModel` 中 `mPendingNoteShares` 维护单项列表（0 个或 1 个元素），与现有全流程无缝兼容。
3. **详情页执行与回显**：
   - `ReminderDetailActivity` 中的 `NoteShareAdapter` 与 `AppActionAdapter` 继续复用已验证的 `UriParser.parse` 与 `startActivity`，确保点击后无缝唤起浏览器或目标 APP。

---

### 四、新增与变更的字符串资源

| Key | en | zh-CN | zh-TW | zh-HK |
|---|---|---|---|---|
| `s_note_share_add_link_hint` | Paste link or Intent (auto-detects clipboard) | 输入/粘贴网页链接或深链（复制后进入自动识别） | 輸入/貼上網頁連結或深鏈（複製後進入自動識別） | 輸入/貼上網頁連結或深鏈（複製後進入自動識別） |
| `s_note_share_clipboard_detected` | Link detected from clipboard | 已自动填入剪贴板链接 | 已自動填入剪貼簿連結 | 已自動填入剪貼簿連結 |
| `s_note_share_clear` | Clear note | 清除关联 | 清除關聯 | 清除關聯 |
| `s_app_search_or_intent_hint` | Search app name or paste Intent URI | 搜索应用名，或直接粘贴 Intent / 深链 | 搜尋應用名，或直接貼上 Intent / 深鏈 | 搜尋應用名，或直接貼上 Intent / 深鏈 |

---

### 五、测试与验证方案

1. **单元测试（Robolectric / JUnit）**：
   - 测试 URL 与各类 Intent 格式（`intent:#Intent;...;end`、`schema://...`）的识别与解析；
   - 测试 `TaskInputViewModel` 对单项 `TaskNoteShare` 的暂存、预填、清除与持久化流转；
2. **真机交互验证**：
   - **笔记模块 - 剪贴板自动识别**：复制外部链接后进入「笔记」模块，验证是否自动填入并聚焦到描述框；
   - **笔记模块 - 手动修改与清除**：手动修改链接与描述，保存后查看任务详情是否正常展示并可点击跳转；点击「清除关联」验证是否清空；
   - **应用跳转模块 - 粘贴 Intent**：在添加弹窗中粘贴包含特定 Action/Data 的 Intent 字符串，验证是否正确识别应用图标与包名并成功保存，任务执行时点击验证是否正确拉起目标界面。
