# 跨应用 Intent 捕获模块

> 设计文档：[2026-06-07-launcher-intent-capture-design.md](../docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md)

# 阶段规划、决策记录

## 定位和功能描述

接管浏览器跳转 APP 的自定义 scheme 与系统文本分享，把 Intent 装进 JustNow 任务的"APP 跳转"附加模块，或作为新建任务的笔记内容。

入口分两条：

- **scheme 嗅探**：`AppActionCaptureActivity` 注册非 http(s) 自定义 scheme 的 `BROWSABLE VIEW`，用户在浏览器选择器里选 JustNow 即捕获完整 Intent。
- **ACTION_SEND 分享**：同一 Activity 注册 `text/plain` SEND，区分 URL（走捕获流）和非 URL（走笔记流）。

捕获后跳 `CapturePickerActivity` 选已有任务或新建。选已有任务 → 跳 `TaskInputActivity` 编辑态自动打开 APP 跳转 sheet 并在顶部预填该 Intent。

## 整体规划和决策

### 阶段 1 — Manifest 注册与分流 Activity
- [x] `AppActionCaptureActivity` 新建：无 UI，`onCreate` 分流即 finish
- [x] Manifest 注册 VIEW + BROWSABLE（`scheme="*"`，运行期黑名单 http/https；实测污染浏览器选择器则回退静态枚举）
- [x] Manifest 注册 SEND `text/plain`
- [x] 工具方法：`pickShorterTitle`、`extractUrl`（`android.util.Patterns.WEB_URL`）、`resolveReferrer`（`Activity.getReferrer()`，scheme `android-app` 取 host）、`resolveAppLabel`

### 阶段 2 — CapturePickerActivity
- [x] 布局 `activity_capture_picker.xml`：Toolbar + 搜索框 + RecyclerView + 底部两按钮上下堆叠
- [x] 按钮样式同 `btn_next_quadrant`（MaterialButton + cornerRadius 16dp + textAppearance.JustNow.Body + wrap_content 居中）
- [x] `TaskRepository.getTasksWithAppActionSync()` + 对应 DAO 查询（带 APP 跳转模块、未归档）
- [x] 搜索框复用 `TextTokenizer` 多 token AND 客户端过滤
- [x] 列表项复用 `item_search_result.xml` 渲染风格
- [x] 三个出口的 `TaskInputActivity` 启动参数封装

### 阶段 3 — TaskInputActivity 接收预填
- [x] 新增 extras 解析：`EXTRA_LOAD_TASK_ID` / `EXTRA_DRAFT_TASK_*` / `EXTRA_OPEN_APP_ACTION_SHEET` / `EXTRA_PREFILL_APP_ACTION_*`
- [x] 沿用 `loadTaskForEdit` + `postDelayed(150ms)` 既有异步模式（同 TaskInputFragment ReminderDetail 跳转路径）
- [x] `TaskInputViewModel.applyDraftPrefill` 注入草稿态字段
- [x] `stagePendingAppActionPrefill` / `consumePendingOpenAppActionSheet` / `consumePendingAppActionPrefill` 一次性消费语义
- [x] `TaskEditFragment.maybeAutoOpenAppActionSheet` 观察 pending sheet 自动打开
- [x] `TaskInputAppActionSheet` `onCreateDialog` 消费 prefill 插入列表顶部
- [x] `TaskInputFragment` 检测外部捕获 extras 跳过首屏键盘弹起

### 阶段 4 — APP 跳转 sheet 项编辑能力
- [x] `item_app_action.xml` item 在删除按钮左侧增加编辑按钮
- [x] `TaskInputAppActionSheet.showEditDialog` 复用 `dialog_app_action_add.xml`：APP 选择区只读，仅 hint 可改
- [x] 编辑回写不改 packageName / deepLink

### 阶段 5 — 字符串资源 + 编译验证
- [x] 4 语言新增 11 个字符串（含 `s_capture_*` 与 `s_app_action_edit`）
- [x] `compileDebugJavaWithJavac` 通过
- [ ] 真机验证（Chrome 自定义 scheme 跳转、文件管理器 URL 分享、非 URL 文本分享、已有 APP 项编辑）

### 后续待决策
- 阶段 1-5 实际可用面窄（大厂 H5 普遍用 `intent://...;package=` 或 App Link autoVerify，绕过选择器），现产生价值的主要是 SEND text/plain 路径 + APP 跳转 sheet 项编辑能力
- WebView 拦截方案已移到独立调研项目 `WebViewProbe`，本项目不再维护。视该项目验证结论决定是否回归主流程

### 已确认规则

**入口范围**：
- 非 http(s) 自定义 scheme（VIEW + BROWSABLE）
- `text/plain` SEND（URL 走捕获，非 URL 走笔记，仅入口 3 可用）

**标题策略**：
- `EXTRA_SUBJECT` + `EXTRA_TITLE` 都有取**字符串短**的，单个用单个
- 缺失：
  - SEND 笔记流（任务标题）：兜底 `<来源 APP 名> 分享`
  - 捕获 Intent 入口 1（sheet 项 hint）/ 入口 2（任务标题）：留空

**任务列表过滤**：所有带 APP 跳转附加模块、未归档的任务；搜索框仅在该列表分词过滤。

**数据写入**：`TaskAppAction.deepLink` 存 `Intent.toUri(URI_INTENT_SCHEME)` 完整 URI（schema 已就绪，无 migration）；`packageName` 取 `Intent.getPackage()` → `getComponent()` → `PackageManager.resolveActivity`。

**编辑能力扩展**：所有 APP 跳转项点击可改 hint；APP 本身不可改。

### 文件结构

```
ui/appactioncapture/
├── AppActionCaptureActivity.java   # Manifest 入口，分流
├── CapturePickerActivity.java       # 任务选择 + 新建出口
└── CaptureTaskListAdapter.java      # 列表适配器

res/layout/
└── activity_capture_picker.xml

修改：
- AndroidManifest.xml（新增 Activity 声明 + 2 个 intent-filter）
- ui/taskinput/TaskInputActivity.java（解析新 extras + 跳过录入首屏）
- ui/taskinput/TaskInputViewModel.java（applyExternalCapture / pending 字段）
- ui/taskinput/TaskEditFragment.java（自动打开 sheet）
- ui/taskinput/TaskInputAppActionSheet.java（顶部预填 + 项编辑）
- ui/taskinput/AddAppActionDialog.java（编辑模式参数）
- res/layout/sheet_app_action_editor.xml（item 编辑按钮）
- data/dao/TaskDao.java（observeTasksWithAppAction 查询）
- data/repository/TaskRepository.java（同上）
- res/values{,-zh-rCN,-zh-rTW,-zh-rHK}/strings.xml（10 个新字符串）
```

# 研究发现、技术决策

> 详见：[设计文档背景与非目标](../docs/superpowers/specs/2026-06-07-launcher-intent-capture-design.md)

### 技术现实判定（2026-06-07）

- Android 没有静默截获其他 APP 跨 APP 跳转 Intent 的标准 API；`ShortcutManager.requestPinShortcut()` 不向第三方 APP 暴露事件，仅调用方自己的 IntentSender 能收到成功回调，且取消无回调
- `LauncherApps` API 只对默认 Launcher 角色生效，普通 APP 调不到
- 大厂私有"分享菜单"（微信/抖音/腾讯视频）不走系统 ACTION_SEND，本 APP 触达范围仅限走系统选择器的来源（Chrome、文件管理器、系统相册、部分海外 APP、`intent://` 浏览器跳转）
- "添加到桌面"语义入口（小程序 appId / 频道 ID / 公众号 ghid）通常无短时签名 → 捕获后可长期使用；带商品/订单/直播间深路径的 Intent 才需要担心 token 过期
- 内置 WebView 方案技术可行但用户成本更高（每次需把 URL 搬进 APP）+ 工程量大 + 兼容性差，舍弃
- 自建 Launcher 方案体验灾难（单次捕获 15+ 步）+ Google Play 政策风险，舍弃
- 现成同类工具（Intent Intercept 等）走的都是"Manifest 注册 + 选择器"路线，证明这是事实标准交互

### scheme="*" 匹配范围（2026-06-07）

- `<data android:scheme="*"/>` 会同时把本 APP 列入 http/https 链接的"打开方式"选择器，可能污染用户浏览
- 防御：`AppActionCaptureActivity.onCreate` 第一行检查 `data.getScheme()`，命中 http/https 直接 finish
- 兜底：若 Android 框架自身仍把本 APP 显示在浏览器选择器（实测验证），改为静态枚举常见非 http(s) scheme（`weixin/tbopen/taobao/openapp.jdmobile/pinduoduo/alipays/snssdk*/...`）。设计文档允许此回退不视为设计变更

### SEND text/plain 不能仅匹配 URL（2026-06-07）

- Android 无"仅 URL 文本"的 MIME 类型，`text/uri-list` 几乎无 APP 使用
- 决策：注册 `text/plain` 全量接收，进 APP 后用 `android.util.Patterns.WEB_URL` 判断；非 URL 走笔记流（默认标题 `<来源 APP 名> 分享`）

### TaskAppAction schema 已就绪（2026-06-07）

- `TaskAppAction.deepLink` 字段早期已定义为存可启动 URI，`ReminderDetailActivity` 已用 `Intent.parseUri(action.deepLink, 0)` 启动
- 本期直接 `Intent.toUri(URI_INTENT_SCHEME)` 写入即可，**零 schema migration**

### 启动失败兜底（2026-06-07）

- Intent URI 失效场景：APP 卸载 / 入口下线 / APP 升级换 scheme / 含 token 的非"加桌"类 Intent 过期
- 本期不做时效预警，启动时 `startActivity` 抛 `ActivityNotFoundException` → toast `s_capture_launch_failed`
- 对"加桌"类入口（永久身份）失效率本身就低，不必复杂兜底

### Manifest 选择器路径实际触达面窄（2026-06-07）

实测发现主流大厂 H5 跳转 APP 链路普遍**绕过系统选择器**：

- `intent://...;package=com.xxx;...end` 带 `package=` → 系统直接送给该 package，不弹选择器
- `https://...` + 目标 APP 已 `autoVerify` 通过 → 浏览器内直接给目标 APP
- 自定义 scheme 唯一归宿（如 `weixin://`、`taobao://`）→ 只有注册该 scheme 的 APP，选择器无候选

剩余能截到的窄缝：`intent://...;scheme=xxx;...end` **不带 package=** 且有多 APP 注册同 scheme，实际场景极少。

**结论**：本期 Manifest 选择器路径价值低，**SEND text/plain 分享路径**（Chrome 分享、文件管理器分享、相册分享）仍有效，**APP 跳转 sheet 项编辑能力**作为独立改进保留。WebView 拦截方案已剥离到独立调研项目 `WebViewProbe`（同级 `/mnt/d/Documents/AndroidStudioProjects/WebViewProbe`），本项目不再维护其代码与文档。
