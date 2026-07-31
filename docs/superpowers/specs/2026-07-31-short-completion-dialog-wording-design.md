# 短完成对话框按钮文案优化与资源复用设计

> 相关模块：[task-execution](../../../modules/task-execution.md)

## 1. 概述

在当前任务执行流程中，实际耗时低于 15 分钟的专注任务触发短完成逻辑，弹出 `ShortCompletionDialog` 对话框。原对话框的第二个按钮文案为“直接完成”，在带有长期安排的场景下，用户容易混淆其是否会清空或影响长期安排。

为了消除歧义并提高资源利用率，将第二个按钮的文案从 `直接完成` 替换为 **`完成本次`**，直接复用既有的字符串资源 `R.string.s_complete_once`（与主界面、任务详情页中的“完成本次”保持一致）。

## 2. 按钮文案矩阵与资源映射

短完成对话框（`ShortCompletionDialog`）按入口和任务状态的按钮文字与资源映射调整如下：

| 按钮类型 | 原始文案与资源 | 优化后文案与资源 | 说明 |
|---|---|---|---|
| **NegativeButton** | `直接完成`<br>(`s_short_completion_btn_direct_complete`) | **`完成本次`**<br>(`R.string.s_complete_once`) | 复用既有字符串资源，消除“直接完成”歧义 |
| **PositiveButton** | `不再安排并调整` / `完成并调整` | **保持不变** | 视任务是否有长期安排决定（有安排使用 `s_short_completion_btn_stop_schedule_and_convert`，无安排使用 `s_short_completion_btn_complete_and_convert`） |
| **NeutralButton** | `取消`<br>(`s_cancel`) | **保持不变**<br>(`R.string.s_cancel`) | 取消弹窗，任务保持执行中状态 |

## 3. 受影响文件与修改范围

### 3.1 代码实现

- **`app/src/main/java/com/nearby/justnow/ui/main/ShortCompletionDialog.java`**
  - 修改 `show()` 方法中的 `.setNegativeButton(...)` 调用，将 `R.string.s_short_completion_btn_direct_complete` 替换为 `R.string.s_complete_once`。

### 3.2 字符串资源

- **`app/src/main/res/values/strings.xml`** 及对应多语言文件（`zh-rCN`, `zh-rHK`, `zh-rTW` 等）
  - 清理无引用的 `s_short_completion_btn_direct_complete` 资源字符串。

### 3.3 模块文档与测试用例

- **`modules/task-execution.md`**
  - 同步更新“< 15min 完成引导”章节中的对话框按钮文案表格。
- **`app/src/test/java/com/nearby/justnow/ui/main/ShortCompletionDialogTest.java`**（若存在）及 ViewModel 相关单测
  - 更新断言与文案匹配的测试代码。

## 4. 验证计划

1. 运行 `compileDebugJavaWithJavac` 验证编译。
2. 运行单单元测试：`./gradlew testDebugUnitTest` 验证 ViewModel 和对话框文案测试通过。
