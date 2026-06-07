# 跨应用 Intent 捕获与任务关联设计

日期：2026-06-07

## 背景

用户在浏览器或其他 APP 内通过 `intent://` / 自定义 scheme 跳转目标 APP 时，希望把这条跳转入口"装"到 JustNow 的任务里，作为该任务执行时可以一键再次唤起目标 APP 的快捷方式（已有的"APP 跳转"附加模块）。

同类需求还包括：用户在 Chrome、文件管理器、系统分享等触发 `ACTION_SEND` 的位置分享 URL / 文本到 JustNow，把分享内容作为任务的 APP 跳转项或笔记内容。

技术调研结论（详见对话记录）：

- 浏览器内点击 `intent://` 或自定义 scheme 时，Android 会弹"打开方式"选择器，本 APP 注册 `<intent-filter>` 后即可作为候选目标接收完整 Intent。
- 不存在静默截获其他 APP 跳转 Intent 的标准方案；`ShortcutManager.requestPinShortcut()` 不向第三方 APP 暴露事件。
- ACTION_SEND 没有"仅匹配 URL 文本"的 MIME 过滤能力，只能注册 `text/plain` 全量接收后在 APP 内判断。
- 大厂私有"分享菜单"（微信/抖音/腾讯视频等）不走系统 ACTION_SEND，本 APP 接不进去；可达范围限于走系统选择器的来源（Chrome、文件管理器、系统相册、部分海外 APP）。
- "添加到桌面"语义的 Intent 通常长期稳定（小程序 appId / 频道 ID / 公众号 ghid 等永久身份），无短时签名，可放心持久化。

## 目标

- 本 APP 注册成"浏览器跳 APP"选择器候选，用户主动选中后可把 Intent 捕获到任务里。
- 本 APP 注册成系统文本分享接收方，URL 走 APP 跳转捕获流，非 URL 走任务笔记流。
- 捕获后提供一个"选任务"页面，支持选已有任务、新建含 APP 跳转任务、新建含笔记任务三条出口。
- 复用现有 `TaskAppAction` 数据结构与 APP 跳转编辑 sheet，零 schema 变更。
- 现有 APP 跳转 sheet 中的项支持编辑（描述），不再只能删除。

## 非目标

- 不实现内置 WebView 浏览器、不实现自定义 Launcher，不追求"完全静默"的捕获。
- 不拦截 http/https 链接（避免污染所有浏览器选择器），仅接管非 http(s) 自定义 scheme。
- 不静默接管"始终用本 APP 打开"——用户若手动设了"始终"是系统行为，本 APP 不做引导。
- 不实现来源 APP 白名单 / 黑名单，所有走系统选择器的来源都可触发。
- 不解析特定厂商私有协议（如微信小程序 `weixin://dl/business/?t=...` 的 token 内容），按通用 Intent URI 透传保存。
- 不预判 Intent 是否会过期；启动失败统一走异常兜底提示。

## 设计

### 一、Manifest 入口注册

新增一个 `AppActionCaptureActivity`，在 Manifest 声明两个 `<intent-filter>`：

```xml
<activity
    android:name=".ui.appactioncapture.AppActionCaptureActivity"
    android:exported="true"
    android:label="@string/s_capture_activity_label"
    android:theme="@style/Theme.JustNow"
    android:taskAffinity=""
    android:excludeFromRecents="true">

    <!-- 入口 A：浏览器跳 APP 的自定义 scheme 选择器 -->
    <intent-filter android:label="@string/s_capture_filter_scheme">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <!-- 不限定 scheme：通配所有非 http(s) 自定义 scheme;
             不写 data 节点会匹配所有 URI，但需要至少一个 data 节点否则不参与匹配 -->
        <data android:scheme="*" />
    </intent-filter>

    <!-- 入口 B：系统分享文本接收 -->
    <intent-filter android:label="@string/s_capture_filter_send">
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
</activity>
```

> **说明**：`<data android:scheme="*"/>` 会同时匹配 http/https。需要在 `AppActionCaptureActivity.onCreate` 内对收到的 scheme 做白名单/黑名单判断——`http` / `https` 直接 `finish()`，避免污染浏览器选择器。
>
> 若 Android 平台对 `scheme="*"` 仍把本 APP 显示在 http(s) 链接选择器内，则改为逐一列出常见非 http(s) scheme（`weixin/tbopen/taobao/openapp.jdmobile/pinduoduo/alipays/snssdk*/...`）静态枚举。两种方案在实现阶段实测决定，本设计文档允许实现期回退到静态枚举方案，不视为设计变更。

### 二、AppActionCaptureActivity：分流

入口 Activity 不渲染 UI，只做分流后立即 `startActivity` 跳到承载页 + `finish()`。

```
onCreate:
  String action = intent.getAction();
  Uri data = intent.getData();
  String type = intent.getType();
  String referrerPackage = resolveReferrer();          // Activity.getReferrer()
  String referrerLabel = resolveAppLabel(referrerPackage);

  if (ACTION_VIEW.equals(action)) {
    if (data == null || isHttpScheme(data.getScheme())) { finish(); return; }
    launchPickerForIntent(intent, referrerLabel);
    return;
  }

  if (ACTION_SEND.equals(action) && "text/plain".equals(type)) {
    String subject = intent.getStringExtra(EXTRA_SUBJECT);
    String title = intent.getStringExtra(EXTRA_TITLE);
    String text = intent.getStringExtra(EXTRA_TEXT);
    String shortTitle = pickShorterTitle(subject, title);  // 都有取短，一个用一个
    String url = extractUrl(text);                         // 正则提 URL
    if (url != null) {
      Intent viewIntent = new Intent(ACTION_VIEW, Uri.parse(url));
      launchPickerForIntent(viewIntent, referrerLabel, shortTitle);
    } else {
      launchPickerForNote(text, shortTitle, referrerLabel);
    }
    return;
  }

  finish();
```

辅助函数：

- `pickShorterTitle(a, b)`：两者都非空取 `length` 短的；其中一个空返回另一个；都空返回 null。
- `extractUrl(text)`：用 `android.util.Patterns.WEB_URL` 在文本里找第一个 URL；找不到返回 null。
- `resolveReferrer()`：`Activity.getReferrer()` 返回 `Uri`，若 scheme 是 `android-app`，取 host 作为 package；为空返回 null。
- `resolveAppLabel(pkg)`：`PackageManager.getApplicationLabel(getApplicationInfo(pkg, 0))`；失败返回 null。

`launchPickerForIntent` / `launchPickerForNote` 都用同一个 `Intent` 跳到下面的 `CapturePickerActivity`，通过 extras 区分模式。

### 三、CapturePickerActivity：任务选择/新建页

独立 Activity（不放进 TaskInputActivity nav graph，承载逻辑完全不同）。

布局（`activity_capture_picker.xml`）从上到下：

| 区域 | 说明 |
|------|------|
| Toolbar | 标题 "选择任务"；返回箭头关闭页面（取消捕获） |
| 搜索框 | `EditText`（无图标，圆角 card），placeholder "搜索任务" |
| RecyclerView | 任务列表，权重 1 撑满 |
| 底部按钮区 | 两个 `MaterialButton` 上下堆叠（横向均分会过窄影响文案）：「新建含 APP 跳转任务」 / 「新建含笔记任务」 |

**按钮样式**：沿用项目规范，同 `fragment_task_edit.xml` 的 `btn_next_quadrant`：
- `MaterialButton` 默认（filled）样式
- `app:cornerRadius="16dp"`
- `android:textAppearance="@style/TextAppearance.JustNow.Body"`
- `android:layout_width="wrap_content"` + `layout_gravity="center_horizontal"`
- `layout_marginHorizontal="12dp"` + `layout_marginBottom="12dp"`（两按钮间用 `marginTop="8dp"` 间隔）

行为：

**任务列表数据源**：`TaskRepository.observeTasksWithAppAction()` —— SQL `SELECT * FROM tasks WHERE archived = 0 AND id IN (SELECT DISTINCT task_id FROM task_app_actions) ORDER BY ...`。排序复用主界面默认顺序（创建时间倒序或现有规则，沿用 Repository 已有方法即可）。

**搜索过滤**：搜索框文本变化时，对当前列表做客户端分词过滤（复用 `TextTokenizer` + 多 token AND）。不调全文检索 API，因为列表已经预过滤过附加模块条件。

**列表项渲染**：复用 `item_search_result.xml` 同款样式（标题 + 标签 chip），命中关键词高亮。

**点击列表项 → 加进已有任务**（入口 1）：
- `startActivity(TaskInputActivity)` 携带 extras：
  - `EXTRA_LOAD_TASK_ID = task.id`
  - `EXTRA_OPEN_APP_ACTION_SHEET = true`
  - `EXTRA_PREFILL_APP_ACTION_URI = <Intent.toUri>`
  - `EXTRA_PREFILL_APP_ACTION_HINT = <shortTitle 或 null>`
- `TaskInputActivity` 启动 → `TaskInputViewModel.loadTaskForEdit(taskId)` → 导航到 `TaskEditFragment` → 检测到 `EXTRA_OPEN_APP_ACTION_SHEET` 自动调用 APP 跳转 sheet 打开方法 → 检测到 `EXTRA_PREFILL_APP_ACTION_URI` 在 sheet 列表顶部预插入一项

**点击底部「新建含 APP 跳转任务」**（入口 2）：
- `startActivity(TaskInputActivity)` 携带 extras：
  - `EXTRA_DRAFT_TASK_TITLE = <shortTitle 或 ""，留空走标签兜底>`
  - `EXTRA_DRAFT_TASK_TAG_NAME = <referrerLabel 或 null>`
  - `EXTRA_DRAFT_OPEN_APP_ACTION_SHEET = true`
  - `EXTRA_PREFILL_APP_ACTION_URI = <Intent.toUri>`
  - `EXTRA_PREFILL_APP_ACTION_HINT = <shortTitle 或 null>`
- 跳过任务录入首屏，直接进入 `TaskEditFragment`（已草稿态填好标题/标签）→ 自动打开 APP 跳转 sheet 且预填首项

**点击底部「新建含笔记任务」**（入口 3）：
- 仅出现在分流到非 URL 的笔记流；URL 流也保留这个按钮（用户可选"把这条 URL 当笔记记录"）
- `startActivity(TaskInputActivity)` 携带 extras：
  - `EXTRA_DRAFT_TASK_TITLE = <shortTitle 或 "<referrerLabel>分享">`
  - `EXTRA_DRAFT_TASK_TAG_NAME = <referrerLabel 或 null>`
  - `EXTRA_DRAFT_TASK_MARKDOWN = <分享文本 / URL>`
- 直接进 `TaskEditFragment` 编辑态，正文 / 标签 / 标题已填好；不打开任何 sheet

### 四、TaskInputActivity 接收 extras 与预填

`TaskInputActivity.onCreate` 解析新增 extras，注入到 `TaskInputViewModel`：

```
long loadTaskId = intent.getLongExtra(EXTRA_LOAD_TASK_ID, -1L);
String prefillTitle = intent.getStringExtra(EXTRA_DRAFT_TASK_TITLE);
String prefillTag = intent.getStringExtra(EXTRA_DRAFT_TASK_TAG_NAME);
String prefillMarkdown = intent.getStringExtra(EXTRA_DRAFT_TASK_MARKDOWN);
boolean openSheet = intent.getBooleanExtra(EXTRA_OPEN_APP_ACTION_SHEET, false)
        || intent.getBooleanExtra(EXTRA_DRAFT_OPEN_APP_ACTION_SHEET, false);
String prefillUri = intent.getStringExtra(EXTRA_PREFILL_APP_ACTION_URI);
String prefillHint = intent.getStringExtra(EXTRA_PREFILL_APP_ACTION_HINT);

if (loadTaskId > 0) {
  // 入口 1：编辑既有任务。先 loadTaskForEdit（异步），加载完成回调里
  // 注入 sheet prefill，再 navigate 到 taskEditFragment
  viewModel.loadTaskForEdit(loadTaskId, () -> {
    viewModel.applyExternalCapture(null, null, null, openSheet, prefillUri, prefillHint);
    navController.navigate(R.id.taskEditFragment);
  });
} else if (任意 prefill 非空) {
  // 入口 2 / 入口 3：新建任务，无需异步加载
  viewModel.applyExternalCapture(prefillTitle, prefillTag, prefillMarkdown,
                                  openSheet, prefillUri, prefillHint);
  navController.navigate(R.id.taskEditFragment);  // 跳过录入首屏
}
```

> `loadTaskForEdit` 现状是无回调的 LiveData 推送，本期需要扩展支持完成回调（或新增一个 `loadTaskForEditOnce(id, Runnable)` 重载），避免在加载完成前 navigate 导致 prefill 被旧数据覆盖。

预填项的 `hint` 字段进入 sheet 后用户**可编辑**——sheet 列表项支持点击编辑（见第六节），未保存任务前所有修改保留在 ViewModel 草稿态。

`TaskInputViewModel.applyExternalCapture(...)`：

- 把 title / markdown / tag 写到 `mDraftTask`
- 把 `prefillUri + prefillHint` 暂存到 `mPendingAppActionPrefill`（LiveData / 一次性字段）
- 设置 `mPendingOpenAppActionSheet` 标记

`TaskEditFragment.onViewCreated` 末尾：

```
viewModel.consumePendingOpenAppActionSheet().observe(this, shouldOpen -> {
  if (Boolean.TRUE.equals(shouldOpen)) {
    showAppActionSheet();
  }
});
```

`TaskInputAppActionSheet.onViewCreated` 末尾：

```
TaskAppAction prefill = viewModel.consumePendingAppActionPrefill();
if (prefill != null) {
  insertPrefillAtTop(prefill);  // 加到 mItems[0]，notify
}
```

> 标签预填走现有标签创建/查找路径：保存任务时 `TaskInputViewModel.saveTask` 已经处理"按名称找不到就新建"。

### 五、TaskAppAction 数据写入

捕获的 Intent → `TaskAppAction`：

```java
String intentUri = capturedIntent.toUri(Intent.URI_INTENT_SCHEME);
String pkg = capturedIntent.getPackage();
if (pkg == null && capturedIntent.getComponent() != null) {
  pkg = capturedIntent.getComponent().getPackageName();
}
if (pkg == null) {
  // 用 PackageManager 解析能处理该 Intent 的第一个候选 APP
  ResolveInfo ri = pm.resolveActivity(capturedIntent, 0);
  pkg = ri != null ? ri.activityInfo.packageName : null;
}

TaskAppAction item = new TaskAppAction();
item.packageName = pkg;          // 可能为 null（无 APP 能处理）
item.deepLink = intentUri;       // 完整 Intent URI，启动时 parseUri
item.hint = shortTitle;          // SEND 携带的标题；A 入口为 null
```

启动复用 `ReminderDetailActivity` 已有逻辑（`Intent.parseUri(action.deepLink, 0)` → `startActivity`），无需改动。

### 六、APP 跳转 sheet 项编辑能力扩展

现状：`TaskInputAppActionSheet` 列表项只有删除按钮。

新增：每项点击进入编辑态，可改 `hint`（描述）；其他字段（packageName、deepLink）由当时添加流程决定，不可改。

实现：

- `sheet_app_action_editor.xml` 内 RecyclerView item 增加"编辑"图标按钮（位于删除按钮左侧）
- 点击编辑按钮 → 弹同一个"添加 APP 对话框"组件（`AddAppActionDialog`，本期已存在），传入当前项数据进入"编辑模式"：
  - APP 选择区域**只读**（显示当前 APP 图标 + 名称，不可重新搜索）
  - 描述输入框预填当前 `hint`
  - 底部按钮文案改为「保存」 / 「取消」
- 保存时通过回调回写到列表对应位置，不改 packageName / deepLink

`AddAppActionDialog` 当前只支持新增模式，需要扩展构造参数：

```java
AddAppActionDialog.newInstance(
    @Nullable TaskAppAction existing,  // null = 新增；非 null = 编辑
    Callback callback)
```

`Callback.onConfirm(TaskAppAction result)` 由父 sheet 区分新增 / 替换。

### 七、新增字符串资源（4 语言）

| key | en | zh-CN | zh-TW | zh-HK |
|-----|------|-------|-------|-------|
| `s_capture_activity_label` | Add to Task | 添加到任务 | 加入任務 | 加入任務 |
| `s_capture_filter_scheme` | Add as task action | 作为任务动作 | 作為任務動作 | 作為任務動作 |
| `s_capture_filter_send` | Save to task | 保存到任务 | 儲存至任務 | 儲存至任務 |
| `s_capture_picker_title` | Choose a task | 选择任务 | 選擇任務 | 選擇任務 |
| `s_capture_picker_search_hint` | Search tasks | 搜索任务 | 搜尋任務 | 搜尋任務 |
| `s_capture_picker_new_app_action` | New task with app action | 新建含 APP 跳转 | 新建含 APP 跳轉 | 新建含 APP 跳轉 |
| `s_capture_picker_new_note` | New task with note | 新建含笔记 | 新建含筆記 | 新建含筆記 |
| `s_capture_share_title_fallback` | %1$s share | %1$s 分享 | %1$s 分享 | %1$s 分享 |
| `s_capture_launch_failed` | Failed to launch target app | 启动目标 APP 失败 | 啟動目標 APP 失敗 | 啟動目標 APP 失敗 |
| `s_app_action_edit` | Edit | 编辑 | 編輯 | 編輯 |

> 港台用语差异已按规范分别处理（"搜索→搜尋"、"保存→儲存"、"添加→加入"）。

### 八、可选行为：捕获后回到来源 APP

不在本期范围。讨论中提到"捕获后转发原 Intent 回浏览器"用户体验更顺滑，但牵涉到任务保存时序（用户在 picker 页是否会取消？保存中 finish 会丢数据？），评估后**本期跳过**：捕获完成后停在 JustNow 编辑页，用户保存或取消后自行按 home / 返回离开。

## 数据流总结

```
[浏览器/Chrome/文件管理器]
    │ 点击 intent:// or 分享文本
    ▼
[Android 选择器]
    │ 用户选 JustNow
    ▼
[AppActionCaptureActivity] (无 UI, 立刻 finish)
    │ 分流: VIEW + 非 http(s) → 捕获流
    │      SEND + URL → 捕获流
    │      SEND + 非 URL → 笔记流（仅入口 3）
    ▼
[CapturePickerActivity]
    │ A. 选已有任务 → TaskInputActivity (EXTRA_LOAD_TASK_ID + EXTRA_OPEN_SHEET + EXTRA_PREFILL_*)
    │ B. 新建含 APP 跳转 → TaskInputActivity (EXTRA_DRAFT_* + EXTRA_OPEN_SHEET + EXTRA_PREFILL_*)
    │ C. 新建含笔记 → TaskInputActivity (EXTRA_DRAFT_* 含 MARKDOWN)
    ▼
[TaskInputActivity → TaskEditFragment]
    │ 预填 title/markdown/tag
    │ 若 EXTRA_OPEN_SHEET → 自动 showAppActionSheet
    ▼
[TaskInputAppActionSheet]
    │ 顶部插入预填项 (deepLink = capturedIntentUri, hint = shortTitle)
    │ 用户可继续添加/编辑/删除其他项
    ▼
[用户点保存] → TaskInputViewModel.saveTask
    │ 标签按名称查找/新建（含 referrerLabel）
    │ TaskAppAction 落库
    ▼
[完成，返回触发分享的来源]
```

## 边界与失败处理

- **来源 APP 不可识别**（`getReferrer()` 返回 null）：标签 / 兜底标题统一空，UI 上把分享内容用 "分享" 作为 fallback 标题。
- **无 APP 能处理 deepLink**（`resolveActivity` 返回 null）：仍保存（用户可能后续装该 APP），`packageName = null`。启动时 `startActivity` 抛 `ActivityNotFoundException` → toast `s_capture_launch_failed`。
- **deepLink 失效**（带 token / TTL 的非"加桌"类 Intent）：本期不预防，启动失败统一兜底提示，用户自行删除或重新捕获。
- **CapturePickerActivity 列表为空**：列表区显示空态文案"还没有带 APP 跳转的任务"，引导用户点底部新建按钮。
- **scheme="*" 误匹配 http(s)**：`AppActionCaptureActivity.onCreate` 第一行白名单过滤；实现期实测若仍出现在浏览器选择器，回退到静态枚举非 http(s) scheme 列表。
- **SEND 文本同时含 URL 和说明文字**（如 B 站分享格式 "【视频标题】链接"）：`extractUrl` 取第一个 URL 作 deepLink，剩余文本不丢——`shortTitle` 已经吸收了 `EXTRA_SUBJECT/TITLE`；若 SEND 没传 subject/title 但 text 含说明文字，本期接受信息丢失，不做拆分。

## 测试要点

数据层（Robolectric 可断言）：
- `pickShorterTitle` 边界：null/null、null/"x"、"abc"/"abcd"、等长。
- `extractUrl` 正则：纯 URL、带前缀文本、多 URL（取第一）、无 URL、URL 编码字符。
- `AppActionCaptureActivity.shouldCapture(intent)` 决策表：http/https/file 拒绝、weixin/tbopen/intent 接受、SEND text/plain 接受、其他 MIME 拒绝。
- `TaskRepository.observeTasksWithAppAction()` SQL：含 APP 跳转的任务返回、不含的不返回、归档的不返回、多个附加模块去重。
- `TaskInputViewModel.applyExternalCapture` 状态：草稿字段写入、`consumePendingOpenAppActionSheet()` 仅触发一次、`consumePendingAppActionPrefill()` 仅返回一次。

UI / 系统行为（真机验证）：
- Chrome 内点击 `intent://...#Intent;scheme=weixin;...end` → 选择器出现 JustNow → 选中 → 进 CapturePickerActivity。
- Chrome 内点击普通 http 链接 → 选择器**不出现** JustNow。
- 文件管理器分享 URL 文本 → JustNow 出现在分享选择器 → 选中 → 进 CapturePickerActivity（URL 流）。
- 任意 APP 分享纯文本 → JustNow 出现在分享选择器 → 选中 → 进 CapturePickerActivity（笔记流，仅底部「新建含笔记」按钮可用——实现期可选择隐藏/禁用其他两个）。
- 选已有任务后保存 → 任务执行时点击新增的 APP 跳转项 → 唤起目标 APP（已装 APP 的稳定 scheme 验证）。

## 影响范围

| 文件 / 资源 | 操作 |
|------------|------|
| `AndroidManifest.xml` | 新增 `AppActionCaptureActivity` 声明与 2 个 intent-filter |
| `ui/appactioncapture/AppActionCaptureActivity.java` | 新建（分流） |
| `ui/appactioncapture/CapturePickerActivity.java` | 新建（任务选择/新建） |
| `ui/appactioncapture/CaptureTaskListAdapter.java` | 新建（列表适配器，可考虑复用 SearchResultAdapter） |
| `res/layout/activity_capture_picker.xml` | 新建 |
| `res/values/strings.xml` + 3 个 values-zh-* | 新增 10 个字符串 |
| `data/repository/TaskRepository.java` | 新增 `observeTasksWithAppAction()` |
| `data/dao/TaskDao.java` | 新增对应查询方法 |
| `ui/taskinput/TaskInputActivity.java` | 解析新 extras，调用 `applyExternalCapture` |
| `ui/taskinput/TaskInputViewModel.java` | 新增 `applyExternalCapture`、`consumePendingOpenAppActionSheet`、`consumePendingAppActionPrefill`、`mPendingAppActionPrefill` 字段 |
| `ui/taskinput/TaskEditFragment.java` | 观察 `consumePendingOpenAppActionSheet`，自动打开 sheet |
| `ui/taskinput/TaskInputAppActionSheet.java` | 消费 prefill 插入顶部；列表项增加编辑按钮 |
| `ui/taskinput/AddAppActionDialog.java` | 扩展构造支持 `existing` 编辑模式 |
| `res/layout/sheet_app_action_editor.xml` | item 增加编辑按钮 |
| `res/layout/dialog_add_app_action.xml`（如已分离） | 编辑模式只读 APP 选择区域 |

## 验收

- Chrome 内点击带 `weixin://` 的网页跳转按钮，JustNow 出现在选择器；选中后能完成"加进已有任务"和"新建任务"两条路径，保存后任务执行时一键唤起微信对应入口。
- 系统相册分享一张图片的文本说明（含 URL）到 JustNow，URL 被识别走捕获流，能加进任务的 APP 跳转项。
- 文件管理器分享纯文件名字符串到 JustNow，非 URL 走笔记流，新建任务正文为该字符串。
- 浏览器内点 http 链接时 JustNow 不出现在"打开方式"选择器。
- 已有 APP 跳转项支持编辑描述，编辑保存后任务详情显示新描述。
