# 测试策略模块

# 阶段规划、决策记录

## 定位和功能描述

测试模块负责约束项目验证边界：优先覆盖业务规则和低成本回归点，避免为了测试而引入高维护成本的 UI 或测试框架复杂度。

## 整体规划和决策

### 已确认策略

- 常规代码改动优先做编译验证：`compileDebugJavaWithJavac`。
- 业务规则适合低成本验证时，使用 JVM 单元测试或已存在的 Robolectric 基础设施。
- UI 验证不作为当前任务执行模块的强制收尾要求。
- 实现成本高、维护负担大的 Robolectric 测试不推进；不为验证主界面弹窗、RecyclerView 动画、点击路径而强行搭建复杂测试。
- Robolectric 保留给有明确收益的模块级业务流程，例如节假日数据、时间段解析、地区规则等。
- Robolectric runtime dependencies 需要单独配置 Maven 镜像：
  - `systemProp.robolectric.dependency.repo.url=https://maven.aliyun.com/repository/public`
  - `systemProp.robolectric.dependency.repo.id=aliyun`
- 新增 Robolectric 测试默认固定 `@Config(sdk = 35)`，与现有测试保持一致。

### 当前结论

- 任务执行模块本轮以 Java 编译通过和代码路径审查作为收尾。
- 琐碎任务相关 UI 路径不补 UI 自动化验证。
- 琐碎任务主界面行为不新增高成本 Robolectric 测试。
- Android Studio `assembleDebug` 会覆盖 `processDebugResources` 等资源链接阶段；CLI 的 `compileDebugJavaWithJavac` 通过不等于完整 APK 资源链接一定通过。
- 若 AAPT 报 `resource style/<parent> not found`，优先检查带点号样式是否缺少显式 `parent`。
- CLI 验证时即使命令传入 `ANDROID_HOME=~/Android/Sdk`，`local.properties` 内旧 Windows `sdk.dir` 仍可能触发警告；只要主体任务成功且没有明确 Java/XML/AAPT/test assertion 错误，不把该警告当作本轮代码问题。

# 研究发现、技术决策

- Robolectric 已集成，用于节假日/时间段等模块级业务测试。
- 确认当前任务执行模块不补 UI 自动化验证。
- 确认任务执行模块不补高成本 UI/Robolectric 测试。
- 记录 Android Studio `assembleDebug` 资源链接问题与样式父级经验。
- 配置 Robolectric runtime dependency 镜像，记录 `@Config(sdk = 35)` 约束。
