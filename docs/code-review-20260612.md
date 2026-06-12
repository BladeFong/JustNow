# Code Review Report — 2026-06-12

## 审查范围

- **提交**：`eb7df2a` feat(smart-display): 支持 YAML 展示策略、`3787fab` feat(smart-display): 展示专注时长小时格式
- **模块**：smart-display（展示策略 YAML 配置化 + 专注时长小时格式化）
- **变更规模**：62 文件，+2133/-304 行

## 结果概要

| 级别 | 数量 |
|------|:--:|
| blocking | 0 |
| important | 0 |
| suggestion | 1 |
| nit | 1 |
| **合计** | **2** |

**决策**：💬 Comment（无阻塞项，均为建议和微调）

---

## 审查发现

### [x] #1 [suggestion] UI 线程调用 getEffectivePolicySync()

**文件**：
- `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java:143-144`
- `app/src/main/java/com/nearby/justnow/ui/quadrant/QuadrantTaskListFragment.java:256-257`

**问题**：`getEffectivePolicySync()` 在 UI 线程调用，该方法内部会读取文件（`mStore.readCustomYaml()` 或 `mStore.readDefaultYaml()`）。虽然 YAML 文件通常很小（几百字节），读取很快，但严格来说不应在 UI 线程执行 I/O 操作。

**建议**：可考虑在 Fragment 初始化时异步加载策略，或在 Application 层缓存已解析的 DisplayPolicy 对象，避免每次调用都读文件。当前实现对小文件影响极小，标记为低优先级。

**关闭理由**：YAML 文件 <1KB 读取可忽略；调用场景非高频；项目整体（MainViewModel、QuadrantTaskListViewModel）都是同样用法，单独改两处无意义。确认日期：2026-06-12。已记录到 `docs/code-review-ignore.md`。

---

### [x] #2 [nit] DisplayPolicyActivity 的 exportYaml() 未验证 YAML 有效性

**文件**：`app/src/main/java/com/nearby/justnow/ui/displaypolicy/DisplayPolicyActivity.java:198-208`

**问题**：`exportYaml()` 直接将编辑框中的文本写入文件，不验证 YAML 是否有效。而 `saveCurrentYaml()` 和 `importYaml()` 都会调用 `mRepository.saveCustomYamlSync()` 进行验证。

**建议**：导出前可先验证 YAML 有效性，避免用户导出无效配置后再导入时困惑。但这不是严重问题，因为导入时会进行验证。

**关闭理由**：导入时已验证，无效 YAML 不会造成数据问题；手动改坏再导出是极端场景。确认日期：2026-06-12。已记录到 `docs/code-review-ignore.md`。

---

## 代码亮点

1. **架构清晰**：DisplayPolicy、DisplayPolicyParser、DisplayPolicyRepository、DisplayPolicyStore 分层合理，职责单一
2. **向后兼容**：DisplayEngine 通过重载方法保持了旧接口的兼容性
3. **测试覆盖充分**：新增了 FocusDurationOptionsTest、DisplayPolicyParserTest、DisplayPolicyRepositoryTest，更新了 WidgetUpdateHelperTest
4. **国际化完整**：所有新增字符串都已翻译为英文、简体中文、繁体中文台湾、繁体中文香港
5. **策略可配置化**：将硬编码的排序规则、比例截取等改为 YAML 配置，扩展性好
6. **专注时长格式化统一**：通过 FocusDurationOptions.format() 收敛所有展示点，避免重复代码

---

## 未处理项汇总

无。全部 2 项已关闭。

---

> 审查日期：2026-06-12
> 审查提交：eb7df2a, 3787fab
> 审查工具：code-review-excellence skill
> 重新处理：按可操作性原则移除 2 项"无需修改"的发现（原 #2 formatHours 格式化、原 #3 readAll String 创建）
