# 智能展示策略 YAML 配置化设计

## 背景

当前智能展示引擎的排序和截取策略分散在代码中：

- `DisplayEngine` 硬编码安排任务优先、优先标签、象限、专注时长的比较逻辑。
- `DisplayEngine` 硬编码 `15` 分钟时间容差和 `120` 分钟专注时长封顶。
- `QuadrantRatioFilter` 硬编码四象限截取比例 `4:2:2:1`。
- 任务编辑页和四象限时长筛选使用固定专注时长档位。

目标是让用户可以导入、编辑、导出智能展示策略 YAML，同时保持主界面、Widget、四象限概览/列表使用同一套策略。

## 设计目标

- 用户可通过 App 内页面导入、编辑、保存、导出 YAML 配置。
- 默认配置和导出配置带中文注释，字段名固定英文。
- 优先级只配置规则顺序，不暴露权重。
- YAML 原文保存到 App 私有文件。
- 运行时解析为结构化 `DisplayPolicy`，引擎只接收对象，不直接读取文件。
- `focus_max_minutes` 影响任务可选专注时长档位；升档不受限制，降档不能低于未归档任务中已经存在的最大专注时长。
- 导入或编辑异常时不覆盖当前有效配置，并提供重置或恢复入口。

## 不做范围

- 不支持用户自定义条件表达式。
- 不支持多套策略档案切换。
- 不支持中文字段名。
- 不为 `150` 分钟新增专用字符串资源；专注时长显示统一动态格式化。

## YAML Schema

默认配置示例：

```yaml
# 智能展示策略配置
version: 1

priority:
  # 组内排序规则。越靠前，越先比较。
  # 可选值必须各出现一次：
  # schedule_priority: 当前安排窗口内的任务优先
  # tag_priority: 当前生效优先标签的任务优先
  # quadrant: 按四象限顺序排序；晚上反转由时段规则自动决定
  # focus_duration: 专注时长排序
  order:
    - schedule_priority
    - tag_priority
    - quadrant
    - focus_duration

time:
  # 任务超出剩余时间不超过该分钟数时，仍视为可容纳
  fit_tolerance_minutes: 15

  # 专注时长最大档位。仅允许 120 或 150。
  # 降低这项配置时，不能低于当前未归档任务中已经存在的最大专注时长。
  focus_max_minutes: 120

  # 专注时长排序方向：desc=长任务优先，asc=短任务优先
  focus_duration_order: desc

ratio:
  # 智能推荐列表超过显示数量时，按四象限比例截取
  # 顺序固定为：紧急重要、紧急不重要、不紧急重要、不紧急不重要
  quadrant: [4, 2, 2, 1]
```

校验规则：

- `version` 必须为当前支持版本 `1`。
- `priority.order` 只能包含 `schedule_priority`、`tag_priority`、`quadrant`、`focus_duration`，且必须各出现一次。
- `time.fit_tolerance_minutes` 必须为非负整数。
- `time.focus_max_minutes` 只能是 `120` 或 `150`，并且必须能被专注时长档位常量整除。
- `time.focus_duration_order` 只能是 `asc` 或 `desc`。
- `ratio.quadrant` 必须为 4 个非负整数，且总和大于 0。

## 存储和解析

新增默认文件：

```text
app/src/main/assets/display_policy/default.yaml
```

新增用户私有文件：

```text
files/display_policy/current.yaml
```

新增核心类：

- `DisplayPolicy`：不可变策略对象。
- `DisplayPolicyParser`：解析 YAML 并执行 schema 校验。
- `DisplayPolicyStore`：读取 assets 默认 YAML，读写私有 YAML 原文。
- `DisplayPolicyRepository`：对 UI、主界面、Widget、四象限提供当前策略。
- `DisplayPolicyValidationException`：承载可展示错误摘要。
- `FocusDurationOptions`：根据策略生成专注时长选项。

YAML 解析库通过 wrapper 封装在 `DisplayPolicyParser` 内，业务层不直接依赖解析库类型。解析失败、schema 校验失败、业务校验失败都返回明确错误，不抛到展示引擎。

## 引擎适配

`DisplayEngine` 保留现有重载，默认委托到 `DisplayPolicy.defaultPolicy()`；新增接收 `DisplayPolicy` 的重载供主界面、Widget、四象限迁移使用。

主推荐列表：

- 时间容纳仍是外层分组：能容纳组优先于不能容纳组。
- `fit_tolerance_minutes` 替代硬编码 `15`。
- 组内按 `priority.order` 构建比较器链。
- `schedule_priority` 表示当前安排窗口内任务优先。
- `tag_priority` 表示当前生效优先标签任务优先。
- `quadrant` 表示按象限排序，晚上反转继续由时段状态控制。
- `focus_duration` 按 `focus_duration_order` 比较，排序封顶使用 `focus_max_minutes`。

四象限概览/列表：

- 仍按象限拆分，不使用 `ratio.quadrant`。
- 只使用该入口需要的规则：时间容纳、安排任务、优先标签、专注时长。
- `quadrant` 规则在单象限列表内无意义，比较器构建时跳过。

`QuadrantRatioFilter`：

- `RATIO` 常量改为从 `DisplayPolicy.ratio.quadrant` 获取。
- 保持现有 A 组优先、A 组不足再取 B 组的语义。

## 专注时长档位

新增共享 helper：

```java
FocusDurationOptions
```

核心规则：

- `FOCUS_SLOT_MINUTES = 30` 作为代码常量，不散落硬编码。
- `focus_max_minutes / FOCUS_SLOT_MINUTES` 得到档位数。
- 生成选项为 `0` 加上 `FOCUS_SLOT_MINUTES` 的整数倍，直到 `focus_max_minutes`。
- `0` 表示片刻任务；大于 `0` 的值用通用分钟格式动态显示。

影响范围：

- `TaskEditFragment` 不再使用固定 RadioButton 数组，按 `FocusDurationOptions` 动态生成选项。
- `TaskInputViewModel.getFocusLabel()` 改为数据驱动格式化。
- `QuadrantTaskListFragment` 的时长筛选不再使用固定数组，复用同一组选项。
- 与最大专注时长相关的注释、校验和测试同步改为读取策略。

`focus_max_minutes` 修改规则：

- 只允许 `120` 或 `150`。
- 升档不受未归档任务限制。
- 降档不能低于当前未归档任务中已经存在的最大专注时长。
- 当前存在 `150` 分钟未归档任务时，不能从 `150` 降到 `120`。
- 已归档任务不参与限制，避免用户为了改档位清空统计数据。
- 导入、保存、重置默认都走同一套业务校验。

## UI 设计

新增 `DisplayPolicyActivity`，入口放主界面右上菜单，菜单文案为“展示策略”。

页面布局：

- 顶部普通按钮行：`导入`、`导出`。
- 中间 YAML 编辑框，占主要空间。
- 底部普通按钮行：`重置`、`保存`。
- 输入法弹出时不主动抬高底部按钮，只保证编辑框内容和光标区域可见。

交互：

- 打开页面时显示当前 YAML 原文；如果自定义 YAML 可读取但解析失败，编辑区显示这份错误原文，并提示运行时当前回退默认策略。
- `导入` 使用系统文件选择器读取 `.yaml` 或 `.yml`。
- `导出` 使用系统文件创建器导出当前编辑区 YAML，默认文件名 `justnow-display-policy.yaml`。
- `保存` 对编辑区内容执行解析、schema 校验、业务校验，全部通过后覆盖私有文件。
- `重置` 弹出选择：
  - 重置到默认配置。
  - 恢复修改前配置。

“修改前配置”定义为进入页面时加载到编辑区的 YAML。恢复修改前只修改编辑区文本，不写文件；用户仍需点击保存。

## 错误处理

保存或导入统一流程：

1. 获取 YAML 原文。
2. 解析 YAML 语法。
3. 校验 schema。
4. 校验业务规则，特别是 `focus_max_minutes` 降档限制。
5. 通过后覆盖私有文件。
6. 通知主界面、Widget、四象限刷新策略。

失败行为：

- 解析失败：提示解析错误摘要，保留编辑区文本，不覆盖私有文件。
- schema 校验失败：提示字段错误，保留编辑区文本，不覆盖私有文件。
- 业务校验失败：提示原因，保留编辑区文本，不覆盖私有文件。
- 私有文件不存在：运行时使用默认配置。
- 私有文件可读取但解析失败：运行时回退默认配置；策略页显示错误原文，提示当前自定义配置不可用。
- 私有文件无法读取：运行时回退默认配置；策略页显示默认 YAML，并提示当前自定义配置无法读取。
- 重置默认失败：不覆盖私有文件，提示失败原因。

## 数据刷新

保存或导入成功后：

- 主界面重新读取 `DisplayPolicy` 并 recompute。
- 四象限概览/列表重新读取 `DisplayPolicy` 并 recompute。
- Widget 触发 `WidgetUpdateHelper.updateAllWidgets()`。

刷新通知可以复用现有数据变更通知体系；若现有体系不适合策略变更，新增轻量的策略变更通知入口，但不让 `DisplayEngine` 直接观察 Android 状态。

## 测试设计

### Parser 测试

- 默认 YAML 可解析。
- 中文注释不影响解析。
- `version` 非支持版本报错。
- `priority.order` 缺失、重复、未知值时报错。
- `ratio.quadrant` 非 4 项、含负数、总和为 0 报错。
- `focus_max_minutes` 非 `120/150` 报错。
- `focus_duration_order` 非 `asc/desc` 报错。

### Repository 测试

- 私有文件不存在时读取默认配置。
- 保存合法 YAML 后读取自定义配置。
- 私有文件损坏时回退默认配置。
- 无 `150` 分钟未归档任务时允许从 `150` 降到 `120`。
- 有 `150` 分钟未归档任务时拒绝从 `150` 降到 `120`。
- 从 `120` 升到 `150` 不受未归档任务限制。

### 引擎测试

- 调整 `priority.order` 后排序顺序符合配置。
- `fit_tolerance_minutes` 替代硬编码 `15`。
- `focus_duration_order` 可切换长任务优先和短任务优先。
- `focus_max_minutes` 影响专注时长排序封顶。
- `ratio.quadrant` 替代固定 `4:2:2:1`。
- Widget 和四象限入口使用同一策略源。

### 专注时长测试

- `focus_max_minutes=120` 生成 `0/30/60/90/120`。
- `focus_max_minutes=150` 生成 `0/30/60/90/120/150`。
- 任务编辑页按选项动态渲染。
- 四象限时长筛选按选项动态渲染。
- 大于 `0` 的时长统一动态格式化，不依赖固定字符串 key。

### UI ViewModel 测试

- 保存成功。
- 保存失败保留编辑区文本。
- 导入成功。
- 导入失败不覆盖文件。
- 重置默认。
- 恢复修改前。

## 风险和约束

- 引入 YAML 解析库会增加依赖体积；解析封装在单一 parser 内，后续可替换。
- `focus_max_minutes` 会影响任务编辑和四象限筛选，必须与引擎策略一同迁移，不能只改排序。
- 用户 YAML 可读性依赖注释和错误提示；导出时必须生成带注释的规范格式。
- 私有文件损坏不能阻塞 App 主流程，运行时必须回退默认策略。
