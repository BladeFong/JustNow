# 跨应用 Intent 捕获与任务关联设计

日期：2026-06-07（2026-06-08 修订）

## 修订记录

### 2026-06-08 — 笔记分享模块补齐 + UI 收尾

原 6/7 设计中"入口 3 新建含笔记任务"实现为"灌 markdown 任务正文"，与"捕获跳转入口"语义脱节。补齐如下：

1. **新增"笔记分享"附加模块**（第九节）：与"APP 操作"模块并列，结构同 `TaskAppAction`（`deepLink` + `hint`），独立 DB 表 `task_note_shares`、独立 sheet、独立详情页区块。
2. **入口 3 在 SEND URL 流改为"新建任务 + 笔记分享 sheet 预填"**（第三节"点击底部「+笔记分享任务」"段）；SEND 纯文本流保持旧行为不变。
3. **底部两按钮上下堆叠 → 横向并排**（第三节布局表 + 按钮样式段）。按钮文案精简：`+APP操作任务` / `+笔记分享任务`。
4. **Toolbar 标题改为"选择APP操作任务"，字体颜色对齐项目 `colorOnPrimary` 白色**（第三节 Toolbar 段）。
5. **任务项渲染统一**（第十节）：单行 `30分钟 #<标签> <任务标题>` 格式，CapturePicker 与 TaskInputFragment 搜索两边同时改。

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
| Toolbar | 标题 `s_capture_picker_title`「选择APP操作任务」；`app:titleTextColor="?attr/colorOnPrimary"` 与项目其他 Toolbar 统一为白色；返回箭头关闭页面（取消捕获） |
| 搜索框 | `MaterialCardView` 包 `EditText`（无图标），placeholder "搜索任务"；去 `strokeColor`，背景填充浅灰底色与列表项卡片区分（实现期可用 `@color/divider` 或新增 `@color/search_box_bg`），保留 8dp 圆角 |
| RecyclerView | 任务列表，权重 1 撑满 |
| 底部按钮区 | 两个 `MaterialButton` 横向并排，外层 horizontal LinearLayout 包裹，各 `layout_width=0dp + layout_weight=1`：「+APP操作任务」 / 「+笔记分享任务」 |

**按钮样式**：沿用项目规范，同 `fragment_task_edit.xml` 的 `btn_next_quadrant`：
- `MaterialButton` 默认（filled）样式
- `app:cornerRadius="16dp"`
- `android:textAppearance="@style/TextAppearance.JustNow.Body"`
- 各按钮 `android:layout_width="0dp"` + `android:layout_weight="1"`，外包 horizontal LinearLayout 容器
- 容器 `layout_marginHorizontal="12dp"` + `layout_marginBottom="12dp"`；两按钮间 `layout_marginHorizontal="4dp"` 间隔

> 按钮文案按 `mMode + mAllowNoteEntry` 动态切换：`MODE_NOTE`（SEND 纯文本）下右按钮保留原文案 `s_capture_picker_new_note`「新建含笔记任务」对应原灌 markdown 行为；其他场景用 `s_capture_picker_new_note_share`「+笔记分享任务」。

行为：

**任务列表数据源**：`TaskRepository.observeTasksWithAppAction()` —— SQL `SELECT * FROM tasks WHERE archived = 0 AND id IN (SELECT DISTINCT task_id FROM task_app_actions) ORDER BY ...`。排序复用主界面默认顺序（创建时间倒序或现有规则，沿用 Repository 已有方法即可）。

**搜索过滤**：搜索框文本变化时，对当前列表做客户端分词过滤（复用 `TextTokenizer` + 多 token AND）。不调全文检索 API，因为列表已经预过滤过附加模块条件。

**列表项渲染**：单行格式 `30分钟 #<标签> <任务标题>`，详见第十节"任务项渲染统一"。TaskInputFragment 搜索结果同步采用同一布局与格式。

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

**点击底部「+笔记分享任务」/「新建含笔记任务」**（入口 3）：

按 `mMode + mAllowNoteEntry` 分流：

- **SEND URL 流（`MODE_CAPTURE` + `mAllowNoteEntry=true`）** — 按钮文案 `+笔记分享任务`，行为：
  - `startActivity(TaskInputActivity)` 携带：
    - `EXTRA_DRAFT_TASK_TITLE = shortTitle 或 ""`
    - `EXTRA_DRAFT_TASK_TAG_NAME = referrerLabel 或 null`
    - `EXTRA_DRAFT_OPEN_NOTE_SHARE_SHEET = true`
    - `EXTRA_PREFILL_NOTE_SHARE_URI = capturedIntentUri 或 URL`
    - `EXTRA_PREFILL_NOTE_SHARE_HINT = shortTitle 或 null`
  - TaskInputActivity 跳过录入首屏 → `TaskEditFragment` 自动打开**笔记分享 sheet** 并预填首项
  - **不发** `EXTRA_DRAFT_TASK_MARKDOWN`

- **SEND 纯文本流（`MODE_NOTE`）** — 按钮文案保持原 `新建含笔记任务`，行为不变（已实现的"灌 markdown 任务正文"流程不动）：
  - `EXTRA_DRAFT_TASK_TITLE = shortTitle 或 "<referrerLabel>分享"`
  - `EXTRA_DRAFT_TASK_TAG_NAME = referrerLabel 或 null`
  - `EXTRA_DRAFT_TASK_MARKDOWN = 分享文本`
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
| `s_capture_picker_title` ✏️ | Pick app-action task | 选择APP操作任务 | 選擇APP操作任務 | 選擇APP操作任務 |
| `s_capture_picker_search_hint` | Search tasks | 搜索任务 | 搜尋任務 | 搜尋任務 |
| `s_capture_picker_new_app_action` ✏️ | +App action task | +APP操作任务 | +APP操作任務 | +APP操作任務 |
| `s_capture_picker_new_note` | New task with note | 新建含笔记任务 | 新建含筆記任務 | 新建含筆記任務 |
| `s_capture_share_title_fallback` | %1$s share | %1$s 分享 | %1$s 分享 | %1$s 分享 |
| `s_capture_launch_failed` | Failed to launch target app | 启动目标 APP 失败 | 啟動目標 APP 失敗 | 啟動目標 APP 失敗 |
| `s_app_action_edit` | Edit | 编辑 | 編輯 | 編輯 |
| `s_capture_picker_new_note_share` 🆕 | +Note share task | +笔记分享任务 | +筆記分享任務 | +筆記分享任務 |
| `s_note_share_module_title` 🆕 | Note share | 笔记分享 | 筆記分享 | 筆記分享 |
| `s_note_share_module_empty` 🆕 | No note shares yet | 暂无笔记分享 | 尚無筆記分享 | 尚無筆記分享 |
| `s_note_share_module_add` 🆕 | Add | 添加 | 新增 | 加入 |
| `s_note_share_add_dialog_title` 🆕 | Add note share | 添加笔记分享 | 新增筆記分享 | 加入筆記分享 |
| `s_note_share_add_link_hint` 🆕 | Paste link or Intent URI | 粘贴链接或 Intent URI | 貼上連結或 Intent URI | 貼上連結或 Intent URI |
| `s_note_share_add_hint_label` 🆕 | Description (optional) | 描述（可选） | 描述（可選） | 描述（可選） |
| `s_note_share_edit` 🆕 | Edit | 编辑 | 編輯 | 編輯 |

> 表中 ✏️ 标记 = 2026-06-08 修订文案，🆕 = 2026-06-08 新增。港台用语差异已按规范分别处理（"搜索→搜尋"、"保存→儲存"、"添加→新增/加入"、"粘贴→貼上"、"链接→連結"）。

### 八、可选行为：捕获后回到来源 APP

不在本期范围。讨论中提到"捕获后转发原 Intent 回浏览器"用户体验更顺滑，但牵涉到任务保存时序（用户在 picker 页是否会取消？保存中 finish 会丢数据？），评估后**本期跳过**：捕获完成后停在 JustNow 编辑页，用户保存或取消后自行按 home / 返回离开。

### 九、笔记分享附加模块（2026-06-08 追加）

#### 9.1 定位

与已有"APP 操作"附加模块并列的新增模块类别，承载"可跳转的笔记入口"——典型来源是浏览器分享的网页文章 URL、笔记类 APP 的深链 Intent URI 等。结构同 `TaskAppAction`（`deepLink` + `hint`），独立 DB 表、独立 sheet、独立详情页区块。

入口：
- CapturePicker SEND URL 流"+笔记分享任务"按钮自动预填（见第三节入口 3）
- 任务编辑页"附加模块"选择器新增的"笔记分享"项手动添加

#### 9.2 数据层

新建表 `task_note_shares`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER PK autoIncrement | |
| `task_id` | INTEGER NOT NULL | FK → `tasks.id`，`onDelete=CASCADE`，加索引 |
| `order_index` | INTEGER NOT NULL | sheet 内排序 |
| `deep_link` | TEXT NOT NULL | 完整 URL（`http(s)://...`）或 `Intent.toUri(URI_INTENT_SCHEME)` |
| `hint` | TEXT | 可选描述 |

`AppDatabase` 版本 v5 → v6，migration 仅 `CREATE TABLE` + 索引，零数据风险。

新建实体 `TaskNoteShareEntity` / `TaskNoteShareDao`（接口与 `TaskAppActionDao` 平行：`getByTaskIdSync` / `deleteByTaskIdSync` / `insertAllSync` / `getTasksWithNoteShareSync` 备用）。

`TaskRepository` 加同步方法：`getNoteSharesByTaskId` / `insertNoteShares` / `deleteNoteSharesByTaskId` / `hasNoteShares`，与 APP 操作那套 1:1 对应。

#### 9.3 UI 层

- 任务编辑页"附加模块"菜单加项："笔记分享"（type=`note_shares`）。沿用现有模块选择器交互。
- 新建 `TaskInputNoteShareSheet` —— 仿 `TaskInputAppActionSheet`：
  - 顶部"添加"按钮触发添加对话框
  - 列表 item 增加编辑/删除按钮（编辑允许改 `deepLink` 与 `hint`，不像 APP 操作那样只能改 `hint`——因为笔记分享没有"已绑定 APP"概念）
  - 入场时消费 `consumePendingNoteSharePrefill()` 把捕获项插入顶部
- 新建 `dialog_note_share_add.xml`：
  - 链接输入框（粘贴 URL 或 Intent URI，`inputType=textUri`）
  - 描述输入框（hint，`inputType=text`，可选）
  - 确认/取消按钮
- 新建 `item_note_share.xml`：
  - 主标题 `hint`（空时回退显示 `deepLink` 摘要）
  - 副信息 `deepLink` 摘要小字（`textAppearance.JustNow.Caption`，`maxLines=1` 末尾省略）
  - 末尾编辑 + 删除两个图标按钮

#### 9.4 ViewModel

`TaskInputViewModel` 加：

```java
private String mPendingNoteSharePrefillUri;
private String mPendingNoteSharePrefillHint;
private boolean mPendingOpenNoteShareSheet;

public void stagePendingNoteSharePrefill(String uri, String hint, boolean openSheet);
public boolean consumePendingOpenNoteShareSheet();      // 一次性
public TaskNoteShare consumePendingNoteSharePrefill();  // 一次性
public boolean hasEffectiveNoteShares();                // deepLink 非空即有效
```

`saveTask` 写库时同步落 `task_note_shares`，删除旧记录 + 重写全量（同 APP 操作）。

`TaskEditFragment` 加 `maybeAutoOpenNoteShareSheet`，位置与 `maybeAutoOpenAppActionSheet` 平行；二者互斥（同次启动只可能有一个 pending）。

#### 9.5 详情页

`ReminderDetailActivity` 加笔记分享区块，渲染逻辑与 APP 操作区块一致：
- 区块标题：`s_note_share_module_title`
- 列表渲染 hint + deepLink 摘要
- 点击项 `Intent.parseUri(deepLink, 0)` → `startActivity`；URL 自动包装为 `ACTION_VIEW`
- 启动失败统一 toast `s_capture_launch_failed`
- 空模块不渲染区块

#### 9.6 TaskInputActivity 新增 extras

```java
public static final String EXTRA_DRAFT_OPEN_NOTE_SHARE_SHEET = "extra_draft_open_note_share_sheet";
public static final String EXTRA_PREFILL_NOTE_SHARE_URI = "extra_prefill_note_share_uri";
public static final String EXTRA_PREFILL_NOTE_SHARE_HINT = "extra_prefill_note_share_hint";
```

`onCreate` 检测后调 `viewModel.stagePendingNoteSharePrefill(uri, hint, openSheet)`，跳过录入首屏。`TaskInputFragment` 已有的 `hasCaptureExtras` 检测扩展加入这三个 key。

### 十、任务项渲染统一（2026-06-08 追加）

#### 10.1 背景

当前 `item_search_result.xml` 是两行（`Body` 标题 + `Caption` 副信息）卡片，CapturePicker 列表与 TaskInputFragment 搜索都用它。实际上 `task.detail` 多数为空，渲染为"标题占一行 + 空 caption 行"，视觉怪。且搜索框与列表卡片同 `divider` 描边，区分弱。

#### 10.2 布局调整

`item_search_result.xml` 改单行：
- 移除 `text2` TextView
- `text1` 单行 `Body`，`maxLines=1` + `ellipsize=end`
- 卡片 stroke + 圆角不动

#### 10.3 单行文本格式

`30分钟 #<标签> <任务标题>`（段间空格，"30分钟"内无空格）：
- `task.focusMinutes == 0` 省略时间段
- `task.tagId == null` 省略 `#<标签>` 段
- 搜索高亮仅作用于 `<任务标题>` 段

`TaskEntity` 已有 `tagId` 单标签字段；标签名通过 `TagRepository.getTagsMap()`（或新增 `getAllTagsSync`）查 `Map<Long,String>`。

#### 10.4 数据流

- **CapturePickerActivity.loadTasks**：background thread 同时查 `getTasksWithAppActionSync` 与 `getAllTagsSync`，把 `Map<Long,String> tagNames` 一同传给 Adapter。
- **TaskInputViewModel**：暴露 `LiveData<Map<Long,String>> tagNamesMap`，与 `searchResults` 同时观察；`SearchResultAdapter` 持有 `tagNames` 引用，搜索结果更新时一并刷新。
- 渲染由 Adapter 调用 `formatTaskLine(task, tagNames)` 统一拼接，避免双份逻辑。

#### 10.5 搜索框与列表项视觉区分

两边搜索框（`activity_capture_picker.xml` + `fragment_task_input.xml`）调整：
- `MaterialCardView` 去 `app:strokeColor` / `app:strokeWidth`
- 背景换浅灰填充（`@color/divider` 同色或新增 `@color/search_box_bg`）
- 保留 8dp 圆角，呈"输入框"感

列表项卡片样式不动，stroke 卡片在浅灰搜索框背景上对比清晰。

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
| `res/values/strings.xml` + 3 个 values-zh-* | 新增 10 个字符串（6/7）；2026-06-08 修订/新增 9 条（`s_capture_picker_title` / `s_capture_picker_new_app_action` 改文案，新增 7 条 `s_note_share_*` + `s_capture_picker_new_note_share`） |
| `data/repository/TaskRepository.java` | 新增 `observeTasksWithAppAction()`（6/7）；2026-06-08 加 `getNoteSharesByTaskId` / `insertNoteShares` / `deleteNoteSharesByTaskId` / `hasNoteShares`；按需 `getAllTagsSync` |
| `data/dao/TaskDao.java` | 新增对应查询方法 |
| `ui/taskinput/TaskInputActivity.java` | 解析新 extras，调用 `applyExternalCapture`；2026-06-08 加 `EXTRA_DRAFT_OPEN_NOTE_SHARE_SHEET` / `EXTRA_PREFILL_NOTE_SHARE_*` 解析 |
| `ui/taskinput/TaskInputViewModel.java` | 新增 `applyExternalCapture`、`consumePendingOpenAppActionSheet`、`consumePendingAppActionPrefill`、`mPendingAppActionPrefill` 字段；2026-06-08 加 `stagePendingNoteSharePrefill` / `consumePendingOpenNoteShareSheet` / `consumePendingNoteSharePrefill` / `hasEffectiveNoteShares` + `tagNamesMap` LiveData |
| `ui/taskinput/TaskEditFragment.java` | 观察 `consumePendingOpenAppActionSheet`，自动打开 sheet；2026-06-08 加 `maybeAutoOpenNoteShareSheet` |
| `ui/taskinput/TaskInputAppActionSheet.java` | 消费 prefill 插入顶部；列表项增加编辑按钮 |
| `ui/taskinput/AddAppActionDialog.java` | 扩展构造支持 `existing` 编辑模式 |
| `res/layout/sheet_app_action_editor.xml` | item 增加编辑按钮 |
| `res/layout/dialog_add_app_action.xml`（如已分离） | 编辑模式只读 APP 选择区域 |
| **2026-06-08 新增** | |
| `data/entity/TaskNoteShareEntity.java` | 新建 |
| `data/dao/TaskNoteShareDao.java` | 新建 |
| `data/db/AppDatabase.java` | v5 → v6 + migration（`CREATE TABLE task_note_shares`） |
| `ui/taskinput/TaskInputNoteShareSheet.java` | 新建（仿 `TaskInputAppActionSheet`） |
| `res/layout/dialog_note_share_add.xml` | 新建 |
| `res/layout/item_note_share.xml` | 新建 |
| `ui/reminderdetail/ReminderDetailActivity.java` | 加笔记分享区块渲染 + 点击启动 + 失败 toast |
| `ui/appactioncapture/CapturePickerActivity.java` | 底部布局并排；按钮文案动态切换；入口 3 URL 流改发笔记分享 extras |
| `res/layout/activity_capture_picker.xml` | 底部 horizontal LinearLayout 包两按钮；Toolbar `titleTextColor`；搜索框去 stroke 改填充背景 |
| `res/layout/item_search_result.xml` | 改单行（移除 text2） |
| `res/layout/fragment_task_input.xml` | 搜索框去 stroke 改填充背景 |
| `ui/taskinput/TaskInputFragment.java` | `SearchResultAdapter` 持 tagNames Map；渲染统一格式 |
| `ui/appactioncapture/CapturePickerActivity.java` | TaskAdapter 渲染统一格式（同上）|

## 验收

- Chrome 内点击带 `weixin://` 的网页跳转按钮，JustNow 出现在选择器；选中后能完成"加进已有任务"和"新建任务"两条路径，保存后任务执行时一键唤起微信对应入口。
- 系统相册分享一张图片的文本说明（含 URL）到 JustNow，URL 被识别走捕获流，能加进任务的 APP 跳转项。
- 文件管理器分享纯文件名字符串到 JustNow，非 URL 走笔记流，新建任务正文为该字符串。
- 浏览器内点 http 链接时 JustNow 不出现在"打开方式"选择器。
- 已有 APP 跳转项支持编辑描述，编辑保存后任务详情显示新描述。

**2026-06-08 修订追加：**

- Chrome 分享一条 https 网页链接到 JustNow，CapturePicker 出现"+笔记分享任务"按钮；点击后跳新建任务，自动打开笔记分享 sheet，顶部预填该 URL 项；保存后任务详情笔记分享区块可点击唤起浏览器打开该网页。
- SEND 纯文本（无 URL）到 JustNow，CapturePicker 右按钮文案保持"新建含笔记任务"，点击后行为不变（灌任务正文，不打开笔记分享 sheet）。
- CapturePicker 底部两按钮横向并排，文案 `+APP操作任务` / `+笔记分享任务` 或 `新建含笔记任务`（按 mode 切换），不溢出。
- CapturePicker Toolbar 标题 `选择APP操作任务` 字体为白色，与项目其他 Toolbar 视觉一致。
- CapturePicker 列表与 TaskInputFragment 搜索结果均为单行 `30分钟 #<标签> <任务标题>` 格式（缺字段省略对应段）；搜索框背景浅灰填充，与下方任务卡片视觉清晰区分。
- 任务编辑页"附加模块"菜单可见"笔记分享"项；选中后打开独立 sheet，可手动粘贴链接 / Intent URI + 描述添加项。
- 笔记分享 item 支持编辑（链接 + 描述均可改）与删除。
- 笔记分享 deepLink 无 APP 能处理时启动失败 toast `s_capture_launch_failed`。
