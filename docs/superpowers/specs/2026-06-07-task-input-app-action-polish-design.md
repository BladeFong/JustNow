# 添加任务返回与 APP 跳转附加模块优化设计

日期：2026-06-07

## 背景

用户反馈 4 个问题：

1. 添加任务首屏仍有返回箭头和标题问题。
2. 编辑任务页打开 APP 跳转附加模块时有明显停顿，疑似同步获取 APP 列表和图标。
3. APP 跳转附加模块中，选择 APP 后还需要点添加按钮，但当前交互不明显，容易直接点右下角保存。
4. APP 跳转附加模块未添加任何 APP 时保存，任务仍被标记为有附加模块；todo 清单需要同样避免空内容被保存为模块。

## 目标

- 添加任务首屏显示标题“添加任务”，左上角始终显示返回箭头，点击后关闭 `TaskInputActivity` 回到来源页面。
- APP 跳转主 Sheet 打开后不被系统 APP 查询阻塞；加载期间明确显示不可用的添加入口。
- APP 添加流程更明确：主 Sheet 顶部只有“添加”入口，点后用居中 Dialog 完成搜索、选择、描述输入和添加。
- APP 列表按最新添加在顶部展示。
- APP / todo 附加模块都按有效条目决定是否真正启用，空内容不能让任务变成“有附加模块”。

## 非目标

- 不改变任务录入的三步流程。
- 不把附加模块 Sheet 的确认动作改成直接写数据库；数据库写入仍发生在最终保存任务时。
- 不引入跨应用会话的长期 APP 缓存；缓存只为当前编辑流程减少重复加载。
- 不重新设计 todo 清单编辑器交互，仅修正空内容保存语义。

## 设计

### 1. Toolbar 标题和返回箭头

`TaskInputActivity` 保留 `Toolbar + FragmentContainerView` 的垂直布局和现有 edge-to-edge inset 处理。

按 `android-view-systembar` 经验修正 Toolbar 逻辑：

- `setSupportActionBar(mBinding.toolbar)` 后，通过 `getSupportActionBar().setDisplayHomeAsUpEnabled(true)` 显示返回箭头。
- 标题只通过 `getSupportActionBar().setTitle(...)` 设置，不直接调用 `toolbar.setTitle(...)`，避免被 Activity title 覆盖。
- 监听 `NavController.addOnDestinationChangedListener`，按 destination 的 `android:label` 更新标题。
- 不使用 `NavigationUI.setupWithNavController()` 处理这个 Activity 的 Toolbar，因为它会把 start destination 当作顶级页面并隐藏返回箭头。
- Toolbar 返回点击规则：
  - 当前不是首屏时，优先 `mNavController.popBackStack()`。
  - 当前已经是首屏，或无法回退时，调用 `finish()`。

### 2. APP 列表缓存

在 `JustNowApplication` 中持有一个短生命周期缓存对象，例如 `AppLaunchCatalogCache`。

缓存数据：

- `packageName`
- `label`
- `icon`

缓存状态：

- `NOT_LOADED`
- `LOADING`
- `LOADED`
- `FAILED`

加载规则：

- `TaskInputAppActionSheet` 打开时读取 Application 缓存状态。
- `NOT_LOADED` 时触发后台加载，并把“添加”按钮置为禁用加载态。
- `LOADING` 时只等待状态变化，不重复触发查询。
- `LOADED` 时启用“添加”按钮。
- `FAILED` 时按钮显示重试语义，点击后重新加载。
- 查询系统 APP 和加载图标都在后台线程完成，避免阻塞 Sheet 打开。
- `MainActivity.onResume()` 调用缓存 `clear()`，作为回到主界面后的释放点。
- Sheet 关闭、添加 Dialog 关闭、`TaskInputActivity` 销毁时不清缓存。

缓存保存在 Application 中，但不能持有 Activity / Dialog / View 引用。图标使用 application context 的 `PackageManager` 加载，缓存清理后释放引用。

### 3. APP 跳转主 Sheet

主 Sheet 只负责：

- 展示已添加 APP 列表。
- 顶部显示“添加”按钮。
- 右下角确认把当前列表写入 ViewModel 暂存。
- 取消关闭 Sheet，不写入 ViewModel。

主 Sheet 不再直接展示 APP 搜索框和描述输入框。

列表顺序：

- 新添加的 APP 插入列表第 0 位。
- 展示时最新项在顶部。
- 点击确认时，按当前展示顺序从上到下重算 `orderIndex`。

### 4. APP 添加 Dialog

点主 Sheet 顶部“添加”后打开居中 Dialog。

Dialog 内容：

- APP 搜索输入框。
- 描述输入框。
- 取消按钮。
- “添加到列表”按钮。

交互规则：

- Dialog 打开后，自动聚焦 APP 搜索框并弹出输入法。
- 搜索只匹配 Application 缓存中的 `label` 和 `packageName`，不再查询系统 APP。
- 未选中有效 APP 时，“添加到列表”按钮禁用并显示灰色状态。
- 从匹配结果中选中 APP 后，“添加到列表”按钮变为主色可用。
- 点击“添加到列表”后：
  - 生成 `TaskAppAction`。
  - `packageName` 来自所选 APP。
  - `hint` 来自描述输入框，可为空。
  - 插入主 Sheet 列表第 0 位。
  - 关闭 Dialog。

### 5. 空附加模块保存语义

`TaskInputViewModel.saveTask()` 保存前统一归一化模块状态。

规则：

- `checklist`：`mPendingChecklistItems` 为空或没有有效条目时，视为无附加模块。
- `app_actions`：`mPendingAppActions` 为空时，视为无附加模块。
- 只有当前选择的模块类型有至少 1 条有效内容，才写入 `mDraftTask.detailModuleType`。
- 无有效内容时，`mDraftTask.detailModuleType = null`。

编辑已有任务时，还需要清理旧子表：

- 原来是清单模块，保存后清单为空：删除该任务旧清单条目。
- 原来是 APP 跳转模块，保存后 APP 列表为空：删除该任务旧 APP 跳转条目。
- 原模块类型被切换或取消时，也清理不再使用的旧模块子表，避免残留数据下次加载回来。

## 数据流

### APP 缓存加载

1. `TaskInputAppActionSheet` 打开。
2. 从 `JustNowApplication.getAppLaunchCatalogCache()` 获取缓存。
3. 若未加载，触发后台加载。
4. Sheet 根据缓存状态刷新“添加”按钮：
   - 加载中：禁用。
   - 成功：可用。
   - 失败：可重试。
5. 用户点“添加”打开 Dialog。
6. Dialog 使用缓存列表做搜索匹配。

### APP 条目保存

1. Dialog 选中 APP 并添加。
2. 主 Sheet 列表顶部插入 `TaskAppAction`。
3. 主 Sheet 确认时重算 `orderIndex`。
4. `TaskInputViewModel.setPendingAppActions()` 暂存列表。
5. 最终保存任务时，`saveTask()` 按有效条目决定是否写 `detailModuleType` 和子表。

### todo 清单保存

1. 清单 Sheet 解析文本。
2. 只有非空内容行会生成 `TaskChecklistItem`。
3. `TaskInputViewModel.setPendingChecklistItems()` 暂存列表。
4. 最终保存任务时，空列表视为无模块，并清理旧子表。

## 错误处理

- APP 缓存加载失败时，不自动关闭 Sheet；“添加”入口显示重试语义。
- 加载失败不影响取消或确认已有列表。
- 搜索无匹配时，下拉列表为空，“添加到列表”保持禁用。
- 如果已添加的 APP 后续不在缓存中，列表仍显示原有 `packageName` 兜底，不阻止删除或保存。

## 测试

新增或调整定向测试：

- `TaskInputViewModel` 保存空 APP 列表时，任务 `detailModuleType` 为 `null`。
- `TaskInputViewModel` 保存空 todo 清单时，任务 `detailModuleType` 为 `null`。
- 编辑已有 APP 模块并删空保存后，旧 APP 子表被清理。
- 编辑已有 todo 模块并删空保存后，旧清单子表被清理。
- 非空 APP / todo 模块仍正常写入 `detailModuleType` 和子表。

手工验证：

- 添加任务首屏显示“添加任务”和返回箭头，点击返回关闭 Activity。
- 编辑页 / 象限页返回箭头优先回上一步。
- APP 跳转 Sheet 打开后先显示加载态，加载完成后添加按钮可用。
- 点添加弹出居中 Dialog，自动弹出输入法。
- 选中 APP 后“添加到列表”按钮状态明显变化。
- 添加后 Dialog 关闭，新 APP 出现在列表顶部。
- 空 APP / 空 todo 保存后，任务不显示附加模块状态。

验证命令：

```bash
~/gradlew-wsl.sh --no-daemon testDebugUnitTest --tests com.nearby.justnow.ui.taskinput.TaskInputViewModelTest
~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac
```
