# intent-capture 进度日志

### 2026-06-08 — 笔记分享模块补齐 + UI 收尾

> 设计文档：[../docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md](../docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md)（6/8 修订追加第九、十节）

**状态**：`compileDebugJavaWithJavac` 通过，待真机验证。

实现要点：
- 新增"笔记分享"附加模块（`task_note_shares` 表，AppDatabase v5→v6 migration）：`TaskNoteShare` 实体 + `TaskNoteShareDao` + `TaskNoteShareRepository`，数据层与 APP 操作完全对齐
- `TaskInputNoteShareSheet` + `item_note_share.xml`：独立 sheet，仅展示捕获流自动创建的项（只读 + 删除），无手动添加/编辑入口
- 任务编辑页"附加模块"选择器加入"笔记分享"文档图标按钮（`ic_module_note_share.xml`）；`TaskEditFragment` 新增 `maybeAutoOpenNoteShareSheet`
- `TaskInputViewModel` 加 `stagePendingNoteSharePrefill` / `consumePendingOpenNoteShareSheet` / `consumePendingNoteSharePrefill` / `hasEffectiveNoteShares` + `tagNamesMap` LiveData
- `TaskInputActivity` 解析 3 个笔记分享 extras（`EXTRA_DRAFT_OPEN_NOTE_SHARE_SHEET` / `EXTRA_PREFILL_NOTE_SHARE_URI` / `EXTRA_PREFILL_NOTE_SHARE_HINT`）
- `ReminderDetailActivity` 加笔记分享区块（标题+列表+点击 startActivity+失败 toast）
- CapturePicker 底部两按钮并排（`weight=1`）；Toolbar 白色标题；按钮文案动态切换（SEND URL 流 `+笔记分享任务` → `onNewWithNoteShare`；MODE_NOTE 保持原行为）
- 任务项渲染统一：`item_search_result.xml` 改单行 `30分钟 #<标签> <任务标题>` 格式；搜索框去 stroke 改浅灰填充
- 捕获流保存成功后引导用户留在 JustNow（`mFromCapture` 标记 → `QuadrantFragment` 回调里 `startActivity(MainActivity)` 再 `finish()`）

4 语言新增 9 条字符串（含 `s_note_share_*` / `s_module_note_share*`），改 2 条。

### 2026-06-07 — WebView 探针剥离到独立项目

> 关联调研项目：`/mnt/d/Documents/AndroidStudioProjects/WebViewProbe`

**状态**：本项目已删除 `WebViewProbeActivity` / `activity_webview_probe.xml` / `WebViewProbeLauncher` activity-alias 与 4 个 `s_probe_*` 字符串（4 语言）。代码与文档完整迁到独立 demo 项目 `WebViewProbe`（同级目录），含 README 记录背景 / UA 伪装 / 拦截后冻结页面 / 腾讯视频可行性验证 / 合规性评估。本项目不再维护 WebView 拦截方案。

### 2026-06-07 — 跨应用 Intent 捕获实现完成

> 设计文档：[../docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md](../docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md)
> 详见：[intent-capture.md](intent-capture.md)

**状态**：`compileDebugJavaWithJavac` 通过，待真机验证。

实现要点：
- 新建 `AppActionCaptureActivity`：Manifest 注册 VIEW（`scheme="*"` + 运行期黑名单 http/https）+ SEND `text/plain`；无 UI，分流后立即 finish
- 新建 `CapturePickerActivity` + `activity_capture_picker.xml`：Toolbar + 搜索框 + 任务列表 + 底部两按钮上下堆叠（沿用 `btn_next_quadrant` 样式规范）；任务列表来自 `TaskRepository.getTasksWithAppActionSync` 新查询；客户端 `TextTokenizer` 分词过滤
- `TaskInputActivity` 新增 7 个 extras：`EXTRA_LOAD_TASK_ID` / `EXTRA_OPEN_APP_ACTION_SHEET` / `EXTRA_DRAFT_OPEN_APP_ACTION_SHEET` / `EXTRA_PREFILL_APP_ACTION_URI` / `EXTRA_PREFILL_APP_ACTION_HINT` / `EXTRA_DRAFT_TASK_TITLE` / `EXTRA_DRAFT_TASK_TAG_NAME` / `EXTRA_DRAFT_TASK_MARKDOWN`；onCreate 时灌入 ViewModel 草稿 + 跳过录入首屏
- `TaskInputViewModel` 加 `applyDraftPrefill` / `stagePendingAppActionPrefill` / `consumePendingOpenAppActionSheet` / `consumePendingAppActionPrefill` 一次性消费语义；`hasEffectiveAppActions` 改为 `packageName` 或 `deepLink` 任一非空视为有效，兼容包名解析失败的纯 Intent URI 捕获项
- `TaskEditFragment.maybeAutoOpenAppActionSheet` 在 `onViewCreated` 末尾消费标记自动打开 sheet
- `TaskInputAppActionSheet` 进入时消费 prefill 顶部插入；item 增加编辑按钮 → `showEditDialog` 复用 `dialog_app_action_add.xml` 但 APP 选择区只读，仅 hint 可改；Adapter 增加 `onEdit` 回调
- `item_app_action.xml` 在删除按钮左侧加 `btn_edit`
- `TaskInputFragment` 在外部捕获 extras 存在时跳过首屏键盘弹起
- `ReminderDetailActivity` APP 跳转 startActivity 失败 → toast `s_capture_launch_failed`
- 4 语言新增 11 个字符串（含 `s_capture_*` 与 `s_app_action_edit`）

零 schema 变更，`TaskAppAction.deepLink` 字段已就绪存完整 `Intent.toUri(URI_INTENT_SCHEME)`。
