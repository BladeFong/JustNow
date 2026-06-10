# 智能展示策略 YAML 配置化 — 实现计划

> 设计文档：[2026-06-10-display-policy-yaml-design.md](2026-06-10-display-policy-yaml-design.md)
> 创建日期：2026-06-10

## 实施原则

- 先落数据模型、解析和校验，再迁移引擎，最后接 UI。
- `DisplayEngine` 不读取 Android `Context`、文件或 SharedPreferences，只接收 `DisplayPolicy`。
- YAML 原文只保存在 App 私有文件；导入、编辑、导出都围绕同一份原文。
- 优先级配置只表达比较器顺序，不引入用户可见权重。
- `focus_max_minutes` 的业务校验必须查询未归档任务中的最大 `focusMinutes`，只阻止会产生冲突的降档。

## P1：依赖、默认 YAML 和策略模型

**Gradle**
- 在 `gradle/libs.versions.toml` 增加 YAML 解析库条目。
- 在 `app/build.gradle.kts` 增加 implementation 依赖。
- 解析库只允许被 `DisplayPolicyParser` 直接使用，避免解析 API 泄漏到业务层。

**Assets**
- 新增 `app/src/main/assets/display_policy/default.yaml`。
- 默认 YAML 使用设计文档中的 schema，并保留中文注释。

**模型**
- 新增 `DisplayPolicy`：保存 `version`、`priorityOrder`、`fitToleranceMinutes`、`focusMaxMinutes`、`focusDurationOrder`、`quadrantRatio`。
- 新增枚举或常量集合：
  - `PriorityRule`: `schedule_priority`、`tag_priority`、`quadrant`、`focus_duration`
  - `FocusDurationOrder`: `asc`、`desc`
- 新增 `DisplayPolicy.defaultPolicy()`，与默认 YAML 保持一致。

## P2：解析、私有文件存储和业务校验

**Parser**
- 新增 `DisplayPolicyParser`。
- YAML 解析结果先转成 Map，再手动读取字段和类型。
- 报错统一抛 `DisplayPolicyValidationException`，消息面向用户，不暴露 Java 堆栈。
- 校验：
  - `version == 1`
  - `priority.order` 四项必须全部出现且不重复
  - `fit_tolerance_minutes >= 0`
  - `focus_max_minutes` 只能是 `120/150`
  - `focus_max_minutes % FOCUS_SLOT_MINUTES == 0`
  - `focus_duration_order` 只能是 `asc/desc`
  - `ratio.quadrant` 四项非负且总和大于 0

**Store / Repository**
- 新增 `DisplayPolicyStore`：
  - 读取 assets 默认 YAML
  - 读取私有文件 `files/display_policy/current.yaml`
  - 覆盖写入私有文件
  - 判断当前是否存在自定义文件
- 新增 `DisplayPolicyRepository`：
  - `getEffectivePolicySync()`
  - `getEditableYamlSync()`
  - `saveCustomYamlSync(String yaml)`
  - `resetToDefaultSync()`
- 读取私有文件失败或解析失败时，运行时回退默认策略；策略页保留可读取的错误原文供用户修正。

**Task 查询**
- `TaskDao` 新增同步查询未归档任务最大专注时长。
- `TaskRepository` 暴露后台线程可调用方法。
- `DisplayPolicyRepository` 保存前比较旧策略和新策略：
  - 升档不受限制。
  - 降档时，新 `focus_max_minutes` 不能低于未归档任务最大 `focusMinutes`。

## P3：引擎策略化

**DisplayEngine**
- 增加接收 `DisplayPolicy` 的 `compute()` / `computeByQuadrant()` 重载。
- 旧重载保留，委托 `DisplayPolicy.defaultPolicy()`。
- 替换硬编码：
  - `15` → `policy.fitToleranceMinutes`
  - `120` → `policy.focusMaxMinutes`
  - 固定权重排序 → 按 `policy.priorityOrder` 构建比较器链
- 时间容纳仍保持外层 A/B 分组，不进入 `priority.order`。
- 主推荐列表中 `quadrant` 规则按当前 `reverseQuadrant` 决定方向。
- 四象限单列表中跳过 `quadrant` 比较器，因为同一列表内象限一致。

**QuadrantRatioFilter**
- `RATIO` 常量改为参数或策略字段。
- 保持现有 A 组优先和比例回填语义。

**调用方**
- `MainViewModel.recomputeSync()`
- `MainViewModel.computeQuadrantOverviewSync()`
- `QuadrantTaskListViewModel.loadDataSync()`
- `WidgetUpdateHelper.computeItems()`

以上入口统一从 `DisplayPolicyRepository` 获取策略后传给引擎。

## P4：专注时长动态档位

**共享 helper**
- 新增 `FocusDurationOptions`。
- `FOCUS_SLOT_MINUTES = 30` 常量化。
- 根据 `focus_max_minutes` 生成 `0, 30, ... focus_max_minutes`。
- 提供动态格式化方法：`0` 用 `s_chore_label`，其他值用 `s_focus_minutes_format`。

**任务编辑页**
- `fragment_task_edit.xml` 中固定 RadioButton 区域改成可动态填充的容器。
- `TaskEditFragment.setupFocusMinutes()` 动态生成 RadioButton。
- `restoreState()` 根据当前 options 勾选。
- 不新增 `s_150min` / `s_150min_label`。

**四象限列表筛选**
- `QuadrantTaskListFragment` 移除固定 `sFocusFilterValues` / `sFocusFilterLabelKeys`。
- 下拉筛选项改为调用 `FocusDurationOptions` 动态生成。

**ViewModel 文案**
- `TaskInputViewModel.getFocusLabel()` 改成调用共享格式化逻辑或同等数据驱动格式化。

## P5：展示策略页面

**Activity / 布局**
- 新增 `DisplayPolicyActivity`，独立 Activity 承载策略页面。
- 新增 `activity_display_policy.xml`。
- 页面结构：
  - 顶部按钮行：`导入`、`导出`
  - 中间 YAML 编辑框
  - 底部按钮行：`重置`、`保存`
- 输入法弹出时不强制把底部按钮抬到键盘上方，只保证编辑框可滚动。

**入口**
- `menu_main.xml` 增加 `action_display_policy`。
- `MainFragment.onOptionsItemSelected()` 跳转 `DisplayPolicyActivity`。
- `AndroidManifest.xml` 注册 Activity。

**交互**
- 导入：`ActivityResultContracts.OpenDocument` 读取 `.yaml/.yml`。
- 导出：`ActivityResultContracts.CreateDocument` 写出当前编辑区文本。
- 保存：解析、schema 校验、业务校验全部通过后写私有文件。
- 重置：弹窗选择“重置到默认配置”或“恢复修改前配置”。
- 保存失败或导入失败不覆盖私有文件，编辑区保留用户文本。

## P6：策略变更刷新

**通知**
- 保存、导入、重置默认成功后通知界面和 Widget 刷新。
- 复用 `DataChangeDispatcher` 时，需要将接口从任务数据变更扩展为策略变更也可触发 Widget 刷新；命名需避免误导。
- 如果扩展现有接口会污染语义，则新增 `PolicyChangeDispatcher`，由 `DisplayPolicyRepository` 调用。

**刷新范围**
- 主界面重新 recompute。
- 四象限概览/列表重新 loadData。
- Widget 调用 `WidgetUpdateHelper.updateAllWidgets()`。

## P7：测试与验证

**新增单元测试**
- `DisplayPolicyParserTest`
- `DisplayPolicyRepositoryTest`
- `FocusDurationOptionsTest`

**更新单元测试**
- `DisplayEngineTest`
- `QuadrantRatioFilterTest`
- `QuadrantTaskListViewModelTest` 或对应可测入口
- `TaskInputViewModelTest`
- `WidgetUpdateHelperTest`

**覆盖点**
- 默认 YAML 可解析。
- 注释不影响解析。
- `priority.order` 缺失、重复、未知值报错。
- `ratio.quadrant` 非 4 项、含负数、总和为 0 报错。
- `focus_max_minutes` 非 `120/150` 报错。
- 无 `150` 分钟未归档任务时允许从 `150` 降到 `120`。
- 有 `150` 分钟未归档任务时拒绝从 `150` 降到 `120`。
- 从 `120` 升到 `150` 不受未归档任务限制。
- `fit_tolerance_minutes` 替代硬编码 `15`。
- `focus_duration_order` 可切换长任务优先和短任务优先。
- `ratio.quadrant` 替代固定 `4:2:2:1`。
- 任务编辑和四象限筛选动态显示 `120/150` 档位。

**验证命令**
- 先跑相关单测：
  - `~/gradlew-wsl.sh --no-daemon testDebugUnitTest --tests com.nearby.justnow.ui.engine.DisplayEngineTest`
  - `~/gradlew-wsl.sh --no-daemon testDebugUnitTest --tests com.nearby.justnow.ui.engine.QuadrantRatioFilterTest`
  - 其他新增测试类逐个跑
- 最后统一跑：
  - `~/gradlew-wsl.sh --no-daemon compileDebugJavaWithJavac`
- 不主动跑全量测试，除非用户确认。

## 风险检查点

- YAML 解析库引入后需确认 release R8 不误删 parser 所需类。
- 动态 RadioButton 需要保持 `textAppearance`，不能硬编码字号。
- 文件导入导出错误提示必须使用字符串资源，四语言补齐。
- 私有 YAML 解析失败时不能阻塞主界面和 Widget。
- 策略变更后 Widget 必须刷新，否则用户会以为导入未生效。
